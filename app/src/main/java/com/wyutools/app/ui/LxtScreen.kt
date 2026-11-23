package com.wyutools.app.ui

import android.widget.Toast
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wyutools.app.lxt.LxtApi
import com.wyutools.app.lxt.LxtUiState
import com.wyutools.app.lxt.LxtViewModel
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
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
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val collapseFraction = rememberWyuCollapseFraction(scrollState)
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showValveDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.onLxtEntered() }
    LaunchedEffect(ui.message) {
        ui.message?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            vm.consumeMessage()
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.onLxtResumed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
                        WyuTopBarAction(
                            icon = Icons.Filled.Refresh,
                            label = "刷新",
                            onClick = vm::refreshBalances,
                            enabled = !ui.balanceBusy,
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
                ColdBalanceCard(ui = ui, onBind = onBind)
                Spacer(Modifier.height(12.dp))
                LxtDeviceCard(ui = ui, onBind = onBindValve)
                Spacer(Modifier.height(12.dp))
                ValveCard(
                    ui = ui,
                    onOpenValve = { showValveDialog = true },
                    onRefreshValve = vm::refreshValve,
                )
                Spacer(Modifier.height(12.dp))
                LxtLogoutCard(onLogout = { showLogoutDialog = true })
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "热水是校外的第三方系统，账号密码与教务不同；" +
                        "余额与开阀都来自学校热水平台，充值请使用支付宝内的校园热水服务。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
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
        TextField(
            value = ui.account,
            onValueChange = vm::setAccount,
            label = "学号 / 手机号",
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        )
        Spacer(Modifier.height(12.dp))
        TextField(
            value = ui.password,
            onValueChange = vm::setPassword,
            label = "密码",
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
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
            TextField(
                value = ui.captchaCode,
                onValueChange = vm::setCaptchaCode,
                label = "验证码",
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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

/**
 * 热水余额：整页的第一落点。
 *
 * 数字用 40sp Bold，单位退成 footnote 并跟数字基线对齐——余额是这一页唯一
 * 要「一眼看到」的东西，标题和说明都只是它的注脚。
 */
@Composable
private fun HotBalanceCard(ui: LxtUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Text(
            text = "热水余额",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(6.dp))
        val hotError = ui.hotError
        val hotCent = ui.hotCent
        when {
            hotError != null -> Text(
                text = hotError,
                color = MiuixTheme.colorScheme.error,
                style = MiuixTheme.textStyles.body2,
            )

            else -> Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = hotCent?.let { formatCents(it) } ?: "—",
                    style = MiuixTheme.textStyles.headline1.copy(fontSize = 40.sp),
                    fontWeight = FontWeight.Bold,
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
    }
}

/**
 * 冷水余额：与热水分开一张卡，因为数据源不同（宿舍冷水表钱包），
 * 没绑定时要能就地绑定，绑好了也要能换宿舍。
 */
@Composable
private fun ColdBalanceCard(ui: LxtUiState, onBind: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "冷水余额",
                style = MiuixTheme.textStyles.title3,
                modifier = Modifier.weight(1f),
            )
            ActionChip(
                text = if (ui.bind == null) "绑定宿舍" else "更换宿舍",
                onClick = onBind,
                icon = Icons.Filled.Edit,
            )
        }
        Spacer(Modifier.height(10.dp))
        val bind = ui.bind
        val coldError = ui.coldError
        val coldCent = ui.coldCent
        if (bind == null) {
            Text(
                text = "尚未绑定宿舍",
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "绑定后可按房间冷水表查询余额",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        } else {
            Text(
                text = "${bind.buildingName} ${bind.roomName}".trim(),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(6.dp))
            when {
                coldError != null -> Text(
                    text = coldError,
                    color = MiuixTheme.colorScheme.error,
                    style = MiuixTheme.textStyles.body2,
                )

                else -> Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = coldCent?.let { formatCents(it) } ?: "—",
                        style = MiuixTheme.textStyles.headline1.copy(fontSize = 32.sp),
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "元",
                        style = MiuixTheme.textStyles.footnote1,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "冷水余额来自宿舍冷水表钱包。",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

/** 浴室设备：一行卡片，点进去选校区→楼栋→楼层→房间。 */
@Composable
private fun LxtDeviceCard(ui: LxtUiState, onBind: () -> Unit) {
    val device = ui.valveDevice
    val room = device?.let { (it.buildingName + it.roomName).ifBlank { it.machineId } }
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        onClick = onBind,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.WaterDrop,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = room ?: "未绑定浴室设备",
                    style = MiuixTheme.textStyles.title2,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (room == null) {
                        "绑定后才能远程开阀"
                    } else {
                        "选一次校区 → 楼栋 → 楼层 → 房间"
                    },
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** 远程开阀：主按钮「开阀用水」+ 次要的「刷新状态」，按截图排成一行。 */
@Composable
private fun ValveCard(
    ui: LxtUiState,
    onOpenValve: () -> Unit,
    onRefreshValve: () -> Unit,
) {
    val device = ui.valveDevice
    val remoteSupported = device != null &&
        device.deviceVer.isNotBlank() &&
        LxtApi.channelWay(device.deviceVer) != 0
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(20.dp),
    ) {
        Text("远程开阀", style = MiuixTheme.textStyles.title3)
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
                    Text("开阀用水")
                }
            }
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = onRefreshValve,
                enabled = !ui.valveChecking,
                minHeight = 48.dp,
                colors = ButtonDefaults.buttonColors(),
            ) {
                Text("刷新状态")
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
        ui.valveUsing?.takeIf { ui.valveUsingActive }?.let { using ->
            Spacer(Modifier.height(14.dp))
            Card(
                colors = CardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.tertiaryContainer,
                    contentColor = MiuixTheme.colorScheme.onTertiaryContainer,
                ),
                insideMargin = PaddingValues(16.dp),
            ) {
                Text(
                    text = "热水阀使用中",
                    style = MiuixTheme.textStyles.title4,
                    color = MiuixTheme.colorScheme.onTertiaryContainer,
                )
                val minutes = ((System.currentTimeMillis() - using.openTime) / 60000L)
                    .coerceAtLeast(0)
                Text(
                    text = "已使用约 $minutes 分钟 · 请在洗澡机上结束用水",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onTertiaryContainer,
                )
                TextButton(text = "刷新状态", onClick = onRefreshValve)
            }
        }
    }
}

/** 退出热水账号：单独一张卡，红字居中偏左，跟截图一致。 */
@Composable
private fun LxtLogoutCard(onLogout: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        onClick = onLogout,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "退出热水账号",
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.error,
            )
        }
    }
}
