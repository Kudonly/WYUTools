package com.wyutools.app.lxt

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class ApiException(val code: Int, msg: String) : Exception(msg)

data class LoginResult(val token: String)

object LxtApi {
    private const val HOST = "https://v3-prod-beta-app.lxt6.cn"
    const val SCHOOL_ID = "80790"
    const val INVESTOR_ID = "600000000000000047"
    private const val SIGN_SALT = "1FF75E512"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json".toMediaType()

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    private fun xGhost(): String {
        val t = java.lang.Long.toHexString(System.currentTimeMillis()).uppercase().padStart(12, '0')
        var sum = 0
        var xor = 0
        var i = 0
        while (i + 1 < t.length) {
            val b = t.substring(i, i + 2).toInt(16)
            sum += b
            xor = xor xor b
            i += 2
        }
        return "%02X".format(sum % 256) + t + "%02X".format(xor and 0xFF)
    }

    private fun buildHeaders(token: String?, isGet: Boolean, signBase: String): Map<String, String> {
        val headers = mutableMapOf(
            "Content-Type" to if (isGet) "application/x-www-form-urlencoded" else "application/json",
            "X-Sign" to md5(SIGN_SALT + signBase),
            "X-Ghost" to xGhost(),
            "X-Sid" to "2021011300001",
            "X-clientType" to "4",
            "X-Product-Ver" to "4.1.6.46#?1.13.23",
            "User-Agent" to "okhttp/3.12.1",
        )
        if (!token.isNullOrEmpty()) {
            headers["x-token"] = "tokenInfo=$token"
            headers["Cookie"] = "tokenInfo=$token"
        }
        return headers
    }

    private class Envelope(
        val code: Int,
        val msg: String,
        val data: Any?,
        val headers: okhttp3.Headers,
    )

    private suspend fun call(
        method: String,
        path: String,
        params: List<Pair<String, String>> = emptyList(),
        body: String? = null,
        token: String? = null,
    ): Envelope = withContext(Dispatchers.IO) {
        val isGet = method == "GET"
        val query = params.joinToString("&") { "${it.first}=${it.second}" }
        val signBase = if (isGet) query else body.orEmpty()
        val url = HOST + path + if (isGet && query.isNotEmpty()) "?$query" else ""
        val requestBody = if (isGet) null else (body ?: "{}").toRequestBody(jsonMedia)
        val builder = Request.Builder().url(url)
        buildHeaders(token, isGet, signBase).forEach { (k, v) -> builder.addHeader(k, v) }
        val response = client.newCall(builder.method(method, requestBody).build()).execute()
        response.use { resp ->
            val text = resp.body?.string().orEmpty()
            val json = try {
                JSONObject(text)
            } catch (e: Exception) {
                throw ApiException(-1, "服务器响应异常（${resp.code}）")
            }
            if (!json.has("Code")) {
                throw ApiException(-1, "服务器响应异常")
            }
            Envelope(
                code = json.optInt("Code"),
                msg = json.optString("Message"),
                data = json.opt("Data"),
                headers = resp.headers,
            )
        }
    }

    private fun ensure(envelope: Envelope): Envelope {
        if (envelope.code != 0) {
            throw ApiException(envelope.code, envelope.msg.ifBlank { "请求失败（${envelope.code}）" })
        }
        return envelope
    }

    private fun quote(value: String): String = buildString {
        append('"')
        for (c in value) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }

    private fun jsonObject(vararg pairs: Pair<String, Any?>): String = buildString {
        append('{')
        pairs.forEachIndexed { index, (key, value) ->
            if (index > 0) append(',')
            append(quote(key))
            append(':')
            when (value) {
                is String -> append(quote(value))
                is Number, is Boolean -> append(value.toString())
                null -> append("null")
                else -> append(quote(value.toString()))
            }
        }
        append('}')
    }

    private fun optText(obj: JSONObject, vararg keys: String): String {
        for (key in keys) {
            if (!obj.has(key)) continue
            val value = obj.opt(key) ?: continue
            val text = value.toString()
            if (text.isNotBlank() && text != "null") return text
        }
        return ""
    }

    suspend fun getCode(account: String): String {
        val envelope = ensure(
            call(
                "GET",
                "/user/authentication/getCode",
                params = listOf("account" to account),
            )
        )
        val data = envelope.data as? String
        if (data.isNullOrBlank()) throw ApiException(-1, "验证码获取失败")
        return data
    }

    suspend fun login(account: String, password: String, code: String): LoginResult {
        val inner = jsonObject(
            "studentMobile" to account,
            "loginPassword" to password,
            "code" to code,
        )
        val payload = jsonObject(
            "data" to Base64.encodeToString(inner.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        )
        val envelope = ensure(
            call("POST", "/user/login/userLoginV2WithEncrypt", body = payload)
        )
        val token = extractToken(envelope.headers)
            ?: throw ApiException(-1, "登录失败：未取到登录凭证")
        return LoginResult(token)
    }

    private fun extractToken(headers: okhttp3.Headers): String? {
        for (name in headers.names()) {
            val lower = name.lowercase()
            if (lower != "tokeninfo" && lower != "set-cookie" && lower != "cookie") continue
            for (value in headers.values(name)) {
                if (lower == "tokeninfo") {
                    val token = value.trim()
                    if (token.isNotEmpty() && !token.contains(';')) return token
                } else {
                    for (part in value.split(';')) {
                        val item = part.trim()
                        if (!item.startsWith("tokeninfo=", ignoreCase = true)) continue
                        val token = item.substringAfter('=').trim()
                        if (token.isNotEmpty()) return token
                    }
                }
            }
        }
        return null
    }

    suspend fun queryBath(token: String): JSONObject? {
        val body = jsonObject(
            "schoolId" to SCHOOL_ID,
            "investorId" to INVESTOR_ID,
            "walletType" to 1,
        )
        val envelope = ensure(
            call("POST", "/payment/student/getWalletInfoByInvestorid", body = body, token = token)
        )
        return envelope.data as? JSONObject
    }

    suspend fun queryWater(token: String, walletKey: String): JSONObject? {
        val envelope = ensure(
            call(
                "GET",
                "/paymentV1/app/wallet/find",
                params = listOf(
                    "investorId" to INVESTOR_ID,
                    "schoolId" to SCHOOL_ID,
                    "walletKey" to walletKey,
                ),
                token = token,
            )
        )
        return envelope.data as? JSONObject
    }

    suspend fun lowerAreas(token: String, areaId: String): List<AreaNode> {
        val envelope = ensure(
            call(
                "GET",
                "/baseDict/site/getLowerAreas",
                params = listOf("areaId" to areaId),
                token = token,
            )
        )
        return toNodes(envelope.data)
    }

    suspend fun rooms(token: String, areaId: String): List<AreaNode> {
        val envelope = ensure(
            call(
                "GET",
                "/baseDict/site/getDormitoryOrPublicRoom",
                params = listOf("areaId" to areaId),
                token = token,
            )
        )
        return toNodes(envelope.data)
    }

    private fun toNodes(data: Any?): List<AreaNode> {
        val array: JSONArray = when (data) {
            is JSONArray -> data
            is JSONObject -> {
                var found: JSONArray? = null
                for (key in listOf("records", "list", "areas", "children", "data")) {
                    found = data.optJSONArray(key)
                    if (found != null) break
                }
                when {
                    found != null -> found
                    optText(data, "id", "areaId", "siteId").isNotEmpty() -> JSONArray().put(data)
                    else -> JSONArray()
                }
            }

            else -> JSONArray()
        }
        val result = ArrayList<AreaNode>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = optText(item, "id", "areaId", "siteId", "roomId")
            if (id.isEmpty()) continue
            result.add(
                AreaNode(
                    id = id,
                    name = optText(item, "name", "areaName", "siteName", "roomName", "dormName", "dormitoryName")
                        .ifEmpty { "未命名" },
                    siteFlag = optInt(item, "siteFlag", "siteflag", "flag"),
                )
            )
        }
        return result
    }

    private fun optInt(obj: JSONObject, vararg keys: String): Int {
        for (key in keys) {
            if (obj.has(key)) return obj.optInt(key)
        }
        return 0
    }

    suspend fun machineByLocation(token: String, siteId: String, siteFlag: Int, typeId: Int): List<MachineInfo> {
        val body = jsonObject(
            "siteId" to siteId,
            "siteFlag" to siteFlag,
            "typeId" to typeId,
        )
        val envelope = ensure(
            call("POST", "/mgapp/machine/getMachineByLocation", body = body, token = token)
        )
        val array: JSONArray = when (val data = envelope.data) {
            is JSONArray -> data
            is JSONObject -> data.optJSONArray("records") ?: data.optJSONArray("list") ?: JSONArray().put(data)
            else -> JSONArray()
        }
        val result = ArrayList<MachineInfo>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val machineId = optText(item, "machineId")
            if (machineId.isEmpty()) continue
            result.add(
                MachineInfo(
                    machineId = machineId,
                    deviceMac = optText(item, "deviceMac"),
                    deviceVer = optText(item, "deviceVer"),
                    roomName = optText(item, "roominfo", "roomName"),
                    siteId = optText(item, "siteId"),
                    siteFlag = optInt(item, "siteFlag", "siteflag", "flag"),
                )
            )
        }
        return result
    }

    fun macFromCode(input: String): String? {
        val text = input.trim()
        if (text.startsWith("http://", ignoreCase = true) || text.startsWith("https://", ignoreCase = true)) {
            return Regex("""^https?://n\.lxt6\.cn/all\?([0-9A-Fa-f]{12})""", RegexOption.IGNORE_CASE)
                .find(text)
                ?.groupValues
                ?.get(1)
                ?.uppercase()
        }
        val compact = text.filterNot { it == ':' || it == '-' || it.isWhitespace() }
        if (compact.length != 12) return null
        if (!compact.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) return null
        return compact.uppercase()
    }

    private fun macParam(mac: String): String =
        mac.uppercase()
            .filter { it in '0'..'9' || it in 'A'..'F' }
            .chunked(2)
            .joinToString(":")

    suspend fun machineByMac(token: String, mac: String): MachineInfo? {
        val normalized = mac.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }
        if (normalized.length != 12) return null
        val envelope = ensure(
            call(
                "GET",
                "/mgapp/machine/getMachineByMac",
                params = listOf("mac" to macParam(normalized), "typeId" to "1"),
                token = token,
            )
        )
        val item = when (val data = envelope.data) {
            is JSONObject -> data.optJSONObject("machine")
                ?: data.optJSONArray("records")?.optJSONObject(0)
                ?: data
            is JSONArray -> data.optJSONObject(0)
            else -> null
        } ?: return null
        val machineId = optText(item, "machineId")
        if (machineId.isEmpty()) return null
        return MachineInfo(
            machineId = machineId,
            deviceMac = optText(item, "deviceMac").ifEmpty { normalized },
            deviceVer = optText(item, "deviceVer"),
            roomName = optText(item, "roominfo", "roomName"),
            siteId = optText(item, "siteId"),
            siteFlag = optInt(item, "siteFlag", "siteflag", "flag"),
        )
    }

    suspend fun machineStatusList(token: String, siteId: String, siteFlag: Int): List<MachineStatus> {
        if (siteId.isBlank()) return emptyList()
        val envelope = ensure(
            call(
                "GET",
                "/bath/machine/status/getList",
                params = listOf(
                    "investorId" to INVESTOR_ID,
                    "schoolId" to SCHOOL_ID,
                    "siteId" to siteId,
                    "siteFlag" to siteFlag.toString(),
                    "typeId" to "1",
                ),
                token = token,
            )
        )
        val array: JSONArray = when (val data = envelope.data) {
            is JSONArray -> data
            is JSONObject -> data.optJSONArray("machineStatusList")
                ?: data.optJSONArray("records")
                ?: data.optJSONArray("list")
                ?: JSONArray()
            else -> JSONArray()
        }
        val result = ArrayList<MachineStatus>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val machineId = optText(item, "machineId")
            if (machineId.isEmpty()) continue
            result.add(MachineStatus(machineId, optInt(item, "curStatus", "status")))
        }
        return result
    }

    fun isMachineRunning(status: Int): Boolean = status == 111 || status == 121

    fun channelWay(deviceVer: String): Int {
        val parts = deviceVer.split(',')
        if (parts.getOrNull(0)?.trim() == "00") return 0
        val middle = parts.getOrNull(1)?.replace(" ", "")?.uppercase().orEmpty()
        return if (middle == "02" || middle == "03" || middle == "04" || middle == "05") 2 else 4
    }

    suspend fun valveOpen(token: String, machineId: String, deviceVer: String): JSONObject? {
        val channel = channelWay(deviceVer)
        if (channel == 0) throw ApiException(-2, "该设备不支持远程开阀，请现场操作")
        val body = buildString {
            append('{')
            append("\"typeId\":1,")
            append("\"channelWay\":").append(channel).append(',')
            append("\"machineId\":").append(quote(machineId)).append(',')
            append("\"deviceVer\":").append(quote(deviceVer)).append(',')
            append("\"machineInfoJson\":{\"lastPosMoney\":\"0000\",\"lastPosSerial\":\"0000\"}")
            append('}')
        }
        val envelope = ensure(
            call("POST", "/bath/valve/open", body = body, token = token)
        )
        return envelope.data as? JSONObject
    }

    suspend fun valveStatus(token: String, machineId: String, communicationVal: String): Int? {
        val envelope = ensure(
            call(
                "GET",
                "/bath/valve/getStatus",
                params = listOf(
                    "typeId" to "1",
                    "machineId" to machineId,
                    "modeValue" to "1",
                    "communicationVal" to communicationVal,
                ),
                token = token,
            )
        )
        return when (val data = envelope.data) {
            is JSONObject -> if (data.has("status")) data.optInt("status") else null
            is Number -> data.toInt()
            else -> null
        }
    }
}
