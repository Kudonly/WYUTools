package com.wyutools.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

/**
 * 玻璃材质高级模式。滑条编辑的是「当前主题」的参数：
 * 柔光玻璃（上游 miuix-glass）与液态玻璃（本地 shader）各自独立存储；
 * 液态玻璃没有 blend/inner/light 层，对应滑条隐藏。
 */
@Composable
fun SoftGlassAdvancedScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scroll = rememberScrollState()
    val fraction = rememberWyuCollapseFraction(scroll)
    val cardTheme by WyuAppearance.cardTheme.collectAsState()
    val isLiquid = cardTheme == WyuCardTheme.Liquid
    val softTuning by WyuAppearance.softGlassTuning.collectAsState()
    val liquidTuning by WyuAppearance.liquidGlassTuning.collectAsState()
    val scope by WyuAppearance.softGlassTuneScope.collectAsState()
    val dark = LocalWyuDarkTheme.current
    var showResetConfirm by remember { mutableStateOf(false) }

    fun paramValue(param: WyuGlassParam): Float =
        if (isLiquid) liquidTuning.liquidValueFor(param, dark) else softTuning.valueFor(param, dark)

    fun paramEdit(param: WyuGlassParam, value: Float) {
        if (isLiquid) {
            WyuAppearance.setLiquidGlassTuning(context, liquidTuning.editedLiquid(param, scope, value))
        } else {
            WyuAppearance.setSoftGlassTuning(context, softTuning.edited(param, value, scope))
        }
    }

    WyuPageScaffold(
        progress = { fraction.value },
        maskCollapseHeight = WyuLargeTitleHeight,
        topBar = {
            WyuActionBar(
                title = "高级模式",
                collapsedFraction = { fraction.value },
                navigationIcon = {
                    WyuTopBarIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        onClick = onBack,
                    )
                },
                actions = {
                    WyuTopBarAction(
                        icon = Icons.Outlined.Restore,
                        label = "恢复默认",
                        onClick = { showResetConfirm = true },
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(top = padding.calculateTopPadding()),
        ) {
            Text(
                text = if (isLiquid) {
                    "正在调整液态玻璃（本地 shader）：参数与柔光玻璃互相独立，默认值即原始推荐值。修改会实时保存并立即生效。"
                } else {
                    "正在调整柔光玻璃（miuix-glass 材质）：卡片与弹出菜单共用同一套参数。修改会实时保存并立即生效。"
                },
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
            Spacer(Modifier.height(4.dp))
            ScopeCard(
                scope = scope,
                onSelect = { WyuAppearance.setSoftGlassTuneScope(context, it) },
            )
            TuningSection(title = "折射与边缘") {
                Text(
                    text = "控制玻璃边缘的弯曲与过渡，数值越大，边缘形变与折射越明显。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(8.dp))
                TuneSlider(
                    label = "折射率",
                    param = WyuGlassParam.RefractionIor,
                    value = paramValue(WyuGlassParam.RefractionIor),
                    valueRange = 0.1f..10f,
                    format = { "%.1f".format(it) },
                    onChange = { paramEdit(WyuGlassParam.RefractionIor, it) },
                )
                TuneSlider(
                    label = "边缘厚度",
                    param = WyuGlassParam.EdgeThickness,
                    value = paramValue(WyuGlassParam.EdgeThickness),
                    valueRange = 0f..800f,
                    format = { it.roundToInt().toString() },
                    onChange = { paramEdit(WyuGlassParam.EdgeThickness, it) },
                )
                TuneSlider(
                    label = "边缘宽度",
                    param = WyuGlassParam.ShapeEdgePx,
                    value = paramValue(WyuGlassParam.ShapeEdgePx),
                    valueRange = 0.5f..432f,
                    format = { it.roundToInt().toString() },
                    onChange = { paramEdit(WyuGlassParam.ShapeEdgePx, it) },
                )
            }
            TuningSection(title = "反射") {
                Text(
                    text = "控制玻璃表面高光的位置与强度，数值过高会让卡片发白。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(8.dp))
                TuneSlider(
                    label = "反射偏移",
                    param = WyuGlassParam.ReflectOffset,
                    value = paramValue(WyuGlassParam.ReflectOffset),
                    valueRange = -3200f..4800f,
                    format = { it.roundToInt().toString() },
                    onChange = { paramEdit(WyuGlassParam.ReflectOffset, it) },
                )
                TuneSlider(
                    label = "反射强度",
                    param = WyuGlassParam.ReflectionStrength,
                    value = paramValue(WyuGlassParam.ReflectionStrength),
                    valueRange = 0f..2f,
                    format = { "${(it * 100).roundToInt()}%" },
                    onChange = { paramEdit(WyuGlassParam.ReflectionStrength, it) },
                )
            }
            TuningSection(title = "背景与色调") {
                Text(
                    text = "控制玻璃透出的壁纸色彩与卡片底色；选择「仅浅色」或「仅深色」时，这两项只影响对应主题。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(8.dp))
                TuneSlider(
                    label = "背景饱和度",
                    param = WyuGlassParam.Saturation,
                    value = paramValue(WyuGlassParam.Saturation),
                    valueRange = 0f..2f,
                    format = { "${(it * 100).roundToInt()}%" },
                    onChange = { paramEdit(WyuGlassParam.Saturation, it) },
                )
                TuneSlider(
                    label = "卡片底色不透明度",
                    param = WyuGlassParam.CardTintAlpha,
                    value = paramValue(WyuGlassParam.CardTintAlpha),
                    valueRange = 0f..1f,
                    format = { "${(it * 100).roundToInt()}%" },
                    onChange = { paramEdit(WyuGlassParam.CardTintAlpha, it) },
                )
            }
            if (!isLiquid) {
                TuningSection(title = "混合与光照") {
                    Text(
                        text = "对应 miuix-glass 的 blend、inner 与 light 层，控制底色混合和内部白度。",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(Modifier.height(8.dp))
                    TuneSlider(
                        label = "混合量",
                        param = WyuGlassParam.BlendAmount,
                        value = paramValue(WyuGlassParam.BlendAmount),
                        valueRange = 0f..1f,
                        format = { "${(it * 100).roundToInt()}%" },
                        onChange = { paramEdit(WyuGlassParam.BlendAmount, it) },
                    )
                    TuneSlider(
                        label = "内层混色",
                        param = WyuGlassParam.InnerColorMix,
                        value = paramValue(WyuGlassParam.InnerColorMix),
                        valueRange = 0f..2f,
                        format = { "%.2f".format(it) },
                        onChange = { paramEdit(WyuGlassParam.InnerColorMix, it) },
                    )
                    TuneSlider(
                        label = "主光强度",
                        param = WyuGlassParam.LightIntensity,
                        value = paramValue(WyuGlassParam.LightIntensity),
                        valueRange = 0f..4f,
                        format = { "%.2f".format(it) },
                        onChange = { paramEdit(WyuGlassParam.LightIntensity, it) },
                    )
                }
            } else {
                Text(
                    text = "液态玻璃是本地 shader 渲染，没有 blend、inner 与 light 层，以上滑条即全部可调项。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
            Text(
                text = "不同设备与壁纸下的观感可能略有差异，可随时点按右上角的恢复按钮还原当前主题的默认参数。",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
            Spacer(Modifier.height(32.dp))
        }
    }

    WyuOverlayDialog(
        show = showResetConfirm,
        title = "恢复默认参数",
        summary = if (isLiquid) {
            "将液态玻璃的高级参数与调节范围一并恢复为默认值，恢复后立即生效。柔光玻璃的参数不受影响。"
        } else {
            "将柔光玻璃的高级参数与调节范围一并恢复为默认值，恢复后立即生效。液态玻璃的参数不受影响。"
        },
        onDismissRequest = { showResetConfirm = false },
    ) {
        Spacer(Modifier.height(16.dp))
        WyuDialogButton(
            text = "恢复默认",
            onClick = {
                if (isLiquid) {
                    WyuAppearance.setLiquidGlassTuning(context, WyuLiquidGlassTuning())
                } else {
                    WyuAppearance.setSoftGlassTuning(context, WyuSoftGlassTuning())
                }
                WyuAppearance.setSoftGlassTuneScope(context, WyuTuneScope.Sync)
                showResetConfirm = false
                Toast.makeText(context, "已恢复默认参数", Toast.LENGTH_SHORT).show()
            },
        )
        Spacer(Modifier.height(8.dp))
        WyuDialogCancelButton(onClick = { showResetConfirm = false })
    }
}

@Composable
private fun ScopeCard(
    scope: WyuTuneScope,
    onSelect: (WyuTuneScope) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf(Rect.Zero) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "调节范围", style = MiuixTheme.textStyles.body1)
                Text(
                    text = "选择滑条作用于浅色、深色，还是两者同步。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Box(modifier = Modifier.onGloballyPositioned { anchor = it.boundsInWindow() }) {
                WyuDropdownTrigger(label = scope.label, onClick = { expanded = true })
                WyuDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    anchor = anchor,
                ) {
                    WyuTuneScope.entries.forEach { option ->
                        WyuDropdownItem(
                            text = option.label,
                            selected = scope == option,
                            onClick = {
                                expanded = false
                                onSelect(option)
                            },
                        )
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun TuneSlider(
    label: String,
    param: WyuGlassParam,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    onChange: (Float) -> Unit,
) {
    SoftGlassSliderRow(
        label = label,
        valueText = format(value),
        value = value,
        valueRange = valueRange,
        onChange = onChange,
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun TuningSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    SmallTitle(
        text = title,
        // 与顶栏大标题对齐（卡片仍在 16dp 页边距上）。
        insideMargin = PaddingValues(horizontal = WyuTextInset, vertical = 8.dp),
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            content = content,
        )
    }
    Spacer(Modifier.height(12.dp))
}
