package com.wyutools.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.glass
import top.yukonga.miuix.kmp.material.GlassDefaults
import top.yukonga.miuix.kmp.material.GlassShape
import top.yukonga.miuix.kmp.material.GlassStyle
import top.yukonga.miuix.kmp.material.GlassStyles

/**
 * Radius, in the pipeline's source-density units, that produces [sigmaPx] on
 * screen.
 *
 * The pipeline turns a style radius into a blur sigma with
 * `radius / SourceDensity * density * 0.45`. The card path does not go through
 * it at all — it bakes the sigma straight into its underlay — so the pipeline
 * has to be handed the inverse of that conversion, or every other glass
 * surface ends up at a different frost level than the cards.
 */
private fun upstreamBlurRadius(sigmaPx: Float, density: Float): Float =
    (sigmaPx * GlassDefaults.SourceDensity / (density * 0.45f)).coerceIn(0f, 160f)

/**
 * The tint the pipeline should lay down for [tint], in the form the card shader
 * would use it.
 *
 * The shader multiplies the colour's alpha by the style's `tintStrength`; the
 * pipeline uses the alpha as given. Handing it the product keeps one wash
 * strength across surfaces that render through either path.
 */
internal fun wyuUpstreamGlassTint(tint: Color, style: GlassStyle): Color =
    tint.copy(alpha = (tint.alpha * style.inner.tintStrength).coerceIn(0f, 1f))

/** Cards and the dropdown popup share one style so the two materials match. */
@Composable
internal fun wyuUpstreamGlassStyle(
    tuning: WyuSoftGlassTuning,
    dark: Boolean,
    blurPercent: Int,
): GlassStyle {
    val density = LocalDensity.current.density.coerceAtLeast(0.1f)
    val scale = GlassDefaults.SourceDensity / density
    val t = tuning.resolvedFor(dark)
    val base = if (dark) {
        GlassStyles.CommonMediumRegularDark
    } else {
        GlassStyles.CommonMediumRegularLowLight
    }
    return GlassStyle(
        blend = base.blend.copy(
            amount = t.blendAmount,
            saturation = t.blendSaturation,
            brightness = t.blendBrightness,
        ),
        inner = base.inner.copy(
            bottom = t.innerBottom,
            colorWhite = t.innerColorWhite,
            colorMix = t.innerColorMix,
            colorPow = t.innerColorPow,
        ),
        edge = base.edge.copy(
            width = t.shapeEdgePx * scale,
            pow = t.shapeEdgePow,
            thickness = t.edgeThickness * scale,
            reflectOffset = t.reflectOffset * scale,
        ),
        reflect = base.reflect.copy(
            lighten = t.reflectLighten,
            strength = t.reflectionStrength,
        ),
        light = base.light.copy(
            intensity = t.lightIntensity,
            oppositeIntensity = t.lightOppositeIntensity,
        ),
        refract = base.refract.copy(ior = t.refractionIor),
        background = base.background.copy(
            saturation = t.saturationLight,
            brightness = t.backgroundBrightness,
        ),
        blur = base.blur.copy(
            // The card path bakes this same sigma into its underlay. Feeding the
            // pipeline the matching radius keeps cards, bars, buttons, menus and
            // dialogs at one frost level; 0% still means no blur at all.
            small = upstreamBlurRadius(wyuSoftGlassScreenBlurPx(blurPercent), density),
        ),
    )
}

@Composable
internal fun Modifier.wyuUpstreamCardGlass(
    backdrop: Backdrop,
    cornerRadius: Dp,
    tuning: WyuSoftGlassTuning,
    dark: Boolean,
    blurPercent: Int,
    tint: Color,
): Modifier {
    val style = wyuUpstreamGlassStyle(tuning, dark, blurPercent)
    return glass(
        backdrop = backdrop,
        shape = GlassShape(cornerRadius),
        style = style,
        tint = wyuUpstreamGlassTint(tint, style),
        noiseCoefficient = tuning.noiseCoefficient,
        shading = true,
    )
}
