package com.wyutools.app.exam

import java.time.LocalDate

/**
 * 一场考试。教务系统里同一门课可能有多个安排（期末、补考、重修），所以这里
 * 不做按课程合并 —— 每一行就是一条独立安排。
 */
data class ExamEntry(
    /** 课程名称，必填。 */
    val courseName: String,
    /** 考试日期；教务个别安排只给周次与星期，此时为 null，排到最后。 */
    val date: LocalDate?,
    /** 开始时间 "09:00"；纯日期安排时为 null。 */
    val startTime: String? = null,
    val endTime: String? = null,
    /** "第 3-4 节"，教务只给节次代码时换算得到。 */
    val sectionLabel: String? = null,
    val room: String? = null,
    val seat: String? = null,
    /** 期末考试 / 补考 / 重修考试，取自 kslbmc。 */
    val kind: String? = null,
    val invigilators: String? = null,
    /** 考核形式（开卷 / 闭卷 / 机考…），取自 khxsmc。 */
    val assessmentForm: String? = null,
    /** 考试形式（笔试 / 上机…），取自 ksxsmc。 */
    val paperMode: String? = null,
    /** 考生人数，教务给的字符型数字。 */
    val candidateCount: String? = null,
    val remark: String? = null,
) {
    val timeLabel: String?
        get() = when {
            startTime != null && endTime != null -> "$startTime - $endTime"
            startTime != null -> startTime
            endTime != null -> endTime
            else -> null
        }

    /** 考核形式 · 考试形式，两项都有时用间隔点连起来。 */
    val methodLabel: String?
        get() = listOfNotNull(assessmentForm, paperMode)
            .distinct()
            .joinToString(" · ")
            .ifEmpty { null }

    /** 用于识别同一场考试的行 key（课程 + 日期 + 时间）。 */
    val key: String
        get() = buildString {
            append(courseName)
            append('|')
            append(date?.toString().orEmpty())
            append('|')
            append(startTime.orEmpty())
            append('|')
            append(room.orEmpty())
        }
}

data class ExamData(
    val termCode: String,
    val termName: String,
    val entries: List<ExamEntry>,
    val fetchedAt: Long = 0L,
) {
    /** 今天及以后的第一场；全考完时为 null。 */
    fun nextFrom(today: LocalDate): ExamEntry? =
        entries.firstOrNull { entry ->
            val date = entry.date ?: return@firstOrNull false
            !date.isBefore(today)
        }
}

data class ExamUiState(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val data: ExamData? = null,
    val error: String? = null,
    val notice: String? = null,
)

/** 解析或接口异常；[sessionExpired] 让上层去重建教务会话。 */
class ExamException(
    message: String,
    val sessionExpired: Boolean = false,
) : Exception(message)
