package com.wyutools.app.schedule

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

class ScheduleParseException(
    message: String,
    val sessionExpired: Boolean = false,
) : Exception(message)

/**
 * 教务系统 xsgrkb/getCalendarWeekDatas 的解析器。
 *
 * 不同学校部署的字段略有差异：星期可能是 jsxq（周一起）/ xq（Java 周日起），
 * 节次可能是 jcdm2 / jcdm / ps+pe，时间字段可能带完整日期。
 * 这里按优先级择一，缺字段时再用标准节次表反推。
 */
object ScheduleParser {
    private val datePattern = Regex("(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})")
    private val timePattern = Regex("(\\d{1,2}):(\\d{2})")
    private val rangePattern = Regex("(\\d+)\\s*[-~至—－]\\s*(\\d+)")
    private val numberPattern = Regex("\\d+")
    private val binaryWeeksPattern = Regex("^[01]{8,}$")

    fun parse(raw: String, termCode: String): ScheduleData {
        val text = raw.trim()
        if (text.isEmpty()) throw ScheduleParseException("课表数据为空，请稍后重试。")
        if (text.startsWith("<")) {
            if (text.contains("login-form") || text.contains("pwdEncryptSalt")) {
                throw ScheduleParseException("登录已失效，请重新登录。", sessionExpired = true)
            }
            throw ScheduleParseException("教务系统返回了异常页面，请稍后重试。")
        }
        val root = runCatching { JSONObject(text) }.getOrNull()
            ?: throw ScheduleParseException("课表数据格式异常，请稍后重试。")

        val code = root.optInt("code", 0)
        if (code < 0) {
            if (code == -401) {
                throw ScheduleParseException("登录已失效，请重新登录。", sessionExpired = true)
            }
            throw ScheduleParseException(
                optText(root, "message", "msg", "error") ?: "课表查询失败（$code）。",
            )
        }
        val success = root.opt("success")
        if (success == false || success?.toString()?.equals("false", true) == true) {
            throw ScheduleParseException(
                optText(root, "message", "msg", "error") ?: "课表查询失败。",
            )
        }

        val rows = extractRows(root)
        val parsed = ArrayList<ParsedRow>()
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            parsed.addAll(parseRow(row))
        }
        if (rows.length() > 0 && parsed.isEmpty()) {
            throw ScheduleParseException("课表数据字段无法识别，请稍后重试。")
        }

        val courses = mergeCourses(parsed)
        val referenceMonday = inferReferenceMonday(parsed, termCode)
        val sections = buildSections(courses)

        return ScheduleData(
            termCode = termCode,
            termName = ScheduleTerm.displayName(termCode),
            courses = courses,
            sections = sections,
            referenceMonday = referenceMonday,
            fetchedAt = System.currentTimeMillis(),
        )
    }

    private fun extractRows(root: JSONObject): JSONArray {
        val value = root.opt("data") ?: root.opt("rows") ?: return JSONArray()
        if (value is JSONArray) return value
        if (value is JSONObject) {
            if (value.has("kcmc")) return JSONArray().put(value)
            value.optJSONArray("list")?.let { return it }
            value.optJSONArray("rows")?.let { return it }
            value.optJSONArray("records")?.let { return it }
            value.optJSONArray("data")?.let { return it }
        }
        return JSONArray()
    }

    private fun parseRow(row: JSONObject): List<ParsedRow> {
        val name = optText(row, "kcmc", "kcName", "courseName", "kcmcDisplay") ?: return emptyList()
        val day = resolveDay(row) ?: return emptyList()
        val times = resolveTimes(row)
        val range = resolveSections(row, times) ?: return emptyList()
        val weeks = parseWeeks(row.opt("zc"))
            ?: parseWeeks(row.opt("weeks"))
            ?: parseWeeks(row.opt("week"))
            ?: DEFAULT_WEEKS

        val startTime = times.start ?: ScheduleSections.defaultStart(range.first)
        val endTime = times.end ?: ScheduleSections.defaultEnd(range.last)
        val teacher = optText(row, "teaxms", "teaxm", "jsxm", "teacher").orEmpty()
        val rooms = resolveRooms(row, weeks)
        return listOf(
            ParsedRow(
                name = name,
                teacher = teacher,
                room = rooms.fallback,
                roomWeeks = rooms.byWeek,
                dayOfWeek = day,
                startSection = range.first,
                endSection = range.last,
                startTime = startTime.orEmpty(),
                endTime = endTime.orEmpty(),
                weeks = weeks,
                date = times.date,
            ),
        )
    }

    /**
     * 五邑教务用 jxcdmc2 给出权威的按周地点，例如
     * “北主楼201-9,北主楼201-10,北主楼805-7,北主楼805-8”；
     * 地点名本身可能带连字符，因此只在最后一个“-数字”处拆分。
     * 空地点（如“-14”）表示该周尚未分配教室，保留空字符串以免回退成其他周的教室。
     */
    private fun resolveRooms(row: JSONObject, courseWeeks: Set<Int>): Rooms {
        val detailed = optText(row, "jxcdmc2")
        if (detailed != null) {
            val byWeek = parseRoomWeeks(detailed, courseWeeks)
            if (byWeek.isNotEmpty()) {
                return Rooms(fallback = mostCommonRoom(byWeek), byWeek = byWeek)
            }
        }
        val raw = optText(row, "jxcdmc", "zdjxcdmc", "cdmc", "classroom").orEmpty()
        val annotated = splitRoomsByWeeks(raw, courseWeeks)
        val single = annotated.singleOrNull()
        if (annotated.size > 1 || (single != null && single.room != raw)) {
            val byWeek = LinkedHashMap<Int, String>()
            for (entry in annotated) {
                for (week in entry.weeks) byWeek.putIfAbsent(week, entry.room)
            }
            if (byWeek.isNotEmpty()) {
                return Rooms(fallback = mostCommonRoom(byWeek), byWeek = byWeek)
            }
        }
        return Rooms(fallback = raw, byWeek = emptyMap())
    }

    private fun parseRoomWeeks(raw: String, courseWeeks: Set<Int>): Map<Int, String> {
        val byWeek = LinkedHashMap<Int, String>()
        for (token in raw.split(',', '，', ';', '；')) {
            val text = token.trim()
            if (text.isEmpty()) continue
            val dash = text.lastIndexOf('-')
            if (dash < 0) continue
            val week = text.substring(dash + 1).trim()
                .removeSuffix("周")
                .toIntOrNull()
                ?: continue
            if (week !in 1..30 || week !in courseWeeks) continue
            byWeek[week] = text.substring(0, dash).trim()
        }
        return byWeek
    }

    private fun mostCommonRoom(byWeek: Map<Int, String>): String =
        byWeek.values
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value })
            ?.key
            .orEmpty()

    /**
     * 教务会把同一门课的多段上课地点写在一个字段里，例如
     * “北主楼201 [9-13,16-17周] 北主楼805 [7-8周]”。
     * 按地点拆成多个带各自周次的条目，周视图才能只显示当周对应的教室。
     */
    private fun splitRoomsByWeeks(raw: String, courseWeeks: Set<Int>): List<RoomWeeks> {
        val text = raw.trim()
        if (text.isEmpty()) return listOf(RoomWeeks("", courseWeeks))
        val matches = roomAnnotationPattern.findAll(text)
            .filter { match ->
                val inner = match.groupValues[1]
                inner.contains("周") || rangePattern.containsMatchIn(inner)
            }
            .toList()
        if (matches.isEmpty()) return listOf(RoomWeeks(text, courseWeeks))

        val entries = LinkedHashMap<String, MutableSet<Int>>()
        var cursor = 0
        for (match in matches) {
            val room = trimRoomToken(text.substring(cursor, match.range.first))
            cursor = match.range.last + 1
            if (room.isEmpty()) continue
            val annotatedWeeks = parseWeeks(match.groupValues[1]) ?: continue
            val weeks = (annotatedWeeks intersect courseWeeks).ifEmpty { annotatedWeeks }
            entries.getOrPut(room) { LinkedHashSet() }.addAll(weeks)
        }
        val trailing = trimRoomToken(text.substring(cursor))
        if (trailing.isNotEmpty() && entries.isNotEmpty()) {
            val covered = entries.values.flatten().toSet()
            val remaining = courseWeeks - covered
            if (remaining.isNotEmpty()) {
                entries.getOrPut(trailing) { LinkedHashSet() }.addAll(remaining)
            }
        }
        if (entries.isEmpty()) return listOf(RoomWeeks(text, courseWeeks))
        return entries.map { (room, weeks) -> RoomWeeks(room, weeks.toSet()) }
    }

    private fun trimRoomToken(raw: String): String {
        fun isSeparator(char: Char): Boolean =
            char.isWhitespace() || char in ROOM_SEPARATORS
        var start = 0
        var end = raw.length
        while (start < end && isSeparator(raw[start])) start++
        while (end > start && isSeparator(raw[end - 1])) end--
        return raw.substring(start, end)
    }

    /**
     * 五邑教务的 xq 是周一起（1=周一），校内 App 导入课表时也直接使用该字段。
     * 响应里的 jsxq 在部分学期按周日起编码（1=周日、2=周一…），若先读它会让
     * 整张课表右移一天。因此 xq 优先，jsxq 与其余数字字段按 Java 日历回退。
     * 纯中文星期优先于数字，避免编码差异。
     */
    private fun resolveDay(row: JSONObject): Int? {
        for (key in DAY_TEXT_KEYS) {
            parseChineseDay(optText(row, key) ?: continue)?.let { return it }
        }
        optText(row, "xq")?.toIntOrNull()
            ?.takeIf { it in 1..7 }
            ?.let { return it }
        optText(row, "jsxq")?.toIntOrNull()
            ?.takeIf { it in 1..7 }
            ?.let { return javaWeekdayToMonday(it) }
        for (key in DAY_ALIASES) {
            val number = optText(row, key)?.toIntOrNull() ?: continue
            if (number in 1..7) return javaWeekdayToMonday(number)
        }
        return null
    }

    private fun javaWeekdayToMonday(javaDay: Int): Int =
        if (javaDay == 1) 7 else javaDay - 1

    private fun parseChineseDay(raw: String): Int? {
        if (raw.contains("周") || raw.contains("星期") || raw.length == 1) {
            return when {
                raw.contains("一") -> 1
                raw.contains("二") -> 2
                raw.contains("三") -> 3
                raw.contains("四") -> 4
                raw.contains("五") -> 5
                raw.contains("六") -> 6
                raw.contains("日") || raw.contains("天") -> 7
                else -> null
            }
        }
        return null
    }

    private fun resolveTimes(row: JSONObject): RowTimes {
        val startRaw = optText(row, "qssj", "startTime", "start_time", "qsj")
        val endRaw = optText(row, "jssj", "endTime", "end_time", "jsj")
        return RowTimes(
            start = extractTime(startRaw),
            end = extractTime(endRaw),
            date = extractDate(startRaw),
        )
    }

    private fun extractTime(raw: String?): String? {
        val match = timePattern.find(raw ?: return null) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return String.format(Locale.US, "%02d:%02d", hour, minute)
    }

    private fun extractDate(raw: String?): LocalDate? {
        val match = datePattern.find(raw ?: return null) ?: return null
        val year = match.groupValues[1].toIntOrNull() ?: return null
        val month = match.groupValues[2].toIntOrNull() ?: return null
        val day = match.groupValues[3].toIntOrNull() ?: return null
        return runCatching { LocalDate.of(year, month, day) }.getOrNull()
    }

    private fun resolveSections(row: JSONObject, times: RowTimes): IntRange? {
        parseSectionField(row.opt("jcdm2"))?.let { return it.asRange() }
        parseSectionField(row.opt("jcdm"))?.let { return it.asRange() }
        val ps = optInt(row, "ps")
        val pe = optInt(row, "pe")
        if (ps != null && ps > 0) {
            val end = if (pe != null && pe >= ps) pe else ps
            return normalizeRange(ps, end)
        }

        val startSection = times.start?.let(ScheduleSections::minutesOf)
            ?.let(ScheduleSections::nearestStartSection)
        val endSection = times.end?.let(ScheduleSections::minutesOf)
            ?.let(ScheduleSections::nearestEndSection)
        if (startSection != null && endSection != null) {
            return normalizeRange(startSection, endSection)
        }
        if (startSection != null) return startSection..startSection
        if (endSection != null) return endSection..endSection
        return null
    }

    private fun parseSectionField(value: Any?): List<Int>? {
        if (value == null || value == JSONObject.NULL) return null
        if (value is JSONArray) {
            val numbers = ArrayList<Int>()
            for (index in 0 until value.length()) {
                val number = value.optInt(index, -1)
                if (number in 1..30) numbers.add(number)
            }
            return numbers.distinct().sorted().takeIf { it.isNotEmpty() }
        }
        val text = value.toString().trim()
        if (text.isEmpty() || text.equals("null", true)) return null

        if (text.matches(Regex("^\\d{4}$"))) {
            val start = text.take(2).toIntOrNull()
            val end = text.drop(2).toIntOrNull()
            if (start != null && end != null && start > 0 && end >= start && end <= 30) {
                return (start..end).toList()
            }
        }

        val numbers = ArrayList<Int>()
        var match = rangePattern.find(text)
        while (match != null) {
            val start = match.groupValues[1].toIntOrNull()
            val end = match.groupValues[2].toIntOrNull()
            if (start != null && end != null && start in 1..30 && end in start..30) {
                numbers.addAll(start..end)
            }
            match = rangePattern.find(text, match.range.last + 1)
        }
        if (numbers.isEmpty()) {
            numberPattern.findAll(text).forEach {
                it.value.toIntOrNull()?.takeIf { n -> n in 1..30 }?.let(numbers::add)
            }
        }
        return numbers.distinct().sorted().takeIf { it.isNotEmpty() }
    }

    private fun normalizeRange(start: Int, end: Int): IntRange? {
        if (start !in 1..30 || end !in 1..30) return null
        val low = minOf(start, end)
        val high = maxOf(start, end)
        return if (high - low > 12) low..low else low..high
    }

    private fun parseWeeks(value: Any?): Set<Int>? {
        if (value == null || value == JSONObject.NULL) return null
        if (value is JSONArray) {
            val weeks = HashSet<Int>()
            for (index in 0 until value.length()) {
                val number = value.optInt(index, 0)
                if (number in 1..30) weeks.add(number)
            }
            return weeks.takeIf { it.isNotEmpty() }
        }
        val raw = value.toString().trim()
        if (raw.isEmpty() || raw.equals("null", true)) return null

        if (binaryWeeksPattern.matches(raw)) {
            val weeks = raw.indices.filter { raw[it] == '1' }.map { it + 1 }
                .filter { it in 1..30 }.toSet()
            return weeks.takeIf { it.isNotEmpty() }
        }
        if (!numberPattern.containsMatchIn(raw)) return null

        val parity = when {
            raw.contains("单") -> 1
            raw.contains("双") -> 0
            else -> -1
        }
        val weeks = LinkedHashSet<Int>()
        var match = rangePattern.find(raw)
        while (match != null) {
            val start = match.groupValues[1].toIntOrNull()
            val end = match.groupValues[2].toIntOrNull()
            if (start != null && end != null && start in 1..30 && end >= start) {
                for (week in start..minOf(end, 30)) {
                    if (parity < 0 || week % 2 == parity) weeks.add(week)
                }
            }
            match = rangePattern.find(raw, match.range.last + 1)
        }
        if (weeks.isEmpty()) {
            numberPattern.findAll(raw).forEach {
                val week = it.value.toIntOrNull() ?: return@forEach
                if (week in 1..30 && (parity < 0 || week % 2 == parity)) weeks.add(week)
            }
        }
        return weeks.takeIf { it.isNotEmpty() }
    }

    private fun mergeCourses(rows: List<ParsedRow>): List<ScheduleCourse> {
        val merged = LinkedHashMap<String, MutableCourse>()
        for (row in rows) {
            val key = listOf(
                row.name,
                row.teacher,
                row.dayOfWeek,
                row.startSection,
                row.endSection,
            ).joinToString("|")
            val existing = merged[key]
            if (existing == null) {
                merged[key] = MutableCourse(
                    id = key,
                    name = row.name,
                    teacher = row.teacher,
                    room = row.room,
                    roomWeeks = row.roomWeeks.toMutableMap(),
                    dayOfWeek = row.dayOfWeek,
                    startSection = row.startSection,
                    endSection = row.endSection,
                    startTime = row.startTime,
                    endTime = row.endTime,
                    weeks = row.weeks.toMutableSet(),
                )
            } else {
                existing.weeks.addAll(row.weeks)
                existing.roomWeeks.putAll(row.roomWeeks)
                if (existing.room.isBlank() && row.room.isNotBlank()) {
                    existing.room = row.room
                }
                if (existing.startTime.isBlank()) existing.startTime = row.startTime
                if (existing.endTime.isBlank()) existing.endTime = row.endTime
            }
        }
        return merged.values.map { it.toCourse() }
            .sortedWith(compareBy({ it.dayOfWeek }, { it.startSection }, { it.name }))
    }

    private fun inferReferenceMonday(rows: List<ParsedRow>, termCode: String): LocalDate? {
        if (rows.isEmpty()) return null
        val heuristic = ScheduleTerm.startMonday(termCode)
        val candidates = ArrayList<LocalDate>()
        for (row in rows) {
            val date = row.date ?: continue
            val minWeek = row.weeks.minOrNull() ?: continue
            if (minWeek < 1) continue
            val offset = (minWeek - 1) * 7L + (row.dayOfWeek - 1)
            val candidate = date.minusDays(offset)
            val distance = ChronoUnit.DAYS.between(heuristic, candidate)
            if (distance in -30..30) candidates.add(candidate)
        }
        if (candidates.isEmpty()) return null
        return candidates.groupingBy { it }.eachCount()
            .maxWithOrNull(
                compareBy<Map.Entry<LocalDate, Int>> { it.value }
                    .thenByDescending { it.key.toEpochDay() },
            )
            ?.key
    }

    private fun buildSections(courses: List<ScheduleCourse>): List<ScheduleSection> {
        if (courses.isEmpty()) {
            return (1..ScheduleSections.maxDefaultSection).map { number ->
                ScheduleSection(
                    number = number,
                    start = ScheduleSections.defaultStart(number).orEmpty(),
                    end = ScheduleSections.defaultEnd(number).orEmpty(),
                )
            }
        }
        val starts = HashMap<Int, MutableList<String>>()
        val ends = HashMap<Int, MutableList<String>>()
        for (course in courses) {
            if (course.startTime.isNotBlank()) {
                starts.getOrPut(course.startSection) { ArrayList() }.add(course.startTime)
            }
            if (course.endTime.isNotBlank()) {
                ends.getOrPut(course.endSection) { ArrayList() }.add(course.endTime)
            }
        }

        val lastSection = courses.maxOf { it.endSection }
        var previousEnd: String? = null
        return (1..lastSection).map { number ->
            val start = mostCommon(starts[number])
                ?: ScheduleSections.defaultStart(number)
                ?: previousEnd?.let { addMinutes(it, 10) }
                ?: "--:--"
            val end = mostCommon(ends[number])
                ?: ScheduleSections.defaultEnd(number)
                ?: addMinutes(start, 45)
                ?: "--:--"
            previousEnd = end
            ScheduleSection(number = number, start = start, end = end)
        }
    }

    private fun mostCommon(values: List<String>?): String? =
        values?.groupingBy { it }?.eachCount()
            ?.maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value })
            ?.key

    private fun addMinutes(time: String, delta: Int): String? {
        val minutes = ScheduleSections.minutesOf(time) ?: return null
        val total = (minutes + delta).coerceIn(0, 23 * 60 + 59)
        return String.format(Locale.US, "%02d:%02d", total / 60, total % 60)
    }

    private fun optText(row: JSONObject, vararg keys: String): String? {
        for (key in keys) {
            if (!row.has(key) || row.isNull(key)) continue
            val value = row.opt(key) ?: continue
            val text = when (value) {
                is String -> value.trim()
                is Number, is Boolean -> value.toString()
                else -> continue
            }
            if (text.isEmpty()) continue
            if (text.equals("null", true) || text.equals("undefined", true)) continue
            if (text == "[]" || text == "{}") continue
            return text
        }
        return null
    }

    private fun optInt(row: JSONObject, key: String): Int? {
        val text = optText(row, key) ?: return null
        return text.toIntOrNull()
    }

    private fun List<Int>.asRange(): IntRange? = normalizeRange(first(), last())

    private data class RowTimes(
        val start: String?,
        val end: String?,
        val date: LocalDate?,
    )

    private data class RoomWeeks(
        val room: String,
        val weeks: Set<Int>,
    )

    private data class Rooms(
        val fallback: String,
        val byWeek: Map<Int, String>,
    )

    private data class ParsedRow(
        val name: String,
        val teacher: String,
        val room: String,
        val roomWeeks: Map<Int, String>,
        val dayOfWeek: Int,
        val startSection: Int,
        val endSection: Int,
        val startTime: String,
        val endTime: String,
        val weeks: Set<Int>,
        val date: LocalDate?,
    )

    private class MutableCourse(
        val id: String,
        val name: String,
        val teacher: String,
        var room: String,
        val roomWeeks: MutableMap<Int, String>,
        val dayOfWeek: Int,
        val startSection: Int,
        val endSection: Int,
        var startTime: String,
        var endTime: String,
        val weeks: MutableSet<Int>,
    ) {
        fun toCourse() = ScheduleCourse(
            id = id,
            name = name,
            teacher = teacher,
            room = room,
            dayOfWeek = dayOfWeek,
            startSection = startSection,
            endSection = endSection,
            startTime = startTime,
            endTime = endTime,
            weeks = weeks.toSet(),
            roomWeeks = roomWeeks.toMap(),
        )
    }

    private val DAY_TEXT_KEYS =
        listOf("xq", "jsxq", "qsxq", "day", "weekday", "xqmc", "jsxqmc")
    private val DAY_ALIASES = listOf("qsxq", "day", "weekday")
    private val DEFAULT_WEEKS = (1..20).toSet()
    private val roomAnnotationPattern =
        Regex("[\\[【(（]([^\\]】)）]*\\d[^\\]】)）]*)[\\]】)）]")
    private const val ROOM_SEPARATORS = ",，;；、/|·"
}
