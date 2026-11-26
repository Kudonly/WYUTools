package com.wyutools.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyutools.app.campus.CampusPhase
import com.wyutools.app.campus.CampusUiState
import com.wyutools.app.campus.CampusViewModel
import java.util.Locale
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Campus network page: one big session clock on top, credentials underneath, and a
 * log panel that stays off until the user turns it on.
 */
@Composable
fun CampusScreen(vm: CampusViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val scrollState = rememberScrollState()
    val collapseFraction = rememberWyuCollapseFraction(scrollState)

    WyuPageScaffold(
        progress = { collapseFraction.value },
        maskCollapseHeight = WyuLargeTitleHeight,
        topBar = {
            WyuActionBar(
                title = "校园网",
                collapsedFraction = { collapseFraction.value },
                navigationIcon = {
                    WyuTopBarIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        onClick = onBack,
                    )
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
            Spacer(Modifier.height(padding.calculateTopPadding() + 8.dp))
            Text(
                text = "连接校园 WiFi 后使用，账号为宽带账号",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = 12.dp),
            )
            Spacer(Modifier.height(12.dp))
            SessionCard(ui)
            Spacer(Modifier.height(12.dp))
            CredentialCard(ui, vm)
            Spacer(Modifier.height(12.dp))
            LogCard(ui, vm)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SessionCard(ui: CampusUiState) {
    // The clock is derived from the moment the portal accepted the session, so it
    // survives recomposition without the ViewModel ticking once per second.
    val since = ui.onlineSince
    var now by remember(since) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(since) {
        if (since == null) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            delay(1000L)
        }
    }
    val elapsed = if (since == null) 0L else ((now - since) / 1000L).coerceAtLeast(0L)

    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "连接时长",
                style = MiuixTheme.textStyles.title3,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = phaseLabel(ui.phase),
                style = MiuixTheme.textStyles.footnote1,
                color = when (ui.phase) {
                    CampusPhase.Online -> MiuixTheme.colorScheme.primary
                    CampusPhase.Failed -> MiuixTheme.colorScheme.error
                    else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                },
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = formatDuration(elapsed),
            // Largest, heaviest type on the page: this is the number the user opens
            // the page to read. Tabular figures keep the digits from hopping as the
            // seconds change.
            style = TextStyle(
                fontSize = 46.sp,
                lineHeight = 54.sp,
                fontWeight = FontWeight.W800,
                letterSpacing = (-1).sp,
                fontFeatureSettings = "tnum",
                fontFamily = FontFamily.Default,
                textAlign = TextAlign.Start,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = ui.notice ?: defaultHint(ui),
            style = MiuixTheme.textStyles.footnote1,
            color = if (ui.notice != null && ui.phase == CampusPhase.Failed) {
                MiuixTheme.colorScheme.error
            } else {
                MiuixTheme.colorScheme.onSurfaceVariantSummary
            },
        )
        if (ui.phase == CampusPhase.Online && ui.algoId != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${ui.server.orEmpty().removePrefix("http://")} · ${ui.algoId}",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun CredentialCard(ui: CampusUiState, vm: CampusViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(20.dp),
    ) {
        Text(text = "宽带账号", style = MiuixTheme.textStyles.title3)
        Spacer(Modifier.height(16.dp))

        val accountState = rememberTextFieldState(ui.account)
        val passwordState = rememberTextFieldState(ui.password)
        GlassTextField(
            state = accountState,
            label = "账号",
            enabled = !ui.busy && !ui.online,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next,
            ),
            onValueChange = vm::setAccount,
        )
        Spacer(Modifier.height(12.dp))
        GlassTextField(
            state = passwordState,
            label = "密码",
            secure = true,
            enabled = !ui.busy && !ui.online,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            onValueChange = vm::setPassword,
        )
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "记住密码", style = MiuixTheme.textStyles.body2)
                Text(
                    text = "关闭后只保留账号",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Switch(
                checked = ui.rememberPassword,
                onCheckedChange = vm::setRememberPassword,
            )
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = { if (ui.online) vm.disconnect() else vm.connect() },
            modifier = Modifier.fillMaxWidth(),
            minHeight = 48.dp,
            enabled = !ui.busy && !ui.cipherMissing,
        ) {
            Text(text = actionLabel(ui))
        }
        if (ui.cipherMissing) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "当前设备架构缺少密码库，无法认证。",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun LogCard(ui: CampusUiState, vm: CampusViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "运行日志", style = MiuixTheme.textStyles.title3)
                Text(
                    text = if (ui.logEnabled) "记录认证与心跳过程" else "默认关闭",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Switch(checked = ui.logEnabled, onCheckedChange = vm::setLogEnabled)
        }
        if (ui.logEnabled) {
            Spacer(Modifier.height(12.dp))
            val tail = ui.logs.takeLast(LOG_VISIBLE_LINES)
            if (tail.isEmpty()) {
                Text(
                    text = "还没有日志。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            } else {
                // Only the newest lines are shown: the page itself already scrolls,
                // and a second scrollable box inside it fights the gesture.
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    tail.forEach { entry ->
                        Text(
                            text = entry,
                            style = MiuixTheme.textStyles.footnote2.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
            if (ui.logs.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "清空",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { vm.clearLogs() }
                            .padding(8.dp),
                    )
                }
            }
        }
    }
}

private fun phaseLabel(phase: CampusPhase): String = when (phase) {
    CampusPhase.Idle -> "未连接"
    CampusPhase.Discovering -> "寻找门户"
    CampusPhase.Authenticating -> "认证中"
    CampusPhase.Online -> "已连接"
    CampusPhase.Failed -> "连接失败"
}

private fun actionLabel(ui: CampusUiState): String = when {
    ui.busy && ui.online -> "断开中…"
    ui.busy -> "连接中…"
    ui.online -> "断开连接"
    else -> "连接网络"
}

private fun defaultHint(ui: CampusUiState): String = when (ui.phase) {
    CampusPhase.Idle -> "填写宽带账号后连接。"
    CampusPhase.Discovering -> "正在寻找校园网认证门户…"
    CampusPhase.Authenticating -> "正在获取票据并认证…"
    CampusPhase.Online -> "保持校园 WiFi 连接即可持续在线。"
    CampusPhase.Failed -> "请检查账号密码与 WiFi 后重试。"
}

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, secs)
}

private const val LOG_VISIBLE_LINES = 12
