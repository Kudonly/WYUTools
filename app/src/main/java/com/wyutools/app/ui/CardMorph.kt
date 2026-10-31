package com.wyutools.app.ui

import android.app.Activity
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.RoundedCorner
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.wyutools.app.SharedCardSnapshot
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A sharper S-curve than Material's standard FastOutSlowIn: it holds longer near
 * both ends, so the speed-up and slow-down are more pronounced. It is the only
 * mapping applied to the morph: geometry, titles and fades all run on this one
 * progress value, so motion stays continuous for the whole duration.
 */
private val MorphEasing = CubicBezierEasing(0.65f, 0f, 0.03f, 1f)
private const val MorphDuration = 400

/** A stalled frame (e.g. the destination's first composition) may cost the
 *  morph at most this much progress, so it slows down instead of skipping. */
private const val MorphMaxFrameStepMs = 34f

private suspend fun Animatable<Float, AnimationVector1D>.animateMorph(
    target: Float,
) {
    val from = value
    val distance = abs(target - from)
    if (distance <= 0.001f) return
    val totalMs = (MorphDuration * distance).coerceAtLeast(1f)
    var lastFrameNanos = 0L
    var elapsedMs = 0f
    while (elapsedMs < totalMs) {
        val frameNanos = withFrameNanos { it }
        if (lastFrameNanos != 0L) {
            val deltaMs = (frameNanos - lastFrameNanos) / 1_000_000f
            elapsedMs += deltaMs.coerceIn(0f, MorphMaxFrameStepMs)
            val eased = MorphEasing.transform((elapsedMs / totalMs).coerceIn(0f, 1f))
            snapTo(from + (target - from) * eased)
        }
        lastFrameNanos = frameNanos
    }
}

private fun smoothstep(edge0: Float, edge1: Float, value: Float): Float {
    val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

fun capturePageBitmap(activity: Activity, onCaptured: (Bitmap?) -> Unit) {
    val window = activity.window
    val view = window.decorView
    val width = view.width
    val height = view.height
    if (width <= 0 || height <= 0) {
        onCaptured(null)
        return
    }
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    PixelCopy.request(
        window,
        bitmap,
        { result -> onCaptured(if (result == PixelCopy.SUCCESS) bitmap else null) },
        Handler(Looper.getMainLooper()),
    )
}

/** Small, box-blurred copy stretched back to full screen as a cheap backdrop blur. */
fun createBlurBackdrop(bitmap: Bitmap, scale: Int = 8): Bitmap {
    val width = (bitmap.width / scale).coerceAtLeast(1)
    val height = (bitmap.height / scale).coerceAtLeast(1)
    val small = Bitmap.createScaledBitmap(bitmap, width, height, true)
    val pixels = IntArray(width * height)
    small.getPixels(pixels, 0, width, 0, 0, width, height)
    boxBlurPass(pixels, width, height, radius = 2, vertical = false)
    boxBlurPass(pixels, width, height, radius = 2, vertical = true)
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}

private fun boxBlurPass(
    pixels: IntArray,
    width: Int,
    height: Int,
    radius: Int,
    vertical: Boolean,
) {
    val lineLength = if (vertical) height else width
    val lineCount = if (vertical) width else height
    if (lineLength == 0 || lineCount == 0) return
    val window = 2 * radius + 1
    val line = IntArray(lineLength)
    fun offset(pos: Int): Int = if (vertical) pos * width else pos
    for (index in 0 until lineCount) {
        val base = if (vertical) index else index * width
        for (pos in 0 until lineLength) {
            line[pos] = pixels[base + offset(pos)]
        }
        var a = 0
        var r = 0
        var g = 0
        var b = 0
        for (i in -radius..radius) {
            val p = line[i.coerceIn(0, lineLength - 1)]
            a += p ushr 24
            r += (p shr 16) and 0xFF
            g += (p shr 8) and 0xFF
            b += p and 0xFF
        }
        for (pos in 0 until lineLength) {
            pixels[base + offset(pos)] = ((a / window) shl 24) or
                ((r / window) shl 16) or
                ((g / window) shl 8) or
                (b / window)
            val out = line[(pos - radius).coerceIn(0, lineLength - 1)]
            val into = line[(pos + radius + 1).coerceIn(0, lineLength - 1)]
            a += (into ushr 24) - (out ushr 24)
            r += ((into shr 16) and 0xFF) - ((out shr 16) and 0xFF)
            g += ((into shr 8) and 0xFF) - ((out shr 8) and 0xFF)
            b += (into and 0xFF) - (out and 0xFF)
        }
    }
}

@Composable
private fun rememberViewLocation(): IntArray {
    val view = LocalView.current
    return remember(view) { IntArray(2).also(view::getLocationOnScreen) }
}

/** The physical display corner radius, so full-screen containers match the device body. */
@Composable
private fun rememberScreenCornerRadiusPx(): Float {
    val view = LocalView.current
    return remember(view) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            0f
        } else {
            runCatching {
                val display = view.display ?: return@runCatching 0f
                val positions = intArrayOf(
                    RoundedCorner.POSITION_TOP_LEFT,
                    RoundedCorner.POSITION_TOP_RIGHT,
                    RoundedCorner.POSITION_BOTTOM_LEFT,
                    RoundedCorner.POSITION_BOTTOM_RIGHT,
                )
                positions.maxOfOrNull { display.getRoundedCorner(it)?.radius ?: 0 }
                    ?.toFloat() ?: 0f
            }.getOrDefault(0f)
        }
    }
}

@Composable
private fun rememberStartBounds(snapshot: SharedCardSnapshot): Rect {
    val location = rememberViewLocation()
    return remember(snapshot) {
        Rect(
            snapshot.bounds.left.toFloat() - location[0],
            snapshot.bounds.top.toFloat() - location[1],
            snapshot.bounds.right.toFloat() - location[0],
            snapshot.bounds.bottom.toFloat() - location[1],
        )
    }
}

private fun Rect.lerpTo(target: Rect, fraction: Float): Rect = Rect(
    left + (target.left - left) * fraction,
    top + (target.top - top) * fraction,
    right + (target.right - right) * fraction,
    bottom + (target.bottom - bottom) * fraction,
)

private fun roundedPath(rect: Rect, radius: Float): Path = Path().apply {
    addRoundRect(
        RoundRect(
            rect.left,
            rect.top,
            rect.right,
            rect.bottom,
            CornerRadius(radius, radius),
        ),
    )
}

private fun DrawScope.drawImageContained(
    image: ImageBitmap,
    rect: Rect,
    alpha: Float,
    scaleBoost: Float = 1f,
) {
    if (alpha <= 0f || rect.width <= 0f || rect.height <= 0f) return
    val scale = min(rect.width / image.width, rect.height / image.height) * scaleBoost
    val width = image.width * scale
    val height = image.height * scale
    drawImage(
        image = image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset(
            (rect.center.x - width / 2f).roundToInt(),
            (rect.center.y - height / 2f).roundToInt(),
        ),
        dstSize = IntSize(width.roundToInt(), height.roundToInt()),
        alpha = alpha,
        filterQuality = FilterQuality.Medium,
    )
}

/** Maps the whole image onto the whole screen, so the sharp and blurred copies align exactly. */
private fun DrawScope.drawImageStretched(image: ImageBitmap, alpha: Float) {
    if (alpha <= 0f) return
    drawImage(
        image = image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        alpha = alpha,
        filterQuality = FilterQuality.Medium,
    )
}

private fun DrawScope.drawImageIntoTarget(image: ImageBitmap, rect: Rect, alpha: Float) {
    if (alpha <= 0f || rect.width <= 0f || rect.height <= 0f) return
    drawImage(
        image = image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
        dstSize = IntSize(rect.width.roundToInt(), rect.height.roundToInt()),
        alpha = alpha,
        filterQuality = FilterQuality.Medium,
    )
}

/**
 * Opens the card into the page: the destination is visible through a growing
 * rounded window from the first frame, while the home backdrop dims and blurs.
 */
@Composable
fun CardMorphOverlay(
    snapshot: SharedCardSnapshot,
    sharpHome: Bitmap?,
    homeBackdrop: Bitmap?,
    cancelRequested: Boolean,
    onCovered: () -> Unit,
    onFinished: () -> Unit,
    onCancelled: () -> Unit,
) {
    val expand = remember(snapshot, sharpHome, homeBackdrop) { Animatable(0f) }
    // Survives effect restarts, so a reversal never re-runs the navigation step.
    val started = remember(snapshot, sharpHome, homeBackdrop) { booleanArrayOf(false) }
    val cardImage = remember(snapshot) { snapshot.bitmap.asImageBitmap() }
    val sharpImage = remember(sharpHome) { sharpHome?.asImageBitmap() }
    val backdropImage = remember(homeBackdrop) { homeBackdrop?.asImageBitmap() }
    val start = rememberStartBounds(snapshot)
    val screenRadius = rememberScreenCornerRadiusPx()
    val surface = MiuixTheme.colorScheme.surface

    LaunchedEffect(snapshot, sharpHome, homeBackdrop, cancelRequested) {
        if (!started[0]) {
            started[0] = true
            withFrameNanos { }
            onCovered()
        }
        val target = if (cancelRequested) 0f else 1f
        expand.animateMorph(target)
        if (cancelRequested) onCancelled() else onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val progress = expand.value
            // Geometry follows the eased progress directly; no second curve on top.
            val hole = Rect(
                lerp(start.left, 0f, progress),
                lerp(start.top, 0f, progress),
                lerp(start.right, size.width, progress),
                lerp(start.bottom, size.height, progress),
            )
            // The window opens up to the device's own corner radius.
            val radius = if (screenRadius > 0f) {
                lerp(snapshot.radiusPx, screenRadius, progress)
            } else {
                snapshot.radiusPx * (1f - progress)
            }
            val holePath = roundedPath(hole, radius)

            clipPath(holePath, ClipOp.Difference) {
                when {
                    sharpImage != null -> {
                        drawImageStretched(sharpImage, 1f)
                        if (backdropImage != null) {
                            drawImageStretched(backdropImage, progress)
                        }
                    }
                    backdropImage != null -> drawImageStretched(backdropImage, 1f)
                    else -> drawRect(surface)
                }
                val dim = 0.19f * progress
                if (dim > 0f) {
                    drawRect(Color.Black.copy(alpha = dim))
                }
            }

            // The card stays put while the page window grows out from under it.
            val cardAlpha = 1f - smoothstep(0.06f, 0.42f, progress)
            if (cardAlpha > 0f && start.width > 0f && start.height > 0f) {
                drawImage(
                    image = cardImage,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(cardImage.width, cardImage.height),
                    dstOffset = IntOffset(start.left.roundToInt(), start.top.roundToInt()),
                    dstSize = IntSize(start.width.roundToInt(), start.height.roundToInt()),
                    alpha = cardAlpha,
                    filterQuality = FilterQuality.Medium,
                )
            }
        }
        FlightTitle(
            snapshot = snapshot,
            progress = { expand.value },
            forward = true,
        )
    }
}

/**
 * Returns to the card: the page snapshot shrinks onto the originating card
 * while the cached home backdrop sharpens and undims in step with the sheet,
 * so by the time the card lands the background is already the live home.
 *
 * The page snapshot arrives a few frames after the overlay: until then the
 * overlay only shows the flying title while the live page hides its own, so
 * the hand-off is invisible and the snapshot never has to be retouched.
 */
@Composable
fun CardReturnOverlay(
    snapshot: SharedCardSnapshot,
    page: Bitmap?,
    homeBackdrop: Bitmap?,
    sharpHome: Bitmap?,
    reopenRequested: Boolean,
    onTap: () -> Unit,
    onSwap: () -> Unit,
    onReopened: () -> Unit,
) {
    val raw = remember(snapshot, page, homeBackdrop) { Animatable(0f) }
    val cardImage = remember(snapshot) { snapshot.bitmap.asImageBitmap() }
    val backdropImage = remember(homeBackdrop) { homeBackdrop?.asImageBitmap() }
    val sharpImage = remember(sharpHome) { sharpHome?.asImageBitmap() }
    val pageImage = remember(page) { page?.asImageBitmap() }
    val target = rememberStartBounds(snapshot)
    val screenRadius = rememberScreenCornerRadiusPx()
    val surface = MiuixTheme.colorScheme.surface

    LaunchedEffect(snapshot, page, homeBackdrop, sharpHome, reopenRequested) {
        if (page == null) return@LaunchedEffect
        val animateTarget = if (reopenRequested) 0f else 1f
        raw.animateMorph(animateTarget)
        if (reopenRequested) {
            // The page stayed composed underneath, so it can simply be handed back.
            onReopened()
        } else {
            // The sheet has landed and the backdrop already matches the live
            // home, so the swap happens under pixels that look unchanged.
            onSwap()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (page != null && raw.value < 0.999f) onTap() },
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val image = pageImage ?: return@Canvas
            val fraction = raw.value
            // Same single curve as opening, so close mirrors open exactly.
            val fullscreen = Rect(0f, 0f, size.width, size.height)
            val rect = fullscreen.lerpTo(target, fraction)
            // Full screen starts at the device's corner radius, landing on the card's.
            val radius = lerp(
                if (screenRadius > 0f) screenRadius else 0f,
                snapshot.radiusPx,
                fraction,
            )
            val path = roundedPath(rect, radius)

            // The sharp home snapshot sits underneath the cached blur, and the
            // blur dissolves into it across the same progress that shrinks the
            // sheet, so the background never lags behind the container.
            if (sharpImage != null) {
                drawImageStretched(sharpImage, 1f)
            } else if (backdropImage != null) {
                drawImageStretched(backdropImage, 1f)
            } else {
                drawRect(surface)
            }
            if (backdropImage != null) {
                val blurAlpha = if (sharpImage != null) {
                    1f - smoothstep(0.22f, 0.9f, fraction)
                } else {
                    1f
                }
                if (blurAlpha > 0.001f) {
                    drawImageStretched(backdropImage, blurAlpha)
                    drawRect(Color.Black.copy(alpha = 0.16f * blurAlpha))
                }
            }

            // The page dissolves into the sheet early, while the sheet itself
            // keeps shrinking all the way onto the card: the landing is the
            // last thing to happen instead of a static tail.
            val pageAlpha = 1f - smoothstep(0.10f, 0.42f, fraction)
            val sheetAlpha = 1f - smoothstep(0.78f, 1f, fraction)
            val cardAlpha = smoothstep(0.72f, 0.96f, fraction)

            if (sheetAlpha > 0f) {
                clipPath(path) {
                    drawRect(
                        color = surface,
                        topLeft = rect.topLeft,
                        size = rect.size,
                        alpha = sheetAlpha,
                    )
                    drawImageIntoTarget(image, rect, pageAlpha * sheetAlpha)
                }
            }
            if (cardAlpha > 0f && target.width > 0f && target.height > 0f) {
                drawImageIntoTarget(cardImage, target, cardAlpha)
            }
        }
        FlightTitle(
            snapshot = snapshot,
            progress = { raw.value },
            forward = false,
        )
    }
}

@Composable
private fun FlightTitle(
    snapshot: SharedCardSnapshot,
    progress: () -> Float,
    forward: Boolean,
) {
    val title = snapshot.title ?: return
    val rawTitleBounds = snapshot.titleBounds ?: return
    val location = rememberViewLocation()
    val density = LocalDensity.current
    val surfaceTop = with(density) {
        WindowInsets.statusBars.getTop(this).toFloat()
    }
    val startBounds = remember(rawTitleBounds) {
        Rect(
            rawTitleBounds.left.toFloat() - location[0],
            rawTitleBounds.top.toFloat() - location[1],
            rawTitleBounds.right.toFloat() - location[0],
            rawTitleBounds.bottom.toFloat() - location[1],
        )
    }
    val targetBounds = remember(density, surfaceTop) {
        val left = with(density) { 28.dp.toPx() }
        Rect(left, surfaceTop + with(density) { 56.dp.toPx() }, left, surfaceTop)
    }
    val startFontPx = snapshot.titleFontSizePx.takeIf { it > 0f }
        ?: with(density) { 22.sp.toPx() }
    val startLinePx = snapshot.titleLineHeightPx.takeIf { it > 0f }
        ?: startFontPx * 1.25f
    val targetFontPx = with(density) { 32.sp.toPx() }
    val targetLinePx = with(density) { 40.sp.toPx() }

    val progressValue = progress()
    // The title rides the same eased progress as the container, so it lands with it.
    val fraction = progressValue
    val from = if (forward) startBounds else targetBounds
    val to = if (forward) targetBounds else startBounds
    val fromFont = if (forward) startFontPx else targetFontPx
    val toFont = if (forward) targetFontPx else startFontPx
    val fromLine = if (forward) startLinePx else targetLinePx
    val toLine = if (forward) targetLinePx else startLinePx

    val x = lerp(from.left, to.left, fraction)
    val y = lerp(from.top, to.top, fraction)
    val fontPx = lerp(fromFont, toFont, fraction)
    val linePx = lerp(fromLine, toLine, fraction)

    Text(
        text = title,
        maxLines = 1,
        style = TextStyle(
            fontSize = with(density) { fontPx.toSp() },
            lineHeight = with(density) { linePx.toSp() },
            fontWeight = if (if (forward) fraction > 0.66f else fraction < 0.34f) {
                FontWeight.SemiBold
            } else {
                FontWeight.Medium
            },
            letterSpacing = (if (forward) lerp(0f, -0.2f, fraction) else lerp(-0.2f, 0f, fraction)).sp,
            color = MiuixTheme.colorScheme.onSurface,
        ),
        modifier = Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) },
    )
}
