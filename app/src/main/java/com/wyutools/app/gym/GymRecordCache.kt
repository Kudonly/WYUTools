package com.wyutools.app.gym

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

private const val MaxCachedRecords = 500

/**
 * The record list is long and the endpoint is slow, so a cold start used to sit
 * on an empty 我的预约 tab until the network answered — and stayed empty
 * forever when the request failed. The last successful pull is therefore kept on
 * disk, tagged with the account it belongs to so switching users never shows the
 * previous one's history.
 */
data class GymRecordCache(
    val account: String,
    val fetchedAt: Long,
    val records: List<GymRecord>,
)

fun encodeGymRecords(cache: GymRecordCache): String {
    val array = JSONArray()
    cache.records.take(MaxCachedRecords).forEach { record ->
        array.put(JSONObject().apply {
            put("w", record.wid)
            record.recordId?.let { put("rid", it) }
            put("vn", record.venueName)
            put("sl", record.slot)
            record.date?.let { put("d", it.toString()) }
            put("dr", record.dateRaw)
            record.statusCode?.let { put("sc", it) }
            put("st", record.status)
            record.payStatusCode?.let { put("ps", it) }
            record.submittedAt?.let { put("sa", it) }
            record.durationMinutes?.let { put("dm", it) }
            record.semester?.let { put("se", it) }
            put("vi", record.violation)
            put("cc", record.canCancel)
        })
    }
    return JSONObject().apply {
        put("account", cache.account)
        put("t", cache.fetchedAt)
        put("records", array)
    }.toString()
}

fun decodeGymRecords(raw: String?): GymRecordCache? {
    if (raw.isNullOrBlank()) return null
    return try {
        val root = JSONObject(raw)
        val account = root.optString("account", "")
        val fetchedAt = root.optLong("t", 0L)
        val array = root.optJSONArray("records") ?: return null
        if (account.isBlank() || fetchedAt <= 0L) return null
        val records = buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val wid = item.optString("w", "")
                if (wid.isBlank()) continue
                add(
                    GymRecord(
                        wid = wid,
                        recordId = item.optString("rid", "").ifBlank { null },
                        venueName = item.optString("vn", ""),
                        slot = item.optString("sl", ""),
                        date = item.optString("d", "").ifBlank { null }
                            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                        dateRaw = item.optString("dr", ""),
                        statusCode = item.optString("sc", "").ifBlank { null },
                        status = item.optString("st", ""),
                        payStatusCode = item.optString("ps", "").ifBlank { null },
                        submittedAt = item.optString("sa", "").ifBlank { null },
                        durationMinutes = if (item.has("dm") && !item.isNull("dm")) {
                            item.optInt("dm")
                        } else {
                            null
                        },
                        semester = item.optString("se", "").ifBlank { null },
                        violation = item.optBoolean("vi", false),
                        canCancel = item.optBoolean("cc", false),
                    ),
                )
            }
        }
        GymRecordCache(account = account, fetchedAt = fetchedAt, records = records)
    } catch (e: Exception) {
        null
    }
}
