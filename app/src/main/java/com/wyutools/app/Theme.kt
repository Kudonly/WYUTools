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
import androidx.compose.ui.platform.LocalContext
import com.wyutools.app.ui.LocalWyuCustomWallpaper
import com.wyutools.app.ui.LocalWyuWallpaper
import com.wyutools.app.ui.WyuAppearance
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils

@Composable
fun WyuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    remember(context) { WyuAppearance.ensureLoaded(appContext) }
    val wallpaper by WyuAppearance.wallpaper.collectAsState()
    val customWallpaper by WyuAppearance.customWallpaper.collectAsState()
    val hasCustomWallpaper by WyuAppearance.hasCustomWallpaper.collectAsState()
    LaunchedEffect(hasCustomWallpaper) {
        if (hasCustomWallpaper) WyuAppearance.loadCustomWallpaper(appContext)
    }
    CompositionLocalProvider(
        LocalWyuWallpaper provides wallpaper,
        LocalWyuCustomWallpaper provides customWallpaper,
    ) {
        MiuixTheme(colors = if (darkTheme) darkColorScheme() else lightColorScheme()) {
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
