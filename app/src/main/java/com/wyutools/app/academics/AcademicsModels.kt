package com.wyutools.app.academics

/**
 * 一条课程成绩，字段直接对应教务 `xskccj/kccjDatas` 的行。
 *
 * 教务把"总成绩"和"数字成绩"分成两个字段：等级制课程（优秀/良好…）只有
 * `zcj` 文本，百分制课程两者都有，所以这里两个都留着，展示时优先文本。
 */
data class GradeEntry(
    /** 成绩代码，查分项与分析单排名要用它。 */
    val cjdm: String?,
    val courseName: String,
    val termCode: String,
    val termName: String,
    /** 总成绩原样文本，例如 "84" 或 "优秀"。 */
    val scoreText: String?,
    val scoreValue: Double?,
    /** 学分绩点。 */
    val point: Double?,
    val credit: Double? = null,
    val courseCode: String? = null,
    /** 课程类别：公共课 / 专业群平台课 … */
    val category: String? = null,
    /** 修读方式：必修 / 选修 … */
    val studyMode: String? = null,
    /** 成绩方式：百分制 / 五级制 … */
    val scoreMode: String? = null,
    /** 考试性质：正常考试 / 补考 … */
    val examNature: String? = null,
    val department: String? = null,
    val hours: Int? = null,
    val remark: String? = null,
) {
    val key: String get() = "${cjdm.orEmpty()}|$courseName|$termCode"

    /** 及格判定：有数字成绩看 60，等级制只有绩点就按 1.0。 */
    val passed: Boolean?
        get() = scoreValue?.let { it >= 60.0 } ?: point?.let { it >= 1.0 }
}

/** 分项成绩：平时 25% 得 100 分这种。 */
data class GradePart(val name: String, val weight: Double?, val score: String?)

/** 成绩分析单里的一行：课程内排名 / 教学班内排名。 */
data class GradeRank(val type: String, val name: String, val total: Int?, val rank: Int?)

data class GradeDetail(
    val total: String? = null,
    val parts: List<GradePart> = emptyList(),
    val ranks: List<GradeRank> = emptyList(),
)

data class GradeTerm(
    val code: String,
    val name: String,
    val entries: List<GradeEntry>,
) {
    val totalCredit: Double
        get() = entries.sumOf { it.credit ?: 0.0 }

    /** 学期 GPA：学分加权（与教务"历年/学期"统计口径一致）。 */
    val gpa: Double?
        get() {
            var weight = 0.0
            var sum = 0.0
            entries.forEach { entry ->
                val credit = entry.credit ?: return@forEach
                val point = entry.point ?: return@forEach
                weight += credit
                sum += credit * point
            }
            return if (weight > 0.0) sum / weight else null
        }
}

data class GradesData(
    val terms: List<GradeTerm>,
    val fetchedAt: Long = 0L,
) {
    val all: List<GradeEntry> get() = terms.flatMap { it.entries }

    val totalCredit: Double get() = all.sumOf { it.credit ?: 0.0 }

    val overallGpa: Double?
        get() {
            var weight = 0.0
            var sum = 0.0
            all.forEach { entry ->
                val credit = entry.credit ?: return@forEach
                val point = entry.point ?: return@forEach
                weight += credit
                sum += credit * point
            }
            return if (weight > 0.0) sum / weight else null
        }

    /** 最早一条成绩的学期码，用来推导入学学期。 */
    val earliestTermCode: String? get() = terms.firstOrNull()?.code
}

data class GradesUiState(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val data: GradesData? = null,
    val error: String? = null,
    val notice: String? = null,
)

/** 绩点统计的一行：教务"历年"或某个学期。 */
data class GpaRow(val label: String, val gpa: Double)

data class GpaData(
    val overall: GpaRow?,
    val terms: List<GpaRow>,
    val fetchedAt: Long = 0L,
) {
    val best: GpaRow? get() = terms.maxByOrNull { it.gpa }
}

data class GpaUiState(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val data: GpaData? = null,
    val error: String? = null,
)

data class PhysicalItem(
    val name: String,
    val value: String?,
    val score: String? = null,
    val level: String? = null,
)

data class PhysicalGroup(val title: String, val items: List<PhysicalItem>)

data class PhysicalTest(
    val termName: String,
    /** 标准总分。 */
    val total: String?,
    val level: String?,
    val remark: String? = null,
    val groups: List<PhysicalGroup> = emptyList(),
)

data class PhysicalData(
    val tests: List<PhysicalTest>,
    val fetchedAt: Long = 0L,
) {
    val latest: PhysicalTest? get() = tests.lastOrNull()
}

data class PhysicalUiState(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val data: PhysicalData? = null,
    val error: String? = null,
)

/** 一条结课评价任务（一门课的一个教学环节）。 */
data class EvalTask(
    val pjdm: String?,
    val courseName: String,
    val teacher: String?,
    /** 教学环节：讲课 / 上机 … */
    val session: String?,
    val closeDate: String?,
    val termCode: String,
    val termName: String,
) {
    val key: String get() = "${pjdm.orEmpty()}|$courseName|${teacher.orEmpty()}|${session.orEmpty()}"
}

data class EvalAnswer(
    val question: String,
    val answer: String?,
    val score: String?,
    val weight: String?,
    val feedback: String?,
)

data class EvalDetail(
    val task: EvalTask,
    val total: String? = null,
    val answers: List<EvalAnswer> = emptyList(),
)

data class EvalTerm(
    val code: String,
    val name: String,
    val tasks: List<EvalTask>,
)

data class EvalData(
    val terms: List<EvalTerm>,
    val fetchedAt: Long = 0L,
) {
    val all: List<EvalTask> get() = terms.flatMap { it.tasks }
}

data class EvalUiState(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val data: EvalData? = null,
    val error: String? = null,
)

/** 教务学期码 "202501" -> "2025-2026-1"；不是这个格式就原样返回。 */
internal fun termDisplayName(code: String): String {
    if (code.length != 6) return code
    val year = code.take(4).toIntOrNull() ?: return code
    return when (code.drop(4)) {
        "01" -> "$year-${year + 1}-1"
        "02" -> "$year-${year + 1}-2"
        else -> code
    }
}
