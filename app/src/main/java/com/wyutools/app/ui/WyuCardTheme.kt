package com.wyutools.app.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The surface language for cards and buttons. Only one can be active at a time,
 * so the settings screen offers them as a single choice instead of stacking
 * independent switches.
 */
enum class WyuCardTheme(val label: String, val summary: String) {
    /** Stock miuix surfaces with the frosted wallpaper wash. */
    Default("默认", "磨砂玻璃卡片，透出壁纸底色"),

    /** The liquid-glass material: refraction, reflection and edge highlight. */
    SoftGlass("柔光玻璃", "带折射与反射的液态玻璃材质"),

    /** Material Design 3: filled tonal cards and solid buttons, no blur. */
    Monet("莫奈", "Material Design 3 风格，实色卡片与按钮"),
}

/**
 * True while [WyuCardTheme.Monet] is active. Published once at the theme root so
 * every card and button reads a composition local instead of collecting the
 * preference flow on its own.
 */
internal val LocalWyuMd3Style = staticCompositionLocalOf { false }
