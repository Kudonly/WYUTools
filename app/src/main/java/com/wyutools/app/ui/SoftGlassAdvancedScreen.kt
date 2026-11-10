package com.wyutools.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

@Composable
fun SoftGlassAdvancedScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scroll = rememberScrollState()
    val fraction = rememberWyuCollapseFraction(scroll)
    val tuning by WyuAppearance.softGlassTuning.collectAsState()
    var showResetConfirm by remember { mutableStateOf(false) }

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
                text = "调整柔光玻璃的材质参数，修改会实时保存并立即生效；点按右上角的恢复按钮可还原默认值。",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
            Spacer(Modifier.height(4.dp))
            TuningSection(title = "折射与边缘") {
                Text(
                    text = "控制玻璃边缘的弯曲与过渡，数值越大，边缘形变与折射越明显。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(8.dp))
                SoftGlassSliderRow(
                    label = "折射率",
                    valueText = "%.1f".format(tuning.refractionIor),
                    value = tuning.refractionIor,
                    valueRange = 0.1f..10f,
                    onChange = {
                        WyuAppearance.setSoftGlassTuning(
                            context,
                            tuning.copy(refractionIor = it),
                        )
                    },
                )
                Spacer(Modifier.height(8.dp))
                SoftGlassSliderRow(
                    label = "边缘厚度",
                    valueText = tuning.edgeThickness.roundToInt().toString(),
                    value = tuning.edgeThickness,
                    valueRange = 0f..800f,
                    onChange = {
                        WyuAppearance.setSoftGlassTuning(
                            context,
                            tuning.copy(edgeThickness = it),
                        )
                    },
                )
                Spacer(Modifier.height(8.dp))
                SoftGlassSliderRow(
                    label = "边缘宽度",
                    valueText = tuning.shapeEdgePx.roundToInt().toString(),
                    value = tuning.shapeEdgePx,
                    valueRange = 0.5f..432f,
                    onChange = {
                        WyuAppearance.setSoftGlassTuning(
                            context,
                            tuning.copy(shapeEdgePx = it),
                        )
                    },
                )
                Spacer(Modifier.height(8.dp))
                SoftGlassSliderRow(
                    label = "边缘过渡幂",
                    valueText = "%.2f".format(tuning.shapeEdgePow),
                    value = tuning.shapeEdgePow,
                    valueRange = 0.05f..22.8f,
                    onChange = {
                        WyuAppearance.setSoftGlassTuning(
                            context,
                            tuning.copy(shapeEdgePow = it),
                        )
                    },
                )
            }
            TuningSection(title = "反射") {
                Text(
                    text = "控制玻璃表面高光的位置与强度，数值过高会让卡片发白。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(8.dp))
                SoftGlassSliderRow(
                    label = "反射偏移",
                    valueText = tuning.reflectOffset.roundToInt().toString(),
                    value = tuning.reflectOffset,
                    valueRange = -3200f..4800f,
                    onChange = {
                        WyuAppearance.setSoftGlassTuning(
                            context,
                            tuning.copy(reflectOffset = it),
                        )
                    },
                )
                Spacer(Modifier.height(8.dp))
                SoftGlassSliderRow(
                    label = "反射强度",
                    valueText = "${(tuning.reflectionStrength * 100).roundToInt()}%",
                    value = tuning.reflectionStrength,
                    valueRange = 0f..2f,
                    onChange = {
                        WyuAppearance.setSoftGlassTuning(
                            context,
                            tuning.copy(reflectionStrength = it),
                        )
                    },
                )
                Spacer(Modifier.height(8.dp))
                SoftGlassSliderRow(
                    label = "反射亮化",
                    valueText = "%.2f".format(tuning.reflectLighten),
                    value = tuning.reflectLighten,
                    valueRange = -4.8f..7.2f,
                    onChange = {
                        WyuAppearance.setSoftGlassTuning(
                            context,
                            tuning.copy(reflectLighten = it),
                        )
                    },
                )
            }
            TuningSection(title = "背景与色调") {
                Text(
                    text = "控制玻璃透出的壁纸色彩与卡片底色，深色与浅色模式分别调整。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(8.dp))
                SoftGlassSliderRow(
                    label = "背景饱和度（浅色）",
                    valueText = "${(tuning.saturationLight * 100).roundToInt()}%",
                    value = tuning.saturationLight,
                    valueRange = 0f..2f,
                    onChange = {
                        WyuAppearance.setSoftGlassTuning(
                            context,
                            tuning.copy(saturationLight = it),
                        )
                    },
                )
                Spacer(Modifier.height(8.dp))
                SoftGlassSliderRow(
                    label = "背景饱和度（深色）",
                    valueText = "${(tuning.saturationDark * 100).roundToInt()}%",
                    value = tuning.saturationDark,
                    valueRange = 0f..2f,
                    onChange = {
                        WyuAppearance.setSoftGlassTuning(
                            context,
                            tuning.copy(saturationDark = it),
                        )
                    },
                )
                Spacer(Modifier.height(8.dp))
                SoftGlassSliderRow(
                    label = "卡片底色不透明度（浅色）",
                    valueText = "${(tuning.cardTintAlphaLight * 100).roundToInt()}%",
                    value = tuning.cardTintAlphaLight,
                    valueRange = 0f..1f,
                    onChange = {
                        WyuAppearance.setSoftGlassTuning(
                            context,
                            tuning.copy(cardTintAlphaLight = it),
                        )
                    },
                )
                Spacer(Modifier.height(8.dp))
                SoftGlassSliderRow(
                    label = "卡片底色不透明度（深色）",
                    valueText = "${(tuning.cardTintAlphaDark * 100).roundToInt()}%",
                    value = tuning.cardTintAlphaDark,
                    valueRange = 0f..1f,
                    onChange = {
                        WyuAppearance.setSoftGlassTuning(
                            context,
                            tuning.copy(cardTintAlphaDark = it),
                        )
                    },
                )
            }
            Text(
                text = "不同设备与壁纸下的观感可能略有差异，可随时点按右上角的恢复按钮还原默认参数。",
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
        summary = "将柔光玻璃的高级参数恢复为默认值，恢复后立即生效。",
        onDismissRequest = { showResetConfirm = false },
    ) {
        Spacer(Modifier.height(16.dp))
        WyuDialogButton(
            text = "恢复默认",
            onClick = {
                WyuAppearance.setSoftGlassTuning(context, WyuSoftGlassTuning())
                showResetConfirm = false
                Toast.makeText(context, "已恢复默认参数", Toast.LENGTH_SHORT).show()
            },
        )
        Spacer(Modifier.height(8.dp))
        WyuDialogCancelButton(onClick = { showResetConfirm = false })
    }
}

@Composable
private fun TuningSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    SmallTitle(
        text = title,
        insideMargin = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
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
