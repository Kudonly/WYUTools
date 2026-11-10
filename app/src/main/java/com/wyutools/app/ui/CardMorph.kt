package com.wyutools.app.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Shader
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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
private const val MorphDuration = 460

/** A stalled frame (e.g. the destination's first composition) may cost the
 *  morph at most this much progress, so it slows down instead of skipping. */
private const val MorphMaxFrameStepMs = 34f

/** Matches the depth of iOS Control Center: the home screen pulls back and darkens. */
private const val MorphBackdropDim = 0.30f
private const val MorphBackdropScale = 0.08f

/** The fixed corner band of the backdrop's contentsCenter. It matches half of
 *  the shrink, so the centre absorbs the pullback while the outer border and
 *  screen corners keep their original pixels. */
private const val MorphBackdropCenterInset = 0.04f

private suspend fun Animatable<Float, AnimationVector1D>.animateMorph(
    target: Float,
    onProgress: (Float) -> Unit = {},
) {
    val from = value
    onProgress(from)
    val distance = abs(target - from)
    if (distance <= 0.001f) {
        snapTo(target)
        onProgress(target)
        return
    }
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
            onProgress(value)
        }
        lastFrameNanos = frameNanos
    }
    snapTo(target)
    onProgress(target)
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

/**
 * Scales the backdrop with CALayer's contentsCenter semantics: the four
 * corners keep their original size, the edges stretch on one axis and the
 * centre stretches on both. FilterQuality.Low is Core Animation's default
 * linear filter, which keeps the resampled wallpaper smooth.
 */
private fun DrawScope.drawImageScaledWithContentsCenter(
    image: ImageBitmap,
    scale: Float,
    alpha: Float,
) {
    if (alpha <= 0f || scale <= 0f) return
    val dstWidth = size.width * scale
    val dstHeight = size.height * scale
    val dstLeft = ((size.width - dstWidth) / 2f).roundToInt()
    val dstTop = ((size.height - dstHeight) / 2f).roundToInt()
    val dstRight = (dstLeft + dstWidth).roundToInt()
    val dstBottom = (dstTop + dstHeight).roundToInt()
    val insetX = (image.width * MorphBackdropCenterInset).roundToInt()
        .coerceIn(0, image.width / 2)
    val insetY = (image.height * MorphBackdropCenterInset).roundToInt()
        .coerceIn(0, image.height / 2)
    if (insetX * 2 >= dstRight - dstLeft || insetY * 2 >= dstBottom - dstTop) {
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset(dstLeft, dstTop),
            dstSize = IntSize(dstRight - dstLeft, dstBottom - dstTop),
            alpha = alpha,
            filterQuality = FilterQuality.Low,
        )
        return
    }
    val srcX = intArrayOf(0, insetX, image.width - insetX, image.width)
    val srcY = intArrayOf(0, insetY, image.height - insetY, image.height)
    val dstX = intArrayOf(dstLeft, dstLeft + insetX, dstRight - insetX, dstRight)
    val dstY = intArrayOf(dstTop, dstTop + insetY, dstBottom - insetY, dstBottom)
    for (row in 0..2) {
        for (column in 0..2) {
            val srcWidth = srcX[column + 1] - srcX[column]
            val srcHeight = srcY[row + 1] - srcY[row]
            if (srcWidth <= 0 || srcHeight <= 0) continue
            drawImage(
                image = image,
                srcOffset = IntOffset(srcX[column], srcY[row]),
                srcSize = IntSize(srcWidth, srcHeight),
                dstOffset = IntOffset(dstX[column], dstY[row]),
                dstSize = IntSize(
                    dstX[column + 1] - dstX[column],
                    dstY[row + 1] - dstY[row],
                ),
                alpha = alpha,
                filterQuality = FilterQuality.Low,
            )
        }
    }
}

private data class MorphHole(val rect: Rect, val radius: Float)

private fun morphHole(
    progress: Float,
    start: Rect,
    width: Float,
    height: Float,
    startRadius: Float,
    screenRadius: Float,
): MorphHole {
    val rect = Rect(
        lerp(start.left, 0f, progress),
        lerp(start.top, 0f, progress),
        lerp(start.right, width, progress),
        lerp(start.bottom, height, progress),
    )
    val radius = if (screenRadius > 0f) {
        lerp(startRadius, screenRadius, progress)
    } else {
        startRadius * (1f - progress)
    }
    return MorphHole(rect, radius)
}

/**
 * The morph backdrop draws the sharp home capture pulled back each frame: the
 * nine-slice contentsCenter keeps the screen border pixel-exact while the
 * mirrored edge band fills the exposed strip. The pull is read in the draw
 * phase, so frames redraw without recomposition and no per-frame blur runs.
 */
@Composable
private fun MorphBackdrop(
    image: ImageBitmap?,
    bitmap: Bitmap?,
    strength: () -> Float,
    modifier: Modifier = Modifier,
) {
    val surface = MiuixTheme.colorScheme.surface
    val mirrorShader = remember(bitmap) {
        bitmap?.let { BitmapShader(it, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR) }
    }
    val mirrorBrush = remember(mirrorShader) {
        mirrorShader?.let { ShaderBrush(it) }
    }
    val matrix = remember(bitmap) { Matrix() }
    val bandPath = remember { Path() }
    Canvas(modifier.fillMaxSize()) {
        // Read in the draw phase: the backdrop redraws each frame without
        // recomposing.
        val pull = strength()
        if (image == null || bitmap == null || mirrorShader == null || mirrorBrush == null) {
            drawRect(surface)
            return@Canvas
        }
        drawMorphBackdropContent(
            image = image,
            bitmap = bitmap,
            mirrorShader = mirrorShader,
            mirrorBrush = mirrorBrush,
            matrix = matrix,
            bandPath = bandPath,
            pull = pull,
        )
    }
}

private fun DrawScope.drawMorphBackdropContent(
    image: ImageBitmap,
    bitmap: Bitmap,
    mirrorShader: BitmapShader,
    mirrorBrush: ShaderBrush,
    matrix: Matrix,
    bandPath: Path,
    pull: Float,
) {
    val scale = 1f - MorphBackdropScale * pull
    val dstWidth = size.width * scale
    val dstHeight = size.height * scale
    val dstLeft = (size.width - dstWidth) / 2f
    val dstTop = (size.height - dstHeight) / 2f
    matrix.setScale(dstWidth / bitmap.width, dstHeight / bitmap.height)
    matrix.postTranslate(dstLeft, dstTop)
    mirrorShader.setLocalMatrix(matrix)
    // The sharp nine-slice paints over the pulled-back centre, so the mirrored
    // fill only covers the exposed band; a 1px overlap hides the seam.
    if (dstWidth < size.width - 0.5f || dstHeight < size.height - 0.5f) {
        bandPath.reset()
        bandPath.addRect(
            Rect(dstLeft + 1f, dstTop + 1f, dstLeft + dstWidth - 1f, dstTop + dstHeight - 1f),
        )
        clipPath(bandPath, ClipOp.Difference) {
            drawRect(brush = mirrorBrush)
        }
    }
    drawImageScaledWithContentsCenter(image, scale, 1f)
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
 * Opens the card into the page: the card preview stretches with the growing
 * rounded window and dissolves into the destination underneath it, while the
 * home backdrop dims and blurs, so the card and the page stay one surface.
 */
@Composable
fun CardMorphOverlay(
    snapshot: SharedCardSnapshot,
    sharpHome: Bitmap?,
    pageBlur: Bitmap?,
    cancelRequested: Boolean,
    modifier: Modifier = Modifier,
    onCovered: () -> Unit,
    onFinished: () -> Unit,
    onCancelled: () -> Unit,
) {
    val expand = remember(snapshot, sharpHome) { Animatable(0f) }
    // Survives effect restarts, so a reversal never re-runs the navigation step.
    val started = remember(snapshot, sharpHome) { booleanArrayOf(false) }
    val cardImage = remember(snapshot) { snapshot.bitmap.asImageBitmap() }
    val sharpImage = remember(sharpHome) { sharpHome?.asImageBitmap() }
    val pageBlurImage = remember(pageBlur) { pageBlur?.asImageBitmap() }
    val start = rememberStartBounds(snapshot)
    val screenRadius = rememberScreenCornerRadiusPx()
    // The cancelled morph stays up until the page has left the host; once the
    // reverse animation has landed it must no longer swallow touches, so the
    // home is usable on the very next frame even while the overlay is fading out.
    var landed by remember(snapshot) { mutableStateOf(false) }

    LaunchedEffect(snapshot, sharpHome, cancelRequested) {
        if (!started[0]) {
            started[0] = true
            withFrameNanos { }
            onCovered()
        }
        val target = if (cancelRequested) 0f else 1f
        expand.animateMorph(target) { WyuMorphPageState.contentProgress = it }
        if (cancelRequested) {
            landed = true
            onCancelled()
        } else {
            onFinished()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(modifier)
            .then(
                if (landed) {
                    Modifier
                } else {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                },
            ),
    ) {
        // The live page shows through the growing hole, so the backdrop is
        // clipped outside it after its real-time blur has been rendered.
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    val hole = morphHole(
                        progress = expand.value,
                        start = start,
                        width = size.width,
                        height = size.height,
                        startRadius = snapshot.radiusPx,
                        screenRadius = screenRadius,
                    )
                    clipPath(roundedPath(hole.rect, hole.radius), ClipOp.Difference) {
                        this@drawWithContent.drawContent()
                    }
                },
        ) {
            MorphBackdrop(
                image = sharpImage,
                bitmap = sharpHome,
                strength = { expand.value },
            )
        }
        Canvas(Modifier.fillMaxSize()) {
            val progress = expand.value
            val hole = morphHole(
                progress = progress,
                start = start,
                width = size.width,
                height = size.height,
                startRadius = snapshot.radiusPx,
                screenRadius = screenRadius,
            )
            val holePath = roundedPath(hole.rect, hole.radius)

            val dim = MorphBackdropDim * expand.value
            if (dim > 0f) {
                clipPath(holePath, ClipOp.Difference) {
                    drawRect(Color.Black.copy(alpha = dim))
                }
            }

            // Reversing the open morph blurs the page as its window closes,
            // matching the normal return instead of snapping back sharp.
            if (pageBlurImage != null && cancelRequested) {
                val blurAlpha = smoothstep(0.05f, 0.75f, 1f - progress)
                if (blurAlpha > 0.001f) {
                    clipPath(holePath) {
                        drawImageStretched(pageBlurImage, blurAlpha)
                    }
                }
            }

            // The card is the window: it rides the growing hole as a stretched
            // preview and dissolves into the page behind it, so the card opens
            // into the page instead of fading in place.
            val cardAlpha = 1f - smoothstep(0.15f, 0.7f, progress)
            if (cardAlpha > 0f && hole.rect.width > 0f && hole.rect.height > 0f) {
                clipPath(holePath) {
                    drawImageIntoTarget(cardImage, hole.rect, cardAlpha)
                }
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
 * Returns to the card: the window condenses the page snapshot back into the
 * originating card while the home backdrop grows, sharpens and undims from the
 * state the open morph left it in, so by the time the card lands the background
 * is already the live home.
 *
 * The page itself has already left the host when the return starts: drawing
 * the window from the page snapshot lets home compose (and lay out) during the
 * whole animation, so it is interactive the instant the window lands instead
 * of only after the swap. The live page below is never needed, so the snapshot
 * has to be opaque from the first frame on.
 */
@Composable
fun CardReturnOverlay(
    snapshot: SharedCardSnapshot,
    page: Bitmap?,
    pageBlur: Bitmap?,
    sharpHome: Bitmap?,
    reopenRequested: Boolean,
    onTap: () -> Unit,
    onSwap: () -> Unit,
    onReopened: () -> Unit,
) {
    val raw = remember(snapshot, page) { Animatable(0f) }
    val cardImage = remember(snapshot) { snapshot.bitmap.asImageBitmap() }
    val sharpImage = remember(sharpHome) { sharpHome?.asImageBitmap() }
    val pageImage = remember(page) { page?.asImageBitmap() }
    val pageBlurImage = remember(pageBlur) { pageBlur?.asImageBitmap() }
    val target = rememberStartBounds(snapshot)
    val screenRadius = rememberScreenCornerRadiusPx()
    val density = LocalDensity.current
    val liftPx = with(density) { WyuMorphPageState.ContentLift.toPx() }
    // Once the window has landed the overlay only waits for the page swap.
    // It must stop intercepting touches right then: the home behind it is the
    // real page and has to respond immediately, not after the overlay is gone.
    var landed by remember(snapshot) { mutableStateOf(false) }

    LaunchedEffect(snapshot, page, sharpHome, reopenRequested) {
        if (page == null) return@LaunchedEffect
        val animateTarget = if (reopenRequested) 0f else 1f
        raw.animateMorph(animateTarget) {
            WyuMorphPageState.contentProgress = 1f - it
        }
        if (reopenRequested) {
            // The page stayed composed underneath, so it can simply be handed back.
            onReopened()
        } else {
            landed = true
            // The window has landed and the backdrop already matches the live
            // home, so the swap happens under pixels that look unchanged.
            onSwap()
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (page == null) {
            // The snapshot is not ready yet, so the return has not visibly
            // begun and the page that is about to leave would still be
            // operated: hold touches until the window appears.
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false).consume()
                        }
                    },
            )
        }
        // The live page shows through the shrinking window, exactly as it does
        // while opening; until the snapshot is ready nothing is covered.
        if (page != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        val hole = morphHole(
                            progress = 1f - raw.value,
                            start = target,
                            width = size.width,
                            height = size.height,
                            startRadius = snapshot.radiusPx,
                            screenRadius = screenRadius,
                        )
                        clipPath(roundedPath(hole.rect, hole.radius), ClipOp.Difference) {
                            this@drawWithContent.drawContent()
                        }
                    },
            ) {
                MorphBackdrop(
                    image = sharpImage,
                    bitmap = sharpHome,
                    strength = { 1f - raw.value },
                )
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val pageBitmap = pageImage ?: return@Canvas
            val fraction = raw.value
            // Same single curve as opening, so close mirrors open exactly.
            val hole = morphHole(
                progress = 1f - fraction,
                start = target,
                width = size.width,
                height = size.height,
                startRadius = snapshot.radiusPx,
                screenRadius = screenRadius,
            )
            val holePath = roundedPath(hole.rect, hole.radius)

            val dim = MorphBackdropDim * (1f - fraction)
            if (dim > 0f) {
                clipPath(holePath, ClipOp.Difference) {
                    drawRect(Color.Black.copy(alpha = dim))
                }
            }

            // The window is filled by the page snapshot, never by the live
            // page; the two alphas sum to one so home never shows through.
            // The sharp copy dissolves into the blurred one, which sinks and
            // lowers as the window closes.
            val blur = pageBlurImage
            val blurAlpha = if (blur != null) smoothstep(0.04f, 0.78f, fraction) else 0f
            clipPath(holePath) {
                drawImageStretched(pageBitmap, 1f - blurAlpha)
            }
            if (blur != null && blurAlpha > 0.001f) {
                clipPath(holePath) {
                    val contentScale = 1f - WyuMorphPageState.ContentShrink * fraction
                    withTransform({
                        translate(0f, liftPx * fraction)
                        scale(contentScale, contentScale, pivot = center)
                    }) {
                        drawImageStretched(blur, blurAlpha)
                    }
                }
            }

            // Mirror of the opening: the widget condenses with the window while
            // the card is stretched back into the landing geometry.
            val cardAlpha = smoothstep(0.3f, 0.85f, fraction)
            if (cardAlpha > 0f) {
                clipPath(holePath) {
                    drawImageIntoTarget(cardImage, hole.rect, cardAlpha)
                }
            }
        }
        FlightTitle(
            snapshot = snapshot,
            progress = { raw.value },
            forward = false,
        )
        if (page != null && !landed) {
            // Only the returning card's landing spot is intercepted: a tap
            // there reopens the page. Every other touch must reach the live
            // home underneath immediately, even while the window is still
            // closing; a full-screen interceptor (consuming or not) blocked
            // hit-testing for the whole return and made taps feel dead.
            val targetWidth = with(density) { target.width.toDp() }
            val targetHeight = with(density) { target.height.toDp() }
            Box(
                Modifier
                    .offset { IntOffset(target.left.roundToInt(), target.top.roundToInt()) }
                    .size(targetWidth, targetHeight)
                    .pointerInput(target) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            val up = waitForUpOrCancellation()
                            if (up != null && raw.value < 0.999f) onTap()
                        }
                    },
            )
        }
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
