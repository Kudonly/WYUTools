package com.wyutools.app.ui

import android.graphics.Rect
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.wyutools.app.SharedCardSnapshot
import com.wyutools.app.SharedViewTransition
import com.wyutools.app.auth.AuthViewModel
import com.wyutools.app.schedule.ScheduleViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    authVm: AuthViewModel,
    scheduleVm: ScheduleViewModel,
    onOpenSchedule: (SharedCardSnapshot?) -> Unit,
    onOpenLxt: (SharedCardSnapshot?) -> Unit,
    onOpenElec: (SharedCardSnapshot?) -> Unit,
    onOpenGym: (SharedCardSnapshot?) -> Unit,
    onLogin: () -> Unit,
    onOpenJxgl: () -> Unit,
    onOpenSettings: (SharedCardSnapshot?) -> Unit,
) {
    val pager = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val toolsScroll = rememberScrollState()
    val meScroll = rememberScrollState()
    val toolsFraction = rememberWyuCollapseFraction(toolsScroll)
    val meFraction = rememberWyuCollapseFraction(meScroll)
    val bottomMargin = rememberWyuNavigatorBottomMargin()
    val page = pager.currentPage
    val activeFraction = rememberWyuPagerCollapseFraction(
        pagerState = pager,
        firstFraction = toolsFraction,
        secondFraction = meFraction,
    )
    val contentBottom = bottomMargin + WyuNavigatorBarHeight + 24.dp
    val dark = LocalWyuDarkTheme.current
    val surface = MiuixTheme.colorScheme.surface
    val primary = MiuixTheme.colorScheme.primary
    val wallpaper = LocalWyuWallpaper.current
    val scheduleUi by scheduleVm.ui.collectAsState()
    val authUi by authVm.ui.collectAsState()
    val now = rememberCurrentMinute()
    val scheduleData = scheduleUi.data
    val scheduleFocus = scheduleData?.focusCourse(scheduleUi.weekAnchor, now)
    val scheduleWeek = scheduleData
        ?.currentWeek(scheduleUi.weekAnchor, now.toLocalDate())
        ?: 1
    // 首页卡片依赖课表缓存，首次启动或缓存失效时静默补一次数据，
    // 避免用户未进课表页前摘要误显示“没有课哦”。
    LaunchedEffect(authUi.restoring, authUi.loggedIn) {
        if (!authUi.restoring && authUi.loggedIn) scheduleVm.onAuthReady()
    }
    val backgroundBrush = remember(wallpaper, dark, surface, primary) {
        if (wallpaper == WyuWallpaper.Custom) {
            null
        } else {
            wallpaper.background(dark, surface) ?: wyuHomeBackground(dark, surface, primary)
        }
    }

    Box(Modifier.fillMaxSize()) {
        WyuPageScaffold(
            progress = { activeFraction.value },
            maskCollapseHeight = WyuLargeTitleHeight,
            backgroundBrush = backgroundBrush,
            overlay = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    WyuNavigatorBar(
                        selectedIndex = page,
                        selectionFraction = {
                            pager.currentPage + pager.currentPageOffsetFraction
                        },
                        onSelect = { index ->
                            if (index != pager.currentPage) {
                                scope.launch {
                                    pager.animateScrollToPage(
                                        page = index,
                                        animationSpec = tween(300, easing = WyuTabEasing),
                                    )
                                }
                            }
                        },
                    )
                }
            },
            topBar = {
                WyuActionBar(
                    title = "工具",
                    altTitle = "我",
                    altFraction = {
                        (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
                    },
                    collapsedFraction = { activeFraction.value },
                    floatingAction = {
                        if (page == 1) {
                            // Fades and deblurs in as the 我 page slides over.
                            Box(
                                modifier = Modifier.graphicsLayer {
                                    val f = pager.currentPage + pager.currentPageOffsetFraction
                                    val t = ((f - 0.5f) * 2f).coerceIn(0f, 1f)
                                    alpha = t
                                    if (t < 0.99f) {
                                        val radius = (1f - t) * 12.dp.toPx()
                                        renderEffect = BlurEffect(radius, radius)
                                    } else {
                                        renderEffect = null
                                    }
                                },
                            ) {
                                SettingsEntry(onOpen = onOpenSettings)
                            }
                        }
                    },
                )
            },
        ) { padding ->
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
            ) { index ->
                if (index == 0) {
                    ToolsContent(
                        scrollState = toolsScroll,
                        topInset = padding.calculateTopPadding(),
                        bottomInset = contentBottom,
                        scheduleFocus = scheduleFocus,
                        scheduleWeek = scheduleWeek,
                        onOpenSchedule = onOpenSchedule,
                        onOpenLxt = onOpenLxt,
                        onOpenElec = onOpenElec,
                        onOpenGym = onOpenGym,
                    )
                } else {
                    MeContent(
                        vm = authVm,
                        onLogin = onLogin,
                        onOpenJxgl = onOpenJxgl,
                        scrollState = meScroll,
                        topInset = padding.calculateTopPadding(),
                        bottomInset = contentBottom,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsEntry(onOpen: (SharedCardSnapshot?) -> Unit) {
    val graphicsLayer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val density = LocalDensity.current
    val radiusPx = with(density) { 24.dp.toPx() }
    val bounds = remember { Rect() }
    var launching by remember { mutableStateOf(false) }
    val openSettings: () -> Unit = {
        if (bounds.isEmpty || launching) {
            if (!launching) onOpen(null)
        } else {
            launching = true
            scope.launch {
                WyuMorphPageState.awaitReturnSettled()
                val bitmap = runCatching {
                    graphicsLayer.toImageBitmap().asAndroidBitmap()
                }.getOrNull()
                launching = false
                onOpen(
                    bitmap?.let {
                        SharedCardSnapshot(
                            bitmap = it,
                            bounds = Rect(bounds),
                            radiusPx = radiusPx,
                        )
                    },
                )
            }
        }
    }
    Box(
        modifier = Modifier
            .onGloballyPositioned { coordinates ->
                val local = coordinates.boundsInWindow()
                val origin = if (SharedViewTransition.boundsUseWindowCoordinate) {
                    null
                } else {
                    IntArray(2).also(view::getLocationOnScreen)
                }
                bounds.set(
                    local.left.roundToInt() + (origin?.get(0) ?: 0),
                    local.top.roundToInt() + (origin?.get(1) ?: 0),
                    local.right.roundToInt() + (origin?.get(0) ?: 0),
                    local.bottom.roundToInt() + (origin?.get(1) ?: 0),
                )
            }
            .drawWithContent {
                graphicsLayer.record {
                    this@drawWithContent.drawContent()
                }
                drawLayer(graphicsLayer)
            },
    ) {
        WyuTopBarAction(
            icon = Icons.Outlined.Settings,
            label = "设置",
            onClick = openSettings,
        )
    }
}
