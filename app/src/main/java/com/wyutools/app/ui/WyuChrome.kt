package com.wyutools.app.ui

import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.roundToInt

val WyuNavigatorBarHeight: Dp = 58.dp

/**
 * How much of the press swell survives horizontally on the navigator thumb.
 * The drag animation scales both axes to 78/56 while pressed; on a thumb that
 * is already one full cell wide, that growth reads as too long and runs over
 * the neighbouring icons, so the width keeps only this fraction of it while
 * the vertical bloom stays untouched.
 */
private const val ThumbPressWidenTrim = 0.82f

internal val WyuCollapsedBarHeight = 56.dp
internal val WyuLargeTitleHeight = 46.dp
// 首页三个页签的大标题沿用原来的 28dp 页边距（比下方内容多一级缩进），
// 字号仍比折叠态大一号。这三项也供切换动画（CardMorph 的 FlightTitle）取用，
// 两处必须同源。
internal val WyuLargeTitlePadding = 28.dp
// 带返回键的子页面从 16dp 起，给返回箭头留出的位置和内容对齐。
internal val WyuNestedTitlePadding = 16.dp
/**
 * 非卡片文字的左对齐线：跟展开态大标题同一个起点。
 *
 * 卡片仍停在 16dp 页边距上，所以页面里那些直接铺在背景上的说明文字、分区标题
 * 要自己补上两者的差（16dp 的页面补 12dp，20dp 的补 8dp）。
 */
internal val WyuTextInset = WyuLargeTitlePadding

internal val WyuLargeTitleFontSize = 36.sp
internal val WyuLargeTitleLineHeight = 44.sp

/** 关键数值（余额、电量、接下来要上的课）统一用最重字重压住版面。 */
internal val WyuEmphasisWeight = FontWeight.Black

internal val LocalWyuPageBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** Wallpaper-only backdrop, so cards and buttons frost the photo without sampling themselves. */
internal val LocalWyuGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** True once the recording layers have real layout coordinates. */
internal val LocalWyuGlassReady = staticCompositionLocalOf { false }

/**
 * Delegating backdrop whose instance identity changes once the recording layer
 * is positioned. drawBackdrop nodes compare backdrop identity on update and
 * invalidate their cache, so the cold-start frame with no layer coordinates is
 * followed by a redraw that actually samples the wallpaper.
 */
internal class WyuDelegatingBackdrop(private val delegate: Backdrop) : Backdrop {
    override val isCoordinatesDependent: Boolean get() = delegate.isCoordinatesDependent
    override val offsetResidualX: Float get() = delegate.offsetResidualX
    override val offsetResidualY: Float get() = delegate.offsetResidualY

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
        downscaleFactor: Float,
    ) {
        with(delegate) { drawBackdrop(density, coordinates, layerBlock, downscaleFactor) }
    }
}

/**
 * Backdrop for inputs that live in a dialog. 弹窗在独立窗口里，拿不到页面那层
 * 墙纸，[LocalWyuGlassBackdrop] 因此被置空；对话框再把页面层登记到这里，让
 * 输入框仍能模糊身后的内容。弹窗内容不在页面层里，所以采样它不会自引用。
 */
internal val LocalWyuDialogInputBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/** Extra 0..1 collapse driven by overscroll drag on pages that cannot scroll. */
internal val LocalWyuCollapseBoost = staticCompositionLocalOf<() -> Float> { { 0f } }

/** True while the card morph draws the title itself, so the page must not draw it too. */
internal object WyuMorphPageState {
    var titleHidden by mutableStateOf(false)

    /** False while the open morph runs; tool data refreshes wait until it lands. */
    var morphSettled by mutableStateOf(true)

    /** 0 = page content slightly smaller and lower, 1 = settled. Both morph
     *  directions drive it; read it only inside layer/draw scopes. */
    var contentProgress by mutableFloatStateOf(1f)

    const val ContentShrink = 0.04f
    val ContentLift = 16.dp

    /** Current visible page-title geometry in window coordinates, captured by
     *  the top bar from its current collapse fraction. Plain fields on purpose:
     *  writing them must not trigger recomposition. The return morph reads them
     *  so the flying title leaves from where it actually sits (collapsed or
     *  expanded) instead of snapping to the expanded large-title spot first. */
    var exitTitleBounds: Rect? = null
    var exitTitleFontPx = 0f
    var exitTitleLinePx = 0f

    /** Where the bar title actually sits on pages that never show a large
     *  title. The opening morph has no large-title slot to fly into there, so
     *  it lands on this instead; state (not a plain field) so the flight picks
     *  it up on the first frame the destination has laid out. */
    var collapsedTitleBounds: Rect? by mutableStateOf(null)
    var collapsedTitleFontPx by mutableFloatStateOf(0f)
    var collapsedTitleLinePx by mutableFloatStateOf(0f)

    /** While true the page scaffold skips its own wallpaper. A morph capture
     *  sets it for exactly one frame so the NavHost records the page content on
     *  a transparent background; the window backdrop keeps painting the very
     *  same wallpaper underneath, so nothing changes on screen. Read in the
     *  draw phase only, so toggling it never recomposes. */
    var suppressPageBackground by mutableStateOf(false)

    /** True from the start of a return until 50ms after it lands. A card tap
     *  waits on it so its snapshot is never taken from the pulled-back home,
     *  which would otherwise open the next morph from a shrunk, shifted card. */
    var returnSettling by mutableStateOf(false)

    /** True while *any* morph is on screen — opening, cancelling or returning.
     *  Maintained by MainActivity, so user input outranks all three phases. */
    var morphActive by mutableStateOf(false)

    /** True while the home pager is actually moving between tabs, from a tap or
     *  a swipe. The return morph paints the home from a still capture, so a
     *  switch that starts underneath it would play invisibly and only its end
     *  state would be seen once the overlay comes off. The return window watches
     *  this and hands the screen back to the live page the moment it flips. */
    var homePagerMoving by mutableStateOf(false)

    /** A pure opening morph answers only to BACK. Every other press is
     *  deliberately ignored by [interruptMorph]: the destination page is still
     *  settling, and bumping the generation / snapping [contentProgress] would
     *  make it jump for a frame. Hit-testing is separate: CardMorphOverlay
     *  passes presses inside the container through once the window is the live
     *  page, so those taps must stay neutral here. Once BACK starts the
     *  interrupt, this flips false and the very next press may open another
     *  card. */
    var openingOnly by mutableStateOf(false)

    /** Generation of the gesture that currently owns the screen. A press does
     *  not cancel a running animation: it only advances this generation. The
     *  old morph keeps playing its own Animatable in parallel, while the new
     *  one starts immediately on a fresh one. */
    var inputEpoch by mutableIntStateOf(0)
        private set

    /** Plain field, written by the active window's animation each frame and
     *  read only when a press hands the window over to a parallel close. */
    var morphWindowProgress = 0f

    /** Non-consuming DOWN hook. The press wins instantly: it ends every wait,
     *  puts the page content back at rest and starts a new generation. It never
     *  touches any Animatable, so no animation is stopped or waited out. */
    fun interruptMorph() {
        // flyingRoute is set synchronously at the very start of open/close,
        // so it covers the frame before morphActive has been published.
        if (!morphActive && flyingRoute == null) return
        // During the opening animation only BACK may interrupt. A stray touch
        // here must not bump the generation or reset contentProgress, or the
        // page would visibly jump out of its settle for one frame.
        if (openingOnly) return
        inputEpoch++
        // This is the fix for the "element drops and shrinks" bug: a morph
        // that is being left behind must not keep driving the page's scale and
        // translation, and the next morph must inherit a fully settled page.
        contentProgress = 1f
        returnSettling = false
    }

    /** Only the newest generation may write [contentProgress]. Old morphs
     *  still animate their windows in parallel, but leave the page alone. */
    fun ownsContent(epoch: Int): Boolean = inputEpoch == epoch

    /** Route whose own copy is currently in flight.
     *
     *  The page keeps the slot — layout must never move when an element leaves —
     *  but draws nothing in it, so the animated copy is the only one on screen.
     *  Two things need that: the capture (the home layers are recorded for the
     *  morph's backdrop, and a card baked into them would sit frozen under the
     *  window for the whole flight), and the close's landing rebound, which
     *  shrinks the window below the card it came from — without the hole, the
     *  live card would show through that reveal as a second, overlapping copy.
     *
     *  Read it in draw scopes only (a `graphicsLayer` block), never in
     *  composition: a capture sets it and records in the very same frame. */
    var flyingRoute by mutableStateOf<String?>(null)

    /** Returns once no open morph is running, so refreshes never land mid-animation. */
    suspend fun awaitSettled() {
        if (!morphSettled) snapshotFlow { morphSettled }.first { it }
    }
}

/**
 * The backdrop of the page currently on screen. Dialogs live above the page in
 * the popup host, so their frosted cards sample the page through this registry.
 */
internal object WyuPageBackdrops {
    private val stack = mutableListOf<LayerBackdrop>()
    var current by mutableStateOf<LayerBackdrop?>(null)
        private set

    fun attach(backdrop: LayerBackdrop) {
        stack.remove(backdrop)
        stack.add(backdrop)
        current = backdrop
    }

    fun detach(backdrop: LayerBackdrop) {
        if (stack.remove(backdrop)) current = stack.lastOrNull()
    }
}

private object WyuNoOpOverscrollEffect : OverscrollEffect {
    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset,
    ): Offset = performScroll(delta)

    override suspend fun applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity,
    ) {
        performFling(velocity)
    }

    override val isInProgress: Boolean get() = false
}

private object WyuNoOpOverscrollFactory : OverscrollFactory {
    override fun createOverscrollEffect(): OverscrollEffect = WyuNoOpOverscrollEffect

    override fun hashCode(): Int = System.identityHashCode(this)

    override fun equals(other: Any?): Boolean = other === this
}

@Composable
fun WyuPageScaffold(
    progress: () -> Float,
    snackbarHost: @Composable () -> Unit = {},
    maskCollapseHeight: Dp = 0.dp,
    maskExtension: Dp = 0.dp,
    maskBaseAlpha: Float = 0f,
    // Off for pages whose bar never expands: the progressive blur costs a live
    // texture pass every frame and only pays off when a large title sits over
    // it.
    topBarBlur: Boolean = true,
    backgroundBrush: Brush? = null,
    overlay: @Composable (LayerBackdrop?) -> Unit = {},
    topBar: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    val surface = MiuixTheme.colorScheme.surface
    val wallpaper = LocalWyuWallpaper.current
    val dark = LocalWyuDarkTheme.current
    val wallpaperBrush = remember(wallpaper, dark, surface) {
        wallpaper.background(dark, surface)
    }
    val pageBrush = backgroundBrush ?: wallpaperBrush
    val customWallpaper = if (wallpaper == WyuWallpaper.Custom) {
        LocalWyuCustomWallpaper.current
    } else {
        null
    }
    val brightnessLight by WyuAppearance.customBrightnessLight.collectAsState()
    val brightnessDark by WyuAppearance.customBrightnessDark.collectAsState()
    val customDim = wyuCustomWallpaperDim(if (dark) brightnessDark else brightnessLight)
    val softGlassEnabled by WyuAppearance.softGlass.collectAsState()
    val softGlassPercent by WyuAppearance.softGlassBlur.collectAsState()
    val liquidPercent by WyuAppearance.liquidGlassBlur.collectAsState()
    val currentCardTheme by WyuAppearance.cardTheme.collectAsState()
    val isLiquidTheme = currentCardTheme == WyuCardTheme.Liquid
    // 烘焙底图的模糊量跟随当前主题自己的滑条（液态/柔光各自独立）。
    // 主题必须 collectAsState：直接读 .value 在切主题时不会重组，
    // 底图就一直留着上一个主题的 sigma（冷启动切液态时表现为滑条失效）。
    val underlayBlurPercent = if (isLiquidTheme) liquidPercent else softGlassPercent
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val windowSize = LocalWindowInfo.current.containerSize
    // Seed with the window size so the first glass frame can be composed
    // without waiting for layout; onGloballyPositioned corrects it if needed.
    val pageBounds = remember(configuration, density) {
        val w = if (windowSize.width > 0) {
            windowSize.width
        } else {
            (configuration.screenWidthDp * density.density).roundToInt()
        }
        val h = if (windowSize.height > 0) {
            windowSize.height
        } else {
            (configuration.screenHeightDp * density.density).roundToInt()
        }
        mutableStateOf(
            if (w > 0 && h > 0) Rect(Offset.Zero, Size(w.toFloat(), h.toFloat())) else null,
        )
    }
    // Cards draw the soft glass straight from this pre-blurred wallpaper copy;
    // only the bars, tiny buttons and the week header keep live backdrops.
    var glassLayerReady by remember { mutableStateOf(false) }
    var pageLayerReady by remember { mutableStateOf(false) }
    // Page-local glass sampling origin: a NavHost slide translates the page
    // with a graphics layer, which must not change the underlay key or the
    // per-card sampling offset (that re-records every glass layer each frame).
    val contentCoordinates = remember { mutableStateOf<LayoutCoordinates?>(null) }
    val glassUnderlayState = remember { mutableStateOf<WyuGlassUnderlay?>(null) }
    val bounds = pageBounds.value
    val presetWallpaperSource = remember(
        wallpaper, dark, surface, bounds?.width, bounds?.height,
    ) {
        val b = bounds
        if (!softGlassEnabled && wallpaper != WyuWallpaper.Custom && b != null) {
            wyuPresetWallpaperImage(
                wallpaper = wallpaper,
                dark = dark,
                surface = surface,
                widthPx = b.width.roundToInt(),
                heightPx = b.height.roundToInt(),
            )
        } else {
            null
        }
    }
    val glassUnderlay = if (
        softGlassEnabled && customWallpaper != null && bounds != null
    ) {
        val current = glassUnderlayState.value
        if (current == null || current.source !== customWallpaper ||
            current.origin != bounds.topLeft ||
            current.widthPx != bounds.width.roundToInt() ||
            current.heightPx != bounds.height.roundToInt()
        ) {
            // The first frame of a scaffold must already show the static
            // material; deferring it would draw live blur first and swap,
            // which reads as a blur flash. The cache makes this a cheap hit
            // for every scaffold after the first.
            wyuGlassUnderlay(
                image = customWallpaper,
                dim = customDim,
                blurSigmaPx = wyuSoftGlassScreenBlurPx(underlayBlurPercent),
                widthPx = bounds.width.roundToInt(),
                heightPx = bounds.height.roundToInt(),
                origin = bounds.topLeft,
            ).also { glassUnderlayState.value = it }
        } else {
            current
        }
    } else {
        null
    }
    LaunchedEffect(customWallpaper, customDim, softGlassEnabled, underlayBlurPercent, bounds) {
        if (!softGlassEnabled || customWallpaper == null) {
            glassUnderlayState.value = null
            return@LaunchedEffect
        }
        val b = bounds ?: return@LaunchedEffect
        val w = b.width.roundToInt()
        val h = b.height.roundToInt()
        val sigma = wyuSoftGlassScreenBlurPx(underlayBlurPercent)
        val current = glassUnderlayState.value
        if (current != null && current.matches(customWallpaper, customDim, sigma, w, h, b.topLeft)) {
            return@LaunchedEffect
        }
        // Coalesce slider drags; the previous underlay stays on screen until
        // the new one is ready.
        delay(120)
        glassUnderlayState.value = withContext(Dispatchers.Default) {
            wyuGlassUnderlay(
                image = customWallpaper,
                dim = customDim,
                blurSigmaPx = sigma,
                widthPx = w,
                heightPx = h,
                origin = b.topLeft,
            )
        }
    }
    // Soft glass off falls back to a Gaussian blur of the same static
    // wallpaper; bake it once per wallpaper/dim instead of replaying the live
    // textureBlur pipeline for every card on every frame.
    val gaussianSigmaPx = remember(density) { wyuGaussianSigmaPx(CardBlurRadius, density.density) }
    val gaussianUnderlayState = remember { mutableStateOf<WyuGlassUnderlay?>(null) }
    val gaussianWallpaper = customWallpaper ?: presetWallpaperSource
    val gaussianDim = if (customWallpaper != null) customDim else 0f
    val gaussianUnderlay = if (
        !softGlassEnabled && gaussianWallpaper != null && bounds != null
    ) {
        val current = gaussianUnderlayState.value
        if (current == null || current.source !== gaussianWallpaper ||
            current.origin != bounds.topLeft ||
            current.widthPx != bounds.width.roundToInt() ||
            current.heightPx != bounds.height.roundToInt()
        ) {
            wyuGlassUnderlay(
                image = gaussianWallpaper,
                dim = gaussianDim,
                blurSigmaPx = gaussianSigmaPx,
                widthPx = bounds.width.roundToInt(),
                heightPx = bounds.height.roundToInt(),
                origin = bounds.topLeft,
            ).also { gaussianUnderlayState.value = it }
        } else {
            current
        }
    } else {
        null
    }
    LaunchedEffect(
        customWallpaper, presetWallpaperSource, customDim,
        softGlassEnabled, gaussianSigmaPx, bounds,
    ) {
        if (softGlassEnabled || gaussianWallpaper == null) {
            gaussianUnderlayState.value = null
            return@LaunchedEffect
        }
        val b = bounds ?: return@LaunchedEffect
        val w = b.width.roundToInt()
        val h = b.height.roundToInt()
        val current = gaussianUnderlayState.value
        if (current != null &&
            current.matches(gaussianWallpaper, gaussianDim, gaussianSigmaPx, w, h, b.topLeft)
        ) {
            return@LaunchedEffect
        }
        delay(120)
        gaussianUnderlayState.value = withContext(Dispatchers.Default) {
            wyuGlassUnderlay(
                image = gaussianWallpaper,
                dim = gaussianDim,
                blurSigmaPx = gaussianSigmaPx,
                widthPx = w,
                heightPx = h,
                origin = b.topLeft,
            )
        }
    }
    val blurSupported = remember { isRuntimeShaderSupported() }
    val backdrop = if (blurSupported) {
        rememberLayerBackdrop {
            when {
                pageBrush != null -> drawRect(brush = pageBrush)
                customWallpaper != null -> drawWyuImageCover(customWallpaper, customDim)
                else -> drawRect(surface)
            }
            drawContent()
        }
    } else {
        null
    }
    val glassBackdrop = if (blurSupported && customWallpaper != null) {
        rememberLayerBackdrop {
            drawWyuImageCover(customWallpaper, customDim)
        }
    } else {
        null
    }
    DisposableEffect(backdrop) {
        if (backdrop != null) WyuPageBackdrops.attach(backdrop)
        onDispose { if (backdrop != null) WyuPageBackdrops.detach(backdrop) }
    }
    val publishedGlassBackdrop = remember(glassBackdrop, glassLayerReady) {
        glassBackdrop?.let(::WyuDelegatingBackdrop)
    }
    val publishedPageBackdrop = remember(backdrop, pageLayerReady) {
        backdrop?.let(::WyuDelegatingBackdrop)
    }
    val currentProgress = rememberUpdatedState(progress)
    val scope = rememberCoroutineScope()
    val stretchRangePx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val collapseRangePx = with(density) { 64.dp.toPx() }
    val pull = remember(stretchRangePx, collapseRangePx, scope) {
        WyuPagePullState(
            stretchRangePx = stretchRangePx,
            collapseRangePx = collapseRangePx,
            progress = { currentProgress.value() },
            scope = scope,
        )
    }
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                // Origin stays at zero: underlays are baked in page space and
                // cards sample relative to the page content. Tracking the
                // animated root position here would rebuild the wallpaper and
                // re-record every glass layer on every slide frame.
                val next = Rect(
                    Offset.Zero,
                    Size(
                        coordinates.size.width.toFloat(),
                        coordinates.size.height.toFloat(),
                    ),
                )
                if (pageBounds.value != next) pageBounds.value = next
            }
            .drawBehind {
                // Skipped for the one frame a morph capture records: the page
                // then comes out with a transparent background, which is what
                // lets the morph split home into a wallpaper layer and a
                // content layer that can move apart.
                if (WyuMorphPageState.suppressPageBackground) return@drawBehind
                when {
                    pageBrush != null -> drawRect(brush = pageBrush)
                    customWallpaper != null -> drawWyuImageCover(customWallpaper, customDim)
                    else -> drawRect(surface)
                }
            },
    ) {
        if (glassBackdrop != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(glassBackdrop)
                    .onGloballyPositioned { glassLayerReady = true },
            )
        }
        CompositionLocalProvider(
            LocalOverscrollFactory provides WyuNoOpOverscrollFactory,
            LocalWyuPageBackdrop provides publishedPageBackdrop,
            LocalWyuGlassBackdrop provides publishedGlassBackdrop,
            LocalWyuGlassReady provides (glassLayerReady && pageLayerReady),
            LocalWyuGlassUnderlay provides glassUnderlay,
            LocalWyuGaussianUnderlay provides gaussianUnderlay,
            LocalWyuPageCoordinates provides { contentCoordinates.value },
            LocalWyuCollapseBoost provides { pull.collapseBoost },
        ) {
            SubcomposeLayout(Modifier.fillMaxSize()) { constraints ->
                val topPlaceables = subcompose("wyu-top-bar") {
                    Box(Modifier.fillMaxWidth()) { topBar() }
                }.map { it.measure(constraints.copy(minHeight = 0)) }
                val topHeight = (topPlaceables.maxOfOrNull { it.height } ?: 0)
                    .coerceIn(0, constraints.maxHeight)

                val contentPlaceables = subcompose("wyu-content") {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .then(
                                if (backdrop != null) {
                                    Modifier
                                        .layerBackdrop(backdrop)
                                        .onGloballyPositioned {
                                            pageLayerReady = true
                                            contentCoordinates.value = it
                                        }
                                } else {
                                    Modifier
                                },
                            )
                            .nestedScroll(pull.connection)
                            .graphicsLayer {
                                val morph = WyuMorphPageState.contentProgress
                                translationY = pull.contentOffset +
                                    (1f - morph) * WyuMorphPageState.ContentLift.toPx()
                                val scale = 1f -
                                    WyuMorphPageState.ContentShrink * (1f - morph)
                                scaleX = scale
                                scaleY = scale
                            },
                    ) {
                        content(PaddingValues(top = topHeight.toDp()))
                    }
                }.map { it.measure(constraints) }

                val fadePx = 10.dp.roundToPx()
                val fraction = maxOf(currentProgress.value(), pull.collapseBoost)
                    .coerceIn(0f, 1f)
                val maxMaskHeight = (topHeight + fadePx + maskExtension.roundToPx())
                    .coerceAtMost(constraints.maxHeight)
                val collapsePx = maskCollapseHeight.roundToPx()
                    .coerceAtMost(topHeight)
                val maskHeight = (maxMaskHeight - (collapsePx * fraction).roundToInt())
                    .coerceIn(1, maxMaskHeight)
                val maskPlaceables = subcompose("wyu-top-mask") {
                    WyuTopBarMaterial(
                        backdrop = backdrop,
                        progress = {
                            maxOf(currentProgress.value(), pull.collapseBoost)
                                .coerceIn(0f, 1f)
                        },
                        baseAlpha = maskBaseAlpha,
                        blur = topBarBlur,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }.map { it.measure(Constraints.fixed(constraints.maxWidth, maskHeight)) }

                val overlayPlaceables = subcompose("wyu-overlay") {
                    Box(Modifier.fillMaxSize()) { overlay(backdrop) }
                }.map { it.measure(constraints) }

                layout(constraints.maxWidth, constraints.maxHeight) {
                    contentPlaceables.forEach { it.placeRelative(0, 0) }
                    maskPlaceables.forEach { it.placeRelative(0, 0) }
                    overlayPlaceables.forEach { it.placeRelative(0, 0) }
                    topPlaceables.forEach { it.placeRelative(0, 0) }
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            snackbarHost()
        }
    }
}

@Composable
private fun WyuTopBarMaterial(
    backdrop: LayerBackdrop?,
    progress: () -> Float,
    baseAlpha: Float,
    blur: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val blurModifier = if (backdrop != null && blur) {
        Modifier.progressiveTextureBlur(
            backdrop = backdrop,
            shape = RectangleShape,
            blurRadius = 28f,
            gradient = ProgressiveBlur.Top.copy(startFraction = 0.55f),
        )
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .graphicsLayer {
                val scrollAlpha = progress().coerceIn(0f, 1f)
                alpha = (baseAlpha + (1f - baseAlpha) * scrollAlpha).coerceIn(0f, 1f)
            }
            .then(blurModifier),
    )
}

@Composable
fun WyuActionBar(
    title: String,
    collapsedFraction: () -> Float,
    modifier: Modifier = Modifier,
    largeTitle: Boolean = true,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingAction: (@Composable () -> Unit)? = null,
    altTitle: String? = null,
    altFraction: () -> Float = { 0f },
) {
    val onSurface = MiuixTheme.colorScheme.onSurface
    val statusTop = rememberWyuStatusBarTopInset()
    val largeStyle = remember(onSurface) {
        TextStyle(
            fontSize = WyuLargeTitleFontSize,
            lineHeight = WyuLargeTitleLineHeight,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.2).sp,
            color = onSurface,
        )
    }
    val collapsedStyle = remember(onSurface) {
        TextStyle(
            fontSize = 18.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.1).sp,
            color = onSurface,
        )
    }
    val density = LocalDensity.current
    val largeFontPx = with(density) { largeStyle.fontSize.toPx() }
    val largeLinePx = with(density) { largeStyle.lineHeight.toPx() }
    val collapsedFontPx = with(density) { collapsedStyle.fontSize.toPx() }
    val collapsedLinePx = with(density) { collapsedStyle.lineHeight.toPx() }
    // Snapshot state on purpose: a settled page never re-runs its layer block,
    // so first layout must itself be able to finish the capture below.
    val largeTitleBounds = remember { mutableStateOf<Rect?>(null) }
    val collapsedTitleBounds = remember { mutableStateOf<Rect?>(null) }
    val boost = LocalWyuCollapseBoost.current
    // A bar without a large title publishes the collapsed spot as the morph
    // target; every other bar clears it so a stale rect can never be used.
    DisposableEffect(largeTitle) {
        if (largeTitle) {
            WyuMorphPageState.collapsedTitleBounds = null
        }
        onDispose {
            if (!largeTitle) {
                WyuMorphPageState.collapsedTitleBounds = null
            }
        }
    }
    val effectiveFraction: () -> Float = {
        (collapsedFraction() + boost()).coerceIn(0f, 1f)
    }
    // Records where the title actually sits right now (expanded, collapsed or
    // in between), so the return morph leaves from the visible geometry. Both
    // the layout callbacks and the draw phase call it: layout settles the very
    // first frame, while draw keeps the value fresh as the fraction moves.
    fun captureExitTitle() {
        val collapsed = collapsedTitleBounds.value ?: return
        val large = largeTitleBounds.value
        if (large == null) {
            // Pages that keep the bar collapsed have no large title to
            // interpolate from; the collapsed spot is the only geometry there is.
            WyuMorphPageState.exitTitleBounds = collapsed
            WyuMorphPageState.exitTitleFontPx = collapsedFontPx
            WyuMorphPageState.exitTitleLinePx = collapsedLinePx
            return
        }
        val f = effectiveFraction()
        WyuMorphPageState.exitTitleBounds = Rect(
            lerp(large.left, collapsed.left, f),
            lerp(large.top, collapsed.top, f),
            lerp(large.right, collapsed.right, f),
            lerp(large.bottom, collapsed.bottom, f),
        )
        WyuMorphPageState.exitTitleFontPx = lerp(largeFontPx, collapsedFontPx, f)
        WyuMorphPageState.exitTitleLinePx = lerp(largeLinePx, collapsedLinePx, f)
    }
    Box(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(statusTop))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WyuCollapsedBarHeight)
                    // 和展开态取同一个左边距，两个标题交叉淡入淡出时才不会横向错位。
                    .padding(
                        start = if (navigationIcon != null) {
                            WyuNestedTitlePadding
                        } else {
                            WyuLargeTitlePadding
                        },
                        end = 10.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (navigationIcon != null) {
                    navigationIcon()
                    Spacer(Modifier.width(2.dp))
                }
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    WyuSwitchingTitle(
                        primary = title,
                        secondary = altTitle,
                        fraction = altFraction,
                        style = collapsedStyle,
                        baseAlpha = {
                            // Read at draw time: the host subcomposition can miss this
                            // state change, but layer invalidation never does.
                            if (WyuMorphPageState.titleHidden) {
                                0f
                            } else if (largeTitle) {
                                effectiveFraction()
                            } else {
                                1f
                            }
                        },
                        onBounds = {
                            collapsedTitleBounds.value = it
                            if (!largeTitle) {
                                WyuMorphPageState.collapsedTitleBounds = it
                                WyuMorphPageState.collapsedTitleFontPx = collapsedFontPx
                                WyuMorphPageState.collapsedTitleLinePx = collapsedLinePx
                            }
                            captureExitTitle()
                        },
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }
            if (largeTitle) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(WyuLargeTitleHeight)
                        .padding(start = WyuLargeTitlePadding, end = 24.dp),
                ) {
                    WyuSwitchingTitle(
                        primary = title,
                        secondary = altTitle,
                        fraction = altFraction,
                        style = largeStyle,
                        baseAlpha = {
                            if (WyuMorphPageState.titleHidden) 0f else 1f - effectiveFraction()
                        },
                        onBounds = {
                            largeTitleBounds.value = it
                            captureExitTitle()
                        },
                        modifier = Modifier.graphicsLayer {
                            val f = effectiveFraction()
                            translationY = -f * 10.dp.toPx()
                            captureExitTitle()
                        },
                    )
                }
            }
        }
        if (largeTitle && floatingAction != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(
                        top = statusTop + WyuCollapsedBarHeight - 1.dp,
                        end = 10.dp,
                    )
                    .graphicsLayer {
                        // 51.dp carries the action from the large-title centre up
                        // into the collapsed row as the page scrolls.
                        translationY = -effectiveFraction() * 51.dp.toPx()
                    },
            ) {
                floatingAction()
            }
        }
    }
}

// Cross-fades the two page titles as the pager moves, blurring each side as
// it leaves the centre so the swap reads as a soft gradient instead of a cut.
@Composable
private fun WyuSwitchingTitle(
    primary: String,
    secondary: String?,
    fraction: () -> Float,
    style: TextStyle,
    baseAlpha: () -> Float,
    modifier: Modifier = Modifier,
    onBounds: ((Rect) -> Unit)? = null,
) {
    val blurPx = with(LocalDensity.current) { 10.dp.toPx() }
    Box(
        modifier.then(
            if (onBounds != null) {
                Modifier.onGloballyPositioned { onBounds(it.boundsInWindow()) }
            } else {
                Modifier
            },
        ),
    ) {
        Text(
            text = primary,
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.graphicsLayer {
                val f = if (secondary == null) 0f else fraction().coerceIn(0f, 1f)
                alpha = baseAlpha() * (1f - f)
                renderEffect = if (f > 0.01f && f < 0.99f) {
                    BlurEffect(f * blurPx, f * blurPx)
                } else {
                    null
                }
            },
        )
        if (secondary != null) {
            Text(
                text = secondary,
                style = style,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.graphicsLayer {
                    val f = fraction().coerceIn(0f, 1f)
                    alpha = baseAlpha() * f
                    renderEffect = if (f > 0.01f && f < 0.99f) {
                        BlurEffect((1f - f) * blurPx, (1f - f) * blurPx)
                    } else {
                        null
                    }
                },
            )
        }
    }
}

// A 44dp disc is barely wider than the captured backdrop padding, so the
// card-sized offsets clamp at the layer boundary and fringe the rim highlight.
internal const val TopBarGlassRefractionPx = 56f
internal const val TopBarGlassReflectionPx = 96f

/**
 * 一块控件玻璃底：投影、材质、裁剪，以及「要不要再叠容器色」。
 *
 * [onGlass] 为 true 表示材质已经交给着色器（自研液态或上游柔光），调用方
 * **不要**再叠 `wyuGlassControlWash()`：shader 自带 tint，叠一层实底色会双重
 * 取色，控件就比卡片深一截。
 */
internal class WyuControlGlass(
    val modifier: Modifier,
    val onGlass: Boolean,
    val dark: Boolean,
)

/**
 * 顶栏圆钮、文字胶囊、分段控件、底栏共用的一块玻璃底。
 *
 * 这几处原来各写一遍「液态 / 柔光 / 纯模糊」三选一的 `when`，参数还各读一次。
 * [wash] 是不走着色器时要压的底色（各控件深浅色不一样，由调用方给）。
 */
@Composable
internal fun rememberWyuControlGlass(
    backdrop: Backdrop?,
    shape: Shape,
    cornerRadius: Dp,
    blurRadius: Float,
    wash: Color,
    elevation: Dp = 3.dp,
): WyuControlGlass {
    val style = rememberWyuGlassStyle()
    val glassReady = LocalWyuGlassReady.current
    val liquidActive = backdrop != null && style.liquidGlass
    val useUpstreamGlass = backdrop != null && style.softGlass && glassReady && !style.liquid
    val glassModifier = when {
        backdrop == null -> Modifier
        liquidActive -> Modifier.wyuLiquidGlass(
            backdrop = backdrop,
            shape = shape,
            cornerRadius = cornerRadius,
            tuning = style.liquidTuning,
            dark = style.dark,
            baseTint = wyuLiquidControlTint(),
            blurPercent = style.liquidBlur,
            refractionThicknessPx = TopBarGlassRefractionPx,
            reflectionThicknessPx = TopBarGlassReflectionPx,
        )
        useUpstreamGlass -> Modifier.wyuUpstreamCardGlass(
            backdrop = backdrop,
            cornerRadius = cornerRadius,
            tuning = style.tuning,
            dark = style.dark,
            blurPercent = style.blur,
            tint = wyuSoftGlassSurfaceTint(),
        )
        else -> Modifier.textureBlur(backdrop = backdrop, shape = shape, blurRadius = blurRadius)
    }
    val onGlass = liquidActive || useUpstreamGlass
    val modifier = Modifier
        .shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.10f),
            spotColor = Color.Black.copy(alpha = 0.16f),
        )
        .then(glassModifier)
        .clip(shape)
        .background(if (onGlass) Color.Transparent else wash)
    return WyuControlGlass(modifier = modifier, onGlass = onGlass, dark = style.dark)
}

/**
 * The frosted surface shared by the top-bar buttons and the small text pills.
 * 莫奈 paints a flat tonal block; every other appearance samples the live page
 * content behind it. Keeping it in one place makes the disc and the capsule
 * read as the same material.
 */
@Composable
internal fun WyuChromeGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    cornerRadius: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val glass = rememberWyuControlGlass(
        backdrop = LocalWyuPageBackdrop.current,
        shape = shape,
        cornerRadius = cornerRadius,
        blurRadius = 24f,
        // 卡片同色，而不是写死的白：深色模式下白色会让这些玻璃钮比卡片亮
        // 出一截，看着就是"白块"。
        wash = wyuGlassControlWash(),
    )
    val dark = glass.dark
    Box(
        modifier = modifier
            .then(glass.modifier)
            .border(
                width = 0.5.dp,
                color = if (dark) {
                    Color.White.copy(alpha = 0.14f)
                } else {
                    Color.Black.copy(alpha = 0.06f)
                },
                shape = shape,
            ),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/**
 * A wide glass strip for a control that has no card of its own — the schedule
 * week slider. It borrows the pills' rim numbers rather than a card's: both are
 * the same 30dp height class, while a card-sized rim would swallow the band and
 * leave its middle flat grey.
 */
@Composable
internal fun WyuGlassCapsule(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 15.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    WyuChromeGlassSurface(
        modifier = modifier,
        shape = shape,
        cornerRadius = cornerRadius,
        content = content,
    )
}

/**
 * Icon-only top bar button. The frosted glass disc stays behind the icon in
 * both the expanded and collapsed states.
 */
@Composable
private fun WyuTopBarGlassButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconSize: Dp = 24.dp,
) {
    val dark = LocalWyuDarkTheme.current
    val onSurface = MiuixTheme.colorScheme.onSurface
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val iconAlpha by animateFloatAsState(
        targetValue = when {
            !enabled -> if (dark) 0.4f else 0.3f
            pressed -> if (dark) 0.53f else 0.6f
            else -> 1f
        },
        animationSpec = tween(100),
        label = "topBarIconAlpha",
    )
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
            ) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        // 圆形玻璃面的上游 GlassShape 必须给半径 = 尺寸一半；
        // 传 0dp 会被折射采样近似成多边形，滑动时边缘呈六边形毛刺。
        WyuChromeGlassSurface(Modifier.size(44.dp), cornerRadius = 22.dp) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = onSurface.copy(alpha = 0.9f),
                modifier = Modifier
                    .size(iconSize)
                    .graphicsLayer { alpha = iconAlpha },
            )
        }
    }
}

private val WyuGlassPillHeight = 30.dp

// Half the pill's height, so both ends are semicircles like the discs above.
private val WyuGlassPillCorner = 15.dp

/**
 * Word-labelled sibling of the glass disc, for chrome that needs a label rather
 * than an icon (返回本周 under the timetable). Same material, capsule instead of
 * a circle; [enabled] = false keeps it in place as a "you are here" tag.
 *
 * [width] pins the capsule to one size so a label that changes length (本周 ⇄
 * 返回本周) does not resize the control under the finger.
 */
@Composable
fun WyuGlassPillButton(
    text: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    width: Dp? = null,
) {
    val dark = LocalWyuDarkTheme.current
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = remember { RoundedCornerShape(WyuGlassPillCorner) }
    // A disabled pill is not a dead control here: it is the "you are already
    // on this week" tag, so it dims only enough to read as inactive.
    val alpha by animateFloatAsState(
        targetValue = when {
            !enabled -> if (dark) 0.55f else 0.62f
            pressed -> if (dark) 0.53f else 0.6f
            else -> 1f
        },
        animationSpec = tween(100),
        label = "glassPillAlpha",
    )
    Box(
        modifier = modifier
            .height(WyuGlassPillHeight)
            .then(if (width != null) Modifier.width(width) else Modifier)
            .clip(shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
            ) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        WyuChromeGlassSurface(
            modifier = if (width != null) Modifier.fillMaxSize() else Modifier.fillMaxHeight(),
            shape = shape,
            cornerRadius = WyuGlassPillCorner,
        ) {
            Text(
                text = text,
                style = TextStyle(fontSize = 11.sp, lineHeight = 13.sp),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.9f * alpha),
                modifier = Modifier.padding(horizontal = 11.dp),
            )
        }
    }
}

/** Icon-only top bar action; [contentDescription] is the accessibility label. */
@Composable
fun WyuTopBarIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    WyuTopBarGlassButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    )
}

/**
 * Slim frosted pill tab row: drag-to-switch with tick haptics, tap debounce and
 * the floating navigator's damped press-follow spring on the sliding thumb.
 * When [selectionFraction] is supplied the thumb mirrors it (pager driven)
 * instead of running its own selection animation.
 */
@Composable
fun WyuFloatingTabs(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    selectionFraction: (() -> Float)? = null,
    /** 选中块用不透明白色（iOS 分段控件观感），而不是默认的深色块。 */
    lightThumb: Boolean = false,
) {
    if (options.isEmpty()) return
    val dark = LocalWyuDarkTheme.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val backdrop = LocalWyuPageBackdrop.current
    val lastIndex = options.lastIndex
    val initialFraction = selectedIndex.coerceIn(0, lastIndex).toFloat()
    val damped = remember(lastIndex) {
        WyuDampedDragAnimation(
            animationScope = scope,
            initialValue = initialFraction,
            valueRange = 0f..lastIndex.toFloat(),
            visibilityThreshold = 0.001f,
            pressedScale = 0.96f,
        )
    }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(initialFraction) }
    var interacting by remember { mutableStateOf(false) }
    var interactionGen by remember { mutableIntStateOf(0) }
    var pressedIndex by remember { mutableIntStateOf(-1) }
    var lastSelectAt by remember { mutableLongStateOf(0L) }
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentSelected by rememberUpdatedState(selectedIndex)

    LaunchedEffect(selectionFraction) {
        val fraction = selectionFraction ?: return@LaunchedEffect
        snapshotFlow { fraction() }.collect { value ->
            if (dragging || pressedIndex >= 0) return@collect
            val target = value.coerceIn(0f, lastIndex.toFloat())
            if (interacting) {
                // Keep the pressed preview in place until the pager lands on it.
                if (abs(target - damped.value) < 0.01f) interacting = false
            } else {
                damped.snapToValue(target)
            }
        }
    }
    LaunchedEffect(selectedIndex, selectionFraction) {
        if (selectionFraction == null) {
            damped.updateValue(selectedIndex.coerceIn(0, lastIndex).toFloat())
        }
    }

    val shape = RoundedCornerShape(percent = 50)
    val glass = backdrop != null
    val scheme = MiuixTheme.colorScheme
    val containerColor = when {
        // 与卡片同源取色：深色下原来的 #2C2C2C 比卡片亮，看着发白。
        glass -> wyuGlassControlWash()
        dark -> Color(0xFF2C2C2C)
        else -> Color.White
    }
    val controlGlass = rememberWyuControlGlass(
        backdrop = backdrop,
        shape = shape,
        cornerRadius = 19.dp,
        blurRadius = 30f,
        wash = containerColor,
        elevation = 10.dp,
    )
    val indicatorColor = when {
        lightThumb && dark -> Color.White.copy(alpha = 0.22f)
        lightThumb -> Color.White
        dark -> Color.White.copy(alpha = 0.16f)
        else -> Color.Black.copy(alpha = 0.10f)
    }
    val borderColor = when {
        glass && dark -> Color.White.copy(alpha = 0.14f)
        glass -> Color.Black.copy(alpha = 0.06f)
        dark -> Color.White.copy(alpha = 0.08f)
        else -> Color.Black.copy(alpha = 0.04f)
    }
    val contentColor = if (dark) Color.White else Color.Black
    val selectedContentColor = contentColor
    val unselectedContentColor = contentColor

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .then(controlGlass.modifier)
            .border(0.5.dp, borderColor, shape)
            .padding(3.dp)
            .pointerInput(options.size, lastIndex) {
                fun revertPreview(target: Int) {
                    scope.launch {
                        damped.animateToValue(target.toFloat())
                        interacting = false
                    }
                }
                fun releasePreviewAfterLanding() {
                    val gen = interactionGen
                    scope.launch {
                        delay(450)
                        if (gen == interactionGen &&
                            pressedIndex < 0 &&
                            !dragging
                        ) {
                            interacting = false
                        }
                    }
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val cellPx = size.width / options.size.toFloat()
                    val startSelected = currentSelected
                    val downIndex = (down.position.x / cellPx).toInt().coerceIn(0, lastIndex)
                    pressedIndex = downIndex
                    damped.press()
                    if (downIndex != startSelected) {
                        // Slide the thumb under the finger immediately on press.
                        interactionGen++
                        interacting = true
                        damped.updateValue(downIndex.toFloat())
                    }
                    val startFraction = if (dragging) dragFraction else downIndex.toFloat()
                    var totalX = 0f
                    var totalY = 0f
                    var dragActive = false
                    var cancelled = false
                    var lastTick = startFraction.roundToInt()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null) {
                            cancelled = true
                            break
                        }
                        if (change.changedToUpIgnoreConsumed()) {
                            change.consume()
                            break
                        }
                        if (!change.pressed) {
                            cancelled = true
                            break
                        }
                        if (!dragActive) {
                            totalX += change.positionChange().x
                            totalY += change.positionChange().y
                            if (abs(totalX) > viewConfiguration.touchSlop &&
                                abs(totalX) > abs(totalY)
                            ) {
                                dragActive = true
                                dragging = true
                                interactionGen++
                                interacting = true
                                dragFraction = (startFraction + totalX / cellPx)
                                    .coerceIn(0f, lastIndex.toFloat())
                                lastTick = dragFraction.roundToInt()
                                damped.updateValue(dragFraction)
                                change.consume()
                            } else if (abs(totalY) > viewConfiguration.touchSlop) {
                                cancelled = true
                                break
                            }
                        } else {
                            val dx = change.positionChange().x
                            if (dx != 0f) {
                                change.consume()
                                dragFraction = (dragFraction + dx / cellPx)
                                    .coerceIn(0f, lastIndex.toFloat())
                                damped.updateValue(dragFraction)
                                val tick = dragFraction.roundToInt()
                                if (tick != lastTick) {
                                    lastTick = tick
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                            }
                        }
                    }
                    pressedIndex = -1
                    if (dragActive) {
                        val target = dragFraction.roundToInt().coerceIn(0, lastIndex)
                        dragging = false
                        damped.updateValue(target.toFloat())
                        damped.release()
                        if (target != startSelected) {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            currentOnSelect(target)
                        }
                        releasePreviewAfterLanding()
                    } else if (!cancelled) {
                        val now = SystemClock.uptimeMillis()
                        if (now - lastSelectAt >= 200L) {
                            lastSelectAt = now
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            if (downIndex != startSelected) {
                                currentOnSelect(downIndex)
                                // Release the preview if the pager transition is
                                // interrupted before it lands.
                                releasePreviewAfterLanding()
                            }
                            if (selectionFraction == null) {
                                damped.updateValue(downIndex.toFloat())
                            }
                            damped.release()
                        } else if (downIndex != startSelected) {
                            revertPreview(startSelected)
                            damped.release()
                        } else {
                            damped.release()
                        }
                    } else {
                        damped.release()
                        if (downIndex != startSelected) {
                            revertPreview(startSelected)
                        }
                    }
                }
            },
    ) {
        val cellWidth = maxWidth / options.size
        Box(
            modifier = Modifier
                .width(cellWidth)
                .fillMaxHeight()
                .graphicsLayer {
                    val velocity = damped.velocity / 10f
                    translationX = damped.value * size.width
                    scaleX = damped.scaleX /
                        (1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f))
                    scaleY = damped.scaleY *
                        (1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f))
                }
                .then(
                    if (lightThumb) {
                        Modifier
                            .shadow(
                                elevation = 2.dp,
                                shape = shape,
                                clip = false,
                                ambientColor = Color.Black.copy(alpha = 0.06f),
                                spotColor = Color.Black.copy(alpha = 0.10f),
                            )
                    } else {
                        Modifier
                    },
                )
                .clip(shape)
                .background(indicatorColor)
                .then(
                    if (lightThumb) {
                        Modifier.border(
                            width = 0.5.dp,
                            color = if (dark) {
                                Color.White.copy(alpha = 0.18f)
                            } else {
                                Color.Black.copy(alpha = 0.05f)
                            },
                            shape = shape,
                        )
                    } else {
                        Modifier
                    },
                ),
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .selectableGroup(),
        ) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val scale by animateFloatAsState(
                    targetValue = if (pressedIndex == index) 0.94f else 1f,
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 700f),
                    label = "floatingTabPress",
                )
                val textColor by animateColorAsState(
                    targetValue = if (selected) {
                        selectedContentColor.copy(alpha = 0.95f)
                    } else {
                        unselectedContentColor.copy(alpha = 0.55f)
                    },
                    animationSpec = tween(180),
                    label = "floatingTabText",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .semantics(mergeDescendants = true) {
                            role = Role.Tab
                            this.selected = selected
                            onClick(label = label) {
                                if (index != currentSelected) {
                                    currentOnSelect(index)
                                }
                                true
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = TextStyle(
                            fontSize = 13.sp,
                            lineHeight = 16.sp,
                        ),
                        color = textColor,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Page transition timings: the pushed page is a full-width sheet that slides
 * in from the right over 400ms while the page underneath is nudged left and
 * pressed down a touch; a pop slides the sheet back out, pushing the revealed
 * page right as it lifts back up and brightens.
 */
const val WyuNavEnterMillis = 400
const val WyuNavExitMillis = 400

/**
 * Accelerate-then-decelerate ease-in-out with a slightly longer deceleration
 * tail: cubic-bezier(0.4, 0, 0.1, 1). Zero velocity at both ends; the peak
 * arrives at ~27% of the duration, so the slowdown half is longer and both
 * velocity ramps are steeper than the Material standard curve.
 */
val WyuNavEasing = CubicBezierEasing(0.4f, 0f, 0.1f, 1f)

/**
 * Tab pager slide easing: same accelerate-then-decelerate shape as
 * [WyuNavEasing] but with a steeper ramp-in and a tighter stop,
 * cubic-bezier(0.25, 0, 0.02, 1) — peak speed arrives at ~15% of the
 * duration and the long tail is pure deceleration.
 */
val WyuTabEasing = CubicBezierEasing(0.25f, 0f, 0.02f, 1f)

/** How the covered page recedes under a push. */
const val WyuNavParentShiftFraction = 0.06f
const val WyuNavParentScale = 0.94f

/**
 * Corner radius the moving sheet wears while it travels.
 *
 * Taken from the display itself, so the travelling page has the same curve as
 * the phone's own glass: on a body with tighter or rounder corners the sheet
 * lands flush instead of guessing a fixed 48dp (which showed a wedge of
 * wallpaper in the corners on devices with a smaller radius).
 */
@Composable
private fun rememberWyuNavPageCorner(): Dp {
    val density = LocalDensity.current
    // One source of truth: the morph window reads the same value, so a card
    // opens into a page whose corner is already exactly this radius.
    val radiusPx = rememberDisplayCornerPx()
    return remember(radiusPx, density) { with(density) { radiusPx.toDp() } }
}

/** Black scrim laid over the page underneath a pushed page. */
const val WyuPageCoveredScrim = 0.40f
const val WyuPageDimInMillis = 400
const val WyuPageDimOutMillis = 400

/**
 * Hosts one navigation destination.
 *
 * A pushed page enters as a full-width sheet sliding in from the right,
 * holding large corners while it travels and squaring off in the landing
 * tail; the page underneath is nudged left, pressed down a touch and dims
 * under a 40% scrim. A pop slides the sheet back out, pushing the revealed
 * page right as it lifts back up, its dim scrim brightening away. All
 * animated values are read in the draw phase, so a transition never
 * recomposes page content. [startSharp] and [startDimmed] mark pages that
 * appear without a slide (cold start, card morphs, or a page revealed by a
 * pop), so they never flash rounded corners and a revealed page starts out
 * dimmed like the page it was under.
 */@Composable
fun WyuPageEffects(
    modifier: Modifier = Modifier,
    active: Boolean = true,
    covered: Boolean = false,
    leaving: Boolean = false,
    startSharp: Boolean = false,
    startDimmed: Boolean = false,
    content: @Composable () -> Unit,
) {
    val pageCorner = rememberWyuNavPageCorner()
    val corner = remember(pageCorner) {
        Animatable(if (startSharp) 0f else pageCorner.value)
    }
    val dim = remember { Animatable(if (startDimmed) WyuPageCoveredScrim else 0f) }
    LaunchedEffect(active, covered, leaving, pageCorner) {
        launch {
            dim.animateTo(
                targetValue = if (covered) WyuPageCoveredScrim else 0f,
                animationSpec = tween(
                    durationMillis = if (covered) WyuPageDimInMillis else WyuPageDimOutMillis,
                    easing = WyuNavEasing,
                ),
            )
        }
        when {
            // Detaching for a pop: the sheet picks its card corners back up
            // as it starts to move.
            leaving -> corner.animateTo(
                targetValue = pageCorner.value,
                animationSpec = tween(WyuNavExitMillis / 2, easing = WyuNavEasing),
            )
            // Landing from a push: hold the corners through the travel and
            // melt them away only in the final approach.
            active -> corner.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = WyuNavEnterMillis / 2,
                    delayMillis = WyuNavEnterMillis / 2,
                    easing = WyuNavEasing,
                ),
            )
            // Covered pages keep a square shape: the page underneath never
            // moves, so nothing should peek through its corners.
            else -> corner.animateTo(
                targetValue = 0f,
                animationSpec = tween(WyuNavEnterMillis / 2, easing = WyuNavEasing),
            )
        }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                val cornerPx = corner.value.dp.toPx()
                shape = RoundedCornerShape(cornerPx)
                clip = cornerPx > 0.5f
            },
    ) {
        content()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val alpha = dim.value
                    if (alpha > 0.001f) {
                        drawRect(Color.Black, alpha = alpha)
                    }
                },
        )
    }
}

/**
 * Paints the window behind the NavHost with the same wallpaper the pages use,
 * so the sliver a page fades or slides over reads as the app's own background
 * instead of the raw white or black window colour.
 */
@Composable
fun WyuWindowBackdrop(modifier: Modifier = Modifier) {
    val surface = MiuixTheme.colorScheme.surface
    val wallpaper = LocalWyuWallpaper.current
    val dark = LocalWyuDarkTheme.current
    val wallpaperBrush = remember(wallpaper, dark, surface) {
        wallpaper.background(dark, surface)
    }
    val customWallpaper = if (wallpaper == WyuWallpaper.Custom) {
        LocalWyuCustomWallpaper.current
    } else {
        null
    }
    val brightnessLight by WyuAppearance.customBrightnessLight.collectAsState()
    val brightnessDark by WyuAppearance.customBrightnessDark.collectAsState()
    val customDim = wyuCustomWallpaperDim(if (dark) brightnessDark else brightnessLight)
    Box(
        modifier
            .fillMaxSize()
            .then(
                when {
                    customWallpaper != null -> Modifier.drawBehind {
                        drawWyuImageCover(customWallpaper, customDim)
                    }

                    wallpaperBrush != null -> Modifier.background(wallpaperBrush)
                    else -> Modifier.background(surface)
                },
            ),
    )
}

@Composable
fun rememberWyuCollapseFraction(scrollState: ScrollState): State<Float> {
    val rangePx = with(LocalDensity.current) { 64.dp.toPx() }
    return remember(scrollState, rangePx) {
        derivedStateOf { (scrollState.value / rangePx).coerceIn(0f, 1f) }
    }
}

@Composable
fun rememberWyuCollapseFraction(listState: LazyListState): State<Float> {
    val rangePx = with(LocalDensity.current) { 64.dp.toPx() }
    return remember(listState, rangePx) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (listState.firstVisibleItemScrollOffset / rangePx).coerceIn(0f, 1f)
            }
        }
    }
}

@Composable
fun rememberWyuCollapseFraction(gridState: LazyGridState): State<Float> {
    val rangePx = with(LocalDensity.current) { 64.dp.toPx() }
    return remember(gridState, rangePx) {
        derivedStateOf {
            if (gridState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (gridState.firstVisibleItemScrollOffset / rangePx).coerceIn(0f, 1f)
            }
        }
    }
}

/**
 * Collapse fraction for a pager with more than two pages: blends the two
 * neighbouring pages' fractions by the continuous page position, so the top bar
 * keeps moving smoothly across every swipe.
 */
@Composable
fun rememberWyuMultiPageCollapseFraction(
    pagerState: PagerState,
    fractions: List<State<Float>>,
): State<Float> = remember(pagerState, fractions) {
    derivedStateOf {
        if (fractions.isEmpty()) {
            0f
        } else {
            val last = fractions.lastIndex.toFloat()
            val position = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                .coerceIn(0f, last)
            val index = position.toInt().coerceIn(0, fractions.lastIndex)
            val next = (index + 1).coerceAtMost(fractions.lastIndex)
            val t = (position - index).coerceIn(0f, 1f)
            val a = fractions[index].value
            val b = fractions[next].value
            a + (b - a) * t
        }
    }
}

/**
 * Collapse fraction for a two-page pager whose pages each drive their own
 * fraction: blends the two fractions by the pager's continuous position, so
 * the top bar slides between the collapsed and expanded poses together with
 * the pages instead of snapping when the current page flips mid-swipe.
 */
@Composable
fun rememberWyuPagerCollapseFraction(
    pagerState: PagerState,
    firstFraction: State<Float>,
    secondFraction: State<Float>,
): State<Float> = remember(pagerState, firstFraction, secondFraction) {
    derivedStateOf {
        val position = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
            .coerceIn(0f, 1f)
        val first = firstFraction.value
        val second = secondFraction.value
        first + (second - first) * position
    }
}

/**
 * Parent-side overscroll: rubber-bands filled pages, collapses the top bar when
 * a short page has nothing left to scroll, and folds leftover fling velocity
 * into the spring so edges rebound instead of stopping dead.
 */
@Stable
private class WyuPagePullState(
    private val stretchRangePx: Float,
    private val collapseRangePx: Float,
    private val progress: () -> Float,
    private val scope: CoroutineScope,
) {
    var contentOffset by mutableFloatStateOf(0f)
        private set
    var collapseBoost by mutableFloatStateOf(0f)
        private set

    private var stretchRaw = 0f
    private var collapseRaw = 0f
    private var settleJob: Job? = null

    val connection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput) return Offset.Zero
            val y = available.y
            if (y == 0f) return Offset.Zero
            cancelSettle()
            var consumed = 0f
            if (stretchRaw > 0f && y < 0f) {
                val take = minOf(-y, stretchRaw)
                stretchRaw -= take
                contentOffset = damped(stretchRaw)
                consumed = -take
            } else if (stretchRaw < 0f && y > 0f) {
                val take = minOf(y, -stretchRaw)
                stretchRaw += take
                contentOffset = damped(stretchRaw)
                consumed = take
            }
            if (collapseRaw > 0f && y > 0f) {
                val take = minOf(y, collapseRaw)
                collapseRaw -= take
                collapseBoost = (collapseRaw / collapseRangePx).coerceIn(0f, 1f)
                if (take > abs(consumed)) consumed = take
            }
            return Offset(0f, consumed)
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            if (source != NestedScrollSource.UserInput) return Offset.Zero
            val y = available.y
            if (y == 0f) return Offset.Zero
            cancelSettle()
            if (y < 0f && progress() < 1f) {
                collapseRaw = (collapseRaw - y).coerceIn(0f, collapseRangePx)
                collapseBoost = (collapseRaw / collapseRangePx).coerceIn(0f, 1f)
            }
            stretchRaw = (stretchRaw + y).coerceIn(-stretchRangePx, stretchRangePx)
            contentOffset = damped(stretchRaw)
            return Offset(0f, y)
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            settle(available.y)
            return available
        }
    }

    fun cancelSettle() {
        settleJob?.cancel()
        settleJob = null
    }

    private fun settle(velocityY: Float) {
        val collapsePulse = velocityY < 0f && progress() < 1f
        // A fling that stops at a list edge leaves its remaining velocity here; replay it
        // as an outward stretch so the edge rebounds instead of stopping dead.
        val stretchPulse = !collapsePulse && abs(velocityY) > 250f
        if (stretchRaw == 0f && collapseRaw == 0f && !collapsePulse && !stretchPulse) return
        cancelSettle()
        settleJob = scope.launch {
            coroutineScope {
                launch {
                    if (stretchRaw != 0f || stretchPulse) {
                        val velocity = when {
                            collapsePulse -> 0f
                            stretchPulse -> (velocityY * 1.2f).coerceIn(-7000f, 7000f)
                            else -> (velocityY * 0.75f).coerceIn(-6000f, 6000f)
                        }
                        animate(
                            initialValue = stretchRaw,
                            targetValue = 0f,
                            initialVelocity = velocity,
                            animationSpec = if (stretchPulse) {
                                spring(dampingRatio = 0.58f, stiffness = 200f)
                            } else {
                                spring(dampingRatio = 0.76f, stiffness = 340f)
                            },
                        ) { value, _ ->
                            stretchRaw = value.coerceIn(-stretchRangePx, stretchRangePx)
                            contentOffset = damped(stretchRaw)
                        }
                    }
                }
                launch {
                    if (collapseRaw != 0f || collapsePulse) {
                        val velocity = if (collapsePulse) {
                            (-velocityY * 0.8f).coerceAtMost(4000f)
                        } else {
                            0f
                        }
                        animate(
                            initialValue = collapseRaw,
                            targetValue = 0f,
                            initialVelocity = velocity,
                            animationSpec = spring(dampingRatio = 0.82f, stiffness = 520f),
                        ) { value, _ ->
                            collapseRaw = value.coerceIn(0f, collapseRangePx)
                            collapseBoost = (collapseRaw / collapseRangePx).coerceIn(0f, 1f)
                        }
                    }
                }
            }
            stretchRaw = 0f
            contentOffset = 0f
            collapseRaw = 0f
            collapseBoost = 0f
        }
    }

    private fun damped(distance: Float): Float {
        if (stretchRangePx <= 0f || distance == 0f) return 0f
        val t = (abs(distance) / stretchRangePx).coerceIn(0f, 1f)
        val factor = t * t * t / 3f - t * t + t
        return if (distance > 0f) factor * stretchRangePx else -factor * stretchRangePx
    }
}

@Composable
fun rememberWyuNavigatorBottomMargin(): Dp {
    val inset = rememberStickyInset(
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
    )
    return if (inset < 24.dp) 24.dp else inset + 8.dp
}

/**
 * Insets of a window that is still on screen but no longer visible collapse to
 * zero (the app parked behind Recents, or a task switch in flight). Every
 * edge-to-edge element that pads itself by such an inset then slides out of
 * place while the user can still see the app in the task switcher, and snaps
 * back on return.
 *
 * Keeping the last non-zero measurement pins the chrome instead: the bar never
 * moves, and the real value takes over again as soon as the window reports one.
 */
@Composable
private fun rememberStickyInset(inset: Dp): Dp {
    val last = remember { StickyInset() }
    if (inset > 0.dp) last.value = inset
    return if (inset > 0.dp) inset else last.value
}

private class StickyInset {
    var value: Dp = 0.dp
}

/** Status-bar height that survives the window being hidden. See [rememberStickyInset]. */
@Composable
internal fun rememberWyuStatusBarTopInset(): Dp =
    rememberStickyInset(WindowInsets.statusBars.asPaddingValues().calculateTopPadding())

private data class WyuNavTab(
    val label: String,
    val icon: ImageVector,
)

private val WyuNavTabs = listOf(
    WyuNavTab("常用", Icons.Outlined.Build),
    WyuNavTab("更多", Icons.Outlined.GridView),
    WyuNavTab("我", Icons.Outlined.Person),
)

@Composable
fun WyuNavigatorBar(
    selectedIndex: Int,
    selectionFraction: () -> Float,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val glassStyle = rememberWyuGlassStyle()
    val dark = glassStyle.dark
    val bottomMargin = rememberWyuNavigatorBottomMargin()
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val backdrop = LocalWyuPageBackdrop.current
    val lastIndex = WyuNavTabs.lastIndex
    val cellWidth = 88.dp
    val inset = 3.dp
    val pillWidth = cellWidth * WyuNavTabs.size + inset * 2
    val pillShape = RoundedCornerShape(percent = 50)
    // 与顶栏标题同一取色：当前页强调不再是写死的纯白/纯黑。
    val contentColor = MiuixTheme.colorScheme.onSurface
    val indicatorColor = if (dark) {
        Color.White.copy(alpha = 0.16f)
    } else {
        Color.Black.copy(alpha = 0.10f)
    }
    val glassContainerColor = when {
        // Default theme cards use surfaceContainer @ CardGlassAlpha; keep the
        // bottom bar on exactly the same wash instead of the soft-glass tint.
        !glassStyle.softGlassOn && !glassStyle.liquid -> wyuGlassControlWash()
        dark -> Color.Black.copy(alpha = glassStyle.tuning.cardTintAlphaDark)
        else -> Color.White.copy(alpha = glassStyle.tuning.cardTintAlphaLight)
    }
    val glassBackdrop = backdrop
    val barBackdrop = rememberLayerBackdrop()
    val thumbBackdrop = if (glassBackdrop != null) {
        rememberWyuCombinedBackdrop(glassBackdrop, barBackdrop)
    } else {
        null
    }
    // One fixed rim for every glass surface: the highlight belongs to the
    // material, not to the way the phone happens to be held.
    val glassSpecular = if (glassBackdrop != null) WyuGlassSpecular else null

    val initialFraction = selectionFraction().coerceIn(0f, lastIndex.toFloat())
    val damped = remember {
        WyuDampedDragAnimation(
            animationScope = scope,
            initialValue = initialFraction,
            valueRange = 0f..lastIndex.toFloat(),
            visibilityThreshold = 0.001f,
            pressedScale = 78f / 56f,
        )
    }
    val glassIconScale: () -> Float = { 1f + 0.02f * damped.pressProgress }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(initialFraction) }
    var pressedIndex by remember { mutableIntStateOf(-1) }
    // The thumb has exactly one source of truth: the live pager position.
    // A tap/drag may put it ahead of the pager (the page starts moving on
    // release); that lead is a hold with an explicit target plus a timeout,
    // and every new tap retargets it instead of being dropped.
    var holdTarget by remember { mutableFloatStateOf(Float.NaN) }
    var holdGen by remember { mutableIntStateOf(0) }
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentSelected by rememberUpdatedState(selectedIndex)
    val currentFraction by rememberUpdatedState(selectionFraction)
    fun pagerPosition(): Float = currentFraction().coerceIn(0f, lastIndex.toFloat())
    /** Keep the thumb on a committed target until the pager catches up. */
    fun holdThumb(target: Float) {
        holdTarget = target
        holdGen += 1
        val gen = holdGen
        // Never snap: the slide towards the finger / back to the committed
        // page must always be a spring, with no teleport in between.
        damped.updateValue(target)
        scope.launch {
            // A tab tween is 300ms; anything longer means the request never
            // landed, so reconcile instead of leaving a permanent lead.
            delay(360)
            if (gen == holdGen && holdTarget == target &&
                pressedIndex < 0 && !dragging
            ) {
                holdTarget = Float.NaN
                damped.updateValue(pagerPosition())
            }
        }
    }
    /**
     * Cancelled gesture or a tap on the page the thumb already sits on: hand
     * the thumb back to the committed page (the target of the switch in
     * flight, or the live pager position), never to a stale selectedIndex.
     */
    fun settleThumb() {
        val hold = holdTarget
        if (hold.isNaN()) {
            holdGen += 1
            damped.updateValue(pagerPosition())
        } else {
            holdThumb(hold)
        }
    }
    val thumbPressed = dragging || pressedIndex >= 0
    val thumbScaleX by animateFloatAsState(
        targetValue = if (thumbPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 600f),
        label = "navThumbScaleX",
    )
    val thumbScaleY by animateFloatAsState(
        targetValue = if (thumbPressed) 0.8f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 600f),
        label = "navThumbScaleY",
    )

    LaunchedEffect(Unit) {
        snapshotFlow { currentFraction() }.collect { fraction ->
            if (dragging || pressedIndex >= 0) return@collect
            val target = fraction.coerceIn(0f, lastIndex.toFloat())
            val hold = holdTarget
            // Mirror the pager exactly on every frame. A hold is released as
            // soon as the pager reaches its target; if the request was dropped
            // or the tween interrupted, holdThumb()'s timeout reconciles it.
            if (hold.isNaN() || abs(target - hold) < 0.01f) {
                holdTarget = Float.NaN
                damped.snapToValue(target)
            }
        }
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .padding(bottom = bottomMargin)
                .width(pillWidth)
                .height(WyuNavigatorBarHeight)
                .shadow(
                    elevation = 10.dp,
                    shape = pillShape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.10f),
                    spotColor = Color.Black.copy(alpha = 0.16f),
                )
                .then(
                    if (glassBackdrop != null) {
                        Modifier.layerBackdrop(barBackdrop)
                    } else {
                        Modifier
                    },
                )
                .then(
                    when {
                        glassBackdrop == null -> Modifier
                        // 底栏玻璃背景：液态玻璃主题用本地 shader，参数独立；
                        // 悬浮液态按钮（thumb）仍走原来的 lens 液态玻璃路径。
                        glassStyle.liquidGlass -> Modifier
                            .wyuLiquidGlass(
                                backdrop = glassBackdrop,
                                shape = pillShape,
                                cornerRadius = WyuNavigatorBarHeight / 2,
                                tuning = glassStyle.liquidTuning,
                                dark = dark,
                                baseTint = wyuLiquidControlTint(),
                                blurPercent = glassStyle.liquidBlur,
                            )
                            .graphicsLayer {
                                val width = size.width.coerceAtLeast(1f)
                                val s = lerp(
                                    1f,
                                    1f + 8.dp.toPx() / width,
                                    damped.pressProgress,
                                )
                                scaleX = s
                                scaleY = s
                            }
                            .drawBehind {
                                drawRect(Color.White.copy(alpha = 0.1f * damped.pressProgress))
                            }

                        // 底栏玻璃背景与卡片同一套上游材质；悬浮液态按钮（thumb）
                        // 仍走原来的 lens 液态玻璃路径。
                        glassStyle.softGlass -> Modifier
                            .wyuUpstreamCardGlass(
                                backdrop = glassBackdrop,
                                cornerRadius = WyuNavigatorBarHeight / 2,
                                tuning = glassStyle.tuning,
                                dark = dark,
                                blurPercent = if (glassStyle.softGlassOn) glassStyle.blur else 100,
                                tint = glassContainerColor,
                            )
                            .graphicsLayer {
                                val width = size.width.coerceAtLeast(1f)
                                val s = lerp(
                                    1f,
                                    1f + 8.dp.toPx() / width,
                                    damped.pressProgress,
                                )
                                scaleX = s
                                scaleY = s
                            }
                            .drawBehind {
                                drawRect(Color.White.copy(alpha = 0.1f * damped.pressProgress))
                            }

                        else -> Modifier.drawBackdrop(
                            backdrop = glassBackdrop,
                            shape = { pillShape },
                            effects = {
                                padding = maxOf(padding, 40.dp.toPx())
                                vibrancy()
                                forceDownscaleExp(1)
                                val blurRadius = wyuSoftGlassScreenBlurPx(
                                    when {
                                        glassStyle.liquid -> glassStyle.liquidBlur
                                        glassStyle.softGlassOn -> glassStyle.blur
                                        else -> 100
                                    },
                                )
                                if (blurRadius > 0.5f) blur(blurRadius, blurRadius)
                                lens(
                                    refractionHeight = 28.dp.toPx(),
                                    refractionAmount = 40.dp.toPx(),
                                )
                            },
                            onDrawFront = {
                                drawWyuSpecular(glassSpecular ?: WyuGlassSpecular, 0.75f)
                            },
                            onDrawSurface = {
                                drawRect(glassContainerColor)
                                drawRect(Color.White.copy(alpha = 0.1f * damped.pressProgress))
                            },
                        )
                    },
                )
                .then(
                    // Pre-33 devices have no runtime shader; keep a plain pill
                    // so the bar stays legible.
                    if (glassBackdrop == null) {
                        Modifier
                            .clip(pillShape)
                            .background(MiuixTheme.colorScheme.surfaceContainer)
                    } else {
                        Modifier
                    },
                )
                .padding(inset)
                .pointerInput(WyuNavTabs.size) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val cellPx = size.width / WyuNavTabs.size.toFloat()
                        val downIndex = (down.position.x / cellPx).toInt().coerceIn(0, lastIndex)
                        pressedIndex = downIndex
                        damped.press()
                        // Slide towards the finger with the damped spring, not
                        // a teleport. The pager may not move the thumb while
                        // the finger is down; on release each tap retargets
                        // the pager and the hold follows it.
                        damped.updateValue(downIndex.toFloat())
                        val startFraction = if (dragging) dragFraction else downIndex.toFloat()
                        var totalX = 0f
                        var totalY = 0f
                        var dragActive = false
                        var cancelled = false
                        var lastTick = startFraction.roundToInt()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null) {
                                cancelled = true
                                break
                            }
                            if (change.changedToUpIgnoreConsumed()) {
                                change.consume()
                                break
                            }
                            if (!change.pressed) {
                                cancelled = true
                                break
                            }
                            if (!dragActive) {
                                totalX += change.positionChange().x
                                totalY += change.positionChange().y
                                if (abs(totalX) > viewConfiguration.touchSlop &&
                                    abs(totalX) > abs(totalY)
                                ) {
                                    dragActive = true
                                    dragging = true
                                    // Keep the committed target: it is where a
                                    // later dropped gesture springs back to.
                                    dragFraction = (startFraction + totalX / cellPx)
                                        .coerceIn(0f, lastIndex.toFloat())
                                    lastTick = dragFraction.roundToInt()
                                    damped.updateValue(dragFraction)
                                    change.consume()
                                } else if (abs(totalY) > viewConfiguration.touchSlop) {
                                    cancelled = true
                                    break
                                }
                            } else {
                                val dx = change.positionChange().x
                                if (dx != 0f) {
                                    change.consume()
                                    dragFraction = (dragFraction + dx / cellPx)
                                        .coerceIn(0f, lastIndex.toFloat())
                                    damped.updateValue(dragFraction)
                                    val tick = dragFraction.roundToInt()
                                    if (tick != lastTick) {
                                        lastTick = tick
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    }
                                }
                            }
                        }
                        pressedIndex = -1
                        // No debounce: read the live position at release and
                        // let every tap retarget the pager, even one that
                        // interrupts a tween already in flight.
                        val livePosition = pagerPosition()
                        if (dragActive) {
                            val target = dragFraction.roundToInt().coerceIn(0, lastIndex)
                            dragging = false
                            damped.updateValue(target.toFloat())
                            damped.release()
                            if (!cancelled && abs(livePosition - target.toFloat()) > 0.001f) {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                currentOnSelect(target)
                                holdThumb(target.toFloat())
                            } else {
                                settleThumb()
                            }
                        } else {
                            if (!cancelled) {
                                view.performHapticFeedback(
                                    HapticFeedbackConstants.VIRTUAL_KEY,
                                )
                                if (abs(livePosition - downIndex.toFloat()) > 0.001f) {
                                    currentOnSelect(downIndex)
                                    // The thumb is already under the finger;
                                    // hold it on the new target until the
                                    // pager arrives.
                                    holdThumb(downIndex.toFloat())
                                } else {
                                    settleThumb()
                                }
                            } else {
                                settleThumb()
                            }
                            damped.release()
                        }
                    }
                },
        ) {
            if (glassBackdrop == null) {
                Box(
                    modifier = Modifier
                        .width(cellWidth)
                        .fillMaxHeight()
                        .graphicsLayer {
                            translationX = damped.value * size.width
                            scaleX = thumbScaleX
                            scaleY = thumbScaleY
                        }
                        .clip(RoundedCornerShape(percent = 50))
                        .background(indicatorColor),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .selectableGroup(),
            ) {
                WyuNavTabs.forEachIndexed { index, tab ->
                    val isSelected = index == selectedIndex
                    val pressScale by animateFloatAsState(
                        targetValue = if (pressedIndex == index) 0.9f else 1f,
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 700f),
                        label = "navigatorPress",
                    )
                    WyuNavigatorTab(
                        tab = tab,
                        selected = isSelected,
                        tint = contentColor,
                        // The thumb is exactly one cell wide, so its overlap
                        // with cell `index` is 1 - |offset|. The tab brightens
                        // by precisely the fraction the slider covers it,
                        // continuously, instead of flipping at the page
                        // halfway point.
                        highlight = {
                            (1f - abs(damped.value - index.toFloat()))
                                .coerceIn(0f, 1f)
                        },
                        scale = if (glassBackdrop != null) {
                            glassIconScale
                        } else {
                            { pressScale }
                        },
                        activate = {
                            if (index != currentSelected) currentOnSelect(index)
                        },
                    )
                }
            }
        }
        if (thumbBackdrop != null && glassSpecular != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomMargin + inset)
                    .width(cellWidth)
                    .height(WyuNavigatorBarHeight - inset * 2)
                    .graphicsLayer {
                        translationX = (damped.value - lastIndex / 2f) * size.width
                    }
                    .drawBackdrop(
                        backdrop = thumbBackdrop,
                        shape = { pillShape },
                        effects = {
                            val progress = damped.pressProgress
                            lens(
                                refractionHeight = 8.dp.toPx() * progress,
                                refractionAmount = 11.dp.toPx() * progress,
                                depthEffect = true,
                                chromaticAberration = 0.5f,
                            )
                        },
                        onDrawFront = {
                            drawWyuSpecular(glassSpecular, damped.pressProgress)
                        },
                        layerBlock = {
                            val velocity = damped.velocity / 10f
                            scaleX = 1f + (damped.scaleX - 1f) * ThumbPressWidenTrim
                            scaleY = damped.scaleY
                            scaleX /= 1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f)
                            scaleY *= 1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f)
                        },
                        onDrawBackdrop = { drawSample ->
                            val progress = damped.pressProgress
                            val zoom = lerp(1f, 0.92f, progress)
                            translate(top = -1.dp.toPx() * progress) {
                                scale(zoom, zoom) { drawSample() }
                            }
                        },
                        onDrawSurface = {
                            val progress = damped.pressProgress
                            drawRect(
                                color = if (dark) {
                                    Color.White.copy(alpha = 0.1f)
                                } else {
                                    Color.Black.copy(alpha = 0.1f)
                                },
                                alpha = 1f - progress,
                            )
                            drawRect(Color.Black.copy(alpha = 0.03f * progress))
                        },
                    ),
            )
        }
    }
}

@Composable
private fun RowScope.WyuNavigatorTab(
    tab: WyuNavTab,
    selected: Boolean,
    tint: Color,
    highlight: () -> Float,
    scale: () -> Float,
    activate: (() -> Unit)?,
) {
    // Semantic / accessibility clicks never pass through pointerInput, so they
    // take the official lifecycle guard directly: dropUnlessResumed() discards
    // the tap while the home destination is leaving RESUMED in a NavHost
    // transition, exactly as it does for a navigate() call in a Button.
    val guardedActivate = activate?.let { action -> dropUnlessResumed { action() } }
    val semanticsModifier = if (guardedActivate != null) {
        Modifier.semantics(mergeDescendants = true) {
            role = Role.Tab
            this.selected = selected
            onClick(label = tab.label) {
                guardedActivate()
                true
            }
        }
    } else {
        Modifier
    }
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .then(semanticsModifier)
            .graphicsLayer {
                val value = scale()
                scaleX = value
                scaleY = value
            },
        contentAlignment = Alignment.Center,
    ) {
        // Dim and bright copies of the same label crossfaded by the thumb's
        // overlap. Reading highlight() inside graphicsLayer keeps the
        // per-frame update in the draw phase rather than recomposing the
        // whole glass bar every frame.
        WyuNavigatorTabContent(
            tab = tab,
            tint = tint,
            alpha = 0.55f,
            emphasized = false,
            modifier = Modifier.graphicsLayer { alpha = 1f - highlight() },
        )
        WyuNavigatorTabContent(
            tab = tab,
            tint = tint,
            alpha = 0.95f,
            emphasized = true,
            // Only one semantic copy of the label should be exposed.
            modifier = Modifier
                .clearAndSetSemantics { }
                .graphicsLayer { alpha = highlight() },
        )
    }
}

@Composable
private fun WyuNavigatorTabContent(
    tab: WyuNavTab,
    tint: Color,
    alpha: Float,
    emphasized: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = tint.copy(alpha = alpha),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = tab.label,
            style = TextStyle(
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = if (emphasized) FontWeight.Medium else FontWeight.Normal,
            ),
            color = tint.copy(alpha = alpha),
            maxLines = 1,
        )
    }
}
