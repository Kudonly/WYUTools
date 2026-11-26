package com.wyutools.app.campus

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Drives one campus-network session and the account list behind it.
 *
 * The heartbeat has to keep running while the user is elsewhere in the app, so the
 * loop hangs off [viewModelScope] rather than the screen. A process death only
 * ends the in-memory cipher: the disk ticket is restored when a later network
 * probe finds the internet already reachable and a heartbeat accepts it.
 */
class CampusViewModel(app: Application) : AndroidViewModel(app) {
    private val store = CampusStore(app)
    private val api = CampusApi(app)

    private var heartbeatJob: Job? = null
    private var probeJob: Job? = null

    private val _ui = MutableStateFlow(
        CampusUiState(
            accounts = store.accounts,
            selectedId = store.selectedId,
            logEnabled = store.logEnabled,
            cipherMissing = !CampusCipher.available,
            networkLabel = CampusNetGate.describe(app),
        ),
    )
    val ui: StateFlow<CampusUiState> = _ui.asStateFlow()

    // ---- network state ----------------------------------------------------

    /**
     * Answers "does this network need a login?" without touching credentials.
     * Runs automatically when the page opens and whenever the user pulls the
     * status card, so the button can say what it will do.
     */
    fun probeNetwork(showChecking: Boolean = false) {
        if (_ui.value.busy) return
        // The page triggers a probe on every visit; an in-flight probe must not be
        // restarted by a second caller (page re-entry, or a manual refresh).
        if (probeJob?.isActive == true) return
        probeJob = viewModelScope.launch {
            if (showChecking) {
                _ui.update {
                    it.copy(
                        phase = if (it.online) CampusPhase.Online else CampusPhase.Checking,
                        networkLabel = CampusNetGate.describe(getApplication()),
                    )
                }
            }
            log("网络检测开始")
            val result = runCatching { api.probe(::log) }
                .getOrElse { CampusProbe(CampusNetState.Failed, detail = it.message ?: "检测失败") }
            val label = CampusNetGate.describe(getApplication())
            log("网络检测：${result.state} ${result.detail}")

            // Like the reference client, a disk ticket is trusted only after the
            // gateway says the internet already works, and only after the portal
            // accepts a heartbeat with that ticket. Not at app start, and not on
            // a network that still shows the login portal.
            var restored: CampusAuthSession? = null
            if (result.state == CampusNetState.Online && !_ui.value.online) {
                val saved = runCatching { store.savedSession }.getOrNull()
                if (saved != null && saved.isValid) {
                    withContext(Dispatchers.IO) {
                        if (api.restore(saved, ::log)) {
                            val active = api.currentSession
                            val alive = active != null &&
                                runCatching { api.heartbeat(active, ::log) }.getOrNull() != null
                            if (alive) {
                                restored = saved
                            } else {
                                log("恢复会话心跳失败，ticket 可能过期")
                            }
                        }
                    }
                }
            }
            val restoredNow = restored
            // No WLAN/Ethernet at all is not a failed login — show 未连接 at once
            // and keep the disk ticket for when the interface comes back.
            val noNetwork = result.state == CampusNetState.NoNetwork
            if (noNetwork) {
                heartbeatJob?.cancel()
                heartbeatJob = null
            }
            if (restoredNow != null) {
                // Heartbeats are signed with the identity the ticket was issued to.
                store.restoreIdentity(restoredNow.identity)
                if (restoredNow.accountId.isNotEmpty()) store.select(restoredNow.accountId)
            }
            _ui.update {
                it.copy(
                    netState = result.state,
                    netDetail = result.detail,
                    networkLabel = label,
                    phase = when {
                        noNetwork -> CampusPhase.Idle
                        restoredNow != null -> CampusPhase.Online
                        it.online -> CampusPhase.Online
                        result.state == CampusNetState.NeedLogin -> CampusPhase.Idle
                        it.phase == CampusPhase.Checking -> CampusPhase.Idle
                        else -> it.phase
                    },
                    onlineSince = if (noNetwork) null
                        else restoredNow?.loginAtMs?.takeIf { ms -> ms > 0L }
                            ?: if (restoredNow == null) it.onlineSince else System.currentTimeMillis(),
                    algoId = restoredNow?.algoId ?: it.algoId,
                    server = restoredNow?.server ?: it.server,
                    accounts = if (restoredNow != null) store.accounts else it.accounts,
                    selectedId = if (restoredNow != null) store.selectedId else it.selectedId,
                    notice = if (restoredNow != null || noNetwork) null else it.notice,
                )
            }
            if (restoredNow != null) {
                log("账号已登录: ${restoredNow.accountName.ifBlank { restoredNow.accountId }}")
                startHeartbeat()
            }
        }
    }

    // ---- settings ---------------------------------------------------------

    fun openSettings() = _ui.update { it.copy(settingsOpen = true) }

    fun closeSettings() = _ui.update { it.copy(settingsOpen = false) }

    // ---- accounts ---------------------------------------------------------

    /**
     * Adds or updates an account. Leaving the password blank keeps the stored one,
     * so the management page can rename an account without retyping the secret.
     */
    fun saveAccount(account: String, password: String) {
        val name = account.trim()
        if (name.isEmpty()) return
        val existing = _ui.value.accounts.firstOrNull { it.account.equals(name, ignoreCase = true) }
        val effective = password.ifBlank { existing?.password.orEmpty() }
        store.save(name, effective)
        _ui.update {
            it.copy(
                accounts = store.accounts,
                selectedId = store.selectedId,
            )
        }
        log("账号已保存：$name")
    }

    fun removeAccount(id: String) {
        val removed = _ui.value.accounts.firstOrNull { it.id == id }
        store.remove(id)
        _ui.update { it.copy(accounts = store.accounts, selectedId = store.selectedId) }
        if (removed != null) log("账号已删除：${removed.account}")
    }

    fun selectAccount(id: String) {
        store.select(id)
        _ui.update { it.copy(accounts = store.accounts, selectedId = store.selectedId) }
    }

    // ---- session ----------------------------------------------------------

    fun connect() {
        val state = _ui.value
        if (state.busy || state.online) return
        val account = state.selected
        if (account == null) {
            _ui.update { it.copy(notice = "还没有账号，点右上角添加。", phase = CampusPhase.Idle) }
            return
        }
        if (account.password.isEmpty()) {
            _ui.update { it.copy(notice = "请先在账号管理里填写密码。", phase = CampusPhase.Failed) }
            return
        }
        if (state.netState == CampusNetState.NoNetwork) {
            _ui.update {
                it.copy(phase = CampusPhase.Idle, onlineSince = null, notice = null)
            }
            return
        }

        store.save(account.account, account.password)
        store.resetIdentity()
        _ui.update {
            it.copy(
                phase = CampusPhase.Authenticating,
                busy = true,
                notice = null,
                accounts = store.accounts,
            )
        }
        log("开始认证 ${account.account}")

        probeJob?.cancel()
        viewModelScope.launch {
            try {
                val session = api.connect(store.identity, account.account, account.password, ::log)
                val since = System.currentTimeMillis()
                _ui.update {
                    it.copy(
                        phase = CampusPhase.Online,
                        netState = CampusNetState.Online,
                        busy = false,
                        onlineSince = since,
                        algoId = session.algoId,
                        server = session.server,
                        notice = null,
                    )
                }
                // Written the moment the portal accepts, so a process kill during
                // the session resumes the same ticket instead of authenticating
                // again and orphaning this one on the server.
                store.savedSession = CampusAuthSession(
                    accountId = account.id,
                    accountName = account.account,
                    ticket = session.ticket,
                    ticketUrl = session.ticketUrl,
                    authUrl = session.authUrl,
                    keepUrl = session.keepUrl,
                    termUrl = session.termUrl,
                    algoId = session.algoId,
                    clientId = session.identity.clientId,
                    hostName = session.identity.hostName,
                    mac = session.identity.mac,
                    schoolId = session.schoolId,
                    domain = session.domain,
                    area = session.area,
                    clientIp = session.clientIp,
                    acIp = session.acIp,
                    keepSeconds = session.keepSeconds,
                    loginAtMs = since,
                )
                log("已连接，服务器 ${session.server}")
                startHeartbeat()
            } catch (e: CampusNoNetworkException) {
                log("认证中止：${e.message}")
                _ui.update {
                    it.copy(phase = CampusPhase.Idle, onlineSince = null, busy = false, notice = null)
                }
            } catch (e: Exception) {
                val message = e.message ?: "认证失败，请稍后重试"
                log("认证失败：$message")
                _ui.update {
                    it.copy(
                        phase = CampusPhase.Failed,
                        busy = false,
                        onlineSince = null,
                        notice = message,
                    )
                }
            }
        }
    }

    fun disconnect() {
        val state = _ui.value
        if (state.busy) return
        val session = api.currentSession
        heartbeatJob?.cancel()
        heartbeatJob = null
        // Whatever happens to the request, the user asked for the session to end:
        // it must not come back on the next launch.
        store.savedSession = null
        if (session == null) {
            _ui.update { it.copy(phase = CampusPhase.Idle, onlineSince = null, busy = false) }
            return
        }
        _ui.update { it.copy(busy = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val ok = api.disconnect(session, ::log)
            _ui.update {
                it.copy(
                    phase = CampusPhase.Idle,
                    busy = false,
                    onlineSince = null,
                    notice = if (ok) "已断开连接。" else "断连未得到确认，可稍后重试。",
                )
            }
            probeNetwork()
        }
    }

    /** Keeps an accepted session alive; the first heartbeat already verified it. */
    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = viewModelScope.launch(Dispatchers.IO) {
            var failures = 0
            while (true) {
                val session = api.currentSession ?: return@launch
                delay(session.keepSeconds.coerceIn(30L, 300L) * 1000)
                val next = runCatching { api.heartbeat(session, ::log) }.getOrNull()
                if (next == null) {
                    failures++
                    log("心跳失败（$failures）")
                    if (failures >= 2) {
                        onSessionLost("连接已断开，请重新认证。")
                        return@launch
                    }
                } else {
                    failures = 0
                }
            }
        }
    }

    /** The portal no longer has this session: forget it everywhere. */
    private fun onSessionLost(message: String) {
        store.savedSession = null
        _ui.update {
            it.copy(
                phase = CampusPhase.Failed,
                onlineSince = null,
                notice = message,
            )
        }
    }

    // ---- log --------------------------------------------------------------

    /**
     * Logging is off by default and only records while enabled — the panel exists
     * for troubleshooting a failed login, not as a permanent trace of the account.
     */
    fun setLogEnabled(enabled: Boolean) {
        store.logEnabled = enabled
        _ui.update {
            it.copy(
                logEnabled = enabled,
                logs = if (enabled) it.logs + line("日志已开启") else emptyList(),
            )
        }
    }

    fun clearLogs() = _ui.update { it.copy(logs = emptyList()) }

    fun dismissNotice() = _ui.update { it.copy(notice = null) }

    private fun log(message: String) {
        if (!_ui.value.logEnabled) return
        _ui.update {
            val next = it.logs + line(message)
            it.copy(logs = if (next.size > MAX_LOG_LINES) next.takeLast(MAX_LOG_LINES) else next)
        }
    }

    private fun line(message: String): String =
        "${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())}  $message"

    override fun onCleared() {
        heartbeatJob?.cancel()
        probeJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val MAX_LOG_LINES = 300
    }
}
