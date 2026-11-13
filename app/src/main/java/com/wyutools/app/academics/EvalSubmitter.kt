package com.wyutools.app.academics

import com.wyutools.app.auth.AuthViewModel
import org.json.JSONArray
import org.json.JSONObject

/**
 * ⚠️ **未验证 / UNVERIFIED** ⚠️
 * ---------------------------------------------------------------------------
 * 教学评价（评教打分）提交能力。
 *
 * 强智教务的评教提交分两步：
 *   1. GET  `new/student/teapj/pj.page?teadm=..&dgksdm=..&_=<ts>` 拿到表单页 HTML，
 *      从中抽出隐藏字段（pjlxdm / wjdm / kcptdm / kcdm）与动态令牌
 *      （32 位 hex + `gXXXX` 参数，值来自页面脚本里的 `x{}` 对象函数）。
 *   2. POST `new/student/teapj/savePj`，表单里带课程字段 + 问卷明细 `dt` + 动态参数。
 *
 * **为什么标注「未验证」：**
 *   1. 本校教务目前未开放学生端评教窗口，`pjDatas` 不返回可评课程，
 *      因此 `pj.page` 的真实 HTML 从未拉取过。下面所有正则都取自前端页面脚本，
 *      页面结构、令牌变量名、隐藏字段位置一律未经真机校验。
 *   2. 内置的问卷指标（`zbdm`）只是一套实验课模板。不同课程 / 学期
 *      用的问卷不同，这套模板能否通用完全未知；本实现优先尝试从表单页
 *      解析真实指标，解析不到才回退到该模板。
 *   3. `savePj` 的成功判定、是否允许重复提交、是否需要二次确认均未验证。
 *
 * **因此界面不提供任何入口**：这里只是把能力实现好留在代码里，等评教窗口
 * 开放后，先抓一次真实 `pj.page` 校验字段与正则，再决定是否接入 UI。
 * 现阶段任何 UI 都不应调用 [submit]。
 * ---------------------------------------------------------------------------
 */
object EvalSubmitter {

    /** 五档评分，下标与教务评教弹窗的等级顺序对齐。 */
    enum class Level(val index: Int, val label: String) {
        EXCELLENT(0, "优秀"),
        GOOD(1, "良好"),
        AVERAGE(2, "中等"),
        PASS(3, "及格"),
        FAIL(4, "不及格"),
        ;

        /** 该档对应的答题结果（百分制）。 */
        val score: Int get() = 100 - 20 * index
    }

    data class Question(val zbdm: String, val zbmc: String)

    data class Result(
        val ok: Boolean,
        val message: String,
        /** 教务原始响应，便于未验证阶段排查。 */
        val raw: String? = null,
    )

    /**
     * 提交一门课的评价。
     *
     * 未验证：不要在界面上暴露。调用方需自行确认用户已明确选择等级。
     */
    suspend fun submit(
        auth: AuthViewModel,
        task: EvalTask,
        level: Level,
    ): Result {
        val teadm = task.teadm?.takeIf { it.isNotBlank() }
        val dgksdm = task.dgksdm?.takeIf { it.isNotBlank() }
        if (teadm == null || dgksdm == null) {
            return Result(
                ok = false,
                message = "这条记录缺少 teadm / dgksdm，无法定位评教表单。",
            )
        }

        val html = try {
            auth.fetchEvalFormPage(teadm, dgksdm)
        } catch (error: Throwable) {
            return Result(false, "评价表单页拉取失败：${error.message ?: "未知错误"}")
        }

        val form = parseForm(html)
            ?: return Result(
                ok = false,
                message = "没有从评教表单页解析出隐藏参数（页面结构可能已变，未验证）。",
                raw = html.take(2000),
            )

        // 优先用表单页里的真实问卷指标；抽不到再退回内置的实验课模板。
        val questions = parseQuestions(html).ifEmpty { DefaultQuestions }

        val fields = buildFields(task, level, form, questions)
        val response = try {
            auth.postEvalSubmit(fields)
        } catch (error: Throwable) {
            return Result(false, "评价提交失败：${error.message ?: "未知错误"}")
        }
        return interpret(response)
    }

    // -------------------- pj.page 解析 --------------------

    private data class FormParams(
        val pjlxdm: String,
        val wjdm: String,
        val kcptdm: String,
        val kcdm: String,
        /** `gXXXX` 动态反爬参数。 */
        val dynamic: Map<String, String>,
    )

    /**
     * 解析评教表单页：先定位含 `savePj` 的 script 块，抽出令牌对象
     * 里 `key:function(){return '..'}` 的返回值、32 位 hex 令牌，再把
     * `gXXXX: xObj['key']()` 映射成实际参数值。
     */
    private fun parseForm(html: String): FormParams? {
        val script = Regex(
            """<script[^>]*>([\s\S]*?)</script>""",
            RegexOption.IGNORE_CASE,
        ).findAll(html)
            .map { it.groupValues[1] }
            .firstOrNull { it.contains("/new/student/teapj/savePj") }
            ?: return null

        val objName = Regex("""let\s+(x\w+)\s*=\s*\{""")
            .find(script)?.groupValues?.get(1)
            ?: return null

        // 令牌对象里各个函数返回的常量值；排除名为 "g" 的项。
        val returns = HashMap<String, String>()
        Regex(
            """['"]?(\w+)['"]?\s*:\s*function\s*\(\s*\)\s*\{\s*return\s+'([^']*)'""",
        ).findAll(script).forEach { match ->
            if (match.groupValues[1] != "g") {
                returns[match.groupValues[1]] = match.groupValues[2]
            }
        }
        val hexToken = Regex("""'([0-9a-f]{32})'""")
            .find(script)?.groupValues?.get(1)

        val dynamic = LinkedHashMap<String, String>()
        Regex(
            """(g\w+)\s*:\s*${Regex.escape(objName)}\['(\w+)'\]\s*\(\s*\)""",
        ).findAll(script).forEach { match ->
            dynamic[match.groupValues[1]] =
                returns[match.groupValues[2]] ?: hexToken ?: ""
        }

        // 隐藏字段在「问卷评分」之后，页面会把空格/换行压掉后再取值。
        val marker = html.indexOf("问卷评分")
        val tail = (if (marker >= 0) html.substring(marker) else html)
            .replace(" ", "")
            .replace("\n", "")
            .replace("\t", "")
            .replace("\r", "")

        fun quoted(key: String): String {
            val startKey = "$key:'"
            val start = tail.indexOf(startKey)
            if (start < 0) return ""
            val rest = tail.substring(start + startKey.length)
            val end = rest.indexOf('\'')
            return if (end < 0) "" else rest.substring(0, end)
        }

        val pjlxdm = quoted("pjlxdm")
        val wjdm = quoted("wjdm")
        val kcptdm = quoted("kcptdm")
        val kcdm = quoted("kcdm")
        if (pjlxdm.isEmpty() || wjdm.isEmpty()) return null

        return FormParams(pjlxdm, wjdm, kcptdm, kcdm, dynamic)
    }

    /** 尝试从表单页抽真实问卷指标；抽不到返回空列表，由调用方回退模板。 */
    private fun parseQuestions(html: String): List<Question> =
        Regex(
            """zbdm\s*[:=]\s*['"]?(\d{6,})['"]?[\s\S]{0,400}?zbmc\s*[:=]\s*'([^']*)'""",
            RegexOption.IGNORE_CASE,
        ).findAll(html)
            .map { Question(it.groupValues[1], it.groupValues[2].trim()) }
            .toList()

    // -------------------- savePj 表单构造 --------------------

    /** 构造提交表单：fz = 分值/10，dtjg = 分值，wtpf = 分值。 */
    private fun buildFields(
        task: EvalTask,
        level: Level,
        form: FormParams,
        questions: List<Question>,
    ): Map<String, String> {
        val score = level.score
        val perQuestionScore = score / 10

        val details = JSONArray()
        questions.forEach { question ->
            details.put(
                JSONObject().apply {
                    put("txdm", 4)
                    val numericCode = question.zbdm.toLongOrNull()
                    if (numericCode != null) {
                        put("zbdm", numericCode)
                    } else {
                        put("zbdm", question.zbdm)
                    }
                    put("zbmc", question.zbmc)
                    put("fz", perQuestionScore)
                    put("dtjg", score)
                },
            )
        }

        val fields = LinkedHashMap<String, String>().apply {
            put("xnxqdm", task.termCode)
            put("wtpf", score.toString())
            put("pjlxdm", form.pjlxdm)
            put("teadm", task.teadm.orEmpty())
            put("teabh", task.teabh.orEmpty())
            put("teaxm", task.teacher.orEmpty())
            put("wjdm", form.wjdm)
            put("kcrwdm", task.kcrwdm.orEmpty())
            put("kcptdm", form.kcptdm)
            put("kcdm", form.kcdm)
            put("dgksdm", task.dgksdm.orEmpty())
            put("jxhjdm", task.jxhjdm.orEmpty())
            put("dt", details.toString())
            // 动态令牌最后并入，避免被上面的固定字段覆盖同名项。
            putAll(form.dynamic)
        }
        return fields
    }

    /** savePj 响应形态未知（JSON 或纯文本都可能），这里做宽松判定。 */
    private fun interpret(raw: String): Result {
        val text = raw.trim()
        val json = runCatching { JSONObject(text) }.getOrNull()
        if (json != null) {
            val code = json.optString("code")
            val ok = code == "0" || code.equals("success", ignoreCase = true) ||
                json.optBoolean("success", false)
            val message = listOf("msg", "message", "info")
                .map { json.optString(it) }
                .firstOrNull { it.isNotBlank() }
            return Result(
                ok = ok,
                message = if (ok) {
                    "评价提交成功（未验证）。"
                } else {
                    message ?: "教务未确认这次提交（未验证）。"
                },
                raw = text,
            )
        }
        val ok = text.contains("成功") && !text.contains("失败")
        return Result(
            ok = ok,
            message = text.take(200).ifBlank { "教务返回空响应（未验证）。" },
            raw = text,
        )
    }

    /**
     * 内置的实验课问卷模板（10 题）。
     * ⚠️ 未验证：仅作兜底，真实课程问卷的指标代码几乎肯定不同。
     */
    private val DefaultQuestions: List<Question> = listOf(
        Question("10011188", "1、教师热情、精神饱满、亲和力强，感染了我"),
        Question("10011189", "2、教师在实验前能够对实验原理、实验目的、实验内容和方法进行准确的讲解"),
        Question("10011190", "3、能够恰当地使用生动、丰富的多媒体课件等现代教学手段，明显改善实验教学效果"),
        Question("10011191", "4、老师能清楚、准确演示实验过程，能及时解答和指导同学们提出的疑难问题"),
        Question("10011192", "5、能安排一定综合性、设计性的实验内容，并在讲解、演示过程中引入新技术"),
        Question("10011193", "6、老师能引导我们运用所学知识分析实验的现象和结果"),
        Question("10011194", "7、老师能够及时帮助学生排除实验过程中出现的问题和故障"),
        Question("10011195", "8、我能明白老师的讲解和演示，并按要求完成实验"),
        Question("10011196", "9、老师能认真批阅我的实验报告，并指出不足"),
        Question("10011197", "10、我喜欢这个教师继续给我们上课"),
    )
}
