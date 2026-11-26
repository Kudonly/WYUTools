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

/**
 * Drives one campus-network session.
 *
 * Lives on the Application, not on the page: leaving the screen must not drop the
 * session, and the heartbeat has to keep running while the user is elsewhere in
 * the app. A process death still ends it — the portal times the session out on its
 * own, and reopening the page simply authenticates again.
 */
class CampusViewModel(app: Application) : AndroidViewModel(app) {
    private val store = CampusStore(app)
    private val api = CampusApi(app)

    private var heartbeatJob: Job? = null

    private val _ui = MutableStateFlow(
        CampusUiState(
            account = store.account,
            password = store.password,
            rememberPassword = store.rememberPassword,
            logEnabled = store.logEnabled,
            cipherMissing = !CampusCipher.available,
        ),
    )
    val ui: StateFlow<CampusUiState> = _ui.asStateFlow()

    fun setAccount(value: String) = _ui.update { it.copy(account = value) }

    fun setPassword(value: String) = _ui.update { it.copy(password = value) }

    fun setRememberPassword(value: Boolean) = _ui.update { it.copy(rememberPassword = value) }

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

    fun connect() {
        val state = _ui.value
        if (state.busy || state.online) return
        val account = state.account.trim()
        val password = state.password
        if (account.isEmpty()) {
            _ui.update { it.copy(notice = "请填写宽带账号。", phase = CampusPhase.Failed) }
            return
        }
        if (password.isEmpty()) {
            _ui.update { it.copy(notice = "请填写宽带密码。", phase = CampusPhase.Failed) }
            return
        }

        store.saveCredentials(account, password, state.rememberPassword)
        store.resetIdentity()

        _ui.update {
            it.copy(phase = CampusPhase.Discovering, busy = true, notice = null)
        }
        log("开始认证 $account")

        viewModelScope.launch {
            try {
                // The first phase is portal discovery, which happens inside connect();
                // flip the label once the handshake reaches the auth step.
                _ui.update { it.copy(phase = CampusPhase.Authenticating) }
                val session = api.connect(store.identity, account, password, ::log)
                _ui.update {
                    it.copy(
                        phase = CampusPhase.Online,
                        busy = false,
                        onlineSince = System.currentTimeMillis(),
                        algoId = session.algoId,
                        server = session.server,
                        notice = null,
                    )
                }
                log("已连接，服务器 ${session.server}")
                startHeartbeat()
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
        }
    }

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
                        _ui.update {
                            it.copy(
                                phase = CampusPhase.Failed,
                                onlineSince = null,
                                notice = "连接已断开，请重新认证。",
                            )
                        }
                        return@launch
                    }
                } else {
                    failures = 0
                }
            }
        }
    }

    private fun log(message: String) {
        if (!_ui.value.logEnabled) return
        _ui.update {
            val next = it.logs + line(message)
            it.copy(logs = if (next.size > MAX_LOG_LINES) next.takeLast(MAX_LOG_LINES) else next)
        }
    }

    private fun line(message: String): String =
        "${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())}  $message"

    private companion object {
        const val MAX_LOG_LINES = 300
    }
}
