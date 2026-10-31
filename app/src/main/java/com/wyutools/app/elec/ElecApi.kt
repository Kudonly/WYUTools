package com.wyutools.app.elec

import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

data class ElecInfo(
    val remainingKwh: Double,
    val usedKwh: Double? = null,
    val schoolName: String? = null,
    val buildingName: String? = null,
    val roomName: String? = null,
    val updatedAt: String? = null,
    val userTypeName: String? = null,
    val status: Int? = null,
)

enum class ElecPeriod(val code: String, val label: String) {
    THREE_MONTHS("3M", "近3个月"),
    ONE_YEAR("1Y", "近1年"),
    ALL("1Y+", "全部"),
}

data class ElecRechargeRecord(
    val paidAtRaw: String,
    val paidAt: LocalDateTime?,
    val amountYuan: Double,
    val method: String,
    val orderCode: String,
)

data class ElecRechargePage(
    val records: List<ElecRechargeRecord>,
    val total: Int,
) {
    val totalAmountYuan: Double get() = records.sumOf { it.amountYuan }
    val averageAmountYuan: Double
        get() = if (records.isEmpty()) 0.0 else totalAmountYuan / records.size
}

enum class ElecPayChannel(val code: String, val label: String) {
    WECHAT("weixin", "微信"),
    ALIPAY("alipay", "支付宝"),
}

data class ElecRechargeOrder(
    val orderCode: String,
    val qrContent: String,
    val expireSeconds: Int,
)

enum class ElecPayResult {
    PENDING,
    SUCCESS,
    FAILED,
}

class ElecException(message: String) : Exception(message)

object ElecApi {
    private const val BASE = "http://202.192.240.231"
    private const val PATH_V2 = "/scp-api/electricity-recharge/getCurrentRemaining_v2"
    private const val PATH_V1 = "/scp-api/electricity-recharge/getCurrentRemaining"
    private const val PATH_HISTORY = "/scp-api/electricity-recharge/orderQueryWithPage"
    private const val PATH_CREATE_ORDER = "/scp-api/electricity-recharge/create-order-cb"
    private const val PATH_CHECK_ORDER = "/scp-api/electricity-recharge/check-order-cb"
    private const val USER_AGENT = "WYUtools/1.0 (Android)"

    private val PAY_TIME_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun query(building: String, room: String): ElecInfo {
        var lastMessage: String? = null
        try {
            postV2(building, room)?.let { return it }
        } catch (e: ElecException) {
            lastMessage = e.message
        }
        try {
            postV1(building, room)?.let { return it }
        } catch (e: ElecException) {
            if (lastMessage.isNullOrBlank()) lastMessage = e.message
        }
        throw ElecException(lastMessage?.takeIf { it.isNotBlank() } ?: "请输入正确的栋数和房间号")
    }

    private fun postV2(building: String, room: String): ElecInfo? {
        val root = execute(PATH_V2, fields(building, room))
        if (!isSuccess(root)) throw ElecException(errorMessage(root))
        val data = root.optJSONObject("data") ?: throw ElecException("服务器未返回电费数据")
        val remaining = data.optDouble("resamp", Double.NaN)
        if (remaining.isNaN()) throw ElecException("服务器未返回剩余电量")
        val used = if (data.has("usedamp") && !data.isNull("usedamp")) data.optDouble("usedamp") else null
        val status = if (data.has("status") && !data.isNull("status")) data.optInt("status") else null
        return ElecInfo(
            remainingKwh = remaining,
            usedKwh = used,
            schoolName = optStringOrNull(data, "schoolName"),
            buildingName = optStringOrNull(data, "apartName"),
            roomName = optStringOrNull(data, "roomName"),
            updatedAt = optStringOrNull(data, "updatedt"),
            userTypeName = optStringOrNull(data, "userTypeName"),
            status = status,
        )
    }

    private fun postV1(building: String, room: String): ElecInfo? {
        val root = execute(PATH_V1, fields(building, room))
        if (!isSuccess(root)) throw ElecException(errorMessage(root))
        val remaining = when (val data = root.opt("data")) {
            is Number -> data.toDouble()
            is String -> data.toDoubleOrNull() ?: Double.NaN
            else -> Double.NaN
        }
        if (remaining.isNaN()) throw ElecException("服务器未返回剩余电量")
        return ElecInfo(
            remainingKwh = remaining,
            buildingName = building.trim(),
            roomName = room.trim(),
        )
    }

    fun rechargeHistory(
        building: String,
        room: String,
        period: ElecPeriod,
    ): ElecRechargePage {
        val pageSize = 200
        val records = mutableListOf<ElecRechargeRecord>()
        var total = 0
        var page = 1
        do {
            val root = execute(
                PATH_HISTORY,
                fields(building, room) + mapOf(
                    "payResult" to "1",
                    "page" to "$page",
                    "pageSize" to "$pageSize",
                    "period" to period.code,
                ),
            )
            if (!isSuccess(root)) throw ElecException(errorMessage(root))
            val data = root.optJSONObject("data")
                ?: throw ElecException("服务器未返回充值记录")
            total = data.optInt("total", 0)
            val items = data.optJSONArray("data") ?: JSONArray()
            var added = 0
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                records.add(parseRechargeRecord(item))
                added += 1
            }
            if (added == 0 || records.size >= total) break
            page += 1
        } while (page <= 5)

        return ElecRechargePage(
            records = records.sortedByDescending { it.paidAtRaw },
            total = if (total > 0) total else records.size,
        )
    }

    fun createRechargeOrder(
        building: String,
        room: String,
        amountCent: Int,
        channel: ElecPayChannel,
    ): ElecRechargeOrder {
        val payload = JSONObject()
            .put("mobile", "")
            .put("building", building.trim())
            .put("room", room.trim())
            .put("payCent", amountCent)
            .put("userTypeId", 1)
            .put("codeType", channel.code)
        val root = executeJson(PATH_CREATE_ORDER, payload.toString())
        if (root.has("success") && !root.optBoolean("success", false)) {
            throw ElecException(errorMessage(root))
        }
        val data = root.optJSONObject("data") ?: throw ElecException("服务器未返回充值订单")
        val orderCode = data.optString("orderCode", "").trim()
        val qrContent = data.optString("qrCode", "").trim()
        if (orderCode.isEmpty() || qrContent.isEmpty()) {
            throw ElecException("充值二维码生成失败，请重试")
        }
        val expireSeconds = data.optInt("qrCodeExpireTime", 120).coerceIn(30, 1800)
        return ElecRechargeOrder(
            orderCode = orderCode,
            qrContent = qrContent,
            expireSeconds = expireSeconds,
        )
    }

    fun checkRechargeOrder(orderCode: String): ElecPayResult {
        val root = execute(PATH_CHECK_ORDER, mapOf("orderCode" to orderCode))
        if (!isSuccess(root)) throw ElecException(errorMessage(root))
        val record = root.optJSONObject("data")?.optJSONObject("record")
            ?: throw ElecException("暂未查询到支付结果")
        return when (val result = record.opt("payResult")) {
            is Number -> if (result.toInt() == 1) ElecPayResult.SUCCESS else if (result.toInt() == 2) ElecPayResult.FAILED else ElecPayResult.PENDING
            is String -> when (result.toIntOrNull()) {
                1 -> ElecPayResult.SUCCESS
                2 -> ElecPayResult.FAILED
                else -> ElecPayResult.PENDING
            }
            else -> ElecPayResult.PENDING
        }
    }

    private fun parseRechargeRecord(item: JSONObject): ElecRechargeRecord {
        val rawTime = item.optString("payTime", "").trim()
        val amountCent = item.optDouble("payCent", 0.0)
        return ElecRechargeRecord(
            paidAtRaw = rawTime,
            paidAt = parsePayTime(rawTime),
            amountYuan = amountCent / 100.0,
            method = paymentMethodLabel(item.optString("payMethod", "")),
            orderCode = item.optString("orderCode", "").trim(),
        )
    }

    private fun parsePayTime(raw: String): LocalDateTime? {
        if (raw.isBlank()) return null
        return try {
            LocalDateTime.parse(raw, PAY_TIME_FORMAT)
        } catch (_: Exception) {
            try {
                LocalDateTime.parse(raw.replace(' ', 'T'))
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun paymentMethodLabel(code: String): String {
        return when (code.trim().lowercase()) {
            "code" -> "扫码支付"
            "wx", "wechat" -> "微信支付"
            "alipay" -> "支付宝"
            "" -> "未知方式"
            else -> code.trim()
        }
    }

    private fun fields(building: String, room: String): Map<String, String> = mapOf(
        "userTypeID" to "1",
        "building" to building.trim(),
        "room" to room.trim(),
    )

    private fun execute(path: String, fields: Map<String, String>): JSONObject {
        val bodyBuilder = FormBody.Builder()
        fields.forEach { (key, value) -> bodyBuilder.add(key, value) }
        return executeRequest(
            Request.Builder()
                .url(BASE + path)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .post(bodyBuilder.build())
                .build(),
        )
    }

    private fun executeJson(path: String, json: String): JSONObject {
        return executeRequest(
            Request.Builder()
                .url(BASE + path)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .post(json.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build(),
        )
    }

    private fun executeRequest(request: Request): JSONObject {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ElecException("服务器返回异常（HTTP ${response.code}）")
            }
            if (text.isBlank()) throw ElecException("服务器返回为空")
            return try {
                JSONObject(text)
            } catch (e: JSONException) {
                throw ElecException("服务器返回数据异常")
            }
        }
    }

    private fun isSuccess(root: JSONObject): Boolean =
        root.optBoolean("success", false) || root.optInt("code", -1) == 10000

    private fun errorMessage(root: JSONObject): String {
        val message = root.optString("message", "")
            .ifBlank { root.optString("msg", "") }
            .ifBlank { root.optString("error", "") }
        if (message.isNotBlank()) return message
        val code = root.optInt("code", -1)
        return if (code >= 0) "查询失败（code=$code）" else "查询失败"
    }

    private fun optStringOrNull(obj: JSONObject, key: String): String? {
        if (!obj.has(key) || obj.isNull(key)) return null
        return obj.optString(key, "").ifBlank { null }
    }
}
