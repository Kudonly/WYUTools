package com.wyutools.app.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Shader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.RoundedCorner
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.wyutools.app.SharedCardSnapshot
import com.wyutools.app.SharedHomeLayers
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.addSquircleRect
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Which window a morph paints. The two are deliberately separate pipelines —
 * they share the backdrop, the springs and the geometry, but nothing about the
 * window's surface, corner or fill is shared.
 *
 * [Card] is the home screen's 常用卡片. It is **frozen**: its constants and its
 * draw function live in one block (see [drawCardWindow]) and nothing else feeds
 * them, so retuning the tile morph can never change how a card opens.
 *
 * [Tile] is everything that is not a card — the 更多 icons and the round top bar
 * buttons. It follows the iOS 11 model measured off the reference clip: the
 * window is filled from the tile's own surface and its glyph is never stretched.
 */
enum class WyuMorphStyle {
    Card,
    Tile,
}

/** Matches the depth of iOS: the home screen recedes behind the window. */
private const val MorphBackdropDim = 0.30f

/** How far the wallpaper travels towards the viewer. It is the one layer that
 *  grows: the frame is filled by it, so any amount only reads as depth. */
private const val MorphWallpaperZoom = 0.20f

/** Everything drawn on top of the wallpaper pulls back instead — the icons,
 *  cards and bars shrink towards the centre the way the iOS home screen does. */
private const val MorphContentShrink = 0.14f

/** The capture is handed over by geometry alone: at 1 the window is the screen
 *  and the rim is already zero wide. Nothing about it is released early — its
 *  defocus and scrim stay at full strength right up to the last frame, so the
 *  home behind the window never thins out into a bright, fuzzy border. */
private const val MorphContentFadeStart = 0.55f
private const val MorphContentFadeEnd = 1f

/**
 * How far the backdrop travel lags the window *while it opens*, as a single
 * exponent.
 *
 * The window advances linearly in time; the backdrop reads the same number
 * through p^k, so while the window grows the wallpaper and the icons are always
 * behind it and only catch up on the last frames. That is what makes the zoom
 * feel slower and longer than the window's — the depth comes from the rate
 * difference, not from extra distance.
 *
 * It is deliberately *open only*. The same curve run backwards (pull 1 → 0) has
 * a slope of k at the very start, which made the close leave at 2.4× the
 * window's speed and overshoot the whole travel in the first quarter. Closing
 * therefore reads the plain window progress, so the backdrop lands in step with
 * the icon it is returning to.
 */
private const val MorphBackdropOpenTravelPower = 2.4f

/** Where the defocus starts and where it is at full strength. It starts late
 *  and climbs until the very end of the pull, so focus drifts away slowly
 *  instead of snapping out of focus in the first third of the zoom. */
private const val MorphBlurStart = 0.10f
private const val MorphBlurEnd = 0.95f

/** Peak defocus of the receding home capture. The capture is blurred on the
 *  GPU every frame, so the radius can keep growing with the pull instead of
 *  crossfading between two pre-blurred copies. Kept low on purpose: the
 *  defocus is scenery, not the subject, and a light wash keeps the wallpaper
 *  recognisable while the window opens. */
private val MorphBackdropBlur = 14.dp

// ---------------------------------------------------------------------------
// 常用卡片 — FROZEN PIPELINE
//
// The card entries have their own window, restored to what they looked like
// before the tile work: the card snapshot is stretched into the growing window
// as one piece and dissolves into the page. Nothing below is read by any other
// entry, and no constant outside this block affects it, so later changes to the
// tile morph leave the cards exactly as they are.
//
// Touch these four numbers only when the cards themselves are being asked about.
// ---------------------------------------------------------------------------

/** The card holds on the way open, then dissolves into the page. */
private const val CardWindowFadeStart = 0.15f
private const val CardWindowFadeEnd = 0.70f

/** Mirror of the above on the way back: the card condenses out of the page. */
private const val CardWindowReturnFadeStart = 0.30f
private const val CardWindowReturnFadeEnd = 0.85f

/**
 * The card as its window: one stretch of the snapshot over the whole hole.
 *
 * A card is a small rounded rectangle that becomes a large rounded rectangle,
 * so stretching it is the honest description of what happens — the card *is*
 * the window. That is why the cards keep this and the tiles do not: a tile is a
 * glyph on a colour, and stretching it would squash the glyph.
 */
private fun DrawScope.drawCardWindow(
    card: ImageBitmap,
    hole: MorphHole,
    alpha: Float,
) {
    if (alpha <= 0f || hole.rect.width <= 0f || hole.rect.height <= 0f) return
    drawImageIntoRect(card, hole.rect, alpha)
}

// ---------------------------------------------------------------------------
// Tiles (更多 icons / top bar buttons) — iOS 11 fill model
// ---------------------------------------------------------------------------

/**
 * The card's *surface* is what fills the window while it opens; its content
 * rides on top and dissolves before the surface does.
 *
 * Drawing the tile bitmap straight into the window squashes everything printed
 * on it: a tile is square, a phone window is tall, and the aspect changes the
 * whole way. iOS 11 fills instead — the icon's background becomes the window and
 * the glyph stays at its own size on top. Same here: the surface is a single
 * column of the bitmap stretched to the window (a gradient cannot distort), and
 * the content is drawn at its natural size, so it is never stretched at all.
 *
 * The window starts covered by the tile's surface alone. The reference clip
 * shows the glyph gone within the first frames — only the colour travels: by the
 * third frame the window is already the page's own light surface, so the colour
 * has to be off the screen long before the window is. It is timed against the
 * clip, not against the mask, and it is a colour fade only — the hole itself
 * never softens, so no edge is ever left fuzzy by it.
 */
private const val MorphCardFillFadeStart = 0.25f
private const val MorphCardFillFadeEnd = 0.60f

/**
 * When the artwork leaves — and, mirrored, when it comes back.
 *
 * The fade starts on the very first frame: the glyph is whole at progress 0 (so
 * the first frame is still the tile itself, pixel for pixel) and begins
 * dissolving immediately, so the window has left the icon before the icon can
 * look like it is being carried along. [MorphCardContentFadeEnd] is the only
 * number worth touching — it is when the artwork is gone.
 *
 * The close reads the same two numbers backwards ([morphArtworkAlpha]): the
 * artwork is invisible until the mirrored point and only then fades back in, so
 * the icon reappears *late*, when the window is already nearly the tile again.
 * Mirroring like this is what makes the return a reversal of the open rather
 * than a separate animation with its own timings.
 */
private const val MorphCardContentFadeStart = 0f
private const val MorphCardContentFadeEnd = 0.24f

/**
 * The artwork's opacity while opening, and its mirror while closing.
 *
 * Both are written here rather than inline at the two draw sites so the pair
 * cannot drift apart: the second is literally the first with the axis flipped,
 * which is what keeps the icon's departure and its return symmetric.
 */
private fun morphArtworkAlpha(progress: Float): Float =
    1f - smoothstep(MorphCardContentFadeStart, MorphCardContentFadeEnd, progress)

private fun morphArtworkReturnAlpha(fraction: Float): Float =
    smoothstep(1f - MorphCardContentFadeEnd, 1f - MorphCardContentFadeStart, fraction)

/** The tile's colour surface, on the same pair of clocks as the artwork: it
 *  leaves once the window is the page's own, and comes back while the window is
 *  still large enough for the colour to be read as a surface rather than a tile. */
private fun morphSurfaceAlpha(progress: Float): Float =
    1f - smoothstep(MorphCardFillFadeStart, MorphCardFillFadeEnd, progress)

private fun morphSurfaceReturnAlpha(fraction: Float): Float =
    smoothstep(1f - MorphCardFillFadeEnd, 1f - MorphCardFillFadeStart, fraction)

/**
 * Exponent of the foot's travel, the reason a tile ends up *stretched*.
 *
 * The bottom edge covers far more ground than the head does — the tile's bottom
 * sits a quarter of the way down the screen, so its share of the move is more
 * than three times the head's — and running it a little behind the head is what
 * turns a rectangle that happens to get taller into a surface that is *pulled*
 * downwards. Just above 1 rather than 2: any larger and the first frames are a
 * widening strip rather than a stretch, because the head has already travelled
 * while the foot has barely left the tile.
 */
private const val MorphTileFootPower = 1.3f

/** RenderEffect is API 31+. Older devices draw the same layers sharp — the
 *  morph still runs, it just has no defocus. */
private val MorphBlurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * iOS home-screen feel: a critically damped spring, so the window travels fast,
 * decelerates hard into the corner and stops without a bounce. No overshoot is
 * deliberate — Apple's default zoom is overdamped, not springy.
 *
 * Opening gets its own, slightly softer rate: the window is being lifted rather
 * than thrown open, so it takes ~20% longer to settle while the close keeps its
 * original snap.
 *
 * The close was then asked to slow down as a whole, so its three clocks are
 * scaled together by the same factor (√stiffness ≈ 0.83, i.e. every one of them
 * runs ~20% longer). Scaling rather than retuning one of them is what keeps the
 * close's internal rhythm intact: the window is still fastest, the backdrop
 * still trails it, and the wallpaper is still the last thing to come to rest.
 */
private const val MorphOpenStiffness = 250f
private const val MorphCloseStiffness = 220f

private fun morphSpring(stiffness: Float, damping: Float = 1f) = spring<Float>(
    dampingRatio = damping,
    stiffness = stiffness,
    visibilityThreshold = 0.0005f,
)

/** The only two specs in the app: every icon, card and window shares them, so
 *  nothing can drift into its own curve. */
private val MorphOpenSpring = morphSpring(MorphOpenStiffness)
private val MorphCloseSpring = morphSpring(MorphCloseStiffness)

/**
 * The backdrop closes on its own, softer spring.
 *
 * The window and the card travel on [MorphCloseSpring] with a hand-off velocity,
 * so they are home quickly. The wallpaper and the icons on top of it keep
 * shrinking for a beat longer, and because they start from rest on a lower
 * stiffness they ease out instead of snapping — the close reads as the home
 * screen slowly settling back into place behind the window. The overlay is
 * swapped only after both springs have finished, so the final frame still
 * matches the live home exactly and nothing jumps when it comes off.
 *
 * This is the one spring in the app that is underdamped, and it is the one the
 * icons ride: on its way home it is carried past its resting size and pulled
 * back, which is the only rebound in the close ([exitIconSettle] turns that
 * overshoot into a legible pop). The window and the wallpaper stay critically
 * damped — a shape that stops where it is aimed.
 */
private const val MorphBackdropExitStiffness = 125f
private const val MorphBackdropExitDamping = 0.55f
private val MorphBackdropExitSpring = morphSpring(
    stiffness = MorphBackdropExitStiffness,
    damping = MorphBackdropExitDamping,
)

/**
 * The wallpaper closes on a third clock, softer again than the backdrop's.
 *
 * Its own [Animatable] runs this spring, so the zoom is not bounded by the
 * window or by the icons: the card lands, the icons settle, and the wallpaper
 * is still easing out after both. That trailing shrink is the whole point — it
 * is what makes the home read as drifting back into place instead of snapping.
 * The wallpaper is the largest thing on screen, so the same rate reads as much
 * faster on it than on the icons; hence the extra softness.
 *
 * It is critically damped like everything else: the wallpaper is the frame the
 * whole screen sits in, and a frame that rings is a frame whose edges can be
 * seen moving. The elasticity of the close lives one layer up, on the icons
 * ([exitIconSettle]), where it can bounce without revealing anything.
 */
private const val MorphWallpaperExitStiffness = 76f
private val MorphWallpaperExitSpring = morphSpring(MorphWallpaperExitStiffness)

/**
 * Exponent of the wallpaper's own closing curve.
 *
 * The wallpaper travels on an exponential ease-out: its speed is highest on the
 * first frame and then decays by a constant factor for the rest of the move, so
 * every equal slice of time covers less ground than the one before. Raising it
 * steepens that decay — the deceleration becomes the dominant sensation rather
 * than a detail at the end.
 */
private const val MorphWallpaperExitExponent = 3f

/**
 * Where along the exponential the wallpaper joins it.
 *
 * Sampling starts this far into the curve instead of at its steep head: the
 * wallpaper skips the fastest part and rides the lower, already-flattening half
 * of the exponential. Two things follow — the first frames are no longer a lurch
 * (the initial speed is only ~63% of the curve's peak), and the shrinking that
 * remains is spread over the whole, longer duration instead of being spent in
 * the first third with a dead tail behind it. The travel is renormalised, so
 * 0 still maps to 1 and 1 still maps to 0 exactly: no jump when the overlay
 * hands back to the live home.
 */
private const val MorphWallpaperExitJoin = 0.30f

/**
 * Bases of the two closing exponentials.
 *
 * Both the zoom and the defocus decay by a constant factor each frame; the base
 * sets *how* constant. A small base (the wallpaper's) keeps the factor close to
 * 1, so the shrink is spread evenly over the move and the slowdown is gentle —
 * the wallpaper appears to keep drifting in at nearly the same rate until the
 * very end. A larger one (the defocus) collapses fast and then crawls, so the
 * home snaps most of the way back into focus early and the last of it is a long,
 * barely-there tail. The defocus is deliberately the higher of the two: focus
 * should be given back *ahead* of the last of the shrink, never trailing behind
 * it, or the home would still be soft when it has already stopped moving.
 */
private const val MorphWallpaperExitBase = 1.5f
private const val MorphDefocusExitBase = 2.0f

/**
 * How far the wallpaper is still pulled in on the way home, 1 → 0.
 *
 * [progress] runs 0 → 1 over the wallpaper's own spring; the return is the
 * portion of the exponential from [MorphWallpaperExitJoin] to its end, rescaled
 * so both endpoints are exact.
 *
 * [base] is the exponential's own base, and it is the only shape knob: a low
 * base decays gently (the move stays close to even over its length), a high one
 * collapses in the first frames and spends the rest of the time barely moving.
 * The wallpaper and the defocus deliberately use different ones — see
 * [MorphWallpaperExitBase] and [MorphDefocusExitBase].
 */
private fun exitWallpaperPull(
    progress: Float,
    base: Float = MorphWallpaperExitBase,
): Float {
    // Strictly one-way, 1 → 0. The wallpaper is the frame the whole screen sits
    // in: any dip below its own size would open a seam around the display, so
    // the frame never overshoots. The close's elasticity belongs to the icons,
    // which sit inside the frame and can bounce without exposing an edge.
    val x = progress.coerceIn(0f, 1f)
    val k = MorphWallpaperExitExponent
    val join = MorphWallpaperExitJoin
    fun travelledAt(u: Float) = (1f - base.pow(-k * u)) / (1f - base.pow(-k))
    val from = travelledAt(join)
    val span = 1f - from
    if (span <= 0f) return 1f - x
    val travelled = ((travelledAt(join + (1f - join) * x) - from) / span)
        .coerceIn(0f, 1f)
    return 1f - travelled
}

/**
 * The close's rebound, and the only place it is allowed: the icons.
 *
 * The wallpaper comes home on a straight, one-way exponential and stops dead. The
 * icons on top of it do not — they are a separate capture with no background of
 * their own, so they can be carried past their resting size and pulled back
 * without any edge becoming visible. That is what a real spring does under a
 * shrinking surface, and it is the part of the motion the eye reads as settling.
 *
 * The rebound is not a second animation bolted on: it is the backdrop spring's
 * own overshoot, [MorphBackdropExitSpring] being underdamped. Once the spring
 * passes 1 the pull (1 − progress) goes negative, and negative means the icons
 * are drawn *larger* than home — a pop outwards from the centre, then a smaller
 * swing the other way, decaying back to exactly 0 on the last frame, so the
 * hand-over to the live home stays pixel-exact.
 *
 * The overshoot of a damped spring is only a few percent, which is the right
 * physics but too small to read at this scale, so it is amplified as it comes
 * into play ([MorphIconReboundGain]). The gain fades in over the last stretch
 * before the spring reaches home, so the amplification only ever touches the
 * ringing part and never the shrink itself. Raise it to make the bounce more
 * obvious (3.5 is a good "show me" setting), lower it to make the landing calm.
 */
private const val MorphIconReboundGain = 2.4f

private fun exitIconSettle(progress: Float): Float {
    val gain = 1f + (MorphIconReboundGain - 1f) * smoothstep(0.90f, 1f, progress)
    return (1f - progress) * gain
}

/**
 * The window's own landing rebound, as a scale about its centre.
 *
 * The home settles with a bounce ([exitIconSettle]) but the window used to come
 * in on a straight line and simply stop on the card. A surface that has travelled
 * this far should carry its momentum into the landing: it closes a little past
 * the card it is aiming at, is pulled back through it and comes to rest exactly
 * on it. That is what this curve does, and it is the only motion left in the
 * close that the eye can still see at the end.
 *
 * One damped cycle over the tail: the inward dip is the landing, the much
 * smaller outward swing is the spring giving the overshoot back, and the envelope
 * reaches zero exactly on the last frame so the hand-over to the live card is
 * pixel-exact. Everything is scaled about the window's centre, radius included,
 * so the shape keeps its proportions through the dip instead of looking squashed.
 *
 * Safe only because the card's own slot is empty while it flies
 * ([WyuMorphPageState.flyingRoute]): the dip reveals whatever is behind the
 * window, and behind it is the wallpaper, not a second copy of the card.
 */
private const val MorphWindowLandRebound = 0.08f
private const val MorphWindowLandReboundStart = 0.55f

private fun exitWindowSettle(fraction: Float): Float {
    val t = ((fraction - MorphWindowLandReboundStart) /
        (1f - MorphWindowLandReboundStart)).coerceIn(0f, 1f)
    if (t <= 0f) return 1f
    val envelope = smoothstep(0f, 0.12f, t) * (1f - smoothstep(0.84f, 1f, t))
    return 1f - MorphWindowLandRebound * sin(2f * PI.toFloat() * t) *
        exp(-1.8f * t) * envelope
}

/** [hole] scaled about its centre by the landing rebound at [fraction]. */
private fun landedWindow(hole: MorphHole, fraction: Float): MorphHole {
    val settle = exitWindowSettle(fraction)
    if (settle >= 0.9995f) return hole
    val halfWidth = hole.rect.width * settle / 2f
    val halfHeight = hole.rect.height * settle / 2f
    val cx = hole.rect.center.x
    val cy = hole.rect.center.y
    return MorphHole(
        Rect(cx - halfWidth, cy - halfHeight, cx + halfWidth, cy + halfHeight),
        hole.radius * settle,
    )
}

/**
 * Hand-off speed every close starts with, in progress units per second.
 *
 * The normal close used to start from a standstill while a close that
 * interrupted an open inherited that open's velocity, so the two exits felt
 * different: the interrupted one kicked off fast and swooped, the deliberate
 * one crawled away from zero. This constant is applied to *both*, so the close
 * always leaves at the same speed — the interrupted close's speed — no matter
 * how it was started. It is the only knob for close pacing.
 *
 * Scaled with the close's stiffness (√stiffness ratio = 0.83): the hand-off is
 * what the spring is given *on top of* its own start, so leaving it at the old
 * value while softening the springs would have made the window lurch away and
 * then crawl. Keeping it proportional is what makes the whole close read as the
 * same gesture, just played slower.
 */
private const val MorphExitVelocity = 2.5f

/**
 * How long a return takes to stand down when a home tab switch begins under it.
 * Long enough to read as a fade rather than a cut, short enough that the window
 * is gone while the pager is still travelling — nothing of the leaving page may
 * be left over the page that is arriving.
 */
private const val YieldFadeMillis = 140

/**
 * The one way a window opens.
 *
 * Opening and closing are deliberately separate entry points — see
 * [animateExit]. Never start a close through this function.
 */
private suspend fun Animatable<Float, AnimationVector1D>.animateOpen(
    target: Float,
    onProgress: (Float) -> Unit = {},
) {
    animateMorphBySpring(
        target = target,
        spec = MorphOpenSpring,
        onProgress = onProgress,
    )
}

/**
 * The one way a window closes — the back gesture, the tap on the backdrop and
 * the reversal of an interrupted open all come through here.
 *
 * That is the guarantee: there is a single spec and a single hand-off velocity,
 * so the exits cannot drift apart again as the constants are retuned. Anything
 * that needs to close the window must call this, not [animateOpen].
 */
private suspend fun Animatable<Float, AnimationVector1D>.animateExit(
    target: Float,
    onProgress: (Float) -> Unit = {},
) {
    animateMorphBySpring(
        target = target,
        spec = MorphCloseSpring,
        onProgress = onProgress,
        entryVelocity = MorphExitVelocity,
    )
}

/**
 * Compose keeps the velocity of a running spring, so a reversal still turns
 * smoothly; [entryVelocity] additionally guarantees a floor for the speed the
 * animation leaves with, which is what makes a deliberate close start as briskly
 * as one that interrupted an open.
 */
private suspend fun Animatable<Float, AnimationVector1D>.animateMorphBySpring(
    target: Float,
    spec: SpringSpec<Float>,
    onProgress: (Float) -> Unit,
    entryVelocity: Float = 0f,
) {
    onProgress(value)
    if (abs(target - value) <= 0.001f) {
        snapTo(target)
        onProgress(target)
        return
    }
    // Point the hand-off the way the window has to travel; a critically damped
    // spring does not overshoot with an initial velocity, so this only makes the
    // launch brisker, never bouncier.
    val direction = if (target >= value) 1f else -1f
    animateTo(target, spec, initialVelocity = entryVelocity * direction) {
        onProgress(this.value)
    }
    snapTo(target)
    onProgress(target)
}

private fun smoothstep(edge0: Float, edge1: Float, value: Float): Float {
    val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** Backdrop travel while the window opens: the slow, trailing clock. Closing
 *  uses the raw window progress instead — see [MorphBackdropOpenTravelPower]. */
private fun backdropOpenTravel(progress: Float): Float =
    progress.coerceIn(0f, 1f).pow(MorphBackdropOpenTravelPower)

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

/**
 * Blur effects are cached per half-pixel radius: a morph drives the radius
 * every frame, and allocating a RenderEffect per frame puts real pressure on
 * the render thread. The UI thread is the only writer, so a plain map is safe.
 */
private val morphBackdropEffects = HashMap<Int, BlurEffect>()

private fun backdropBlurEffect(radiusPx: Float): BlurEffect {
    val key = (radiusPx * 2f).roundToInt().coerceAtLeast(1)
    return morphBackdropEffects.getOrPut(key) {
        BlurEffect(key / 2f, key / 2f, TileMode.Clamp)
    }
}

@Composable
private fun rememberViewLocation(): IntArray {
    val view = LocalView.current
    return remember(view) { IntArray(2).also(view::getLocationOnScreen) }
}

/**
 * The physical display corner radius, so full-screen containers match the device
 * body. The sheets and this morph share one reader, so a card lands on exactly
 * the corner the page behind it wears.
 */
@Composable
private fun rememberScreenCornerRadiusPx(): Float = rememberDisplayCornerPx()

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

/**
 * The window silhouette, on Miuix's continuous-corner squircle rather than a
 * plain arc: the same shape the glass cards use, so a card opening into its
 * page and an icon opening into its page share one corner language. The radius
 * is a plain float so the morph can interpolate it every frame.
 */
private fun squirclePath(rect: Rect, radius: Float): Path {
    if (rect.width <= 0f || rect.height <= 0f) return Path()
    val limit = min(rect.width, rect.height) / 2f
    val clamped = radius.coerceIn(0f, limit)
    // A zero radius has no corners to smooth; skipping the squircle maths also
    // keeps the final frames (where the window is the full screen and some
    // devices report no display corner) allocation-free.
    if (clamped < 0.5f) {
        return Path().apply { addRect(rect) }
    }
    // A radius that reaches half the short side is a circle (a round top bar
    // button); a squircle there would read as a rounded square instead.
    if (clamped >= limit - 0.5f) {
        return Path().apply {
            addRoundRect(
                RoundRect(rect, CornerRadius(clamped, clamped)),
            )
        }
    }
    val path = Path()
    path.addSquircleRect(rect.width, rect.height, clamped)
    // addSquircleRect always starts at the origin; move it onto the window.
    val bounds = path.getBounds()
    path.translate(Offset(rect.left - bounds.left, rect.top - bounds.top))
    return path
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

private fun DrawScope.drawImageIntoRect(image: ImageBitmap, rect: Rect, alpha: Float) {
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
 * Paints the card's surface over [rect] by stretching one column of the bitmap
 * across it — the gradient the card already carries, sampled just inside its
 * left edge, where neither glyph nor label ever sits.
 *
 * Stretching a *gradient* is invisible, which is the whole point: the window can
 * then be filled with the card's own colours at any aspect ratio without a
 * single pixel of content being squashed.
 */
private fun DrawScope.drawCardSurface(image: ImageBitmap, rect: Rect, alpha: Float) {
    if (alpha <= 0f || rect.width <= 0f || rect.height <= 0f || image.width <= 0) return
    val column = (image.width * 0.12f).roundToInt().coerceIn(0, image.width - 1)
    // The tile's own corners are transparent: sampling them would stretch that
    // transparency into a band across the whole window. The sample therefore
    // starts below the corner arc — 22% of the height clears the roundest tile
    // in the app — and the opaque middle is stretched over the full rect.
    val inset = (image.height * 0.22f).roundToInt().coerceIn(0, image.height / 3)
    val srcHeight = (image.height - inset * 2).coerceAtLeast(1)
    drawImage(
        image = image,
        srcOffset = IntOffset(column, inset),
        srcSize = IntSize(1, srcHeight),
        dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
        dstSize = IntSize(rect.width.roundToInt(), rect.height.roundToInt()),
        alpha = alpha,
        filterQuality = FilterQuality.Low,
    )
}

/**
 * The tile's artwork, hung from the middle of the window's top edge and drawn to
 * exactly its width.
 *
 * The window is already taller than the tile from the first frame — it is a
 * stretch, not a zoom — so the artwork cannot cover it and is not asked to: it
 * fills the *head*, at its own aspect, while [drawCardSurface] carries the tile's
 * colours down into the rest of the window. Filling the width is why the scale
 * needs no constant: it is whatever makes the bitmap meet the hole's sides, so
 * the very first frame is the tile pixel for pixel and nothing is ever squeezed.
 */
private fun tileContentRect(window: Rect, image: ImageBitmap): Rect {
    if (image.width <= 0 || window.width <= 0f) return window
    val grow = window.width / image.width
    val halfWidth = image.width * grow / 2f
    val centerX = window.center.x
    return Rect(
        centerX - halfWidth,
        window.top,
        centerX + halfWidth,
        window.top + image.height * grow,
    )
}

private data class MorphHole(val rect: Rect, val radius: Float)

private fun morphHole(
    progress: Float,
    start: Rect,
    width: Float,
    height: Float,
    startRadius: Float,
    screenRadius: Float,
    style: WyuMorphStyle,
): MorphHole {
    val p = progress.coerceIn(0f, 1f)

    // A card is one rectangle zooming into another: every edge shares the
    // progress and both radii tween across the whole move.
    if (style == WyuMorphStyle.Card) {
        return MorphHole(
            Rect(
                lerp(start.left, 0f, p),
                lerp(start.top, 0f, p),
                lerp(start.right, width, p),
                lerp(start.bottom, height, p),
            ),
            if (screenRadius > 0f) {
                lerp(startRadius, screenRadius, p)
            } else {
                startRadius * (1f - p)
            },
        )
    }

    // A tile stretches from the first frame.
    //
    // Every edge leaves on the spring's own progress — the head and both sides
    // together, the foot a touch behind ([MorphTileFootPower]) — so the window is
    // never a square being zoomed: it is the tile's own rect opening, taller than
    // it is wide from the first frame onwards. No square stage, no growth spurt
    // halfway in, just the shape deforming for the whole move.
    val left = lerp(start.left, 0f, p)
    val right = lerp(start.right, width, p)
    val top = lerp(start.top, 0f, p)
    val bottom = lerp(start.bottom, height, p.pow(MorphTileFootPower))
    val run = right - left
    val scale = if (start.width > 0f) run / start.width else 1f

    // The corner is the tile's own, scaled with the mask — so the very first
    // frame is the icon's rounded square exactly — capped at the body's, which
    // it reaches by itself once the mask is a few times the tile. Capping rather
    // than ramping keeps the mask from ever being rounder than the artwork it is
    // covering, which would clip the icon's corners open for a frame or two.
    val cap = if (screenRadius > 0f) screenRadius else startRadius * scale
    val radius = min(startRadius * scale, cap)

    return MorphHole(Rect(left, top, right, bottom), radius)
}

/**
 * The three backdrop clocks live outside every morph window.
 *
 * A window owns only its own card, its hole and its icon. The wallpaper zoom,
 * the shrink of the elements on top of it and the defocus belong here instead,
 * so switching from one window to another never rebuilds them. Every retarget
 * starts from the value that is on screen *right now* and animates towards the
 * new target: nothing is stopped and nothing is snapped back to the resting
 * zoom. A BACK, a tap during a close or a quick switch between two cards
 * therefore reads as one continuous scale/blur change rather than a jump back
 * to the default followed by a fresh animation.
 *
 * The clocks are separate so the wallpaper stays the slowest, focus comes back
 * ahead of it, and only the icons are allowed to overshoot on the way home.
 */
private object MorphBackdropState {
    /** 0 = home at rest, 1 = fully pulled in and defocused. These three values
     *  are the *only* source of the backdrop's zoom, shrink and blur, and they
     *  survive every window. */
    val travel = Animatable(0f)
    val wallpaper = Animatable(0f)
    val defocus = Animatable(0f)

    private enum class Command(val target: Float) { OPEN(1f), CLOSE(0f) }

    // CONFLATED is what makes a quick container switch a single turn instead
    // of two animations: BACK posts CLOSE and the very next tap posts OPEN
    // before the clock has gone anywhere, so CLOSE is dropped entirely and the
    // three values simply reverse from the pull that is on screen. Even when
    // CLOSE has already been running for a while, OPEN takes over at the exact
    // current value — no trip back to the resting zoom, no fresh 0 -> 1 replay.
    private val commands = Channel<Command>(Channel.CONFLATED)
    private var lastCommand: Command? = null

    fun open() = send(Command.OPEN)
    fun close() = send(Command.CLOSE)

    private fun send(command: Command) {
        if (lastCommand == command) return
        lastCommand = command
        commands.trySend(command)
    }

    /**
     * Runs in a composition that outlives every window ([MorphBackdropDriver]).
     * Animatable.animateTo needs a MonotonicFrameClock, which only a
     * composition-scoped context provides; a hand-built CoroutineScope has none
     * and crashes. Keeping the driver at the activity level means a window
     * being torn down — onFinished, an interrupt, a preempt — never cancels
     * the animation halfway (the backdrop is slower than the window, so it used
     * to be cut off well before it reached its target).
     */
    suspend fun runIn(scope: CoroutineScope) {
        while (true) {
            val command = commands.receive()
            // A fresh animateTo takes each Animatable over at its current
            // value. Nothing is stopped and nothing is snapped.
            scope.launch { retarget(command) }
        }
    }

    private suspend fun retarget(command: Command) {
        val travelSpec: SpringSpec<Float>
        val wallpaperSpec: SpringSpec<Float>
        val defocusSpec: SpringSpec<Float>
        if (command == Command.OPEN) {
            travelSpec = morphSpring(MorphBackdropOpenStiffness)
            wallpaperSpec = morphSpring(MorphWallpaperOpenStiffness)
            defocusSpec = morphSpring(MorphDefocusStiffness)
        } else {
            travelSpec = MorphBackdropExitSpring
            wallpaperSpec = MorphWallpaperExitSpring
            defocusSpec = morphSpring(MorphDefocusStiffness)
        }
        val target = command.target
        coroutineScope {
            launch { travel.animateTo(target, travelSpec, initialVelocity = turnedVelocity(travel, target)) }
            launch { wallpaper.animateTo(target, wallpaperSpec, initialVelocity = turnedVelocity(wallpaper, target)) }
            launch { defocus.animateTo(target, defocusSpec, initialVelocity = turnedVelocity(defocus, target)) }
        }
    }

    /**
     * Keep the speed the spring was running at, but point it at the new target
     * in the very first frame. A plain spring hand-off would coast a little in
     * the old direction before turning, which reads as a stall; starting from
     * rest reads as a restart. This gives an immediate, continuous reversal.
     */
    private fun turnedVelocity(
        animatable: Animatable<Float, AnimationVector1D>,
        target: Float,
    ): Float {
        val direction = if (target >= animatable.value) 1f else -1f
        return direction * min(abs(animatable.velocityVector.value), MorphRetargetMaxSpeed)
    }

    /** Hold a closing window up until the shared clocks are truly home, so the
     *  overlay never comes off one frame before the live home matches it. */
    suspend fun awaitAtRest() {
        // Short on purpose: the springs settle in well under 400ms. If a new
        // window has taken the clocks over in the meantime the wait must end
        // instead of holding the old overlay (and its BackHandler) hostage —
        // that was what made a second return feel unresponsive for seconds.
        withTimeoutOrNull(500L) {
            snapshotFlow {
                maxOf(abs(travel.value), abs(wallpaper.value), abs(defocus.value))
            }.first { it <= 0.002f }
        }
    }
}

/** Mounted once at the activity level: the backdrop's animation driver must
 *  never be tied to a window that can disappear between two taps. */
@Composable
fun MorphBackdropDriver() {
    LaunchedEffect(Unit) {
        MorphBackdropState.runIn(this)
    }
}

private const val MorphRetargetMaxSpeed = 3f

/** Opening rates of the three independent clocks. All are softer than the
 *  window (250) so the depth still trails the card; the wallpaper is the
 *  slowest and the defocus the quickest, keeping the rhythm of the close. */
private const val MorphBackdropOpenStiffness = 110f
private const val MorphWallpaperOpenStiffness = 70f
private const val MorphDefocusStiffness = 170f

/**
 * The middle layer of the morph, split the way iOS splits it: the wallpaper
 * moves towards the viewer while everything drawn on top of it — icons, cards,
 * bars — pulls back towards the centre and shrinks. Both captures come from the
 * same frame, so the two layers stay registered pixel for pixel.
 *
 * The defocus is a live RenderEffect on this one layer, so the blur radius
 * grows continuously with the pull instead of crossfading between two
 * pre-blurred copies. Everything is read in the draw phase: frames redraw
 * without recomposing and no bitmap is ever rebuilt.
 */
@Composable
private fun MorphBackdrop(
    wallpaper: ImageBitmap?,
    content: ImageBitmap?,
    strength: () -> Float,
    modifier: Modifier = Modifier,
    motionStrength: () -> Float = strength,
    wallpaperStrength: (() -> Float)? = null,
    defocusStrength: (() -> Float)? = null,
) {
    val surface = MiuixTheme.colorScheme.surface
    val maxBlurPx = with(LocalDensity.current) { MorphBackdropBlur.toPx() }
    val focus = defocusStrength ?: strength
    Canvas(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                // One radius for the whole middle layer: the wallpaper and the
                // content it carries lose focus together, the way a single
                // surface receding behind the window would. Clamp keeps the
                // edges opaque where the zoomed wallpaper overruns the layer.
                if (MorphBlurSupported) {
                    // Full defocus all the way: it is the *window edge* that ends
                    // the capture, not a release of focus, so the last strip of
                    // home never turns into a sharp or a glaring border.
                    //
                    // The caller's curve is taken as the radius itself when it
                    // hands one over: the close supplies an exponential that is
                    // already normalised 1 → 0, and running it through the
                    // window's own ramp would chop off its long tail. Opening
                    // still uses that ramp, so the defocus fades in with the
                    // window instead of appearing at full strength.
                    val amount = focus().coerceIn(0f, 1f)
                    val radius = if (defocusStrength != null) {
                        amount * maxBlurPx
                    } else {
                        smoothstep(MorphBlurStart, MorphBlurEnd, amount) * maxBlurPx
                    }
                    renderEffect = if (radius >= 0.75f) {
                        backdropBlurEffect(radius)
                    } else {
                        null
                    }
                }
            },
    ) {
        if (wallpaper == null && content == null) {
            drawRect(surface)
            return@Canvas
        }
        // [motionStrength] carries the travel and may run on a different clock
        // than the window; the defocus above always tracks the window itself.
        // The wallpaper may take its own curve on top of that — the close uses
        // one whose speed decays exponentially, so it pulls away fastest at the
        // very start and then keeps easing for the rest of the move.
        val travel = motionStrength()
        drawMorphBackdropContent(
            wallpaper = wallpaper,
            content = content,
            surface = surface,
            pull = travel,
            wallpaperPull = wallpaperStrength?.invoke() ?: travel,
        )
    }
}

private fun DrawScope.drawMorphBackdropContent(
    wallpaper: ImageBitmap?,
    content: ImageBitmap?,
    surface: Color,
    pull: Float,
    wallpaperPull: Float = pull,
) {
    // Wallpaper: it is the frame, so zooming it never exposes an edge. The
    // window edge is the only hand-over — no alpha crossfade, because a fading
    // capture would show the page *outside* the window and leave a soft border
    // around it. The rim simply shrinks to nothing as the window reaches the
    // screen.
    if (wallpaper != null) {
        val zoom = 1f + MorphWallpaperZoom * wallpaperPull
        val halfWidth = size.width * zoom / 2f
        val halfHeight = size.height * zoom / 2f
        drawImageIntoRect(
            image = wallpaper,
            rect = Rect(
                left = size.width / 2f - halfWidth,
                top = size.height / 2f - halfHeight,
                right = size.width / 2f + halfWidth,
                bottom = size.height / 2f + halfHeight,
            ),
            alpha = 1f,
        )
    } else {
        drawRect(surface)
    }
    // Content: everything that sits on the wallpaper. It recedes instead — a
    // slight pull towards the centre plus a fade, so home reads as one surface
    // sinking behind the window rather than a stack of moving pieces.
    if (content != null) {
        val contentAlpha = 1f - smoothstep(MorphContentFadeStart, MorphContentFadeEnd, pull)
        if (contentAlpha > 0.001f) {
            val scale = 1f - MorphContentShrink * pull
            withTransform({ scale(scale, scale, pivot = center) }) {
                drawImageIntoRect(content, Rect(Offset.Zero, size), contentAlpha)
            }
        }
    }
}

/**
 * Opens the card into the page: the card preview stretches with the growing
 * rounded window and dissolves into the destination underneath it, while the
 * home backdrop recedes, blurs and finally hands over to the page.
 */
@Composable
fun CardMorphOverlay(
    snapshot: SharedCardSnapshot,
    home: SharedHomeLayers,
    page: Bitmap?,
    cancelRequested: Boolean,
    style: WyuMorphStyle,
    modifier: Modifier = Modifier,
    /** A morph started by interrupting another one must not pull the page
     *  down and shrink it a second time. The page is already at rest. */
    suppressPageSettle: Boolean = false,
    onCovered: () -> Unit,
    onFinished: () -> Unit,
    onCancelled: () -> Unit,
) {
    val expand = remember(snapshot, home) { Animatable(0f) }
    // A preempt removes this overlay outright, so it never needs a generation
    // guard. Keeping the normal writes means an incidental touch during an
    // opening does not freeze the page half-way through its settle.
    fun applyContent(value: Float) {
        if (!suppressPageSettle) WyuMorphPageState.contentProgress = value
    }
    // Survives effect restarts, so a reversal never re-runs the navigation step.
    val started = remember(snapshot, home) { booleanArrayOf(false) }
    val cardImage = remember(snapshot) { snapshot.bitmap.asImageBitmap() }
    val wallpaperImage = remember(home.wallpaper) { home.wallpaper.asImageBitmap() }
    val contentImage = remember(home.content) { home.content.asImageBitmap() }
    val pageImage = remember(page) { page?.asImageBitmap() }
    val start = rememberStartBounds(snapshot)
    val screenRadius = rememberScreenCornerRadiusPx()
    // The cancelled morph stays up until the page has left the host; once the
    // reverse animation has landed it must no longer swallow touches, so the
    // home is usable on the very next frame even while the overlay is fading out.
    var landed by remember(snapshot) { mutableStateOf(false) }
    // Detaching twice would run the cancel clean-up twice, so the two places
    // that can end this window share one guard.
    val ended = remember(snapshot) { booleanArrayOf(false) }
    fun endOnce() {
        if (ended[0]) return
        ended[0] = true
        onCancelled()
    }
    // The reverse animation is not modal: home is live under it and its bottom
    // bar can be used from the frame BACK lands. A tab switch started there
    // would play behind this window — it covers the whole screen with the home
    // capture and the page snapshot — and only its end state would be seen once
    // the overlay came off. So the window stands down as soon as the pager
    // moves, and detaches with it: an invisible overlay would still hold the
    // page's state and the source card's slot.
    val yielded = remember(snapshot) { mutableStateOf(false) }
    LaunchedEffect(snapshot, cancelRequested) {
        if (!cancelRequested) return@LaunchedEffect
        snapshotFlow { WyuMorphPageState.homePagerMoving }.first { it }
        yielded.value = true
        delay(YieldFadeMillis.toLong() + 24L)
        if (cancelRequested) {
            Log.d("WyuMorph", "cancelled open hands the screen back to the page switch")
            endOnce()
        }
    }
    val yieldAlpha by animateFloatAsState(
        targetValue = if (yielded.value) 0f else 1f,
        animationSpec = tween(durationMillis = YieldFadeMillis),
        label = "cancelYield",
    )
    // The backdrop clocks are global (MorphBackdropState). This window only
    // redirects them, so an interrupt or a quick card switch continues the
    // zoom, shrink and defocus from the values already on screen instead of
    // rebuilding them at the resting scale.
    LaunchedEffect(snapshot, home, cancelRequested) {
        if (!started[0]) {
            started[0] = true
            withFrameNanos { }
            onCovered()
        }
        // A card leaves the page alone — it has always been a still target that
        // the window merely reveals, so its pipeline keeps it at rest.
        //
        // A tile does the opposite, and that is what the reference clip shows:
        // the page underneath settles *with* the morph. It starts a few percent
        // small and a little low, then arrives as the window does, so what is
        // being revealed is a page that is still coming to rest rather than a
        // frozen sheet. Only the values in [WyuMorphPageState] are used, so the
        // page keeps its own geometry the whole time.
        WyuMorphPageState.morphWindowProgress = expand.value
        applyContent(if (style == WyuMorphStyle.Tile && !suppressPageSettle) 0f else 1f)
        if (cancelRequested) {
            // BACK has started the interrupt. From this exact frame the opening
            // is no longer modal: home is live in the NavHost and its cards
            // must be hit-testable while this reverse animation is still
            // playing, so a tap can start the next morph immediately.
            landed = true
            // The backdrop is neither reseeded nor stopped: its three global
            // clocks simply retarget to rest from exactly the pull on screen.
            // A later card tap retargets them back to 1 just as continuously,
            // so no frame ever drops to the default zoom and starts over.
            MorphBackdropState.close()
            applyContent(1f)
            coroutineScope {
                launch {
                    expand.animateExit(0f) { WyuMorphPageState.morphWindowProgress = it }
                }
                // Keep the window up until the backdrop is home: otherwise the
                // last pulled-in frame would be replaced by the sharp live home
                // the moment the overlay detaches. If a new card is tapped in
                // the meantime this overlay is simply removed and the shared
                // clocks retarget to the new window without waiting.
                launch { MorphBackdropState.awaitAtRest() }
            }
            landed = true
            endOnce()
        } else {
            MorphBackdropState.open()
            expand.animateOpen(1f) { settled ->
                WyuMorphPageState.morphWindowProgress = settled
                if (style == WyuMorphStyle.Tile) applyContent(settled)
            }
            if (style == WyuMorphStyle.Tile) applyContent(1f)
            onFinished()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(modifier)
            // A window standing down for a tab switch fades out as one piece:
            // the home capture, the scrim and the page still shrinking back into
            // the card. Nothing of the page being left may remain over the page
            // that is sliding in.
            .then(
                if (yieldAlpha < 1f) {
                    Modifier.graphicsLayer { alpha = yieldAlpha }
                } else {
                    Modifier
                },
            )
            .then(
                // During a pure opening this consumes every press: only BACK
                // is allowed to interrupt. The moment BACK fires,
                // cancelRequested is true in the very same composition, so the
                // blocker is gone and touches reach the live home underneath.
                if (landed || cancelRequested) {
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
                        style = style,
                    )
                    clipPath(squirclePath(hole.rect, hole.radius), ClipOp.Difference) {
                        this@drawWithContent.drawContent()
                    }
                },
        ) {
            MorphBackdrop(
                wallpaper = wallpaperImage,
                content = contentImage,
                // All three come from the window-independent clocks. Whether
                // this window is opening, being cancelled or replacing another
                // one, these are the values already on screen and they keep
                // moving from there — no reset to the resting scale.
                strength = { MorphBackdropState.travel.value },
                wallpaperStrength = { MorphBackdropState.wallpaper.value },
                defocusStrength = { MorphBackdropState.defocus.value },
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
                style = style,
            )
            val holePath = squirclePath(hole.rect, hole.radius)

            // The scrim belongs to the middle layer and deepens with it; it is
            // never lifted early, so the strip of home around the window stays a
            // settled, dimmed surface instead of brightening into a halo.
            val dim = MorphBackdropDim * progress
            if (dim > 0f) {
                clipPath(holePath, ClipOp.Difference) {
                    drawRect(Color.Black.copy(alpha = dim))
                }
            }

            // Reversing the open morph hands the window back to the page: the
            // snapshot is laid in at full sharpness and simply fades out as the
            // window shrinks, never defocused. Nothing inside the container is
            // ever blurred on the way out — an exit is the page being pulled back
            // into the icon, not a sheet of frosted glass lifting off the home.
            if (pageImage != null && cancelRequested) {
                // BACK now pops the destination immediately so home can be
                // tapped during the reverse. The window therefore cannot show
                // the live page through the hole any more: it is painted from
                // the snapshot at full opacity from the first frame onward,
                // otherwise home would flash through the shrinking window.
                clipPath(holePath) {
                    drawImageStretched(pageImage, 1f)
                }
            }

            when (style) {
                // The card is one picture that grows: it holds, then dissolves
                // into the page behind it. See the frozen block above.
                WyuMorphStyle.Card -> {
                    val cardAlpha =
                        1f - smoothstep(CardWindowFadeStart, CardWindowFadeEnd, progress)
                    clipPath(holePath) {
                        drawCardWindow(cardImage, hole, cardAlpha)
                    }
                }

                // The tile becomes the window the way iOS 11 does it: its
                // *surface* fills the hole, its content sits on the hole's head
                // at its natural aspect and only the surface outlives the
                // content. Nothing printed on the tile is stretched by the
                // window's changing aspect — not even on the first frame, since
                // at progress 0 the hole *is* the tile and the natural rect
                // covers it exactly.
                WyuMorphStyle.Tile -> {
                    val fillAlpha = morphSurfaceAlpha(progress)
                    if (fillAlpha > 0.001f &&
                        hole.rect.width > 0f &&
                        hole.rect.height > 0f
                    ) {
                        clipPath(holePath) {
                            drawCardSurface(cardImage, hole.rect, fillAlpha)
                            val contentAlpha = fillAlpha * morphArtworkAlpha(progress)
                            if (contentAlpha > 0.001f) {
                                drawImageIntoRect(
                                    cardImage,
                                    tileContentRect(hole.rect, cardImage),
                                    contentAlpha,
                                )
                            }
                        }
                    }
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
    home: SharedHomeLayers?,
    reopenRequested: Boolean,
    style: WyuMorphStyle,
    onTap: () -> Unit,
    onSwap: () -> Unit,
    onReopened: () -> Unit,
    /** A press handed this window over to a new morph. It keeps animating in
     *  parallel from exactly where it was, but owns no global state. */
    retiring: Boolean = false,
    /** Window progress to resume from when taking over an interrupted open. */
    initialFraction: Float = 0f,
    titleStartBounds: Rect? = null,
    titleStartFontPx: Float = 0f,
    titleStartLinePx: Float = 0f,
    modifier: Modifier = Modifier,
    /** A parallel (retiring) close normally paints no backdrop, because the
     *  new window owns the screen. During the two layout frames between the
     *  preempt and the new window's creation, though, nothing else would paint
     *  one at all — the live home would flash at its default zoom and the next
     *  window would look like a replay from rest. Keeping this layer's backdrop
     *  until the new window exists closes that gap: both read the same global
     *  clocks, so the hand-over is a single continuous frame. */
    drawBackdrop: () -> Boolean = { true },
) {
    // Resume exactly where the interrupted open left off — no snap, no stop.
    val raw = remember(snapshot, page) { Animatable(initialFraction.coerceIn(0f, 1f)) }
    // Only the generation that owns the screen may drive the page content.
    // A retiring window keeps its own animation running in parallel but never
    // writes the shared scale/translation again.
    val epoch = remember(snapshot) { WyuMorphPageState.inputEpoch }
    // A tab switch started underneath this window takes the screen: the live
    // page is what the user is driving from then on. Painted capture and page
    // pull-back both belong to a home that is standing still, so they are given
    // up — sticky for the rest of this window, since a capture that came back
    // as the switch ended would jump to the zoom it was frozen at.
    val yielded = remember(snapshot) { mutableStateOf(false) }
    fun applyContent(value: Float) {
        if (yielded.value) return
        if (!retiring && WyuMorphPageState.ownsContent(epoch)) {
            WyuMorphPageState.contentProgress = value
        }
    }
    val cardImage = remember(snapshot) { snapshot.bitmap.asImageBitmap() }
    val wallpaperImage = remember(home?.wallpaper) { home?.wallpaper?.asImageBitmap() }
    val contentImage = remember(home?.content) { home?.content?.asImageBitmap() }
    val pageImage = remember(page) { page?.asImageBitmap() }
    val target = rememberStartBounds(snapshot)
    val screenRadius = rememberScreenCornerRadiusPx()
    val density = LocalDensity.current
    val liftPx = with(density) { WyuMorphPageState.ContentLift.toPx() }
    // Once the window has landed the overlay only waits for the page swap.
    // It must stop intercepting touches right then: the home behind it is the
    // real page and has to respond immediately, not after the overlay is gone.
    var landed by remember(snapshot) { mutableStateOf(false) }
    // Once a new container has taken the screen this window is background for
    // good. Its move may still be playing when the new opening lands, and it
    // must not pop back into view at that moment: it stays out of the way and
    // finishes behind the container that replaced it. Latched during
    // composition, so the paint pass never writes state.
    val newMorphOnScreen = !drawBackdrop()
    var everCovered by remember(snapshot) { mutableStateOf(false) }
    if (retiring && newMorphOnScreen) {
        SideEffect { everCovered = true }
    }
    // The backdrop clocks are global, so this close continues the very same
    // zoom/shrink/defocus the previous window left on screen. A retiring
    // parallel close does not retarget them at all — the new window owns them.
    // No press epoch in the key: a new tap must NOT restart (and thereby stop)
    // this animation. It keeps playing in parallel while the new morph starts
    // on its own Animatable. Only a deliberate reopen redirects this window.
    LaunchedEffect(snapshot, page, home, reopenRequested) {
        if (page == null) {
            // A parallel close whose snapshot never arrived has drawn nothing.
            // Detach it at once so it cannot leave a stuck overlay behind.
            if (retiring) onSwap()
            return@LaunchedEffect
        }
        if (reopenRequested && !retiring) {
            // Re-opening after a partial close is an open, not a close. The
            // global clocks just turn around from the pull on screen instead
            // of being rebuilt at the resting scale.
            MorphBackdropState.open()
            raw.animateOpen(0f) { applyContent(1f - it) }
            // The page stayed composed underneath, so it can simply be handed back.
            onReopened()
        } else {
            // The window goes home on the shared exit curve; the backdrop follows
            // on its own, softer spring, so the wallpaper and icons settle a beat
            // later instead of arriving with the card. The wallpaper outlasts both.
            // All three have to finish before the overlay comes off, otherwise the
            // last frame would differ from the live home underneath it.
            // A retiring window is a parallel visual layer only: it must not
            // retarget the shared clocks, which already belong to the new
            // window opening on top of it.
            // A retiring window is a parallel visual layer only: it must not
            // retarget the shared clocks, which already belong to the new
            // window opening on top of it.
            if (!retiring) MorphBackdropState.close()
            coroutineScope {
                launch {
                    raw.animateExit(1f) { applyContent(1f - it) }
                }
                // Hold the window until the backdrop is home, so the swap
                // happens under pixels that look unchanged.
                launch { if (!retiring) MorphBackdropState.awaitAtRest() }
            }
            applyContent(1f)
            landed = true
            // The window has landed, the backdrop is exactly home, so the swap
            // happens under pixels that look unchanged. When retiring, the host
            // only detaches this overlay — global state belongs to the new morph.
            onSwap()
        }
    }

    // The home is a still capture for the whole return: nothing the live page
    // does can be seen through it. When a tab switch begins underneath, the
    // whole window stands down — capture, scrim and the card still flying home —
    // because a page that is already switching must not have a page-shaped
    // window sweeping across it. What is left of the move is invisible, so the
    // overlay detaches as soon as the fade is done instead of holding
    // `returnSettling` (and the source card's hole) for a move nobody can see.
    LaunchedEffect(snapshot) {
        snapshotFlow { WyuMorphPageState.homePagerMoving }.first { it }
        yielded.value = true
        if (!retiring && WyuMorphPageState.ownsContent(epoch)) {
            WyuMorphPageState.contentProgress = 1f
        }
        Log.d("WyuMorph", "return hands the screen back to the page switch")
        delay(YieldFadeMillis.toLong() + 24L)
        onSwap()
    }
    // Short enough that the pager's first frames are already on the live page,
    // long enough that the window does not cut away in a single frame.
    val yieldAlpha by animateFloatAsState(
        targetValue = if (yielded.value) 0f else 1f,
        animationSpec = tween(durationMillis = YieldFadeMillis),
        label = "returnCapture",
    )

    Box(
        Modifier
            .fillMaxSize()
            .then(modifier)
            // Faded as one piece, so nothing of the leaving page is left over the
            // one that is sliding in.
            .then(
                if (yieldAlpha < 1f) {
                    Modifier.graphicsLayer { alpha = yieldAlpha }
                } else {
                    Modifier
                },
            ),
    ) {
        if (page == null && !retiring) {
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
        // A retiring close is only its own window — no full-screen home
        // capture — so the new morph (and its live destination page) shows
        // through everywhere else from the first frame.
        if (page != null && (!retiring || (!everCovered && !newMorphOnScreen))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        // The cut-out follows the window's landing rebound frame
                        // for frame: if it kept the un-rebounded rect, the dip
                        // would leave a ring that the backdrop no longer covers.
                        val hole = landedWindow(
                            morphHole(
                                progress = 1f - raw.value,
                                start = target,
                                width = size.width,
                                height = size.height,
                                startRadius = snapshot.radiusPx,
                                screenRadius = screenRadius,
                                style = style,
                            ),
                            fraction = raw.value,
                        )
                        clipPath(squirclePath(hole.rect, hole.radius), ClipOp.Difference) {
                            this@drawWithContent.drawContent()
                        }
                    },
            ) {
                MorphBackdrop(
                    wallpaper = wallpaperImage,
                    content = contentImage,
                    // The three clocks the opening left behind, still running.
                    // Only `travel` is underdamped (icon rebound); the
                    // wallpaper stays critically damped and the defocus comes
                    // back ahead of the shrink. None of them restarts here.
                    strength = { MorphBackdropState.travel.value },
                    wallpaperStrength = { MorphBackdropState.wallpaper.value },
                    defocusStrength = { MorphBackdropState.defocus.value },
                )
            }
        }
        Canvas(
            Modifier
                .fillMaxSize(),
        ) {
            // A handed-over window is background only. While a new morph owns
            // the screen it paints nothing at all: it is still playing, but the
            // container that replaced it is completely on top, so not one icon
            // or corner may show through the hole the new window grows out of.
            // Only in the frames between the press and the new window's
            // creation — when no other layer would cover the home — does this
            // window keep painting, and then it keeps painting the backdrop too.
            // Sticky: once it has been covered it never comes back, even if its
            // own move outlives the new container's opening.
            if (retiring && (everCovered || newMorphOnScreen)) return@Canvas
            val pageBitmap = pageImage ?: return@Canvas
            val fraction = raw.value
            // The window stays sharp and fully opaque for the whole move: a
            // leftover layer is an animation that is still being played, not a
            // sheet dissolving into the background. It is removed the frame it
            // has landed on the card, which is the frame it matches the home
            // card pixel for pixel, so the hand-over has nothing to show.
            val windowAlpha = 1f
            if (windowAlpha <= 0.001f) return@Canvas
            // Same single curve as opening, so close mirrors open exactly —
            // plus the landing rebound, which only exists on the way home.
            val hole = landedWindow(
                morphHole(
                    progress = 1f - fraction,
                    start = target,
                    width = size.width,
                    height = size.height,
                    startRadius = snapshot.radiusPx,
                    screenRadius = screenRadius,
                    style = style,
                ),
                fraction = fraction,
            )
            val holePath = squirclePath(hole.rect, hole.radius)

            // Mirrors the open: the scrim belongs to the home capture and lifts
            // only as the capture itself leaves, never before.
            val dim = MorphBackdropDim * (1f - fraction)
            if (!retiring && dim > 0f) {
                clipPath(holePath, ClipOp.Difference) {
                    drawRect(Color.Black.copy(alpha = dim))
                }
            }

            // The window is filled by the page snapshot, never by the live page,
            // so home never shows through — and it is drawn *sharp*: no defocus,
            // no blur under the mask at any point of the close. A blur inside the
            // container would make the window read as a sheet of frosted glass
            // sitting on the home, while the close is meant to read as the page
            // itself being pulled back into the icon. The only motion inside the
            // window is the sink and shrink below.
            val contentScale = 1f - WyuMorphPageState.ContentShrink * fraction
            clipPath(holePath) {
                withTransform({
                    translate(0f, liftPx * fraction)
                    scale(contentScale, contentScale, pivot = center)
                }) {
                    drawImageStretched(pageBitmap, windowAlpha)
                }
            }

            when (style) {
                // Card pipeline, unchanged from before the tile work: the card
                // condenses out of the page, one picture over the whole hole.
                WyuMorphStyle.Card -> {
                    val cardAlpha = smoothstep(
                        CardWindowReturnFadeStart,
                        CardWindowReturnFadeEnd,
                        fraction,
                    ) * windowAlpha
                    if (cardAlpha > 0.001f) {
                        clipPath(holePath) {
                            drawCardWindow(cardImage, hole, cardAlpha)
                        }
                    }
                }

                // Mirror of the opening: the tile's surface refills the window
                // while the page snapshot sinks away, and the content comes back
                // on the window's head, shrinking proportionally until it lands
                // on the tile's own rect — so the hand-over is the tile itself,
                // never a stretched one.
                WyuMorphStyle.Tile -> {
                    val fillAlpha = morphSurfaceReturnAlpha(fraction) * windowAlpha
                    if (fillAlpha > 0.001f) {
                        clipPath(holePath) {
                            drawCardSurface(cardImage, hole.rect, fillAlpha)
                            val contentAlpha = fillAlpha * morphArtworkReturnAlpha(fraction)
                            if (contentAlpha > 0.001f) {
                                drawImageIntoRect(
                                    cardImage,
                                    tileContentRect(hole.rect, cardImage),
                                    contentAlpha,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (!retiring) {
            FlightTitle(
                snapshot = snapshot,
                progress = { raw.value },
                forward = false,
                returnStartBounds = titleStartBounds,
                returnStartFontPx = titleStartFontPx,
                returnStartLinePx = titleStartLinePx,
            )
        }
        if (page != null && !landed && !retiring) {
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
                    .pointerInput(target, retiring) {
                        // A retiring window owns nothing: it must not take the
                        // press that is meant for the page that just opened on
                        // top of it, nor reopen itself.
                        if (retiring) return@pointerInput
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
    returnStartBounds: Rect? = null,
    returnStartFontPx: Float = 0f,
    returnStartLinePx: Float = 0f,
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
    val from = if (forward) startBounds else (returnStartBounds ?: targetBounds)
    val to = if (forward) targetBounds else startBounds
    val fromFont = if (forward) startFontPx else (returnStartFontPx.takeIf { it > 0f } ?: targetFontPx)
    val toFont = if (forward) targetFontPx else startFontPx
    val fromLine = if (forward) startLinePx else (returnStartLinePx.takeIf { it > 0f } ?: targetLinePx)
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
