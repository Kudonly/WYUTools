package com.wyutools.app.elec

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.math.RoundingMode
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

data class ElecUiState(
    val building: String = "",
    val room: String = "",
    val remember: Boolean = false,
    val loading: Boolean = false,
    val result: ElecInfo? = null,
    val error: String? = null,
    val history: List<ElecSnapshot> = emptyList(),
    val stats: ElecStats = ElecStats(),
    val period: ElecPeriod = ElecPeriod.ALL,
    val recharge: ElecRechargePage? = null,
    val rechargeLoading: Boolean = false,
    val rechargeError: String? = null,
    val rechargeOpen: Boolean = false,
    val rechargeAmount: String = "",
    val rechargeChannel: ElecPayChannel = ElecPayChannel.WECHAT,
    val order: ElecRechargeOrder? = null,
    val qrBitmap: Bitmap? = null,
    val orderLoading: Boolean = false,
    val orderError: String? = null,
    val payChecking: Boolean = false,
    val payStatus: ElecPayStatus = ElecPayStatus.IDLE,
    val payStatusText: String? = null,
    val orderExpireAt: Long = 0L,
)

enum class ElecPayStatus {
    IDLE,
    CHECKING,
    SUCCESS,
    FAILED,
    TIMEOUT,
}

class ElecViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("elec", Context.MODE_PRIVATE)
    private var autoQueried = false
    private var createOrderJob: Job? = null
    private var payJob: Job? = null

    private val _ui = MutableStateFlow(
        ElecUiState(
            building = prefs.getString("building", "").orEmpty(),
            room = prefs.getString("room", "").orEmpty(),
            remember = prefs.getBoolean("remember", false),
            period = runCatching {
                ElecPeriod.valueOf(prefs.getString("period", ElecPeriod.ALL.name).orEmpty())
            }.getOrDefault(ElecPeriod.ALL),
        )
    )
    val ui: StateFlow<ElecUiState> = _ui.asStateFlow()

    fun onEntered() {
        val state = _ui.value
        if (!autoQueried && state.remember && state.building.isNotBlank() && state.room.isNotBlank()) {
            autoQueried = true
            query()
        }
    }

    fun setBuilding(value: String) {
        val filtered = value.filter { c -> c.isDigit() }
        if (_ui.value.building == filtered) return
        cancelRechargeJobs()
        _ui.update {
            it.copy(
                building = filtered,
                result = null,
                error = null,
                history = emptyList(),
                stats = ElecStats(),
                recharge = null,
                rechargeError = null,
                rechargeLoading = false,
                rechargeOpen = false,
                order = null,
                qrBitmap = null,
                orderLoading = false,
                orderError = null,
                payChecking = false,
                payStatus = ElecPayStatus.IDLE,
                payStatusText = null,
                orderExpireAt = 0L,
            )
        }
    }

    fun setRoom(value: String) {
        val filtered = value.filter { c -> c.isDigit() }
        if (_ui.value.room == filtered) return
        cancelRechargeJobs()
        _ui.update {
            it.copy(
                room = filtered,
                result = null,
                error = null,
                history = emptyList(),
                stats = ElecStats(),
                recharge = null,
                rechargeError = null,
                rechargeLoading = false,
                rechargeOpen = false,
                order = null,
                qrBitmap = null,
                orderLoading = false,
                orderError = null,
                payChecking = false,
                payStatus = ElecPayStatus.IDLE,
                payStatusText = null,
                orderExpireAt = 0L,
            )
        }
    }

    fun setRemember(value: Boolean) {
        _ui.update { it.copy(remember = value) }
        val editor = prefs.edit().putBoolean("remember", value)
        if (value) {
            val state = _ui.value
            editor.putString("building", state.building.trim())
                .putString("room", state.room.trim())
                .apply()
        } else {
            editor.remove("building").remove("room").apply()
        }
    }

    fun query() {
        val state = _ui.value
        if (state.loading) return
        val building = state.building.trim()
        val room = state.room.trim()
        if (building.isEmpty()) {
            _ui.update { it.copy(error = "请填写楼栋号码") }
            return
        }
        if (room.isEmpty()) {
            _ui.update { it.copy(error = "请填写房间号") }
            return
        }
        if (state.remember) {
            prefs.edit().putString("building", building).putString("room", room).apply()
        }
        _ui.update {
            it.copy(
                loading = true,
                error = null,
                recharge = null,
                rechargeError = null,
                rechargeLoading = false,
            )
        }
        viewModelScope.launch {
            try {
                val (info, history) = withContext(Dispatchers.IO) {
                    val queryInfo = ElecApi.query(building, room)
                    val key = historyKey(building, room)
                    val merged = appendSnapshot(decodeHistory(prefs.getString(key, null)), queryInfo)
                    prefs.edit().putString(key, encodeHistory(merged)).apply()
                    queryInfo to merged
                }
                val stats = computeStats(history, info.remainingKwh)
                _ui.update {
                    it.copy(
                        loading = false,
                        result = info,
                        history = history,
                        stats = stats,
                    )
                }
                loadRecharge(building, room, _ui.value.period)
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = friendlyMessage(e)) }
            }
        }
    }

    fun setPeriod(period: ElecPeriod) {
        if (_ui.value.period == period) return
        prefs.edit().putString("period", period.name).apply()
        _ui.update {
            it.copy(
                period = period,
                recharge = null,
                rechargeError = null,
                rechargeLoading = false,
            )
        }
        val state = _ui.value
        if (state.result != null) {
            loadRecharge(state.building.trim(), state.room.trim(), period)
        }
    }

    fun retryRecharge() {
        val state = _ui.value
        if (state.rechargeLoading) return
        loadRecharge(state.building.trim(), state.room.trim(), state.period)
    }

    fun openRecharge() {
        if (_ui.value.result == null) return
        cancelRechargeJobs()
        _ui.update {
            it.copy(
                rechargeOpen = true,
                rechargeAmount = "",
                rechargeChannel = ElecPayChannel.WECHAT,
                order = null,
                qrBitmap = null,
                orderLoading = false,
                orderError = null,
                payChecking = false,
                payStatus = ElecPayStatus.IDLE,
                payStatusText = null,
                orderExpireAt = 0L,
            )
        }
    }

    fun dismissRecharge() {
        cancelRechargeJobs()
        _ui.update {
            it.copy(
                rechargeOpen = false,
                order = null,
                qrBitmap = null,
                orderLoading = false,
                orderError = null,
                payChecking = false,
                payStatus = ElecPayStatus.IDLE,
                payStatusText = null,
                orderExpireAt = 0L,
            )
        }
    }

    fun setRechargeAmount(value: String) {
        val normalized = normalizeRechargeAmount(value) ?: return
        _ui.update { it.copy(rechargeAmount = normalized, orderError = null) }
    }

    fun setRechargeChannel(channel: ElecPayChannel) {
        _ui.update { it.copy(rechargeChannel = channel, orderError = null) }
    }

    fun createRechargeOrder() {
        val state = _ui.value
        if (state.orderLoading) return
        val building = state.building.trim()
        val room = state.room.trim()
        if (state.result == null || building.isEmpty() || room.isEmpty()) {
            _ui.update { it.copy(orderError = "请先查询电费后再充值") }
            return
        }
        val amountCent = parseAmountCent(state.rechargeAmount)
        if (amountCent == null || amountCent !in 100..100_000) {
            _ui.update { it.copy(orderError = "请输入 1 至 1000 元的充值金额") }
            return
        }
        cancelRechargeJobs()
        _ui.update {
            it.copy(
                orderLoading = true,
                orderError = null,
                order = null,
                qrBitmap = null,
                payChecking = false,
                payStatus = ElecPayStatus.IDLE,
                payStatusText = null,
                orderExpireAt = 0L,
            )
        }
        createOrderJob = viewModelScope.launch {
            try {
                val channel = _ui.value.rechargeChannel
                val order = withContext(Dispatchers.IO) {
                    ElecApi.createRechargeOrder(building, room, amountCent, channel)
                }
                val bitmap = try {
                    withContext(Dispatchers.Default) { QrCodes.encode(order.qrContent) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    throw ElecException("二维码生成失败，请重试")
                }
                if (!_ui.value.rechargeOpen) return@launch
                _ui.update {
                    it.copy(
                        order = order,
                        qrBitmap = bitmap,
                        orderLoading = false,
                        orderExpireAt = System.currentTimeMillis() + order.expireSeconds * 1000L,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!_ui.value.rechargeOpen) return@launch
                _ui.update { it.copy(orderLoading = false, orderError = friendlyMessage(e)) }
            }
        }
    }

    fun confirmPaid() {
        val order = _ui.value.order ?: return
        if (_ui.value.payChecking) return
        payJob?.cancel()
        _ui.update {
            it.copy(
                payChecking = true,
                payStatus = ElecPayStatus.CHECKING,
                payStatusText = "正在查询支付结果…",
            )
        }
        payJob = viewModelScope.launch {
            val deadline = System.currentTimeMillis() + 60_000L
            var lastError: String? = null
            var checks = 0
            while (true) {
                try {
                    val result = withContext(Dispatchers.IO) {
                        ElecApi.checkRechargeOrder(order.orderCode)
                    }
                    lastError = null
                    when (result) {
                        ElecPayResult.SUCCESS -> {
                            _ui.update {
                                it.copy(
                                    payChecking = false,
                                    payStatus = ElecPayStatus.SUCCESS,
                                    payStatusText = null,
                                )
                            }
                            query()
                            return@launch
                        }

                        ElecPayResult.FAILED -> {
                            _ui.update {
                                it.copy(
                                    payChecking = false,
                                    payStatus = ElecPayStatus.FAILED,
                                    payStatusText = "支付失败，请重新充值",
                                )
                            }
                            return@launch
                        }

                        ElecPayResult.PENDING -> Unit
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = friendlyMessage(e)
                }
                checks += 1
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) {
                    _ui.update {
                        it.copy(
                            payChecking = false,
                            payStatus = ElecPayStatus.TIMEOUT,
                            payStatusText = lastError
                                ?: "暂未查询到支付结果，订单可能仍在处理中，请稍后刷新余额确认",
                        )
                    }
                    return@launch
                }
                _ui.update {
                    it.copy(
                        payStatusText = lastError ?: "正在等待支付结果…（已查询 $checks 次）",
                    )
                }
                delay(minOf(5_000L, remaining))
            }
        }
    }

    private fun cancelRechargeJobs() {
        createOrderJob?.cancel()
        createOrderJob = null
        payJob?.cancel()
        payJob = null
    }

    private fun parseAmountCent(raw: String): Int? {
        val value = raw.trim().toBigDecimalOrNull() ?: return null
        if (value.signum() <= 0 || value.scale() > 2) return null
        return runCatching {
            value.movePointRight(2)
                .setScale(0, RoundingMode.UNNECESSARY)
                .intValueExact()
        }.getOrNull()
    }

    private fun loadRecharge(building: String, room: String, period: ElecPeriod) {
        if (building.isEmpty() || room.isEmpty()) return
        viewModelScope.launch {
            _ui.update { it.copy(rechargeLoading = true, rechargeError = null) }
            try {
                val page = withContext(Dispatchers.IO) {
                    ElecApi.rechargeHistory(building, room, period)
                }
                if (!isCurrentQuery(building, room, period)) return@launch
                _ui.update {
                    it.copy(recharge = page, rechargeLoading = false, rechargeError = null)
                }
            } catch (e: Exception) {
                if (!isCurrentQuery(building, room, period)) return@launch
                _ui.update {
                    it.copy(rechargeLoading = false, rechargeError = friendlyMessage(e))
                }
            }
        }
    }

    private fun isCurrentQuery(building: String, room: String, period: ElecPeriod): Boolean {
        val state = _ui.value
        return state.building.trim() == building &&
            state.room.trim() == room &&
            state.period == period
    }

    private fun historyKey(building: String, room: String): String = "history_${building}_$room"

    private fun friendlyMessage(e: Exception): String = when (e) {
        is ElecException -> e.message ?: "查询失败"
        is UnknownHostException -> "无法连接服务器，请连接校园网后重试"
        is ConnectException -> "无法连接电费服务器，请确认已连接校园网"
        is SocketTimeoutException -> "连接超时，请检查校园网后重试"
        is IOException -> {
            if (e.message.orEmpty().contains("CLEARTEXT", ignoreCase = true)) {
                "系统阻止了明文 HTTP 请求，请更新 App 后重试"
            } else {
                "网络请求失败，请检查是否连接校园网"
            }
        }
        else -> "查询失败，请稍后重试"
    }
}

/** 过滤充值金额输入；整数部分超过 4 位时返回 null，表示忽略本次输入。 */
internal fun normalizeRechargeAmount(value: String): String? {
    val builder = StringBuilder()
    var hasDot = false
    for (ch in value) {
        when {
            ch.isDigit() -> builder.append(ch)
            ch == '.' && !hasDot -> {
                hasDot = true
                builder.append(ch)
            }
        }
    }
    val raw = builder.toString()
    val dot = raw.indexOf('.')
    val intDigits = if (dot >= 0) raw.substring(0, dot) else raw
    if (intDigits.length > 4) return null
    return if (dot >= 0) {
        "$intDigits.${raw.substring(dot + 1).take(2)}"
    } else {
        intDigits
    }
}
