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
import com.wyutools.app.ui.LocalWyuCustomWallpaper
import com.wyutools.app.ui.LocalWyuDarkTheme
import com.wyutools.app.ui.LocalWyuMd3Style
import com.wyutools.app.ui.LocalWyuWallpaper
import com.wyutools.app.ui.WyuAppearance
import com.wyutools.app.ui.WyuCardTheme
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
    val cardTheme by WyuAppearance.cardTheme.collectAsState()
    val paletteScheme by WyuAppearance.paletteScheme.collectAsState()
    val md3 = cardTheme == WyuCardTheme.Monet
    LaunchedEffect(hasCustomWallpaper) {
        if (hasCustomWallpaper) WyuAppearance.loadCustomWallpaper(appContext)
    }
    val controller = remember(darkTheme, wallpaper, monetSeed, md3, paletteScheme) {
        // 默认/柔光玻璃 only take a custom photo's seed; 莫奈 always derives one,
        // falling back to the gradient's middle stop for stock wallpapers.
        val seed = if (md3) {
            if (wallpaper == WyuWallpaper.Custom) {
                monetSeed
            } else {
                wallpaper.seedColor(darkTheme)?.toArgb()
            }
        } else {
            monetSeed?.takeIf { wallpaper == WyuWallpaper.Custom }
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
        LocalWyuDarkTheme provides darkTheme,
        LocalWyuWallpaper provides wallpaper,
        LocalWyuCustomWallpaper provides customWallpaper,
        LocalWyuMd3Style provides md3,
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
