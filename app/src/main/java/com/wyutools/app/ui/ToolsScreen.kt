package com.wyutools.app.ui

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.foundation.ScrollState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.SportsBasketball
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyutools.app.SharedCardSnapshot
import com.wyutools.app.SharedViewTransition
import com.wyutools.app.schedule.ScheduleFocus
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

@Composable
fun ToolsContent(
    scrollState: ScrollState,
    topInset: Dp,
    bottomInset: Dp,
    scheduleFocus: ScheduleFocus?,
    scheduleWeek: Int,
    onOpenSchedule: (SharedCardSnapshot?) -> Unit,
    onOpenLxt: (SharedCardSnapshot?) -> Unit,
    onOpenElec: (SharedCardSnapshot?) -> Unit,
    onOpenGym: (SharedCardSnapshot?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp),
    ) {
        // MeContent starts its first card at topInset + 8dp column padding
        // + 14dp spacedBy, so match that exact offset here.
        Spacer(Modifier.height(topInset + 22.dp))
        ScheduleToolEntry(
            focus = scheduleFocus,
            week = scheduleWeek,
            onClick = onOpenSchedule,
        )
        Spacer(Modifier.height(12.dp))
        ToolEntry(
            icon = Icons.Outlined.WaterDrop,
            title = "洗澡",
            summary = "热水余额 · 开阀用水",
            onClick = onOpenLxt,
        )
        Spacer(Modifier.height(12.dp))
        ToolEntry(
            icon = Icons.Outlined.Bolt,
            title = "电费",
            summary = "用电查询 · 扫码充值",
            onClick = onOpenElec,
        )
        Spacer(Modifier.height(12.dp))
        ToolEntry(
            icon = Icons.Outlined.SportsBasketball,
            title = "体育馆预约",
            summary = "场地查询 · 在线预约",
            onClick = onOpenGym,
        )
        Spacer(Modifier.height(32.dp))
        Text(
            text = "更多工具正在路上",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(bottomInset + 24.dp))
    }
}

@Composable
private fun ToolCard(
    title: String,
    onClick: (SharedCardSnapshot?) -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = WyuCardCornerRadius,
    color: Color? = null,
    contentColor: Color? = null,
    content: @Composable ColumnScope.(
        titleModifier: Modifier,
        onTitleLayout: (TextLayoutResult) -> Unit,
    ) -> Unit,
) {
    val graphicsLayer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val density = LocalDensity.current
    val radiusPx = with(density) { cornerRadius.toPx() }
    val cardBounds = remember { Rect() }
    val titleBounds = remember { Rect() }
    // The title is an element of its own: the card layer below never contains
    // it, and it is painted on top of that layer with the layout the Text node
    // in the content reports. The morph's stretched card image is therefore
    // always title-free, and the flying title is the only title on screen.
    // Its place is resolved in the card's local coordinates at draw time:
    // window coordinates go stale mid-swipe (pager layer offsets are applied
    // without a global-position pass for both nodes in the same frame).
    val titleLayout = remember { mutableStateOf<TextLayoutResult?>(null) }
    var cardCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var titleCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val titleStyle = MiuixTheme.textStyles.headline2
    val titleFontSizePx = with(density) { titleStyle.fontSize.toPx() }
    val titleStyleLineHeightPx = with(density) {
        val lineHeight = titleStyle.lineHeight
        (if (lineHeight != androidx.compose.ui.unit.TextUnit.Unspecified) {
            lineHeight
        } else {
            titleStyle.fontSize * 1.25f
        }).toPx()
    }
    var launching by remember { mutableStateOf(false) }
    val cardColors = if (color != null && contentColor != null) {
        CardDefaults.defaultColors(color = color, contentColor = contentColor)
    } else {
        CardDefaults.defaultColors()
    }
    val openCard: () -> Unit = {
        if (cardBounds.isEmpty || launching) {
            if (!launching) onClick(null)
        } else {
            launching = true
            scope.launch {
                // A return may still be pulling the home back; wait until it
                // lands plus its 50ms margin so the snapshot is never taken
                // from a shrunk, shifted card.
                WyuMorphPageState.awaitReturnSettled()
                val bitmap = runCatching {
                    graphicsLayer.toImageBitmap().asAndroidBitmap()
                }.getOrNull()
                launching = false
                onClick(
                    bitmap?.let {
                        SharedCardSnapshot(
                            bitmap = it,
                            bounds = Rect(cardBounds),
                            radiusPx = radiusPx,
                            title = title,
                            titleBounds = if (titleBounds.isEmpty) null else Rect(titleBounds),
                            titleFontSizePx = titleFontSizePx,
                            titleLineHeightPx = if (titleBounds.isEmpty) {
                                titleStyleLineHeightPx
                            } else {
                                titleBounds.height().toFloat()
                            },
                        )
                    },
                )
            }
        }
    }
    Card(
        onClick = openCard,
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                cardCoordinates = coordinates
                val bounds = coordinates.boundsInWindow()
                val origin = if (SharedViewTransition.boundsUseWindowCoordinate) {
                    null
                } else {
                    IntArray(2).also(view::getLocationOnScreen)
                }
                cardBounds.set(
                    bounds.left.roundToInt() + (origin?.get(0) ?: 0),
                    bounds.top.roundToInt() + (origin?.get(1) ?: 0),
                    bounds.right.roundToInt() + (origin?.get(0) ?: 0),
                    bounds.bottom.roundToInt() + (origin?.get(1) ?: 0),
                )
            }
            .drawWithContent {
                graphicsLayer.record {
                    this@drawWithContent.drawContent()
                }
                drawLayer(graphicsLayer)
                val layout = titleLayout.value
                val card = cardCoordinates
                val title = titleCoordinates
                if (layout != null && !WyuMorphPageState.titleHidden &&
                    card != null && title != null &&
                    card.isAttached && title.isAttached
                ) {
                    drawText(
                        textLayoutResult = layout,
                        topLeft = card.localPositionOf(title, Offset.Zero),
                    )
                }
            },
        insideMargin = PaddingValues(0.dp),
        cornerRadius = cornerRadius,
        colors = cardColors,
    ) {
        content(
            Modifier
                .drawWithContent {
                    // Reserved for layout only; the card's draw pass paints the
                    // title above the recorded layer.
                }
                .onGloballyPositioned { coordinates ->
                    titleCoordinates = coordinates
                    val bounds = coordinates.boundsInWindow()
                    val origin = if (SharedViewTransition.boundsUseWindowCoordinate) {
                        null
                    } else {
                        IntArray(2).also(view::getLocationOnScreen)
                    }
                    titleBounds.set(
                        bounds.left.roundToInt() + (origin?.get(0) ?: 0),
                        bounds.top.roundToInt() + (origin?.get(1) ?: 0),
                        bounds.right.roundToInt() + (origin?.get(0) ?: 0),
                        bounds.bottom.roundToInt() + (origin?.get(1) ?: 0),
                    )
                },
        ) { layoutResult ->
            titleLayout.value = layoutResult
        }
    }
}

@Composable
private fun ToolEntry(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: (SharedCardSnapshot?) -> Unit,
) {
    ToolCard(title = title, onClick = onClick) { titleModifier, onTitleLayout ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.60f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                    tint = MiuixTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.headline2,
                    fontWeight = FontWeight.Medium,
                    modifier = titleModifier,
                    onTextLayout = onTitleLayout,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = summary,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        }
    }
}

@Composable
private fun ScheduleToolEntry(
    focus: ScheduleFocus?,
    week: Int,
    onClick: (SharedCardSnapshot?) -> Unit,
) {
    val primary = MiuixTheme.colorScheme.primary
    val onSurface = MiuixTheme.colorScheme.onSurface
    val secondary = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val course = focus?.course
    val ongoing = focus?.ongoing == true
    val status = when {
        focus == null -> "今天没有课了"
        ongoing -> "正在上课"
        else -> "接下来"
    }
    val detail = course?.let {
        buildList {
            add(it.compactTimeText())
            val room = it.roomForWeek(week)
            if (room.isNotBlank()) add(room)
        }.filter { it.isNotBlank() }.joinToString(" · ")
    }.orEmpty().ifBlank { "好好休息吧！( ˘ω˘ )" }

    ToolCard(
        title = "课表",
        onClick = onClick,
    ) { titleModifier, onTitleLayout ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = primary,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "课表",
                    style = MiuixTheme.textStyles.headline2,
                    fontWeight = FontWeight.Medium,
                    modifier = titleModifier,
                    onTextLayout = onTitleLayout,
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = status,
                style = MiuixTheme.textStyles.footnote1,
                fontWeight = FontWeight.SemiBold,
                color = if (ongoing) primary else secondary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = course?.name ?: "没有课哦",
                fontSize = 24.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Bold,
                color = onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = detail,
                style = MiuixTheme.textStyles.footnote1,
                color = secondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
