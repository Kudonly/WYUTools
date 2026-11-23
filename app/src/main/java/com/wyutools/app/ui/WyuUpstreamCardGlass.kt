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
 * Upstream miuix-glass surface for cards. The local 11-knob tuning is mapped
 * onto the closest GlassStyle fields; everything else keeps the upstream
 * preset. Pixel uniforms are authored at [GlassDefaults.SourceDensity] (3x),
 * so they are scaled by sourceDensity / actualDensity before being passed in.
 */
@Composable
internal fun Modifier.wyuUpstreamCardGlass(
    backdrop: Backdrop,
    cornerRadius: Dp,
    dark: Boolean,
    blurPercent: Int,
    tint: Color,
    edgePx: Float,
    edgePow: Float,
    refractionPx: Float,
    reflectionPx: Float,
    reflectionLighten: Float,
    reflectionStrength: Float,
    refractionIor: Float,
    bgSaturation: Float,
): Modifier {
    val density = LocalDensity.current.density.coerceAtLeast(0.1f)
    val scale = GlassDefaults.SourceDensity / density
    val style = wyuCardGlassStyle(
        dark = dark,
        blurSmall = (wyuSoftGlassScreenBlurPx(blurPercent) * scale).coerceIn(4f, 120f),
        edgeWidth = edgePx * scale,
        edgePow = edgePow,
        thickness = refractionPx * scale,
        reflectOffset = reflectionPx * scale,
        reflectLighten = reflectionLighten,
        reflectStrength = reflectionStrength,
        ior = refractionIor,
        saturation = bgSaturation,
    )
    return glass(
        backdrop = backdrop,
        shape = GlassShape(cornerRadius),
        style = style,
        tint = tint,
        shading = true,
    )
}

private fun wyuCardGlassStyle(
    dark: Boolean,
    blurSmall: Float,
    edgeWidth: Float,
    edgePow: Float,
    thickness: Float,
    reflectOffset: Float,
    reflectLighten: Float,
    reflectStrength: Float,
    ior: Float,
    saturation: Float,
): GlassStyle {
    val base = if (dark) {
        GlassStyles.CommonMediumRegularDark
    } else {
        GlassStyles.CommonMediumRegularLowLight
    }
    return base.copy(
        edge = base.edge.copy(
            width = edgeWidth.coerceIn(4f, 240f),
            pow = edgePow.coerceIn(0.5f, 8f),
            thickness = thickness.coerceIn(0f, 400f),
            reflectOffset = reflectOffset.coerceIn(0f, 1600f),
        ),
        reflect = base.reflect.copy(
            lighten = reflectLighten.coerceIn(0f, 2f),
            strength = reflectStrength.coerceIn(0f, 2f),
        ),
        refract = base.refract.copy(ior = ior.coerceIn(0.1f, 10f)),
        background = base.background.copy(saturation = saturation.coerceIn(0f, 3f)),
        blur = base.blur.copy(small = blurSmall),
    )
}
