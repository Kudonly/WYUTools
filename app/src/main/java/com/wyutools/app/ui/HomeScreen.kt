package com.wyutools.app.ui

import android.graphics.Rect
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DashboardCustomize
import androidx.compose.material.icons.outlined.Edit
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wyutools.app.SharedCardSnapshot
import com.wyutools.app.SharedViewTransition
import com.wyutools.app.auth.AuthViewModel
import com.wyutools.app.schedule.ScheduleViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.pow
import kotlin.math.roundToInt

private val HomeTabs = listOf("常用", "更多", "我")

@Composable
fun HomeScreen(
    authVm: AuthViewModel,
    scheduleVm: ScheduleViewModel,
    onOpenCard: (WyuTool, SharedCardSnapshot?) -> Unit,
    onOpenIcon: (WyuTool, SharedCardSnapshot?) -> Unit,
    onLogin: () -> Unit,
    onOpenJxgl: () -> Unit,
    onOpenSettings: (SharedCardSnapshot?) -> Unit,
    onOpenCommon: (SharedCardSnapshot?) -> Unit,
) {
    val pager = rememberPagerState(pageCount = { HomeTabs.size })
    val scope = rememberCoroutineScope()
    val toolsScroll = rememberScrollState()
    val moreGrid = rememberLazyGridState()
    val meScroll = rememberScrollState()
    val toolsFraction = rememberWyuCollapseFraction(toolsScroll)
    val moreFraction = rememberWyuCollapseFraction(moreGrid)
    val meFraction = rememberWyuCollapseFraction(meScroll)
    val bottomMargin = rememberWyuNavigatorBottomMargin()
    val page = pager.currentPage
    val activeFraction = rememberWyuMultiPageCollapseFraction(
        pagerState = pager,
        fractions = listOf(toolsFraction, moreFraction, meFraction),
    )
    val contentBottom = bottomMargin + WyuNavigatorBarHeight + 24.dp
    val dark = LocalWyuDarkTheme.current
    val surface = MiuixTheme.colorScheme.surface
    val primary = MiuixTheme.colorScheme.primary
    val wallpaper = LocalWyuWallpaper.current
    val scheduleUi by scheduleVm.ui.collectAsState()
    val authUi by authVm.ui.collectAsState()
    val layout by WyuToolLayoutStore.state.collectAsState()
    var moreEditing by remember { mutableStateOf(false) }
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
    // Leaving the tab ends edit mode, the way iOS drops the jitter when you swipe away.
    LaunchedEffect(page) {
        if (page != 1) moreEditing = false
    }
    // A tab switch may start while a return morph is still shrinking its window
    // back into a card. The return covers the home with a still capture, so the
    // switch would be played behind it and only its end state would show. Publish
    // the motion so that window can hand the screen back to this live page.
    LaunchedEffect(pager.isScrollInProgress) {
        WyuMorphPageState.homePagerMoving = pager.isScrollInProgress
    }
    val backgroundBrush = remember(wallpaper, dark, surface, primary) {
        if (wallpaper == WyuWallpaper.Custom) {
            null
        } else {
            wallpaper.background(dark, surface) ?: wyuHomeBackground(dark, surface, primary)
        }
    }
    val pagePosition: () -> Float = {
        (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, 2f)
    }
    // The switching title crossfades two labels, so feed it the current pair.
    val segment = if (pagePosition() < 1f) 0 else 1

    // No page-switch debounce: every tap starts a fresh animateScrollToPage.
    // PagerState serialises scroll requests through its own mutate mutex, so
    // a faster second tap simply cancels the first tween and retargets it --
    // exactly what the user wants from a rapid double tap.
    fun requestTab(index: Int) {
        // Same frame as the tap: the return morph must let go of the screen
        // before the tween's first frame, not one frame later.
        WyuMorphPageState.homePagerMoving = true
        scope.launch {
            pager.animateScrollToPage(
                page = index,
                animationSpec = tween(300, easing = WyuTabEasing),
            )
            // Tapping the tab that is already selected starts no scroll, so the
            // flag has to be cleared here as well as from isScrollInProgress.
            if (!pager.isScrollInProgress) WyuMorphPageState.homePagerMoving = false
        }
    }
    // No lifecycle guard here. The tabs never navigate — they only scroll the
    // pager, which serialises the requests itself — so the only thing
    // dropUnlessResumed could do is drop a real tap. That is exactly what
    // happened right after BACK interrupted an opening: the pop leaves home at
    // STARTED for a frame, the press landed inside it, and the switch was
    // swallowed. A tab tap must always move the pager.
    val tabActions = listOf(
        { requestTab(0) },
        { requestTab(1) },
        { requestTab(2) },
    )
    val selectTab: (Int) -> Unit = { index -> tabActions.getOrNull(index)?.invoke() }
    Box(Modifier.fillMaxSize()) {
        WyuPageScaffold(
            progress = { activeFraction.value },
            maskCollapseHeight = WyuLargeTitleHeight,
            backgroundBrush = backgroundBrush,
            overlay = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    WyuNavigatorBar(
                        selectedIndex = page,
                        selectionFraction = { pagePosition() },
                        onSelect = selectTab,
                    )
                }
            },
            topBar = {
                WyuActionBar(
                    title = HomeTabs[segment],
                    altTitle = HomeTabs[segment + 1],
                    altFraction = { (pagePosition() - segment).coerceIn(0f, 1f) },
                    collapsedFraction = { activeFraction.value },
                    floatingAction = {
                        when (page) {
                            0 -> TopBarActionDefocus(
                                // 1 at rest, 0 by the halfway point of the swipe —
                                // which is exactly where the pager swaps this
                                // button for 更多's, so the fade is the whole
                                // hand-over. Written as `1 - 2p`: the previous
                                // `(1 - p) * 2` saturated at 1 until p = 0.5 and
                                // then dropped to 0 in the same frame the button
                                // was replaced, so this one never faded — and a
                                // button that never fades never blurs either.
                                alpha = (1f - pagePosition() * 2f).coerceIn(0f, 1f),
                            ) {
                                // Card picker: it sits on 常用, next to the cards
                                // it controls, and opens as a morph out of itself.
                                val picker = rememberMorphLaunch(
                                    radiusDp = TopBarButtonRadius,
                                    route = ROUTE_COMMON,
                                    onOpen = onOpenCommon,
                                )
                                WyuTopBarIconButton(
                                    icon = Icons.Outlined.DashboardCustomize,
                                    contentDescription = "常用卡片显示",
                                    onClick = picker.launch,
                                    modifier = picker.modifier,
                                )
                            }

                            1 -> TopBarActionDefocus(
                                alpha = (1f - kotlin.math.abs(pagePosition() - 1f))
                                    .coerceIn(0f, 1f),
                            ) {
                                WyuTopBarIconButton(
                                    icon = if (moreEditing) {
                                        Icons.Outlined.Check
                                    } else {
                                        Icons.Outlined.Edit
                                    },
                                    contentDescription = if (moreEditing) "完成" else "编辑",
                                    onClick = { moreEditing = !moreEditing },
                                )
                            }

                            2 -> TopBarActionDefocus(
                                alpha = ((pagePosition() - 1.5f) * 2f).coerceIn(0f, 1f),
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
                when (index) {
                    0 -> ToolsContent(
                        scrollState = toolsScroll,
                        topInset = padding.calculateTopPadding(),
                        bottomInset = contentBottom,
                        tools = layout.commonTools,
                        scheduleFocus = scheduleFocus,
                        scheduleWeek = scheduleWeek,
                        onOpen = onOpenCard,
                    )

                    1 -> MoreContent(
                        gridState = moreGrid,
                        topInset = padding.calculateTopPadding(),
                        bottomInset = contentBottom,
                        editing = moreEditing,
                        tools = layout.moreTools,
                        onMove = WyuToolLayoutStore::moveInMore,
                        onOpen = onOpenIcon,
                        onEnterEdit = { moreEditing = true },
                    )

                    else -> MeContent(
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
    val entry = rememberMorphLaunch(
        radiusDp = 24.dp,
        route = SharedViewTransition.ROUTE_SETTINGS,
        onOpen = onOpen,
    )
    Box(entry.modifier) {
        WyuTopBarAction(
            icon = Icons.Outlined.Settings,
            label = "设置",
            onClick = entry.launch,
        )
    }
}

/** The glass disc's corner radius, shared by every top bar morph trigger. */
private val TopBarButtonRadius = 24.dp

/**
 * Defocus a top bar action is allowed on its way out, matching the page title's
 * own hand-over ([WyuSwitchingTitle]) so the whole bar dissolves as one.
 */
private val TopBarActionBlurDp = 10.dp

/**
 * Room the defocus gets around the control.
 *
 * A `renderEffect` only ever sees the layer it is attached to, and a layer the
 * exact size of the control has nowhere for the blur to fall off into: the disc
 * keeps a crisp rim however large the radius grows, which is why these buttons
 * used to read as plain alpha fades next to the title even though they carried
 * the same radius. This padding is that room. It is paid back by the offset, so
 * the control does not move — only the layer around it grows.
 */
private val TopBarActionBlurSlack = 12.dp

/**
 * The whole leaving/arriving state of a top bar action: transparency and defocus
 * driven by one number, exactly like the page title beside it.
 *
 * [alpha] is the control's own visibility (1 = at rest on this page). The fade
 * runs ahead of the blur on purpose: a control that only blurs as fast as it
 * fades is already almost transparent by the time the radius is worth looking
 * at, so the effect reads as a plain alpha dip — which is exactly how a button
 * "without blur" looks. Here the radius is at full strength while the control is
 * still nearly half visible, so the eye sees it *go out of focus* first and
 * disappear second.
 */
@Composable
private fun TopBarActionDefocus(
    alpha: Float,
    content: @Composable () -> Unit,
) {
    val a = alpha.coerceIn(0f, 1f)
    // Sub-linear on the way down (exponent < 1): the radius is already most of
    // the way there at half visibility, so the blur is seen rather than implied.
    val radius = TopBarActionBlurDp * (1f - a).pow(0.55f)
    Box(
        modifier = Modifier
            // Order matters. The offset pays the padding back so the control does
            // not move; the layer sits between them, so it is the *padded* node —
            // the control plus its slack — that gets blurred, not the control's
            // own tight bounds.
            .offset(x = TopBarActionBlurSlack, y = -TopBarActionBlurSlack)
            .graphicsLayer {
                this.alpha = a
                renderEffect = if (radius > 0.05.dp) {
                    val px = radius.toPx()
                    BlurEffect(px, px)
                } else {
                    null
                }
            }
            .padding(TopBarActionBlurSlack),
    ) {
        content()
    }
}

/**
 * How far inside the 48dp touch target the morph's window starts.
 *
 * The control it flies out of is a 44dp disc, not the whole target: starting on
 * the target's edge puts the disc's own rim — the 0.5dp border and the shadow it
 * carries — on the window's outline, where the mask magnifies it into a visible
 * burr. Starting two and a half dp in lands the mask on the disc's surface
 * instead, so the outline is clean glass and the rim is simply never captured.
 */
private val TopBarMorphInset = 2.5.dp

private class MorphLaunch(val modifier: Modifier, val launch: () -> Unit)

/**
 * A top bar control that hands its own pixels to the shared-element morph, so
 * the page grows out of the button that was tapped instead of sliding in.
 * [radiusDp] is the button's corner radius, which the growing window inherits.
 * [route] is the page it opens: while that route is in flight the button draws
 * nothing in its slot, exactly as a card or an icon does — the window is the
 * copy that travels, and two copies of the same glass disc were visible side by
 * side for the first frames of the flight.
 */
@Composable
private fun rememberMorphLaunch(
    radiusDp: Dp,
    route: String?,
    onOpen: (SharedCardSnapshot?) -> Unit,
): MorphLaunch {
    val graphicsLayer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val density = LocalDensity.current
    val radiusPx = with(density) { radiusDp.toPx() }
    val insetPx = with(density) { TopBarMorphInset.toPx() }.roundToInt()
    val bounds = remember { Rect() }
    var launching by remember { mutableStateOf(false) }
    val launch: () -> Unit = {
        // A press outranks the animation: stop a running return immediately,
        // even when this button consumed its own DOWN before the global hook.
        WyuMorphPageState.interruptMorph()
        // Before the first layout there are no pixels to fly, so fall back to a
        // plain navigation rather than freezing a blank window.
        if (bounds.isEmpty || launching) {
            if (!launching) onOpen(null)
        } else {
            launching = true
            scope.launch {
                // The press has already settled the page; capture right away
                // instead of waiting for the previous morph to finish.
                val bitmap = runCatching {
                    graphicsLayer.toImageBitmap().asAndroidBitmap()
                }.getOrNull()?.let { cropMorphInset(it, insetPx) }
                launching = false
                onOpen(
                    bitmap?.let {
                        // The window starts on the disc's surface, not on the
                        // touch target's edge — see [TopBarMorphInset]. Image and
                        // bounds are inset by the same amount, so the capture
                        // still maps one to one and nothing is rescaled.
                        SharedCardSnapshot(
                            bitmap = it,
                            bounds = Rect(
                                bounds.left + insetPx,
                                bounds.top + insetPx,
                                bounds.right - insetPx,
                                bounds.bottom - insetPx,
                            ),
                            radiusPx = radiusPx,
                        )
                    },
                )
            }
        }
    }
    val modifier = Modifier
        // The disc that is out draws nothing in its slot. The capture above has
        // already been taken, so hiding it here cannot blank the flying copy —
        // and it keeps the *page* capture clean too, since the window is the
        // only copy that should be on screen from the first frame on.
        .graphicsLayer {
            alpha = if (route != null && WyuMorphPageState.flyingRoute == route) 0f else 1f
        }
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
        }
    return MorphLaunch(modifier, launch)
}

/**
 * Drops the outer [insetPx] of a captured control. The rim of the glass disc
 * lives exactly there, and the morph would otherwise stretch it into a burr
 * around the growing window's edge.
 */
private fun cropMorphInset(bitmap: android.graphics.Bitmap, insetPx: Int): android.graphics.Bitmap {
    if (insetPx <= 0) return bitmap
    if (bitmap.width <= insetPx * 2 + 1 || bitmap.height <= insetPx * 2 + 1) return bitmap
    return runCatching {
        android.graphics.Bitmap.createBitmap(
            bitmap,
            insetPx,
            insetPx,
            bitmap.width - insetPx * 2,
            bitmap.height - insetPx * 2,
        )
    }.getOrDefault(bitmap)
}
