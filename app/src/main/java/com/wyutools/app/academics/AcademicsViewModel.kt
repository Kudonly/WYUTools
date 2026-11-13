package com.wyutools.app.academics

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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** 单科分析单的加载状态。 */
data class GradeDetailState(
    val cjdm: String? = null,
    val loading: Boolean = false,
    val detail: GradeDetail? = null,
    val error: String? = null,
)

data class EvalDetailState(
    val key: String? = null,
    val loading: Boolean = false,
    val detail: EvalDetail? = null,
    val error: String? = null,
)

/**
 * 成绩 / 平均绩点 / 体测 / 教评。四块数据同源（同一套教务会话与学期码），
 * 放在一起可以复用入学学期的推断，也只用维护一份缓存。
 *
 * 缓存的是**原始响应**：重新打开页面时先解析本地 JSON 立刻出内容，再在后台
 * 拉新的，格式一变也不会读出脏数据。
 */
class AcademicsViewModel(
    app: Application,
    private val auth: AuthViewModel,
) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _grades = MutableStateFlow(GradesUiState())
    val grades: StateFlow<GradesUiState> = _grades.asStateFlow()

    private val _gpa = MutableStateFlow(GpaUiState())
    val gpa: StateFlow<GpaUiState> = _gpa.asStateFlow()

    private val _physical = MutableStateFlow(PhysicalUiState())
    val physical: StateFlow<PhysicalUiState> = _physical.asStateFlow()

    private val _eval = MutableStateFlow(EvalUiState())
    val eval: StateFlow<EvalUiState> = _eval.asStateFlow()

    private val _gradeDetail = MutableStateFlow(GradeDetailState())
    val gradeDetail: StateFlow<GradeDetailState> = _gradeDetail.asStateFlow()

    private val _evalDetail = MutableStateFlow(EvalDetailState())
    val evalDetail: StateFlow<EvalDetailState> = _evalDetail.asStateFlow()

    private var gradesJob: Job? = null
    private var gpaJob: Job? = null
    private var physicalJob: Job? = null
    private var evalJob: Job? = null

    init {
        restoreCache()
    }

    // -------------------- 成绩 --------------------

    fun onGradesEnter() {
        if (loginRequired()) {
            _grades.update { it.copy(loading = false, error = "登录统一身份认证后即可查看成绩。") }
            return
        }
        val cached = _grades.value.data
        val stale = cached == null ||
            System.currentTimeMillis() - cached.fetchedAt > CACHE_TTL_MS
        if (cached == null) _grades.update { it.copy(loading = true) }
        if (stale) refreshGrades() else _grades.update { it.copy(loading = false) }
    }

    fun refreshGrades(showNotice: Boolean = true) {
        if (gradesJob?.isActive == true) return
        if (loginRequired()) {
            _grades.update { it.copy(loading = false, error = "登录统一身份认证后即可查看成绩。") }
            return
        }
        val hadData = _grades.value.data != null
        _grades.update { it.copy(loading = !hadData, refreshing = true, error = null) }
        gradesJob = viewModelScope.launch {
            try {
                val raw = auth.fetchGrades()
                val parsed = withContext(Dispatchers.Default) { AcademicsParser.parseGrades(raw) }
                prefs.edit().putString(KEY_GRADES, raw).apply()
                if (hadData) WyuMorphPageState.awaitSettled()
                _grades.update {
                    it.copy(
                        data = parsed,
                        loading = false,
                        refreshing = false,
                        error = null,
                        notice = if (showNotice) "成绩已更新。" else null,
                    )
                }
            } catch (error: Throwable) {
                failGrades(error, hadData, showNotice)
            }
        }
    }

    fun consumeGradesNotice() = _grades.update { it.copy(notice = null) }

    fun loadGradeDetail(entry: GradeEntry) {
        val cjdm = entry.cjdm ?: run {
            _gradeDetail.value = GradeDetailState(error = "这条成绩没有提供分项明细。")
            return
        }
        if (_gradeDetail.value.cjdm == cjdm && _gradeDetail.value.detail != null) return
        _gradeDetail.value = GradeDetailState(cjdm = cjdm, loading = true)
        viewModelScope.launch {
            try {
                val pair = coroutineScope {
                    val detail = async { auth.fetchGradeDetail(cjdm) }
                    val ranks = async {
                        runCatching { auth.fetchGradeRankSheet(cjdm) }.getOrNull()
                    }
                    detail.await() to ranks.await()
                }
                val parsed = withContext(Dispatchers.Default) {
                    AcademicsParser.parseGradeDetail(pair.first, pair.second)
                }
                _gradeDetail.value = GradeDetailState(cjdm = cjdm, detail = parsed)
            } catch (error: Throwable) {
                _gradeDetail.value = GradeDetailState(
                    cjdm = cjdm,
                    error = friendlyMessage(error),
                )
            }
        }
    }

    fun clearGradeDetail() {
        _gradeDetail.value = GradeDetailState()
    }

    // -------------------- 平均绩点 --------------------

    fun onGpaEnter() {
        if (loginRequired()) {
            _gpa.update { it.copy(loading = false, error = "登录统一身份认证后即可查看平均绩点。") }
            return
        }
        val cached = _gpa.value.data
        if (cached == null) _gpa.update { it.copy(loading = true) }
        val stale = cached == null ||
            System.currentTimeMillis() - cached.fetchedAt > CACHE_TTL_MS
        if (stale) refreshGpa() else _gpa.update { it.copy(loading = false) }
    }

    fun refreshGpa() {
        if (gpaJob?.isActive == true) return
        if (loginRequired()) {
            _gpa.update { it.copy(loading = false, error = "登录统一身份认证后即可查看平均绩点。") }
            return
        }
        val hadData = _gpa.value.data != null
        _gpa.update { it.copy(loading = !hadData, refreshing = true, error = null) }
        gpaJob = viewModelScope.launch {
            try {
                val pair = coroutineScope {
                    val overall = async { auth.fetchGpa(SCOPE_YEARLY) }
                    val terms = async { auth.fetchGpa(SCOPE_TERM) }
                    overall.await() to terms.await()
                }
                val parsed = withContext(Dispatchers.Default) {
                    AcademicsParser.parseGpa(pair.first, pair.second)
                }
                prefs.edit()
                    .putString(KEY_GPA_OVERALL, pair.first)
                    .putString(KEY_GPA_TERM, pair.second)
                    .apply()
                _gpa.update {
                    it.copy(data = parsed, loading = false, refreshing = false, error = null)
                }
            } catch (error: Throwable) {
                _gpa.update {
                    if (hadData) {
                        it.copy(loading = false, refreshing = false)
                    } else {
                        it.copy(loading = false, refreshing = false, error = friendlyMessage(error))
                    }
                }
            }
        }
    }

    // -------------------- 体测 --------------------

    fun onPhysicalEnter() {
        if (loginRequired()) {
            _physical.update { it.copy(loading = false, error = "登录统一身份认证后即可查看体测。") }
            return
        }
        val cached = _physical.value.data
        if (cached == null) _physical.update { it.copy(loading = true) }
        val stale = cached == null ||
            System.currentTimeMillis() - cached.fetchedAt > CACHE_TTL_MS
        if (stale) refreshPhysical() else _physical.update { it.copy(loading = false) }
    }

    fun refreshPhysical() {
        if (physicalJob?.isActive == true) return
        if (loginRequired()) {
            _physical.update { it.copy(loading = false, error = "登录统一身份认证后即可查看体测。") }
            return
        }
        val hadData = _physical.value.data != null
        _physical.update { it.copy(loading = !hadData, refreshing = true, error = null) }
        physicalJob = viewModelScope.launch {
            try {
                val raw = auth.fetchPhysicalTests()
                val parsed = withContext(Dispatchers.Default) { AcademicsParser.parsePhysical(raw) }
                prefs.edit().putString(KEY_PHYSICAL, raw).apply()
                _physical.update {
                    it.copy(data = parsed, loading = false, refreshing = false, error = null)
                }
            } catch (error: Throwable) {
                _physical.update {
                    if (hadData) {
                        it.copy(loading = false, refreshing = false)
                    } else {
                        it.copy(loading = false, refreshing = false, error = friendlyMessage(error))
                    }
                }
            }
        }
    }

    // -------------------- 教评 --------------------

    fun onEvalEnter() {
        if (loginRequired()) {
            _eval.update { it.copy(loading = false, error = "登录统一身份认证后即可查看结课评价。") }
            return
        }
        val cached = _eval.value.data
        if (cached == null) _eval.update { it.copy(loading = true) }
        val stale = cached == null ||
            System.currentTimeMillis() - cached.fetchedAt > CACHE_TTL_MS
        if (stale) refreshEval() else _eval.update { it.copy(loading = false) }
    }

    fun refreshEval() {
        if (evalJob?.isActive == true) return
        if (loginRequired()) {
            _eval.update { it.copy(loading = false, error = "登录统一身份认证后即可查看结课评价。") }
            return
        }
        val hadData = _eval.value.data != null
        _eval.update { it.copy(loading = !hadData, refreshing = true, error = null) }
        evalJob = viewModelScope.launch {
            try {
                val terms = availableTerms()
                val results = coroutineScope {
                    terms.map { term ->
                        async {
                            val raw = runCatching { auth.fetchEvalTasks(term) }.getOrNull()
                            term to raw
                        }
                    }.awaitAll()
                }
                val parsed = withContext(Dispatchers.Default) {
                    val rawMap = JSONObject()
                    val evalTerms = results.mapNotNull { (term, raw) ->
                        if (raw == null) return@mapNotNull null
                        rawMap.put(term, raw)
                        val tasks = AcademicsParser.parseEvalTasks(raw, term)
                        if (tasks.isEmpty()) null else EvalTerm(
                            code = term,
                            name = termDisplayName(term),
                            tasks = tasks,
                        )
                    }.sortedByDescending { it.code }
                    prefs.edit().putString(KEY_EVAL, rawMap.toString()).apply()
                    EvalData(terms = evalTerms, fetchedAt = System.currentTimeMillis())
                }
                _eval.update {
                    it.copy(data = parsed, loading = false, refreshing = false, error = null)
                }
            } catch (error: Throwable) {
                _eval.update {
                    if (hadData) {
                        it.copy(loading = false, refreshing = false)
                    } else {
                        it.copy(loading = false, refreshing = false, error = friendlyMessage(error))
                    }
                }
            }
        }
    }

    fun loadEvalDetail(task: EvalTask) {
        val pjdm = task.pjdm ?: run {
            _evalDetail.value = EvalDetailState(error = "这门课还没有可查看的评价记录。")
            return
        }
        if (_evalDetail.value.key == task.key && _evalDetail.value.detail != null) return
        _evalDetail.value = EvalDetailState(key = task.key, loading = true)
        viewModelScope.launch {
            try {
                val raw = auth.fetchEvalDetail(pjdm)
                val parsed = withContext(Dispatchers.Default) {
                    AcademicsParser.parseEvalDetail(task, raw)
                }
                _evalDetail.value = if (parsed.answers.isEmpty()) {
                    EvalDetailState(key = task.key, error = "教务没有返回这门课的评价明细。")
                } else {
                    EvalDetailState(key = task.key, detail = parsed)
                }
            } catch (error: Throwable) {
                _evalDetail.value = EvalDetailState(
                    key = task.key,
                    error = friendlyMessage(error),
                )
            }
        }
    }

    fun clearEvalDetail() {
        _evalDetail.value = EvalDetailState()
    }

    // -------------------- 内部 --------------------

    private fun loginRequired(): Boolean = !auth.ui.value.loggedIn

    /**
     * 教评可选学期：从最早有成绩的学期到当前学期。
     * 成绩还没拉过时先向教务要一次，失败就按学号推入学年份。
     */
    private suspend fun availableTerms(): List<String> {
        val current = ScheduleTerm.currentCode()
        val earliest = _grades.value.data?.earliestTermCode
            ?: runCatching {
                val raw = auth.fetchGrades()
                prefs.edit().putString(KEY_GRADES, raw).apply()
                withContext(Dispatchers.Default) { AcademicsParser.parseGrades(raw) }
            }.getOrNull()?.earliestTermCode
            ?: enrollmentTermFromAccount()
            ?: current
        val terms = ArrayList<String>()
        var year = earliest.take(4).toIntOrNull() ?: current.take(4).toIntOrNull() ?: 0
        var half = earliest.drop(4).toIntOrNull() ?: 1
        val currentYear = current.take(4).toIntOrNull() ?: year
        val currentHalf = current.drop(4).toIntOrNull() ?: 1
        while (year < currentYear || (year == currentYear && half <= currentHalf)) {
            terms += String.format(java.util.Locale.US, "%d%02d", year, half)
            if (half == 1) half = 2 else {
                half = 1
                year += 1
            }
            if (terms.size > 16) break
        }
        return terms.ifEmpty { listOf(current) }
    }

    /** 学号第 3、4 位是入学年份（"3125001460" -> 2025）。 */
    private fun enrollmentTermFromAccount(): String? {
        val account = runCatching { auth.ui.value.profile?.account }.getOrNull() ?: return null
        val digits = account.filter { it.isDigit() }
        if (digits.length < 4) return null
        val year = digits.substring(2, 4).toIntOrNull() ?: return null
        return String.format(java.util.Locale.US, "%d01", 2000 + year)
    }

    private fun failGrades(error: Throwable, hadData: Boolean, showNotice: Boolean) {
        val message = friendlyMessage(error)
        _grades.update {
            if (hadData) {
                it.copy(
                    loading = false,
                    refreshing = false,
                    notice = if (showNotice || error !is SsoException) message else it.notice,
                )
            } else {
                it.copy(loading = false, refreshing = false, error = message)
            }
        }
    }

    private fun friendlyMessage(error: Throwable): String = when (error) {
        is SsoException -> error.message ?: "教务系统访问失败。"
        is UnknownHostException -> "网络连接失败，请检查网络后重试。"
        is SocketTimeoutException -> "网络连接超时，请稍后重试。"
        is ConnectException -> "无法连接服务器，请稍后重试。"
        is IOException -> "网络连接失败，请稍后重试。"
        else -> error.message ?: "加载失败，请稍后重试。"
    }

    private fun restoreCache() {
        prefs.getString(KEY_GRADES, null)?.let { raw ->
            runCatching { AcademicsParser.parseGrades(raw) }.getOrNull()?.let { data ->
                _grades.value = GradesUiState(data = data)
            }
        }
        val gpaOverall = prefs.getString(KEY_GPA_OVERALL, null)
        val gpaTerms = prefs.getString(KEY_GPA_TERM, null)
        if (gpaOverall != null) {
            runCatching { AcademicsParser.parseGpa(gpaOverall, gpaTerms) }.getOrNull()?.let { data ->
                _gpa.value = GpaUiState(data = data.copy(fetchedAt = 0L))
            }
        }
        prefs.getString(KEY_PHYSICAL, null)?.let { raw ->
            runCatching { AcademicsParser.parsePhysical(raw) }.getOrNull()?.let { data ->
                _physical.value = PhysicalUiState(data = data.copy(fetchedAt = 0L))
            }
        }
        prefs.getString(KEY_EVAL, null)?.let { raw ->
            runCatching {
                val root = JSONObject(raw)
                val terms = root.keys().asSequence().mapNotNull { term ->
                    val body = root.optString(term)
                    val tasks = AcademicsParser.parseEvalTasks(body, term)
                    if (tasks.isEmpty()) null else EvalTerm(
                        code = term,
                        name = termDisplayName(term),
                        tasks = tasks,
                    )
                }.sortedByDescending { it.code }.toList()
                EvalData(terms = terms, fetchedAt = 0L)
            }.getOrNull()?.let { data ->
                _eval.value = EvalUiState(data = data)
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "academics"
        private const val KEY_GRADES = "grades"
        private const val KEY_GPA_OVERALL = "gpa_overall"
        private const val KEY_GPA_TERM = "gpa_term"
        private const val KEY_PHYSICAL = "physical"
        private const val KEY_EVAL = "eval"
        private const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L

        /** 教务"统计方式"的三个取值。 */
        const val SCOPE_TERM = 1
        const val SCOPE_SCHOOL_YEAR = 2
        const val SCOPE_YEARLY = 3
    }
}
