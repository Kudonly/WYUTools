package com.wyutools.app.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val SplashFadeMs = 320
private const val SplashMinVisibleMs = 200L
private const val SplashMaxWaitMs = 1600L

// Must equal the platform theme's windowSplashScreenBackground (window_bg and
// its night counterpart); the system starting window hands off to this mask.
private val SplashBackgroundLight = Color(0xFFFAFDFB)
private val SplashBackgroundDark = Color(0xFF191C1B)

/**
 * AOSP-style launch mask drawn over the already-composing app. The platform
 * splash (API 31+) shows the icon; this mask only continues its background
 * and fades out once the custom wallpaper is decoded (or a hard cap elapses).
 */
@Composable
fun WyuLaunchSplash() {
    val dark = LocalWyuDarkTheme.current
    val background = if (dark) SplashBackgroundDark else SplashBackgroundLight
    val primary = MiuixTheme.colorScheme.primary
    val wallpaper by WyuAppearance.wallpaper.collectAsState()
    val wallpaperResolved by WyuAppearance.customWallpaperResolved.collectAsState()
    var visible by remember { mutableStateOf(true) }
    val shownAt = remember { SystemClock.uptimeMillis() }
    val ready = wallpaper != WyuWallpaper.Custom || wallpaperResolved
    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        val elapsed = SystemClock.uptimeMillis() - shownAt
        delay((SplashMinVisibleMs - elapsed).coerceAtLeast(0L))
        visible = false
    }
    LaunchedEffect(Unit) {
        delay(SplashMaxWaitMs)
        visible = false
    }
    AnimatedVisibility(
        visible = visible,
        enter = EnterTransition.None,
        exit = fadeOut(tween(SplashFadeMs, easing = FastOutSlowInEasing)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            background,
                            lerp(background, primary, if (dark) 0.10f else 0.06f),
                        ),
                    ),
                )
                // Swallow taps until the mask is gone.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {},
        )
    }
}
