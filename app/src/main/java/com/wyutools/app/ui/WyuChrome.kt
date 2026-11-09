package com.wyutools.app.ui

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
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
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/** DecelerateInterpolator, matching the clock's fixed 350 ms page switch. */
val WyuDecelerateEasing = Easing { t -> 1f - (1f - t) * (1f - t) }

val WyuNavigatorBarHeight: Dp = 58.dp

private val WyuCollapsedBarHeight = 56.dp
internal val WyuLargeTitleHeight = 46.dp

private val LocalWyuPageBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/** Wallpaper-only backdrop, so cards and buttons frost the photo without sampling themselves. */
internal val LocalWyuGlassBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

private val LocalWyuTopBarFraction = staticCompositionLocalOf<() -> Float> { { 0f } }

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
    maskBaseAlpha: Float = 0f,
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
    val customBrightness by WyuAppearance.customBrightness.collectAsState()
    val customDim = wyuCustomWallpaperDim(customBrightness, dark)
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
    val currentProgress = rememberUpdatedState(progress)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
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
            .then(
                when {
                    pageBrush != null -> Modifier.background(pageBrush)
                    customWallpaper != null -> Modifier.drawBehind {
                        drawWyuImageCover(customWallpaper, customDim)
                    }

                    else -> Modifier.background(surface)
                },
            ),
    ) {
        if (glassBackdrop != null) {
            Box(Modifier.fillMaxSize().layerBackdrop(glassBackdrop))
        }
        CompositionLocalProvider(
            LocalOverscrollFactory provides WyuNoOpOverscrollFactory,
            LocalWyuPageBackdrop provides backdrop,
            LocalWyuGlassBackdrop provides glassBackdrop,
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
                                    Modifier.layerBackdrop(backdrop)
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
                val maxMaskHeight = (topHeight + fadePx).coerceAtMost(constraints.maxHeight)
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
    modifier: Modifier = Modifier,
) {
    val blurModifier = if (backdrop != null) {
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
) {
    val onSurface = MiuixTheme.colorScheme.onSurface
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val largeStyle = remember(onSurface) {
        TextStyle(
            fontSize = 32.sp,
            lineHeight = 40.sp,
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
    val boost = LocalWyuCollapseBoost.current
    val effectiveFraction: () -> Float = {
        (collapsedFraction() + boost()).coerceIn(0f, 1f)
    }
    CompositionLocalProvider(
        LocalWyuTopBarFraction provides effectiveFraction,
    ) {
        Box(modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(statusTop))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(WyuCollapsedBarHeight)
                        .padding(
                            start = if (navigationIcon != null) 16.dp else 28.dp,
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
                        Crossfade(
                            targetState = title,
                            animationSpec = tween(180),
                            label = "wyuCollapsedTitle",
                            modifier = Modifier.graphicsLayer {
                                // Read at draw time: the host subcomposition can miss this
                                // state change, but layer invalidation never does.
                                alpha = if (WyuMorphPageState.titleHidden) {
                                    0f
                                } else if (largeTitle) {
                                    effectiveFraction()
                                } else {
                                    1f
                                }
                            },
                        ) { current ->
                            Text(
                                text = current,
                                style = collapsedStyle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
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
                            .padding(start = 28.dp, end = 24.dp),
                    ) {
                        Crossfade(
                            targetState = title,
                            animationSpec = tween(180),
                            label = "wyuLargeTitle",
                            modifier = Modifier.graphicsLayer {
                                val fraction = effectiveFraction()
                                alpha = if (WyuMorphPageState.titleHidden) 0f else 1f - fraction
                                translationY = -fraction * 10.dp.toPx()
                            },
                        ) { current ->
                            Text(
                                text = current,
                                style = largeStyle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
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
}

// A 44dp disc is barely wider than the captured backdrop padding, so the
// card-sized offsets clamp at the layer boundary and fringe the rim highlight.
private const val TopBarGlassRefractionPx = 56f
private const val TopBarGlassReflectionPx = 96f

/**
 * Icon-only top bar button. Expanded: bare icon over the content. Collapsed: a
 * frosted glass disc fades in behind the icon, following the clock's two states.
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
    val backdrop = LocalWyuPageBackdrop.current
    val softGlass by WyuAppearance.softGlass.collectAsState()
    val softGlassBlur by WyuAppearance.softGlassBlur.collectAsState()
    val useSoftGlass = softGlass && wyuSoftGlassSupported
    val fraction = LocalWyuTopBarFraction.current
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
        Box(
            modifier = Modifier
                .size(44.dp)
                .graphicsLayer {
                    val reveal = fraction().coerceIn(0f, 1f)
                    alpha = reveal
                    val scale = 0.82f + 0.18f * reveal
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(
                    elevation = 3.dp,
                    shape = CircleShape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.10f),
                    spotColor = Color.Black.copy(alpha = 0.16f),
                )
                .then(
                    when {
                        backdrop == null -> Modifier
                        useSoftGlass -> Modifier.wyuSoftGlass(
                            backdrop = backdrop,
                            shape = CircleShape,
                            glassShape = WyuSoftGlassShape.Circle,
                            blurPercent = softGlassBlur,
                            refractionThicknessPx = TopBarGlassRefractionPx,
                            reflectionThicknessPx = TopBarGlassReflectionPx,
                            bgSaturation = softGlassBgSaturation(dark),
                        )

                        else -> Modifier.textureBlur(
                            backdrop = backdrop,
                            shape = CircleShape,
                            blurRadius = 24f,
                        )
                    },
                )
                .clip(CircleShape)
                .then(
                    if (useSoftGlass) {
                        Modifier
                    } else {
                        Modifier.background(
                            if (dark) {
                                Color.White.copy(alpha = 0.16f)
                            } else {
                                Color.White.copy(alpha = 0.55f)
                            },
                        )
                    },
                )
                .border(
                    width = 0.5.dp,
                    color = if (dark) {
                        Color.White.copy(alpha = 0.14f)
                    } else {
                        Color.Black.copy(alpha = 0.06f)
                    },
                    shape = CircleShape,
                ),
        )
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

/** Icon-only top bar action; [label] is kept as the accessibility description. */
@Composable
fun WyuTopBarAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    WyuTopBarGlassButton(
        icon = icon,
        contentDescription = label,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    )
}

/**
 * Slim frosted pill tab row: drag-to-switch with tick haptics, tap debounce and
 * the floating navigator's 350 ms decelerate motion. When [selectionFraction]
 * is supplied the indicator mirrors it (pager driven) instead of running its own
 * selection animation.
 */
@Composable
fun WyuFloatingTabs(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    selectionFraction: (() -> Float)? = null,
) {
    if (options.isEmpty()) return
    val dark = LocalWyuDarkTheme.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val backdrop = LocalWyuPageBackdrop.current
    val lastIndex = options.lastIndex
    val initialFraction = selectedIndex.coerceIn(0, lastIndex).toFloat()
    val indicator = remember { Animatable(initialFraction) }
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
                if (abs(target - indicator.value) < 0.01f) interacting = false
            } else {
                indicator.snapTo(target)
            }
        }
    }
    LaunchedEffect(selectedIndex, selectionFraction) {
        if (selectionFraction == null) {
            indicator.animateTo(
                selectedIndex.coerceIn(0, lastIndex).toFloat(),
                tween(350, easing = WyuDecelerateEasing),
            )
        }
    }

    val shape = RoundedCornerShape(percent = 50)
    val glass = backdrop != null
    val containerColor = if (dark) {
        Color(0xFF2C2C2C).copy(alpha = if (glass) 0.55f else 1f)
    } else {
        Color.White.copy(alpha = if (glass) 0.58f else 1f)
    }
    val indicatorColor = if (dark) {
        Color.White.copy(alpha = 0.16f)
    } else {
        Color.Black.copy(alpha = 0.10f)
    }
    val borderColor = when {
        glass && dark -> Color.White.copy(alpha = 0.14f)
        glass -> Color.Black.copy(alpha = 0.06f)
        dark -> Color.White.copy(alpha = 0.08f)
        else -> Color.Black.copy(alpha = 0.04f)
    }
    val contentColor = if (dark) Color.White else Color.Black

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .shadow(
                elevation = 10.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.10f),
                spotColor = Color.Black.copy(alpha = 0.16f),
            )
            .then(
                if (backdrop != null) {
                    Modifier.textureBlur(
                        backdrop = backdrop,
                        shape = shape,
                        blurRadius = 30f,
                    )
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .background(containerColor)
            .border(0.5.dp, borderColor, shape)
            .padding(3.dp)
            .pointerInput(options.size, lastIndex) {
                fun revertPreview(target: Int) {
                    scope.launch {
                        indicator.animateTo(
                            target.toFloat(),
                            tween(160, easing = WyuDecelerateEasing),
                        )
                        interacting = false
                    }
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val cellPx = size.width / options.size.toFloat()
                    val startSelected = currentSelected
                    val downIndex = (down.position.x / cellPx).toInt().coerceIn(0, lastIndex)
                    pressedIndex = downIndex
                    if (downIndex != startSelected) {
                        // Slide the thumb under the finger immediately on press.
                        interactionGen++
                        interacting = true
                        scope.launch {
                            indicator.animateTo(
                                downIndex.toFloat(),
                                tween(160, easing = WyuDecelerateEasing),
                            )
                        }
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
                        val released = dragFraction
                        val target = released.roundToInt().coerceIn(0, lastIndex)
                        scope.launch {
                            indicator.snapTo(released)
                            dragging = false
                            indicator.animateTo(
                                target.toFloat(),
                                tween(350, easing = WyuDecelerateEasing),
                            )
                            interacting = false
                        }
                        if (target != startSelected) {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            currentOnSelect(target)
                        }
                    } else if (!cancelled) {
                        val now = SystemClock.uptimeMillis()
                        if (now - lastSelectAt >= 200L) {
                            lastSelectAt = now
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            if (downIndex != startSelected) {
                                currentOnSelect(downIndex)
                                // Release the preview if the pager transition is
                                // interrupted before it lands.
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
                            if (selectionFraction == null) {
                                scope.launch {
                                    interacting = true
                                    indicator.animateTo(
                                        downIndex.toFloat(),
                                        tween(350, easing = WyuDecelerateEasing),
                                    )
                                    interacting = false
                                }
                            }
                        } else if (downIndex != startSelected) {
                            revertPreview(startSelected)
                        }
                    } else if (downIndex != startSelected) {
                        revertPreview(startSelected)
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
                    val fraction = if (dragging) dragFraction else indicator.value
                    translationX = fraction * size.width
                }
                .clip(shape)
                .background(indicatorColor),
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
                        contentColor.copy(alpha = 0.95f)
                    } else {
                        contentColor.copy(alpha = 0.55f)
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

@Composable
fun WyuPageEffects(
    modifier: Modifier = Modifier,
    active: Boolean = true,
    startBlurred: Boolean = true,
    content: @Composable () -> Unit,
) {
    val blurRadius = remember { Animatable(if (startBlurred) 12f else 0f) }

    LaunchedEffect(active) {
        blurRadius.animateTo(
            targetValue = if (active) 0f else 12f,
            animationSpec = tween(
                durationMillis = if (active) 300 else 220,
                easing = FastOutSlowInEasing,
            ),
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        val radius = blurRadius.value
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (radius > 0.05f) {
                        Modifier.blur(
                            radius = radius.dp,
                            edgeTreatment = BlurredEdgeTreatment.Rectangle,
                        )
                    } else {
                        Modifier
                    },
                ),
        ) {
            content()
        }
    }
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
    val inset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return if (inset < 24.dp) 24.dp else inset + 8.dp
}

private data class WyuNavTab(
    val label: String,
    val icon: ImageVector,
)

private val WyuNavTabs = listOf(
    WyuNavTab("工具", Icons.Outlined.Build),
    WyuNavTab("我", Icons.Outlined.Person),
)

@Composable
fun WyuNavigatorBar(
    selectedIndex: Int,
    selectionFraction: () -> Float,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = LocalWyuDarkTheme.current
    val bottomMargin = rememberWyuNavigatorBottomMargin()
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val backdrop = LocalWyuPageBackdrop.current
    val lastIndex = WyuNavTabs.lastIndex
    val cellWidth = 88.dp
    val inset = 3.dp
    val pillWidth = cellWidth * WyuNavTabs.size + inset * 2
    val pillShape = RoundedCornerShape(percent = 50)
    val contentColor = if (dark) Color.White else Color.Black
    val indicatorColor = if (dark) {
        Color.White.copy(alpha = 0.16f)
    } else {
        Color.Black.copy(alpha = 0.10f)
    }
    val glassContainerColor = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.4f)
    val glassBackdrop = backdrop
    val barBackdrop = rememberLayerBackdrop()
    val thumbBackdrop = if (glassBackdrop != null) {
        rememberWyuCombinedBackdrop(glassBackdrop, barBackdrop)
    } else {
        null
    }
    val pillSpecular = if (glassBackdrop != null) rememberWyuSpecularHighlight(extraDegrees = -45f) else null
    val thumbSpecular = if (glassBackdrop != null) rememberWyuSpecularHighlight(extraDegrees = 90f) else null

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
    var interacting by remember { mutableStateOf(false) }
    var interactionGen by remember { mutableIntStateOf(0) }
    var pressedIndex by remember { mutableIntStateOf(-1) }
    var lastSelectAt by remember { mutableLongStateOf(0L) }
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentSelected by rememberUpdatedState(selectedIndex)
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
        snapshotFlow { selectionFraction() }.collect { fraction ->
            if (dragging || pressedIndex >= 0) return@collect
            val target = fraction.coerceIn(0f, lastIndex.toFloat())
            if (interacting) {
                // Keep the pressed preview in place until the pager lands on it.
                if (abs(target - damped.value) < 0.01f) interacting = false
            } else {
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
                    if (glassBackdrop != null) {
                        Modifier.drawBackdrop(
                            backdrop = glassBackdrop,
                            shape = { pillShape },
                            effects = {
                                padding = maxOf(padding, 40.dp.toPx())
                                vibrancy()
                                // The 2dp radius would otherwise stay at full resolution;
                                // 1/2 linear keeps its variance while quartering the work.
                                forceDownscaleExp(1)
                                blur(2.dp.toPx(), 2.dp.toPx())
                                lens(
                                    refractionHeight = 28.dp.toPx(),
                                    refractionAmount = 40.dp.toPx(),
                                )
                            },
                            highlight = {
                                pillSpecular?.value?.copy(alpha = 0.75f)
                                    ?: WyuGlassSpecular.copy(alpha = 0f)
                            },
                            layerBlock = {
                                val width = size.width.coerceAtLeast(1f)
                                val s = lerp(
                                    1f,
                                    1f + 8.dp.toPx() / width,
                                    damped.pressProgress,
                                )
                                scaleX = s
                                scaleY = s
                            },
                            onDrawSurface = {
                                drawRect(glassContainerColor)
                                drawRect(Color.White.copy(alpha = 0.1f * damped.pressProgress))
                            },
                        )
                    } else {
                        Modifier
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
                        val cellPx = size.width / WyuNavTabs.size.toFloat()
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
                            } else if (downIndex != startSelected) {
                                revertPreview(startSelected)
                            }
                            damped.release()
                        } else {
                            damped.release()
                            if (downIndex != startSelected) {
                                revertPreview(startSelected)
                            }
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
                        alpha = if (isSelected) 0.95f else 0.55f,
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
        if (thumbBackdrop != null && thumbSpecular != null) {
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
                        highlight = {
                            thumbSpecular.value.copy(alpha = damped.pressProgress)
                        },
                        layerBlock = {
                            val velocity = damped.velocity / 10f
                            scaleX = damped.scaleX
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
    alpha: Float,
    scale: () -> Float,
    activate: (() -> Unit)?,
) {
    val semanticsModifier = if (activate != null) {
        Modifier.semantics(mergeDescendants = true) {
            role = Role.Tab
            this.selected = selected
            onClick(label = tab.label) {
                activate()
                true
            }
        }
    } else {
        Modifier
    }
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .then(semanticsModifier)
            .graphicsLayer {
                val value = scale()
                scaleX = value
                scaleY = value
            },
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
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            ),
            color = tint.copy(alpha = alpha),
            maxLines = 1,
        )
    }
}
