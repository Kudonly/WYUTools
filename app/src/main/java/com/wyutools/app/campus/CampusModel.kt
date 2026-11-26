package com.wyutools.app.campus

/** What the campus network page is doing right now. */
enum class CampusPhase {
    /** Nothing started yet — no credentials, or the user disconnected. */
    Idle,

    /** Asking the gateway which portal it wants us to talk to. */
    Discovering,

    /** Portal located: fetching a ticket and authenticating. */
    Authenticating,

    /** Session accepted by the portal; heartbeat keeps it alive. */
    Online,

    /** Last attempt failed. [CampusUiState.notice] carries the reason. */
    Failed,
}

data class CampusUiState(
    val phase: CampusPhase = CampusPhase.Idle,
    val account: String = "",
    val password: String = "",
    /** Whether the password is written to disk (account always is). */
    val rememberPassword: Boolean = true,
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
) {
    val online: Boolean get() = phase == CampusPhase.Online
}

/** Thrown for failures that should be shown to the user verbatim. */
class CampusException(message: String) : Exception(message)
