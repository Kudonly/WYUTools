package com.wyutools.app.ui

import android.os.Build
import android.view.RoundedCorner
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonColors
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.basic.TextFieldColors
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.squircle.addSquircleRect
import top.yukonga.miuix.kmp.utils.PressFeedbackType

private const val CardGlassAlpha = 0.55f
private const val CardSoftGlassAlpha = 0f

/**
 * 玻璃控件（顶栏圆钮、胶囊、分段控件）压在模糊层上的底色。
 *
 * 原来这里写死 `Color.White`，浅色下没问题，深色下就成了一块发白的灰片，
 * 跟旁边同为玻璃材质的卡片完全不像一类东西。现在直接取卡片的容器色
 * （浅色白、深色 #242424）再套卡片同一档不透明度：控件与卡片在任何模式下
 * 都是同一块玻璃。
 */
@Composable
internal fun wyuGlassControlWash(): Color =
    MiuixTheme.colorScheme.surfaceContainer.copy(alpha = CardGlassAlpha)

/**
 * 柔光玻璃件的 tint：与卡片同一公式——浅色纯白、深色纯黑，alpha 取调参里的
 * 卡片底色档。
 *
 * 每一块柔光玻璃都要用这一份：tint 决定玻璃的洗色强度，任何一处另起一套
 * 颜色或 alpha（比如 `surfaceContainer @ 0.55`），那块玻璃看上去就跟卡片
 * 不是同一种材质。
 */
@Composable
internal fun wyuSoftGlassSurfaceTint(): Color {
    val tuning by WyuAppearance.softGlassTuning.collectAsState()
    return if (LocalWyuDarkTheme.current) {
        Color.Black.copy(alpha = tuning.cardTintAlphaDark)
    } else {
        Color.White.copy(alpha = tuning.cardTintAlphaLight)
    }
}

/**
 * 液态玻璃控件（顶栏面、胶囊、分段、输入框、底栏、菜单）的底色：
 * 与液态卡片同一公式——浅色白、深色黑，alpha 取液态调参里的
 * 卡片底色档——保证所有玻璃组件的取色跟卡片完全一致。
 */
@Composable
internal fun wyuLiquidControlTint(): Color {
    val liquidTuning by WyuAppearance.liquidGlassTuning.collectAsState()
    val dark = LocalWyuDarkTheme.current
    return if (dark) {
        Color.Black.copy(alpha = liquidTuning.cardTintAlphaDark)
    } else {
        Color.White.copy(alpha = liquidTuning.cardTintAlphaLight)
    }
}

/**
 * 当前玻璃材质的一整套参数：开关、调参、模糊档位、是不是液态主题。
 *
 * 卡片、输入框、顶栏、分段控件、底栏、对话框、菜单、课表时间轴这八处玻璃面，
 * 原来各自 `collectAsState` 六七个 flow、各自算 `cardTheme == Liquid`。改一档
 * 参数要找八个地方，还容易出现「卡片改了、顶栏没改」的材质不一致。现在只在
 * [rememberWyuGlassStyle] 里读一次。
 *
 * [tuning] 已按深浅色解析过；[softGlass] / [liquidGlass] 已经把「设备是否支持
 * 模糊」折进去了，调用方不必再判 [wyuSoftGlassSupported]。
 */
internal class WyuGlassStyle(
    /** 原始主题值：菜单还要区分「柔光玻璃」与液态两个预设。 */
    val cardTheme: WyuCardTheme,
    val softGlassOn: Boolean,
    val blur: Int,
    val tuning: WyuSoftGlassTuning,
    val liquid: Boolean,
    val liquidBlur: Int,
    val liquidTuning: WyuLiquidGlassTuning,
    val dark: Boolean,
) {
    /** 柔光玻璃（上游 miuix-glass 管线）现在可用。 */
    val softGlass: Boolean get() = softGlassOn && wyuSoftGlassSupported

    /** 液态玻璃现在可用。 */
    val liquidGlass: Boolean get() = softGlass && liquid
}

@Composable
internal fun rememberWyuGlassStyle(): WyuGlassStyle {
    val softGlassOn by WyuAppearance.softGlass.collectAsState()
    val blur by WyuAppearance.softGlassBlur.collectAsState()
    val tuning by WyuAppearance.softGlassTuning.collectAsState()
    val liquidTuning by WyuAppearance.liquidGlassTuning.collectAsState()
    val liquidBlur by WyuAppearance.liquidGlassBlur.collectAsState()
    val cardTheme by WyuAppearance.cardTheme.collectAsState()
    val dark = LocalWyuDarkTheme.current
    return remember(softGlassOn, blur, tuning, liquidTuning, liquidBlur, cardTheme, dark) {
        WyuGlassStyle(
            cardTheme = cardTheme,
            softGlassOn = softGlassOn,
            blur = blur,
            tuning = tuning.resolvedFor(dark),
            liquid = cardTheme == WyuCardTheme.Liquid,
            liquidBlur = liquidBlur,
            liquidTuning = liquidTuning,
            dark = dark,
        )
    }
}

internal const val SoftGlassWhiteTintAlpha = 0.64f
private const val CardSoftGlassBlurScale = 0.6f// All soft-glass surfaces sample the backdrop at 1/2 resolution. Cards used to
// step down to 1/4, but there the corner rim lighting quantised into visible
// burrs once upscaled, so they stay at 1/2 too. miuix-blur only supports
// 1/2/4/8/16.
private const val CardSoftGlassDownscale = 2
private const val ButtonGlassAlpha = 0.78f

/**
 * Miuix renders card and button surfaces with its continuous-corner squircle,
 * so the blur underneath must share that silhouette; a plain rounded rect
 * leaves visible slivers at the four corners.
 */
internal class WyuSquircleShape(private val cornerRadius: Dp) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        if (size.width <= 0f || size.height <= 0f) return Outline.Rectangle(Rect.Zero)
        val radius = with(density) { cornerRadius.toPx() }
        return Outline.Generic(
            Path().apply { addSquircleRect(size.width, size.height, radius) },
        )
    }
}

/**
 * 把任意 [Shape] 的轮廓摊成一条 [Path]。
 *
 * 玻璃着色器按路径裁剪（squircle 走 `Outline.Generic`），三处画玻璃的地方
 * 原来各自抄了一遍这个 `when`。
 */
internal fun Shape.wyuOutlinePath(
    size: Size,
    layoutDirection: LayoutDirection,
    density: Density,
): Path = when (val outline = createOutline(size, layoutDirection, density)) {
    is Outline.Generic -> Path().apply { addPath(outline.path) }
    is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
    is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
}

internal const val CardBlurRadius = 38f
private const val ButtonBlurRadius = 30f
// Same live blur radius as the top-bar glass buttons, so a pill sitting next
// to them reads as the same material instead of a softer, card-like surface.
private const val TopBarGlassBlurRadius = 24f

/**
 * iOS ships an icon corner of 22.37% of the side, but that figure is the
 * *visual* radius of the quintic squircle. A continuous-corner path reads
 * rounder than an arc at the same nominal radius, so the nominal value is the
 * visual one scaled by 0.8 — verified against an arc of the same side length.
 */
internal const val WyuIconCornerRatio = 0.2237f * 0.8f

/**
 * The shared silhouette for a square tinted tile (a tool icon, an app icon).
 * Use it together with [rememberWyuIconShape] so the path is built once.
 */
internal fun wyuIconShape(side: Dp): Shape = WyuSquircleShape(side * WyuIconCornerRatio)

@Composable
internal fun rememberWyuIconShape(side: Dp): Shape = remember(side) { wyuIconShape(side) }

/** Same silhouette, but with the radius given directly — for tiles that are
 *  deliberately rounder than the shared ratio. */
@Composable
internal fun rememberWyuSquircle(radius: Dp): Shape = remember(radius) { WyuSquircleShape(radius) }

/** Corner every full-bleed surface has to land on: the sheets pushed by the
 *  navigator and the window a card opens into. Read from the panel itself on
 *  API 31+, so a phone with tighter or rounder body corners is followed instead
 *  of guessed; devices that cannot report one share the sheets' fallback. */
internal val WyuNavPageCornerFallback = 48.dp

@Composable
internal fun rememberDisplayCornerPx(): Float {
    val view = LocalView.current
    val density = LocalDensity.current
    return remember(view, density) {
        val reported = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            0
        } else {
            runCatching {
                val display = view.display ?: return@runCatching 0
                intArrayOf(
                    RoundedCorner.POSITION_TOP_LEFT,
                    RoundedCorner.POSITION_TOP_RIGHT,
                    RoundedCorner.POSITION_BOTTOM_LEFT,
                    RoundedCorner.POSITION_BOTTOM_RIGHT,
                ).maxOfOrNull { display.getRoundedCorner(it)?.radius ?: 0 } ?: 0
            }.getOrDefault(0)
        }
        if (reported > 0) reported.toFloat() else with(density) { WyuNavPageCornerFallback.toPx() }
    }
}

/** One corner radius for every card in the app, one step below the dialogs' 32dp. */
internal val WyuCardCornerRadius = 28.dp

/**
 * 卡片投影。只做一点点浮起：浅色下把卡片从壁纸上托起来，边缘落一圈很淡的暗部；
 * 深色下压得更低，只在轮廓外留一点几乎看不见的阴影，不去抢玻璃本身的高光。
 */
private val WyuCardShadowElevation = 8.dp

private fun Modifier.wyuCardShadow(shape: Shape, dark: Boolean): Modifier = shadow(
    elevation = WyuCardShadowElevation,
    shape = shape,
    clip = false,
    ambientColor = Color.Black.copy(alpha = if (dark) 0.26f else 0.07f),
    spotColor = Color.Black.copy(alpha = if (dark) 0.40f else 0.15f),
)

// Rim values tuned for cards are larger than a short pill is tall: a 40dp band
// would spend its whole height inside the rim, and the top edge then samples
// outside the captured layer and stays unfilled. These are the numbers the 38dp
// segmented pill uses, which is the same height class as the week capsule.
private const val CompactGlassEdgePx = 40f
private const val CompactGlassEdgePow = 2.2f
private const val CompactGlassRefractionPx = 56f
private const val CompactGlassReflectionPx = 800f
private const val CompactGlassBlurRadius = 30f

/**
 * Frosted-glass variants of Miuix's card and button. They stay identical to the
 * originals unless a wallpaper-only backdrop is published, which only happens
 * while a custom wallpaper is active.
 *
 * Both kinds of card share this entry point: the glass-only knobs
 * ([samplePageContent], [compactGlass]) and the press parameters are optional,
 * and a plain container still takes Miuix's non-interactive path.
 *
 * [samplePageContent] and a click are mutually exclusive. A surface that reads
 * the live page cannot also be recorded as part of that page — the glass would
 * end up sampling a layer that contains its own copy.
 */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = WyuCardCornerRadius,
    insideMargin: PaddingValues = CardDefaults.InsideMargin,
    colors: CardColors = CardDefaults.defaultColors(),
    /** Read the live page behind this card instead of the wallpaper. Only
     *  overlays (date capsules, bars) may use it. */
    samplePageContent: Boolean = false,
    /** Short surface (a 40dp band): keep the rim inside the shape. */
    compactGlass: Boolean = false,
    pressFeedbackType: PressFeedbackType = PressFeedbackType.None,
    showIndication: Boolean = false,
    holdDownState: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactive = onClick != null || onLongPress != null || showIndication ||
        holdDownState || pressFeedbackType != PressFeedbackType.None
    check(!samplePageContent || !interactive) {
        "A page-sampling Card cannot be clickable: it is recorded as part of the page it samples."
    }
    val glassReady = LocalWyuGlassReady.current
    val glass = if (samplePageContent) {
        LocalWyuPageBackdrop.current
    } else {
        LocalWyuGlassBackdrop.current
    }
    val underlay = LocalWyuGlassUnderlay.current
    val gaussianUnderlay = LocalWyuGaussianUnderlay.current
    val style = rememberWyuGlassStyle()
    val dark = style.dark
    val wallpaper = LocalWyuWallpaper.current
    // Preset gradients are tinted by Monet; in light mode the user wants
    // their cards to stay pure white regardless of the wallpaper seed.
    val cardColors = if (!dark && wallpaper != WyuWallpaper.Custom) {
        colors.copy(color = Color.White)
    } else {
        colors
    }
    // Default containers keep the white/dark glass wash; custom containers
    // (e.g. elec card primaryContainer) carry their own tint into the shader.
    // The dark wash starts at zero alpha, so the slider adds smoke from nothing;
    // custom tints must not follow it or coloured cards lose their colour in
    // dark mode.
    val softGlassCardTint = when {
        cardColors.color != MiuixTheme.colorScheme.surfaceContainer ->
            cardColors.color.copy(alpha = style.tuning.cardTintAlphaLight)
        dark -> Color.Black.copy(alpha = style.tuning.cardTintAlphaDark)
        else -> Color.White.copy(alpha = style.tuning.cardTintAlphaLight)
    }
    val liquidCardTint = when {
        cardColors.color != MiuixTheme.colorScheme.surfaceContainer ->
            cardColors.color.copy(alpha = style.liquidTuning.cardTintAlphaLight)
        dark -> Color.Black.copy(alpha = style.liquidTuning.cardTintAlphaDark)
        else -> Color.White.copy(alpha = style.liquidTuning.cardTintAlphaLight)
    }
    val shape = remember(cornerRadius) { WyuSquircleShape(cornerRadius) }
    val blurRadius = if (compactGlass) CompactGlassBlurRadius else CardBlurRadius
    val liquidEdgePx = if (compactGlass) CompactGlassEdgePx else style.liquidTuning.shapeEdgePx
    val liquidEdgePow = if (compactGlass) CompactGlassEdgePow else style.liquidTuning.shapeEdgePow
    val liquidRefractionPx = if (compactGlass) {
        CompactGlassRefractionPx
    } else {
        style.liquidTuning.edgeThickness
    }
    val liquidReflectionPx = if (compactGlass) {
        CompactGlassReflectionPx
    } else {
        style.liquidTuning.reflectOffset
    }
    // 投影挂在最外层：里面几层都会把内容裁到圆角里，放里面就看不见了。
    val shadowed = modifier.wyuCardShadow(shape, dark)
    val glassModifier = when {
        glass == null -> shadowed
        style.liquidGlass && !samplePageContent && underlay != null ->
            shadowed.wyuLiquidGlass(
                backdrop = null,
                underlay = underlay,
                shape = shape,
                cornerRadius = cornerRadius,
                tuning = style.liquidTuning,
                dark = dark,
                baseTint = liquidCardTint,
                shapeEdgePx = liquidEdgePx,
                shapeEdgePow = liquidEdgePow,
                refractionThicknessPx = liquidRefractionPx,
                reflectionThicknessPx = liquidReflectionPx,
            )
        style.liquidGlass ->
            shadowed.wyuLiquidGlass(
                backdrop = glass,
                shape = shape,
                cornerRadius = cornerRadius,
                tuning = style.liquidTuning,
                dark = dark,
                baseTint = liquidCardTint,
                blurPercent = style.liquidBlur,
                // Interactive cards always used the raw scale; ordinary
                // containers keep the cheaper card scale they were tuned with.
                blurScale = if (interactive) 1f else CardSoftGlassBlurScale,
                downscale = CardSoftGlassDownscale,
                shapeEdgePx = liquidEdgePx,
                shapeEdgePow = liquidEdgePow,
                refractionThicknessPx = liquidRefractionPx,
                reflectionThicknessPx = liquidReflectionPx,
            )
        style.softGlass && !style.liquid && !samplePageContent && underlay != null ->
            shadowed.wyuCardGlass(
                underlay = underlay,
                shape = shape,
                cornerRadius = cornerRadius,
                style = wyuUpstreamGlassStyle(style.tuning, dark, style.blur),
                tint = softGlassCardTint,
            )
        style.softGlass && glassReady && !style.liquid ->
            shadowed.wyuUpstreamCardGlass(
                backdrop = glass,
                cornerRadius = cornerRadius,
                tuning = style.tuning,
                dark = dark,
                blurPercent = style.blur,
                tint = softGlassCardTint,
            )
        style.softGlass ->
            if (underlay != null) {
                shadowed.wyuWallpaperBlur(underlay = underlay, shape = shape)
            } else {
                shadowed.textureBlur(backdrop = glass, shape = shape, blurRadius = blurRadius)
            }
        !samplePageContent && gaussianUnderlay != null ->
            shadowed.wyuWallpaperBlur(underlay = gaussianUnderlay, shape = shape)
        else -> shadowed.textureBlur(backdrop = glass, shape = shape, blurRadius = blurRadius)
    }
    val surfaceColors = if (glass != null) {
        cardColors.frosted(
            if (style.softGlass) CardSoftGlassAlpha else CardGlassAlpha,
        )
    } else {
        cardColors
    }
    if (interactive) {
        // Cards stay silent on purpose: they are large surfaces (tool tiles,
        // semester headers, list rows) where a tick reads as noise. Only real
        // buttons answer with a haptic.
        MiuixCard(
            modifier = glassModifier,
            cornerRadius = cornerRadius,
            insideMargin = insideMargin,
            colors = surfaceColors,
            pressFeedbackType = pressFeedbackType,
            showIndication = showIndication,
            holdDownState = holdDownState,
            onClick = if (onClick != null) {
                { onClick.invoke() }
            } else {
                null
            },
            onLongPress = onLongPress,
            content = content,
        )
    } else {
        MiuixCard(
            modifier = glassModifier,
            cornerRadius = cornerRadius,
            insideMargin = insideMargin,
            colors = surfaceColors,
            content = content,
        )
    }
}

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cornerRadius: Dp = ButtonDefaults.CornerRadius,
    minWidth: Dp = ButtonDefaults.MinWidth,
    minHeight: Dp = ButtonDefaults.MinHeight,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    insideMargin: PaddingValues = ButtonDefaults.InsideMargin,
    content: @Composable RowScope.() -> Unit,
) {
    val glass = LocalWyuGlassBackdrop.current
    val useGlass = glass != null && colors.color.alpha > 0.01f
    val shape = remember(cornerRadius) { WyuSquircleShape(cornerRadius) }
    // Every button in the app is one of these, so the tap tick lives here
    // instead of at each call site.
    val tap = hapticClick(enabled) { onClick() }
    MiuixButton(
        onClick = tap,
        modifier = if (useGlass) {
            modifier.textureBlur(backdrop = glass, shape = shape, blurRadius = ButtonBlurRadius)
        } else {
            modifier
        },
        enabled = enabled,
        cornerRadius = cornerRadius,
        minWidth = minWidth,
        minHeight = minHeight,
        colors = if (useGlass) colors.frosted(ButtonGlassAlpha) else colors,
        insideMargin = insideMargin,
        content = content,
    )
}

/**
 * Text-only button, same tick as [Button]. Declared here (instead of importing
 * the Miuix one at every call site) so no click path can skip the haptic.
 */
@Composable
fun TextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    MiuixTextButton(
        text = text,
        onClick = hapticClick(enabled, onClick),
        modifier = modifier,
        enabled = enabled,
    )
}

/** Icon-only button, same tick as [Button]. */
@Composable
fun IconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    MiuixIconButton(
        onClick = hapticClick(enabled, onClick),
        modifier = modifier,
        enabled = enabled,
        content = content,
    )
}

/**
 * Material Design 3 filled card, used while 莫奈 is the active theme.
 *
 * Opaque tonal container, MD3's 12dp corner and an 8% state layer on press —
 * no blur, no baked underlay, nothing sampled from the page behind. Custom
 * containers (a course tile, an elec `primaryContainer`) keep their colour;
 * only the stock `surfaceContainer` is remapped to MD3's `surfaceContainerHigh`
 * so cards still separate from the page.
 */
private fun CardColors.frosted(alpha: Float): CardColors =
    if (color.alpha >= 0.99f) copy(color = color.copy(alpha = alpha)) else this

private fun ButtonColors.frosted(alpha: Float): ButtonColors = copy(
    color = color.glassTint(alpha),
    disabledColor = disabledColor.glassTint(alpha * 0.75f),
)

private fun Color.glassTint(alpha: Float): Color =
    if (this.alpha >= 0.99f) copy(alpha = alpha) else this

private const val InputBlurRadius = 46f

/** 弹窗、弹出菜单的模糊度固定档： overlays 不跟随设置里的模糊档位。 */
internal const val PopupGlassBlurPercent = 40

/**
 * Glass input field: blurs whatever surface sits behind it (the page backdrop
 * on screens, the dialog-registered page layer inside popups) and falls back
 * to a quiet wash without one. 现在只有这一个重载：文本由 [TextFieldState]
 * 持有，[onValueChange] 把变化转成 String 供 ViewModel 同步。
 *
 * @param secure 显示为圆点（新 foundation API 没有 SecureTextField，
 *   用输出变换混淆；露出明文时由调用方传 false）。
 */
@Composable
fun GlassTextField(
    state: TextFieldState,
    label: String,
    modifier: Modifier = Modifier,
    insideMargin: DpSize = TextFieldDefaults.InsideMargin,
    enabled: Boolean = true,
    secure: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    inputTransformation: InputTransformation? = null,
    onValueChange: ((String) -> Unit)? = null,
) {
    // 对话框里 LocalWyuGlassBackdrop 被置空（弹窗在另一个窗口，拿不到页面那层
    // 墙纸），输入框会退化成一块纯色。弹窗自己把页面层登记进
    // LocalWyuDialogInputBackdrop，这里跟着用，取色和模糊就与页面上的输入框一致。
    // 注意不能直接用 WyuPageBackdrops.current：页面里的输入框本身就在页面层里，
    // 采样自己会让 hwui 递归到栈溢出（成绩页页签那次闪退就是这个原因）。
    val glass = LocalWyuGlassBackdrop.current ?: LocalWyuDialogInputBackdrop.current
    val shape = remember { WyuSquircleShape(TextFieldDefaults.CornerRadius) }
    val glassStyle = rememberWyuGlassStyle()
    val dark = glassStyle.dark
    val useUpstreamGlass = glass != null &&
        glassStyle.softGlass && !glassStyle.liquid && LocalWyuGlassReady.current
    val useLiquidGlass = glass != null && glassStyle.liquidGlass
    val glassModifier = when {
        glass == null -> modifier
        useLiquidGlass -> modifier.wyuLiquidGlass(
            backdrop = glass,
            shape = shape,
            cornerRadius = TextFieldDefaults.CornerRadius,
            tuning = glassStyle.liquidTuning,
            dark = dark,
            baseTint = wyuLiquidControlTint(),
            blurPercent = glassStyle.liquidBlur,
        )
        useUpstreamGlass -> modifier.wyuUpstreamCardGlass(
            backdrop = glass,
            cornerRadius = TextFieldDefaults.CornerRadius,
            tuning = glassStyle.tuning,
            dark = dark,
            blurPercent = glassStyle.blur,
            tint = wyuSoftGlassSurfaceTint(),
        )

        else -> modifier.textureBlur(backdrop = glass, shape = shape, blurRadius = InputBlurRadius)
    }
    // ViewModel 仍以 String 持有文本：state 一变就转出去（初始值也会发一次）。
    val listener = rememberUpdatedState(onValueChange)
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.collect { listener.value?.invoke(it) }
    }
    MiuixTextField(
        state = state,
        modifier = glassModifier,
        insideMargin = insideMargin,
        colors = glassTextFieldColors(glass != null),
        label = label,
        useLabelAsPlaceholder = true,
        enabled = enabled,
        inputTransformation = inputTransformation,
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction,
        lineLimits = TextFieldLineLimits.SingleLine,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        outputTransformation = if (secure) WyuPasswordMask else null,
    )
}

/** 密码混淆：foundation 新 API 没有 SecureTextField，用输出变换画圆点。 */
private val WyuPasswordMask = OutputTransformation {
    if (length > 0) replace(0, length, "\u2022".repeat(length))
}

@Composable
private fun glassTextFieldColors(frosted: Boolean): TextFieldColors {
    val scheme = MiuixTheme.colorScheme
    return TextFieldDefaults.textFieldColors(
        backgroundColor = if (frosted) {
            scheme.surfaceContainer.copy(alpha = 0.45f)
        } else {
            scheme.onSurface.copy(alpha = 0.055f)
        },
        labelColor = scheme.onSurfaceVariantSummary,
        borderColor = scheme.primary.copy(alpha = 0.4f),
    )
}
