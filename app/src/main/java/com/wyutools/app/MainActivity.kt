package com.wyutools.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wyutools.app.ui.AuthLoginScreen
import com.wyutools.app.ui.CardMorphOverlay
import com.wyutools.app.ui.MorphBackdropDriver
import com.wyutools.app.ui.CommonCardsScreen
import com.wyutools.app.ui.CardReturnOverlay
import com.wyutools.app.ui.ElecScreen
import com.wyutools.app.ui.EvalScreen
import com.wyutools.app.ui.ExamScreen
import com.wyutools.app.ui.GymScreen
import com.wyutools.app.ui.HomeScreen
import com.wyutools.app.ui.GpaScreen
import com.wyutools.app.ui.GradesScreen
import com.wyutools.app.ui.JxglWebScreen
import com.wyutools.app.ui.PhysicalScreen
import com.wyutools.app.ui.LocalWyuDarkTheme
import com.wyutools.app.ui.ROUTE_COMMON
import com.wyutools.app.ui.ROUTE_EVAL
import com.wyutools.app.ui.ROUTE_EXAM
import com.wyutools.app.ui.ROUTE_GPA
import com.wyutools.app.ui.ROUTE_GRADES
import com.wyutools.app.ui.ROUTE_PHYSICAL
import com.wyutools.app.ui.LxtBindScreen
import com.wyutools.app.ui.LxtScreen
import com.wyutools.app.ui.ScheduleScreen
import com.wyutools.app.ui.SettingsScreen
import com.wyutools.app.ui.SoftGlassAdvancedScreen
import com.wyutools.app.ui.WyuMorphPageState
import com.wyutools.app.ui.WyuMorphStyle
import com.wyutools.app.ui.WyuNavEasing
import com.wyutools.app.ui.WyuNavEnterMillis
import com.wyutools.app.ui.WyuNavExitMillis
import com.wyutools.app.ui.WyuNavParentScale
import com.wyutools.app.ui.WyuNavParentShiftFraction
import com.wyutools.app.ui.WyuPageCoveredScrim
import com.wyutools.app.ui.WyuPageDimInMillis
import com.wyutools.app.ui.WyuPageDimOutMillis
import com.wyutools.app.ui.WyuPageEffects
import com.wyutools.app.ui.WyuLaunchSplash
import com.wyutools.app.ui.WyuWindowBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt
import kotlin.coroutines.resume

/** The window that is opening right now is the absolute top layer: no parallel
 *  close and no page-level element may paint over it. A returning window sits
 *  clearly below, so a quick card switch always shows the new window rather
 *  than a leftover from the previous one. */
private const val MorphOpenZ = 100f
private const val MorphReturnZ = 10f

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initialRoute = SharedViewTransition.featureRouteOrNull(
            intent.getStringExtra(SharedViewTransition.EXTRA_START_ROUTE),
        ) ?: "home"
        setContent {
            WyuTheme {
                Box(Modifier.fillMaxSize()) {
                    val dark = LocalWyuDarkTheme.current
                    LaunchedEffect(dark) {
                        // Android 15+ draws its own 90% white contrast scrim behind
                        // the 3-button nav bar for auto styles. An explicit light
                        // style registers transparent protections instead and turns
                        // the system contrast off; icons are themed manually.
                        enableEdgeToEdge(
                            statusBarStyle = SystemBarStyle.auto(
                                android.graphics.Color.TRANSPARENT,
                                android.graphics.Color.TRANSPARENT,
                            ) { dark },
                            navigationBarStyle = SystemBarStyle.light(
                                android.graphics.Color.TRANSPARENT,
                                android.graphics.Color.TRANSPARENT,
                            ),
                        )
                        WindowCompat.getInsetsController(window, window.decorView).apply {
                            isAppearanceLightStatusBars = !dark
                            isAppearanceLightNavigationBars = !dark
                        }
                    }
                    WyuApp(
                        initialRoute = initialRoute,
                        onExit = { finish() },
                    )
                    // The page composes and the wallpaper decodes in parallel;
                    // this mask covers the wait and fades out per AOSP.
                    WyuLaunchSplash()
                }
            }
        }
    }
}

@Composable
fun WyuApp(
    initialRoute: String = "home",
    onExit: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as WyuApplication
    val activity = context.findActivity()
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val currentRoute = route ?: initialRoute
    // A page that is no longer in the back stack is playing its exit
    // transition; a page still in it but not on top is covered by a pushed
    // page. WyuPageEffects renders the two cases differently.
    val stackEntries by nav.currentBackStack.collectAsState()
    val stackRoutes = stackEntries.mapNotNull { it.destination.route }
    // A destination revealed by a pop composes anew underneath the leaving
    // sheet; it must appear square instead of replaying the pushed-sheet
    // corners, so pages revealed by a shrunk back stack start sharp.
    val navCount = remember { intArrayOf(stackRoutes.size) }
    val pageRevealSharp = stackRoutes.size < navCount[0]
    navCount[0] = stackRoutes.size
    // Backdrop scrim for the strips a receded page bares around itself: the
    // page's own scrim is clipped to its shape, so without this the wallpaper
    // edges stay bright while the page under a push dims.
    val childPageOnTop = stackRoutes.size > 1
    val backdropDim = remember { Animatable(0f) }
    LaunchedEffect(childPageOnTop) {
        backdropDim.animateTo(
            targetValue = if (childPageOnTop) WyuPageCoveredScrim else 0f,
            animationSpec = tween(
                durationMillis = if (childPageOnTop) WyuPageDimInMillis else WyuPageDimOutMillis,
                easing = WyuNavEasing,
            ),
        )
    }
    val authVm = app.authViewModel
    // Pages that can be opened by a card or an icon on home and must return to
    // it with the same morph. 考试 is not part of the system shared-view set
    // (it is never launched from a shortcut), but it still opens in-app.
    val morphRoutes = remember {
        SharedViewTransition.featureRoutes + ROUTE_EXAM + ROUTE_COMMON +
            ROUTE_GRADES + ROUTE_GPA + ROUTE_EVAL + ROUTE_PHYSICAL
    }

    val cardSnapshots = remember { mutableMapOf<String, SharedCardSnapshot>() }
    // Which pipeline each open window belongs to, remembered per route so the
    // return uses exactly the same one. Cards and tiles are separate on purpose:
    // retuning the tile morph must leave the 常用卡片 alone.
    val morphStyles = remember { mutableMapOf<String, WyuMorphStyle>() }
    // Home split into the two layers the morph moves apart: the wallpaper and
    // the content on top of it. Captured once per open and reused by the return.
    val homeLayers = remember { mutableMapOf<String, SharedHomeLayers>() }
    var cardMorph by remember { mutableStateOf<CardMorph?>(null) }
    var returnMorph by remember { mutableStateOf<CardReturn?>(null) }
    // Windows left behind by a rapid switch while the parallel slot was already
    // taken. Each keeps its own Animatable and plays its close to the end; none
    // of them owns navigation, input or the shared backdrop clocks.
    val retiringLayers = remember { mutableStateListOf<CardReturn>() }
    val returnRunning = remember { mutableStateOf(false) }
    val openPending = remember { mutableStateOf(false) }
    val openCancel = remember { mutableStateOf(false) }
    val returnReopen = remember { mutableStateOf(false) }
    // The page capture that starts a return. A press cancels it so the new
    // morph's own capture is the only one writing the shared capture flags.
    var closeCaptureJob by remember { mutableStateOf<Job?>(null) }
    // The snapshot taken when BACK interrupts an opening. It is cancelled as
    // soon as a new card is tapped, so it can never pop the stack afterwards.
    var cancelCaptureJob by remember { mutableStateOf<Job?>(null) }
    // The in-flight open capture. A second tap supersedes it instead of being
    // dropped while these few frames are being recorded.
    var openJob by remember { mutableStateOf<Job?>(null) }
    val openGen = remember { intArrayOf(0) }
    val scope = rememberCoroutineScope()
    fun settleMorphState() {
        // A retiring window is a parallel visual layer only: it must not claim
        // the title, the source slot or the settled state of the new morph.
        val liveReturn = returnMorph?.takeUnless { it.retiring }
        WyuMorphPageState.titleHidden = cardMorph != null || liveReturn != null
        if (cardMorph == null && liveReturn == null) {
            WyuMorphPageState.contentProgress = 1f
        }
        WyuMorphPageState.morphSettled = cardMorph == null
        // The source slot stays empty for exactly as long as one of its copies
        // is in flight, and is handed back the frame the overlay comes off — the
        // window covers the slot at that instant, so the swap has nothing to show.
        WyuMorphPageState.flyingRoute = cardMorph?.route ?: liveReturn?.route
        // NavHost transitions still treat a parallel close as "no slide".
        returnRunning.value = returnMorph != null
    }
    // The global press hook must outrank opening, cancelling AND returning,
    // not just the exit. Keeping this in sync makes interruptMorph() a no-op
    // when the screen is idle.
    LaunchedEffect(cardMorph, returnMorph, openPending.value, openCancel.value) {
        WyuMorphPageState.morphActive =
            cardMorph != null || returnMorph != null || openPending.value
        // Only a pure opening ignores stray presses. A parallel (retiring)
        // close is irrelevant here — it owns no navigation and no input.
        // As soon as BACK starts the interrupt, the screen must be ready for
        // the very next operation.
        WyuMorphPageState.openingOnly =
            cardMorph != null && !openCancel.value
    }
    // Exit snapshots are recorded from the NavHost layer while the live page
    // hides its title, so no title pixels have to be painted out afterwards.
    val pageLayer = rememberGraphicsLayer()
    var pageCaptureRequested by remember { mutableStateOf(false) }
    val pageCaptureDone = remember { booleanArrayOf(false) }
    // The wallpaper is recorded from the window backdrop: it paints the
    // wallpaper for every page and is not part of the NavHost.
    val wallpaperLayer = rememberGraphicsLayer()
    var wallpaperCaptureRequested by remember { mutableStateOf(false) }
    val wallpaperCaptureDone = remember { booleanArrayOf(false) }
    // Pages first composed during the cold start appear without a slide, so
    // the page-card corners must not play for them. Pages composed underneath
    // a card morph are covered by the overlay and must start square too.
    val coldStart = remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        coldStart.value = false
    }
    val pageStartsSharp = coldStart.value || cardMorph != null || returnMorph != null

    suspend fun recordNavHostPage(): Bitmap? {
        withFrameNanos { }
        pageCaptureRequested = true
        var frames = 0
        // The draw hook normally answers on the next frame. If the page is not
        // repainting at all, waiting the old 100 frames (~1.6s) left a
        // full-screen touch blocker and a disabled BackHandler on screen, which
        // is what made "open the same tool, then return" feel stuck. Give up
        // quickly and let the caller fall back to a plain pop instead.
        while (!pageCaptureDone[0] && frames < 24) {
            withFrameNanos { }
            frames++
        }
        pageCaptureRequested = false
        val recorded = pageCaptureDone[0]
        pageCaptureDone[0] = false
        return if (recorded) {
            runCatching { pageLayer.toImageBitmap().asAndroidBitmap() }.getOrNull()
        } else {
            null
        }
    }

    /**
     * Captures home as the two layers an iOS-style zoom needs, in one frame: the
     * window backdrop records the wallpaper, while the NavHost records the page
     * with its own wallpaper switched off, so the content comes out on a
     * transparent background. The backdrop keeps painting that same wallpaper
     * underneath the suppressed page, so the frame looks unchanged on screen.
     */
    suspend fun recordHomeLayers(): SharedHomeLayers? {
        withFrameNanos { }
        WyuMorphPageState.suppressPageBackground = true
        pageCaptureRequested = true
        wallpaperCaptureRequested = true
        pageCaptureDone[0] = false
        wallpaperCaptureDone[0] = false
        var content: Bitmap? = null
        var wallpaper: Bitmap? = null
        try {
            var frames = 0
            while ((!pageCaptureDone[0] || !wallpaperCaptureDone[0]) && frames < 100) {
                withFrameNanos { }
                frames++
            }
            if (pageCaptureDone[0]) {
                content = runCatching {
                    pageLayer.toImageBitmap().asAndroidBitmap()
                }.getOrNull()
            }
            if (wallpaperCaptureDone[0]) {
                wallpaper = runCatching {
                    wallpaperLayer.toImageBitmap().asAndroidBitmap()
                }.getOrNull()
            }
        } finally {
            // A second tap may cancel this capture mid-frame. Leaving the
            // request flags set would freeze the next capture or strip the
            // wallpaper off the live home page for good.
            WyuMorphPageState.suppressPageBackground = false
            pageCaptureRequested = false
            wallpaperCaptureRequested = false
            pageCaptureDone[0] = false
            wallpaperCaptureDone[0] = false
        }
        if (content == null || wallpaper == null) {
            Log.w("WyuMorph", "home capture incomplete, content=" + (content != null) +
                " wallpaper=" + (wallpaper != null))
            return null
        }
        return SharedHomeLayers(wallpaper = wallpaper, content = content)
    }

    val goBack: () -> Unit = {
        if (!nav.popBackStack()) onExit()
    }
    val closeTool: (String) -> Unit = close@{ toolRoute ->
        // A parallel (retiring) close owns no navigation; it must not swallow
        // an immediate back on the page the user just switched to.
        val busyReturn = returnMorph?.takeUnless { it.retiring }
        if (cardMorph != null) return@close
        if (busyReturn != null) {
            // While the page snapshot is still being recorded no window exists
            // yet: there is only a full-screen touch blocker and a disabled
            // BackHandler. A second BACK is unambiguous — drop the pending
            // morph and leave at once instead of waiting for the capture. The
            // first BACK never lands here (it is what created the return), so
            // this can only ever mean "the user pressed again".
            if (busyReturn.page == null) {
                closeCaptureJob?.cancel()
                closeCaptureJob = null
                returnMorph = null
                returnRunning.value = false
                WyuMorphPageState.returnSettling = false
                settleMorphState()
                goBack()
            }
            return@close
        }
        val snapshot = cardSnapshots[toolRoute]
        if (snapshot != null &&
            nav.previousBackStackEntry?.destination?.route == "home" &&
            activity != null
        ) {
            returnReopen.value = false
            // Freeze the title's current visible geometry (collapsed or expanded)
            // before hiding it, so the return flight leaves from where the title
            // actually sits instead of snapping to the expanded large-title spot.
            val titleStartBounds = WyuMorphPageState.exitTitleBounds
            val titleStartFontPx = WyuMorphPageState.exitTitleFontPx
            val titleStartLinePx = WyuMorphPageState.exitTitleLinePx
            // Hide the live title before recording: the overlay's flying title
            // sits over the same spot from this frame on, so the page snapshot
            // comes out clean and the page itself never visibly loses it. A
            // title-less source (a 更多 icon, a top bar button) has no flight to
            // hand the title to, so its page keeps drawing its own.
            WyuMorphPageState.titleHidden = snapshot.title != null
            returnRunning.value = true
            WyuMorphPageState.returnSettling = true
            // Same rule on the way home: home is about to compose underneath the
            // overlay with its own copy of this card, and the window shrinks past
            // the card's rect before it settles — so the slot has to stay empty
            // until the window has landed on it.
            WyuMorphPageState.flyingRoute = toolRoute
            returnMorph = CardReturn(
                toolRoute,
                snapshot,
                null,
                homeLayers[toolRoute],
                style = morphStyles[toolRoute] ?: WyuMorphStyle.Tile,
                titleStartBounds = titleStartBounds,
                titleStartFontPx = titleStartFontPx,
                titleStartLinePx = titleStartLinePx,
            )
            closeCaptureJob = scope.launch {
                val page = recordNavHostPage()
                if (page != null) {
                    // The overlay paints the window from this snapshot from the
                    // first animation frame on, so the real page can leave the
                    // host right away: home composes and lays out during the
                    // whole return and is interactive the instant the window
                    // lands. Its state is saved so an interrupted return can
                    // re-navigate straight back into the same page.
                    returnMorph = returnMorph?.copy(page = page)
                    if (nav.currentBackStackEntry?.destination?.route == toolRoute) {
                        nav.popBackStack(toolRoute, inclusive = true, saveState = true)
                    }
                } else {
                    returnMorph = null
                    returnRunning.value = false
                    WyuMorphPageState.returnSettling = false
                    WyuMorphPageState.titleHidden = false
                    if (nav.currentBackStackEntry?.destination?.route == toolRoute) {
                        nav.popBackStack()
                    }
                }
            }
        } else {
            goBack()
        }
    }
    /**
     * A press outranks every morph on screen. Nothing is stopped and nothing
     * is waited out: the current window is moved to the parallel-closing slot,
     * where it keeps animating from its exact current value on its own
     * Animatable, while [cardMorph] is freed for the new window that starts in
     * the same frame. Both windows therefore fly at the same time.
     */
    fun preemptForInput(): Boolean {
        // Resets run even when there is no window left to hand over: a
        // superseded capture coroutine may have been cancelled mid-frame.
        closeCaptureJob?.cancel()
        closeCaptureJob = null
        cancelCaptureJob?.cancel()
        cancelCaptureJob = null
        pageCaptureRequested = false
        wallpaperCaptureRequested = false
        pageCaptureDone[0] = false
        wallpaperCaptureDone[0] = false
        WyuMorphPageState.suppressPageBackground = false
        val opening = cardMorph
        val returning = returnMorph
        if (opening == null && returning == null) return false
        var retiring = returning?.copy(retiring = true)
        // No snapshot means nothing was drawn yet; the overlay's own effect
        // detaches such a parallel close immediately.
        if (retiring?.page == null) retiring = null
        if (opening != null) {
            cardMorph = null
            openCancel.value = false
            if (opening.page != null) {
                val abandoned = CardReturn(
                    route = opening.route,
                    snapshot = opening.snapshot,
                    page = opening.page,
                    home = opening.home,
                    style = opening.style,
                    retiring = true,
                    // The open's 0 -> 1 is the close's 1 -> 0: resume from the
                    // very frame on screen instead of snapping back to full.
                    initialFraction =
                        (1f - WyuMorphPageState.morphWindowProgress).coerceIn(0f, 1f),
                )
                if (retiring == null) {
                    retiring = abandoned
                } else {
                    // The parallel slot is already taken by a window that is
                    // still on its way home. Give this one its own layer
                    // instead of dropping it: no window on screen may freeze
                    // mid-move just because a newer one was tapped.
                    retiringLayers.add(abandoned)
                    Log.d("WyuMorph", "abandoned open " + abandoned.route +
                        " frac=" + abandoned.initialFraction + " layers=" + retiringLayers.size)
                    // The leftovers are only ever a few frames of motion; keep
                    // the stack bounded so a manic tap spree cannot pile up.
                    while (retiringLayers.size > 3) retiringLayers.removeAt(0)
                }
            }
            // Home has to be the current destination so the new morph can
            // freeze its layers. This is a stack change, not an animation wait.
            if (nav.currentBackStackEntry?.destination?.route == opening.route) {
                nav.popBackStack(opening.route, inclusive = true, saveState = true)
            }
        }
        // A new morph always starts from home. If a return's page capture was
        // cancelled before it popped, the destination would still be on top;
        // fix the stack now so the home layers are captured from the real home.
        if (nav.currentBackStackEntry?.destination?.route != "home") {
            nav.popBackStack("home", inclusive = false)
        }
        returnReopen.value = false
        returnMorph = retiring
        WyuMorphPageState.contentProgress = 1f
        WyuMorphPageState.returnSettling = false
        // Synchronous, so the very next DOWN is not mistaken for a press on a
        // pure opening (which must ignore everything except BACK).
        WyuMorphPageState.openingOnly = false
        settleMorphState()
        return true
    }

    val openTool: (String, SharedCardSnapshot?, WyuMorphStyle) -> Unit =
        { toolRoute, card, style ->
        // No "cardMorph == null" / "openPending" guard any more: a tap that
        // arrives while a snapshot is being recorded supersedes that job
        // instead of being silently dropped.
        if (!SharedViewTransition.launchTool(activity, toolRoute, card)) {
            if (card != null && activity != null) {
                // Whether a live morph was on screen when this tap arrived.
                // Read *before* cancelling anything, so the new window knows
                // it must not sink and shrink the page a second time.
                val interrupted = cardMorph != null ||
                    returnMorph?.retiring == false || openJob?.isActive == true
                val gen = ++openGen[0]
                openJob?.cancel()
                openPending.value = false
                val preempted = preemptForInput()
                openPending.value = true
                openJob = scope.launch {
                    // Only layout frames when a window was handed over,
                    // never animation frames. The retiring close keeps
                    // playing in parallel while these are captured.
                    if (preempted) {
                        withFrameNanos { }
                        withFrameNanos { }
                    }
                    if (openGen[0] != gen) return@launch
                    // The source slot empties *before* the capture: the home
                    // layers become the morph's backdrop for the whole flight,
                    // and a card baked into them would sit there frozen under
                    // the window — and, with the close's landing rebound,
                    // visibly doubled beside the copy that is still moving.
                    WyuMorphPageState.flyingRoute = toolRoute
                    val layers = recordHomeLayers()
                    if (openGen[0] != gen) return@launch
                    openPending.value = false
                    cardSnapshots[toolRoute] = card
                    morphStyles[toolRoute] = style
                    if (layers != null) {
                        homeLayers[toolRoute] = layers
                        openCancel.value = false
                        WyuMorphPageState.titleHidden = card.title != null
                        cardMorph = CardMorph(
                            toolRoute,
                            card,
                            layers,
                            style = style,
                            // A morph born from an interrupt starts on a
                            // page that is already at rest; sinking it a
                            // second time is the "drops and shrinks" bug.
                            suppressPageSettle = interrupted,
                        )
                        // From here until BACK, this opening accepts no other
                        // operation. Set synchronously so the first frame is
                        // already guarded, not one LaunchedEffect later.
                        WyuMorphPageState.openingOnly = true
                        // one LaunchedEffect later. The destination is live from
                        // the very first morph frame, so its content is readable
                        // while the window is still growing instead of only
                        // appearing once the animation has finished.
                        val top = nav.currentBackStackEntry?.destination?.route
                        if (top != toolRoute) {
                            if (top == "home") {
                                nav.navigate(toolRoute)
                            } else {
                                nav.navigate(toolRoute) { popUpTo("home") }
                            }
                        }
                        // Tool data must load at once. Previously this stayed
                        // false until onFinished, so the whole secondary page
                        // looked empty for the entire animation.
                        WyuMorphPageState.morphSettled = true
                    } else {
                        // No capture, no morph: hand the slot back at once.
                        WyuMorphPageState.flyingRoute = null
                        WyuMorphPageState.morphSettled = true
                        nav.navigate(toolRoute)
                    }
                }
            } else {
                nav.navigate(toolRoute)
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            // User input has the highest priority. The first DOWN of any
            // gesture stops a running return morph at its current frame
            // instead of making the tap wait for the exit animation. This is
            // deliberately non-consuming: the element under the finger keeps
            // its own click / drag behaviour and only the morph is interrupted.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    WyuMorphPageState.interruptMorph()
                }
            },
    ) {
        // The wallpaper zoom, element shrink and defocus are driven here, at
        // activity scope, so no window teardown can cut them short or reseed
        // them. A quick switch only changes the target: the three clocks turn
        // around from the value on screen instead of replaying from rest.
        MorphBackdropDriver()
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    if (wallpaperCaptureRequested && !wallpaperCaptureDone[0]) {
                        wallpaperLayer.record { this@drawWithContent.drawContent() }
                        wallpaperCaptureDone[0] = true
                    }
                    drawContent()
                },
        ) {
            WyuWindowBackdrop()
        }
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val alpha = backdropDim.value
                    if (alpha > 0.001f) drawRect(Color.Black, alpha = alpha)
                },
        )
        NavHost(
            navController = nav,
            startDestination = initialRoute,
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    if (pageCaptureRequested && !pageCaptureDone[0]) {
                        pageLayer.record { this@drawWithContent.drawContent() }
                        pageCaptureDone[0] = true
                    }
                    drawContent()
                },
            enterTransition = {
                // A page re-entering underneath a running return overlay must
                // not slide; the overlay is the only thing in motion.
                if (cardMorph != null || returnRunning.value) {
                    EnterTransition.None
                } else {
                    // The pushed page is a full-width sheet sliding in from
                    // the right, corners and all.
                    slideInHorizontally(
                        animationSpec = tween(WyuNavEnterMillis, easing = WyuNavEasing),
                    ) { it }
                }
            },
            exitTransition = {
                if (cardMorph != null || returnRunning.value) {
                    ExitTransition.None
                } else {
                    // The page underneath is nudged left and pressed down a
                    // touch while the sheet slides over it.
                    slideOutHorizontally(
                        animationSpec = tween(WyuNavEnterMillis, easing = WyuNavEasing),
                    ) { -(it * WyuNavParentShiftFraction).roundToInt() } +
                        scaleOut(
                            animationSpec = tween(WyuNavEnterMillis, easing = WyuNavEasing),
                            targetScale = WyuNavParentScale,
                        )
                }
            },
            popEnterTransition = {
                if (returnRunning.value) {
                    EnterTransition.None
                } else {
                    // Popping pushes the revealed page right and lifts it back
                    // up while its dim scrim brightens.
                    slideInHorizontally(
                        animationSpec = tween(WyuNavExitMillis, easing = WyuNavEasing),
                    ) { -(it * WyuNavParentShiftFraction).roundToInt() } +
                        scaleIn(
                            animationSpec = tween(WyuNavExitMillis, easing = WyuNavEasing),
                            initialScale = WyuNavParentScale,
                        )
                }
            },
            popExitTransition = {
                if (returnRunning.value) {
                    ExitTransition.None
                } else {
                    // The sheet slides back off to the right over the page it
                    // was covering.
                    slideOutHorizontally(
                        animationSpec = tween(WyuNavExitMillis, easing = WyuNavEasing),
                    ) { it }
                }
            },
        ) {
            composable("home") {
                // Stay sharp while a morph is on screen: the return overlay
                // already crossfades to a sharp home, so the live page under it
                // must not be mid-unblur when the overlay comes off.
                WyuRoutePage(
                    route = "home",
                    currentRoute = currentRoute,
                    stackRoutes = stackRoutes,
                    startSharp = pageStartsSharp,
                    reveal = pageRevealSharp,
                    activeOverride = cardMorph != null || returnMorph != null,
                ) {
                    HomeScreen(
                        authVm = authVm,
                        scheduleVm = app.scheduleViewModel,
                        onOpenCard = { tool, card ->
                            // 常用卡片 keep their own, frozen morph.
                            openTool(tool.route, card, WyuMorphStyle.Card)
                        },
                        onOpenIcon = { tool, card ->
                            openTool(tool.route, card, WyuMorphStyle.Tile)
                        },
                        onLogin = { nav.navigate("me/login") },
                        onOpenJxgl = { nav.navigate("me/jxgl") },
                        onOpenSettings = { card ->
                            openTool(
                                SharedViewTransition.ROUTE_SETTINGS,
                                card,
                                WyuMorphStyle.Tile,
                            )
                        },
                        onOpenCommon = { card ->
                            // Same morph as 更多 icons: the page grows out of the
                            // top bar control.
                            openTool(ROUTE_COMMON, card, WyuMorphStyle.Tile)
                        },
                    )
                }
            }
            composable("schedule") {
                WyuRoutePage(
                    route = "schedule",
                    currentRoute = currentRoute,
                    stackRoutes = stackRoutes,
                    startSharp = pageStartsSharp,
                    reveal = pageRevealSharp,
                ) {
                    ScheduleScreen(
                        vm = app.scheduleViewModel,
                        authVm = authVm,
                        onLogin = { nav.navigate("me/login") },
                        onBack = { closeTool(SharedViewTransition.ROUTE_SCHEDULE) },
                    )
                }
            }
            composable("settings") {
                WyuRoutePage(
                    route = "settings",
                    currentRoute = currentRoute,
                    stackRoutes = stackRoutes,
                    startSharp = pageStartsSharp,
                    reveal = pageRevealSharp,
                ) {
                    SettingsScreen(
                        authVm = authVm,
                        onBack = { closeTool(SharedViewTransition.ROUTE_SETTINGS) },
                        onOpenAdvanced = { nav.navigate("settings/advanced") },
                    )
                }
            }
            composable(ROUTE_COMMON) {
                WyuRoutePage(
                    route = ROUTE_COMMON,
                    currentRoute = currentRoute,
                    stackRoutes = stackRoutes,
                    startSharp = pageStartsSharp,
                    reveal = pageRevealSharp,
                ) {
                    CommonCardsScreen(onBack = { closeTool(ROUTE_COMMON) })
                }
            }
            composable(ROUTE_EXAM) {
                WyuRoutePage(
                    route = ROUTE_EXAM,
                    currentRoute = currentRoute,
                    stackRoutes = stackRoutes,
                    startSharp = pageStartsSharp,
                    reveal = pageRevealSharp,
                ) {
                    ExamScreen(
                        vm = app.examViewModel,
                        authVm = authVm,
                        onLogin = { nav.navigate("me/login") },
                        onBack = { closeTool(ROUTE_EXAM) },
                    )
                }
            }
            composable(ROUTE_GRADES) {
                WyuRoutePage(
                    route = ROUTE_GRADES,
                    currentRoute = currentRoute,
                    stackRoutes = stackRoutes,
                    startSharp = pageStartsSharp,
                    reveal = pageRevealSharp,
                ) {
                    GradesScreen(
                        vm = app.academicsViewModel,
                        authVm = authVm,
                        onLogin = { nav.navigate("me/login") },
                        onOpenGpa = { nav.navigate(ROUTE_GPA) },
                        onBack = { closeTool(ROUTE_GRADES) },
                    )
                }
            }
            composable(ROUTE_GPA) {
                WyuRoutePage(
                    route = ROUTE_GPA,
                    currentRoute = currentRoute,
                    stackRoutes = stackRoutes,
                    startSharp = pageStartsSharp,
                    reveal = pageRevealSharp,
                ) {
                    GpaScreen(
                        vm = app.academicsViewModel,
                        authVm = authVm,
                        onLogin = { nav.navigate("me/login") },
                        onBack = { nav.popBackStack() },
                    )
                }
            }
            composable(ROUTE_EVAL) {
                WyuRoutePage(
                    route = ROUTE_EVAL,
                    currentRoute = currentRoute,
                    stackRoutes = stackRoutes,
                    startSharp = pageStartsSharp,
                    reveal = pageRevealSharp,
                ) {
                    EvalScreen(
                        vm = app.academicsViewModel,
                        authVm = authVm,
                        onLogin = { nav.navigate("me/login") },
                        onBack = { closeTool(ROUTE_EVAL) },
                    )
                }
            }
            composable(ROUTE_PHYSICAL) {
                WyuRoutePage(
                    route = ROUTE_PHYSICAL,
                    currentRoute = currentRoute,
                    stackRoutes = stackRoutes,
                    startSharp = pageStartsSharp,
                    reveal = pageRevealSharp,
                ) {
                    PhysicalScreen(
                        vm = app.academicsViewModel,
                        authVm = authVm,
                        onLogin = { nav.navigate("me/login") },
                        onBack = { closeTool(ROUTE_PHYSICAL) },
                    )
                }
            }
            composable("settings/advanced") {
                WyuRoutePage(
                    route = "settings/advanced",
                    currentRoute = currentRoute,
                    stackRoutes = stackRoutes,
                    reveal = pageRevealSharp,
                ) {
                    SoftGlassAdvancedScreen(onBack = goBack)
                }
            }
            composable("me/login") {
                WyuRoutePage(
                    route = "me/login",
                    currentRoute = currentRoute,
                    stackRoutes = stackRoutes,
                    reveal = pageRevealSharp,
                ) {
                    AuthLoginScreen(
                        vm = authVm,
                        onBack = goBack,
                    )
                }
            }
            composable("me/jxgl") {
                WyuRoutePage(
                    route = "me/jxgl",
                    currentRoute = currentRoute,
                    stackRoutes = stackRoutes,
                    reveal = pageRevealSharp,
                ) {
                    JxglWebScreen(
                        vm = authVm,
                        onBack = goBack,
                        onLogin = { nav.navigate("me/login") },
                    )
                }
            }
            composable("lxt") {
                val lxtVm = remember { app.lxtViewModel }
                WyuRoutePage(
                    route = "lxt",
                    currentRoute = currentRoute,
                    stackRoutes = stackRoutes,
                    startSharp = pageStartsSharp,
                    reveal = pageRevealSharp,
                ) {
                    LxtScreen(
                        vm = lxtVm,
                        onBack = { closeTool(SharedViewTransition.ROUTE_LXT) },
                        onBind = { nav.navigate("lxt/bind") },
                        onBindValve = { nav.navigate("lxt/valve") },
                    )
                }
            }
            composable("lxt/bind") {
                val lxtVm = remember { app.lxtViewModel }
                WyuRoutePage(
                    route = "lxt/bind",
                    currentRoute = currentRoute,
                    stackRoutes = stackRoutes,
                    reveal = pageRevealSharp,
                ) {
                    LxtBindScreen(
                        vm = lxtVm,
                        onBack = goBack,
                    )
                }
            }
            composable("lxt/valve") {
                val lxtVm = remember { app.lxtViewModel }
                WyuRoutePage(
                    route = "lxt/valve",
                    currentRoute = currentRoute,
                    stackRoutes = stackRoutes,
                    reveal = pageRevealSharp,
                ) {
                    LxtBindScreen(
                        vm = lxtVm,
                        onBack = goBack,
                        forValve = true,
                    )
                }
            }
            composable("elec") {
                val elecVm = remember { app.elecViewModel }
                WyuRoutePage(
                    route = "elec",
                    currentRoute = currentRoute,
                    stackRoutes = stackRoutes,
                    startSharp = pageStartsSharp,
                    reveal = pageRevealSharp,
                ) {
                    ElecScreen(
                        vm = elecVm,
                        onBack = { closeTool(SharedViewTransition.ROUTE_ELEC) },
                    )
                }
            }
            composable("gym") {
                val gymVm = remember { app.gymViewModel }
                WyuRoutePage(
                    route = "gym",
                    currentRoute = currentRoute,
                    stackRoutes = stackRoutes,
                    startSharp = pageStartsSharp,
                    reveal = pageRevealSharp,
                ) {
                    GymScreen(
                        vm = gymVm,
                        authVm = authVm,
                        onLogin = { nav.navigate("me/login") },
                        onBack = { closeTool(SharedViewTransition.ROUTE_GYM) },
                    )
                }
            }
        }
        // A retiring close is only a parallel visual layer; for navigation and
        // input purposes the screen belongs to the new morph or the live page.
        val liveReturn = returnMorph?.takeUnless { it.retiring }
        BackHandler(
            enabled = currentRoute in morphRoutes &&
                cardMorph == null &&
                // A return that has not painted a window yet (its page snapshot
                // is still being recorded) must not swallow BACK: that window
                // is invisible, so there is nothing to interrupt.
                (liveReturn == null || liveReturn.page == null),
        ) {
            closeTool(currentRoute)
        }
        BackHandler(
            enabled = openPending.value || cardMorph != null || liveReturn != null,
        ) {
            // Back is the *only* operation accepted while a window is opening.
            // It reverses the morph and, crucially, puts the live home back in
            // the NavHost from the first frames of the interrupt — otherwise
            // the home cards are frozen pixels and no new tap can reach them
            // until the whole reverse animation has played out.
            val current = cardMorph
            if (current == null) {
                if (liveReturn != null) {
                    // The window is already on its way home and the user tapped
                    // the card again, so it is reversing. A second BACK must not
                    // be swallowed for the length of that reversal: the very
                    // same window turns around once more from the frame it is
                    // on — no second capture, no waiting for the reopen to land.
                    if (returnReopen.value) {
                        returnReopen.value = false
                        WyuMorphPageState.returnSettling = true
                    }
                } else if (openPending.value) {
                    // No window exists yet: the home layers are still being
                    // recorded. Drop the pending open at once instead of
                    // leaving BACK dead until the capture finishes.
                    openGen[0]++
                    openJob?.cancel()
                    openJob = null
                    openPending.value = false
                    WyuMorphPageState.flyingRoute = null
                    WyuMorphPageState.openingOnly = false
                    WyuMorphPageState.morphSettled = true
                    settleMorphState()
                }
                return@BackHandler
            }
            if (!openCancel.value) {
                openCancel.value = true
                returnRunning.value = true
                // The interrupt is now the only thing that has happened, and
                // every later press must be able to open another card.
                WyuMorphPageState.openingOnly = false
                cancelCaptureJob = scope.launch {
                    // Capture the destination before it leaves the host: the
                    // reverse window is painted from these pixels afterwards.
                    if (cardMorph !== current) return@launch
                    val page = current.page ?: recordNavHostPage()
                    if (cardMorph !== current) return@launch
                    if (page != null) cardMorph = current.copy(page = page)
                    // Home becomes the live destination while the reverse is
                    // still playing, so its cards are real, hit-testable
                    // composables from this point on. The overlay paints the
                    // whole screen from snapshots, so the swap is invisible.
                    if (nav.currentBackStackEntry?.destination?.route == current.route) {
                        nav.popBackStack()
                    }
                }
            }
        }
        cardMorph?.let { morph ->
            CardMorphOverlay(
                snapshot = morph.snapshot,
                home = morph.home,
                page = morph.page,
                cancelRequested = openCancel.value,
                style = morph.style,
                modifier = Modifier.zIndex(MorphOpenZ),
                suppressPageSettle = morph.suppressPageSettle,
                onCovered = {
                    // Fallback only: openTool already navigates in the same
                    // frame the window is created, so the destination page is
                    // live before this overlay draws its first frame.
                    val top = nav.currentBackStackEntry?.destination?.route
                    if (top != morph.route) {
                        if (top == "home") {
                            nav.navigate(morph.route)
                        } else {
                            // A returning page is still on the stack; end the
                            // stack at home -> new route.
                            nav.navigate(morph.route) { popUpTo("home") }
                        }
                    }
                    WyuMorphPageState.morphSettled = true
                },
                onFinished = {
                    openCancel.value = false
                    cardMorph = null
                    WyuMorphPageState.openingOnly = false
                    WyuMorphPageState.morphSettled = true
                    settleMorphState()
                },
                onCancelled = {
                    // BACK already popped when the interrupt began; this is a
                    // fallback for the rare case its snapshot capture failed.
                    val popped = if (nav.currentBackStackEntry?.destination?.route == morph.route) {
                        returnRunning.value = true
                        nav.popBackStack()
                    } else {
                        false
                    }
                    // Keep the overlay up until the page has actually left the
                    // NavHost. Dropping it on a fixed frame count lifts it a
                    // frame before home is composed, so the tool page shows up
                    // once after the cancel animation has finished.
                    scope.launch {
                        if (popped) {
                            val settled = withTimeoutOrNull(400) {
                                nav.visibleEntries.first { entries ->
                                    entries.none { it.destination.route == morph.route }
                                }
                            }
                            if (settled == null) Log.w("WyuMorph", "cancel settle timed out")
                        }
                        withFrameNanos { }
                        withFrameNanos { }
                        // This coroutine outlives the overlay. Compare the
                        // snapshot (stable across copy(page = ...)) instead of
                        // the input epoch: an incidental press during the
                        // interrupt must not strand the overlay forever, while
                        // a newer morph must never be cleared by this one.
                        if (cardMorph?.snapshot !== morph.snapshot ||
                            openPending.value
                        ) return@launch
                        cardMorph = null
                        openCancel.value = false
                        WyuMorphPageState.openingOnly = false
                        WyuMorphPageState.morphSettled = true
                        settleMorphState()
                        homeLayers.remove(morph.route)
                    }
                },
            )
        }
        returnMorph?.let { morph ->
            CardReturnOverlay(
                snapshot = morph.snapshot,
                page = morph.page,
                home = morph.home,
                reopenRequested = returnReopen.value,
                style = morph.style,
                // A leftover window keeps playing its exit, but it is
                // background: it sits *under* the new container's blurred
                // backdrop instead of floating sharp on top of it. It owns no
                // input at that layer (see CardReturnOverlay); only its moving
                // window is painted, and only where the new window is not.
                modifier = Modifier.zIndex(MorphReturnZ),
                retiring = morph.retiring,
                // While the next window is being captured there is no other
                // layer painting the backdrop. Let this retiring close keep
                // doing it until the new morph exists, so the wallpaper never
                // flashes back to its default zoom between two cards.
                drawBackdrop = { cardMorph == null },
                initialFraction = morph.initialFraction,
                titleStartBounds = morph.titleStartBounds,
                titleStartFontPx = morph.titleStartFontPx,
                titleStartLinePx = morph.titleStartLinePx,
                onTap = { returnReopen.value = true },
                onSwap = {
                    // Read the live state, not the captured `morph`: a running
                    // LaunchedEffect keeps the lambda it started with, and the
                    // overlay may have been marked retiring afterwards.
                    if (returnMorph?.retiring == true) {
                        // Parallel close finished. Detach only this overlay:
                        // the new morph owns the navigation, the title and the
                        // source slot, so none of them may be touched here.
                        returnMorph = null
                        returnReopen.value = false
                        if (cardMorph == null) settleMorphState()
                    } else {
                        // Normally already popped when the return started; this is
                        // only a fallback. Home has had the full animation to
                        // compose, so the overlay can come off immediately.
                        if (nav.currentBackStackEntry?.destination?.route == morph.route) {
                            nav.popBackStack()
                        }
                        // The return finished without being reopened; its saved
                        // page state would otherwise pile up, so drop it.
                        nav.clearBackStack(morph.route)
                        returnMorph = null
                        returnReopen.value = false
                        settleMorphState()
                        cardSnapshots.remove(morph.route)
                        homeLayers.remove(morph.route)
                        // Keep card taps waiting for a 50ms margin after the window
                        // lands, so the next snapshot comes from the settled home.
                        scope.launch {
                            delay(50)
                            WyuMorphPageState.returnSettling = false
                        }
                    }
                },
                onReopened = {
                    if (returnMorph?.retiring == true) {
                        returnMorph = null
                        returnReopen.value = false
                    } else {
                        // The page was popped when the return started; bring it
                        // back with its saved state and only then lift the overlay.
                        scope.launch {
                            if (nav.currentBackStackEntry?.destination?.route != morph.route) {
                                nav.navigate(morph.route) { restoreState = true }
                                val restored = withTimeoutOrNull(300) {
                                    nav.visibleEntries.first { entries ->
                                        entries.any { it.destination.route == morph.route }
                                    }
                                }
                                if (restored == null) Log.w("WyuMorph", "reopen settle timed out")
                            }
                            // A second BACK may have turned this very window
                            // around while the page was being restored: it
                            // belongs to the close again. Dropping it here would
                            // put the page back on screen with no animation,
                            // which is exactly the "my back press did nothing"
                            // the reopen interrupt is meant to avoid.
                            if (!returnReopen.value ||
                                returnMorph?.snapshot !== morph.snapshot
                            ) return@launch
                            withFrameNanos { }
                            withFrameNanos { }
                            if (!returnReopen.value ||
                                returnMorph?.snapshot !== morph.snapshot
                            ) return@launch
                            returnMorph = null
                            returnReopen.value = false
                            settleMorphState()
                            delay(50)
                            WyuMorphPageState.returnSettling = false
                        }
                    }
                },
            )
        }
        // Leftover windows from a rapid switch. They sit above the new morph so
        // their move stays visible until it ends, but they own nothing: no
        // navigation, no input, no shared clocks, no backdrop.
        retiringLayers.forEach { layer ->
            key(layer.snapshot) {
                CardReturnOverlay(
                    snapshot = layer.snapshot,
                    page = layer.page,
                    home = layer.home,
                    reopenRequested = false,
                    style = layer.style,
                    modifier = Modifier.zIndex(MorphReturnZ),
                    retiring = true,
                    initialFraction = layer.initialFraction,
                    onTap = {},
                    onSwap = {
                        retiringLayers.remove(layer)
                        Log.d("WyuMorph", "leftover closed " + layer.route)
                    },
                    onReopened = { retiringLayers.remove(layer) },
                    // Painted only in the frames before the new window exists;
                    // from then on the new morph owns every pixel of the screen.
                    drawBackdrop = { cardMorph == null },
                )
            }
        }
    }
}

/**
 * Wraps a destination with the page transition effects, deriving whether the
 * page is covered by a pushed page or is itself leaving the back stack.
 */
@Composable
private fun WyuRoutePage(
    route: String,
    currentRoute: String?,
    stackRoutes: List<String>,
    startSharp: Boolean = false,
    reveal: Boolean = false,
    activeOverride: Boolean = false,
    content: @Composable () -> Unit,
) {
    val active = activeOverride || currentRoute == route
    WyuPageEffects(
        active = active,
        covered = !active && route in stackRoutes,
        leaving = stackRoutes.isNotEmpty() && route !in stackRoutes,
        startSharp = startSharp || reveal,
        // A page revealed while the morph overlay is up must arrive bright:
        // the overlay has already faded to the undimmed home, so starting it
        // dimmed would flash the whole screen dark at the hand-off.
        startDimmed = reveal && !activeOverride,
    ) {
        content()
    }
}

private data class CardMorph(
    val route: String,
    val snapshot: SharedCardSnapshot,
    val home: SharedHomeLayers,
    val page: Bitmap? = null,
    val style: WyuMorphStyle = WyuMorphStyle.Tile,
    /** Born from an interrupt: the page is already at rest, so this morph
     *  must not sink/shrink it again. */
    val suppressPageSettle: Boolean = false,
)

private data class CardReturn(
    val route: String,
    val snapshot: SharedCardSnapshot,
    val page: Bitmap?,
    val home: SharedHomeLayers?,
    val style: WyuMorphStyle = WyuMorphStyle.Tile,
    /** Parallel close left behind by a press. Keeps its own animation but
     *  owns no navigation and no global page state. */
    val retiring: Boolean = false,
    /** Close progress to resume from when taking over an interrupted open. */
    val initialFraction: Float = 0f,
    val titleStartBounds: Rect? = null,
    val titleStartFontPx: Float = 0f,
    val titleStartLinePx: Float = 0f,
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
