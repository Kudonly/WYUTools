package com.wyutools.app

import android.os.Build
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
import androidx.compose.ui.platform.LocalContext
import com.wyutools.app.ui.LocalWyuCustomWallpaper
import com.wyutools.app.ui.LocalWyuDarkTheme
import com.wyutools.app.ui.LocalWyuWallpaper
import com.wyutools.app.ui.WyuAppearance
import com.wyutools.app.ui.WyuThemeMode
import com.wyutools.app.ui.WyuWallpaper
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle
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
    LaunchedEffect(hasCustomWallpaper) {
        if (hasCustomWallpaper) WyuAppearance.loadCustomWallpaper(appContext)
    }
    val controller = remember(darkTheme, wallpaper, monetSeed) {
        val seed = monetSeed?.takeIf { wallpaper == WyuWallpaper.Custom }
        if (seed != null) {
            ThemeController(
                colorSchemeMode = ColorSchemeMode.MonetSystem,
                keyColor = Color(seed),
                colorSpec = if (Build.VERSION.SDK_INT >= 36) {
                    ThemeColorSpec.Spec2025
                } else {
                    ThemeColorSpec.Spec2021
                },
                paletteStyle = ThemePaletteStyle.TonalSpot,
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
