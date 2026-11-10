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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wyutools.app.ui.AuthLoginScreen
import com.wyutools.app.ui.CardMorphOverlay
import com.wyutools.app.ui.CardReturnOverlay
import com.wyutools.app.ui.ElecScreen
import com.wyutools.app.ui.GymScreen
import com.wyutools.app.ui.HomeScreen
import com.wyutools.app.ui.JxglWebScreen
import com.wyutools.app.ui.LocalWyuDarkTheme
import com.wyutools.app.ui.LxtBindScreen
import com.wyutools.app.ui.LxtScreen
import com.wyutools.app.ui.ScheduleScreen
import com.wyutools.app.ui.SettingsScreen
import com.wyutools.app.ui.SoftGlassAdvancedScreen
import com.wyutools.app.ui.WyuMorphPageState
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
import com.wyutools.app.ui.capturePageBitmap
import com.wyutools.app.ui.createBlurBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt
import kotlin.coroutines.resume

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

    val cardSnapshots = remember { mutableMapOf<String, SharedCardSnapshot>() }
    val homeSharpSnapshots = remember { mutableMapOf<String, Bitmap>() }
    var cardMorph by remember { mutableStateOf<CardMorph?>(null) }
    var returnMorph by remember { mutableStateOf<CardReturn?>(null) }
    val returnRunning = remember { mutableStateOf(false) }
    val openPending = remember { mutableStateOf(false) }
    val openCancel = remember { mutableStateOf(false) }
    val returnReopen = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun settleMorphState() {
        WyuMorphPageState.titleHidden = cardMorph != null || returnMorph != null
        if (cardMorph == null && returnMorph == null) {
            WyuMorphPageState.contentProgress = 1f
        }
        WyuMorphPageState.morphSettled = cardMorph == null
        returnRunning.value = returnMorph != null
    }
    // Exit snapshots are recorded from the NavHost layer while the live page
    // hides its title, so no title pixels have to be painted out afterwards.
    val pageLayer = rememberGraphicsLayer()
    var pageCaptureRequested by remember { mutableStateOf(false) }
    val pageCaptureDone = remember { booleanArrayOf(false) }
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
        while (!pageCaptureDone[0] && frames < 100) {
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

    val goBack: () -> Unit = {
        if (!nav.popBackStack()) onExit()
    }
    val closeTool: (String) -> Unit = close@{ toolRoute ->
        if (returnMorph != null || cardMorph != null) return@close
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
            // comes out clean and the page itself never visibly loses it.
            WyuMorphPageState.titleHidden = true
            returnRunning.value = true
            WyuMorphPageState.returnSettling = true
            returnMorph = CardReturn(
                toolRoute,
                snapshot,
                null,
                homeSharpSnapshots[toolRoute],
                titleStartBounds = titleStartBounds,
                titleStartFontPx = titleStartFontPx,
                titleStartLinePx = titleStartLinePx,
            )
            scope.launch {
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
                    val blurred = withContext(Dispatchers.Default) {
                        runCatching { createBlurBackdrop(page, scale = 10) }.getOrNull()
                    }
                    if (blurred != null) {
                        returnMorph = returnMorph?.copy(pageBlur = blurred)
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
    val openTool: (String, SharedCardSnapshot?) -> Unit = { toolRoute, card ->
        if (!openPending.value && cardMorph == null) {
            if (!SharedViewTransition.launchTool(activity, toolRoute, card)) {
                if (card != null && activity != null) {
                    openPending.value = true
                    scope.launch {
                        // A return may still be finishing underneath (a tap in
                        // its last frames reaches home directly); let it clear,
                        // and let home draw its settled frame, before freezing
                        // the window — otherwise the return's pulled-back last
                        // frame ends up inside the new backdrop.
                        var waits = 0
                        while ((returnMorph != null || WyuMorphPageState.returnSettling) && waits < 90) {
                            withFrameNanos { }
                            waits++
                        }
                        val captured = captureWindow(activity)
                        openPending.value = false
                        cardSnapshots[toolRoute] = card
                        if (captured != null) {
                            homeSharpSnapshots[toolRoute] = captured
                            openCancel.value = false
                            WyuMorphPageState.titleHidden = card.title != null
                            // The page composes at once; only its data refresh
                            // waits for the morph, so nothing lands mid-animation.
                            WyuMorphPageState.morphSettled = false
                            cardMorph = CardMorph(toolRoute, card, captured)
                        } else {
                            nav.navigate(toolRoute)
                        }
                    }
                } else {
                    nav.navigate(toolRoute)
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        WyuWindowBackdrop()
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
                        onOpenSchedule = { card ->
                            openTool(SharedViewTransition.ROUTE_SCHEDULE, card)
                        },
                        onOpenLxt = { card -> openTool(SharedViewTransition.ROUTE_LXT, card) },
                        onOpenElec = { card -> openTool(SharedViewTransition.ROUTE_ELEC, card) },
                        onOpenGym = { card -> openTool(SharedViewTransition.ROUTE_GYM, card) },
                        onLogin = { nav.navigate("me/login") },
                        onOpenJxgl = { nav.navigate("me/jxgl") },
                        onOpenSettings = { card ->
                            openTool(SharedViewTransition.ROUTE_SETTINGS, card)
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
        BackHandler(
            enabled = currentRoute in SharedViewTransition.featureRoutes &&
                cardMorph == null &&
                returnMorph == null,
        ) {
            closeTool(currentRoute)
        }
        BackHandler(
            enabled = openPending.value || cardMorph != null || returnMorph != null,
        ) {
            // Back during opening reverses the morph instead of dropping it.
            if (cardMorph != null) {
                openCancel.value = true
                if (cardMorph?.page == null) {
                    scope.launch {
                        val page = recordNavHostPage()
                        if (page != null) {
                            val blurred = withContext(Dispatchers.Default) {
                                runCatching { createBlurBackdrop(page, scale = 10) }
                                    .getOrNull()
                            }
                            cardMorph = cardMorph?.copy(page = page, pageBlur = blurred)
                        }
                    }
                }
            }
        }
        cardMorph?.let { morph ->
            CardMorphOverlay(
                snapshot = morph.snapshot,
                sharpHome = morph.sharpHome,
                pageBlur = morph.pageBlur,
                cancelRequested = openCancel.value,
                modifier = Modifier.zIndex(1f),
                onCovered = {
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
                },
                onFinished = {
                    openCancel.value = false
                    cardMorph = null
                    settleMorphState()
                },
                onCancelled = {
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
                        cardMorph = null
                        openCancel.value = false
                        settleMorphState()
                        homeSharpSnapshots.remove(morph.route)
                    }
                },
            )
        }
        returnMorph?.let { morph ->
            CardReturnOverlay(
                snapshot = morph.snapshot,
                page = morph.page,
                pageBlur = morph.pageBlur,
                sharpHome = morph.sharpHome,
                reopenRequested = returnReopen.value,
                titleStartBounds = morph.titleStartBounds,
                titleStartFontPx = morph.titleStartFontPx,
                titleStartLinePx = morph.titleStartLinePx,
                onTap = { returnReopen.value = true },
                onSwap = {
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
                    homeSharpSnapshots.remove(morph.route)
                    // Keep card taps waiting for a 50ms margin after the window
                    // lands, so the next snapshot comes from the settled home.
                    scope.launch {
                        delay(50)
                        WyuMorphPageState.returnSettling = false
                    }
                },
                onReopened = {
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
                        withFrameNanos { }
                        withFrameNanos { }
                        returnMorph = null
                        returnReopen.value = false
                        settleMorphState()
                        delay(50)
                        WyuMorphPageState.returnSettling = false
                    }
                },
            )
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
    val sharpHome: Bitmap,
    val page: Bitmap? = null,
    val pageBlur: Bitmap? = null,
)

private data class CardReturn(
    val route: String,
    val snapshot: SharedCardSnapshot,
    val page: Bitmap?,
    val sharpHome: Bitmap?,
    val pageBlur: Bitmap? = null,
    val titleStartBounds: Rect? = null,
    val titleStartFontPx: Float = 0f,
    val titleStartLinePx: Float = 0f,
)

private suspend fun captureWindow(activity: Activity): Bitmap? =
    suspendCancellableCoroutine { continuation ->
        capturePageBitmap(activity) { bitmap -> continuation.resume(bitmap) }
    }

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
