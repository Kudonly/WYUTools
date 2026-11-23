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

/** Cards and the dropdown popup share one style so the two materials match. */
@Composable
internal fun wyuUpstreamGlassStyle(
    tuning: WyuSoftGlassTuning,
    dark: Boolean,
    blurPercent: Int,
): GlassStyle {
    val density = LocalDensity.current.density.coerceAtLeast(0.1f)
    val scale = GlassDefaults.SourceDensity / density
    val base = if (dark) {
        GlassStyles.CommonMediumRegularDark
    } else {
        GlassStyles.CommonMediumRegularLowLight
    }
    return GlassStyle(
        blend = base.blend.copy(
            amount = tuning.blendAmount,
            saturation = tuning.blendSaturation,
            brightness = tuning.blendBrightness,
        ),
        inner = base.inner.copy(
            bottom = tuning.innerBottom,
            colorWhite = tuning.innerColorWhite,
            colorMix = tuning.innerColorMix,
            colorPow = tuning.innerColorPow,
        ),
        edge = base.edge.copy(
            width = tuning.shapeEdgePx * scale,
            pow = tuning.shapeEdgePow,
            thickness = tuning.edgeThickness * scale,
            reflectOffset = tuning.reflectOffset * scale,
        ),
        reflect = base.reflect.copy(
            lighten = tuning.reflectLighten,
            strength = tuning.reflectionStrength,
        ),
        light = base.light.copy(
            intensity = tuning.lightIntensity,
            oppositeIntensity = tuning.lightOppositeIntensity,
        ),
        refract = base.refract.copy(ior = tuning.refractionIor),
        background = base.background.copy(
            saturation = tuning.saturation(dark),
            brightness = tuning.backgroundBrightness,
        ),
        blur = base.blur.copy(
            small = (wyuSoftGlassScreenBlurPx(blurPercent) * 4f * scale).coerceIn(4f, 160f),
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
): Modifier = glass(
    backdrop = backdrop,
    shape = GlassShape(cornerRadius),
    style = wyuUpstreamGlassStyle(tuning, dark, blurPercent),
    tint = tint,
    shading = true,
)
