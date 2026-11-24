package com.wyutools.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyutools.app.lxt.LxtApi
import com.wyutools.app.lxt.LxtUiState
import com.wyutools.app.lxt.LxtViewModel
import com.wyutools.app.lxt.ValveUsing
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale

private fun formatCents(cents: Long): String =
    String.format(Locale.CHINA, "%.2f", cents / 100.0)

@Composable
fun LxtScreen(
    vm: LxtViewModel,
    onBack: () -> Unit,
    onBind: () -> Unit,
    onBindValve: () -> Unit,
) {
    val ui by vm.ui.collectAsState()
    val scrollState = rememberScrollState()
    val collapseFraction = rememberWyuCollapseFraction(scrollState)
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showValveDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.onLxtEntered() }
    WyuNoticeToast(ui.message) { vm.consumeMessage() }
    OnResumeEffect { vm.onLxtResumed() }

    WyuPageScaffold(
        progress = { collapseFraction.value },
        maskCollapseHeight = WyuLargeTitleHeight,
        topBar = {
            WyuActionBar(
                title = "洗澡",
                collapsedFraction = { collapseFraction.value },
                navigationIcon = {
                    WyuTopBarIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        onClick = onBack,
                    )
                },
                actions = {
                    if (ui.token != null) {
                        WyuTopBarIconButton(
                            icon = Icons.Filled.Refresh,
                            contentDescription = "刷新",
                            onClick = vm::refreshAll,
                            enabled = !ui.balanceBusy && !ui.valveChecking && !ui.valveBusy,
                        )
                        // 退出账号从卡片挪到顶栏，跟刷新并排两颗玻璃按钮。
                        WyuTopBarIconButton(
                            icon = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "退出登录",
                            onClick = { showLogoutDialog = true },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(padding.calculateTopPadding()))
            Spacer(Modifier.height(12.dp))
            if (ui.token == null) {
                LoginCard(ui = ui, vm = vm)
            } else {
                HotBalanceCard(ui = ui)
                Spacer(Modifier.height(12.dp))
                ColdBalanceCard(ui = ui)
                Spacer(Modifier.height(12.dp))
                ValveCard(
                    ui = ui,
                    onOpenValve = { showValveDialog = true },
                )
                Spacer(Modifier.height(12.dp))
                // 宿舍修改不是每天要看的数字，只是偶尔进出的设置，压到页尾。
                DormCard(ui = ui, onEditHot = onBindValve, onEditCold = onBind)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "账号使用乐校园通，充值请到支付宝",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    // 与顶栏大标题对齐：页面 16dp 页边距 + 12dp。
                    modifier = Modifier.padding(start = WyuTextInset - 16.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    MiuixConfirmDialog(
        show = showLogoutDialog,
        title = "退出登录",
        message = "退出后需要重新登录，确定吗？",
        confirmText = "退出",
        destructive = true,
        onDismiss = { showLogoutDialog = false },
        onConfirm = {
            showLogoutDialog = false
            vm.logout()
        },
    )

    val valveTarget = ui.valveDevice?.let { device ->
        (device.buildingName + device.roomName).ifBlank { device.machineId }
    }.orEmpty()
    MiuixConfirmDialog(
        show = showValveDialog,
        title = "开启热水阀",
        message = "将为「$valveTarget」开启热水阀，开阀后开始计费，请在洗澡机上结束用水。",
        confirmText = "开阀",
        onDismiss = { showValveDialog = false },
        onConfirm = {
            showValveDialog = false
            vm.openValve()
        },
    )

    MiuixConfirmDialog(
        show = ui.valveResumePrompt && ui.token != null && ui.valveDevice != null,
        title = "继续开启热水阀",
        message = "登录已恢复。是否为「$valveTarget」开启热水阀？开阀后开始计费，请在洗澡机上结束用水。",
        confirmText = "继续开阀",
        onDismiss = vm::dismissValveResume,
        onConfirm = {
            vm.dismissValveResume()
            vm.openValve()
        },
    )

}

@Composable
private fun LoginCard(ui: LxtUiState, vm: LxtViewModel) {
    var passwordVisible by remember { mutableStateOf(false) }
    val accountState = rememberTextFieldState(ui.account)
    val passwordState = rememberTextFieldState(ui.password)
    val captchaState = rememberTextFieldState(ui.captchaCode)
    // 刷新验证码 / 登录失败会清 VM 侧字符串，同步回输入框。
    LaunchedEffect(ui.account) {
        if (accountState.text.toString() != ui.account) {
            accountState.edit { replace(0, length, ui.account) }
        }
    }
    LaunchedEffect(ui.password) {
        if (passwordState.text.toString() != ui.password) {
            passwordState.edit { replace(0, length, ui.password) }
        }
    }
    LaunchedEffect(ui.captchaCode) {
        if (captchaState.text.toString() != ui.captchaCode) {
            captchaState.edit { replace(0, length, ui.captchaCode) }
        }
    }
    val rememberToggle = hapticClick(enabled = !ui.loginBusy) {
        vm.setRememberPassword(!ui.rememberPassword)
    }
    val reloadCaptcha = hapticClick { vm.loadCaptcha() }
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MiuixTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.WaterDrop,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("登录洗澡", style = MiuixTheme.textStyles.title2)
                Text(
                    "使用乐校通账号登录",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        GlassTextField(
            state = accountState,
            label = "学号 / 手机号",
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            onValueChange = vm::setAccount,
        )
        Spacer(Modifier.height(12.dp))
        GlassTextField(
            state = passwordState,
            label = "密码",
            secure = !passwordVisible,
            modifier = Modifier.fillMaxWidth(),
            onValueChange = vm::setPassword,
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) {
                            Icons.Outlined.VisibilityOff
                        } else {
                            Icons.Outlined.Visibility
                        },
                        contentDescription = "切换密码可见性",
                    )
                }
            },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = !ui.loginBusy,
                ) {
                    rememberToggle()
                }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                state = if (ui.rememberPassword) ToggleableState.On else ToggleableState.Off,
                onClick = null,
            )
            Spacer(Modifier.width(4.dp))
            Text("记住密码", style = MiuixTheme.textStyles.body2)
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassTextField(
                state = captchaState,
                label = "验证码",
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                onValueChange = vm::setCaptchaCode,
            )
            Spacer(Modifier.width(12.dp))
            val captchaBitmap = ui.captchaBitmap
            Box(
                modifier = Modifier
                    .size(width = 128.dp, height = 56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MiuixTheme.colorScheme.surfaceVariant)
                    .border(
                        width = 1.dp,
                        color = MiuixTheme.colorScheme.outline.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(10.dp),
                    )
                    .clickable(onClick = reloadCaptcha),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    ui.captchaLoading -> CircularProgressIndicator(
                        size = 20.dp,
                        strokeWidth = 2.dp,
                    )

                    captchaBitmap != null -> Image(
                        bitmap = captchaBitmap.asImageBitmap(),
                        contentDescription = "验证码",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                        contentScale = ContentScale.Fit,
                    )

                    else -> Text(
                        text = "获取验证码",
                        style = MiuixTheme.textStyles.footnote2,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "看不清？点击图片换一张",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        ui.loginError?.let { error ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = error,
                color = MiuixTheme.colorScheme.error,
                style = MiuixTheme.textStyles.footnote1,
            )
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = vm::login,
            enabled = !ui.loginBusy,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            if (ui.loginBusy) {
                CircularProgressIndicator(
                    size = 20.dp,
                    strokeWidth = 2.dp,
                    colors = ProgressIndicatorDefaults.progressIndicatorColors(
                        foregroundColor = MiuixTheme.colorScheme.onPrimary,
                    ),
                )
            } else {
                Text("登录")
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "密码仅用于向热水平台换取登录凭证；勾选记住密码后使用系统密钥库加密保存在本机。",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

/** 宿舍号小标签：标题旁边一枚浅底胶囊，没绑定时显示「未绑定宿舍」。 */
@Composable
private fun DormTag(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 余额卡的标题行：小标题 + 该路热水/冷水对应的宿舍号。 */
@Composable
private fun BalanceTitle(title: String, room: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.width(8.dp))
        DormTag(text = room ?: "未绑定宿舍")
    }
}

/**
 * 余额数字：40sp + 最重字重，「元」退成 footnote 并跟数字基线对齐。
 * 热水、冷水共用这一套排版，两张卡除了标题与来源说明之外完全一致。
 */
@Composable
private fun BalanceAmount(cents: Long?) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = cents?.let { formatCents(it) } ?: "—",
            style = MiuixTheme.textStyles.headline1.copy(fontSize = 40.sp),
            fontWeight = WyuEmphasisWeight,
            color = MiuixTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "元",
            style = MiuixTheme.textStyles.footnote1,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}

/**
 * 热水余额：整页的第一落点。
 *
 * 余额是这一页唯一要「一眼看到」的东西，标题、宿舍号和说明都只是它的注脚。
 */
@Composable
private fun HotBalanceCard(ui: LxtUiState) {
    val room = ui.valveDevice
        ?.let { (it.buildingName + it.roomName).trim().ifBlank { null } }
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
    ) {
        BalanceTitle(title = "热水余额", room = room)
        Spacer(Modifier.height(6.dp))
        val hotError = ui.hotError
        if (hotError != null) {
            Text(
                text = hotError,
                color = MiuixTheme.colorScheme.error,
                style = MiuixTheme.textStyles.body2,
            )
        } else {
            BalanceAmount(ui.hotCent)
        }
    }
}

/**
 * 冷水余额：数据源是宿舍冷水表钱包，排版与热水卡完全对齐。
 *
 * 绑定入口统一在下面的宿舍卡片里，这里只负责显示。
 */
@Composable
private fun ColdBalanceCard(ui: LxtUiState) {
    val bind = ui.bind
    val room = bind?.let { "${it.buildingName}${it.roomName}".trim().ifBlank { null } }
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
    ) {
        BalanceTitle(title = "冷水余额", room = room)
        Spacer(Modifier.height(6.dp))
        val coldError = ui.coldError
        when {
            bind == null -> BalanceAmount(null)
            coldError != null -> Text(
                text = coldError,
                color = MiuixTheme.colorScheme.error,
                style = MiuixTheme.textStyles.body2,
            )

            else -> BalanceAmount(ui.coldCent)
        }
    }
}

/**
 * 宿舍：卡片本身只留一个「修改宿舍」入口。
 *
 * 热水（浴室设备）与冷水（冷水表）是两个账号，点进来才选改哪一个——卡片上
 * 不铺两行，页面读起来才是一个整体的余额视图，而不是一堆设置项。
 */
@Composable
private fun DormCard(
    ui: LxtUiState,
    onEditHot: () -> Unit,
    onEditCold: () -> Unit,
) {
    val hotRoom = ui.valveDevice
        ?.let { (it.buildingName + it.roomName).trim().ifBlank { null } }
    val coldRoom = ui.bind
        ?.let { "${it.buildingName}${it.roomName}".trim().ifBlank { null } }
    var pickerOpen by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
    ) {
        val click = hapticClick { pickerOpen = true }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = click)
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "修改宿舍",
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
    WyuOverlayDialog(
        show = pickerOpen,
        title = "修改宿舍",
        onDismissRequest = { pickerOpen = false },
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = "热水与冷水是两个账号，点一项修改。",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(6.dp))
            DormRow(
                label = "热水",
                hint = "浴室设备",
                room = hotRoom,
                onClick = {
                    pickerOpen = false
                    onEditHot()
                },
            )
            DormRow(
                label = "冷水",
                hint = "冷水表",
                room = coldRoom,
                onClick = {
                    pickerOpen = false
                    onEditCold()
                },
            )
            Spacer(Modifier.height(16.dp))
            WyuDialogButton(text = "取消", onClick = { pickerOpen = false })
        }
    }
}

/** 宿舍卡里的一行：标签 + 当前房间 + 「绑定 / 修改」。 */
@Composable
private fun DormRow(
    label: String,
    hint: String,
    room: String?,
    onClick: () -> Unit,
) {
    val click = hapticClick(action = onClick)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = click)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$label · $hint",
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = room ?: "未绑定",
                style = MiuixTheme.textStyles.footnote1,
                color = if (room == null) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                },
            )
        }
        Text(
            text = if (room == null) "绑定" else "修改",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.primary,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 远程开阀：主按钮「开阀」。 */
@Composable
private fun ValveCard(
    ui: LxtUiState,
    onOpenValve: () -> Unit,
) {
    val device = ui.valveDevice
    val remoteSupported = device != null &&
        device.deviceVer.isNotBlank() &&
        LxtApi.channelWay(device.deviceVer) != 0
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("开热水", style = MiuixTheme.textStyles.title3)
            ui.valveUsing?.takeIf { ui.valveUsingActive }?.let { using ->
                Spacer(Modifier.width(10.dp))
                ValveUsingCapsule(using = using)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = when {
                device == null -> "先绑定浴室设备，才能远程开阀。"
                !remoteSupported -> "该设备不支持远程开阀，请现场操作。"
                else -> "开阀后开始计费，请在洗澡机上结束用水。"
            },
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = onOpenValve,
                enabled = remoteSupported && !ui.valveBusy && !ui.valveChecking,
                modifier = Modifier.weight(1f),
                minHeight = 48.dp,
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                if (ui.valveBusy) {
                    CircularProgressIndicator(
                        size = 18.dp,
                        strokeWidth = 2.dp,
                        colors = ProgressIndicatorDefaults.progressIndicatorColors(
                            foregroundColor = MiuixTheme.colorScheme.onPrimary,
                        ),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("正在开阀…")
                } else {
                    Icon(
                        Icons.Outlined.WaterDrop,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("开阀")
                }
            }
        }
        if (ui.valveChecking) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(size = 14.dp, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "正在确认热水阀状态…",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

/** 「使用中」玻璃胶囊：盖在「开热水」标题旁边，只显示状态与已用时长；
 *  刷新走顶栏刷新按钮。 */
@Composable
private fun ValveUsingCapsule(using: ValveUsing) {
    val minutes = ((System.currentTimeMillis() - using.openTime) / 60000L)
        .coerceAtLeast(0)
    Card(
        cornerRadius = 12.dp,
        insideMargin = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
        compactGlass = true,
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
            contentColor = MiuixTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MiuixTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "使用中 · 已约 $minutes 分钟",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onTertiaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
