package com.wyutools.app.gym

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wyutools.app.ui.WyuMorphPageState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.LocalDate

data class GymNotice(
    val message: String,
    val error: Boolean = false,
)

data class GymUiState(
    val user: GymUser? = null,
    val checkingSession: Boolean = true,
    val loginNeeded: Boolean = false,
    val loggingIn: Boolean = false,
    val loginError: String? = null,
    val loginExpired: Boolean = false,
    val selectedDate: LocalDate = LocalDate.now(),
    val sportFilter: String = "",
    val venues: List<GymVenue> = emptyList(),
    val loadingVenues: Boolean = false,
    val venuesLoaded: Boolean = false,
    val records: List<GymRecord> = emptyList(),
    val loadingRecords: Boolean = false,
    val recordsLoaded: Boolean = false,
    val savedPhone: String = "",
    val booking: Boolean = false,
    val cancellingWid: String? = null,
    val notice: GymNotice? = null,
)

class GymViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("gym", Context.MODE_PRIVATE)
    private val api = GymApi(
        cookieProvider = { prefs.getString("cookie", "").orEmpty() },
        onCookieUpdated = { prefs.edit().putString("cookie", it).apply() },
    )
    private val venueCache = mutableMapOf<LocalDate, List<GymVenue>>()

    private val _ui = MutableStateFlow(
        GymUiState(
            savedPhone = prefs.getString("phone", "").orEmpty(),
            sportFilter = prefs.getString("filter_sport", "").orEmpty(),
        ),
    )
    val ui: StateFlow<GymUiState> = _ui.asStateFlow()

    init {
        restoreSession()
    }

    private fun restoreSession() {
        val cookie = prefs.getString("cookie", "").orEmpty()
        val name = prefs.getString("name", "").orEmpty()
        val account = prefs.getString("account", "").orEmpty()
        if (cookie.isBlank()) {
            _ui.update { it.copy(checkingSession = false, loginNeeded = true) }
            return
        }

        val cachedUser = if (name.isNotBlank() && account.isNotBlank()) {
            GymUser(name = name, account = account)
        } else {
            null
        }
        if (cachedUser != null) {
            _ui.update { it.copy(user = cachedUser) }
        }

        viewModelScope.launch {
            try {
                val user = withContext(Dispatchers.IO) { api.validateSession() }
                saveUser(user)
                _ui.update {
                    it.copy(
                        user = user,
                        checkingSession = false,
                        loginNeeded = false,
                        loginError = null,
                    )
                }
                loadAll()
            } catch (e: Exception) {
                if (e is GymException && e.authExpired) {
                    clearStoredSession()
                    _ui.update {
                        it.copy(
                            user = null,
                            checkingSession = false,
                            loginNeeded = true,
                            loginError = null,
                        )
                    }
                } else if (cachedUser != null) {
                    _ui.update {
                        it.copy(
                            checkingSession = false,
                            notice = GymNotice(friendlyMessage(e), error = true),
                        )
                    }
                    loadAll()
                } else {
                    clearStoredSession()
                    _ui.update {
                        it.copy(
                            checkingSession = false,
                            loginNeeded = true,
                            loginError = friendlyMessage(e),
                        )
                    }
                }
            }
        }
    }

    fun loginWithCookie(cookie: String) {
        if (cookie.isBlank() || _ui.value.loggingIn) return
        _ui.update { it.copy(loggingIn = true, loginError = null, loginExpired = false) }
        prefs.edit().putString("cookie", cookie).apply()
        api.resetSession()
        viewModelScope.launch {
            try {
                val user = withContext(Dispatchers.IO) { api.validateSession() }
                saveUser(user)
                venueCache.clear()
                _ui.update {
                    it.copy(
                        user = user,
                        loggingIn = false,
                        checkingSession = false,
                        loginNeeded = false,
                        loginError = null,
                        loginExpired = false,
                    )
                }
                loadAll()
            } catch (e: Exception) {
                prefs.edit().remove("cookie").apply()
                _ui.update {
                    it.copy(
                        loggingIn = false,
                        loginError = friendlyMessage(e),
                        loginExpired = e is GymException && e.authExpired,
                    )
                }
            }
        }
    }

    fun logout() {
        clearStoredSession()
        venueCache.clear()
        api.resetSession()
        _ui.update {
            GymUiState(
                savedPhone = it.savedPhone,
                sportFilter = it.sportFilter,
                checkingSession = false,
                loginNeeded = true,
            )
        }
    }

    fun selectSportFilter(value: String) {
        if (_ui.value.sportFilter == value) return
        prefs.edit().putString("filter_sport", value).apply()
        _ui.update { it.copy(sportFilter = value) }
    }

    fun selectDate(date: LocalDate) {
        if (_ui.value.user == null) return
        val cached = venueCache[date]
        if (cached != null) {
            _ui.update {
                it.copy(
                    selectedDate = date,
                    venues = cached,
                    venuesLoaded = true,
                    loadingVenues = false,
                )
            }
            return
        }
        loadVenues(date)
    }

    fun refreshAll() {
        if (_ui.value.user == null) return
        val date = _ui.value.selectedDate
        venueCache.remove(date)
        loadVenues(date)
        loadRecords()
    }

    fun book(venue: GymVenue, slot: GymSlot, phone: String) {
        val state = _ui.value
        val user = state.user ?: return
        if (state.booking) return
        if (!PHONE_REGEX.matches(phone)) {
            _ui.update {
                it.copy(notice = GymNotice("请输入正确的 11 位手机号", error = true))
            }
            return
        }
        prefs.edit().putString("phone", phone).apply()
        val date = state.selectedDate
        _ui.update { it.copy(booking = true, savedPhone = phone) }
        viewModelScope.launch {
            try {
                val confirmed = withContext(Dispatchers.IO) {
                    api.book(user, venue, slot, date, phone)
                }
                val message = if (confirmed != null) {
                    "预约成功：${venue.name} ${slot.time}"
                } else {
                    "预约已提交，请在“我的预约”中确认状态"
                }
                _ui.update {
                    it.copy(booking = false, notice = GymNotice(message))
                }
                venueCache.remove(date)
                loadVenues(date)
                loadRecords()
            } catch (e: Exception) {
                handleApiError(e)
            }
        }
    }

    fun cancel(record: GymRecord) {
        if (record.wid.isEmpty() || _ui.value.cancellingWid != null) return
        _ui.update { it.copy(cancellingWid = record.wid) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { api.cancel(record.wid) }
                _ui.update {
                    it.copy(
                        cancellingWid = null,
                        notice = GymNotice("已取消：${record.venueName} ${record.slot}"),
                    )
                }
                loadRecords()
                record.date?.let { date ->
                    venueCache.remove(date)
                    if (date == _ui.value.selectedDate) loadVenues(date)
                }
            } catch (e: Exception) {
                handleApiError(e)
            }
        }
    }

    fun consumeNotice() {
        _ui.update { it.copy(notice = null) }
    }

    private fun loadAll() {
        if (_ui.value.user == null) return
        loadVenues(_ui.value.selectedDate)
        loadRecords()
    }

    private fun loadVenues(date: LocalDate) {
        if (_ui.value.user == null) return
        _ui.update { old ->
            old.copy(
                selectedDate = date,
                venues = if (old.selectedDate == date) old.venues else emptyList(),
                loadingVenues = true,
                venuesLoaded = false,
            )
        }
        viewModelScope.launch {
            try {
                val venues = withContext(Dispatchers.IO) { api.fetchVenues(date) }
                venueCache[date] = venues
                WyuMorphPageState.awaitSettled()
                if (_ui.value.selectedDate != date) return@launch
                _ui.update {
                    it.copy(
                        venues = venues,
                        loadingVenues = false,
                        venuesLoaded = true,
                    )
                }
            } catch (e: Exception) {
                if (_ui.value.selectedDate == date) {
                    _ui.update {
                        it.copy(loadingVenues = false, venuesLoaded = true)
                    }
                }
                handleApiError(e)
            }
        }
    }

    private fun loadRecords() {
        if (_ui.value.user == null) return
        _ui.update { it.copy(loadingRecords = true) }
        viewModelScope.launch {
            try {
                val records = withContext(Dispatchers.IO) { api.fetchRecords() }
                WyuMorphPageState.awaitSettled()
                _ui.update {
                    it.copy(
                        records = records,
                        loadingRecords = false,
                        recordsLoaded = true,
                    )
                }
            } catch (e: Exception) {
                _ui.update { it.copy(loadingRecords = false, recordsLoaded = true) }
                handleApiError(e)
            }
        }
    }

    private fun handleApiError(e: Exception) {
        if (e is GymException && e.authExpired) {
            clearStoredSession()
            api.resetSession()
            venueCache.clear()
            _ui.update {
                it.copy(
                    user = null,
                    loginNeeded = true,
                    loginError = "登录已失效，请重新登录。",
                    loadingVenues = false,
                    loadingRecords = false,
                    booking = false,
                    cancellingWid = null,
                )
            }
            return
        }
        _ui.update {
            it.copy(
                notice = GymNotice(friendlyMessage(e), error = true),
                loadingVenues = false,
                loadingRecords = false,
                booking = false,
                cancellingWid = null,
            )
        }
    }

    private fun saveUser(user: GymUser) {
        prefs.edit()
            .putString("name", user.name)
            .putString("account", user.account)
            .apply()
    }

    private fun clearStoredSession() {
        GymSessionStore.clear(getApplication())
        prefs.edit().remove("cookie").remove("name").remove("account").apply()
    }

    private fun friendlyMessage(e: Exception): String = when (e) {
        is GymException -> e.message ?: "操作失败"
        is UnknownHostException -> "无法连接学校服务器，请连接校园网后重试"
        is ConnectException -> "无法连接学校服务器，请确认已连接校园网"
        is SocketTimeoutException -> "连接学校服务器超时，请稍后重试"
        is IOException -> "网络请求失败，请检查校园网连接"
        else -> "操作失败，请稍后重试"
    }

    companion object {
        private val PHONE_REGEX = Regex("^1[3-9]\\d{9}$")
    }
}
