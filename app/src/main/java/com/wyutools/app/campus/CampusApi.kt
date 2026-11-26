package com.wyutools.app.campus

import android.content.Context
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.security.MessageDigest
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
 *  1. Probe a plain-HTTP generate_204 endpoint from the bound WLAN. While
 *     unauthenticated the gateway answers 302 and the Location header leads to the
 *     portal page, which is the only place the login endpoints are announced.
 *  2. Read the portal page's config block. It carries `<ticket-url>` and
 *     `<auth-url>` — full URLs, query included, possibly on a different host than
 *     the page itself. Constructing these from the server IP works on a standard
 *     deployment, but the announced URLs are the contract; guessing them is the
 *     fallback.
 *  3. POST ticket-url with the zero Algo-ID. The answer is a ZSM blob (bytes
 *     starting with "001") that names the Algo-ID to install. Unsupported ids are
 *     dropped and the identity regenerated, because reusing the same client-id
 *     would only fetch the same rejected id again.
 *  4. With the cipher installed, POST ticket → auth, every packet an encrypted XML
 *     body. The auth answer carries `<keep-url>`, `<term-url>` and `<keep-retry>`;
 *     heartbeats and logout go to those, not to derived paths.
 *
 * Every POST has to carry the portal's client headers (`Algo-ID`, `Client-ID`,
 * `CDC-Checksum` and the school triple). Without them ticket.cgi answers with an
 * empty body instead of the ZSM — which is exactly what a "portal not responding"
 * dead end looks like from the outside.
 *
 * Redirects are deliberately not followed on the shared client: the portal URL is
 * exactly the value of the 302 Location header.
 *
 * Every request goes through [CampusNetGate]'s socket factory. Without it an
 * unauthenticated WLAN has no default route, the traffic leaks onto mobile data
 * and the portal subnet becomes unreachable — see [CampusNetGate] for the detail.
 */
class CampusApi(private val context: Context) {

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        // The portal only answers on the local subnet; a system-wide proxy would
        // send those requests somewhere that cannot resolve them.
        .proxy(Proxy.NO_PROXY)
        .socketFactory(CampusNetGate.liveSocketFactory())
        .build()

    // School identity the portal advertises in its response headers. It is echoed
    // back on every POST, so it has to be captured from whichever answer arrives
    // first — the probe's portal page usually gets there before ticket.cgi.
    private var schoolId = ""
    private var domain = ""
    private var area = ""

    private var session: CampusSession? = null

    val currentSession: CampusSession? get() = session

    /**
     * Decides what the current WLAN needs, without any credentials.
     *
     * This is the "do I need to log in?" question, answered by what the gateway
     * does to a plain HTTP request:
     *  - 204 / 301 / 404 → internet already works, nothing to do;
     *  - 302 → follow the Location chain, a portal page answers 200;
     *  - 200 straight away → the probe itself was served the portal page;
     *  - no answer at all → retry the backup probe, then the auth servers.
     */
    suspend fun probe(log: (String) -> Unit): CampusProbe = withContext(Dispatchers.IO) {
        if (!CampusNetGate.hasWifiOrEthernet(context)) {
            return@withContext CampusProbe(state = CampusNetState.NoNetwork)
        }
        CampusNetGate.bind(context)
        log("网络：${CampusNetGate.describe(context)}")

        var url = PROBE_URL
        var hops = 0
        var primaryFailed = false
        var lastPortal = ""

        while (hops < MAX_PROBE_HOPS) {
            val response = tryGet(url, withAuthHeaders = hops > 0)
            if (response == null) {
                if (!primaryFailed) {
                    primaryFailed = true
                    log("主探测地址不可达，改用备用地址")
                    url = PROBE_BAK_URL
                    hops++
                    continue
                }
                log("探测失败，尝试认证服务器连通性")
                for (server in AUTH_SERVERS) {
                    val result = tryGet(server, withAuthHeaders = false) ?: continue
                    log("认证服务器 $server → ${result.code}")
                    if (result.code == 200 || result.code == 302) {
                        val portal = result.location?.takeIf { it.isNotBlank() } ?: server
                        return@withContext CampusProbe(
                            state = CampusNetState.NeedLogin,
                            portalUrl = portal,
                        )
                    }
                }
                return@withContext CampusProbe(
                    state = CampusNetState.Failed,
                    detail = "网络请求失败，请检查是否已连上校园 WiFi",
                )
            }

            when (response.code) {
                204, 301, 404 -> {
                    log("已可上网（${response.code}），无需认证")
                    return@withContext CampusProbe(state = CampusNetState.Online)
                }

                302 -> {
                    val location = response.location
                    if (location.isNullOrBlank()) {
                        return@withContext CampusProbe(
                            state = CampusNetState.Failed,
                            detail = "门户重定向缺少地址",
                        )
                    }
                    url = resolve(url, location)
                    lastPortal = url
                    hops++
                    log("重定向 → $url")
                }

                200 -> {
                    // The probe got a page instead of an empty answer: that page is
                    // the portal, and the WLAN is not authenticated yet.
                    log("命中门户页 $url")
                    return@withContext CampusProbe(
                        state = CampusNetState.NeedLogin,
                        portalUrl = response.location?.takeIf { it.isNotBlank() } ?: url,
                    )
                }

                else -> {
                    log("意外响应码 ${response.code}")
                    return@withContext CampusProbe(
                        state = CampusNetState.Failed,
                        detail = "意外响应码 ${response.code}",
                    )
                }
            }
        }

        if (lastPortal.isNotEmpty()) {
            return@withContext CampusProbe(
                state = CampusNetState.NeedLogin,
                portalUrl = lastPortal,
            )
        }
        CampusProbe(state = CampusNetState.Failed, detail = "无法判定网络状态")
    }

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

        val found = probe(log)
        when (found.state) {
            CampusNetState.NoNetwork ->
                throw CampusNoNetworkException("未连接校园 WiFi 或以太网，请先连接后再试。")

            CampusNetState.Online ->
                throw CampusException("当前网络已能直接上网，无需认证。")

            CampusNetState.NeedLogin -> Unit

            CampusNetState.Failed ->
                throw CampusException(found.detail)

            CampusNetState.Unknown ->
                throw CampusException("网络状态未知，请重新检测后再试。")
        }

        val portal = readPortal(found.portalUrl, log)
        log("客户端 IP ${portal.clientIp} / AC ${portal.acIp}")
        log("票据地址 ${portal.ticketUrl}")
        log("认证地址 ${portal.authUrl}")

        val algoId = installAlgo(portal.ticketUrl, identity, log)
        val active = CampusSession(
            ticketUrl = portal.ticketUrl,
            authUrl = portal.authUrl,
            clientIp = portal.clientIp,
            acIp = portal.acIp,
            algoId = algoId,
            identity = identity,
        )

        val ticket = fetchTicket(active, log)
        val opened = login(active.copy(ticket = ticket), account, password, log)
        val established = active.copy(
            ticket = ticket,
            keepUrl = opened.keepUrl,
            termUrl = opened.termUrl,
            keepSeconds = opened.keepSeconds,
            // The portal's school triple is echoed on every POST. It has to be
            // persisted with the ticket or a resumed heartbeat misses the headers.
            schoolId = schoolId,
            domain = domain,
            area = area,
        )
        session = established
        established
    }

    /**
     * Rebuilds a session from the flat disk record, the same way the reference
     * client restores `auth_session`: ticket + keep + term must all be present,
     * the native cipher is reinstalled from the saved Algo-ID, and the school
     * triple is put back so heartbeats carry the portal's headers.
     *
     * This only reconstructs memory — the caller still has to heartbeat before
     * claiming the account is logged in.
     */
    fun restore(saved: CampusAuthSession, log: (String) -> Unit): Boolean {
        if (!saved.isValid) {
            log("磁盘会话票据不完整")
            return false
        }
        if (!CampusCipher.available) return false
        CampusCipher.release()
        if (!CampusCipher.install(saved.algoId)) {
            log("恢复会话 algo 无效: ${saved.algoId}")
            return false
        }
        schoolId = saved.schoolId
        domain = saved.domain
        area = saved.area
        session = CampusSession(
            ticketUrl = saved.ticketUrl,
            authUrl = saved.authUrl,
            clientIp = saved.clientIp,
            acIp = saved.acIp,
            algoId = saved.algoId,
            identity = saved.identity,
            ticket = saved.ticket,
            keepUrl = saved.keepUrl,
            termUrl = saved.termUrl,
            keepSeconds = saved.keepSeconds,
            schoolId = saved.schoolId,
            domain = saved.domain,
            area = saved.area,
        )
        log("恢复会话 ticket=${saved.ticket.take(8)}…")
        return true
    }

    /**
     * Finds the login endpoints. The portal page is the authority: it ships a
     * config block (`<!--//config.campus.js.chinatelecom.com … -->`) holding the
     * real `<ticket-url>` and `<auth-url>`, query parameters included. Only when
     * that block is missing do we build the standard URLs from the server IP.
     */
    private fun readPortal(portalUrl: String, log: (String) -> Unit): PortalEndpoint {
        val html = fetchPortalPage(portalUrl, log)
        val config = extractBetween(html, CONFIG_START, CONFIG_END)
        if (config == null) log("门户页未带配置块，按标准地址登录")

        var ticketUrl = config?.let { cleanCdata(tag(it, "ticket-url")) }.orEmpty()
        var authUrl = config?.let { cleanCdata(tag(it, "auth-url")) }.orEmpty()

        // The IP pair is mandatory for every ticket POST; it rides in the URL.
        var clientIp = query(ticketUrl.ifBlank { portalUrl }, "wlanuserip")
            ?: query(portalUrl, "wlanuserip")
        var acIp = query(ticketUrl.ifBlank { portalUrl }, "wlanacip")
            ?: query(portalUrl, "wlanacip")
        if (clientIp.isNullOrBlank()) clientIp = regexParam(html, "wlanuserip")
        if (acIp.isNullOrBlank()) acIp = regexParam(html, "wlanacip")
        if (clientIp.isNullOrBlank()) {
            // Last resort: our own address on the bound network is the same value
            // the gateway would report for this client.
            clientIp = CampusNetGate.localIpv4(context)
        }
        // Same for the AC: the gateway that handed us this address *is* the access
        // controller on this deployment.
        if (acIp.isNullOrBlank()) acIp = CampusNetGate.gatewayIp(context)
        if (clientIp.isNullOrBlank()) throw CampusException("门户未提供客户端 IP，无法认证。")
        if (acIp.isNullOrBlank()) throw CampusException("门户未提供网关地址，无法认证。")

        if (ticketUrl.isBlank() || authUrl.isBlank()) {
            val base = ticketUrl.takeIf { it.isNotBlank() }?.let(::baseOf)
                ?: "http://${pickServer(log)}:${CampusChannel.PORT}"
            if (ticketUrl.isBlank()) {
                ticketUrl = "$base/ticket.cgi?wlanuserip=$clientIp&wlanacip=$acIp"
            }
            if (authUrl.isBlank()) authUrl = "$base/auth.cgi"
        }
        return PortalEndpoint(
            portalUrl = portalUrl,
            clientIp = clientIp,
            acIp = acIp,
            ticketUrl = ticketUrl,
            authUrl = authUrl,
        )
    }

    /**
     * Fetches the ZSM and installs the cipher it names. Retries when the blob is
     * empty, unreadable, or names an unsupported algorithm — an empty answer is the
     * portal refusing the request, so the retry also flips to the empty-body form
     * some portals insist on.
     */
    private fun installAlgo(
        ticketUrl: String,
        identity: CampusIdentity,
        log: (String) -> Unit,
    ): String {
        var lastCode = 0
        var lastSize = 0
        repeat(MAX_ALGO_ATTEMPTS) { attempt ->
            var reply = postXml(ticketUrl, ZERO_ALGO, ZERO_ALGO, identity.clientId)
            // Upstream fallback: a handful of portals answer the first POST with an
            // empty 200 and only hand out the blob when the body is empty.
            if (reply.bytes.isEmpty() && reply.code == 200) {
                log("票据响应为空，按空报体重试")
                reply = postXml(ticketUrl, "", ZERO_ALGO, identity.clientId)
            }
            lastCode = reply.code
            lastSize = reply.bytes.size
            if (reply.bytes.isEmpty()) {
                log("票据响应为空（HTTP ${reply.code}），重试 ${attempt + 1}")
                return@repeat
            }
            if (!looksLikeZsm(reply.bytes)) {
                log("票据响应不是算法报文（HTTP ${reply.code}，${reply.bytes.size} 字节）")
                return@repeat
            }
            val algo = parseAlgoId(reply.bytes)
            if (algo == null) {
                log("未解析出算法标识（${reply.bytes.size} 字节），重试 ${attempt + 1}")
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
        throw CampusException(
            if (lastSize == 0) {
                "门户无响应（HTTP $lastCode），已重试 $MAX_ALGO_ATTEMPTS 次。请确认连着校园 WiFi，稍后再试。"
            } else {
                "门户下发的密码算法不受支持，暂时无法认证。"
            },
        )
    }

    private fun fetchTicket(active: CampusSession, log: (String) -> Unit): String {
        val plain = exchange(active.ticketUrl, payload(active, Payload.Ticket), active, log)
            ?: throw CampusException("获取票据失败：门户返回为空。")
        val ticket = tag(plain, "ticket") ?: throw CampusException("票据解析失败。")
        log("票据已获取")
        return ticket
    }

    /**
     * Login, and with it the two URLs the rest of the session lives on. The portal
     * answers with `<keep-url>` / `<term-url>`; deriving them from the ticket URL
     * is only the fallback for a portal that omits them.
     */
    private fun login(
        active: CampusSession,
        account: String,
        password: String,
        log: (String) -> Unit,
    ): Opened {
        val plain = exchange(
            active.authUrl,
            payload(active, Payload.Login, account = account, password = password),
            active,
            log,
        ) ?: throw CampusException("认证失败：门户未返回结果。")

        val base = baseOf(active.ticketUrl)
        val keepUrl = cleanCdata(tag(plain, "keep-url")).orEmpty().ifBlank { "$base/keep.cgi" }
        val termUrl = cleanCdata(tag(plain, "term-url")).orEmpty().ifBlank { "$base/term.cgi" }
        val keep = tag(plain, "keep-retry")?.trim()?.toLongOrNull() ?: DEFAULT_KEEP_SECONDS
        log("认证成功，心跳间隔 ${keep}s")
        return Opened(keepUrl = keepUrl, termUrl = termUrl, keepSeconds = keep)
    }

    /** Returns the server-reported next interval, or null when the session is gone. */
    fun heartbeat(active: CampusSession, log: (String) -> Unit): Long? {
        val url = active.keepUrl.ifBlank { "${baseOf(active.ticketUrl)}/keep.cgi" }
        val plain = exchange(url, payload(active, Payload.Heartbeat), active, log) ?: return null
        return tag(plain, "interval")?.trim()?.toLongOrNull() ?: active.keepSeconds
    }

    /** Logout: a final encrypted packet to the portal's own term URL. */
    fun disconnect(active: CampusSession, log: (String) -> Unit): Boolean {
        val encrypted = CampusCipher.encrypt(payload(active, Payload.Terminate))
        if (encrypted == null) {
            log("断连报文构造失败")
            return false
        }
        val url = active.termUrl.ifBlank { "${baseOf(active.ticketUrl)}/term.cgi" }
        val reply = try {
            postXml(url, encrypted, active.algoId, active.identity.clientId)
        } catch (e: CampusException) {
            log("断连请求失败：${e.message}")
            return false
        }
        log("断连返回 HTTP ${reply.code} Error-Code=${reply.errorCode ?: "无"}")
        val ok = reply.code in 200..299 &&
            (reply.errorCode == null || reply.errorCode == "0" || reply.errorCode == "200")
        if (ok) {
            session = null
            CampusCipher.release()
        }
        return ok
    }

    // ---- portal page ------------------------------------------------------

    private fun fetchPortalPage(url: String, log: (String) -> Unit): String {
        var current = url
        repeat(4) {
            val response = tryGet(current, withAuthHeaders = false)
            if (response == null) {
                log("门户页获取失败")
                return ""
            }
            val next = response.location
            if (response.code in 300..399 && !next.isNullOrBlank()) {
                current = resolve(current, next)
                return@repeat
            }
            return response.body.orEmpty()
        }
        return ""
    }

    // ---- transport --------------------------------------------------------

    private data class ProbeResponse(
        val code: Int,
        val location: String?,
        val body: String?,
    )

    private data class Reply(
        val code: Int,
        val bytes: ByteArray,
        val location: String?,
        val errorCode: String?,
    )

    /** GET that never throws: null stands for "no answer", like the reference client. */
    private fun tryGet(url: String, withAuthHeaders: Boolean): ProbeResponse? {
        val builder = Request.Builder().url(url).get().header("Accept", ACCEPT)
        if (withAuthHeaders) {
            builder.header("User-Agent", CampusChannel.USER_AGENT)
        }
        return try {
            http.newCall(builder.build()).execute().use { response ->
                captureSchool(response)
                ProbeResponse(
                    code = response.code,
                    location = response.header("Location"),
                    body = if (response.code == 200) {
                        runCatching { response.body?.string() }.getOrNull()
                    } else {
                        null
                    },
                )
            }
        } catch (e: IOException) {
            null
        }
    }

    /**
     * One portal POST, with the headers the portal checks before it will answer:
     * `CDC-Checksum` is the MD5 of the exact body, `Algo-ID` says which cipher the
     * packet was built with, `Client-ID` ties it to the identity in the XML.
     */
    private fun postXml(url: String, body: String, algoId: String, clientId: String): Reply {
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(CONTENT_TYPE.toMediaType()))
            .header("Accept", ACCEPT)
            .header("User-Agent", CampusChannel.USER_AGENT)
            .header("CDC-Checksum", md5(body))
            .header("Algo-ID", algoId)
            .header("Client-ID", clientId)
            .apply {
                if (schoolId.isNotEmpty()) header("CDC-SchoolId", schoolId)
                if (domain.isNotEmpty()) header("CDC-Domain", domain)
                if (area.isNotEmpty()) header("CDC-Area", area)
            }
            .build()
        return execute(request)
    }

    private fun execute(request: Request): Reply = try {
        http.newCall(request).execute().use { response ->
            captureSchool(response)
            Reply(
                code = response.code,
                bytes = response.body?.bytes() ?: ByteArray(0),
                location = response.header("Location"),
                errorCode = response.header("Error-Code"),
            )
        }
    } catch (e: IOException) {
        throw CampusException("与门户通信失败：${e.message}")
    }

    /** The portal tells us which school/domain/area its AC serves; echo it back. */
    private fun captureSchool(response: Response) {
        if (schoolId.isEmpty()) schoolId = response.header("schoolid")?.trim().orEmpty()
        if (domain.isEmpty()) domain = response.header("domain")?.trim().orEmpty()
        if (area.isEmpty()) area = response.header("area")?.trim().orEmpty()
    }

    private fun exchange(
        url: String,
        xml: String,
        active: CampusSession,
        log: (String) -> Unit,
    ): String? {
        if (url.isBlank()) {
            log("门户地址缺失")
            return null
        }
        val encrypted = CampusCipher.encrypt(xml) ?: run {
            log("报文加密失败")
            return null
        }
        val reply = postXml(url, encrypted, active.algoId, active.identity.clientId)
        val code = reply.errorCode
        if (code != null && code != "0" && code != "200") {
            log("门户返回 Error-Code=$code")
            throw CampusException(describeError(code))
        }
        if (reply.code !in 200..299) {
            log("门户返回 HTTP ${reply.code}")
            throw CampusException("门户返回 HTTP ${reply.code}，请稍后重试。")
        }
        val raw = String(reply.bytes, Charsets.UTF_8)
        if (raw.isBlank()) {
            log("门户响应为空（HTTP ${reply.code}）")
            return null
        }
        return CampusCipher.decrypt(raw) ?: run {
            log("响应解密失败（长度 ${raw.length}）")
            null
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
        // Trailing newline matches the reference client byte for byte: the checksum
        // header is computed over this string, so the two must not drift.
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
                    <gwip>${active.acIp}</gwip>
                </request>
            """.trimIndent() + "\n"

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
            """.trimIndent() + "\n"

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
            """.trimIndent() + "\n"
        }
    }

    // ---- environment ------------------------------------------------------

    /**
     * Picks the auth server that answers first. Only used when the portal page did
     * not announce its own endpoints. The socket has to come from the bound network
     * too, otherwise the check itself reports a false negative.
     */
    private fun pickServer(log: (String) -> Unit): String {
        val factory = CampusNetGate.liveSocketFactory()
        val reachable = CampusChannel.SERVERS.firstOrNull { server ->
            runCatching {
                factory.createSocket().use { socket ->
                    socket.connect(InetSocketAddress(server, CampusChannel.PORT), 2500)
                }
                true
            }.getOrDefault(false)
        }
        val chosen = reachable ?: CampusChannel.SERVERS.first()
        log("认证服务器 $chosen")
        return chosen
    }

    private fun resolve(base: String, ref: String): String = runCatching {
        URI(base).resolve(ref).toString()
    }.getOrDefault(ref)

    private fun describeError(code: String): String = when {
        code.startsWith("140102") -> "认证被拒绝（$code）：账号状态或密码有问题。"
        code == "140101" -> "账号或密码错误。"
        code == "140200" -> "客户端密钥已过期，请重试。"
        code.startsWith("2001") -> "本地连接异常（$code），请检查 WiFi 后重试。"
        else -> "认证失败（Error-Code=$code）。"
    }

    private data class Opened(
        val keepUrl: String,
        val termUrl: String,
        val keepSeconds: Long,
    )

    private enum class Payload { Ticket, Login, Heartbeat, Terminate }

    companion object {
        private const val PROBE_URL = "http://connect.rom.miui.com/generate_204"
        private const val PROBE_BAK_URL = "http://1.1.1.1"
        private val AUTH_SERVERS = listOf("http://14.146.227.141:7001", "http://121.8.177.212:7001")
        private const val MAX_PROBE_HOPS = 8
        private const val CONTENT_TYPE = "application/x-www-form-urlencoded"
        private const val ACCEPT = "text/html,text/xml,application/xhtml+xml,application/x-javascript,*/*"
        private const val ZERO_ALGO = "00000000-0000-0000-0000-000000000000"
        private const val MAX_ALGO_ATTEMPTS = 4
        private const val DEFAULT_KEEP_SECONDS = 60L

        // The portal page wraps its endpoints in this HTML comment.
        private const val CONFIG_START = "<!--//config.campus.js.chinatelecom.com"
        private const val CONFIG_END = "//config.campus.js.chinatelecom.com-->"

        private fun md5(text: String): String {
            val digest = MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }

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

        /** Portal endpoints are sometimes wrapped in CDATA; unwrap and trim. */
        private fun cleanCdata(raw: String?): String? {
            val text = raw ?: return null
            val start = text.indexOf("<![CDATA[")
            if (start < 0) return text.trim().ifEmpty { null }
            val from = start + "<![CDATA[".length
            val end = text.indexOf("]]>", from)
            if (end < 0) return null
            return text.substring(from, end).trim().ifEmpty { null }
        }

        private fun extractBetween(text: String, startTag: String, endTag: String): String? {
            val start = text.indexOf(startTag)
            if (start < 0) return null
            val from = start + startTag.length
            val end = text.indexOf(endTag, from)
            if (end < 0) return null
            return text.substring(from, end)
        }

        /** `http://host:7001/ticket.cgi?a=b` → `http://host:7001`. */
        private fun baseOf(url: String): String {
            val noQuery = url.substringBefore('?')
            return noQuery.substringBeforeLast('/').ifEmpty { noQuery }
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
    val ticketUrl: String,
    val authUrl: String,
)

data class CampusProbe(
    val state: CampusNetState,
    val portalUrl: String = "",
    val detail: String = "",
)

data class CampusSession(
    val ticketUrl: String,
    val authUrl: String,
    val clientIp: String,
    val acIp: String,
    val algoId: String,
    val identity: CampusIdentity,
    val ticket: String = "",
    val keepUrl: String = "",
    val termUrl: String = "",
    val keepSeconds: Long = 60L,
    val schoolId: String = "",
    val domain: String = "",
    val area: String = "",
) {
    /** What the status card shows next to the Algo-ID: the portal's own host. */
    val server: String
        get() = runCatching {
            java.net.URI(ticketUrl).let { uri ->
                val host = uri.host ?: return@runCatching ticketUrl
                if (uri.port > 0) "http://$host:${uri.port}" else "http://$host"
            }
        }.getOrDefault(ticketUrl)
}
