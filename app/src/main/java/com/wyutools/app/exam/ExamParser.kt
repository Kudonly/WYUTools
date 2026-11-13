package com.wyutools.app.exam

import com.wyutools.app.schedule.ScheduleTerm
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.Locale

/**
 * 教务系统「学生考试任务」接口（`new/student/xsksrw/paginateXsksrw`）返回值 -> [ExamData]。
 *
 * 返回体是 jqGrid 形状：`{ rows: [...] }`，每行沿用强智那套字段名
 * （kcmc / ksrq / kssj / kscdmc / zwh / jkteaxms ...）。不同版本的模块会换
 * 别名，所以每个值都按一串候选键去取，取到第一个非空就停；日期与时间从
 * 字符串里抠，避免因为 "2026年1月12日" / "2026-1-12 09:00" 这类格式差异整条丢掉。
 */
object ExamParser {
    fun parse(raw: String, termCode: String): ExamData {
        val body = raw.trim()
        if (body.isEmpty()) throw ExamException("考试数据为空，请稍后重试。")
        val root = runCatching { JSONObject(body) }.getOrNull()
            ?: throw ExamException("考试数据格式异常，请稍后重试。")
        val code = root.optString("code").trim()
        if (code == "-401" || code == "401") {
            throw ExamException("登录已失效，请重新登录。", sessionExpired = true)
        }
        val rows = root.optJSONArray("rows")
            ?: root.optJSONArray("data")
            ?: root.optJSONArray("list")
        if (rows == null) {
            // 没有 rows 往往是业务错误（还没排考、模块未开放），把教务的原话
            // 透出来比笼统的"格式异常"有用。
            val message = listOf("msg", "message", "error")
                .map { root.optString(it).trim() }
                .firstOrNull { it.isNotEmpty() && it != "null" }
            if (message != null) throw ExamException(message)
        }
        val entries = ArrayList<ExamEntry>()
        if (rows != null) {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                mapRow(row)?.let(entries::add)
            }
        }
        entries.sortWith(
            compareBy(
                { it.date == null },
                { it.date },
                { it.startTime.orEmpty() },
                { it.courseName },
            ),
        )
        return ExamData(
            termCode = termCode,
            termName = ScheduleTerm.displayName(termCode),
            entries = entries,
            fetchedAt = System.currentTimeMillis(),
        )
    }

    private fun mapRow(row: JSONObject): ExamEntry? {
        val fields = flatten(row)
        val course = text(fields, "kcmc", "kcmc_cn", "coursename", "course")
            ?: return null
        // kssj 常见的写法是 "10:30:00--12:00:00"，个别学期会把日期也塞进去
        // （"2026-01-12 10:30:00--12:00:00"），所以日期同时从这里兜一层。
        val timeText = text(fields, "kssj", "kssj_str", "examtime", "time", "kssjformat")
        val dateText = text(fields, "ksrq", "ksrq_str", "examdate", "date")
            ?: timeText
        val (start, end) = timeRange(timeText)
        val section = sectionLabel(text(fields, "jcdm2", "jcmc", "jcdm", "jc"))
            ?: timeText?.takeIf { it.contains("节") }
        return ExamEntry(
            courseName = course,
            date = parseDate(dateText),
            startTime = start,
            endTime = end,
            sectionLabel = section,
            room = room(fields),
            seat = text(fields, "zwh", "zw", "zwmc", "seatno", "seat"),
            kind = text(fields, "kslbmc", "kslxmc", "ksaplxmc", "examtype", "kslx"),
            invigilators = text(fields, "jkteaxms", "jkteaxm", "jkjsxm", "teacher"),
            assessmentForm = text(fields, "khxsmc", "khxs"),
            paperMode = text(fields, "ksxsmc", "ksxs"),
            candidateCount = text(fields, "ksrs", "xs"),
            remark = text(fields, "bz", "remark", "memo"),
        )
    }

    /** 校区 + 考场，重复项（例如两边都是"江门校区"）只留一个。 */
    private fun room(fields: Map<String, String>): String? {
        val parts = LinkedHashSet<String>()
        text(fields, "xqmc", "campusName")?.let(parts::add)
        text(fields, "kscdmc", "kscd", "ksdd", "cdmc", "room")?.let(parts::add)
        if (parts.isEmpty()) return null
        return parts.joinToString(" · ")
    }

    /** 键统一小写，值统一 trim；教务会把空值写成 "null" / "undefined"。 */
    private fun flatten(row: JSONObject): Map<String, String> {
        val map = HashMap<String, String>(row.length() * 2)
        val keys = row.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = row.opt(key)?.toString()?.trim().orEmpty()
            if (value.isEmpty() || value == "null" || value == "undefined") continue
            if (value == "-" || value == "—") continue
            map[key.lowercase(Locale.US)] = value
        }
        return map
    }

    private fun text(fields: Map<String, String>, vararg keys: String): String? {
        for (key in keys) {
            val value = fields[key]
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    private val DatePattern = Regex("(\\d{4})\\s*[-/.年]\\s*(\\d{1,2})\\s*[-/.月]\\s*(\\d{1,2})")
    private val TimePattern = Regex("(\\d{1,2})\\s*[:：]\\s*(\\d{2})")

    private fun parseDate(raw: String?): LocalDate? {
        if (raw.isNullOrBlank()) return null
        val match = DatePattern.find(raw) ?: return null
        val (year, month, day) = match.destructured
        return runCatching {
            LocalDate.of(year.toInt(), month.toInt(), day.toInt())
        }.getOrNull()
    }

    /** "09:00-11:00" / "9:00~11:00" / "10:30:00--12:00:00" 都只取两个时刻。 */
    private fun timeRange(raw: String?): Pair<String?, String?> {
        if (raw.isNullOrBlank()) return null to null
        val times = TimePattern.findAll(raw).map { match ->
            val (hour, minute) = match.destructured
            String.format(Locale.US, "%02d:%s", hour.toIntOrNull() ?: 0, minute)
        }.toList()
        return when (times.size) {
            0 -> null to null
            1 -> times[0] to null
            else -> times[0] to times[1]
        }
    }

    /** "0304" -> "第 3-4 节"；"1-2节" 之类的原样保留。 */
    private fun sectionLabel(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        if (trimmed.length == 4 && trimmed.all { it.isDigit() }) {
            val from = trimmed.substring(0, 2).toIntOrNull()
            val to = trimmed.substring(2).toIntOrNull()
            if (from != null && to != null) {
                return if (from == to) "第 $from 节" else "第 $from-$to 节"
            }
        }
        return trimmed
    }
}

/** 考试安排缓存：几行 JSON，放在 auth 之外自己的 prefs 里。 */
internal object ExamCache {
    fun encode(data: ExamData): String {
        val rows = JSONArray()
        data.entries.forEach { entry ->
            rows.put(
                JSONObject()
                    .put("course", entry.courseName)
                    .put("date", entry.date?.toString().orEmpty())
                    .put("start", entry.startTime.orEmpty())
                    .put("end", entry.endTime.orEmpty())
                    .put("section", entry.sectionLabel.orEmpty())
                    .put("room", entry.room.orEmpty())
                    .put("seat", entry.seat.orEmpty())
                    .put("kind", entry.kind.orEmpty())
                    .put("invigilators", entry.invigilators.orEmpty())
                    .put("form", entry.assessmentForm.orEmpty())
                    .put("mode", entry.paperMode.orEmpty())
                    .put("count", entry.candidateCount.orEmpty())
                    .put("remark", entry.remark.orEmpty()),
            )
        }
        return JSONObject()
            .put("termCode", data.termCode)
            .put("termName", data.termName)
            .put("fetchedAt", data.fetchedAt)
            .put("rows", rows)
            .toString()
    }

    fun decode(raw: String?): ExamData? {
        if (raw.isNullOrBlank()) return null
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val termCode = root.optString("termCode").takeIf { it.isNotBlank() } ?: return null
        val rows = root.optJSONArray("rows") ?: JSONArray()
        val entries = ArrayList<ExamEntry>(rows.length())
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val course = row.optString("course").takeIf { it.isNotBlank() } ?: continue
            entries += ExamEntry(
                courseName = course,
                date = row.optString("date").takeIf { it.isNotBlank() }
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                startTime = row.optString("start").takeIf { it.isNotBlank() },
                endTime = row.optString("end").takeIf { it.isNotBlank() },
                sectionLabel = row.optString("section").takeIf { it.isNotBlank() },
                room = row.optString("room").takeIf { it.isNotBlank() },
                seat = row.optString("seat").takeIf { it.isNotBlank() },
                kind = row.optString("kind").takeIf { it.isNotBlank() },
                invigilators = row.optString("invigilators").takeIf { it.isNotBlank() },
                assessmentForm = row.optString("form").takeIf { it.isNotBlank() },
                paperMode = row.optString("mode").takeIf { it.isNotBlank() },
                candidateCount = row.optString("count").takeIf { it.isNotBlank() },
                remark = row.optString("remark").takeIf { it.isNotBlank() },
            )
        }
        return ExamData(
            termCode = termCode,
            termName = root.optString("termName").ifBlank {
                ScheduleTerm.displayName(termCode)
            },
            entries = entries,
            fetchedAt = root.optLong("fetchedAt", 0L),
        )
    }
}
