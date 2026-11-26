package com.wyutools.app.campus

/** What the campus network page is doing right now. */
enum class CampusPhase {
    /** Nothing started yet — no credentials, or the user disconnected. */
    Idle,

    /** Probing the gateway to find out whether a login is needed at all. */
    Checking,

    /** Portal located: fetching a ticket and authenticating. */
    Authenticating,

    /** Session accepted by the portal; heartbeat keeps it alive. */
    Online,

    /** Last attempt failed. [CampusUiState.notice] carries the reason. */
    Failed,
}

/** What the current network actually needs, decided by [CampusApi.probe]. */
enum class CampusNetState {
    /** Not probed yet. */
    Unknown,

    /** No WLAN/Ethernet interface — campus network is out of reach. */
    NoNetwork,

    /** A portal answered: credentials are required. */
    NeedLogin,

    /** The internet already works, nothing to authenticate. */
    Online,

    /** Probe could not reach anything. */
    Failed,
}

/**
 * One saved broadband account.
 *
 * Saving an account means remembering its password — there is no separate
 * "remember" switch, so [password] is simply empty only while a new entry is
 * being typed.
 */
data class CampusAccount(
    val id: String,
    val account: String,
    val password: String = "",
)

data class CampusUiState(
    val phase: CampusPhase = CampusPhase.Idle,
    val netState: CampusNetState = CampusNetState.Unknown,
    /** Short transport description, e.g. 已连接 WLAN. */
    val networkLabel: String = "",
    val netDetail: String = "",
    val accounts: List<CampusAccount> = emptyList(),
    val selectedId: String = "",
    /** Wall-clock millis of the moment the portal accepted the session. */
    val onlineSince: Long? = null,
    /** Portal assigned algorithm id, kept for the log panel and diagnostics. */
    val algoId: String? = null,
    val server: String? = null,
    val notice: String? = null,
    val logEnabled: Boolean = false,
    val logs: List<String> = emptyList(),
    /** True while a connect/disconnect call is in flight — buttons must wait. */
    val busy: Boolean = false,
    /** No native cipher for this ABI: the page can only explain, not connect. */
    val cipherMissing: Boolean = false,
    val settingsOpen: Boolean = false,
) {
    val online: Boolean get() = phase == CampusPhase.Online

    val selected: CampusAccount? get() = accounts.firstOrNull { it.id == selectedId } ?: accounts.firstOrNull()

    val hasAccount: Boolean get() = selected != null

    val account: String get() = selected?.account.orEmpty()

    val password: String get() = selected?.password.orEmpty()
}

/**
 * The login ticket kept on disk, laid out like the reference client's
 * `auth_session`: flat fields rather than one JSON blob, and only a complete
 * ticket/keep/term triple counts as a session worth restoring. The portal still
 * has the final say — a restored ticket is accepted only after a heartbeat.
 */
data class CampusAuthSession(
    val accountId: String = "",
    val accountName: String = "",
    val ticket: String = "",
    val ticketUrl: String = "",
    val authUrl: String = "",
    val keepUrl: String = "",
    val termUrl: String = "",
    val algoId: String = "",
    val clientId: String = "",
    val hostName: String = "",
    val mac: String = "",
    val schoolId: String = "",
    val domain: String = "",
    val area: String = "",
    val clientIp: String = "",
    val acIp: String = "",
    val keepSeconds: Long = 0L,
    val loginAtMs: Long = 0L,
) {
    val isValid: Boolean
        get() = ticket.isNotBlank() && keepUrl.isNotBlank() && termUrl.isNotBlank()

    val identity: CampusIdentity
        get() = CampusIdentity(clientId, hostName, mac)

    /** Portal host, for the status card only — never shown with the IP itself. */
    val server: String
        get() = runCatching {
            java.net.URI(ticketUrl).let { uri ->
                val host = uri.host ?: return@runCatching ticketUrl
                if (uri.port > 0) "http://$host:${uri.port}" else "http://$host"
            }
        }.getOrDefault(ticketUrl)
}

/** Thrown for failures that should be shown to the user verbatim. */
open class CampusException(message: String) : Exception(message)

/** No WLAN/Ethernet at all: the card must say "未连接", not "连接失败". */
class CampusNoNetworkException(message: String) : CampusException(message)
