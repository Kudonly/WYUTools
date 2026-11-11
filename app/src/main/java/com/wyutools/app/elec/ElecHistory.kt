package com.wyutools.app.elec

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import kotlin.math.abs

const val ELEC_PRICE_PER_KWH = 0.63

data class ElecSnapshot(
    val recordedAt: Long,
    val remainingKwh: Double,
    val usedKwh: Double?,
    val updatedAt: String?,
)

data class ElecStats(
    val sampleCount: Int = 0,
    val dailyUsageKwh: Double? = null,
    val daysLeft: Double? = null,
    val monthUsageKwh: Double? = null,
    val monthCostYuan: Double? = null,
)

fun appendSnapshot(
    history: List<ElecSnapshot>,
    info: ElecInfo,
    now: Long = System.currentTimeMillis(),
): List<ElecSnapshot> {
    val last = history.lastOrNull()
    val sameReading = last != null &&
        last.updatedAt == info.updatedAt &&
        abs(last.remainingKwh - info.remainingKwh) < 0.001 &&
        usedReadingEquals(last.usedKwh, info.usedKwh)
    val next = if (sameReading) history else history + ElecSnapshot(
        recordedAt = now,
        remainingKwh = info.remainingKwh,
        usedKwh = info.usedKwh,
        updatedAt = info.updatedAt,
    )
    return if (next.size > 400) next.takeLast(400) else next
}

fun computeStats(
    history: List<ElecSnapshot>,
    remainingKwh: Double,
    now: Long = System.currentTimeMillis(),
): ElecStats {
    val samples = history.sortedBy { it.recordedAt }
    val weekAgo = now - 7L * 24 * 60 * 60 * 1000
    val daily = dailyUsage(samples.filter { it.recordedAt >= weekAgo }) ?: dailyUsage(samples)
    val monthUsage = usageDelta(samples.filter { it.recordedAt >= monthStart(now) })
    return ElecStats(
        sampleCount = samples.size,
        dailyUsageKwh = daily,
        daysLeft = daily?.takeIf { it > 0.01 }?.let { remainingKwh / it },
        monthUsageKwh = monthUsage,
        monthCostYuan = monthUsage?.times(ELEC_PRICE_PER_KWH),
    )
}

fun encodeHistory(history: List<ElecSnapshot>): String {
    val array = JSONArray()
    history.forEach { snapshot ->
        array.put(JSONObject().apply {
            put("t", snapshot.recordedAt)
            put("r", snapshot.remainingKwh)
            snapshot.usedKwh?.let { put("u", it) }
            snapshot.updatedAt?.takeIf { it.isNotBlank() }?.let { put("at", it) }
        })
    }
    return array.toString()
}

/** The last reading that came back for a room, kept on disk for offline use. */
data class ElecCachedInfo(
    val info: ElecInfo,
    /** Wall-clock time this reading was fetched, used to label it as “上次查询”. */
    val fetchedAt: Long,
)

fun encodeCachedInfo(cached: ElecCachedInfo): String = JSONObject().apply {
    put("t", cached.fetchedAt)
    put("r", cached.info.remainingKwh)
    val info = cached.info
    info.usedKwh?.let { put("u", it) }
    info.schoolName?.let { put("school", it) }
    info.buildingName?.let { put("building", it) }
    info.roomName?.let { put("room", it) }
    info.updatedAt?.let { put("at", it) }
    info.userTypeName?.let { put("type", it) }
    info.status?.let { put("status", it) }
}.toString()

fun decodeCachedInfo(raw: String?): ElecCachedInfo? {
    if (raw.isNullOrBlank()) return null
    return try {
        val item = JSONObject(raw)
        val fetchedAt = item.optLong("t", 0L)
        val remaining = item.optDouble("r", Double.NaN)
        if (fetchedAt <= 0L || remaining.isNaN()) return null
        ElecCachedInfo(
            info = ElecInfo(
                remainingKwh = remaining,
                usedKwh = if (item.has("u")) item.optDouble("u") else null,
                schoolName = item.optString("school", "").ifBlank { null },
                buildingName = item.optString("building", "").ifBlank { null },
                roomName = item.optString("room", "").ifBlank { null },
                updatedAt = item.optString("at", "").ifBlank { null },
                userTypeName = item.optString("type", "").ifBlank { null },
                status = if (item.has("status")) item.optInt("status") else null,
            ),
            fetchedAt = fetchedAt,
        )
    } catch (e: Exception) {
        null
    }
}

fun decodeHistory(raw: String?): List<ElecSnapshot> {
    if (raw.isNullOrBlank()) return emptyList()
    return try {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val recordedAt = item.optLong("t", 0L)
                val remaining = item.optDouble("r", Double.NaN)
                if (recordedAt <= 0L || remaining.isNaN()) continue
                add(
                    ElecSnapshot(
                        recordedAt = recordedAt,
                        remainingKwh = remaining,
                        usedKwh = if (item.has("u") && !item.isNull("u")) item.optDouble("u") else null,
                        updatedAt = item.optString("at", "").ifBlank { null },
                    )
                )
            }
        }.sortedBy { it.recordedAt }
    } catch (e: Exception) {
        emptyList()
    }
}

private fun usedReadingEquals(a: Double?, b: Double?): Boolean =
    (a == null && b == null) || (a != null && b != null && abs(a - b) < 0.001)

private fun usageDelta(samples: List<ElecSnapshot>): Double? {
    val withUsed = samples.filter { it.usedKwh != null }
    if (withUsed.size < 2) return null
    val delta = withUsed.last().usedKwh!! - withUsed.first().usedKwh!!
    return if (delta >= 0) delta else null
}

private fun dailyUsage(samples: List<ElecSnapshot>): Double? {
    val withUsed = samples.filter { it.usedKwh != null }
    if (withUsed.size < 2) return null
    val first = withUsed.first()
    val last = withUsed.last()
    val hours = (last.recordedAt - first.recordedAt) / 3_600_000.0
    if (hours < 6.0) return null
    val delta = last.usedKwh!! - first.usedKwh!!
    if (delta < 0) return null
    return delta / (hours / 24.0)
}

private fun monthStart(now: Long): Long {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = now
    calendar.set(Calendar.DAY_OF_MONTH, 1)
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}
