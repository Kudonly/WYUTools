package com.wyutools.app.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 成绩/考试页的「更新于」角标与充值记录共用的时间样式。 */
private val ClockStampFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.CHINA).withZone(ZoneId.systemDefault())

private val WeekdayNames = listOf("一", "二", "三", "四", "五", "六", "日")

/**
 * 周几。[short] 为 true 给「周一」，false 给「星期一」。
 *
 * 以前课表、考试、体育馆各写一份 `when (dayOfWeek.value)`，改文案要改三处。
 */
internal fun weekdayLabel(date: LocalDate, short: Boolean = true): String {
    val name = WeekdayNames[date.dayOfWeek.value - 1]
    return if (short) "周$name" else "星期$name"
}

/**
 * `MM-dd HH:mm`（设备时区）。时间戳无效时返回 [fallback]。
 */
internal fun formatClockStamp(timestamp: Long, fallback: String = "—"): String =
    runCatching { ClockStampFormat.format(Instant.ofEpochMilli(timestamp)) }.getOrDefault(fallback)

/**
 * 「更新于 MM-dd HH:mm · 」——成绩页、考试页的角标前缀。没有时间戳时给空串，
 * 调用方直接 append 即可。
 */
internal fun updatedStampLabel(fetchedAt: Long): String =
    if (fetchedAt <= 0L) "" else "更新于 ${formatClockStamp(fetchedAt)} · "
