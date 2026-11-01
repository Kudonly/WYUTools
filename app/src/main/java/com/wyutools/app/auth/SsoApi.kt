package com.wyutools.app.auth

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

data class LoginPageFields(
    val salt: String,
    val execution: String,
    val lt: String,
)

data class SsoProfile(
    val name: String,
    val account: String,
    val dept: String?,
)

class SliderChallenge(
    val bigImage: ByteArray,
    val smallImage: ByteArray,
    val tagWidth: Int,
    val yHeight: Int,
    val safeSecure: String,
)

data class SliderTrack(val a: Int, val b: Int, val c: Int)

data class SliderPayload(
    val canvasLength: Int,
    val moveLength: Int,
    val tracks: List<SliderTrack>,
)

data class SliderVerifyResult(val passed: Boolean, val message: String?)

/** 服务端未登录时会把字段返回成 JSON null，org.json 的 optString 会得到 "null"。 */
internal fun sanitizeWyuText(raw: String?): String? {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return null
    if (text.equals("null", ignoreCase = true)) return null
    if (text.equals("undefined", ignoreCase = true)) return null
    return text
}

data class CookieEntry(
    val name: String,
    val value: String,
    val domain: String,
    val path: String,
)

class SsoException(
    message: String,
    val sessionExpired: Boolean = false,
) : Exception(message)

/**
 * 五邑大学统一身份认证 + 教务系统入口。
 *
 * 移植自 shiyi 的 wyu_portal_api：
 * 登录页解析 -> checkNeedCaptcha（必要时滑块）-> AES-CBC 加密密码 ->
 * POST 登录 -> 跟随 302 -> getLoginUserAndGuest 校验。
 */
class SsoApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    private val cookies = SsoCookieStore()

    private val jxglMutex = Mutex()

    private data class RawResponse(
        val code: Int,
        val body: String,
        val url: HttpUrl,
        val location: String?,
    )

    fun snapshotCookies(): String = cookies.snapshot()

    fun restoreCookies(raw: String) = cookies.restore(raw)

    fun clearCookies() = cookies.clear()

    fun cookieEntries(): List<CookieEntry> = cookies.export()

    /**
     * ehall 与认证中心的合并 Cookie 头。
     * 体育馆等入口会经 authserver 换票，必须同时携带 CASTGC；
     * 认证中心 Cookie 的路径是 /authserver，因此这里只按域名匹配、不按路径过滤。
     * 同名 Cookie 以靠后的域（认证中心）为准。
     */
    fun ssoCookieHeader(): String? = cookies.headersForHosts(
        listOf("ehall.wyu.edu.cn", "authserver.wyu.edu.cn"),
    )

    suspend fun fetchLoginPage(): LoginPageFields = withContext(Dispatchers.IO) {
        val response = execute(newGet(loginUrl()))
        if (response.code != 200 || response.body.isBlank()) {
            throw SsoException("登录页访问失败（HTTP ${response.code}），请稍后重试。")
        }
        parseLoginPage(response.body)
    }

    /**
     * 与 shiyi 一致：用独立会话探测，避免影响登录页 cookie。
     * 网络异常时按“不需要滑块”处理。
     */
    suspend fun checkNeedCaptcha(username: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val ts = System.currentTimeMillis()
            val url = "https://authserver.wyu.edu.cn/authserver/checkNeedCaptcha.htl" +
                "?username=${URLEncoder.encode(username, "UTF-8")}&_=$ts"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code != 200) return@withContext false
                val body = response.body?.string().orEmpty().trim()
                if (body.isEmpty()) return@withContext false
                val json = runCatching { JSONObject(body) }.getOrNull()
                    ?: return@withContext false
                val need = json.opt("isNeed")
                need == true || need?.toString() == "true"
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun triggerSlider() = withContext(Dispatchers.IO) {
        runCatching {
            execute(newGet("https://authserver.wyu.edu.cn/authserver/common/toSliderCaptcha.htl"))
        }
        Unit
    }

    suspend fun openSliderCaptcha(): SliderChallenge = withContext(Dispatchers.IO) {
        val url = "https://authserver.wyu.edu.cn/authserver/common/openSliderCaptcha.htl" +
            "?_=${System.currentTimeMillis()}"
        val response = execute(newGet(url))
        if (response.code != 200) {
            throw SsoException("拉取滑块图片失败（HTTP ${response.code}）。")
        }
        val json = runCatching { JSONObject(response.body) }.getOrNull()
            ?: throw SsoException("滑块数据格式异常，请重试。")
        val big = decodeBase64(json.optString("bigImage"))
        val small = decodeBase64(json.optString("smallImage"))
        if (big.isEmpty() || small.size < 16) {
            throw SsoException("滑块图片数据异常，请重试。")
        }
        val safeSecure = String(
            small.copyOfRange(small.size - 16, small.size),
            Charsets.US_ASCII,
        )
        SliderChallenge(
            bigImage = big,
            smallImage = small,
            tagWidth = json.optInt("tagWidth"),
            yHeight = json.optInt("yHeight"),
            safeSecure = safeSecure,
        )
    }

    suspend fun verifySliderCaptcha(
        challenge: SliderChallenge,
        payload: SliderPayload,
    ): SliderVerifyResult = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("canvasLength", payload.canvasLength)
            put("moveLength", payload.moveLength)
            put("tracks", JSONArray().apply {
                payload.tracks.forEach { track ->
                    put(
                        JSONObject()
                            .put("a", track.a)
                            .put("b", track.b)
                            .put("c", track.c),
                    )
                }
            })
        }.toString()
        val sign = SsoCrypto.encrypt(body, challenge.safeSecure)
        val response = execute(
            newFormPost(
                "https://authserver.wyu.edu.cn/authserver/common/verifySliderCaptcha.htl",
                mapOf("sign" to sign),
            ),
        )
        if (response.code != 200) {
            throw SsoException("滑块校验失败（HTTP ${response.code}）。")
        }
        val json = runCatching { JSONObject(response.body) }.getOrNull()
            ?: throw SsoException("滑块校验响应异常。")
        val errorCode = json.opt("errorCode")
        val passed = errorCode?.toString() == "1"
        val message = json.optString("msg").trim()
            .ifEmpty { json.optString("errorMsg").trim() }
            .ifEmpty { null }
        SliderVerifyResult(passed = passed, message = message)
    }

    suspend fun submitLogin(
        username: String,
        password: String,
        page: LoginPageFields,
    ): SsoProfile = withContext(Dispatchers.IO) {
        val payload = linkedMapOf(
            "username" to username,
            "password" to SsoCrypto.encrypt(password, page.salt),
            "_eventId" to "submit",
            "cllt" to "userNameLogin",
            "dllt" to "generalLogin",
            "lt" to page.lt,
            "execution" to page.execution,
        )
        var response = execute(newFormPost(loginUrl(), payload))

        if (response.code == 200 && looksLikeKickOut(response.body)) {
            response = handleKickOut(response.body)
        }

        if (response.code != 302) {
            throw SsoException(mapLoginFailure(response.body))
        }

        val location = response.location.orEmpty()
        if (location.contains("needCaptcha")) {
            throw SsoException("当前账号需要图形验证码，请稍后再试。")
        }
        followRedirects(response.url, location)

        val profile = fetchProfile()
            ?: throw SsoException("登录态校验失败，请稍后重试。")
        profile
    }

    /** 返回 null 表示登录态已失效；网络异常会抛出 IOException。 */
    suspend fun fetchProfile(): SsoProfile? = withContext(Dispatchers.IO) {
        val url = "https://ehall.wyu.edu.cn/getLoginUserAndGuest" +
            "?_t=${System.currentTimeMillis()}"
        val response = execute(newGet(url))
        if (response.code != 200) return@withContext null
        val root = runCatching { JSONObject(response.body.trim()) }.getOrNull()
            ?: return@withContext null
        val data = root.optJSONObject("data") ?: return@withContext null
        val account = sanitizeWyuText(data.optString("userAccount"))
            ?: return@withContext null
        val name = sanitizeWyuText(data.optString("userName")) ?: account
        val dept = sanitizeWyuText(data.optString("deptName"))
        SsoProfile(name = name, account = account, dept = dept)
    }

    /**
     * 建立教务系统应用态会话。
     *
     * 优先使用 ehall「系统直通车」卡片的 pcAccessUrl；部分账号的卡片
     * 返回空 appData，此时回退到 jxgl 自己的 CAS 入口，直接凭统一认证
     * 会话换取 jxgl 的 JSESSIONID。
     */
    suspend fun establishJxgl(): String = withContext(Dispatchers.IO) {
        jxglMutex.withLock { establishJxglLocked() }
    }

    private fun establishJxglLocked(): String {
        val candidates = buildList {
            fetchJxglCardEntry()?.let { add(it) }
            add(JXGL_DIRECT_ENTRY)
        }
        var lastError: SsoException? = null
        for (entry in candidates) {
            try {
                warmUpJxgl(entry)
                return entry
            } catch (error: SsoException) {
                if (error.sessionExpired) throw error
                lastError = error
            }
        }
        throw lastError ?: SsoException("教务系统访问失败，请稍后重试。")
    }

    /**
     * 拉取教务课表原始 JSON。
     *
     * 先直接复用现有 jxgl 会话；被踢回统一认证时重建一次会话再重试。
     * 返回原始响应给 schedule 层解析，避免认证层依赖课表模型。
     */
    suspend fun fetchJxglSchedule(termCode: String): String = withContext(Dispatchers.IO) {
        fun request(): RawResponse {
            // 与教务系统的课表页保持一致：只带学期，zc 留空表示整学期
            val body = FormBody.Builder()
                .add("xnxqdm", termCode)
                .add("zc", "")
                .build()
            val request = newRequest(JXGL_SCHEDULE_DATA_URL)
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", JXGL_SCHEDULE_PAGE_URL)
                .post(body)
                .build()
            return execute(request)
        }

        var response = request()
        if (scheduleNeedsLogin(response)) {
            establishJxgl()
            response = request()
        }
        if (response.code != 200) {
            throw SsoException("课表获取失败（HTTP ${response.code}），请稍后重试。")
        }
        if (scheduleNeedsLogin(response)) {
            throw SsoException("登录已失效，请重新登录。", sessionExpired = true)
        }
        if (response.body.trim().startsWith("<")) {
            throw SsoException("教务系统返回了异常页面，请稍后重试。")
        }
        checkAuthPayload(
            runCatching { JSONObject(response.body.trim()) }.getOrNull()
                ?: throw SsoException("课表数据格式异常，请稍后重试。"),
        )
        response.body
    }

    private fun scheduleNeedsLogin(response: RawResponse): Boolean {
        if (response.code == 401 || response.code == 403) return true
        if (isAuthServerUrl(response.url.toString())) return true
        val location = response.location
        if (location != null && (isAuthServerUrl(location) || location.contains("login", true))) {
            return true
        }
        val body = response.body
        if (SESSION_CODE_PATTERN.containsMatchIn(body)) return true
        if (looksLikeLoginPage(body)) return true
        return false
    }

    /** 返回 null 表示卡片未提供可用入口（入口丢失或列表为空）。 */
    private fun fetchJxglCardEntry(): String? {
        val payload = JSONObject().apply {
            put("cardId", "SYS_CARD_SERVICEBUS")
            put("cardWid", JXGL_SERVICE_CARD_WID)
            put("method", "renderData")
            put("param", JSONObject().put("lang", "zh_CN"))
        }
        val url = "https://ehall.wyu.edu.cn/execCardMethod/$JXGL_SERVICE_CARD_WID" +
            "/SYS_CARD_SERVICEBUS?n=${Math.random()}"
        // 卡片接口按 UA 下发不同服务列表，移动端 UA 会得到空 appData
        val request = newRequest(url)
            .header("User-Agent", DESKTOP_USER_AGENT)
            .header("Accept", "application/json, text/plain, */*")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val response = execute(request)
        if (response.location != null) {
            throw SsoException("登录已失效，请重新登录。", sessionExpired = true)
        }
        if (response.code != 200) return null
        val root = runCatching { JSONObject(response.body) }.getOrNull() ?: return null
        checkAuthPayload(root)
        val appData = root.optJSONObject("data")?.optJSONArray("appData") ?: return null

        for (index in 0 until appData.length()) {
            val service = appData.optJSONObject(index) ?: continue
            val name = service.optString("appName").trim()
                .ifEmpty { service.optString("serviceName").trim() }
            if (name == JXGL_APP_NAME) {
                val entry = service.optString("pcAccessUrl").trim()
                if (entry.isNotEmpty()) return entry
            }
        }
        return null
    }

    suspend fun logout() {
        withContext(Dispatchers.IO) {
            try {
                val service = URLEncoder.encode(
                    "https://ehall.wyu.edu.cn/default/index.html",
                    "UTF-8",
                )
                execute(
                    newGet(
                        "https://authserver.wyu.edu.cn/authserver/logout?service=$service",
                    ),
                )
            } catch (_: Exception) {
                // 远端注销失败也照常清本地会话
            } finally {
                cookies.clear()
            }
        }
    }

    // -------------------- 内部流程 --------------------

    private fun warmUpJxgl(entry: String) {
        var url = entry
        repeat(8) {
            val response = execute(newGet(url))
            if (response.code in 300..399) {
                val location = response.location ?: return
                url = response.url.resolve(location)?.toString() ?: return
                return@repeat
            }
            if (response.code != 200) {
                throw SsoException("教务系统访问失败（HTTP ${response.code}）。")
            }
            if (isAuthServerUrl(response.url.toString()) || looksLikeLoginPage(response.body)) {
                throw SsoException("登录已失效，请重新登录。", sessionExpired = true)
            }
            verifyJxglHome()
            return
        }
        throw SsoException("教务系统访问超时，请稍后重试。")
    }

    /** 换票页返回 200 不代表会话真的可用，再用首页确认没有被打回统一认证。 */
    private fun verifyJxglHome() {
        var url = JXGL_HOME
        repeat(4) {
            val response = execute(newGet(url))
            if (response.code in 300..399) {
                val location = response.location ?: return
                if (isAuthServerUrl(location)) {
                    throw SsoException("登录已失效，请重新登录。", sessionExpired = true)
                }
                url = response.url.resolve(location)?.toString() ?: return
                return@repeat
            }
            if (response.code != 200) {
                throw SsoException("教务系统访问失败（HTTP ${response.code}）。")
            }
            if (isAuthServerUrl(response.url.toString()) || looksLikeLoginPage(response.body)) {
                throw SsoException("登录已失效，请重新登录。", sessionExpired = true)
            }
            return
        }
        throw SsoException("教务系统访问超时，请稍后重试。")
    }

    private fun isAuthServerUrl(url: String): Boolean =
        url.contains(AUTH_SERVER_HOST, ignoreCase = true)

    private fun followRedirects(from: HttpUrl, location: String) {
        var url = from.resolve(location) ?: return
        repeat(6) {
            val response = execute(newGet(url.toString()))
            if (response.code !in 300..399) return
            val next = response.location ?: return
            url = response.url.resolve(next) ?: return
        }
    }

    private fun handleKickOut(body: String): RawResponse {
        val form = Regex(
            "<form[^>]*id=\"continue\"[^>]*>.*?</form>",
            RegexOption.DOT_MATCHES_ALL,
        ).find(body)?.value ?: throw SsoException("检测到会话冲突，请稍后重试。")

        val execution = Regex("name=\"execution\"\\s+value=\"([^\"]+)\"")
            .find(form)?.groupValues?.getOrNull(1)
            ?: throw SsoException("会话冲突处理失败，请稍后重试。")
        val eventId = Regex("name=\"_eventId\"\\s+value=\"([^\"]+)\"")
            .find(form)?.groupValues?.getOrNull(1)
            ?: throw SsoException("会话冲突处理失败，请稍后重试。")

        val response = execute(
            newFormPost(
                loginUrl(),
                mapOf("execution" to execution, "_eventId" to eventId),
            ),
        )
        if (response.code != 302) {
            throw SsoException("接管旧会话失败，请稍后重试。")
        }
        return response
    }

    private fun checkAuthPayload(root: JSONObject) {
        val code = root.optString("code").trim()
        if (code == "-401" || code == "401") {
            throw SsoException("登录已失效，请重新登录。", sessionExpired = true)
        }
        val message = listOf("msg", "message", "error")
            .map { root.optString(it).trim() }
            .firstOrNull { it.isNotEmpty() }
        if (message != null && SESSION_EXPIRED_HINTS.any { message.contains(it) }) {
            throw SsoException("登录已失效，请重新登录。", sessionExpired = true)
        }
    }

    private fun parseLoginPage(html: String): LoginPageFields {
        val salt = extractInputValue(html, "pwdEncryptSalt").orEmpty()
        val execution = extractInputValue(html, "execution")
            ?: throw SsoException("统一认证登录页结构已变化，请稍后重试。")
        val lt = extractInputValue(html, "lt").orEmpty()
        if (salt.isEmpty()) {
            throw SsoException("统一认证登录页结构已变化，请稍后重试。")
        }
        return LoginPageFields(salt = salt, execution = execution, lt = lt)
    }

    private fun extractInputValue(html: String, id: String): String? {
        val tag = Regex("<input[^>]*\\bid=\"$id\"[^>]*>", RegexOption.IGNORE_CASE)
            .find(html)?.value
            ?: Regex("<input[^>]*\\bname=\"$id\"[^>]*>", RegexOption.IGNORE_CASE)
                .find(html)?.value
            ?: return null
        return Regex("\\bvalue=\"([^\"]*)\"").find(tag)?.groupValues?.getOrNull(1)
    }

    private fun mapLoginFailure(body: String): String {
        if (body.contains("您提供的用户名或者密码有误")) {
            return "学号或密码错误。"
        }
        if (body.contains("验证码")) {
            return "当前账号需要验证码登录，请稍后再试。"
        }
        val message = Regex("<span\\s+id=\"showErrorTip\"[^>]*>([^<]*)</span>")
            .find(body)?.groupValues?.getOrNull(1)?.trim()
        if (!message.isNullOrEmpty()) return message
        return "登录失败，请检查学号密码或稍后重试。"
    }

    private fun looksLikeKickOut(body: String): Boolean =
        body.contains("kick-out") || body.contains("踢出会话")

    private fun looksLikeLoginPage(body: String): Boolean =
        looksLikeSchoolLogin(body) || looksLikeJxglLogin(body)

    private fun looksLikeSchoolLogin(body: String): Boolean {
        if (!body.trimStart().startsWith("<")) return false
        return body.contains("id=\"pwdEncryptSalt\"") ||
            body.contains("authserver/login") ||
            body.contains("id=\"casLoginForm\"") ||
            body.contains("authserver.wyu.edu.cn/authserver")
    }

    /** JXGL 未登录时首页直接返回 200 的自有登录表单，而不是跳转认证中心。 */
    private fun looksLikeJxglLogin(body: String): Boolean {
        if (!body.trimStart().startsWith("<")) return false
        return body.contains("id=\"login-form\"") && body.contains("/new/ssoLogin")
    }

    // -------------------- HTTP 基础 --------------------

    private fun loginUrl(): String {
        val service = URLEncoder.encode(SERVICE_URL, "UTF-8")
        return "$LOGIN_URL?service=$service"
    }

    private fun newGet(url: String): Request = newRequest(url).get().build()

    private fun newFormPost(url: String, fields: Map<String, String>): Request {
        val body = FormBody.Builder().apply {
            fields.forEach { (key, value) -> add(key, value) }
        }.build()
        return newRequest(url).post(body).build()
    }

    private fun newRequest(url: String): Request.Builder {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
        cookies.headerFor(url.toHttpUrl())?.let { builder.header("Cookie", it) }
        return builder
    }

    private fun execute(request: Request): RawResponse {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            cookies.updateFrom(response.request.url, response.headers("Set-Cookie"))
            return RawResponse(
                code = response.code,
                body = body,
                url = response.request.url,
                location = response.header("Location"),
            )
        }
    }

    private fun decodeBase64(raw: String): ByteArray {
        if (raw.isBlank()) return ByteArray(0)
        return runCatching {
            Base64.decode(raw, Base64.DEFAULT)
        }.getOrDefault(ByteArray(0))
    }

    companion object {
        internal const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/118.0 Mobile Safari/537.36"
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0 Safari/537.36"
        private const val LOGIN_URL = "https://authserver.wyu.edu.cn/authserver/login"
        private const val SERVICE_URL = "https://ehall.wyu.edu.cn/login"
        private const val JXGL_SERVICE_CARD_WID = "017434820995445355"
        private const val JXGL_APP_NAME = "教务系统"
        private const val JXGL_DIRECT_ENTRY = "https://jxgl.wyu.edu.cn/new/ssoLogin"
        private const val JXGL_HOME = "https://jxgl.wyu.edu.cn/"
        private const val JXGL_SCHEDULE_PAGE_URL =
            "https://jxgl.wyu.edu.cn/new/student/xsgrkb/main.page"
        private const val JXGL_SCHEDULE_DATA_URL =
            "https://jxgl.wyu.edu.cn/new/student/xsgrkb/getCalendarWeekDatas"
        private const val AUTH_SERVER_HOST = "authserver.wyu.edu.cn"
        private val SESSION_CODE_PATTERN = Regex("\"code\"\\s*:\\s*-401")
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val SESSION_EXPIRED_HINTS = listOf(
            "尚未登录",
            "请先登录",
            "登录失效",
            "已在别处登录",
            "被迫退出",
        )
    }
}

internal object SsoCrypto {
    private const val AES_CHARS = "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678"
    private val random = java.security.SecureRandom()

    fun encrypt(plain: String, salt: String): String {
        if (salt.isEmpty()) return plain
        val prefixed = randomString(64) + plain
        val iv = randomString(16)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(salt.toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec(iv.toByteArray(Charsets.UTF_8)),
        )
        val encrypted = cipher.doFinal(prefixed.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun randomString(length: Int): String = buildString {
        repeat(length) { append(AES_CHARS[random.nextInt(AES_CHARS.length)]) }
    }
}

internal class SsoCookieStore {
    private data class Entry(
        val name: String,
        val value: String,
        val domain: String,
        val path: String,
        val expiresAt: Long,
    )

    private val entries = mutableListOf<Entry>()

    @Synchronized
    fun updateFrom(url: HttpUrl, setCookieHeaders: List<String>) {
        for (header in setCookieHeaders) {
            val cookie = runCatching { Cookie.parse(url, header) }.getOrNull() ?: continue
            entries.removeAll {
                it.name == cookie.name &&
                    it.domain == cookie.domain &&
                    it.path == cookie.path
            }
            if (cookie.expiresAt > System.currentTimeMillis()) {
                entries.add(
                    Entry(
                        name = cookie.name,
                        value = cookie.value,
                        domain = cookie.domain,
                        path = cookie.path,
                        expiresAt = cookie.expiresAt,
                    ),
                )
            }
        }
    }

    @Synchronized
    fun headerFor(url: HttpUrl): String? {
        val now = System.currentTimeMillis()
        val matched = entries.filter {
            it.expiresAt > now &&
                domainMatches(url.host, it.domain) &&
                url.encodedPath.startsWith(it.path)
        }
        if (matched.isEmpty()) return null
        return matched.joinToString("; ") { "${it.name}=${it.value}" }
    }

    /** 只按域名匹配、忽略路径，用于需要跨系统转发的合并 Cookie 头。 */
    @Synchronized
    fun headersForHosts(hosts: List<String>): String? {
        val now = System.currentTimeMillis()
        val merged = LinkedHashMap<String, String>()
        for (host in hosts) {
            for (entry in entries) {
                if (entry.expiresAt <= now) continue
                if (!domainMatches(host, entry.domain)) continue
                merged[entry.name] = "${entry.name}=${entry.value}"
            }
        }
        if (merged.isEmpty()) return null
        return merged.values.joinToString("; ")
    }

    @Synchronized
    fun clear() = entries.clear()

    @Synchronized
    fun snapshot(): String {
        val array = JSONArray()
        val now = System.currentTimeMillis()
        for (entry in entries) {
            if (entry.expiresAt <= now) continue
            array.put(
                JSONObject()
                    .put("name", entry.name)
                    .put("value", entry.value)
                    .put("domain", entry.domain)
                    .put("path", entry.path)
                    .put("expiresAt", entry.expiresAt),
            )
        }
        return array.toString()
    }

    @Synchronized
    fun restore(raw: String) {
        entries.clear()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return
        val now = System.currentTimeMillis()
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            val name = obj.optString("name")
            val domain = obj.optString("domain")
            val expiresAt = obj.optLong("expiresAt", 0L)
            if (name.isEmpty() || domain.isEmpty() || expiresAt <= now) continue
            entries.add(
                Entry(
                    name = name,
                    value = obj.optString("value"),
                    domain = domain,
                    path = obj.optString("path", "/").ifEmpty { "/" },
                    expiresAt = expiresAt,
                ),
            )
        }
    }

    @Synchronized
    fun export(): List<CookieEntry> {
        val now = System.currentTimeMillis()
        return entries.filter { it.expiresAt > now }
            .map { CookieEntry(it.name, it.value, it.domain, it.path) }
    }

    private fun domainMatches(host: String, domain: String): Boolean {
        val normalized = domain.removePrefix(".")
        return host == normalized || host.endsWith(".$normalized")
    }
}
