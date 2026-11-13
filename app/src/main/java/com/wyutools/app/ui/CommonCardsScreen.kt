package com.wyutools.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 设置 → 常用页面: which tools keep a card on the 常用 tab. Anything switched off
 * moves to 更多 as an icon, so the two lists always add up to every tool.
 */
@Composable
fun CommonCardsScreen(onBack: () -> Unit) {
    val scroll = rememberScrollState()
    val fraction = rememberWyuCollapseFraction(scroll)
    val layout by WyuToolLayoutStore.state.collectAsState()
    val primary = MiuixTheme.colorScheme.primary

    WyuPageScaffold(
        progress = { fraction.value },
        maskCollapseHeight = WyuLargeTitleHeight,
        topBar = {
            WyuActionBar(
                title = "常用卡片",
                collapsedFraction = { fraction.value },
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
                .verticalScroll(scroll)
                .padding(top = padding.calculateTopPadding()),
        ) {
            SmallTitle(
                text = "显示的卡片",
                insideMargin = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                // Draws on the same frosted-glass wrapper as every other card in
                // the app: 28dp squircle, wallpaper underlay or Gaussian blur
                // depending on the appearance setting. samplePageContent stays
                // false so it samples the wallpaper, not the list behind it.
                cornerRadius = WyuCardCornerRadius,
                samplePageContent = false,
                insideMargin = PaddingValues(vertical = 4.dp),
            ) {
                WyuTool.entries.forEachIndexed { index, tool ->
                    val checked = layout.isCommon(tool)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(rememberWyuIconShape(38.dp))
                                .background(primary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = tool.icon,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = primary,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tool.title,
                                style = MiuixTheme.textStyles.headline2,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if (tool.lockedInCommon) "固定显示，不可移除" else tool.summary,
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = checked,
                            onCheckedChange = { on ->
                                WyuToolLayoutStore.setInCommon(tool, on)
                            },
                            enabled = !tool.lockedInCommon,
                        )
                    }
                    if (index != WyuTool.entries.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 70.dp, end = 20.dp),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.12f),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "关掉的卡片会以图标形式收进「更多」，在那里也能直接打开，随时可以再放回来。",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}
