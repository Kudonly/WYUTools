package com.wyutools.app.auth

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wyutools.app.gym.GymSessionStore
import com.wyutools.app.security.PasswordVault
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

enum class JxglStatus { Idle, Connecting, Ready, Failed }

data class MeUiState(
    val restoring: Boolean = true,
    val loggedIn: Boolean = false,
    val profile: SsoProfile? = null,
    val loggingIn: Boolean = false,
    val autoLoggingIn: Boolean = false,
    val loginError: String? = null,
    /** True after the user signed out on purpose; pages that would otherwise
     *  wait for a silent login must ask for a login instead. */
    val loggedOut: Boolean = false,
    val loginSuccessCount: Int = 0,
    val loggingOut: Boolean = false,
    val captcha: SliderChallenge? = null,
    val captchaVerifying: Boolean = false,
    val captchaError: String? = null,
    val jxglStatus: JxglStatus = JxglStatus.Idle,
    val jxglError: String? = null,
    val notice: String? = null,
)

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val api = SsoApi()
    private val _ui = MutableStateFlow(MeUiState())
    val ui: StateFlow<MeUiState> = _ui.asStateFlow()

    private val _demoMode = MutableStateFlow(prefs.getBoolean(KEY_DEMO_MODE, false))
    val demoMode: StateFlow<Boolean> = _demoMode.asStateFlow()

    private data class PendingLogin(
        val username: String,
        val password: String,
        val page: LoginPageFields,
        val rememberPassword: Boolean,
    )

    private var pendingLogin: PendingLogin? = null
    private var captchaRefreshing = false
    private var autoLoginJob: Job? = null
    private var autoLoginAttempted = false

    val savedUsername: String
        get() = sanitizeWyuText(prefs.getString(KEY_USERNAME, null)).orEmpty()

    val savedPassword: String
        get() = PasswordVault.load(getApplication(), KEY_PASSWORD)

    /** The device remembers an account, so a missing session alone is not a
     *  reason to ask for credentials — a silent attempt gets to try first. */
    val hasSavedAccount: Boolean
        get() = savedUsername.isNotEmpty()

    /**
     * Signs back in without user input. Pages that sit behind the SSO session
     * (gym, schedule) call this while the account is remembered but the session
     * has not come up yet, so opening them right after a cold start does not
     * flash a "please log in" prompt at a user who never signed out. A failure
     * lands in [MeUiState.loginError], and that is the only state that may ask
     * for a real login.
     */
    fun trySilentLogin() {
        val current = _ui.value
        if (current.restoring || current.loggedIn || current.loggingIn ||
            current.autoLoggingIn || current.loggingOut || current.loggedOut ||
            current.captcha != null
        ) {
            return
        }
        if (autoLoginJob?.isActive == true) return
        val username = savedUsername
        val password = savedPassword
        if (username.isEmpty() || password.isEmpty() || autoLoginAttempted) {
            _ui.update {
                it.copy(loginError = it.loginError ?: "请重新登录统一身份认证。")
            }
            return
        }
        autoLoginAttempted = true
        _ui.update { it.copy(autoLoggingIn = true, loginError = null) }
        autoLoginJob = viewModelScope.launch { performAutoLogin(username, password) }
    }

    init {
        restoreSession()
    }

    fun setRememberPasswordOption(remember: Boolean) {
        pendingLogin = pendingLogin?.copy(rememberPassword = remember)
        if (!remember) PasswordVault.clear(getApplication(), KEY_PASSWORD)
    }

    fun setDemoMode(enabled: Boolean) {
        if (_demoMode.value == enabled) return
        prefs.edit().putBoolean(KEY_DEMO_MODE, enabled).apply()
        _demoMode.value = enabled
    }

    fun login(username: String, password: String, rememberPassword: Boolean) {
        val user = sanitizeWyuText(username).orEmpty()
        if (user.isEmpty() || password.isEmpty()) {
            _ui.update { it.copy(loginError = "请输入学号和密码。") }
            return
        }
        val current = _ui.value
        if (current.loggingIn || current.autoLoggingIn || current.captcha != null) return

        autoLoginAttempted = false
        pendingLogin = null
        _ui.update {
            it.copy(
                loggingIn = true,
                loginError = null,
                captcha = null,
                captchaError = null,
            )
        }
        viewModelScope.launch {
            try {
                val page = api.fetchLoginPage()
                if (api.checkNeedCaptcha(user)) {
                    api.triggerSlider()
                    val challenge = api.openSliderCaptcha()
                    pendingLogin = PendingLogin(user, password, page, rememberPassword)
                    _ui.update { it.copy(loggingIn = false, captcha = challenge) }
                } else {
                    completeLogin(PendingLogin(user, password, page, rememberPassword))
                }
            } catch (error: SsoException) {
                failLogin(error.message ?: "登录失败，请稍后重试。")
            } catch (_: IOException) {
                failLogin("网络连接失败，请检查网络后重试。")
            } catch (error: Exception) {
                failLogin(error.message ?: "登录失败，请稍后重试。")
            }
        }
    }

    fun submitCaptcha(payload: SliderPayload) {
        val challenge = _ui.value.captcha ?: return
        val pending = pendingLogin ?: return
        if (_ui.value.captchaVerifying) return

        _ui.update { it.copy(captchaVerifying = true, captchaError = null) }
        viewModelScope.launch {
            try {
                val result = api.verifySliderCaptcha(challenge, payload)
                if (result.passed) {
                    _ui.update { it.copy(captcha = null, captchaVerifying = false) }
                    completeLogin(pending)
                } else {
                    _ui.update {
                        it.copy(
                            captchaVerifying = false,
                            captchaError = result.message ?: "滑块验证未通过，请重试。",
                        )
                    }
                }
            } catch (_: IOException) {
                _ui.update {
                    it.copy(
                        captchaVerifying = false,
                        captchaError = "网络连接失败，请重试。",
                    )
                }
            } catch (error: Exception) {
                _ui.update {
                    it.copy(
                        captchaVerifying = false,
                        captchaError = error.message ?: "滑块校验失败，请重试。",
                    )
                }
            }
        }
    }

    fun refreshCaptcha() {
        if (_ui.value.captcha == null || _ui.value.captchaVerifying || captchaRefreshing) {
            return
        }
        captchaRefreshing = true
        viewModelScope.launch {
            try {
                val challenge = api.openSliderCaptcha()
                _ui.update { it.copy(captcha = challenge, captchaError = null) }
            } catch (_: IOException) {
                _ui.update { it.copy(captchaError = "网络连接失败，无法刷新验证码。") }
            } catch (error: Exception) {
                _ui.update { it.copy(captchaError = error.message ?: "验证码刷新失败。") }
            } finally {
                captchaRefreshing = false
            }
        }
    }

    fun cancelCaptcha() {
        if (_ui.value.captchaVerifying) return
        pendingLogin = null
        _ui.update {
            it.copy(
                captcha = null,
                captchaVerifying = false,
                captchaError = null,
            )
        }
    }

    fun retryJxgl() {
        if (!_ui.value.loggedIn) return
        if (_ui.value.jxglStatus == JxglStatus.Connecting) return
        connectJxgl()
    }

    /** 教务网页被重定向到统一认证时调用：静默重建会话。 */
    fun recoverJxglSession() {
        if (_ui.value.jxglStatus == JxglStatus.Connecting) return
        handleSessionExpired(forceAutoLogin = true)
    }

    /** 课表导入专用：直接复用教务会话，过期时自动尝试重新登录。 */
    suspend fun fetchSchedule(termCode: String): String {
        if (!_ui.value.loggedIn) {
            throw SsoException("登录统一身份认证后即可导入课表。")
        }
        return try {
            api.fetchJxglSchedule(termCode)
        } catch (error: SsoException) {
            if (error.sessionExpired) handleSessionExpired(forceAutoLogin = true)
            throw error
        }
    }

    fun logout() {
        if (_ui.value.loggingOut) return
        autoLoginJob?.cancel()
        autoLoginJob = null
        autoLoginAttempted = false
        pendingLogin = null
        _ui.update { it.copy(loggingOut = true) }
        viewModelScope.launch {
            api.logout()
            clearLocalSession()
            // The gym session is a copy of the cookie just thrown away, so it
            // has to go too — otherwise a restart restores a user from a dead
            // session and the gym page never asks to log in again.
            GymSessionStore.clear(getApplication())
            _ui.value = MeUiState(restoring = false, loggedOut = true, notice = "已退出登录。")
        }
    }

    fun consumeNotice() {
        _ui.update { it.copy(notice = null) }
    }

    fun cookieEntries(): List<CookieEntry> = api.cookieEntries()

    /** 统一认证 + 门户 Cookie 头，供体育馆等需经 authserver 换票的业务复用。 */
    fun ssoCookieHeader(): String? {
        if (!_ui.value.loggedIn) return null
        return api.ssoCookieHeader()
    }

    private fun restoreSession() {
        val rawCookies = prefs.getString(KEY_COOKIES, null)
        if (rawCookies.isNullOrBlank()) {
            prefs.edit()
                .remove(KEY_NAME)
                .remove(KEY_ACCOUNT)
                .remove(KEY_DEPT)
                .apply()
            _ui.value = MeUiState(restoring = false)
            return
        }

        api.restoreCookies(rawCookies)
        viewModelScope.launch {
            try {
                val profile = api.fetchProfile()
                if (profile == null) {
                    handleSessionExpired()
                    return@launch
                }
                persistProfile(profile)
                _ui.value = MeUiState(
                    restoring = false,
                    loggedIn = true,
                    profile = profile,
                )
                connectJxgl()
            } catch (_: IOException) {
                val cached = readCachedProfile()
                if (cached == null) {
                    _ui.value = MeUiState(
                        restoring = false,
                        loginError = "网络连接失败，请检查网络后重试。",
                    )
                } else {
                    _ui.value = MeUiState(
                        restoring = false,
                        loggedIn = true,
                        profile = cached,
                        jxglStatus = JxglStatus.Failed,
                        jxglError = "网络连接失败，无法连接教务系统。",
                        notice = "网络异常，已显示本地登录信息。",
                    )
                }
            } catch (error: Exception) {
                val cached = readCachedProfile()
                _ui.value = MeUiState(
                    restoring = false,
                    loggedIn = cached != null,
                    profile = cached,
                    loginError = if (cached == null) {
                        error.message ?: "登录态恢复失败。"
                    } else {
                        null
                    },
                )
            }
        }
    }

    private suspend fun completeLogin(pending: PendingLogin) {
        try {
            val profile = api.submitLogin(pending.username, pending.password, pending.page)
            persistSession(profile, pending.username)
            if (pending.rememberPassword) {
                PasswordVault.save(getApplication(), KEY_PASSWORD, pending.password)
            } else {
                PasswordVault.clear(getApplication(), KEY_PASSWORD)
            }
            pendingLogin = null
            autoLoginAttempted = false
            _ui.update {
                it.copy(
                    loggedIn = true,
                    loggingIn = false,
                    autoLoggingIn = false,
                    loggedOut = false,
                    loginError = null,
                    loginSuccessCount = it.loginSuccessCount + 1,
                    captcha = null,
                    captchaError = null,
                    profile = profile,
                    jxglStatus = JxglStatus.Idle,
                    jxglError = null,
                )
            }
            connectJxgl()
        } catch (error: SsoException) {
            failLogin(error.message ?: "登录失败，请稍后重试。")
        } catch (_: IOException) {
            failLogin("网络连接失败，请检查网络后重试。")
        } catch (error: Exception) {
            failLogin(error.message ?: "登录失败，请稍后重试。")
        }
    }

    private fun failLogin(message: String) {
        pendingLogin = null
        _ui.update {
            it.copy(
                loggedIn = false,
                loggingIn = false,
                captcha = null,
                captchaVerifying = false,
                loginError = message,
            )
        }
    }

    private fun connectJxgl() {
        if (!_ui.value.loggedIn) return
        if (_ui.value.jxglStatus == JxglStatus.Connecting) return
        _ui.update {
            it.copy(jxglStatus = JxglStatus.Connecting, jxglError = null)
        }
        viewModelScope.launch {
            try {
                api.establishJxgl()
                autoLoginAttempted = false
                _ui.update {
                    it.copy(jxglStatus = JxglStatus.Ready, jxglError = null)
                }
            } catch (error: SsoException) {
                if (error.sessionExpired) {
                    handleSessionExpired()
                } else {
                    _ui.update {
                        it.copy(
                            jxglStatus = JxglStatus.Failed,
                            jxglError = error.message ?: "教务系统连接失败。",
                        )
                    }
                }
            } catch (_: IOException) {
                _ui.update {
                    it.copy(
                        jxglStatus = JxglStatus.Failed,
                        jxglError = "网络连接失败，无法连接教务系统。",
                    )
                }
            } catch (error: Exception) {
                _ui.update {
                    it.copy(
                        jxglStatus = JxglStatus.Failed,
                        jxglError = error.message ?: "教务系统连接失败。",
                    )
                }
            }
        }
    }

    private fun handleSessionExpired(forceAutoLogin: Boolean = false) {
        if (autoLoginJob?.isActive == true) return
        clearLocalSession()
        pendingLogin = null
        if (forceAutoLogin) autoLoginAttempted = false

        val username = savedUsername
        val password = savedPassword
        if (username.isEmpty() || password.isEmpty() || autoLoginAttempted) {
            _ui.value = MeUiState(
                restoring = false,
                notice = "登录已失效，请重新登录。",
            )
            return
        }

        autoLoginAttempted = true
        _ui.value = MeUiState(
            restoring = false,
            autoLoggingIn = true,
            notice = "登录已失效，正在自动登录…",
        )
        autoLoginJob = viewModelScope.launch {
            performAutoLogin(username, password)
        }
    }

    private suspend fun performAutoLogin(username: String, password: String) {
        try {
            val page = api.fetchLoginPage()
            if (api.checkNeedCaptcha(username)) {
                _ui.value = MeUiState(
                    restoring = false,
                    loginError = "登录已失效，请重新登录并完成安全验证。",
                    notice = "登录已失效，请重新登录。",
                )
                return
            }
            val profile = api.submitLogin(username, password, page)
            persistSession(profile, username)
            _ui.value = MeUiState(
                restoring = false,
                loggedIn = true,
                loginSuccessCount = _ui.value.loginSuccessCount + 1,
                profile = profile,
                notice = "已自动重新登录。",
            )
            connectJxgl()
        } catch (error: SsoException) {
            _ui.value = MeUiState(
                restoring = false,
                loginError = "自动登录失败：${error.message ?: "请重新登录。"}",
                notice = "自动登录失败，请重新登录。",
            )
        } catch (_: IOException) {
            _ui.value = MeUiState(
                restoring = false,
                loginError = "网络连接失败，自动登录未完成。",
                notice = "网络连接失败，请稍后重试。",
            )
        } catch (error: Exception) {
            _ui.value = MeUiState(
                restoring = false,
                loginError = "自动登录失败：${error.message ?: "请重新登录。"}",
                notice = "自动登录失败，请重新登录。",
            )
        } finally {
            autoLoginJob = null
        }
    }

    private fun persistSession(profile: SsoProfile, username: String) {
        prefs.edit()
            .putString(KEY_COOKIES, api.snapshotCookies())
            .putString(KEY_NAME, profile.name)
            .putString(KEY_ACCOUNT, profile.account)
            .putString(KEY_DEPT, profile.dept.orEmpty())
            .putString(KEY_USERNAME, username)
            .apply()
    }

    private fun persistProfile(profile: SsoProfile) {
        prefs.edit()
            .putString(KEY_COOKIES, api.snapshotCookies())
            .putString(KEY_NAME, profile.name)
            .putString(KEY_ACCOUNT, profile.account)
            .putString(KEY_DEPT, profile.dept.orEmpty())
            .apply()
    }

    private fun readCachedProfile(): SsoProfile? {
        val account = sanitizeWyuText(prefs.getString(KEY_ACCOUNT, null)) ?: return null
        val name = sanitizeWyuText(prefs.getString(KEY_NAME, null)) ?: account
        val dept = sanitizeWyuText(prefs.getString(KEY_DEPT, null))
        return SsoProfile(name = name, account = account, dept = dept)
    }

    private fun clearLocalSession() {
        api.clearCookies()
        prefs.edit()
            .remove(KEY_COOKIES)
            .remove(KEY_NAME)
            .remove(KEY_ACCOUNT)
            .remove(KEY_DEPT)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "auth"
        private const val KEY_COOKIES = "sso_cookies"
        private const val KEY_NAME = "sso_name"
        private const val KEY_ACCOUNT = "sso_account"
        private const val KEY_DEPT = "sso_dept"
        private const val KEY_USERNAME = "last_username"
        private const val KEY_PASSWORD = "sso_password"
        private const val KEY_DEMO_MODE = "demo_mode"
    }
}
