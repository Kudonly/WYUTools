package com.wyutools.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.wyutools.app.ui.LocalWyuCustomWallpaper
import com.wyutools.app.ui.LocalWyuDarkTheme
import com.wyutools.app.ui.LocalWyuWallpaper
import com.wyutools.app.ui.WyuAppearance
import com.wyutools.app.ui.WyuThemeMode
import com.wyutools.app.ui.WyuWallpaper
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils

@Composable
fun WyuTheme(
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    remember(context) { WyuAppearance.ensureLoaded(appContext) }
    val textScale by WyuAppearance.textScale.collectAsState()
    val themeMode by WyuAppearance.themeMode.collectAsState()
    val darkTheme = when (themeMode) {
        WyuThemeMode.System -> isSystemInDarkTheme()
        WyuThemeMode.Light -> false
        WyuThemeMode.Dark -> true
    }
    val wallpaper by WyuAppearance.wallpaper.collectAsState()
    val customWallpaper by WyuAppearance.customWallpaper.collectAsState()
    val hasCustomWallpaper by WyuAppearance.hasCustomWallpaper.collectAsState()
    val monetSeed by WyuAppearance.monetSeed.collectAsState()
    val paletteScheme by WyuAppearance.paletteScheme.collectAsState()
    // 字号只改字体缩放，不动 dp：布局尺寸保持不变，只有文字跟着放大／缩小。
    // 编译期的系统值仍然是基准，跟随系统（0）时原样透传。
    val systemDensity = LocalDensity.current
    val density = if (textScale <= 0f) {
        systemDensity
    } else {
        Density(density = systemDensity.density, fontScale = textScale)
    }
    LaunchedEffect(hasCustomWallpaper) {
        if (hasCustomWallpaper) WyuAppearance.loadCustomWallpaper(appContext)
    }
    val controller = remember(darkTheme, wallpaper, monetSeed, paletteScheme) {
        // 取色方案对所有卡片主题生效：自定义壁纸取照片种子，系统壁纸取渐变主色。
        val seed = if (wallpaper == WyuWallpaper.Custom) {
            monetSeed
        } else {
            wallpaper.seedColor(darkTheme)?.toArgb()
        }
        if (seed != null) {
            // 2025 规范只重定义了 均衡 / 鲜艳 / 表达 / 柔和；选到仍然沿用 2021
            // 映射的方案（单色）时，colorSpec 必须跟着降级，否则颜色会被裁成默认。
            val palette = paletteScheme
            ThemeController(
                colorSchemeMode = ColorSchemeMode.MonetSystem,
                keyColor = Color(seed),
                colorSpec = palette.spec,
                paletteStyle = palette.style(darkTheme),
                isDark = darkTheme,
            )
        } else {
            ThemeController(
                colorSchemeMode = ColorSchemeMode.System,
                isDark = darkTheme,
            )
        }
    }
    CompositionLocalProvider(
        LocalDensity provides density,
        LocalWyuDarkTheme provides darkTheme,
        LocalWyuWallpaper provides wallpaper,
        LocalWyuCustomWallpaper provides customWallpaper,
    ) {
        MiuixTheme(controller = controller) {
            // Miuix dialogs only work inside its Scaffold: it provides the dialog
            // state and renders the popup host our custom scaffold omits.
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                popupHost = { MiuixPopupUtils.MiuixPopupHost() },
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
            ) { _ ->
                content()
            }
        }
    }
}
