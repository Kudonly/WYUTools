package com.wyutools.app.ui

/**
 * The surface language for cards and buttons. Only one can be active at a time,
 * so the settings screen offers them as a single choice instead of stacking
 * independent switches.
 */
enum class WyuCardTheme(val label: String, val summary: String) {
    /** Stock miuix surfaces with the frosted wallpaper wash. */
    Default("默认", "磨砂玻璃性能占用最低"),

    /** main 分支的本地柔光玻璃 shader，参数与柔光玻璃完全独立。 */
    Liquid("液态玻璃", "液态玻璃性能占用适中"),

    /** The upstream miuix-glass material: refraction, reflection and edge highlight. */
    SoftGlass("柔光玻璃", "柔光玻璃beta性能占用最高"),
}
