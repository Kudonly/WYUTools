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

    /** Material 3 Expressive: filled tonal cards, big corners, pill buttons. */
    Monet(
        "莫奈",
        "M3E 2025 取色 · 大圆角实色卡片与胶囊按钮，不叠玻璃，取色方案可选",
    ),
}

/**
 * True while [WyuCardTheme.Monet] is active. Published once at the theme root so
 * every card and button reads a composition local instead of collecting the
 * preference flow on its own.
 */
internal val LocalWyuMd3Style = staticCompositionLocalOf { false }
