package com.wyutools.app.academics

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * 教务新版模块（成绩 / 绩点 / 体测 / 教评）的响应解析。
 *
 * 这些接口都是 easyui datagrid 的 `{ rows: [...] }` 形状，字段名沿用强智那套
 * 拼音缩写（kcmc / zcj / cjjd / xf …）。所有取值都做空值与 "null" 兜底，单条
 * 记录异常不影响整页。
 */
object AcademicsParser {

    // -------------------- 成绩 --------------------

    fun parseGrades(raw: String): GradesData {
        val root = JSONObject(raw)
        val rows = root.optJSONArray("rows") ?: root.optJSONArray("data") ?: JSONArray()
        val entries = ArrayList<GradeEntry>(rows.length())
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val course = row.text("kcmc") ?: continue
            val termCode = row.text("xnxqdm").orEmpty()
            entries += GradeEntry(
                cjdm = row.text("cjdm"),
                courseName = course,
                termCode = termCode,
                termName = row.text("xnxqmc") ?: termDisplayName(termCode),
                scoreText = row.text("zcj") ?: row.text("zcjfs"),
                scoreValue = row.double("zcjfs") ?: row.double("zcj"),
                point = row.double("cjjd"),
                credit = row.double("xf"),
                courseCode = row.text("kcbh"),
                category = row.text("kcdlmc"),
                studyMode = row.text("xdfsmc"),
                scoreMode = row.text("cjfsmc"),
                examNature = row.text("ksxzmc"),
                department = row.text("kkbmmc"),
                hours = row.double("zxs")?.toInt(),
                remark = row.text("bz"),
            )
        }
        // 学期从早到晚；同分按课程名，保持每次打开顺序一致。
        val terms = entries
            .groupBy { it.termCode }
            .toList()
            .sortedBy { it.first }
            .map { (code, list) ->
                GradeTerm(
                    code = code,
                    name = list.firstOrNull()?.termName ?: termDisplayName(code),
                    entries = list.sortedWith(
                        compareBy({ it.courseName }, { it.examNature.orEmpty() }),
                    ),
                )
            }
        return GradesData(terms = terms, fetchedAt = System.currentTimeMillis())
    }

    /** `getDetail` 的 JSON + `kccjfxd.page` 的 HTML 合成一张分析单。 */
    fun parseGradeDetail(detailRaw: String, rankHtml: String?): GradeDetail {
        val root = runCatching { JSONObject(detailRaw) }.getOrNull()
        val data = root?.optJSONArray("data")
        var total: String? = null
        val parts = ArrayList<GradePart>()
        val first = data?.optJSONObject(0)
        if (first != null) {
            total = first.text("zcj")
            for (index in 1..5) {
                val score = first.text("cj$index") ?: continue
                val name = first.text("bl${index}mc") ?: continue
                parts += GradePart(
                    name = name,
                    weight = first.double("bl$index"),
                    score = score,
                )
            }
        }
        return GradeDetail(
            total = total,
            parts = parts,
            ranks = parseRanks(rankHtml),
        )
    }

    /** 分析单是一张 4 列小表：类型 / 名称 / 总人数 / 排名。 */
    private fun parseRanks(html: String?): List<GradeRank> {
        if (html.isNullOrBlank()) return emptyList()
        val rows = Regex("<tr[^>]*>(.*?)</tr>", RegexOption.DOT_MATCHES_ALL)
            .findAll(html)
            .map { it.groupValues[1] }
        val ranks = ArrayList<GradeRank>()
        for (row in rows) {
            val cells = Regex("<td[^>]*>(.*?)</td>", RegexOption.DOT_MATCHES_ALL)
                .findAll(row)
                .map { it.groupValues[1].cleanHtml() }
                .toList()
            if (cells.size < 4) continue
            ranks += GradeRank(
                type = cells[0],
                name = cells[1],
                total = cells[2].toIntOrNull(),
                rank = cells[3].toIntOrNull(),
            )
        }
        return ranks
    }

    private fun String.cleanHtml(): String = replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .trim()

    // -------------------- 绩点 --------------------

    fun parseGpa(overallRaw: String, termRaw: String?): GpaData {
        val overallRows = parseGpaRows(overallRaw)
        // "历年"是教务页面上默认勾选的那种统计口径，也就是总平均绩点。
        val overall = overallRows.firstOrNull { it.label.contains("历年") }
            ?: overallRows.firstOrNull()
        val terms = termRaw?.let(::parseGpaRows).orEmpty()
        return GpaData(
            overall = overall,
            terms = terms,
            fetchedAt = System.currentTimeMillis(),
        )
    }

    private fun parseGpaRows(raw: String): List<GpaRow> {
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
        val rows = root.optJSONArray("rows") ?: return emptyList()
        val list = ArrayList<GpaRow>(rows.length())
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val label = row.text("mc") ?: continue
            val gpa = row.double("pjxfjd") ?: continue
            list += GpaRow(label = label, gpa = gpa)
        }
        return list
    }

    // -------------------- 体测 --------------------

    fun parsePhysical(raw: String): PhysicalData {
        val root = JSONObject(raw)
        val rows = root.optJSONArray("rows") ?: JSONArray()
        val tests = ArrayList<PhysicalTest>(rows.length())
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val term = row.text("xnxqmc") ?: "未标注学期"
            tests += PhysicalTest(
                termName = term,
                total = row.text("zf"),
                level = row.text("dj"),
                remark = row.text("tips"),
                groups = buildPhysicalGroups(row),
            )
        }
        // 最近一次体测排在前面。
        tests.sortByDescending { it.termName }
        return PhysicalData(tests = tests, fetchedAt = System.currentTimeMillis())
    }

    private fun buildPhysicalGroups(row: JSONObject): List<PhysicalGroup> {
        fun item(
            name: String,
            value: String?,
            score: String? = null,
            level: String? = null,
        ) = PhysicalItem(name, value, score, level)

        val body = listOf(
            item("身高", row.text("sg")?.let { "$it 厘米" }),
            item("体重", row.text("tz")?.let { "$it 千克" }, row.text("tzpf"), row.text("tzdj")),
        )
        val lung = listOf(
            item("肺活量", row.text("fhl")?.let { it.cnUnit("毫升") }, row.text("fhlpf"), row.text("fhldj")),
        )
        val sprint = listOf(
            item("50 米跑", row.text("wsm")?.let { "$it 秒" }, row.text("wsmpf"), row.text("wsmdj")),
        )
        val longRun = listOf(
            item(
                "1000 / 800 米跑",
                row.text("yqmorbbm")?.let(::secondsToText),
                row.text("yqmorbbmpf"),
                row.text("yqmorbbmdj"),
            ),
        )
        val sitReach = listOf(
            item("坐位体前屈", row.text("zwtqq")?.let { "$it 厘米" }, row.text("zwtqqpf"), row.text("zwtqqdj")),
        )
        val jump = listOf(
            item("立定跳远", row.text("ldty")?.let { it.cnUnit("厘米") }, row.text("ldtypf"), row.text("ldtydj")),
        )
        val strength = listOf(
            item(
                "引体向上 / 仰卧起坐",
                row.text("ytxsorywqz")?.let { "${it.cnUnit("")} 个" },
                row.text("ytxsorywqzpf"),
                row.text("ytxsorywqzdj"),
            ),
        )
        val bonus = listOf(
            item("长跑加分", row.text("yqmorbbmjf")),
            item("力量加分", row.text("ytxsorywqzjf")),
            item("附加分", row.text("fjf")),
        )
        val vision = listOf(
            item("左眼裸眼视力", row.text("zylysl")),
            item("右眼裸眼视力", row.text("yylysl")),
            item("左眼屈光不正", row.text("zyqgbz")),
            item("右眼屈光不正", row.text("yyqgbz")),
            item("左眼串镜", row.text("zycj")),
            item("右眼串镜", row.text("yycj")),
        )
        return listOf(
            PhysicalGroup("身体形态", body.filter(::hasValue)),
            PhysicalGroup("肺活量", lung.filter(::hasValue)),
            PhysicalGroup("速度", sprint.filter(::hasValue)),
            PhysicalGroup("耐力", longRun.filter(::hasValue)),
            PhysicalGroup("柔韧", sitReach.filter(::hasValue)),
            PhysicalGroup("爆发力", jump.filter(::hasValue)),
            PhysicalGroup("力量", strength.filter(::hasValue)),
            PhysicalGroup("加分项", bonus.filter(::hasValue)),
            PhysicalGroup("视力", vision.filter(::hasValue)),
        ).filter { it.items.isNotEmpty() }
    }

    private fun hasValue(item: PhysicalItem): Boolean =
        !item.value.isNullOrBlank() || !item.score.isNullOrBlank()

    /** "168.9" 这种教务带一位小数的整数，直接拼单位会显得脏。 */
    private fun String.cnUnit(unit: String): String {
        val number = toDoubleOrNull()
        val clean = if (number != null && number == Math.floor(number)) {
            number.toInt().toString()
        } else {
            this
        }
        return if (unit.isEmpty()) clean else "$clean $unit"
    }

    /** "200" 秒 -> 3'20"。 */
    private fun secondsToText(raw: String): String {
        val seconds = raw.toDoubleOrNull()?.toInt() ?: return raw
        if (seconds < 60) return "$seconds 秒"
        val minutes = seconds / 60
        val rest = seconds % 60
        return String.format(Locale.US, "%d'%02d\"", minutes, rest)
    }

    // -------------------- 教评 --------------------

    fun parseEvalTasks(raw: String, termCode: String): List<EvalTask> {
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
        val rows = root.optJSONArray("rows") ?: return emptyList()
        val tasks = ArrayList<EvalTask>(rows.length())
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val course = row.text("kcmc") ?: continue
            tasks += EvalTask(
                pjdm = row.text("pjdm"),
                courseName = course,
                teacher = row.text("teaxm"),
                session = row.text("jxhjmc"),
                closeDate = row.text("jkrq"),
                termCode = row.text("xnxqdm") ?: termCode,
                termName = row.text("xnxqmc") ?: termDisplayName(termCode),
            )
        }
        return tasks
    }

    fun parseEvalDetail(task: EvalTask, raw: String): EvalDetail {
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: JSONArray()
        val answers = ArrayList<EvalAnswer>(array.length())
        // 每题给一个分值（xzpf）和一个百分比答案（dtjg），总分就是按分值加权的
        // 百分比之和，教务页面上显示的"评分为：100"正是这么算出来的。
        var totalScore = 0.0
        var scored = array.length() > 0
        for (index in 0 until array.length()) {
            val row = array.optJSONObject(index) ?: continue
            val question = row.text("zbmc") ?: continue
            val answer = row.text("dtjg")
            val weight = row.double("xzpf")
            answers += EvalAnswer(
                question = question,
                answer = answer,
                score = row.text("xzpf"),
                weight = row.text("zbfz"),
                feedback = row.text("yjfk"),
            )
            val answerValue = answer?.toDoubleOrNull()
            if (weight != null && answerValue != null) {
                totalScore += weight * answerValue / 100.0
            } else {
                scored = false
            }
        }
        val total = if (scored) String.format(Locale.US, "%.0f", totalScore) else null
        return EvalDetail(task = task, total = total, answers = answers)
    }
}

// -------------------- JSON 取值兜底 --------------------

internal fun JSONObject.text(key: String): String? {
    val raw = opt(key) ?: return null
    val value = raw.toString().trim()
    if (value.isEmpty() || value == "null" || value == "undefined" || value == "-") return null
    return value
}

internal fun JSONObject.double(key: String): Double? = text(key)?.toDoubleOrNull()
