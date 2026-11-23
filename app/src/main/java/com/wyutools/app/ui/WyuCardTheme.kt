package com.wyutools.app.ui

/**
 * The surface language for cards and buttons. Only one can be active at a time,
 * so the settings screen offers them as a single choice instead of stacking
 * independent switches.
 */
enum class WyuCardTheme(val label: String, val summary: String) {
    /** Stock miuix surfaces with the frosted wallpaper wash. */
    Default("默认", "磨砂玻璃卡片，透出壁纸底色"),

    /** The liquid-glass material: refraction, reflection and edge highlight. */
    SoftGlass("柔光玻璃", "miuix-glass 材质：折射、反射与边缘高光"),

    /** main 分支的本地柔光玻璃 shader，参数与柔光玻璃完全独立。 */
    Liquid("液态玻璃", "本地 shader 材质：折射、反射与边缘高光"),
}
