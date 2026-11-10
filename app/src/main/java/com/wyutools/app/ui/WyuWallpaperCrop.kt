package com.wyutools.app.ui

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Text

private const val MaxCropZoom = 6f
private val CropBackground = Color(0xFF101010)
private val CropAccent = Color(0xFF8AB4FF)

/**
 * Full-screen crop editor: the viewport keeps the physical screen aspect so the
 * saved wallpaper is exactly what is shown, while the image can be dragged and
 * pinch-zoomed underneath it.
 */
@Composable
fun WyuWallpaperCropDialog(
    bitmap: Bitmap,
    onCancel: () -> Unit,
    onConfirm: (Rect) -> Unit,
) {
    val context = LocalContext.current
    val metrics = remember(context) { context.resources.displayMetrics }
    val screenAspect = metrics.widthPixels.toFloat() / metrics.heightPixels.toFloat()
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val density = LocalDensity.current
    val view = LocalView.current

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val window = (view.parent as? DialogWindowProvider)?.window
        DisposableEffect(window) {
            val controller = window?.let { WindowCompat.getInsetsController(it, view) }
            controller?.isAppearanceLightStatusBars = false
            controller?.isAppearanceLightNavigationBars = false
            onDispose {}
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CropBackground),
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding(),
            ) {
                val toolbarHeight = 56.dp
                val sidePadding = 18.dp
                val widthPx = with(density) { maxWidth.toPx() }
                val heightPx = with(density) { maxHeight.toPx() }
                val toolbarPx = with(density) { toolbarHeight.toPx() }
                val sidePx = with(density) { sidePadding.toPx() }
                val areaWidth = (widthPx - sidePx * 2f).coerceAtLeast(1f)
                val areaHeight = (heightPx - toolbarPx).coerceAtLeast(1f)
                val viewportWidth = min(areaWidth, areaHeight * screenAspect)
                val viewportHeight = viewportWidth / screenAspect
                val viewportCenter = Offset(
                    widthPx / 2f,
                    toolbarPx + areaHeight / 2f,
                )
                val coverScale = max(
                    viewportWidth / bitmap.width,
                    viewportHeight / bitmap.height,
                )

                var zoom by remember(viewportWidth, viewportHeight, bitmap) {
                    mutableFloatStateOf(1f)
                }
                var pan by remember(viewportWidth, viewportHeight, bitmap) {
                    mutableStateOf(Offset.Zero)
                }

                fun clampPan(value: Offset, zoomValue: Float): Offset {
                    val scale = coverScale * zoomValue
                    val halfX = ((bitmap.width * scale - viewportWidth) / 2f).coerceAtLeast(0f)
                    val halfY = ((bitmap.height * scale - viewportHeight) / 2f).coerceAtLeast(0f)
                    return Offset(
                        value.x.coerceIn(-halfX, halfX),
                        value.y.coerceIn(-halfY, halfY),
                    )
                }

                fun sourceRect(): Rect {
                    val scale = coverScale * zoom
                    val imageLeft = viewportCenter.x - bitmap.width * scale / 2f + pan.x
                    val imageTop = viewportCenter.y - bitmap.height * scale / 2f + pan.y
                    val vpLeft = viewportCenter.x - viewportWidth / 2f
                    val vpTop = viewportCenter.y - viewportHeight / 2f
                    val left = ((vpLeft - imageLeft) / scale).roundToInt()
                        .coerceIn(0, bitmap.width - 1)
                    val top = ((vpTop - imageTop) / scale).roundToInt()
                        .coerceIn(0, bitmap.height - 1)
                    val width = (viewportWidth / scale).roundToInt()
                        .coerceIn(1, bitmap.width - left)
                    val height = (viewportHeight / scale).roundToInt()
                        .coerceIn(1, bitmap.height - top)
                    return Rect(left, top, left + width, top + height)
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .pointerInput(bitmap, viewportWidth, viewportHeight, coverScale) {
                            detectTransformGestures { centroid, gesturePan, gestureZoom, _ ->
                                val oldZoom = zoom
                                val newZoom =
                                    (oldZoom * gestureZoom).coerceIn(1f, MaxCropZoom)
                                val ratio = newZoom / oldZoom
                                val anchor = centroid - viewportCenter
                                val moved = anchor + (pan - anchor) * ratio + gesturePan
                                pan = clampPan(moved, newZoom)
                                zoom = newZoom
                            }
                        },
                ) {
                    val scale = coverScale * zoom
                    val drawnWidth = bitmap.width * scale
                    val drawnHeight = bitmap.height * scale
                    val imageLeft = viewportCenter.x - drawnWidth / 2f + pan.x
                    val imageTop = viewportCenter.y - drawnHeight / 2f + pan.y
                    drawImage(
                        image = image,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(bitmap.width, bitmap.height),
                        dstOffset = IntOffset(
                            imageLeft.roundToInt(),
                            imageTop.roundToInt(),
                        ),
                        dstSize = IntSize(
                            drawnWidth.roundToInt().coerceAtLeast(1),
                            drawnHeight.roundToInt().coerceAtLeast(1),
                        ),
                        filterQuality = FilterQuality.Medium,
                    )

                    val vpLeft = viewportCenter.x - viewportWidth / 2f
                    val vpTop = viewportCenter.y - viewportHeight / 2f
                    val vpRight = vpLeft + viewportWidth
                    val vpBottom = vpTop + viewportHeight
                    val dim = Color.Black.copy(alpha = 0.55f)
                    drawRect(dim, Offset.Zero, Size(size.width, vpTop))
                    drawRect(
                        dim,
                        Offset(0f, vpBottom),
                        Size(size.width, size.height - vpBottom),
                    )
                    drawRect(dim, Offset(0f, vpTop), Size(vpLeft, viewportHeight))
                    drawRect(
                        dim,
                        Offset(vpRight, vpTop),
                        Size(size.width - vpRight, viewportHeight),
                    )
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.9f),
                        topLeft = Offset(vpLeft, vpTop),
                        size = Size(viewportWidth, viewportHeight),
                        cornerRadius = CornerRadius(14.dp.toPx()),
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                    val grid = Color.White.copy(alpha = 0.22f)
                    for (i in 1..2) {
                        val x = vpLeft + viewportWidth * i / 3f
                        drawLine(grid, Offset(x, vpTop), Offset(x, vpBottom), 1f)
                        val y = vpTop + viewportHeight * i / 3f
                        drawLine(grid, Offset(vpLeft, y), Offset(vpRight, y), 1f)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(toolbarHeight)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "取消",
                        style = TextStyle(fontSize = 16.sp, color = Color.White),
                        modifier = Modifier
                            .clickable(onClick = hapticClick(action = onCancel))
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "裁切壁纸",
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.9f),
                        ),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "完成",
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = CropAccent,
                        ),
                        modifier = Modifier
                            .clickable { onConfirm(sourceRect()) }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                }
                Text(
                    text = "双指缩放 · 拖动调整位置",
                    style = TextStyle(
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.55f),
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 18.dp),
                )
            }
        }
    }
}
