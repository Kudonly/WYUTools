package com.wyutools.app.lxt

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wyutools.app.security.PasswordVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val KEY_PASSWORD = "lxt_password"
private const val VALVE_USING_MAX_AGE = 60 * 60 * 1000L
private const val VALVE_USING_RETRY_AGE = 10 * 60 * 1000L
private const val BALANCE_FRESH_AGE = 60 * 1000L

private val NaturalAreaOrder = Comparator<AreaNode> { a, b -> naturalCompare(a.name, b.name) }

/**
 * Campus entries name their water service ("…热水" / "…冷水"), so the campus
 * list hides the entries that belong to the other bind type. Neutral names
 * without either keyword stay visible for both types.
 */
private fun matchesBindType(name: String, forValve: Boolean): Boolean {
    val hot = name.contains("热水")
    val cold = name.contains("冷水")
    return if (forValve) hot || !cold else cold || !hot
}

/** Digit runs compare by value, so 2栋 < 10栋 and 101 < 1001. */
private fun naturalCompare(a: String, b: String): Int {
    var i = 0
    var j = 0
    while (i < a.length && j < b.length) {
        val ca = a[i]
        val cb = b[j]
        if (ca.isDigit() && cb.isDigit()) {
            val startA = i
            val startB = j
            while (i < a.length && a[i].isDigit()) i++
            while (j < b.length && b[j].isDigit()) j++
            val numA = a.substring(startA, i).trimStart('0')
            val numB = b.substring(startB, j).trimStart('0')
            val cmp = if (numA.length != numB.length) {
                numA.length - numB.length
            } else {
                numA.compareTo(numB)
            }
            if (cmp != 0) return cmp
        } else {
            if (ca != cb) return ca.compareTo(cb)
            i++
            j++
        }
    }
    return (a.length - i) - (b.length - j)
}

data class LxtUiState(
    val token: String? = null,
    val account: String = "",
    val password: String = "",
    val rememberPassword: Boolean = false,
    val captchaCode: String = "",
    val captchaBitmap: Bitmap? = null,
    val captchaLoading: Boolean = false,
    val loginBusy: Boolean = false,
    val loginError: String? = null,
    val hotCent: Long? = null,
    val hotError: String? = null,
    val coldCent: Long? = null,
    val coldError: String? = null,
    val balanceBusy: Boolean = false,
    val bind: BindInfo? = null,
    val valveDevice: ValveDevice? = null,
    val valveUsing: ValveUsing? = null,
    val valveUsingActive: Boolean = false,
    val valveChecking: Boolean = false,
    val valveBusy: Boolean = false,
    val valveResumePrompt: Boolean = false,
    val message: String? = null,
    val bindForValve: Boolean = false,
    val bindLevel: Int = 0,
    val bindPath: List<AreaNode> = emptyList(),
    val bindItems: List<AreaNode> = emptyList(),
    val bindLoading: Boolean = false,
    val bindRoom: AreaNode? = null,
    val bindMachine: MachineInfo? = null,
    val bindMachineChecked: Boolean = false,
    val bindError: String? = null,
)

class LxtViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("lxt", Context.MODE_PRIVATE)
    private val savedPassword = PasswordVault.load(app, KEY_PASSWORD)
    private var lastBalanceAt = 0L

    private val _ui = MutableStateFlow(
        LxtUiState(
            token = prefs.getString("token", null),
            account = prefs.getString("account", "").orEmpty(),
            password = savedPassword,
            rememberPassword = savedPassword.isNotEmpty(),
            bind = BindInfo.fromJson(prefs.getString("bind", null)),
            valveDevice = ValveDevice.fromJson(prefs.getString("valveDevice", null)),
            valveUsing = ValveUsing.fromJson(prefs.getString("valveUsing", null)),
        )
    )
    val ui: StateFlow<LxtUiState> = _ui.asStateFlow()

    fun setAccount(value: String) = _ui.update { it.copy(account = value) }

    fun setPassword(value: String) = _ui.update { it.copy(password = value) }

    fun setRememberPassword(value: Boolean) {
        if (_ui.value.rememberPassword == value) return
        if (!value) PasswordVault.clear(getApplication(), KEY_PASSWORD)
        _ui.update { it.copy(rememberPassword = value) }
    }

    fun setCaptchaCode(value: String) = _ui.update { it.copy(captchaCode = value.filter { c -> c.isDigit() }.take(5)) }

    fun consumeMessage() = _ui.update { it.copy(message = null) }

    fun onLxtEntered() {
        val state = _ui.value
        if (state.token == null) {
            if (state.captchaBitmap == null && !state.captchaLoading && state.account.trim().length >= 6) {
                loadCaptcha()
            }
        } else {
            refreshBalancesIfStale(state)
            if (state.valveUsing != null) validateValve(showResult = false)
        }
    }

    fun onLxtResumed() {
        val state = _ui.value
        if (state.token != null) {
            refreshBalancesIfStale(state)
            if (state.valveUsing != null) validateValve(showResult = false)
        }
    }

    private fun refreshBalancesIfStale(state: LxtUiState) {
        if (state.balanceBusy) return
        if (state.hotCent == null || System.currentTimeMillis() - lastBalanceAt > BALANCE_FRESH_AGE) {
            refreshBalances()
        }
    }

    fun loadCaptcha() {
        val account = _ui.value.account.trim()
        if (account.length < 6) {
            _ui.update { it.copy(message = "请先输入学号或手机号") }
            return
        }
        if (_ui.value.captchaLoading) return
        _ui.update { it.copy(captchaLoading = true, captchaBitmap = null, captchaCode = "") }
        viewModelScope.launch {
            try {
                val dataUrl = withContext(Dispatchers.IO) { LxtApi.getCode(account) }
                val base64 = dataUrl.substringAfter("base64,", dataUrl)
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                _ui.update { it.copy(captchaBitmap = bitmap, captchaLoading = false) }
            } catch (e: Exception) {
                _ui.update {
                    it.copy(
                        captchaLoading = false,
                        message = if (e is ApiException) e.message else "验证码获取失败，点击重试",
                    )
                }
            }
        }
    }

    fun login() {
        val state = _ui.value
        val account = state.account.trim()
        val password = state.password
        val captcha = state.captchaCode.trim()
        when {
            account.length < 6 -> {
                _ui.update { it.copy(message = "请输入学号或手机号") }
                return
            }

            password.isBlank() -> {
                _ui.update { it.copy(message = "请输入密码") }
                return
            }

            captcha.length != 5 -> {
                _ui.update { it.copy(message = "请输入 5 位验证码") }
                return
            }

            state.loginBusy -> return
        }
        _ui.update { it.copy(loginBusy = true, loginError = null) }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { LxtApi.login(account, password, captcha) }
                val oldAccount = prefs.getString("account", "").orEmpty()
                prefs.edit().putString("token", result.token).putString("account", account).apply()
                if (state.rememberPassword) {
                    PasswordVault.save(getApplication(), KEY_PASSWORD, password)
                } else {
                    PasswordVault.clear(getApplication(), KEY_PASSWORD)
                }
                if (oldAccount.isNotEmpty() && oldAccount != account) {
                    prefs.edit().remove("bind").remove("valveDevice").remove("valveUsing").apply()
                    _ui.update {
                        it.copy(
                            bind = null,
                            valveDevice = null,
                            valveUsing = null,
                            valveUsingActive = false,
                            valveChecking = false,
                            valveResumePrompt = false,
                        )
                    }
                }
                _ui.update {
                    it.copy(
                        token = result.token,
                        loginBusy = false,
                        password = "",
                        captchaCode = "",
                        captchaBitmap = null,
                        loginError = null,
                        hotCent = null,
                        coldCent = null,
                        message = "登录成功",
                    )
                }
                refreshBalances()
                if (_ui.value.valveUsing != null) validateValve(showResult = false)
            } catch (e: Exception) {
                _ui.update {
                    it.copy(
                        loginBusy = false,
                        loginError = if (e is ApiException) e.message else "网络异常，请稍后重试",
                    )
                }
                loadCaptcha()
            }
        }
    }

    fun logout() {
        prefs.edit().remove("token").remove("valveUsing").apply()
        val remembered = if (_ui.value.rememberPassword) {
            PasswordVault.load(getApplication(), KEY_PASSWORD)
        } else {
            ""
        }
        _ui.update {
            it.copy(
                token = null,
                password = remembered,
                rememberPassword = remembered.isNotEmpty(),
                captchaCode = "",
                captchaBitmap = null,
                loginError = null,
                hotCent = null,
                hotError = null,
                coldCent = null,
                coldError = null,
                valveUsing = null,
                valveUsingActive = false,
                valveChecking = false,
                valveResumePrompt = false,
            )
        }
    }

    fun refreshBalances() {
        val state = _ui.value
        val token = state.token ?: return
        if (state.balanceBusy) return
        _ui.update { it.copy(balanceBusy = true, hotError = null, coldError = null) }
        viewModelScope.launch {
            var expired = false
            try {
                val hot = withContext(Dispatchers.IO) { LxtApi.queryBath(token) }
                val cents = parseCents(hot?.opt("money")) ?: 0L
                _ui.update { it.copy(hotCent = cents) }
            } catch (e: Exception) {
                if (e is ApiException && e.code == -44) {
                    expired = true
                } else {
                    _ui.update { it.copy(hotError = friendly(e)) }
                }
            }
            val bind = _ui.value.bind
            if (bind != null && !expired) {
                try {
                    val cold = withContext(Dispatchers.IO) { LxtApi.queryWater(token, bind.machineId) }
                    val cents = parseCents(cold?.opt("canUseMoney")) ?: 0L
                    _ui.update { it.copy(coldCent = cents) }
                } catch (e: Exception) {
                    if (e is ApiException && e.code == -44) {
                        expired = true
                    } else {
                        _ui.update { it.copy(coldError = friendly(e)) }
                    }
                }
            }
            _ui.update { it.copy(balanceBusy = false) }
            lastBalanceAt = System.currentTimeMillis()
            if (expired) sessionExpired()
        }
    }

    fun startBind(forValve: Boolean = false) {
        _ui.update {
            it.copy(
                bindForValve = forValve,
                bindLevel = 0,
                bindPath = emptyList(),
                bindRoom = null,
                bindMachine = null,
                bindMachineChecked = false,
                bindError = null,
                bindItems = emptyList(),
            )
        }
        loadBindLevel(0)
    }

    private fun loadBindLevel(level: Int) {
        val token = _ui.value.token ?: return
        _ui.update {
            it.copy(
                bindLevel = level,
                bindLoading = true,
                bindError = null,
                bindItems = emptyList(),
                bindRoom = null,
                bindMachine = null,
                bindMachineChecked = false,
            )
        }
        viewModelScope.launch {
            try {
                val forValve = _ui.value.bindForValve
                val items = withContext(Dispatchers.IO) {
                    when (level) {
                        0 -> LxtApi.lowerAreas(token, LxtApi.SCHOOL_ID)
                        1 -> LxtApi.lowerAreas(token, _ui.value.bindPath.getOrNull(0)?.id ?: "")
                        2 -> LxtApi.lowerAreas(token, _ui.value.bindPath.getOrNull(1)?.id ?: "")
                        else -> LxtApi.rooms(token, _ui.value.bindPath.getOrNull(2)?.id ?: "")
                    }
                }
                val visible = if (level == 0) {
                    items.filter { matchesBindType(it.name, forValve) }
                } else {
                    items
                }
                val sorted = if (level == 0) visible else visible.sortedWith(NaturalAreaOrder)
                _ui.update { it.copy(bindItems = sorted, bindLoading = false) }
            } catch (e: Exception) {
                _ui.update { it.copy(bindLoading = false, bindError = friendly(e)) }
                if (e is ApiException && e.code == -44) sessionExpired()
            }
        }
    }

    fun chooseBindItem(node: AreaNode) {
        val state = _ui.value
        if (state.bindLoading) return
        if (state.bindLevel < 3) {
            val newPath = state.bindPath.take(state.bindLevel) + node
            _ui.update { it.copy(bindPath = newPath) }
            loadBindLevel(state.bindLevel + 1)
            return
        }
        _ui.update {
            it.copy(
                bindRoom = node,
                bindMachine = null,
                bindMachineChecked = false,
                bindError = null,
            )
        }
        val token = state.token ?: return
        viewModelScope.launch {
            try {
                val typeId = if (state.bindForValve) 1 else 18
                val machines = withContext(Dispatchers.IO) {
                    LxtApi.machineByLocation(token, node.id, node.siteFlag, typeId)
                }
                _ui.update { current ->
                    if (current.bindRoom?.id == node.id) {
                        current.copy(bindMachine = machines.firstOrNull(), bindMachineChecked = true)
                    } else {
                        current
                    }
                }
            } catch (e: Exception) {
                if (e is ApiException && e.code == -44) {
                    sessionExpired()
                } else {
                    _ui.update { current ->
                        if (current.bindRoom?.id == node.id) {
                            current.copy(bindMachineChecked = true, bindError = friendly(e))
                        } else {
                            current
                        }
                    }
                }
            }
        }
    }

    fun goBindLevel(level: Int) {
        val state = _ui.value
        if (state.bindLoading) return
        _ui.update { it.copy(bindPath = it.bindPath.take(level)) }
        loadBindLevel(level)
    }

    fun confirmBind(onDone: () -> Unit) {
        val state = _ui.value
        val path = state.bindPath
        val room = state.bindRoom ?: return
        val machine = state.bindMachine ?: return
        if (path.size < 3) return
        if (state.bindForValve) {
            if (machine.deviceVer.isBlank()) {
                _ui.update { it.copy(message = "设备信息不完整，请重新选择") }
                return
            }
            if (LxtApi.channelWay(machine.deviceVer) == 0) {
                _ui.update { it.copy(message = "该设备不支持远程开阀，请现场操作") }
                return
            }
            val device = ValveDevice(
                machineId = machine.machineId,
                deviceMac = machine.deviceMac,
                deviceVer = machine.deviceVer,
                siteId = room.id,
                siteFlag = room.siteFlag,
                roomName = room.name,
                buildingName = path[1].name,
                floorName = path[2].name,
                campusName = path[0].name,
            )
            prefs.edit().putString("valveDevice", device.toJson()).apply()
            _ui.update { it.copy(valveDevice = device, message = "热水设备绑定成功") }
            onDone()
            return
        }
        val info = BindInfo(
            campusName = path[0].name,
            buildingName = path[1].name,
            floorName = path[2].name,
            roomName = room.name,
            siteId = room.id,
            siteFlag = room.siteFlag,
            machineId = machine.machineId,
            deviceMac = machine.deviceMac,
        )
        prefs.edit().putString("bind", info.toJson()).apply()
        _ui.update { it.copy(bind = info, coldCent = null, coldError = null, message = "绑定成功") }
        refreshBalances()
        onDone()
    }

    fun openValve() {
        val state = _ui.value
        val token = state.token ?: return
        val device = state.valveDevice
        if (device == null) {
            _ui.update { it.copy(message = "请先绑定热水设备") }
            return
        }
        if (device.deviceVer.isBlank()) {
            _ui.update { it.copy(message = "设备信息不完整，请重新绑定") }
            return
        }
        if (LxtApi.channelWay(device.deviceVer) == 0) {
            _ui.update { it.copy(message = "该设备不支持远程开阀，请现场操作") }
            return
        }
        if (state.valveBusy) return
        if (state.valveChecking) {
            _ui.update { it.copy(message = "正在确认热水阀状态，请稍候") }
            return
        }
        _ui.update { it.copy(valveBusy = true) }
        viewModelScope.launch {
            try {
                val openData = withContext(Dispatchers.IO) {
                    LxtApi.valveOpen(token, device.machineId, device.deviceVer)
                }
                val communicationVal = openData?.opt("communicationVal")
                    ?.toString()
                    ?.takeUnless { it == "null" }
                    .orEmpty()
                val using = ValveUsing(
                    machineId = device.machineId,
                    deviceVer = device.deviceVer,
                    communicationVal = communicationVal,
                    siteId = device.siteId,
                    siteFlag = device.siteFlag,
                    roomName = device.buildingName + device.roomName,
                    openTime = System.currentTimeMillis(),
                )
                prefs.edit().putString("valveUsing", using.toJson()).apply()
                _ui.update { it.copy(valveUsing = using, valveUsingActive = false) }
                delay(2000)
                var expired = false
                val status = try {
                    withContext(Dispatchers.IO) {
                        LxtApi.valveStatus(token, device.machineId, communicationVal)
                    }
                } catch (e: ApiException) {
                    if (e.code == -44) expired = true
                    null
                } catch (e: Exception) {
                    null
                }
                var opened = status == 0
                if (!opened && !expired) {
                    delay(2000)
                    opened = try {
                        withContext(Dispatchers.IO) {
                            LxtApi.machineStatusList(token, device.siteId, device.siteFlag)
                        }.any { it.machineId == device.machineId && LxtApi.isMachineRunning(it.curStatus) }
                    } catch (e: ApiException) {
                        if (e.code == -44) expired = true
                        false
                    } catch (e: Exception) {
                        false
                    }
                }
                _ui.update {
                    it.copy(
                        valveBusy = false,
                        valveUsingActive = opened,
                        message = if (opened) "开阀成功，开始计费" else "开阀结果未确认，如已出水请到洗澡机上结束用水",
                    )
                }
                if (expired) sessionExpired("登录已失效，请重新登录后确认热水阀状态")
            } catch (e: Exception) {
                _ui.update { it.copy(valveBusy = false) }
                if (e is ApiException && e.code == -44) {
                    sessionExpired("登录已失效，请重新登录后再开阀", resumeOpen = true)
                } else {
                    _ui.update { it.copy(message = friendly(e)) }
                }
            }
        }
    }

    fun dismissValveResume() = _ui.update { it.copy(valveResumePrompt = false) }

    fun refreshValve() {
        validateValve(showResult = true)
    }

    private fun validateValve(showResult: Boolean) {
        val state = _ui.value
        val token = state.token ?: return
        val using = state.valveUsing ?: return
        if (state.valveChecking) return
        val age = System.currentTimeMillis() - using.openTime
        if (age > VALVE_USING_MAX_AGE) {
            prefs.edit().remove("valveUsing").apply()
            _ui.update {
                it.copy(
                    valveUsing = null,
                    valveUsingActive = false,
                    valveChecking = false,
                    message = if (showResult) "已结束用水" else it.message,
                )
            }
            return
        }
        _ui.update { it.copy(valveChecking = true) }
        viewModelScope.launch {
            var expired = false
            var reachable = false
            var running = false
            if (using.siteId.isNotBlank()) {
                try {
                    val list = withContext(Dispatchers.IO) {
                        LxtApi.machineStatusList(token, using.siteId, using.siteFlag)
                    }
                    reachable = true
                    running = list.any {
                        it.machineId == using.machineId && LxtApi.isMachineRunning(it.curStatus)
                    }
                } catch (e: ApiException) {
                    if (e.code == -44) expired = true
                } catch (e: Exception) {
                }
            }
            if (!reachable && !expired) {
                try {
                    val status = withContext(Dispatchers.IO) {
                        LxtApi.valveStatus(token, using.machineId, using.communicationVal)
                    }
                    if (status != null) {
                        reachable = true
                        running = status == 0
                    }
                } catch (e: ApiException) {
                    if (e.code == -44) expired = true
                } catch (e: Exception) {
                }
            }
            if (expired) {
                sessionExpired()
                return@launch
            }
            if (_ui.value.valveUsing?.openTime != using.openTime) {
                _ui.update { it.copy(valveChecking = false) }
                return@launch
            }
            when {
                running -> _ui.update {
                    it.copy(
                        valveUsingActive = true,
                        valveChecking = false,
                        message = if (showResult) "热水阀仍在使用中" else it.message,
                    )
                }

                reachable -> {
                    if (System.currentTimeMillis() - using.openTime > VALVE_USING_RETRY_AGE) {
                        prefs.edit().remove("valveUsing").apply()
                        _ui.update {
                            it.copy(
                                valveUsing = null,
                                valveUsingActive = false,
                                valveChecking = false,
                                message = if (showResult) "已结束用水" else it.message,
                            )
                        }
                    } else {
                        _ui.update {
                            it.copy(
                                valveUsingActive = false,
                                valveChecking = false,
                                message = if (showResult) "已结束用水" else it.message,
                            )
                        }
                    }
                }

                else -> _ui.update {
                    it.copy(
                        valveChecking = false,
                        message = if (showResult) "暂未获取到阀状态" else it.message,
                    )
                }
            }
        }
    }

    private fun sessionExpired(
        message: String = "登录已失效，请重新登录",
        resumeOpen: Boolean = false,
    ) {
        prefs.edit().remove("token").apply()
        val remembered = if (_ui.value.rememberPassword) {
            PasswordVault.load(getApplication(), KEY_PASSWORD)
        } else {
            ""
        }
        _ui.update {
            it.copy(
                token = null,
                password = remembered,
                rememberPassword = remembered.isNotEmpty(),
                hotCent = null,
                coldCent = null,
                valveUsingActive = false,
                valveChecking = false,
                valveResumePrompt = if (resumeOpen) it.valveDevice != null else it.valveResumePrompt,
                balanceBusy = false,
                valveBusy = false,
                message = message,
            )
        }
        val state = _ui.value
        if (state.captchaBitmap == null && !state.captchaLoading && state.account.trim().length >= 6) {
            loadCaptcha()
        }
    }

    private fun friendly(e: Throwable): String =
        if (e is ApiException) (e.message ?: "请求失败") else "网络异常，请稍后重试"

    private fun parseCents(value: Any?): Long? = when (value) {
        is Number -> Math.round(value.toDouble())
        is String -> value.toDoubleOrNull()?.let { Math.round(it) }
        else -> null
    }
}
