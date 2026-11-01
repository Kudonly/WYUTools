package com.wyutools.app.schedule

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

data class ScheduleSection(
    val number: Int,
    val start: String,
    val end: String,
)

data class ScheduleCourse(
    val id: String,
    val name: String,
    val teacher: String,
    val room: String,
    val dayOfWeek: Int,
    val startSection: Int,
    val endSection: Int,
    val startTime: String,
    val endTime: String,
    val weeks: Set<Int>,
    val roomWeeks: Map<Int, String> = emptyMap(),
) {
    /** 指定周次的上课地点；空字符串表示教务在该周没有分配地点。 */
    fun roomForWeek(week: Int?): String {
        if (week == null || roomWeeks.isEmpty()) return room
        return roomWeeks[week] ?: room
    }

    /** 详情页的完整地点摘要，例如“北主楼201 [9-13,16-17周] 北主楼805 [7-8周]”。 */
    fun roomSummary(): String {
        if (roomWeeks.isEmpty()) return room
        val byRoom = LinkedHashMap<String, MutableList<Int>>()
        roomWeeks.entries.sortedBy { it.key }.forEach { (week, name) ->
            byRoom.getOrPut(name) { ArrayList() }.add(week)
        }
        if (byRoom.size == 1) {
            return byRoom.keys.first().ifBlank { "地点待定" }
        }
        return byRoom.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, MutableList<Int>>> { it.value.size }
                    .thenBy { it.value.first() },
            )
            .joinToString(" ") { (name, weeks) ->
                val label = name.ifBlank { "地点待定" }
                "$label [${formatWeekRuns(weeks)}周]"
            }
    }

    fun startMinutes(): Int? =
        ScheduleSections.minutesOf(startTime)
            ?: ScheduleSections.defaultStartMinutes(startSection)

    fun endMinutes(): Int? =
        ScheduleSections.minutesOf(endTime)
            ?: ScheduleSections.defaultEndMinutes(endSection)

    fun isOngoingAt(minutes: Int): Boolean {
        val start = startMinutes() ?: return false
        val end = endMinutes() ?: return false
        return minutes in start until end
    }

    fun hasEndedAt(minutes: Int): Boolean {
        val end = endMinutes() ?: return false
        return minutes >= end
    }

    fun compactTimeText(): String {
        val start = startTime.ifBlank {
            ScheduleSections.defaultStart(startSection).orEmpty()
        }
        val end = endTime.ifBlank {
            ScheduleSections.defaultEnd(endSection).orEmpty()
        }
        return when {
            start.isNotBlank() && end.isNotBlank() -> "$start – $end"
            start.isNotBlank() -> "$start 开始"
            startSection == endSection -> "第 $startSection 节"
            else -> "第 $startSection-$endSection 节"
        }
    }
}

data class ScheduleFocus(
    val course: ScheduleCourse,
    val ongoing: Boolean,
)

private fun formatWeekRuns(weeks: List<Int>): String {
    if (weeks.isEmpty()) return ""
    val ranges = ArrayList<IntRange>()
    var start = weeks.first()
    var previous = start
    for (week in weeks.drop(1)) {
        if (week == previous + 1) {
            previous = week
        } else {
            ranges += start..previous
            start = week
            previous = week
        }
    }
    ranges += start..previous
    return ranges.joinToString(",") { range ->
        if (range.first == range.last) "${range.first}" else "${range.first}-${range.last}"
    }
}

data class ScheduleData(
    val termCode: String,
    val termName: String,
    val courses: List<ScheduleCourse>,
    val sections: List<ScheduleSection>,
    val referenceMonday: LocalDate?,
    val fetchedAt: Long,
) {
    val maxWeek: Int
        get() = maxOf(20, courses.asSequence().flatMap { it.weeks.asSequence() }.maxOrNull() ?: 0)
            .coerceAtMost(30)

    fun coursesOn(dayOfWeek: Int, week: Int): List<ScheduleCourse> =
        courses.filter { it.dayOfWeek == dayOfWeek && week in it.weeks }
            .sortedBy { it.startSection }

    fun ongoingCourse(
        anchor: WeekAnchor?,
        now: LocalDateTime = LocalDateTime.now(),
    ): ScheduleCourse? {
        val week = currentWeek(anchor, now.toLocalDate())
        val minutes = now.hour * 60 + now.minute
        return coursesOn(now.dayOfWeek.value, week).firstOrNull { it.isOngoingAt(minutes) }
    }

    fun nextCourse(
        anchor: WeekAnchor?,
        now: LocalDateTime = LocalDateTime.now(),
    ): ScheduleCourse? {
        val week = currentWeek(anchor, now.toLocalDate())
        val minutes = now.hour * 60 + now.minute
        return coursesOn(now.dayOfWeek.value, week).firstOrNull { course ->
            val start = course.startMinutes() ?: return@firstOrNull false
            start > minutes
        }
    }

    fun focusCourse(
        anchor: WeekAnchor?,
        now: LocalDateTime = LocalDateTime.now(),
    ): ScheduleFocus? =
        ongoingCourse(anchor, now)?.let { ScheduleFocus(it, ongoing = true) }
            ?: nextCourse(anchor, now)?.let { ScheduleFocus(it, ongoing = false) }

    fun weekOneMonday(anchor: WeekAnchor?): LocalDate {
        if (anchor != null && anchor.termCode == termCode) {
            return anchor.monday.minusWeeks((anchor.week - 1).toLong())
        }
        return referenceMonday ?: ScheduleTerm.startMonday(termCode)
    }

    fun currentWeek(anchor: WeekAnchor?, today: LocalDate = LocalDate.now()): Int {
        val monday = ScheduleTerm.mondayOf(today)
        val offset = ChronoUnit.WEEKS.between(weekOneMonday(anchor), monday).toInt()
        return (offset + 1).coerceIn(1, maxWeek)
    }

    fun mondayOfWeek(anchor: WeekAnchor?, week: Int): LocalDate =
        weekOneMonday(anchor).plusWeeks((week - 1).toLong())
}

data class WeekAnchor(
    val termCode: String,
    val monday: LocalDate,
    val week: Int,
)

data class ScheduleNotice(
    val message: String,
    val error: Boolean = false,
)

data class ScheduleUiState(
    val data: ScheduleData? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val showWeekend: Boolean = false,
    val selectedWeek: Int = 1,
    val weekAnchor: WeekAnchor? = null,
    val notice: ScheduleNotice? = null,
)

object ScheduleTerm {
    /**
     * 教务本科接口只给学期代码，学期按 8 月至次年 1 月、2 月至 7 月划分。
     * 1 月仍属于上一学年第一学期。
     */
    fun currentCode(today: LocalDate = LocalDate.now()): String {
        val month = today.monthValue
        val startYear = when {
            month == 1 -> today.year - 1
            month in 2..7 -> today.year - 1
            else -> today.year
        }
        val semester = if (month in 2..7) 2 else 1
        return String.format(Locale.US, "%d%02d", startYear, semester)
    }

    fun displayName(code: String): String {
        val year = code.take(4).toIntOrNull() ?: return code
        return when (code.drop(4)) {
            "02" -> "$year-${year + 1} 第二学期"
            "01" -> "$year-${year + 1} 第一学期"
            else -> code
        }
    }

    fun startMonday(code: String): LocalDate {
        val year = code.take(4).toIntOrNull() ?: LocalDate.now().year
        val anchor = if (code.drop(4) == "02") {
            LocalDate.of(year + 1, 3, 1)
        } else {
            LocalDate.of(year, 9, 1)
        }
        return mondayOf(anchor)
    }

    fun mondayOf(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
}

object ScheduleSections {
    /**
     * 五邑大学教务的 14 节作息，取自校方课表页的大节起止时间。
     * 第 13、14 节是中午时段，在课表里排在最后，仅作为缺失字段时的回退。
     */
    private val defaultTimes = listOf(
        "08:15" to "09:05",
        "09:05" to "09:50",
        "10:10" to "11:00",
        "11:00" to "11:45",
        "14:30" to "15:20",
        "15:20" to "16:05",
        "16:25" to "17:15",
        "17:15" to "18:00",
        "19:30" to "20:20",
        "20:20" to "21:05",
        "21:20" to "22:15",
        "22:15" to "23:00",
        "12:30" to "13:20",
        "13:20" to "14:05",
    )

    val maxDefaultSection: Int
        get() = defaultTimes.size

    fun defaultStart(section: Int): String? = defaultTimes.getOrNull(section - 1)?.first

    fun defaultEnd(section: Int): String? = defaultTimes.getOrNull(section - 1)?.second

    fun defaultStartMinutes(section: Int): Int? =
        defaultStart(section)?.let(::minutesOf)

    fun defaultEndMinutes(section: Int): Int? =
        defaultEnd(section)?.let(::minutesOf)

    fun minutesOf(time: String): Int? {
        val match = Regex("(\\d{1,2}):(\\d{2})").find(time) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    fun nearestStartSection(minutes: Int): Int? {
        val candidates = defaultTimes.indices.mapNotNull { index ->
            val start = defaultStartMinutes(index + 1) ?: return@mapNotNull null
            val distance = kotlin.math.abs(start - minutes)
            if (distance > 35) null else index + 1 to distance
        }
        return candidates.minByOrNull { it.second }?.first
    }

    fun nearestEndSection(minutes: Int): Int? {
        val candidates = defaultTimes.indices.mapNotNull { index ->
            val end = defaultEndMinutes(index + 1) ?: return@mapNotNull null
            val distance = kotlin.math.abs(end - minutes)
            if (distance > 35) null else index + 1 to distance
        }
        return candidates.minByOrNull { it.second }?.first
    }
}

const val SCHEDULE_TAB_DAILY = 0
const val SCHEDULE_TAB_WEEKLY = 1

val scheduleWeekdayLabels = listOf("一", "二", "三", "四", "五", "六", "日")
