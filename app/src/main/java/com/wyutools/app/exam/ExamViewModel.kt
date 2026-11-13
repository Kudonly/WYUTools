package com.wyutools.app.exam

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wyutools.app.auth.AuthViewModel
import com.wyutools.app.auth.SsoException
import com.wyutools.app.schedule.ScheduleTerm
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

/**
 * 教务考试安排。与课表共用同一个教务会话（[AuthViewModel] 里的 jxgl JSESSIONID），
 * 学期代码也复用课表那套（"202601" 这种格式），所以两页永远指向同一学期。
 */
class ExamViewModel(
    app: Application,
    private val auth: AuthViewModel,
) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _ui = MutableStateFlow(ExamUiState())
    val ui: StateFlow<ExamUiState> = _ui.asStateFlow()

    private var refreshJob: Job? = null

    init {
        restoreCache()
    }

    fun onEnter() {
        maybeAutoRefresh()
    }

    fun onAuthReady() = maybeAutoRefresh()

    fun refresh(showNotice: Boolean = true) {
        if (refreshJob?.isActive == true) return
        if (!auth.ui.value.loggedIn) {
            if (_ui.value.data == null) {
                _ui.update { it.copy(error = "登录统一身份认证后即可查看考试安排。") }
            } else {
                _ui.update { it.copy(notice = "请先登录后刷新考试安排。") }
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
                val raw = auth.fetchExams(termCode)
                val parsed = withContext(Dispatchers.Default) {
                    ExamParser.parse(raw, termCode)
                }
                // 刷新时页面可能还在开场动画里，等它停稳再换数据，避免动画
                // 采到一半内容。
                if (hadData) WyuMorphPageState.awaitSettled()
                applyData(parsed, showNotice)
            } catch (error: Throwable) {
                // 会话过期由 AuthViewModel.fetchExams 统一处理（它会拉起一次
                // 静默重新登录），这里只负责把失败翻译给人看。
                fail(error, hadData, showNotice)
            }
        }
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

    private fun applyData(parsed: ExamData, showNotice: Boolean) {
        persistCache(parsed)
        _ui.update {
            it.copy(
                data = parsed,
                loading = false,
                refreshing = false,
                error = null,
                notice = if (showNotice) "考试安排已更新。" else null,
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
                    // 有缓存时不把页面换成错误页，只飘一条提示。
                    notice = if (showNotice || error !is SsoException) message else it.notice,
                )
            } else {
                it.copy(loading = false, refreshing = false, error = message)
            }
        }
    }

    private fun friendlyMessage(error: Throwable): String = when (error) {
        is ExamException -> error.message ?: "考试安排解析失败。"
        is SsoException -> error.message ?: "教务系统访问失败。"
        is UnknownHostException -> "网络连接失败，请检查网络后重试。"
        is SocketTimeoutException -> "网络连接超时，请稍后重试。"
        is ConnectException -> "无法连接服务器，请稍后重试。"
        is IOException -> "网络连接失败，请稍后重试。"
        else -> error.message ?: "考试安排加载失败，请稍后重试。"
    }

    private fun restoreCache() {
        val data = ExamCache.decode(prefs.getString(KEY_CACHE, null)) ?: return
        _ui.value = ExamUiState(data = data)
    }

    private fun persistCache(data: ExamData) {
        prefs.edit().putString(KEY_CACHE, ExamCache.encode(data)).apply()
    }

    companion object {
        private const val PREFS_NAME = "exam"
        private const val KEY_CACHE = "cache"
        private const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L
    }
}
