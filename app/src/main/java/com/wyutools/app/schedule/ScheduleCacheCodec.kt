package com.wyutools.app.schedule

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** 课表的本地快照，只保存展示所需字段，不含任何登录信息。 */
object ScheduleCacheCodec {
    private const val VERSION = 5

    fun encode(data: ScheduleData): String = JSONObject().apply {
        put("version", VERSION)
        put("termCode", data.termCode)
        put("termName", data.termName)
        put("fetchedAt", data.fetchedAt)
        data.referenceMonday?.let { put("referenceMonday", it.toEpochDay()) }
        put("sections", JSONArray().apply {
            data.sections.forEach { section ->
                put(
                    JSONObject()
                        .put("number", section.number)
                        .put("start", section.start)
                        .put("end", section.end),
                )
            }
        })
        put("courses", JSONArray().apply {
            data.courses.forEach { course ->
                put(
                    JSONObject()
                        .put("id", course.id)
                        .put("name", course.name)
                        .put("teacher", course.teacher)
                        .put("room", course.room)
                        .put("day", course.dayOfWeek)
                        .put("startSection", course.startSection)
                        .put("endSection", course.endSection)
                        .put("startTime", course.startTime)
                        .put("endTime", course.endTime)
                        .put("weeks", JSONArray(course.weeks.sorted()))
                        .put(
                            "roomWeeks",
                            JSONArray().apply {
                                course.roomWeeks.toSortedMap().forEach { (week, room) ->
                                    put(JSONObject().put("week", week).put("room", room))
                                }
                            },
                        ),
                )
            }
        })
    }.toString()

    fun decode(raw: String?): ScheduleData? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val root = JSONObject(raw)
            if (root.optInt("version", 0) != VERSION) return null
            val termCode = root.optString("termCode").takeIf { it.isNotBlank() } ?: return null
            val termName = root.optString("termName").takeIf { it.isNotBlank() }
                ?: ScheduleTerm.displayName(termCode)
            val fetchedAt = root.optLong("fetchedAt", 0L)
            val referenceMonday = if (root.has("referenceMonday") && !root.isNull("referenceMonday")) {
                LocalDate.ofEpochDay(root.optLong("referenceMonday"))
            } else {
                null
            }
            val sectionArray = root.optJSONArray("sections") ?: JSONArray()
            val sections = buildList {
                for (index in 0 until sectionArray.length()) {
                    val item = sectionArray.optJSONObject(index) ?: continue
                    val number = item.optInt("number", 0)
                    if (number !in 1..30) continue
                    add(
                        ScheduleSection(
                            number = number,
                            start = item.optString("start"),
                            end = item.optString("end"),
                        ),
                    )
                }
            }.sortedBy { it.number }
            if (sections.isEmpty()) return null

            val courseArray = root.optJSONArray("courses") ?: JSONArray()
            val courses = buildList {
                for (index in 0 until courseArray.length()) {
                    val item = courseArray.optJSONObject(index) ?: continue
                    val name = item.optString("name").takeIf { it.isNotBlank() } ?: continue
                    val day = item.optInt("day", 0)
                    val startSection = item.optInt("startSection", 0)
                    val endSection = item.optInt("endSection", 0)
                    if (day !in 1..7 || startSection !in 1..30 || endSection !in 1..30) continue
                    val weekArray = item.optJSONArray("weeks") ?: JSONArray()
                    val weeks = buildSet {
                        for (weekIndex in 0 until weekArray.length()) {
                            val week = weekArray.optInt(weekIndex, 0)
                            if (week in 1..30) add(week)
                        }
                    }
                    if (weeks.isEmpty()) continue
                    val roomWeekArray = item.optJSONArray("roomWeeks") ?: JSONArray()
                    val roomWeeks = buildMap {
                        for (roomIndex in 0 until roomWeekArray.length()) {
                            val entry = roomWeekArray.optJSONObject(roomIndex) ?: continue
                            val week = entry.optInt("week", 0)
                            if (week !in weeks) continue
                            put(week, entry.optString("room"))
                        }
                    }
                    add(
                        ScheduleCourse(
                            id = item.optString("id").ifBlank {
                                "$day|$startSection-$endSection|$name"
                            },
                            name = name,
                            teacher = item.optString("teacher"),
                            room = item.optString("room"),
                            dayOfWeek = day,
                            startSection = startSection,
                            endSection = endSection,
                            startTime = item.optString("startTime"),
                            endTime = item.optString("endTime"),
                            weeks = weeks,
                            roomWeeks = roomWeeks,
                        ),
                    )
                }
            }
            ScheduleData(
                termCode = termCode,
                termName = termName,
                courses = courses,
                sections = sections,
                referenceMonday = referenceMonday,
                fetchedAt = fetchedAt,
            )
        }.getOrNull()
    }
}
