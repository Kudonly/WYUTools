package com.wyutools.app.schedule

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wyutools.app.auth.AuthViewModel
import com.wyutools.app.auth.SsoException
import com.wyutools.app.ui.WyuMorphPageState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

class ScheduleViewModel(
    app: Application,
    private val auth: AuthViewModel,
) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _ui = MutableStateFlow(ScheduleUiState())
    val ui: StateFlow<ScheduleUiState> = _ui.asStateFlow()

    private var refreshJob: Job? = null

    init {
        restoreCache()
    }

    fun onEnter() = maybeAutoRefresh()

    fun onAuthReady() = maybeAutoRefresh()

    fun refresh(showNotice: Boolean = true) {
        if (refreshJob?.isActive == true) return
        if (!auth.ui.value.loggedIn) {
            if (_ui.value.data == null) {
                _ui.update { it.copy(error = "登录统一身份认证后即可导入课表。") }
            } else {
                _ui.update { it.copy(notice = ScheduleNotice("请先登录后刷新课表。", error = true)) }
            }
            return
        }
        val termCode = ScheduleTerm.currentCode()
        val hadData = _ui.value.data != null
        _ui.update {
            it.copy(
                loading = it.data == null,
                refreshing = true,
                error = if (it.data == null) null else it.error,
            )
        }
        refreshJob = viewModelScope.launch {
            try {
                val raw = auth.fetchSchedule(termCode)
                val parsed = withContext(Dispatchers.Default) {
                    ScheduleParser.parse(raw, termCode)
                }
                if (hadData) WyuMorphPageState.awaitSettled()
                applyData(parsed, showNotice)
            } catch (error: ScheduleParseException) {
                if (error.sessionExpired) auth.recoverJxglSession()
                fail(error, hadData, showNotice)
            } catch (error: SsoException) {
                fail(error, hadData, showNotice)
            } catch (error: IOException) {
                fail(error, hadData, showNotice)
            } catch (error: Exception) {
                fail(error, hadData, showNotice)
            }
        }
    }

    fun selectWeek(week: Int) {
        _ui.update { state ->
            val max = state.data?.maxWeek ?: return@update state
            state.copy(selectedWeek = week.coerceIn(1, max))
        }
    }

    fun setShowWeekend(show: Boolean) {
        if (_ui.value.showWeekend == show) return
        prefs.edit().putBoolean(KEY_SHOW_WEEKEND, show).apply()
        _ui.update { it.copy(showWeekend = show) }
    }

    fun shouldShowWeekendHint(): Boolean =
        !prefs.getBoolean(KEY_WEEKEND_HINT_SEEN, false)

    fun dismissWeekendHint() {
        prefs.edit().putBoolean(KEY_WEEKEND_HINT_SEEN, true).apply()
    }

    /** 把当前这周标记为第 [week] 周，修正教务接口没有当前周信息带来的偏差。 */
    fun calibrateWeek(week: Int) {
        val data = _ui.value.data ?: return
        val clamped = week.coerceIn(1, data.maxWeek)
        val anchor = WeekAnchor(
            termCode = data.termCode,
            monday = ScheduleTerm.mondayOf(LocalDate.now()),
            week = clamped,
        )
        prefs.edit()
            .putString(KEY_ANCHOR_TERM, anchor.termCode)
            .putLong(KEY_ANCHOR_MONDAY, anchor.monday.toEpochDay())
            .putInt(KEY_ANCHOR_WEEK, anchor.week)
            .apply()
        _ui.update { it.copy(weekAnchor = anchor, selectedWeek = clamped) }
    }

    fun consumeNotice() {
        _ui.update { it.copy(notice = null) }
    }

    private fun maybeAutoRefresh() {
        if (!auth.ui.value.loggedIn) {
            if (_ui.value.data == null) _ui.update { it.copy(loading = false) }
            return
        }
        if (refreshJob?.isActive == true) return
        val data = _ui.value.data
        val stale = data == null ||
            data.termCode != ScheduleTerm.currentCode() ||
            System.currentTimeMillis() - data.fetchedAt > CACHE_TTL_MS
        if (data == null) {
            _ui.update { it.copy(loading = true, error = null) }
        }
        if (stale) refresh(showNotice = false) else _ui.update { it.copy(loading = false) }
    }

    private fun applyData(parsed: ScheduleData, showNotice: Boolean) {
        val current = _ui.value
        val sameTerm = current.data?.termCode == parsed.termCode
        val anchor = if (current.weekAnchor?.termCode == parsed.termCode) {
            current.weekAnchor
        } else {
            readAnchor(parsed.termCode)
        }
        val selected = if (sameTerm) {
            current.selectedWeek.coerceIn(1, parsed.maxWeek)
        } else {
            parsed.currentWeek(anchor).coerceIn(1, parsed.maxWeek)
        }
        persistCache(parsed)
        _ui.update {
            it.copy(
                data = parsed,
                loading = false,
                refreshing = false,
                error = null,
                selectedWeek = selected,
                weekAnchor = anchor,
                notice = if (showNotice) ScheduleNotice("课表已更新。") else null,
            )
        }
    }

    private fun fail(error: Throwable, hadData: Boolean, showNotice: Boolean) {
        val message = friendlyMessage(error)
        _ui.update {
            if (hadData) {
                it.copy(
                    loading = false,
                    refreshing = false,
                    notice = if (showNotice || error !is SsoException) {
                        ScheduleNotice(message, error = true)
                    } else {
                        it.notice
                    },
                )
            } else {
                it.copy(
                    loading = false,
                    refreshing = false,
                    error = message,
                )
            }
        }
    }

    private fun friendlyMessage(error: Throwable): String = when (error) {
        is ScheduleParseException -> error.message ?: "课表数据解析失败。"
        is SsoException -> error.message ?: "教务系统访问失败。"
        is UnknownHostException -> "网络连接失败，请检查网络后重试。"
        is SocketTimeoutException -> "网络连接超时，请稍后重试。"
        is ConnectException -> "无法连接服务器，请稍后重试。"
        is IOException -> "网络连接失败，请稍后重试。"
        else -> error.message ?: "课表加载失败，请稍后重试。"
    }

    private fun restoreCache() {
        val data = ScheduleCacheCodec.decode(prefs.getString(KEY_CACHE, null))
        val anchor = data?.let { readAnchor(it.termCode) }
        val showWeekend = prefs.getBoolean(KEY_SHOW_WEEKEND, false)
        if (data == null) {
            _ui.value = ScheduleUiState(showWeekend = showWeekend)
            return
        }
        _ui.value = ScheduleUiState(
            data = data,
            showWeekend = showWeekend,
            selectedWeek = data.currentWeek(anchor).coerceIn(1, data.maxWeek),
            weekAnchor = anchor,
        )
    }

    private fun persistCache(data: ScheduleData) {
        prefs.edit().putString(KEY_CACHE, ScheduleCacheCodec.encode(data)).apply()
    }

    private fun readAnchor(termCode: String): WeekAnchor? {
        if (prefs.getString(KEY_ANCHOR_TERM, null) != termCode) return null
        val monday = prefs.getLong(KEY_ANCHOR_MONDAY, Long.MIN_VALUE)
        if (monday == Long.MIN_VALUE) return null
        val week = prefs.getInt(KEY_ANCHOR_WEEK, 0)
        if (week !in 1..30) return null
        val date = runCatching { LocalDate.ofEpochDay(monday) }.getOrNull() ?: return null
        return WeekAnchor(termCode, date, week)
    }

    companion object {
        private const val PREFS_NAME = "schedule"
        private const val KEY_CACHE = "cache"
        private const val KEY_SHOW_WEEKEND = "show_weekend"
        private const val KEY_WEEKEND_HINT_SEEN = "weekend_hint_seen"
        private const val KEY_ANCHOR_TERM = "anchor_term"
        private const val KEY_ANCHOR_MONDAY = "anchor_monday"
        private const val KEY_ANCHOR_WEEK = "anchor_week"
        private const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L
    }
}
