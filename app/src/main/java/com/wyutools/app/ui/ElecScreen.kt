package com.wyutools.app.ui

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wyutools.app.elec.ELEC_PRICE_PER_KWH
import com.wyutools.app.elec.ElecInfo
import com.wyutools.app.elec.ElecPayChannel
import com.wyutools.app.elec.ElecPayStatus
import com.wyutools.app.elec.ElecPeriod
import com.wyutools.app.elec.ElecRechargeOrder
import com.wyutools.app.elec.ElecRechargeRecord
import com.wyutools.app.elec.ElecSnapshot
import com.wyutools.app.elec.ElecStats
import com.wyutools.app.elec.ElecUiState
import com.wyutools.app.elec.ElecViewModel
import com.wyutools.app.elec.PaymentLauncher
import com.wyutools.app.elec.QrCodes
import com.wyutools.app.elec.normalizeRechargeAmount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val RECORD_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault())

private const val ElecQueryColorMillis = 500
private const val ElecQueryLabelMillis = 280
private const val ElecValueHoldMillis = 550L
private const val ElecValueFadeMillis = 420
private const val ElecValueBlurDp = 8f

private fun formatKwh(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString()
    else String.format(Locale.CHINA, "%.2f", value)

private fun formatYuan(value: Double): String = String.format(Locale.CHINA, "¥%.2f", value)

private fun formatDays(days: Double): String = when {
    days >= 100 -> "99+ 天"
    days >= 10 -> "${days.toInt()} 天"
    else -> String.format(Locale.CHINA, "%.1f 天", days)
}

private fun formatRecordTime(timestamp: Long): String =
    RECORD_TIME_FORMATTER.format(Instant.ofEpochMilli(timestamp))

private fun formatAmountInput(raw: String): String {
    val value = raw.toDoubleOrNull() ?: return "¥$raw"
    return String.format(Locale.CHINA, "¥%.2f", value)
}

private fun formatCountdown(seconds: Long): String =
    String.format(Locale.CHINA, "%02d:%02d", seconds / 60, seconds % 60)

private fun dormLabel(ui: ElecUiState): String {
    val info = ui.result
    val named = listOfNotNull(info?.buildingName, info?.roomName).joinToString(" · ")
    return named.ifBlank {
        val building = ui.building.trim()
        val room = ui.room.trim()
        when {
            building.isNotEmpty() && room.isNotEmpty() -> "$building 栋 $room 室"
            building.isNotEmpty() -> "$building 栋"
            room.isNotEmpty() -> "$room 室"
            else -> "宿舍电费"
        }
    }
}

@Composable
fun ElecScreen(vm: ElecViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val collapseFraction = rememberWyuCollapseFraction(scrollState)
    // The cards render at once so the growing window never shows a blank page;
    // only the data refresh waits for the morph, since its recomposition
    // mid-flight is what made the animation stutter.
    val morphSettled = WyuMorphPageState.morphSettled

    LaunchedEffect(morphSettled) {
        if (morphSettled) vm.onEntered()
    }

    WyuPageScaffold(
        progress = { collapseFraction.value },
        maskCollapseHeight = WyuLargeTitleHeight,
        topBar = {
            WyuActionBar(
                title = "电费",
                collapsedFraction = { collapseFraction.value },
                navigationIcon = {
                    WyuTopBarIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        onClick = onBack,
                    )
                },
                actions = {
                    if (ui.result != null) {
                        WyuTopBarAction(
                            icon = Icons.Filled.Refresh,
                            label = "刷新",
                            onClick = vm::query,
                            enabled = !ui.loading,
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
            Spacer(Modifier.height(padding.calculateTopPadding() + 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "仅支持校园网 / 学校内网访问",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Spacer(Modifier.height(16.dp))
            QueryCard(ui = ui, vm = vm)
            Spacer(Modifier.height(16.dp))
            // The cards are laid out before any data exists, so the page is
            // whole from the first frame; the query result simply fills in.
            ResultCard(
                info = ui.result,
                stats = ui.stats,
                dorm = dormLabel(ui),
            )
            Spacer(Modifier.height(16.dp))
            RechargeEntryCard(
                onRecharge = vm::openRecharge,
                enabled = ui.result != null,
            )
            Spacer(Modifier.height(16.dp))
            RechargeCard(
                ui = ui,
                onSelectPeriod = vm::setPeriod,
                onRetry = vm::retryRecharge,
            )
            Spacer(Modifier.height(16.dp))
            HistoryCard(history = ui.history, stats = ui.stats)
            Spacer(Modifier.height(24.dp))
            Text(
                text = "数据来自五邑大学电费充值系统",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "充值二维码由学校电费系统生成，支付在微信 / 支付宝完成",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    if (ui.rechargeOpen) {
        RechargeDialog(
            ui = ui,
            vm = vm,
            onDismiss = vm::dismissRecharge,
            onSaveQr = { bitmap, onResult ->
                scope.launch {
                    val outcome = withContext(Dispatchers.IO) {
                        runCatching { QrCodes.saveToGallery(context, bitmap) }
                            .fold(
                                onSuccess = { true to it },
                                onFailure = { false to "保存失败：${it.message ?: "请重试"}" },
                            )
                    }
                    onResult(outcome.first, outcome.second)
                }
            },
        )
    }
}

@Composable
private fun QueryCard(ui: ElecUiState, vm: ElecViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(20.dp),
    ) {
        Text(
            text = "输入宿舍信息",
            style = MiuixTheme.textStyles.title3,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassTextField(
                value = ui.building,
                onValueChange = vm::setBuilding,
                label = "楼栋",
                enabled = !ui.loading,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.weight(1f),
            )
            GlassTextField(
                value = ui.room,
                onValueChange = vm::setRoom,
                label = "房间号",
                enabled = !ui.loading,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { vm.query() }),
                modifier = Modifier.weight(1.4f),
            )
        }
        val error = ui.error
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = error,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(16.dp))
        val idleQueryColor = MiuixTheme.colorScheme.primary
        val busyQueryColor = lerp(idleQueryColor, Color.Black, 0.16f)
        val queryColor by animateColorAsState(
            targetValue = if (ui.loading) busyQueryColor else idleQueryColor,
            animationSpec = tween(ElecQueryColorMillis),
            label = "elecQueryColor",
        )
        Button(
            onClick = vm::query,
            modifier = Modifier.fillMaxWidth(),
            minHeight = 48.dp,
            colors = ButtonDefaults.buttonColorsPrimary(color = queryColor),
        ) {
            Crossfade(
                targetState = ui.loading,
                animationSpec = tween(ElecQueryLabelMillis),
                label = "elecQueryLabel",
            ) { loading ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (loading) {
                        CircularProgressIndicator(
                            size = 18.dp,
                            strokeWidth = 2.dp,
                            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                                foregroundColor = MiuixTheme.colorScheme.onPrimary,
                            ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("查询中…")
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Bolt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("查询电费")
                    }
                }
            }
        }
    }
}

@Composable
private fun RechargeEntryCard(
    onRecharge: () -> Unit,
    enabled: Boolean = true,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.QrCode2,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MiuixTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "电费充值",
                style = MiuixTheme.textStyles.title3,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "微信 / 支付宝",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "输入充值金额，生成学校电费系统支付二维码，扫码完成支付。",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onRecharge,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            minHeight = 48.dp,
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            Icon(
                imageVector = Icons.Filled.QrCode2,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("充值电费")
        }
    }
}

@Composable
private fun ResultCard(
    info: ElecInfo?,
    stats: ElecStats,
    dorm: String,
) {
    val low = info != null && info.remainingKwh < 20.0
    val container = if (low) {
        MiuixTheme.colorScheme.errorContainer
    } else {
        MiuixTheme.colorScheme.primaryContainer
    }
    val onContainer = if (low) {
        MiuixTheme.colorScheme.onErrorContainer
    } else {
        MiuixTheme.colorScheme.onPrimaryContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(20.dp),
        colors = CardDefaults.defaultColors(
            color = container,
            contentColor = onContainer,
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(onContainer.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Bolt,
                    contentDescription = null,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "剩余电量",
                    style = MiuixTheme.textStyles.footnote1,
                )
                if (dorm.isNotBlank()) {
                    BlurredValue(value = dorm) { text ->
                        Text(
                            text = text,
                            style = MiuixTheme.textStyles.footnote2,
                            color = onContainer.copy(alpha = 0.75f),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        BlurredValue(value = info?.let { formatKwh(it.remainingKwh) } ?: "—") { kwh ->
            Row {
                Text(
                    text = kwh,
                    style = MiuixTheme.textStyles.headline2,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.alignByBaseline(),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "度 (kWh)",
                    style = MiuixTheme.textStyles.body2,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
        if (low) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "电量偏低，请及时充值",
                    style = MiuixTheme.textStyles.footnote1,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = onContainer.copy(alpha = 0.15f))
        Spacer(Modifier.height(8.dp))
        val daily = stats.dailyUsageKwh
        val daysLeft = stats.daysLeft
        InfoRow("抄表时间", info?.updatedAt ?: "—", animateValue = true)
        InfoRow(
            "折合电费",
            info?.let { "≈ ${formatYuan(it.remainingKwh * ELEC_PRICE_PER_KWH)}" } ?: "—",
            animateValue = true,
        )
        InfoRow("近期日均", daily?.let { "${formatKwh(it)} 度" } ?: "—", animateValue = true)
        InfoRow("预计可用", daysLeft?.let { formatDays(it) } ?: "—", animateValue = true)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "折合电费按 0.63 元/度估算；日均与可用天数来自本机查询记录",
            style = MiuixTheme.textStyles.footnote2,
            color = onContainer.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun RechargeCard(
    ui: ElecUiState,
    onSelectPeriod: (ElecPeriod) -> Unit,
    onRetry: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.History,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MiuixTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "充值记录",
                style = MiuixTheme.textStyles.title3,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "学校系统",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "从学校电费系统读取的历史充值，可对照余额判断补电节奏",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ElecPeriod.values().forEach { period ->
                ActionChip(
                    text = period.label,
                    onClick = { onSelectPeriod(period) },
                    enabled = ui.result != null &&
                        (!ui.rechargeLoading || ui.period == period),
                    primary = ui.period == period,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        val error = ui.rechargeError
        when {
            ui.rechargeLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    size = 18.dp,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "正在读取充值记录…",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }

            error != null -> Column {
                Text(
                    text = error,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.error,
                )
                TextButton(text = "重试", onClick = onRetry)
            }

            else -> {
                val page = ui.recharge
                if (ui.result == null) {
                    Text(
                        text = "查询电费后自动读取充值记录",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                } else if (page == null) {
                    Text(
                        text = "暂无充值记录",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                } else if (page.records.isEmpty()) {
                    Text(
                        text = "${ui.period.label}内没有充值记录",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatCell(
                            label = "充值总额",
                            value = formatYuan(page.totalAmountYuan),
                            modifier = Modifier.weight(1f),
                        )
                        StatCell(
                            label = "充值笔数",
                            value = "${page.total} 笔",
                            modifier = Modifier.weight(1f),
                        )
                        StatCell(
                            label = "平均每笔",
                            value = formatYuan(page.averageAmountYuan),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (page.records.count { it.paidAt != null } >= 2) {
                        Spacer(Modifier.height(16.dp))
                        RechargeMonthlyBars(records = page.records)
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(4.dp))
                    val expanded = remember(ui.period, page.total) { mutableStateOf(false) }
                    val visible = if (expanded.value) page.records else page.records.take(5)
                    visible.forEach { RechargeRow(it) }
                    if (page.records.size > 5) {
                        TextButton(
                            text = if (expanded.value) "收起" else "查看全部 ${page.records.size} 条",
                            onClick = { expanded.value = !expanded.value },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "共 ${page.total} 笔，当前展示 ${page.records.size} 笔；仅显示已成功到账的充值",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}

@Composable
private fun RechargeDialog(
    ui: ElecUiState,
    vm: ElecViewModel,
    onDismiss: () -> Unit,
    onSaveQr: (Bitmap, (Boolean, String) -> Unit) -> Unit,
) {
    val order = ui.order
    val qrBitmap = ui.qrBitmap
    var visible by remember { mutableStateOf(true) }
    val requestClose = { visible = false }
    WyuOverlayDialog(
        show = visible,
        dismissOnOutsideTap = false,
        onDismissRequest = requestClose,
        onDismissFinished = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            when {
                ui.payStatus == ElecPayStatus.SUCCESS && order != null -> {
                    RechargeSuccessContent(ui = ui, order = order, onDismiss = requestClose)
                }

                order != null && qrBitmap != null -> {
                    QrRechargeContent(
                        ui = ui,
                        order = order,
                        bitmap = qrBitmap,
                        vm = vm,
                        onDismiss = requestClose,
                        onSaveQr = onSaveQr,
                    )
                }

                else -> {
                    RechargeAmountContent(ui = ui, vm = vm, onDismiss = requestClose)
                }
            }
        }
    }
}

@Composable
private fun RechargeDialogHeader(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.title2,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Filled.Close, contentDescription = "关闭")
        }
    }
}

@Composable
private fun RechargeAmountContent(
    ui: ElecUiState,
    vm: ElecViewModel,
    onDismiss: () -> Unit,
) {
    var amountField by remember { mutableStateOf(TextFieldValue(ui.rechargeAmount)) }
    LaunchedEffect(ui.rechargeAmount) {
        if (amountField.text != ui.rechargeAmount) {
            amountField = TextFieldValue(
                text = ui.rechargeAmount,
                selection = TextRange(ui.rechargeAmount.length),
            )
        }
    }
    RechargeDialogHeader(
        title = "充值电费",
        subtitle = dormLabel(ui),
        onDismiss = onDismiss,
    )
    Spacer(Modifier.height(16.dp))
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "当前剩余电量",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = ui.result?.let { "${formatKwh(it.remainingKwh)} 度" } ?: "—",
                    style = MiuixTheme.textStyles.title3,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = "学校电费系统",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
    Spacer(Modifier.height(20.dp))
    Text("选择金额", style = MiuixTheme.textStyles.title4)
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(10, 20, 30, 50).forEach { amount ->
            val label = amount.toString()
            ActionChip(
                text = "¥$amount",
                onClick = { vm.setRechargeAmount(label) },
                primary = ui.rechargeAmount == label,
                modifier = Modifier.weight(1f),
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    GlassTextField(
        value = amountField,
        onValueChange = { field ->
            val normalized = normalizeRechargeAmount(field.text)
            if (normalized != null) {
                amountField = if (normalized == field.text) {
                    field
                } else {
                    TextFieldValue(normalized, selection = TextRange(normalized.length))
                }
                vm.setRechargeAmount(normalized)
            }
        },
        label = "充值金额（元）",
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(
            onDone = { if (ui.rechargeAmount.isNotBlank()) vm.createRechargeOrder() },
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(6.dp))
    Text(
        text = "单次充值 1 - 1000 元，支持两位小数",
        style = MiuixTheme.textStyles.footnote2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
    Spacer(Modifier.height(12.dp))
    Text("支付方式", style = MiuixTheme.textStyles.title4)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ElecPayChannel.values().forEach { channel ->
            ActionChip(
                text = channel.label,
                onClick = { vm.setRechargeChannel(channel) },
                primary = ui.rechargeChannel == channel,
            )
        }
    }
    val error = ui.orderError
    if (error != null) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = error,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.error,
        )
    }
    Spacer(Modifier.height(20.dp))
    Button(
        onClick = vm::createRechargeOrder,
        enabled = !ui.orderLoading && ui.rechargeAmount.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
        minHeight = 50.dp,
        colors = ButtonDefaults.buttonColorsPrimary(),
    ) {
        if (ui.orderLoading) {
            CircularProgressIndicator(
                size = 20.dp,
                strokeWidth = 2.dp,
                colors = ProgressIndicatorDefaults.progressIndicatorColors(
                    foregroundColor = MiuixTheme.colorScheme.onPrimary,
                ),
            )
            Spacer(Modifier.width(8.dp))
            Text("正在生成二维码…")
        } else {
            Icon(
                imageVector = Icons.Filled.QrCode2,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("生成充值二维码")
        }
    }
    Spacer(Modifier.height(10.dp))
    Text(
        text = "生成二维码后支付宝会自动尝试打开；微信支付码需保存后在扫一扫的相册中选择。未支付不会扣款，也可用其他设备扫码。",
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
    TextButton(
        text = "取消",
        onClick = onDismiss,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun QrRechargeContent(
    ui: ElecUiState,
    order: ElecRechargeOrder,
    bitmap: Bitmap,
    vm: ElecViewModel,
    onDismiss: () -> Unit,
    onSaveQr: (Bitmap, (Boolean, String) -> Unit) -> Unit,
) {
    var now by remember(order.orderCode) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(order.orderCode) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    val remainingSeconds = ((ui.orderExpireAt - now) / 1_000L).coerceAtLeast(0L)
    val expired = ui.orderExpireAt <= 0L || remainingSeconds <= 0L

    val context = LocalContext.current
    var autoLaunched by rememberSaveable(order.orderCode) { mutableStateOf(false) }
    LaunchedEffect(order.orderCode) {
        if (autoLaunched || ui.rechargeChannel == ElecPayChannel.WECHAT) return@LaunchedEffect
        autoLaunched = true
        delay(600L)
        if (ui.orderExpireAt <= System.currentTimeMillis()) return@LaunchedEffect
        val result = PaymentLauncher.launch(context, ui.rechargeChannel, order.qrContent)
        if (result is PaymentLauncher.LaunchResult.Failed) {
            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
        }
    }

    RechargeDialogHeader(
        title = "${ui.rechargeChannel.label}充值",
        subtitle = dormLabel(ui),
        onDismiss = onDismiss,
    )
    Spacer(Modifier.height(16.dp))
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "充值金额",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Text(
                    text = formatAmountInput(ui.rechargeAmount),
                    style = MiuixTheme.textStyles.headline2,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${ui.rechargeChannel.label}扫码支付",
                    style = MiuixTheme.textStyles.footnote1,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "订单 ${shortOrderCode(order.orderCode)}",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    Box(
        modifier = Modifier
            .size(252.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "充值二维码",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (expired) 0.15f else 1f),
        )
        if (expired) {
            Text(
                text = "二维码已过期",
                style = MiuixTheme.textStyles.title4,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        text = if (expired) {
            "二维码已过期，请重新生成后再支付"
        } else {
            "二维码有效期 ${formatCountdown(remainingSeconds)}"
        },
        style = MiuixTheme.textStyles.footnote1,
        color = if (expired) {
            MiuixTheme.colorScheme.error
        } else {
            MiuixTheme.colorScheme.onSurfaceVariantSummary
        },
    )
    if (expired) {
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = vm::createRechargeOrder,
            enabled = !ui.orderLoading,
            modifier = Modifier.fillMaxWidth(),
            minHeight = 48.dp,
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            if (ui.orderLoading) {
                CircularProgressIndicator(
                    size = 18.dp,
                    strokeWidth = 2.dp,
                    colors = ProgressIndicatorDefaults.progressIndicatorColors(
                        foregroundColor = MiuixTheme.colorScheme.onPrimary,
                    ),
                )
                Spacer(Modifier.width(8.dp))
                Text("正在生成…")
            } else {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("重新生成二维码")
            }
        }
    }
    if (!expired) {
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                when (val result = PaymentLauncher.launch(context, ui.rechargeChannel, order.qrContent)) {
                    is PaymentLauncher.LaunchResult.Launched ->
                        Toast.makeText(context, "正在打开${result.appLabel}…", Toast.LENGTH_SHORT).show()

                    is PaymentLauncher.LaunchResult.Failed ->
                        Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            minHeight = 48.dp,
            colors = ButtonDefaults.buttonColors(),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("打开${ui.rechargeChannel.label}支付")
        }
    }
    Spacer(Modifier.height(10.dp))
    Button(
        onClick = {
            onSaveQr(bitmap) { _, message ->
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        },
        enabled = !expired && !ui.orderLoading && ui.payStatus != ElecPayStatus.FAILED,
        modifier = Modifier.fillMaxWidth(),
        minHeight = 48.dp,
        colors = ButtonDefaults.buttonColors(),
    ) {
        Icon(
            imageVector = Icons.Filled.Save,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text("保存二维码")
    }
    Spacer(Modifier.height(10.dp))
    Button(
        onClick = vm::confirmPaid,
        enabled = !ui.payChecking && !ui.orderLoading,
        modifier = Modifier.fillMaxWidth(),
        minHeight = 52.dp,
        colors = ButtonDefaults.buttonColorsPrimary(),
    ) {
        if (ui.payChecking) {
            CircularProgressIndicator(
                size = 18.dp,
                strokeWidth = 2.dp,
                colors = ProgressIndicatorDefaults.progressIndicatorColors(
                    foregroundColor = MiuixTheme.colorScheme.onPrimary,
                ),
            )
            Spacer(Modifier.width(8.dp))
            Text("正在查询…")
        } else {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(if (ui.payStatus == ElecPayStatus.TIMEOUT) "再次查询支付结果" else "已完成支付")
        }
    }
    Spacer(Modifier.height(10.dp))
    TextButton(
        text = "暂不支付",
        onClick = onDismiss,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(16.dp))
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = if (ui.rechargeChannel == ElecPayChannel.WECHAT) {
                    "1. 点击上方按钮会尝试识别二维码链接并打开微信；如未跳转，可保存二维码后在微信扫一扫中从相册选择。"
                } else {
                    "1. 已自动尝试打开支付宝；如未跳转，可点击上方按钮或使用「扫一扫」扫描二维码。"
                },
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Text(
                text = "2. 支付完成后点击「已完成支付」查询到账结果。",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Text(
                text = "3. 如未点击，系统会自动处理，最迟 1 小时内到账。",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
    if (ui.payChecking) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                size = 16.dp,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = ui.payStatusText ?: "正在查询支付结果…",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    } else if (ui.payStatus == ElecPayStatus.TIMEOUT || ui.payStatus == ElecPayStatus.FAILED) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = ui.payStatusText ?: "暂未查询到支付结果",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.error,
        )
        if (ui.payStatus == ElecPayStatus.FAILED) {
            TextButton(
                text = "重新生成二维码",
                onClick = vm::createRechargeOrder,
                enabled = !ui.orderLoading,
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    Text(
        text = "暂不支付不会取消订单；如已扫码付款，请点击「已完成支付」确认到账。",
        style = MiuixTheme.textStyles.footnote2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun RechargeSuccessContent(
    ui: ElecUiState,
    order: ElecRechargeOrder,
    onDismiss: () -> Unit,
) {
    RechargeDialogHeader(
        title = "充值成功",
        subtitle = dormLabel(ui),
        onDismiss = onDismiss,
    )
    Spacer(Modifier.height(28.dp))
    Box(
        modifier = Modifier
            .size(76.dp)
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(44.dp),
            tint = MiuixTheme.colorScheme.onPrimaryContainer,
        )
    }
    Spacer(Modifier.height(16.dp))
    Text(
        text = formatAmountInput(ui.rechargeAmount),
        style = MiuixTheme.textStyles.headline1,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(6.dp))
    Text(
        text = "支付已确认，电费将稍后到账",
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
    Spacer(Modifier.height(20.dp))
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            InfoRow("宿舍", dormLabel(ui))
            InfoRow("支付方式", ui.rechargeChannel.label)
            InfoRow("订单号", shortOrderCode(order.orderCode))
        }
    }
    Spacer(Modifier.height(12.dp))
    Text(
        text = "已自动刷新电费数据；若余额暂未更新，请稍后点击页面刷新。",
        style = MiuixTheme.textStyles.footnote2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
    Spacer(Modifier.height(20.dp))
    Button(
        onClick = onDismiss,
        modifier = Modifier.fillMaxWidth(),
        minHeight = 48.dp,
        colors = ButtonDefaults.buttonColorsPrimary(),
    ) {
        Text("完成")
    }
}

@Composable
private fun RechargeRow(record: ElecRechargeRecord) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            contentColor = MiuixTheme.colorScheme.onPrimaryContainer,
        ) {
            Text(
                text = formatYuan(record.amountYuan),
                style = MiuixTheme.textStyles.footnote1,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = record.method,
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "${formatRechargeTime(record)} · 订单 ${shortOrderCode(record.orderCode)}",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "已充值",
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.primary,
        )
    }
}

private data class RechargeMonthBar(val label: String, val amountYuan: Double)

@Composable
private fun RechargeMonthlyBars(records: List<ElecRechargeRecord>) {
    val bars = remember(records) { buildRechargeMonthBars(records) }
    if (bars.isEmpty()) return
    val maxAmount = bars.maxOf { it.amountYuan }.coerceAtLeast(1.0)
    Column {
        Text(
            text = "按月充值",
            style = MiuixTheme.textStyles.title4,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            bars.forEach { bar ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Text(
                        text = formatYuanCompact(bar.amountYuan),
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .width(26.dp)
                            .height((12 + 70 * (bar.amountYuan / maxAmount)).dp)
                            .clip(
                                RoundedCornerShape(
                                    topStart = 8.dp,
                                    topEnd = 8.dp,
                                ),
                            )
                            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.75f)),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = bar.label,
                        style = MiuixTheme.textStyles.footnote2,
                    )
                }
            }
        }
    }
}

private fun buildRechargeMonthBars(records: List<ElecRechargeRecord>): List<RechargeMonthBar> {
    val grouped = records
        .mapNotNull { record -> record.paidAt?.let { YearMonth.from(it) to record.amountYuan } }
        .groupBy({ it.first }, { it.second })
    return grouped.entries
        .sortedBy { it.key }
        .takeLast(6)
        .map { (month, amounts) -> RechargeMonthBar("${month.monthValue}月", amounts.sum()) }
}

private fun formatRechargeTime(record: ElecRechargeRecord): String {
    val time = record.paidAt ?: return record.paidAtRaw.ifBlank { "时间未知" }
    val pattern = if (time.year == LocalDate.now().year) "MM-dd HH:mm" else "yyyy-MM-dd HH:mm"
    return time.format(DateTimeFormatter.ofPattern(pattern))
}

private fun shortOrderCode(code: String): String {
    if (code.isBlank()) return "—"
    return if (code.length > 12) "…${code.takeLast(12)}" else code
}

private fun formatYuanCompact(value: Double): String =
    if (value == value.toLong().toDouble()) "¥${value.toLong()}"
    else String.format(Locale.CHINA, "¥%.1f", value)

@Composable
private fun HistoryCard(history: List<ElecSnapshot>, stats: ElecStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ShowChart,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MiuixTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "用电趋势",
                style = MiuixTheme.textStyles.title3,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "本机记录",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Spacer(Modifier.height(12.dp))
        val recent = history.takeLast(30)
        if (recent.size >= 2) {
            RemainingSparkline(
                values = recent.map { it.remainingKwh.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp),
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatRecordTime(recent.first().recordedAt),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Text(
                    text = "近 ${recent.size} 次查询",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Text(
                    text = formatRecordTime(recent.last().recordedAt),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        } else {
            Text(
                text = "每次查询会自动记录读数，积累 2 条以上后显示趋势。",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCell(
                label = "近 7 日日均",
                value = stats.dailyUsageKwh?.let { "${formatKwh(it)} 度" } ?: "—",
                modifier = Modifier.weight(1f),
            )
            StatCell(
                label = "本月用电",
                value = stats.monthUsageKwh?.let { "${formatKwh(it)} 度" } ?: "—",
                modifier = Modifier.weight(1f),
            )
            StatCell(
                label = "本月电费",
                value = stats.monthCostYuan?.let { formatYuan(it) } ?: "—",
                modifier = Modifier.weight(1f),
            )
        }
        if (stats.daysLeft != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "按近期用电速度，当前剩余电量约可用 ${formatDays(stats.daysLeft)}",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        contentColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = value,
                style = MiuixTheme.textStyles.footnote1,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MiuixTheme.textStyles.footnote2,
            )
        }
    }
}

@Composable
private fun RemainingSparkline(values: List<Float>, modifier: Modifier = Modifier) {
    val lineColor = MiuixTheme.colorScheme.primary
    val fillColor = lineColor.copy(alpha = 0.12f)
    val gridColor = MiuixTheme.colorScheme.dividerLine.copy(alpha = 0.6f)
    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas
        val minValue = values.minOrNull() ?: return@Canvas
        val maxValue = values.maxOrNull() ?: return@Canvas
        val span = (maxValue - minValue).coerceAtLeast(0.5f)
        val padding = 6.dp.toPx()
        val chartHeight = size.height - padding * 2
        val stepX = size.width / (values.size - 1)

        fun point(index: Int): Offset {
            val x = stepX * index
            val y = padding + (1f - (values[index] - minValue) / span) * chartHeight
            return Offset(x, y)
        }

        for (i in 0..2) {
            val y = padding + chartHeight * i / 2f
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f,
            )
        }

        val linePath = Path()
        values.indices.forEach { index ->
            val p = point(index)
            if (index == 0) linePath.moveTo(p.x, p.y) else linePath.lineTo(p.x, p.y)
        }
        val areaPath = Path().apply {
            addPath(linePath)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(path = areaPath, color = fillColor)
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
        drawCircle(
            color = lineColor,
            radius = 3.dp.toPx(),
            center = point(values.lastIndex),
        )
    }
}

/**
 * Holds the outgoing value briefly, then crossfades it with a Gaussian blur so
 * refreshed readings do not snap. Only the value itself is blurred; the card
 * surface and the static labels stay untouched.
 */
@Composable
private fun <T> BlurredValue(
    value: T,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    var current by remember { mutableStateOf(value) }
    var previous by remember { mutableStateOf<T?>(null) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(value) {
        if (value == current) {
            if (previous != null) {
                progress.animateTo(1f, tween(ElecValueFadeMillis))
                previous = null
            }
            return@LaunchedEffect
        }
        delay(ElecValueHoldMillis)
        previous = current
        current = value
        progress.snapTo(0f)
        progress.animateTo(1f, tween(ElecValueFadeMillis))
        previous = null
    }
    Box(modifier = modifier.animateContentSize(tween(ElecValueFadeMillis))) {
        Box(
            modifier = Modifier
                .alpha(progress.value)
                .blur(
                    radius = (ElecValueBlurDp * (1f - progress.value)).dp,
                    edgeTreatment = BlurredEdgeTreatment.Unbounded,
                ),
        ) {
            content(current)
        }
        previous?.let { old ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .alpha(1f - progress.value)
                    .blur(
                        radius = (ElecValueBlurDp * progress.value).dp,
                        edgeTreatment = BlurredEdgeTreatment.Unbounded,
                    ),
            ) {
                content(old)
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String?,
    animateValue: Boolean = false,
) {
    if (value.isNullOrBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            color = LocalContentColor.current.copy(alpha = 0.7f),
        )
        Spacer(Modifier.width(16.dp))
        if (animateValue) {
            BlurredValue(value = value) { text ->
                Text(
                    text = text,
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.Medium,
                )
            }
        } else {
            Text(
                text = value,
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
