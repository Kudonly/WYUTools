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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
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
import top.yukonga.miuix.kmp.basic.SmallTitle
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
        Spacer(Modifier.height(topInset + 4.dp))
        SmallTitle(
            text = "校园服务",
            insideMargin = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
        )
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
    cornerRadius: Dp = 20.dp,
    color: Color? = null,
    contentColor: Color? = null,
    content: @Composable ColumnScope.(Modifier) -> Unit,
) {
    val graphicsLayer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val density = LocalDensity.current
    val radiusPx = with(density) { cornerRadius.toPx() }
    val cardBounds = remember { Rect() }
    val titleBounds = remember { Rect() }
    val titleStyle = MiuixTheme.textStyles.headline2
    val surfaceColor = MiuixTheme.colorScheme.surface
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
    val fallbackColor = (color ?: surfaceColor).toArgb()
    val cardColors = if (color != null && contentColor != null) {
        CardDefaults.defaultColors(color = color, contentColor = contentColor)
    } else {
        CardDefaults.defaultColors()
    }

    Card(
        onClick = {
            if (cardBounds.isEmpty || launching) {
                if (!launching) onClick(null)
                return@Card
            }
            launching = true
            scope.launch {
                val bitmap = runCatching {
                    graphicsLayer.toImageBitmap().asAndroidBitmap()
                }.getOrNull()
                val snapshotBitmap = bitmap?.let {
                    if (SharedViewTransition.isSupported()) {
                        it
                    } else {
                        eraseCardTitle(
                            bitmap = it,
                            cardBounds = cardBounds,
                            titleBounds = titleBounds,
                            fallbackColor = fallbackColor,
                            paddingPx = (3 * density.density).roundToInt(),
                        )
                    }
                }
                launching = false
                onClick(
                    snapshotBitmap?.let {
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
        },
        modifier = modifier
            .onGloballyPositioned { coordinates ->
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
            },
        insideMargin = PaddingValues(0.dp),
        cornerRadius = cornerRadius,
        colors = cardColors,
    ) {
        content(
            Modifier.onGloballyPositioned { coordinates ->
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
        )
    }
}

@Composable
private fun ToolEntry(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: (SharedCardSnapshot?) -> Unit,
) {
    ToolCard(title = title, onClick = onClick) { titleModifier ->
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
        cornerRadius = 24.dp,
    ) { titleModifier ->
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

/** The title is animated as a real Text in the overlay, so remove it from the snapshot. */
private fun eraseCardTitle(
    bitmap: Bitmap,
    cardBounds: Rect,
    titleBounds: Rect,
    fallbackColor: Int,
    paddingPx: Int,
): Bitmap {
    if (titleBounds.isEmpty || cardBounds.isEmpty || bitmap.isRecycled) return bitmap
    val left = (titleBounds.left - cardBounds.left - paddingPx).coerceAtLeast(0)
    val top = (titleBounds.top - cardBounds.top - paddingPx / 2).coerceAtLeast(0)
    val right = (titleBounds.right - cardBounds.left + paddingPx).coerceAtMost(bitmap.width)
    val bottom = (titleBounds.bottom - cardBounds.top + paddingPx / 4).coerceAtMost(bitmap.height)
    if (right <= left || bottom <= top) return bitmap
    // GraphicsLayer snapshots are HARDWARE bitmaps; copy to software before sampling pixels.
    val mutable = runCatching { bitmap.copy(Bitmap.Config.ARGB_8888, true) }.getOrNull()
        ?: return bitmap
    // One flat fill can never match frosted glass: the original sampled a far
    // edge and left a bright rectangle. Rebuild each row from the pixels just
    // outside both sides of the band instead, so only the smooth local blur
    // remains and the patch is invisible.
    val width = right - left
    val row = IntArray(width)
    val fallback = fallbackColor or 0xFF000000.toInt()
    for (y in top until bottom) {
        var lr = 0
        var lg = 0
        var lb = 0
        var ln = 0
        var rr = 0
        var rg = 0
        var rb = 0
        var rn = 0
        for (dy in -2..2) {
            val sy = (y + dy).coerceIn(0, mutable.height - 1)
            for (dx in 1..5) {
                val lp = mutable.getPixel((left - dx).coerceIn(0, mutable.width - 1), sy)
                if (lp ushr 24 >= 0xF0) {
                    lr += (lp shr 16) and 0xFF
                    lg += (lp shr 8) and 0xFF
                    lb += lp and 0xFF
                    ln++
                }
                val rp = mutable.getPixel((right - 1 + dx).coerceIn(0, mutable.width - 1), sy)
                if (rp ushr 24 >= 0xF0) {
                    rr += (rp shr 16) and 0xFF
                    rg += (rp shr 8) and 0xFF
                    rb += rp and 0xFF
                    rn++
                }
            }
        }
        if (ln == 0 && rn == 0) {
            row.fill(fallback)
        } else {
            val lColorR = if (ln > 0) lr / ln else rr / rn
            val lColorG = if (ln > 0) lg / ln else rg / rn
            val lColorB = if (ln > 0) lb / ln else rb / rn
            val rColorR = if (rn > 0) rr / rn else lColorR
            val rColorG = if (rn > 0) rg / rn else lColorG
            val rColorB = if (rn > 0) rb / rn else lColorB
            for (x in 0 until width) {
                val t = (x + 1f) / (width + 1f)
                val r = (lColorR + (rColorR - lColorR) * t).roundToInt()
                val g = (lColorG + (rColorG - lColorG) * t).roundToInt()
                val b = (lColorB + (rColorB - lColorB) * t).roundToInt()
                row[x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        mutable.setPixels(row, 0, width, left, y, width, 1)
    }
    return mutable
}
