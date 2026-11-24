package com.wyutools.app.ui

import android.view.View
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.layout.LayoutCoordinates
import top.yukonga.miuix.kmp.blur.Backdrop
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import top.yukonga.miuix.kmp.glass.glass
import top.yukonga.miuix.kmp.material.GlassShape
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.PI
import kotlin.math.roundToInt

/**
 * 贴着触发控件弹出的选项菜单。
 *
 * 几何与动效照搬 miuix glass 的 `GlassDropdownPopup`（`GlassMotion.arc*` 那一套）：
 *
 * - 面板从 **69% 宽、高宽比 0.2** 的扁胶囊长开——尺寸和中心各跑一条弹簧，
 *   进场时中心比尺寸快、出场对调，所以轨迹是弧线而不是直线拉伸；
 * - 圆角 4dp → 24dp，内容按 0.69 → 1 等比缩放；
 * - 进场 50ms 淡入，尺寸过 10% 之前整块不可见；出场 260ms 淡出，
 *   同时把整块面板（含底色）按 30dp 高斯模糊——收起是「缩回按钮、糊掉、消失」；
 * - 右边缘贴住触发控件右边缘，下方放不下时翻到上方，离屏幕边缘至少 12dp。
 *
 * @param expanded 是否展开。
 * @param onDismissRequest 点外部或返回键时回调，调用方把 [expanded] 置 false。
 * @param anchor 触发控件在宿主窗口坐标里的矩形（`boundsInWindow()`）。
 * @param modifier 作用于面板本身。
 * @param minWidth 菜单最小宽度。
 * @param maxWidth 菜单最大宽度。
 * @param content 选项列表，通常是一串 [WyuDropdownItem]。
 */
@Composable
internal fun WyuDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    anchor: Rect,
    modifier: Modifier = Modifier,
    minWidth: Dp = 112.dp,
    maxWidth: Dp = 232.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    // 收起动画播完之前不能把 Popup 拆掉，否则会「啪」地消失。
    var mounted by remember { mutableStateOf(false) }
    val transition = updateTransition(targetState = expanded, label = "wyuDropdown")
    // 尺寸：进场 response 0.35、出场 0.22。
    val size by transition.animateFloat(
        transitionSpec = {
            spring(
                dampingRatio = ArcSpringDamping,
                stiffness = arcStiffness(if (targetState) 0.35f else 0.22f),
                visibilityThreshold = ArcVisibilityThreshold,
            )
        },
        label = "wyuDropdownSize",
    ) { if (it) 1f else 0f }
    // 中心：和尺寸对调，两条速度不同的弹簧才凑出那条弧线。
    // 出场要比进场快一点（0.28 而不是 0.35）：收起的路程更长——要一路缩回按钮，
    // 太慢就会在还没到位时先淡没了。
    val position by transition.animateFloat(
        transitionSpec = {
            spring(
                dampingRatio = ArcSpringDamping,
                stiffness = arcStiffness(if (targetState) 0.22f else 0.28f),
                visibilityThreshold = ArcVisibilityThreshold,
            )
        },
        label = "wyuDropdownPosition",
    ) { if (it) 1f else 0f }
    // 淡入 50ms、淡出 260ms，都是线性。淡出比参考实现的 200ms 长一点，
    // 因为面板要先缩回按钮再消失，太短会在半路上没影。
    val fade by transition.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = if (targetState) 50 else 220,
                easing = if (targetState) LinearEasing else FastOutLinearInEasing,
            )
        },
        label = "wyuDropdownFade",
    ) { if (it) 1f else 0f }
    // 模糊是单向的：进场直接吸附到清晰，只有出场才线性涨到 30dp（与淡出同窗）。
    val sharpness by transition.animateFloat(
        transitionSpec = {
            if (targetState) snap() else tween(durationMillis = 240, easing = LinearEasing)
        },
        label = "wyuDropdownBlur",
    ) { if (it) 1f else 0f }

    LaunchedEffect(expanded) { if (expanded) mounted = true }
    val active = expanded || size > 0.0001f
    LaunchedEffect(active) { if (!active) mounted = false }
    if (!mounted) return

    val density = LocalDensity.current
    var panelSize by remember { mutableStateOf(IntSize.Zero) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    // 宿主是 edge-to-edge，窗口原点在屏幕左上；而弹出窗口会被系统栏内缩，原点比宿主低一截，
    // 直接把 boundsInWindow() 的坐标丢进去就会整体偏下（实测偏一个状态栏高）。
    // 两边各取一次「根视图在屏幕上的位置」，把锚点换算到弹出窗口自己的坐标系里。
    var hostOrigin by remember { mutableStateOf<Offset?>(null) }
    var popupOrigin by remember { mutableStateOf<Offset?>(null) }
    val hostView = LocalView.current
    LaunchedEffect(hostView) { hostOrigin = hostView.rootView.screenOrigin() }
    val dark = LocalWyuDarkTheme.current
    val tuning by WyuAppearance.softGlassTuning.collectAsState()
    val softGlassBlur by WyuAppearance.softGlassBlur.collectAsState()
    // 玻璃面板：模糊身后的页面内容，再压一层卡片同档的容器色。
    val glassBackdrop = LocalWyuPageBackdrop.current
    val gaussianUnderlay = LocalWyuGaussianUnderlay.current
    val panelColor = when {
        // 没有可模糊的底时（不支持模糊的机器）退回不透明容器色，
        // 否则半透明面板会直接透出下面的页面。
        glassBackdrop == null -> MiuixTheme.colorScheme.surfaceContainer
        else -> MiuixTheme.colorScheme.surfaceContainer.copy(alpha = tuning.cardTintAlpha(dark))
    }
    // 接入 miuix-glass：面板改为官方 GlassStyle 渲染，保留同样的容器色
    // 作为 tint，视觉上仍是原来的菜单，只是边缘折射与高光交给上游着色器。
    val currentCardTheme by WyuAppearance.cardTheme.collectAsState()
    val isLiquid = currentCardTheme == WyuCardTheme.Liquid
    val liquidTuning by WyuAppearance.liquidGlassTuning.collectAsState()
    val liquidBlur by WyuAppearance.liquidGlassBlur.collectAsState()
    val useUpstreamGlass = glassBackdrop != null && !isLiquid &&
        currentCardTheme == WyuCardTheme.SoftGlass
    val useLiquidGlass = glassBackdrop != null && isLiquid && wyuSoftGlassSupported
    val strokeColor = if (dark) {
        Color.White.copy(alpha = 0.08f)
    } else {
        Color.Black.copy(alpha = 0.06f)
    }
    // 面板宽度按最宽的选项收窄：写死 164dp 时两个字的选项会在文字和勾之间
    // 留出一大片空白。
    val widths = remember { DropdownWidths() }
    val itemChrome = DropdownItemChrome
    val panelWidth = if (widths.widest <= 0) {
        minWidth
    } else {
        with(density) { widths.widest.toDp() + itemChrome }.coerceIn(minWidth, maxWidth)
    }
    val scrimInteraction = remember { MutableInteractionSource() }
    val panelInteraction = remember { MutableInteractionSource() }

    // 面板宽度是由每一项量出来的，选项通过这个 local 上报自己的文字宽度。
    CompositionLocalProvider(LocalDropdownWidths provides widths) {
        Popup(
            popupPositionProvider = WindowOriginPositionProvider,
            onDismissRequest = onDismissRequest,
            properties = PopupProperties(focusable = true),
        ) {
            val popupView = LocalView.current
            // 铺满整窗的透明层：既当「点外部关闭」的响应区，也给面板一个和窗口一致的坐标系。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { rootSize = it }
                    .onGloballyPositioned { popupOrigin = popupView.rootView.screenOrigin() }
                    .clickable(interactionSource = scrimInteraction, indication = null) {
                        onDismissRequest()
                    },
            ) {
                // 0 尺寸的测量副本：先量出菜单的最终尺寸，面板才知道要长到多大。
                // 不画（drawWithContent 空实现）也不吃触摸（clipToBounds）。
                Box(modifier = Modifier.size(0.dp).clipToBounds()) {
                    Box(modifier = Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)) {
                        DropdownPanelContent(
                            width = panelWidth,
                            modifier = Modifier
                                .drawWithContent { }
                                .onSizeChanged { panelSize = it },
                            content = content,
                        )
                    }
                }

                val host = hostOrigin
                val popup = popupOrigin
                if (panelSize != IntSize.Zero && rootSize != IntSize.Zero && host != null && popup != null) {
                    val end = Size(panelSize.width.toFloat(), panelSize.height.toFloat())
                    // anchor 是宿主窗口坐标，先加上宿主窗口在屏幕上的位置，再减掉弹出窗口的位置。
                    val shift = Offset(host.x - popup.x, host.y - popup.y)
                    // Default cards blur a baked, pre-Gaussian wallpaper copy.
                    // Reuse that exact bitmap here; only its origin is rebased
                    // into the popup window so the panel samples what is really
                    // behind it instead of the host-window coordinates.
                    val popupGaussian = gaussianUnderlay?.let { u ->
                        WyuGlassUnderlay(
                            bitmap = u.bitmap,
                            scale = u.scale,
                            origin = u.origin + shift,
                            source = u.source,
                            dim = u.dim,
                            blurSigmaPx = u.blurSigmaPx,
                            widthPx = u.widthPx,
                            heightPx = u.heightPx,
                        )
                    }
                    // The popup lives in a different window; its LayoutCoordinates report
                    // positions relative to that window while the page layer was recorded
                    // in the host window. Apply the same screen-space shift used for the
                    // anchor so the glass samples the pixels actually behind the panel.
                    val sampledBackdrop = remember(glassBackdrop, shift) {
                        glassBackdrop?.let { ShiftedBackdrop(it, shift) }
                    }
                    val anchorInPopup = anchor.translate(shift)
                    val window = Rect(
                        left = 0f,
                        top = 0f,
                        right = rootSize.width.toFloat(),
                        bottom = rootSize.height.toFloat(),
                    )
                    val margin = with(density) { 12.dp.toPx() }
                    val placement = placeDropdownPopup(anchorInPopup, end, margin, window)
                    // 进场从面板右上角的小胶囊长开；收回则一路缩回按钮本身。
                    val frame = arcFrame(placement, anchorInPopup, end, expanded, size, position, density)

                    val shape = RoundedCornerShape(with(density) { frame.cornerRadius.toDp() })
                    // 与卡片同一份参数：样式只建一次，tint 按卡片 shader 的口径
                    // 预先乘上 tintStrength，管路那边照旧用颜色自身 alpha。
                    val panelGlassStyle = wyuUpstreamGlassStyle(tuning, dark, softGlassBlur)
                    // 内容按 0.69 → 1 等比缩放，缩放中心就是面板中心，所以永远严丝合缝。
                    val contentScale = ArcStartWidth + (1f - ArcStartWidth) * size.coerceAtLeast(0f)
                    // 尺寸还没过 10% 时整块不可见：出现在眼前的是个清晰的小胶囊，
                    // 而不是一枚几乎透明的碎点。
                    val panelAlpha = if (expanded && size <= ArcVisibleFraction) 0f else fade
                    val blurPx = with(density) { ArcExitBlurDp.dp.toPx() } * (1f - sharpness)

                    Box(
                        modifier = modifier
                            .offset {
                                IntOffset(frame.left.roundToInt(), frame.top.roundToInt())
                            }
                            .size(
                                width = with(density) { frame.width.toDp() },
                                height = with(density) { frame.height.toDp() },
                            )
                            .graphicsLayer { alpha = panelAlpha }
                            .shadow(elevation = 12.dp, shape = shape, clip = false)
                            .clip(shape)
                            // 模糊层必须排在 clip 内侧：否则圆角之外的透明区域也被糊成
                            // 一圈矩形光晕，收起时看起来就是一块方形模糊突然消失。
                            .graphicsLayer {
                                if (MorphBlurSupported) {
                                    renderEffect = if (blurPx > 0.5f) {
                                        backdropBlurEffect(blurPx)
                                    } else {
                                        null
                                    }
                                }
                            }
                            .then(
                                if (useLiquidGlass) {
                                    Modifier.wyuLiquidGlass(
                                        backdrop = sampledBackdrop ?: glassBackdrop,
                                        shape = shape,
                                        cornerRadius = with(density) { frame.cornerRadius.toDp() },
                                        tuning = liquidTuning,
                                        dark = dark,
                                        baseTint = wyuLiquidControlTint(),
                                        blurPercent = liquidBlur,
                                    )
                                } else if (popupGaussian != null) {
                                    Modifier.wyuWallpaperBlur(
                                        underlay = popupGaussian,
                                        shape = shape,
                                        // 面板在弹出窗口里，底图原点已按窗口位移重定，
                                        // 只有窗口坐标能对上。
                                        pageSpace = false,
                                    )
                                } else if (useUpstreamGlass && glassBackdrop != null) {
                                    Modifier.glass(
                                        backdrop = sampledBackdrop ?: glassBackdrop,
                                        shape = GlassShape(
                                            with(density) { frame.cornerRadius.toDp() },
                                        ),
                                        style = panelGlassStyle,
                                        tint = wyuUpstreamGlassTint(
                                            wyuSoftGlassSurfaceTint(),
                                            panelGlassStyle,
                                        ),
                                        shading = true,
                                    )
                                } else {
                                    Modifier
                                },
                            )
                            .then(
                                if (useUpstreamGlass || useLiquidGlass) {
                                    // 两种 shader 路径都自带 tint，实底色只留给回退。
                                    Modifier
                                } else {
                                    Modifier
                                        .background(panelColor)
                                        .border(width = 0.5.dp, color = strokeColor, shape = shape)
                                },
                            )
                            // 吃掉落在面板空白处的点击，免得误触到背景的关闭区。
                            .clickable(interactionSource = panelInteraction, indication = null) {},
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .requiredSize(
                                        width = with(density) { end.width.toDp() },
                                        height = with(density) { end.height.toDp() },
                                    )
                                    .graphicsLayer {
                                        scaleX = contentScale
                                        scaleY = contentScale
                                    },
                            ) {
                                DropdownPanelContent(
                                    width = panelWidth,
                                    content = content,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 弹簧阻尼，整套弧线运动共用。 */
private const val ArcSpringDamping = 0.8f

/** 和 `GlassMotion` 一致的停稳阈值。 */
private const val ArcVisibilityThreshold = 0.0015f

/** 面板展开时的宽度占最终宽度的比例。 */
private const val ArcStartWidth = 0.69f

/** 展开那一刻的高度占自身宽度的比例（很扁的胶囊）。 */
private const val ArcStartRatio = 0.2f

/** 尺寸走到这个比例之前整块不可见。 */
private const val ArcVisibleFraction = 0.1f

/** 收起时的模糊半径，dp。 */
private const val ArcExitBlurDp = 18f

/** 起点与展开后的圆角。 */
private val ArcStartCorner = 4.dp
private val ArcExpandedCorner = 24.dp

/**
 * folme 的 `response` 换算成 Compose 弹簧的 `stiffness`：`(2π / response)²`。
 *
 * 参考实现写的 `springOf(0.8f, 0.35f)` 就是 folmeSpring(阻尼, response)，
 * 换算过来约等于 stiffness 322；response 0.22 约等于 815。
 */
private fun arcStiffness(response: Float): Float {
    val omega = 2f * PI.toFloat() / response
    return omega * omega
}

/** 面板矩形与圆角。 */
private class DropdownFrame(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val cornerRadius: Float,
)

/** 摆放结果。 */
private class DropdownPlacement(val rect: Rect, val alignTop: Boolean)

/**
 * 摆放规则照搬 `placeGlassPopup`：右边缘贴住触发控件右边缘（并夹在窗口安全边距内），
 * 优先把面板上边缘对齐到触发控件上边缘，放不下时翻到上方。
 */
private fun placeDropdownPopup(
    anchor: Rect,
    size: Size,
    margin: Float,
    window: Rect,
): DropdownPlacement {
    val minLeft = window.left + margin
    val left = (anchor.right - size.width)
        .coerceIn(minLeft, (window.right - size.width - margin).coerceAtLeast(minLeft))
    val minTop = minOf(window.top + margin, anchor.top).coerceAtLeast(window.top)
    val below = window.bottom - margin - anchor.top
    val above = anchor.bottom - minTop
    val alignTop = size.height <= below || below >= above
    val wanted = if (alignTop) anchor.top else anchor.bottom - size.height
    val top = wanted.coerceIn(minTop, (window.bottom - size.height - margin).coerceAtLeast(minTop))
    return DropdownPlacement(Rect(left, top, left + size.width, top + size.height), alignTop)
}

/**
 * 弧形生长与收回。
 *
 * 进场照 `arcFrame`：尺寸和中心各跑一条弹簧，起点是贴着摆放位置右上角的一枚小胶囊
 * （宽 = 最终宽 × 0.69、高 = 起点宽 × 0.2），圆角 4dp → 24dp。
 *
 * 收回改走按钮：起点换成触发控件的矩形（[anchor]），面板顺着两条弹簧缩回按钮里，
 * 缩到底正好落在按钮上，而不是原地缩成一小块再消失。
 * 两个方向在 t = 1 处都等于展开后的面板矩形，所以方向切换的那一帧不会跳。
 */
private fun arcFrame(
    placement: DropdownPlacement,
    anchor: Rect,
    end: Size,
    entering: Boolean,
    sizeFraction: Float,
    positionFraction: Float,
    density: Density,
): DropdownFrame {
    val t = sizeFraction.coerceAtLeast(0f)
    val startWidth: Float
    val startHeight: Float
    val startCenterX: Float
    val startCenterY: Float
    if (entering) {
        startWidth = end.width * ArcStartWidth
        startHeight = startWidth * ArcStartRatio
        startCenterX = placement.rect.right - startWidth / 2f
        startCenterY = if (placement.alignTop) {
            placement.rect.top + startHeight / 2f
        } else {
            placement.rect.bottom - startHeight / 2f
        }
    } else {
        // 收回的终点就是按钮，缩到底时面板和按钮完全重合。
        startWidth = anchor.width
        startHeight = anchor.height
        startCenterX = anchor.center.x
        startCenterY = anchor.center.y
    }
    val width = startWidth + (end.width - startWidth) * t
    val height = startHeight + (end.height - startHeight) * t
    val centerX = startCenterX + (placement.rect.center.x - startCenterX) * positionFraction
    val centerY = startCenterY + (placement.rect.center.y - startCenterY) * positionFraction
    val radius = with(density) {
        val start = if (entering) ArcStartCorner.toPx() else anchor.height / 2f
        start + (ArcExpandedCorner.toPx() - start) * t
    }
    return DropdownFrame(
        left = centerX - width / 2f,
        top = centerY - height / 2f,
        width = width,
        height = height,
        cornerRadius = radius,
    )
}

/** 面板内容：宽度就是量出来的那一条，左右不再留多余的空白。 */
@Composable
private fun DropdownPanelContent(
    width: Dp,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .width(width)
            .padding(vertical = 8.dp),
        content = content,
    )
}

/**
 * 选项文字宽度的汇总处。每行量一次正文宽度报上来，面板取最大值再收窄，
 * 所以「本周」这种短选项不会再被撑成一条长条。
 */
private class DropdownWidths {
    var widest by mutableIntStateOf(0)
        private set

    fun record(width: Int) {
        if (width > widest) widest = width
    }
}

private val LocalDropdownWidths = staticCompositionLocalOf<DropdownWidths?> { null }

/** 一行的固定开销：左右各 7dp 外边距 + 13dp 内边距 + 勾选图标的 8dp + 24dp。 */
private val DropdownItemChrome = 72.dp

/** 面板玻璃的模糊半径与容器色不透明度（与卡片同一档）。 */
private const val DropdownGlassBlur = 30f
private const val DropdownGlassTint = 0.62f

/**
 * [WyuDropdownMenu] 的触发控件：当前值 + 右侧上下双箭头。
 *
 * 不垫胶囊底色，直接裁成 [CircleShape] 让点按高光变成胶囊；点按范围靠内边距撑出来，
 * 与旁边文字基线对齐。带触感反馈。
 */
@Composable
internal fun WyuDropdownTrigger(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val open = hapticClick(enabled = enabled, action = onClick)
    Row(
        modifier = modifier
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = open)
            .padding(start = 6.dp, end = 2.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = if (enabled) {
                MiuixTheme.colorScheme.onSurface
            } else {
                MiuixTheme.colorScheme.onSurfaceVariantSummary
            },
        )
        Icon(
            imageVector = Icons.Filled.UnfoldMore,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * [WyuDropdownMenu] 里的一行选项，参数取自 `GlassPopupItem`：
 * 最小 44dp 高、按下时 15dp 圆角的高光（左右各缩进 7dp）、
 * 文字左右各 13dp（视觉上与 20dp 一致）、尾部 24dp 勾选图标。
 *
 * 选中时文字和勾选图标一起变主色；勾选图标在未选中时是透明的，
 * 但仍然占着 24dp 的位置，所以切换选中项时文字不会抖。
 */
@Composable
internal fun WyuDropdownItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val dark = LocalWyuDarkTheme.current
    // 把这一行的正文宽度报给面板：菜单宽度按最宽的一行收窄。
    val widths = LocalDropdownWidths.current
    val textMeasurer = rememberTextMeasurer()
    val textStyle = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Medium)
    val density = LocalDensity.current
    LaunchedEffect(widths, text, textStyle, density) {
        if (widths == null) return@LaunchedEffect
        widths.record(
            textMeasurer.measure(
                text = AnnotatedString(text),
                style = textStyle,
                maxLines = 1,
                softWrap = false,
            ).size.width,
        )
    }
    // 参考实现：偏暗主题用 10% 白，否则 10% 黑。
    val pressColor = if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.10f)
    val background = if (pressed && enabled) pressColor else Color.Transparent
    val contentColor = when {
        !enabled -> MiuixTheme.colorScheme.onSurfaceVariantSummary
        selected -> MiuixTheme.colorScheme.primary
        else -> MiuixTheme.colorScheme.onSurface
    }
    val tickColor = if (selected && enabled) contentColor else Color.Transparent
    val hapticOnClick = hapticClick(enabled = enabled, action = onClick)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(horizontal = 7.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(background)
            .selectable(
                selected = selected,
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = hapticOnClick,
            )
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = textStyle,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = tickColor,
            modifier = Modifier
                .padding(start = 8.dp)
                .size(24.dp),
        )
    }
}

/**
 * 根视图（DecorView / 弹出窗口本身）在屏幕上的左上角。
 *
 * 宿主窗口和弹出窗口的坐标原点不一定重合，两者的差就是 [WyuDropdownMenu] 里要补的偏移。
 */
private fun View.screenOrigin(): Offset {
    val location = IntArray(2)
    rootView.getLocationOnScreen(location)
    return Offset(location[0].toFloat(), location[1].toFloat())
}

/**
 * 让弹出窗口铺满整个宿主窗口：面板自己按窗口坐标摆放，
 * 不需要再把锚点换算到弹出窗口里。
 */
private object WindowOriginPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}

/** Rebases a host-window backdrop into the popup window coordinate system. */
private class ShiftedBackdrop(
    private val delegate: Backdrop,
    private val shift: Offset,
) : Backdrop {
    override val isCoordinatesDependent: Boolean get() = delegate.isCoordinatesDependent
    override val offsetResidualX: Float get() = delegate.offsetResidualX
    override val offsetResidualY: Float get() = delegate.offsetResidualY

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
        downscaleFactor: Float,
    ) {
        // LayerBackdrop maps the surface offset into the reduced sampling
        // buffer with 1/downscaleFactor. The host-vs-popup window shift is in
        // full pixels, so it needs the same reduction — otherwise a 4x blur
        // downscale lands the sample 4 windows apart.
        val sampled = if (downscaleFactor > 1f) 1f / downscaleFactor else 1f
        withTransform({ translate(shift.x * sampled, shift.y * sampled) }) {
            with(delegate) { drawBackdrop(density, coordinates, layerBlock, downscaleFactor) }
        }
    }
}
