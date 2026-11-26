package com.wyutools.app.campus

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * Portal protocol for the campus network.
 *
 * Shape of one session:
 *
 *  1. Hit a plain-HTTP generate_204 endpoint. While unauthenticated the gateway
 *     answers 302 and the Location header is the portal URL, which carries
 *     wlanuserip / wlanacip. Those two are mandatory later, so everything starts
 *     here — no portal URL, no login.
 *  2. POST the ticket endpoint with the zero Algo-ID. The answer is a ZSM blob
 *     (bytes starting with "001") that carries the Algo-ID to install. Unsupported
 *     ids are dropped and the identity regenerated, because reusing the same
 *     client-id would only fetch the same rejected id again.
 *  3. With the cipher installed, POST ticket → login → heartbeat, every packet an
 *     encrypted XML body, every answer encrypted in turn.
 *  4. Disconnect is a final encrypted packet to term.cgi — the portal does
 *     implement logout, so no session is left dangling on the server.
 *
 * Redirects are deliberately not followed on the shared client: the portal URL is
 * exactly the value of the 302 Location header.
 */
class CampusApi(private val context: Context) {

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private var session: CampusSession? = null

    val currentSession: CampusSession? get() = session

    suspend fun connect(
        identity: CampusIdentity,
        account: String,
        password: String,
        log: (String) -> Unit,
    ): CampusSession = withContext(Dispatchers.IO) {
        if (!CampusCipher.available) {
            throw CampusException("当前设备缺少密码库，校园网功能不可用。")
        }
        CampusCipher.release()
        val portal = discoverPortal(log)
        log("客户端 IP ${portal.clientIp} / AC ${portal.acIp}")

        val server = pickServer(log)
        val base = "http://$server:${CampusChannel.PORT}"
        val algoId = installAlgo(base, portal, identity, log)
        val active = CampusSession(
            server = base,
            clientIp = portal.clientIp,
            acIp = portal.acIp,
            algoId = algoId,
            identity = identity,
        )

        val ticket = fetchTicket(active, log)
        val keep = login(active, account, password, ticket, log)
        val established = active.copy(ticket = ticket, keepSeconds = keep)
        session = established
        established
    }

    /**
     * Fetches the ZSM and installs the cipher it names. Retries with the same
     * zero id when the blob is unreadable or names an unsupported algorithm.
     */
    private fun installAlgo(
        base: String,
        portal: PortalEndpoint,
        identity: CampusIdentity,
        log: (String) -> Unit,
    ): String {
        val url = "$base/ticket.cgi?wlanuserip=${portal.clientIp}&wlanacip=${portal.acIp}"
        repeat(MAX_ALGO_ATTEMPTS) { attempt ->
            val response = post(url, ZERO_ALGO)
            val bytes = response.body?.bytes()
            response.close()
            if (bytes == null || bytes.isEmpty()) throw CampusException("门户无响应，请确认已连上校园 WiFi。")
            if (!looksLikeZsm(bytes)) {
                throw CampusException("门户未下发算法标识，请稍后重试。")
            }
            val algo = parseAlgoId(bytes)
            if (algo == null) {
                log("未解析出算法标识（${bytes.size} 字节），重试 ${attempt + 1}")
                return@repeat
            }
            if (!CampusCipher.supported(algo)) {
                log("算法 $algo 不受支持，重试 ${attempt + 1}")
                return@repeat
            }
            if (!CampusCipher.install(algo)) {
                log("算法 $algo 初始化失败，重试 ${attempt + 1}")
                return@repeat
            }
            log("算法标识 $algo")
            return algo
        }
        throw CampusException("门户下发的密码算法不受支持，暂时无法认证。")
    }

    private fun fetchTicket(active: CampusSession, log: (String) -> Unit): String {
        val url = "${active.server}/ticket.cgi?wlanuserip=${active.clientIp}&wlanacip=${active.acIp}"
        val plain = exchange(url, payload(active, Payload.Ticket), log)
            ?: throw CampusException("获取票据失败。")
        val ticket = tag(plain, "ticket") ?: throw CampusException("票据解析失败。")
        log("票据已获取")
        return ticket
    }

    private fun login(
        active: CampusSession,
        account: String,
        password: String,
        ticket: String,
        log: (String) -> Unit,
    ): Long {
        val withTicket = active.copy(ticket = ticket)
        val plain = exchange(
            "${withTicket.server}/auth.cgi",
            payload(withTicket, Payload.Login, account = account, password = password),
            log,
        ) ?: throw CampusException("认证失败：门户未返回结果。")
        val keep = tag(plain, "keep-retry")?.toLongOrNull() ?: DEFAULT_KEEP_SECONDS
        log("认证成功，心跳间隔 ${keep}s")
        return keep
    }

    /** Returns the server-reported next interval, or null when the session is gone. */
    fun heartbeat(active: CampusSession, log: (String) -> Unit): Long? {
        val plain = exchange("${active.server}/keep.cgi", payload(active, Payload.Heartbeat), log)
            ?: return null
        return tag(plain, "interval")?.toLongOrNull() ?: active.keepSeconds
    }

    /** Logout: a final encrypted packet to term.cgi. */
    fun disconnect(active: CampusSession, log: (String) -> Unit): Boolean {
        val encrypted = CampusCipher.encrypt(payload(active, Payload.Terminate))
        if (encrypted == null) {
            log("断连报文构造失败")
            return false
        }
        val response = try {
            post("${active.server}/term.cgi", encrypted)
        } catch (e: CampusException) {
            log("断连请求失败：${e.message}")
            return false
        }
        val code = response.header("Error-Code")
        response.close()
        log("断连返回 Error-Code=${code ?: "无"}")
        session = null
        CampusCipher.release()
        return code == null || code == "0" || code == "200"
    }

    // ---- portal discovery -------------------------------------------------

    fun discoverPortal(log: (String) -> Unit): PortalEndpoint {
        val response = try {
            http.newCall(Request.Builder().url(PROBE_URL).get().build()).execute()
        } catch (e: IOException) {
            throw CampusException("网络不可达，请先连接校园 WiFi。")
        }
        val code = response.code
        val location = response.header("Location")
        val resolved = location?.let { response.request.url.resolve(it)?.toString() }
        val finalUrl = resolved ?: response.request.url.toString()
        response.close()

        if (code == 204 || code == 200) {
            // Already online: the gateway let the probe straight through.
            throw CampusException("当前网络已可直接上网，无需认证。")
        }
        if (finalUrl.isBlank()) throw CampusException("未获取到认证门户地址，请稍后重试。")

        var clientIp = query(finalUrl, "wlanuserip")
        var acIp = query(finalUrl, "wlanacip")
        if (clientIp.isNullOrBlank() || acIp.isNullOrBlank()) {
            val page = fetchPortalPage(finalUrl, log)
            if (clientIp.isNullOrBlank()) clientIp = regexParam(page, "wlanuserip")
            if (acIp.isNullOrBlank()) acIp = regexParam(page, "wlanacip")
        }
        if (clientIp.isNullOrBlank()) throw CampusException("门户未提供客户端 IP，无法认证。")
        if (acIp.isNullOrBlank()) throw CampusException("门户未提供网关地址，无法认证。")
        return PortalEndpoint(portalUrl = finalUrl, clientIp = clientIp, acIp = acIp)
    }

    private fun fetchPortalPage(url: String, log: (String) -> Unit): String {
        var current = url
        repeat(4) {
            val response = try {
                http.newCall(Request.Builder().url(current).get().build()).execute()
            } catch (e: IOException) {
                log("门户页获取失败：${e.message}")
                return ""
            }
            val next = response.header("Location")
            if (response.code in 300..399 && next != null) {
                current = response.request.url.resolve(next)?.toString() ?: current
                response.close()
                return@repeat
            }
            val body = response.body?.string().orEmpty()
            response.close()
            return body
        }
        return ""
    }

    // ---- transport --------------------------------------------------------

    private fun exchange(url: String, xml: String, log: (String) -> Unit): String? {
        val encrypted = CampusCipher.encrypt(xml) ?: run {
            log("报文加密失败")
            return null
        }
        val response = post(url, encrypted)
        val code = response.header("Error-Code")
        val raw = response.body?.string().orEmpty()
        response.close()

        if (code != null && code != "0" && code != "200") {
            log("门户返回 Error-Code=$code")
            throw CampusException(describeError(code))
        }
        if (raw.isBlank()) {
            log("门户响应为空")
            return null
        }
        return CampusCipher.decrypt(raw) ?: run {
            log("响应解密失败（长度 ${raw.length}）")
            null
        }
    }

    private fun post(url: String, body: String): Response {
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(CONTENT_TYPE.toMediaType()))
            .header("User-Agent", CampusChannel.USER_AGENT)
            .build()
        return try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw CampusException("与门户通信失败：${e.message}")
        }
    }

    // ---- payloads ---------------------------------------------------------

    private fun payload(
        active: CampusSession,
        kind: Payload,
        account: String = "",
        password: String = "",
    ): String {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val identity = active.identity
        return when (kind) {
            Payload.Ticket -> """
                <?xml version="1.0" encoding="utf-8"?>
                <request>
                    <user-agent>${CampusChannel.USER_AGENT}</user-agent>
                    <client-id>${identity.clientId}</client-id>
                    <local-time>$time</local-time>
                    <host-name>${identity.hostName}</host-name>
                    <ipv4>${active.clientIp}</ipv4>
                    <ipv6></ipv6>
                    <mac>${identity.mac}</mac>
                    <ostag>${CampusChannel.OSTAG}</ostag>
                    <gwip>${gatewayIp().orEmpty()}</gwip>
                </request>
            """.trimIndent()

            Payload.Login -> """
                <?xml version="1.0" encoding="utf-8"?>
                <request>
                    <user-agent>${CampusChannel.USER_AGENT}</user-agent>
                    <client-id>${identity.clientId}</client-id>
                    <ticket>${active.ticket}</ticket>
                    <local-time>$time</local-time>
                    <userid>$account</userid>
                    <passwd>$password</passwd>
                </request>
            """.trimIndent()

            Payload.Heartbeat, Payload.Terminate -> """
                <?xml version="1.0" encoding="utf-8"?>
                <request>
                    <user-agent>${CampusChannel.USER_AGENT}</user-agent>
                    <client-id>${identity.clientId}</client-id>
                    <local-time>$time</local-time>
                    <host-name>${identity.hostName}</host-name>
                    <ipv4>${active.clientIp}</ipv4>
                    <ticket>${active.ticket}</ticket>
                    <ipv6></ipv6>
                    <mac>${identity.mac}</mac>
                    <ostag>${CampusChannel.OSTAG}</ostag>
                </request>
            """.trimIndent()
        }
    }

    // ---- environment ------------------------------------------------------

    private fun pickServer(log: (String) -> Unit): String {
        val reachable = CampusChannel.SERVERS.firstOrNull { server ->
            runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(server, CampusChannel.PORT), 2000)
                }
                true
            }.getOrDefault(false)
        }
        val chosen = reachable ?: CampusChannel.SERVERS.first()
        log("认证服务器 $chosen")
        return chosen
    }

    private fun gatewayIp(): String? = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val props: LinkProperties = cm.activeNetwork?.let { cm.getLinkProperties(it) } ?: return null
        props.routes.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
            ?.gateway?.hostAddress
    }.getOrNull()

    private fun describeError(code: String): String = when {
        code.startsWith("140102") -> "认证被拒绝（$code）：账号状态或密码有问题。"
        code == "140101" -> "账号或密码错误。"
        code == "140200" -> "客户端密钥已过期，请重试。"
        code.startsWith("2001") -> "本地连接异常（$code），请检查 WiFi 后重试。"
        else -> "认证失败（Error-Code=$code）。"
    }

    private enum class Payload { Ticket, Login, Heartbeat, Terminate }

    companion object {
        private const val PROBE_URL = "http://connect.rom.miui.com/generate_204"
        private const val CONTENT_TYPE = "application/x-www-form-urlencoded"
        private const val ZERO_ALGO = "00000000-0000-0000-0000-000000000000"
        private const val MAX_ALGO_ATTEMPTS = 4
        private const val DEFAULT_KEEP_SECONDS = 60L

        private fun looksLikeZsm(bytes: ByteArray): Boolean =
            bytes.size >= 4 &&
                bytes[0] == '0'.code.toByte() &&
                bytes[1] == '0'.code.toByte() &&
                bytes[2] == '1'.code.toByte()

        /**
         * The Algo-ID sits inside the ZSM as two length-prefixed strings right after a
         * three byte header; whichever of them looks like a UUID is the id. Older blobs
         * only carry it as the trailing 36 bytes, hence the fallback.
         */
        internal fun parseAlgoId(bytes: ByteArray): String? {
            if (bytes.size >= 5) {
                var offset = 3
                fun readPascal(): ByteArray? {
                    if (offset >= bytes.size) return null
                    val len = bytes[offset].toInt() and 0xFF
                    offset++
                    if (offset + len > bytes.size) return null
                    val slice = bytes.copyOfRange(offset, offset + len)
                    offset += len
                    return slice
                }
                val first = readPascal()
                val second = readPascal()
                second?.takeIf(::isUuid)?.let { return String(it, Charsets.US_ASCII).uppercase() }
                first?.takeIf(::isUuid)?.let { return String(it, Charsets.US_ASCII).uppercase() }
            }
            return tailUuid(bytes)
        }

        private fun tailUuid(bytes: ByteArray): String? {
            var end = bytes.size
            while (end > 0 && bytes[end - 1].toInt().toChar() in listOf('\n', '\r', '\u0000', ' ', '\t')) end--
            if (end < 36) return null
            val tail = bytes.copyOfRange(end - 36, end)
            return if (isUuid(tail)) String(tail, Charsets.US_ASCII).uppercase() else null
        }

        private fun isUuid(data: ByteArray): Boolean {
            if (data.size != 36) return false
            val hyphens = setOf(8, 13, 18, 23)
            return data.withIndex().all { (i, b) ->
                val c = b.toInt().toChar()
                if (i in hyphens) c == '-' else c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'
            }
        }

        private fun tag(xml: String, name: String): String? {
            val start = xml.indexOf("<$name>")
            if (start < 0) return null
            val from = start + name.length + 2
            val end = xml.indexOf("</$name>", from)
            if (end <= from) return null
            return xml.substring(from, end)
        }

        private fun query(url: String, key: String): String? = runCatching {
            android.net.Uri.parse(url).getQueryParameter(key)
        }.getOrNull()

        private fun regexParam(text: String, key: String): String? =
            Regex("$key=([^&\"'<>\\s]+)").find(text)?.groupValues?.getOrNull(1)
    }
}

data class PortalEndpoint(
    val portalUrl: String,
    val clientIp: String,
    val acIp: String,
)

data class CampusSession(
    val server: String,
    val clientIp: String,
    val acIp: String,
    val algoId: String,
    val identity: CampusIdentity,
    val ticket: String = "",
    val keepSeconds: Long = 60L,
)
