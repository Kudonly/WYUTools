package com.wyutools.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 成绩 / 绩点 / 体测 / 教评四个页面共用的外壳与小控件。
 *
 * 它们都挂在同一个「教务数据」体系下面，所以加载中、未登录、加载失败这三种
 * 状态长得一模一样，做成公共件比复制四份更省事，也保证以后只改一处。
 */
@Composable
internal fun AcademicStatePage(
    title: String,
    onBack: () -> Unit,
    showRefresh: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    WyuPageScaffold(
        progress = { 0f },
        maskCollapseHeight = WyuLargeTitleHeight,
        topBar = {
            WyuActionBar(
                title = title,
                collapsedFraction = { 0f },
                navigationIcon = {
                    WyuTopBarIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        onClick = onBack,
                    )
                },
                actions = {
                    if (showRefresh && onRefresh != null) {
                        WyuTopBarAction(
                            icon = Icons.Filled.Refresh,
                            label = "刷新",
                            onClick = onRefresh,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            content = content,
        )
    }
}

@Composable
internal fun AcademicGlyph(icon: ImageVector, primary: Boolean) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(
                if (primary) {
                    MiuixTheme.colorScheme.primaryContainer
                } else {
                    MiuixTheme.colorScheme.errorContainer
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (primary) {
                MiuixTheme.colorScheme.onPrimaryContainer
            } else {
                MiuixTheme.colorScheme.onErrorContainer
            },
            modifier = Modifier.size(34.dp),
        )
    }
}

@Composable
internal fun AcademicTitle(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.title2,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
    )
}

@Composable
internal fun AcademicBody(text: String, error: Boolean = false) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.body2,
        color = if (error) {
            MiuixTheme.colorScheme.error
        } else {
            MiuixTheme.colorScheme.onSurfaceVariantSummary
        },
        textAlign = TextAlign.Center,
    )
}

/** 未登录时四页通用的引导块。 */
@Composable
internal fun AcademicLoginPrompt(
    icon: ImageVector,
    title: String,
    body: String,
    error: String?,
    onLogin: () -> Unit,
) {
    AcademicGlyph(icon, primary = true)
    Spacer(Modifier.height(20.dp))
    AcademicTitle(title)
    Spacer(Modifier.height(8.dp))
    AcademicBody(body)
    if (error != null) {
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(rememberWyuSquircle(16.dp))
                .background(MiuixTheme.colorScheme.errorContainer.copy(alpha = 0.55f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = error,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    Spacer(Modifier.height(28.dp))
    Button(
        onClick = onLogin,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColorsPrimary(),
    ) {
        Text(text = "登录统一身份认证", fontWeight = FontWeight.SemiBold)
    }
}

/** 加载失败时的重试块。 */
@Composable
internal fun AcademicErrorBlock(message: String, onRetry: () -> Unit) {
    AcademicGlyph(icon = Icons.Outlined.CloudOff, primary = false)
    Spacer(Modifier.height(20.dp))
    AcademicTitle("加载失败")
    Spacer(Modifier.height(8.dp))
    AcademicBody(message, error = true)
    Spacer(Modifier.height(28.dp))
    Button(
        onClick = onRetry,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColorsPrimary(),
    ) {
        Text(text = "重试", fontWeight = FontWeight.SemiBold)
    }
}

/** 学期选择：横向滚动的胶囊，最新的排在前面由调用方决定顺序。 */
@Composable
internal fun TermChips(
    terms: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(end = 4.dp),
    ) {
        items(terms, key = { it.first }) { (code, label) ->
            ActionChip(
                text = label,
                onClick = { onSelect(code) },
                primary = code == selected,
            )
        }
    }
}

@Composable
internal fun AcademicInfoRow(
    icon: ImageVector?,
    text: String,
    tint: androidx.compose.ui.graphics.Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    bold: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = tint,
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote1,
            fontWeight = if (bold) FontWeight.Medium else FontWeight.Normal,
            color = tint,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 详情弹窗的可见性托管。
 *
 * 弹窗要从底部滑出去得先把 `show` 置 false 让它播完退出动画，动画结束后才
 * 能丢掉数据。如果调用方直接把数据置空，整个 Composable 会被移出组合，
 * 卡片就硬生生消失。这个类就是替调用方记住这两步。
 */
@Stable
internal class AcademicDetailSheetState<T> {
    var target by mutableStateOf<T?>(null)
        private set
    var visible by mutableStateOf(false)
        private set

    fun open(value: T) {
        target = value
        visible = true
    }

    fun close() {
        visible = false
    }

    fun clear() {
        target = null
    }
}

@Composable
internal fun <T> rememberAcademicDetailSheet(): AcademicDetailSheetState<T> =
    remember { AcademicDetailSheetState() }

/**
 * 二级详情弹窗：分析单、体测子项、评价明细都住在这里。
 *
 * 和电费、作息、健身房那些工具共用 [WyuOverlayDialog]，从底部弹上来，
 * 玻璃卡片、圆角、进出动画完全一致。
 */
@Composable
internal fun AcademicDetailDialog(
    show: Boolean,
    title: String,
    subtitle: String? = null,
    onDismiss: () -> Unit,
    onDismissed: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    WyuOverlayDialog(
        show = show,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissed,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.headline2,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(14.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp),
                content = content,
            )
        }
    }
}
