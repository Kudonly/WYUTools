package com.wyutools.app.ui

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
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
import androidx.compose.ui.unit.dp
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
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
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
    var showValveCodeDialog by remember { mutableStateOf(false) }
    var valveCode by remember { mutableStateOf("") }

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
                        WyuTopBarAction(
                            icon = Icons.AutoMirrored.Filled.ExitToApp,
                            label = "退出",
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
            if (ui.balanceBusy) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            if (ui.token == null) {
                LoginCard(ui = ui, vm = vm)
            } else {
                HotWaterCard(
                    ui = ui,
                    onOpenValve = { showValveDialog = true },
                    onRefreshValve = vm::refreshValve,
                    onBindValve = onBindValve,
                    onEnterCode = { showValveCodeDialog = true },
                )
                Spacer(Modifier.height(16.dp))
                ColdWaterCard(ui = ui, onBind = onBind)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "数据来自学校热水平台，充值请使用支付宝内的校园热水服务。",
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

    WyuOverlayDialog(
        show = showValveCodeDialog,
        title = "设备码绑定",
        summary = "粘贴洗澡机二维码内容，或输入 12 位设备码。",
        onDismissRequest = {
            if (!ui.valveCodeBusy) {
                showValveCodeDialog = false
                vm.clearValveCodeError()
            }
        },
    ) {
        Spacer(Modifier.height(12.dp))
        TextField(
            value = valveCode,
            onValueChange = {
                valveCode = it
                vm.clearValveCodeError()
            },
            label = "设备二维码 / 设备码",
            singleLine = true,
            enabled = !ui.valveCodeBusy,
            modifier = Modifier.fillMaxWidth(),
        )
        ui.valveCodeError?.let { error ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = error,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(16.dp))
        WyuDialogButton(
            text = "绑定",
            loading = ui.valveCodeBusy,
            loadingText = "绑定中…",
            enabled = valveCode.isNotBlank(),
            onClick = {
                vm.bindValveByCode(valveCode) {
                    showValveCodeDialog = false
                    valveCode = ""
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        WyuDialogCancelButton(
            onClick = {
                showValveCodeDialog = false
                vm.clearValveCodeError()
            },
            enabled = !ui.valveCodeBusy,
        )
    }
}

@Composable
private fun LoginCard(ui: LxtUiState, vm: LxtViewModel) {
    var passwordVisible by remember { mutableStateOf(false) }
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
                .clickable(enabled = !ui.loginBusy) {
                    vm.setRememberPassword(!ui.rememberPassword)
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
                    .clickable(onClick = vm::loadCaptcha),
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

@Composable
private fun HotWaterCard(
    ui: LxtUiState,
    onOpenValve: () -> Unit,
    onRefreshValve: () -> Unit,
    onBindValve: () -> Unit,
    onEnterCode: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(20.dp),
    ) {
        Text("热水余额", style = MiuixTheme.textStyles.title3)
        Spacer(Modifier.height(8.dp))
        val hotError = ui.hotError
        val hotCent = ui.hotCent
        when {
            hotError != null -> Text(
                text = hotError,
                color = MiuixTheme.colorScheme.error,
                style = MiuixTheme.textStyles.body2,
            )

            hotCent != null -> Text(
                text = "¥${formatCents(hotCent)}",
                style = MiuixTheme.textStyles.headline1,
                fontWeight = FontWeight.SemiBold,
            )

            else -> Text(
                text = "—",
                style = MiuixTheme.textStyles.headline1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Spacer(Modifier.height(16.dp))
        val valveDevice = ui.valveDevice
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("热水设备", style = MiuixTheme.textStyles.title4)
            Spacer(Modifier.weight(1f))
            ActionChip(
                text = if (valveDevice == null) "绑定" else "更换",
                onClick = onBindValve,
                icon = Icons.Filled.Edit,
            )
            Spacer(Modifier.width(8.dp))
            ActionChip(
                text = "设备码",
                onClick = onEnterCode,
                icon = Icons.Filled.QrCode2,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = valveDevice?.let { device ->
                (device.buildingName + device.roomName).ifBlank { device.roomName }
            } ?: "未绑定，开阀前需绑定房间热水机",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(12.dp))
        val remoteSupported = valveDevice != null &&
            valveDevice.deviceVer.isNotBlank() &&
            LxtApi.channelWay(valveDevice.deviceVer) != 0
        Button(
            onClick = onOpenValve,
            enabled = remoteSupported && !ui.valveBusy && !ui.valveChecking,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            if (ui.valveBusy) {
                CircularProgressIndicator(
                    size = 20.dp,
                    strokeWidth = 2.dp,
                    colors = ProgressIndicatorDefaults.progressIndicatorColors(
                        foregroundColor = MiuixTheme.colorScheme.onPrimary,
                    ),
                )
                Spacer(Modifier.width(10.dp))
                Text("正在开阀…")
            } else {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("开启热水阀")
            }
        }
        if (valveDevice == null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "绑定热水设备后即可开阀",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        } else if (!remoteSupported) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "该设备不支持远程开阀，请现场操作",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.error,
            )
        }
        if (ui.valveChecking) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    size = 14.dp,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "正在确认热水阀状态…",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        ui.valveUsing?.takeIf { ui.valveUsingActive }?.let { using ->
            Spacer(Modifier.height(16.dp))
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
                val minutes = ((System.currentTimeMillis() - using.openTime) / 60000L).coerceAtLeast(0)
                Text(
                    text = "已使用约 $minutes 分钟 · 请在洗澡机上结束用水",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onTertiaryContainer,
                )
                TextButton(
                    text = "刷新状态",
                    onClick = onRefreshValve,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "热水余额来自学校热水钱包；开阀后开始计费，请在洗澡机上结束订单。",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun ColdWaterCard(ui: LxtUiState, onBind: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("冷水余额", style = MiuixTheme.textStyles.title3)
            Spacer(Modifier.weight(1f))
            ActionChip(
                text = if (ui.bind == null) "绑定宿舍" else "更换宿舍",
                onClick = onBind,
                icon = Icons.Filled.Edit,
            )
        }
        val bind = ui.bind
        if (bind != null) {
            Text(
                text = "${bind.buildingName} ${bind.roomName}",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(8.dp))
            val coldError = ui.coldError
            val coldCent = ui.coldCent
            when {
                coldError != null -> Text(
                    text = coldError,
                    color = MiuixTheme.colorScheme.error,
                    style = MiuixTheme.textStyles.body2,
                )

                coldCent != null -> Text(
                    text = "¥${formatCents(coldCent)}",
                    style = MiuixTheme.textStyles.headline1,
                    fontWeight = FontWeight.SemiBold,
                )

                else -> Text(
                    text = "—",
                    style = MiuixTheme.textStyles.headline1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        } else {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "尚未绑定宿舍",
                style = MiuixTheme.textStyles.body2,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "绑定后可按房间冷水表查询余额",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "冷水余额来自宿舍冷水表钱包。",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}
