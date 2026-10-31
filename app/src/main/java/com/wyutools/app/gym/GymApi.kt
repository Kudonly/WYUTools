package com.wyutools.app.gym

import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.random.Random

data class GymUser(
    val name: String,
    val account: String,
)

data class GymSlot(
    val time: String,
    val startMinutes: Int,
    val durationMinutes: Int? = null,
)

data class GymVenue(
    val wid: String,
    val bizWid: String,
    val name: String,
    val venueType: String?,
    val venueTypeId: String?,
    val sportName: String?,
    val sportId: String?,
    val address: String?,
    val openStatus: String?,
    val capacity: Int,
    val maxDurationMinutes: Int?,
    val slots: List<GymSlot>,
)

data class GymRecord(
    val wid: String,
    val recordId: String?,
    val venueName: String,
    val slot: String,
    val date: LocalDate?,
    val dateRaw: String,
    val statusCode: String?,
    val status: String,
    val payStatusCode: String?,
    val submittedAt: String?,
    val durationMinutes: Int?,
    val semester: String?,
    val violation: Boolean,
    val canCancel: Boolean,
)

class GymException(
    message: String,
    val authExpired: Boolean = false,
) : Exception(message)

class GymApi(
    private val cookieProvider: () -> String,
    private val onCookieUpdated: (String) -> Unit,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    @Volatile
    private var pcAccessUrl: String? = null

    private data class RawResponse(
        val code: Int,
        val body: String,
        val url: HttpUrl,
        val location: String?,
    )

    @Synchronized
    fun resetSession() {
        pcAccessUrl = null
    }

    fun validateSession(): GymUser {
        val request = baseRequest(profileUrl())
            .get()
            .build()
        val raw = execute(request)
        if (raw.location != null) {
            throw GymException("校园统一认证登录已失效，请重新登录。", authExpired = true)
        }
        val root = parseJsonObject(raw.body)
            ?: throw GymException("登录信息读取失败，请重新登录。", authExpired = true)
        checkAuthPayload(root)
        val data = root.optJSONObject("data")
            ?: throw GymException("登录已失效，请重新登录。", authExpired = true)
        val account = data.optString("userAccount").trim()
        if (account.isEmpty()) {
            throw GymException("登录已失效，请重新登录。", authExpired = true)
        }
        val name = data.optString("userName").trim().ifEmpty { account }
        return GymUser(name = name, account = account)
    }

    fun fetchVenues(date: LocalDate): List<GymVenue> {
        ensureAppSession()
        val root = postJson(
            "/modules/application/getOpeningRoom.do",
            linkedMapOf(
                "BEGIN" to date.toString(),
                "END" to date.toString(),
                "querySetting" to "[]",
                "pageSize" to "100",
                "pageNumber" to "1",
            ),
        )
        val rows = extractRows(root, "getOpeningRoom")
        return rows.mapNotNull { parseVenue(it, date) }
            .sortedBy { it.name }
    }

    fun fetchRecords(): List<GymRecord> {
        ensureAppSession()
        val root = postJson(
            "/modules/myApplication/getMyAppointmentListData.do",
            linkedMapOf(
                "pageSize" to "50",
                "pageNumber" to "1",
            ),
        )
        val rows = extractRows(root, "getMyAppointmentListData")
        return rows.mapNotNull { parseRecord(it) }
            .distinctBy { record -> record.recordId ?: "${record.wid}|${record.dateRaw}|${record.slot}" }
            .sortedWith(
                compareByDescending<GymRecord> { it.date }
                    .thenByDescending { it.submittedAt.orEmpty() },
            )
    }

    /**
     * 完整预约链路，顺序与网页端一致：
     * getSjdByRoom -> getFill -> checkSelfTodayYyData -> checkCanApply ->
     * startFlow -> insertSelfToZtry。
     *
     * 返回 null 表示提交成功但暂时无法在列表确认（网络/刷新问题），
     * 调用方应提示用户到“我的预约”查看。
     */
    fun book(
        user: GymUser,
        venue: GymVenue,
        slot: GymSlot,
        date: LocalDate,
        phone: String,
    ): GymRecord? {
        ensureAppSession()
        val formData = buildApplyFormData(user, venue, slot, date, phone)

        checkRealtimeSlot(venue, slot, date)
        runCatching {
            postRaw("/modules/pay/getFill.do", linkedMapOf(
                "BIZ_WID" to venue.bizWid,
                "SYSJ" to slot.time,
            ))
        }

        val selfCheck = postJson(
            "/modules/application/checkSelfTodayYyData.do",
            linkedMapOf("YYRQ" to date.toString()),
        )
        if (extractRows(selfCheck, "checkSelfTodayYyData").isNotEmpty()) {
            throw GymException("当天已存在预约记录，无法再次预约。")
        }

        val check = postJson(
            "/modules/application/checkCanApply.do",
            formData.toMutableMap(),
        )
        val code = check.optString("code").trim()
        if (code.isNotEmpty() && code != "0") {
            throw GymException(pickMessage(check) ?: "预约校验未通过。")
        }
        val checkData = check.optJSONObject("data")
        if (checkData != null) {
            val canApply = checkData.optString("canApply").trim()
            if (canApply == "false" || canApply == "0") {
                throw GymException(
                    checkData.optString("msg").trim()
                        .ifEmpty { pickMessage(check) ?: "预约校验未通过。" },
                )
            }
        }

        val startFlow = postJson(
            "/sys/emapflow/tasks/startFlow.do",
            linkedMapOf(
                "formData" to JSONObject(formData).toString(),
                "id" to "start",
                "sendMessage" to "true",
                "commandType" to "start",
                "execute" to "do_start",
                "name" to "提交",
                "commandEvent" to
                    "com.wisedu.emap.lwWiseduCgyy.service.Impl.ApplyCheckFlowService",
                "url" to "/sys/emapflow/tasks/startFlow.do",
                "buttonType" to "success",
                "taskId" to "",
                "defKey" to "lwWiseduCgyy.MainFlow",
            ),
        )
        val succeed = startFlow.opt("succeed")
        if (succeed == false || succeed?.toString() == "false") {
            throw GymException(pickMessage(startFlow) ?: "预约提交失败。")
        }

        val applyWid = formData["WID"] as String
        runCatching {
            postJson(
                "/modules/application/insertSelfToZtry.do",
                linkedMapOf(
                    "APPLY_WID" to applyWid,
                    "USER_ID" to user.account,
                    "BY5" to "1",
                ),
            )
        }

        val records = try {
            fetchRecords()
        } catch (auth: GymException) {
            throw auth
        } catch (_: Exception) {
            return null
        }
        val matches = records.filter {
            it.venueName == venue.name && it.slot == slot.time && it.date == date
        }
        matches.firstOrNull { it.statusCode == "001" }?.let { return it }

        val newest = matches.firstOrNull()
        if (newest != null) {
            val hint = if (newest.violation) {
                "系统拒绝预约：当前可能处于违约冷冻期，请稍后再试或换一个项目。"
            } else {
                "系统已自动取消该预约，可能受违约冷冻、次数上限或时段冲突限制。"
            }
            throw GymException(hint)
        }
        return null
    }

    fun cancel(wid: String) {
        ensureAppSession()
        runCatching {
            postRaw("/modules/pay/qxPay.do", linkedMapOf("WID" to wid))
        }
        runCatching {
            postJson(
                "/modules/myApplication/deleteZtcyYyData.do",
                linkedMapOf("APPLY_WID" to wid),
            )
        }
        val now = java.time.LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val root = postJson(
            "/modules/myApplication/T_WISEDU_CGYY_YY_SAVE.do",
            linkedMapOf(
                "WID" to wid,
                "QXYY" to "0",
                "QXSJ" to now,
                "SYZT" to "003",
                "PAY_STATUS" to "CG_QX",
            ),
        )
        val code = root.optString("code").trim()
        if (code.isNotEmpty() && code != "0") {
            throw GymException(pickMessage(root) ?: "取消预约失败。")
        }
    }

    // -------------------- 预约内部步骤 --------------------

    private fun checkRealtimeSlot(venue: GymVenue, slot: GymSlot, date: LocalDate) {
        if (venue.wid.isEmpty()) return
        val raw = runCatching {
            postRaw(
                "/modules/application/getSjdByRoom.do",
                linkedMapOf(
                    "applyDate" to date.toString(),
                    "roomId" to venue.wid,
                ),
            )
        }.getOrNull() ?: return
        val available = parseStringArray(raw)
        if (available.isNotEmpty() && slot.time !in available) {
            throw GymException("该时段已被占用，请换一个时段。")
        }
    }

    private fun buildApplyFormData(
        user: GymUser,
        venue: GymVenue,
        slot: GymSlot,
        date: LocalDate,
        phone: String,
    ): Map<String, Any> {
        val dateStr = date.toString()
        val bizWid = venue.bizWid.ifEmpty { venue.wid }
        val timestamp = System.currentTimeMillis().toString()
        val rawVerification = "${user.account}$dateStr${slot.time}${bizWid}_WISEDU_$timestamp"
        val encoded = URLEncoder.encode(rawVerification, "UTF-8")
            .replace("+", "%20")
            .replace("%7E", "~")
        val verification = android.util.Base64.encodeToString(
            encoded.toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP,
        )
        return linkedMapOf(
            "YYRXM" to user.name,
            "YYLX_DISPLAY" to "个人预约",
            "YYLX" to "001",
            "YYRQ" to dateStr,
            "SYSJ" to slot.time,
            "SYRS" to "1",
            "LXDH" to phone,
            "HYSLX_DISPLAY" to (venue.venueType ?: ""),
            "HYSLX" to (venue.venueTypeId ?: ""),
            "GLBM_DISPLAY" to (venue.sportName ?: ""),
            "GLBM" to (venue.sportId ?: ""),
            "HYSMC" to venue.name,
            "HYZT" to "",
            "HYMS" to "",
            "BZ" to "",
            "FJ" to "",
            "WID" to randomHex(32),
            "XGH" to user.account,
            "BIZ_WID" to bizWid,
            "SYZT" to "001",
            "VERIFICATION" to verification,
            "BCSQRS" to "1",
            "USERID" to user.account,
        )
    }

    // -------------------- ehall 应用态 --------------------

    @Synchronized
    private fun ensureAppSession() {
        if (pcAccessUrl != null) return
        var entry = searchGymEntry(typeId = null)
        if (entry == null) {
            entry = searchGymEntry(typeId = GYM_SERVICE_TYPE_ID)
        }
        if (entry == null) {
            entry = GYM_DIRECT_ENTRY
        }
        warmUpEntry(entry)
        pcAccessUrl = entry
    }

    private fun searchGymEntry(typeId: String?): String? {
        val param = JSONObject().apply {
            put("lang", "zh_CN")
            if (typeId != null) put("typeId", typeId)
        }
        val payload = JSONObject().apply {
            put("cardId", "SYS_CARD_SERVICEBUS")
            put("cardWid", SERVICE_CARD_WID)
            put("method", "renderData")
            put("param", param)
        }
        val request = baseRequest(
            "https://ehall.wyu.edu.cn/execCardMethod/$SERVICE_CARD_WID/SYS_CARD_SERVICEBUS?n=${Math.random()}",
        )
            // 卡片接口按 UA 下发不同服务列表，移动端 UA 会得到空 appData
            .header("User-Agent", DESKTOP_USER_AGENT)
            .header("Accept", "application/json, text/plain, */*")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val raw = execute(request)
        if (raw.location != null) {
            throw GymException("校园统一认证登录已失效，请重新登录。", authExpired = true)
        }
        val root = parseJsonObject(raw.body) ?: return null
        checkAuthPayload(root)
        val appData = root.optJSONObject("data")?.optJSONArray("appData") ?: return null
        for (index in 0 until appData.length()) {
            val service = appData.optJSONObject(index) ?: continue
            val name = service.optString("appName").trim()
                .ifEmpty { service.optString("serviceName").trim() }
            if (name == GYM_APP_NAME) {
                val url = service.optString("pcAccessUrl").trim()
                if (url.isNotEmpty()) return url
            }
        }
        return null
    }

    private fun warmUpEntry(entry: String) {
        var url = entry
        repeat(5) {
            val raw = execute(baseRequest(url).get().build())
            if (raw.code !in 300..399) {
                if (raw.code == 200) return
                throw GymException("体育馆预约系统访问失败（HTTP ${raw.code}）。")
            }
            val location = raw.location ?: return
            url = raw.url.resolve(location)?.toString() ?: return
        }
    }

    // -------------------- HTTP 基础 --------------------

    private fun profileUrl(): String =
        "https://ehall.wyu.edu.cn/getLoginUserAndGuest?_t=${System.currentTimeMillis()}"

    private fun baseRequest(url: String): Request.Builder {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json, text/plain, */*")
        val referer = pcAccessUrl
        if (referer != null) {
            builder.header("Referer", referer)
            builder.header("X-Requested-With", "XMLHttpRequest")
        }
        val cookie = cookieProvider().trim()
        if (cookie.isNotEmpty()) {
            builder.header("Cookie", cookie)
        }
        return builder
    }

    private fun postJson(path: String, fields: Map<String, Any>): JSONObject {
        val raw = postRaw(path, fields)
        val root = parseJsonObject(raw)
            ?: throw GymException("体育馆接口返回格式异常。")
        checkAuthPayload(root)
        return root
    }

    private fun postRaw(path: String, fields: Map<String, Any>): String {
        val builder = FormBody.Builder()
        fields.forEach { (key, value) -> builder.add(key, value.toString()) }
        val request = baseRequest(apiUrl(path))
            .header("X-Requested-With", "XMLHttpRequest")
            .post(builder.build())
            .build()
        val raw = execute(request)
        if (raw.location != null) {
            throw GymException("校园统一认证登录已失效，请重新登录。", authExpired = true)
        }
        if (raw.code == 401 || raw.code == 403) {
            throw GymException("校园统一认证登录已失效，请重新登录。", authExpired = true)
        }
        if (raw.code >= 400) {
            throw GymException("体育馆接口请求失败（HTTP ${raw.code}）。")
        }
        return raw.body
    }

    private fun apiUrl(path: String): String {
        return if (path.startsWith("/sys/emapflow/")) {
            "https://ehall.wyu.edu.cn/qljfwapp$path"
        } else {
            "https://ehall.wyu.edu.cn/qljfwapp/sys/lwWiseduCgyy$path"
        }
    }

    private fun execute(request: Request): RawResponse {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            absorbCookies(response.headers("Set-Cookie"))
            if (looksLikeLoginHtml(body)) {
                throw GymException("校园统一认证登录已失效，请重新登录。", authExpired = true)
            }
            return RawResponse(
                code = response.code,
                body = body,
                url = response.request.url,
                location = response.header("Location"),
            )
        }
    }

    @Synchronized
    private fun absorbCookies(setCookies: List<String>) {
        if (setCookies.isEmpty()) return
        var current = cookieProvider().trim()
        if (current.isEmpty()) return
        var changed = false
        for (header in setCookies) {
            val first = header.substringBefore(';')
            val name = first.substringBefore('=').trim()
            val value = first.substringAfter('=', "").trim()
            if (name.isEmpty()) continue
            val parts = current.split(';')
                .map { it.trim() }
                .filter { it.isNotEmpty() && it.substringBefore('=').trim() != name }
                .toMutableList()
            if (value.isNotEmpty()) {
                parts.add("$name=$value")
            }
            current = parts.joinToString("; ")
            changed = true
        }
        if (changed) {
            onCookieUpdated(current)
        }
    }

    private fun checkAuthPayload(root: JSONObject) {
        val code = root.optString("code").trim()
        if (code == "-401" || code == "401") {
            throw GymException("校园统一认证登录已失效，请重新登录。", authExpired = true)
        }
        val message = pickMessage(root)
        if (message != null &&
            listOf("尚未登录", "请先登录", "登录失效", "已在别处登录", "被迫退出")
                .any { message.contains(it) }
        ) {
            throw GymException("校园统一认证登录已失效，请重新登录。", authExpired = true)
        }
    }

    private fun looksLikeLoginHtml(body: String): Boolean {
        val trimmed = body.trimStart()
        if (!trimmed.startsWith("<")) return false
        return trimmed.contains("pwdEncryptSalt") ||
            trimmed.contains("统一身份认证") ||
            trimmed.contains("authserver/login") ||
            trimmed.contains("账号登录") ||
            trimmed.contains("验证码登录")
    }

    private fun parseJsonObject(body: String): JSONObject? {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return null
        return try {
            JSONObject(trimmed)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseStringArray(raw: String): List<String> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyList()
        val array = try {
            JSONArray(trimmed)
        } catch (_: Exception) {
            try {
                findFirstArray(JSONObject(trimmed))
            } catch (_: Exception) {
                null
            }
        } ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            array.opt(index)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        }
    }

    private fun findFirstArray(obj: JSONObject): JSONArray? {
        val keys = obj.keys()
        while (keys.hasNext()) {
            when (val value = obj.opt(keys.next())) {
                is JSONArray -> return value
                is JSONObject -> findFirstArray(value)?.let { return it }
            }
        }
        return null
    }

    private fun extractRows(root: JSONObject, key: String): List<JSONObject> {
        val datas = root.optJSONObject("datas")
        val sub = datas?.optJSONObject(key)
        val rows = sub?.optJSONArray("rows")
        if (rows != null) return rows.toObjectList()
        val data = root.optJSONObject("data")
        val dataRows = data?.optJSONArray("rows")
        if (dataRows != null) return dataRows.toObjectList()
        return if (data != null) listOf(data) else emptyList()
    }

    private fun JSONArray.toObjectList(): List<JSONObject> =
        (0 until length()).mapNotNull { optJSONObject(it) }

    // -------------------- 解析 --------------------

    private fun parseVenue(row: JSONObject, date: LocalDate): GymVenue? {
        val name = pick(row, "HYSMC", "BIZ_WID_DISPLAY", "WID_DISPLAY", "NAME")
            ?: return null
        val wid = pick(row, "WID") ?: ""
        val bizWid = pick(row, "BIZ_WID") ?: ""
        if (wid.isEmpty() && bizWid.isEmpty()) return null

        val today = LocalDate.now()
        val nowMinutes = LocalTime.now().let { it.hour * 60 + it.minute }
        val slots = mutableListOf<GymSlot>()
        for (index in 1..7) {
            val day = row.optJSONObject("day$index") ?: continue
            val dayDate = parseDate(day.optString("date")) ?: continue
            if (dayDate != date) continue
            val times = day.optString("times").trim()
            if (times.isEmpty()) continue
            times.split(',').forEach { raw ->
                val time = raw.trim()
                if (time.isEmpty()) return@forEach
                val start = startMinutes(time) ?: return@forEach
                if (dayDate == today && start <= nowMinutes) return@forEach
                if (slots.none { it.time == time }) {
                    slots.add(
                        GymSlot(
                            time = time,
                            startMinutes = start,
                            durationMinutes = slotDurationMinutes(time),
                        ),
                    )
                }
            }
        }

        return GymVenue(
            wid = wid.ifEmpty { bizWid },
            bizWid = bizWid.ifEmpty { wid },
            name = name,
            venueType = pick(row, "HYSLX_DISPLAY"),
            venueTypeId = pick(row, "HYSLX"),
            sportName = pick(row, "GLBM_DISPLAY", "GLBMMC"),
            sportId = pick(row, "GLBM"),
            address = pick(row, "XXDZ"),
            openStatus = pick(row, "KFZT_DISPLAY", "KFZT"),
            capacity = row.optInt("RNRS", 0),
            maxDurationMinutes = parseMinutes(row, "ZDKYYSC"),
            slots = slots.sortedBy { it.startMinutes },
        )
    }

    private fun parseRecord(row: JSONObject): GymRecord? {
        val name = pick(row, "HYSMC", "BIZ_WID_DISPLAY", "HYSLX_DISPLAY")
            ?: return null
        val wid = pick(row, "WID") ?: ""
        val slot = pick(row, "SYSJ", "SJ", "timeRange") ?: "时段未知"
        val dateRaw = pick(row, "YYRQ", "YYSJ", "date") ?: ""
        val date = parseDate(dateRaw)
        val syzt = pick(row, "SYZT")
        val payStatus = pick(row, "PAY_STATUS")
        val effectiveCode = if (payStatus == "CG_QX") "003" else syzt
        val status = statusLabel(effectiveCode)
            ?: pick(row, "SYZT_DISPLAY")
            ?: effectiveCode
            ?: "未知"
        val violation = pick(row, "SFWY") == "1" || pick(row, "SFWY_DISPLAY") == "是"
        val canCancel = effectiveCode == "001" &&
            wid.isNotEmpty() &&
            isSlotInFuture(date, slot)
        return GymRecord(
            wid = wid,
            recordId = pick(row, "ID_", "PROC_INST_ID_"),
            venueName = name,
            slot = slot,
            date = date,
            dateRaw = dateRaw,
            statusCode = effectiveCode,
            status = status,
            payStatusCode = payStatus,
            submittedAt = pick(row, "SQSJ"),
            durationMinutes = parseMinutes(row, "YDSC") ?: slotDurationMinutes(slot),
            semester = pick(row, "XQ_DISPLAY", "XQ"),
            violation = violation,
            canCancel = canCancel,
        )
    }

    private fun parseMinutes(row: JSONObject, key: String): Int? {
        if (!row.has(key) || row.isNull(key)) return null
        val value = row.optDouble(key, Double.NaN)
        if (value.isNaN() || value <= 0) return null
        return value.toInt()
    }

    private fun parseDate(raw: String?): LocalDate? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        return try {
            LocalDate.parse(text.take(10), DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (_: Exception) {
            null
        }
    }

    private fun startMinutes(time: String): Int? {
        val start = time.substringBefore('-').trim()
        val parts = start.split(':')
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        return hour * 60 + minute
    }

    private fun slotDurationMinutes(slot: String): Int? {
        if ('-' !in slot) return null
        val start = startMinutes(slot) ?: return null
        val endText = slot.substringAfter('-').trim()
        val parts = endText.split(':')
        if (parts.size != 2) return null
        val end = (parts[0].toIntOrNull() ?: return null) * 60 +
            (parts[1].toIntOrNull() ?: return null)
        val diff = end - start
        return diff.takeIf { it in 1..300 }
    }

    private fun isSlotInFuture(date: LocalDate?, slot: String): Boolean {
        if (date == null) return true
        val start = startMinutes(slot) ?: return true
        val slotStart = date.atStartOfDay().plusMinutes(start.toLong())
        return slotStart.isAfter(java.time.LocalDateTime.now())
    }

    private fun statusLabel(code: String?): String? = when (code) {
        "001" -> "未使用"
        "002" -> "已使用"
        "003" -> "已取消"
        else -> null
    }

    private fun pick(obj: JSONObject, vararg keys: String): String? {
        for (key in keys) {
            if (!obj.has(key) || obj.isNull(key)) continue
            val value = obj.optString(key).trim()
            if (value.isNotEmpty()) return value
        }
        return null
    }

    private fun pickMessage(obj: JSONObject): String? =
        pick(obj, "msg", "message", "error")

    private fun randomHex(length: Int): String {
        val chars = "0123456789abcdef"
        return buildString(length) {
            repeat(length) { append(chars[Random.nextInt(chars.length)]) }
        }
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/118.0 Mobile Safari/537.36"
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0 Safari/537.36"
        private const val SERVICE_CARD_WID = "8558486040491173"
        private const val GYM_SERVICE_TYPE_ID = "1296107574652694529"
        private const val GYM_APP_NAME = "体育场馆预约"
        private const val GYM_DIRECT_ENTRY =
            "https://ehall.wyu.edu.cn/qljfwapp/sys/lwWiseduCgyy/index.do"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
