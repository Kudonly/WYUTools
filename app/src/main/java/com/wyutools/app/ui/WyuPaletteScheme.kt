package com.wyutools.app.ui

import androidx.compose.ui.graphics.Color
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.hct.Hct
import com.materialkolor.dynamiccolor.DynamicScheme
import com.materialkolor.scheme.SchemeExpressive
import com.materialkolor.scheme.SchemeMonochrome
import com.materialkolor.scheme.SchemeNeutral
import com.materialkolor.scheme.SchemeTonalSpot
import com.materialkolor.scheme.SchemeVibrant
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

/**
 * How the 莫奈 theme turns the wallpaper seed into a palette. This is the
 * Material 3 Expressive knob (`paletteStyle`); it changes hues and chroma only,
 * never the shape language, so every option keeps the same surfaces.
 *
 * 表达 is the default: it rotates the primary hue away from the seed, which is
 * what makes the surfaces read as "designed" rather than as a tinted copy of
 * the wallpaper. [ThemeColorSpec.Spec2025] only redefines the first four, so 单色
 * stays on the 2021 mapping — grayscale anyway, and it is the one option that
 * needs it.
 */
enum class WyuPaletteScheme(
    val label: String,
    val summary: String,
    val light: ThemePaletteStyle,
    val dark: ThemePaletteStyle,
    val spec: ThemeColorSpec,
) {
    /** Default. Hue rotation on purpose: primary drifts away from the seed. */
    Expressive(
        "表达",
        "主色会偏离壁纸色，色相跳跃、更有设计感",
        ThemePaletteStyle.Expressive,
        ThemePaletteStyle.Expressive,
        ThemeColorSpec.Spec2025,
    ),

    /** Google's Material You default; the safest, least noisy mapping. */
    Balanced(
        "均衡",
        "Material You 经典配色，色调平衡、耐看",
        ThemePaletteStyle.TonalSpot,
        ThemePaletteStyle.TonalSpot,
        ThemeColorSpec.Spec2025,
    ),

    /** Maximum chroma: the seed colour leads the whole interface. */
    Vivid(
        "鲜艳",
        "彩度最高，主色和按钮最醒目",
        ThemePaletteStyle.Vibrant,
        ThemePaletteStyle.Vibrant,
        ThemeColorSpec.Spec2025,
    ),

    /** Low chroma, close to grey: quiet, text-first interfaces. */
    Soft(
        "柔和",
        "低彩度、接近灰调，界面安静克制",
        ThemePaletteStyle.Neutral,
        ThemePaletteStyle.Neutral,
        ThemeColorSpec.Spec2025,
    ),

    /** No hue at all, only tonal steps. */
    Mono(
        "单色",
        "完全去色，只保留明暗层次",
        ThemePaletteStyle.Monochrome,
        ThemePaletteStyle.Monochrome,
        ThemeColorSpec.Spec2021,
    ),
    ;

    fun style(isDark: Boolean): ThemePaletteStyle = if (isDark) dark else light

    companion object {
        val Default: WyuPaletteScheme = Expressive

        /**
         * Stored name → entry. The retired 自动（浅 Vibrant / 深 TonalSpot）entry
         * has no successor: it resolves to the default like any unknown value.
         */
        fun fromStored(name: String?): WyuPaletteScheme =
            entries.firstOrNull { it.name == name } ?: Default
    }
}

/**
 * Three-swatch preview for the picker: the palette this scheme would produce
 * from [seedArgb], so the rows are told apart by colour instead of by wording.
 * Only 6 schemes render per dialog, and the result is remembered by the caller.
 */
internal fun palettePreview(seedArgb: Int, scheme: WyuPaletteScheme, dark: Boolean): List<Color> {
    val hct = Hct.fromInt(seedArgb)
    val spec = when (scheme.spec) {
        ThemeColorSpec.Spec2025 -> ColorSpec.SpecVersion.SPEC_2025
        ThemeColorSpec.Spec2021 -> ColorSpec.SpecVersion.SPEC_2021
    }
    val dynamic = dynamicSchemeFor(scheme.style(dark), hct, dark, spec)
    return listOf(
        Color(dynamic.primary),
        Color(dynamic.secondaryContainer),
        Color(dynamic.tertiary),
    )
}

private fun dynamicSchemeFor(
    style: ThemePaletteStyle,
    hct: Hct,
    dark: Boolean,
    spec: ColorSpec.SpecVersion,
): DynamicScheme {
    val platform = DynamicScheme.Platform.PHONE
    return when (style) {
        ThemePaletteStyle.Neutral -> SchemeNeutral(hct, dark, 0.0, spec, platform)
        ThemePaletteStyle.Vibrant -> SchemeVibrant(hct, dark, 0.0, spec, platform)
        ThemePaletteStyle.Expressive -> SchemeExpressive(hct, dark, 0.0, spec, platform)
        ThemePaletteStyle.Monochrome -> SchemeMonochrome(hct, dark, 0.0, spec, platform)
        // 2025 only redefines these four, and the rest of the enum is not
        // exposed in the picker; TonalSpot is the safe stand-in.
        else -> SchemeTonalSpot(hct, dark, 0.0, spec, platform)
    }
}
