package com.wyutools.app.ui

import android.os.Build
import android.view.RoundedCorner
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.text.input.TextFieldValue
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
import top.yukonga.miuix.kmp.theme.LocalContentColor
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

internal const val SoftGlassWhiteTintAlpha = 0.64f
private const val CardSoftGlassBlurScale = 0.6f
// All soft-glass surfaces sample the backdrop at 1/2 resolution. Cards used to
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
 */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = WyuCardCornerRadius,
    insideMargin: PaddingValues = CardDefaults.InsideMargin,
    colors: CardColors = CardDefaults.defaultColors(),
    samplePageContent: Boolean = false,
    /** Short surface (a 40dp band): keep the rim inside the shape. */
    compactGlass: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glassReady = LocalWyuGlassReady.current
    val glass = if (samplePageContent) {
        LocalWyuPageBackdrop.current
    } else {
        LocalWyuGlassBackdrop.current
    }
    val underlay = LocalWyuGlassUnderlay.current
    val gaussianUnderlay = LocalWyuGaussianUnderlay.current
    val softGlass by WyuAppearance.softGlass.collectAsState()
    val softGlassBlur by WyuAppearance.softGlassBlur.collectAsState()
    val tuning by WyuAppearance.softGlassTuning.collectAsState()
    val tuningForTheme = tuning.resolvedFor(LocalWyuDarkTheme.current)
    val cardTheme by WyuAppearance.cardTheme.collectAsState()
    val liquidTuning by WyuAppearance.liquidGlassTuning.collectAsState()
    val liquidBlur by WyuAppearance.liquidGlassBlur.collectAsState()
    val isLiquid = cardTheme == WyuCardTheme.Liquid
    val dark = LocalWyuDarkTheme.current
    // Default containers keep the white/dark glass wash; custom containers
    // (e.g. elec card primaryContainer) carry their own tint into the shader.
    // The dark wash starts at zero alpha, so the slider adds smoke from nothing;
    // custom tints must not follow it or coloured cards lose their colour in
    // dark mode.
    val softGlassCardTint = when {
        colors.color != MiuixTheme.colorScheme.surfaceContainer ->
            colors.color.copy(alpha = tuningForTheme.cardTintAlphaLight)
        dark -> Color.Black.copy(alpha = tuningForTheme.cardTintAlphaDark)
        else -> Color.White.copy(alpha = tuningForTheme.cardTintAlphaLight)
    }
    val liquidCardTint = when {
        colors.color != MiuixTheme.colorScheme.surfaceContainer ->
            colors.color.copy(alpha = liquidTuning.cardTintAlphaLight)
        dark -> Color.Black.copy(alpha = liquidTuning.cardTintAlphaDark)
        else -> Color.White.copy(alpha = liquidTuning.cardTintAlphaLight)
    }
    val shape = remember(cornerRadius) { WyuSquircleShape(cornerRadius) }
    val edgePx = if (compactGlass) CompactGlassEdgePx else tuningForTheme.shapeEdgePx
    val edgePow = if (compactGlass) CompactGlassEdgePow else tuningForTheme.shapeEdgePow
    val refractionPx = if (compactGlass) CompactGlassRefractionPx else tuningForTheme.edgeThickness
    val reflectionPx = if (compactGlass) CompactGlassReflectionPx else tuningForTheme.reflectOffset
    val blurRadius = if (compactGlass) CompactGlassBlurRadius else CardBlurRadius
    val liquidEdgePx = if (compactGlass) CompactGlassEdgePx else liquidTuning.shapeEdgePx
    val liquidEdgePow = if (compactGlass) CompactGlassEdgePow else liquidTuning.shapeEdgePow
    val liquidRefractionPx = if (compactGlass) CompactGlassRefractionPx else liquidTuning.edgeThickness
    val liquidReflectionPx = if (compactGlass) CompactGlassReflectionPx else liquidTuning.reflectOffset
    // 投影挂在最外层：里面几层都会把内容裁到圆角里，放里面就看不见了。
    val shadowed = modifier.wyuCardShadow(shape, dark)
    MiuixCard(
        modifier = when {
            glass == null -> shadowed
            isLiquid && softGlass && wyuSoftGlassSupported && !samplePageContent && underlay != null ->
                shadowed.wyuLiquidGlass(
                    backdrop = null,
                    underlay = underlay,
                    shape = shape,
                    cornerRadius = cornerRadius,
                    tuning = liquidTuning,
                    dark = dark,
                    baseTint = liquidCardTint,
                    shapeEdgePx = liquidEdgePx,
                    shapeEdgePow = liquidEdgePow,
                    refractionThicknessPx = liquidRefractionPx,
                    reflectionThicknessPx = liquidReflectionPx,
                )
            isLiquid && softGlass && wyuSoftGlassSupported ->
                shadowed.wyuLiquidGlass(
                    backdrop = glass,
                    shape = shape,
                    cornerRadius = cornerRadius,
                    tuning = liquidTuning,
                    dark = dark,
                    baseTint = liquidCardTint,
                    blurPercent = liquidBlur,
                    blurScale = CardSoftGlassBlurScale,
                    downscale = CardSoftGlassDownscale,
                    shapeEdgePx = liquidEdgePx,
                    shapeEdgePow = liquidEdgePow,
                    refractionThicknessPx = liquidRefractionPx,
                    reflectionThicknessPx = liquidReflectionPx,
                )
            softGlass && wyuSoftGlassSupported && glassReady && !isLiquid ->
                shadowed.wyuUpstreamCardGlass(
                    backdrop = glass,
                    cornerRadius = cornerRadius,
                    tuning = tuningForTheme,
                    dark = dark,
                    blurPercent = softGlassBlur,
                    tint = softGlassCardTint,
                )
            softGlass && wyuSoftGlassSupported && underlay != null ->
                shadowed.wyuWallpaperBlur(underlay = underlay, shape = shape)
            softGlass && wyuSoftGlassSupported ->
                shadowed.textureBlur(backdrop = glass, shape = shape, blurRadius = blurRadius)
            !samplePageContent && gaussianUnderlay != null ->
                shadowed.wyuWallpaperBlur(underlay = gaussianUnderlay, shape = shape)
            else -> shadowed.textureBlur(backdrop = glass, shape = shape, blurRadius = blurRadius)
        },
        cornerRadius = cornerRadius,
        insideMargin = insideMargin,
        colors = if (glass != null) {
            colors.frosted(if (softGlass && wyuSoftGlassSupported) CardSoftGlassAlpha else CardGlassAlpha)
        } else {
            colors
        },
        content = content,
    )
}

@Composable
fun Card(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = WyuCardCornerRadius,
    insideMargin: PaddingValues = CardDefaults.InsideMargin,
    colors: CardColors = CardDefaults.defaultColors(),
    pressFeedbackType: PressFeedbackType = PressFeedbackType.None,
    showIndication: Boolean = false,
    holdDownState: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glass = LocalWyuGlassBackdrop.current
    val glassReady = LocalWyuGlassReady.current
    val underlay = LocalWyuGlassUnderlay.current
    val gaussianUnderlay = LocalWyuGaussianUnderlay.current
    val softGlass by WyuAppearance.softGlass.collectAsState()
    val softGlassBlur by WyuAppearance.softGlassBlur.collectAsState()
    val tuning by WyuAppearance.softGlassTuning.collectAsState()
    val tuningForTheme = tuning.resolvedFor(LocalWyuDarkTheme.current)
    val cardTheme by WyuAppearance.cardTheme.collectAsState()
    val liquidTuning by WyuAppearance.liquidGlassTuning.collectAsState()
    val liquidBlur by WyuAppearance.liquidGlassBlur.collectAsState()
    val isLiquid = cardTheme == WyuCardTheme.Liquid
    val dark = LocalWyuDarkTheme.current
    // Default containers keep the white/dark glass wash; custom containers
    // (e.g. elec card primaryContainer) carry their own tint into the shader.
    // The dark wash starts at zero alpha, so the slider adds smoke from nothing;
    // custom tints must not follow it or coloured cards lose their colour in
    // dark mode.
    val softGlassCardTint = when {
        colors.color != MiuixTheme.colorScheme.surfaceContainer ->
            colors.color.copy(alpha = tuningForTheme.cardTintAlphaLight)
        dark -> Color.Black.copy(alpha = tuningForTheme.cardTintAlphaDark)
        else -> Color.White.copy(alpha = tuningForTheme.cardTintAlphaLight)
    }
    val liquidCardTint = when {
        colors.color != MiuixTheme.colorScheme.surfaceContainer ->
            colors.color.copy(alpha = liquidTuning.cardTintAlphaLight)
        dark -> Color.Black.copy(alpha = liquidTuning.cardTintAlphaDark)
        else -> Color.White.copy(alpha = liquidTuning.cardTintAlphaLight)
    }
    val shape = remember(cornerRadius) { WyuSquircleShape(cornerRadius) }
    val shadowed = modifier.wyuCardShadow(shape, dark)
    // Cards stay silent on purpose: they are large surfaces (tool tiles,
    // semester headers, list rows) where a tick reads as noise. Only real
    // buttons answer with a haptic.
    MiuixCard(
        modifier = when {
            glass == null -> shadowed
            isLiquid && softGlass && wyuSoftGlassSupported && underlay != null ->
                shadowed.wyuLiquidGlass(
                    backdrop = null,
                    underlay = underlay,
                    shape = shape,
                    cornerRadius = cornerRadius,
                    tuning = liquidTuning,
                    dark = dark,
                    baseTint = liquidCardTint,
                )
            isLiquid && softGlass && wyuSoftGlassSupported ->
                shadowed.wyuLiquidGlass(
                    backdrop = glass,
                    shape = shape,
                    cornerRadius = cornerRadius,
                    tuning = liquidTuning,
                    dark = dark,
                    baseTint = liquidCardTint,
                    blurPercent = liquidBlur,
                )
            softGlass && wyuSoftGlassSupported && glassReady && !isLiquid ->
                shadowed.wyuUpstreamCardGlass(
                    backdrop = glass,
                    cornerRadius = cornerRadius,
                    tuning = tuningForTheme,
                    dark = dark,
                    blurPercent = softGlassBlur,
                    tint = softGlassCardTint,
                )
            softGlass && wyuSoftGlassSupported && underlay != null ->
                shadowed.wyuWallpaperBlur(underlay = underlay, shape = shape)
            gaussianUnderlay != null ->
                shadowed.wyuWallpaperBlur(underlay = gaussianUnderlay, shape = shape)
            else -> shadowed.textureBlur(backdrop = glass, shape = shape, blurRadius = CardBlurRadius)
        },
        cornerRadius = cornerRadius,
        insideMargin = insideMargin,
        colors = if (glass != null) {
            colors.frosted(if (softGlass && wyuSoftGlassSupported) CardSoftGlassAlpha else CardGlassAlpha)
        } else {
            colors
        },
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
}

/**
 * Glass card for top-bar chrome that samples the **live page content** behind
 * it, the same way the neighbouring top-bar glass buttons (refresh / back) do.
 *
 * The regular [Card] points its shader at `LocalWyuGlassBackdrop` — wallpaper
 * only — and, when a custom wallpaper is set, at a cached pre-baked underlay
 * bitmap. That is cheap but frozen: the list scrolling underneath never shows
 * through. `LocalWyuPageBackdrop` records the `wyu-content` layer live every
 * frame; the top bar lives in a separate subcompose slot, so it is not part of
 * the capture and the pill cannot frost an image of itself.
 *
 * No baked underlay branch here and no pre-blurred wallpaper fallback: the
 * surface stays live in every appearance mode. Shader tuning mirrors
 * `WyuTopBarGlassButton` so the pill and the round buttons read as one material.
 */
@Composable
fun LiveGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = WyuCardCornerRadius,
    insideMargin: PaddingValues = CardDefaults.InsideMargin,
    colors: CardColors = CardDefaults.defaultColors(),
    content: @Composable ColumnScope.() -> Unit,
) {
    // Same live source as the adjacent top-bar glass buttons: the content layer,
    // not the wallpaper-only backdrop.
    val backdrop = LocalWyuPageBackdrop.current
    val softGlass by WyuAppearance.softGlass.collectAsState()
    val softGlassBlur by WyuAppearance.softGlassBlur.collectAsState()
    val tuning by WyuAppearance.softGlassTuning.collectAsState()
    val tuningForTheme = tuning.resolvedFor(LocalWyuDarkTheme.current)
    val cardTheme by WyuAppearance.cardTheme.collectAsState()
    val liquidTuning by WyuAppearance.liquidGlassTuning.collectAsState()
    val liquidBlur by WyuAppearance.liquidGlassBlur.collectAsState()
    val isLiquid = cardTheme == WyuCardTheme.Liquid
    val dark = LocalWyuDarkTheme.current
    val useSoftGlass = softGlass && wyuSoftGlassSupported
    val shape = remember(cornerRadius) { WyuSquircleShape(cornerRadius) }
    val shadowed = modifier.wyuCardShadow(shape, dark)
    MiuixCard(
        // Never take the baked-underlay branch. When soft glass is off the
        // pre-blurred wallpaper is skipped too, so the surface stays live in
        // every appearance mode.
        modifier = when {
            backdrop == null -> shadowed
            useSoftGlass && isLiquid -> shadowed.wyuLiquidGlass(
                backdrop = backdrop,
                shape = shape,
                cornerRadius = cornerRadius,
                tuning = liquidTuning,
                dark = dark,
                baseTint = if (dark) {
                    Color.Transparent
                } else {
                    Color.White.copy(alpha = SoftGlassWhiteTintAlpha)
                },
                blurPercent = liquidBlur,
                refractionThicknessPx = TopBarGlassRefractionPx,
                reflectionThicknessPx = TopBarGlassReflectionPx,
            )
            useSoftGlass -> shadowed.wyuUpstreamCardGlass(
                backdrop = backdrop,
                cornerRadius = cornerRadius,
                tuning = tuning,
                dark = dark,
                blurPercent = softGlassBlur,
                tint = if (dark) {
                    Color.Transparent
                } else {
                    Color.White.copy(alpha = SoftGlassWhiteTintAlpha)
                },
            )
            else -> shadowed.textureBlur(
                backdrop = backdrop,
                shape = shape,
                blurRadius = TopBarGlassBlurRadius,
            )
        },
        cornerRadius = cornerRadius,
        insideMargin = insideMargin,
        colors = if (backdrop != null) {
            colors.frosted(if (useSoftGlass) CardSoftGlassAlpha else CardGlassAlpha)
        } else {
            colors
        },
        content = content,
    )
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

/**
 * Text field on a frosted surface. The fill sits a touch lighter than the
 * card's glass tint so the field reads as an inset without breaking the
 * surface; without a wallpaper backdrop it falls back to a quiet wash.
 */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    insideMargin: DpSize = TextFieldDefaults.InsideMargin,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    // 对话框里 LocalWyuGlassBackdrop 被置空（弹窗在另一个窗口，拿不到页面那层
    // 墙纸），输入框会退化成一块纯色。弹窗自己把页面层登记进
    // LocalWyuDialogInputBackdrop，这里跟着用，取色和模糊就与页面上的输入框一致。
    // 注意不能直接用 WyuPageBackdrops.current：页面里的输入框本身就在页面层里，
    // 采样自己会让 hwui 递归到栈溢出（成绩页页签那次闪退就是这个原因）。
    val glass = LocalWyuGlassBackdrop.current ?: LocalWyuDialogInputBackdrop.current
    val shape = remember { WyuSquircleShape(TextFieldDefaults.CornerRadius) }
    // 柔光玻璃开启时输入框与卡片共用同一套上游玻璃材质。
    val softGlassOn by WyuAppearance.softGlass.collectAsState()
    val softGlassBlur by WyuAppearance.softGlassBlur.collectAsState()
    val tuning by WyuAppearance.softGlassTuning.collectAsState()
    val liquidTuning by WyuAppearance.liquidGlassTuning.collectAsState()
    val liquidBlur by WyuAppearance.liquidGlassBlur.collectAsState()
    val isLiquid = WyuAppearance.cardTheme.value == WyuCardTheme.Liquid
    val dark = LocalWyuDarkTheme.current
    val useUpstreamGlass = glass != null &&
        softGlassOn && wyuSoftGlassSupported && !isLiquid && LocalWyuGlassReady.current
    val useLiquidGlass = glass != null && softGlassOn && wyuSoftGlassSupported && isLiquid
    val glassModifier = when {
        glass == null -> modifier
        useLiquidGlass -> modifier.wyuLiquidGlass(
            backdrop = glass,
            shape = shape,
            cornerRadius = TextFieldDefaults.CornerRadius,
            tuning = liquidTuning,
            dark = dark,
            baseTint = wyuGlassControlWash(),
            blurPercent = liquidBlur,
        )
        useUpstreamGlass -> modifier.wyuUpstreamCardGlass(
            backdrop = glass,
            cornerRadius = TextFieldDefaults.CornerRadius,
            tuning = tuning.resolvedFor(dark),
            dark = dark,
            blurPercent = softGlassBlur,
            tint = wyuGlassControlWash(),
        )

        else -> modifier.textureBlur(backdrop = glass, shape = shape, blurRadius = InputBlurRadius)
    }
    MiuixTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = glassModifier,
        insideMargin = insideMargin,
        colors = glassTextFieldColors(glass != null),
        label = label,
        useLabelAsPlaceholder = true,
        singleLine = true,
        enabled = enabled,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
    )
}

@Composable
fun GlassTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    insideMargin: DpSize = TextFieldDefaults.InsideMargin,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    // 对话框里 LocalWyuGlassBackdrop 被置空（弹窗在另一个窗口，拿不到页面那层
    // 墙纸），输入框会退化成一块纯色。弹窗自己把页面层登记进
    // LocalWyuDialogInputBackdrop，这里跟着用，取色和模糊就与页面上的输入框一致。
    // 注意不能直接用 WyuPageBackdrops.current：页面里的输入框本身就在页面层里，
    // 采样自己会让 hwui 递归到栈溢出（成绩页页签那次闪退就是这个原因）。
    val glass = LocalWyuGlassBackdrop.current ?: LocalWyuDialogInputBackdrop.current
    val shape = remember { WyuSquircleShape(TextFieldDefaults.CornerRadius) }
    // 柔光玻璃开启时输入框与卡片共用同一套上游玻璃材质。
    val softGlassOn by WyuAppearance.softGlass.collectAsState()
    val softGlassBlur by WyuAppearance.softGlassBlur.collectAsState()
    val tuning by WyuAppearance.softGlassTuning.collectAsState()
    val liquidTuning by WyuAppearance.liquidGlassTuning.collectAsState()
    val liquidBlur by WyuAppearance.liquidGlassBlur.collectAsState()
    val isLiquid = WyuAppearance.cardTheme.value == WyuCardTheme.Liquid
    val dark = LocalWyuDarkTheme.current
    val useUpstreamGlass = glass != null &&
        softGlassOn && wyuSoftGlassSupported && !isLiquid && LocalWyuGlassReady.current
    val useLiquidGlass = glass != null && softGlassOn && wyuSoftGlassSupported && isLiquid
    val glassModifier = when {
        glass == null -> modifier
        useLiquidGlass -> modifier.wyuLiquidGlass(
            backdrop = glass,
            shape = shape,
            cornerRadius = TextFieldDefaults.CornerRadius,
            tuning = liquidTuning,
            dark = dark,
            baseTint = wyuGlassControlWash(),
            blurPercent = liquidBlur,
        )
        useUpstreamGlass -> modifier.wyuUpstreamCardGlass(
            backdrop = glass,
            cornerRadius = TextFieldDefaults.CornerRadius,
            tuning = tuning.resolvedFor(dark),
            dark = dark,
            blurPercent = softGlassBlur,
            tint = wyuGlassControlWash(),
        )

        else -> modifier.textureBlur(backdrop = glass, shape = shape, blurRadius = InputBlurRadius)
    }
    MiuixTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = glassModifier,
        insideMargin = insideMargin,
        colors = glassTextFieldColors(glass != null),
        label = label,
        useLabelAsPlaceholder = true,
        singleLine = true,
        enabled = enabled,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
    )
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
