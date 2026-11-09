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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
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
import com.wyutools.app.ui.WyuMorphPageState
import com.wyutools.app.ui.WyuPageEffects
import com.wyutools.app.ui.capturePageBitmap
import com.wyutools.app.ui.createBlurBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
                val dark = LocalWyuDarkTheme.current
                LaunchedEffect(dark) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                        ) { dark },
                        navigationBarStyle = SystemBarStyle.auto(
                            android.graphics.Color.argb(0xe6, 0xff, 0xff, 0xff),
                            android.graphics.Color.argb(0x80, 0x1b, 0x1b, 0x1b),
                        ) { dark },
                    )
                }
                WyuApp(
                    initialRoute = initialRoute,
                    onExit = { finish() },
                )
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
    val authVm = app.authViewModel

    val cardSnapshots = remember { mutableMapOf<String, SharedCardSnapshot>() }
    val homeSharpSnapshots = remember { mutableMapOf<String, Bitmap>() }
    var cardMorph by remember { mutableStateOf<CardMorph?>(null) }
    var returnMorph by remember { mutableStateOf<CardReturn?>(null) }
    val morphRunning = remember { mutableStateOf(false) }
    val returnRunning = remember { mutableStateOf(false) }
    val openPending = remember { mutableStateOf(false) }
    val openCancel = remember { mutableStateOf(false) }
    val returnReopen = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Exit snapshots are recorded from the NavHost layer while the live page
    // hides its title, so no title pixels have to be painted out afterwards.
    val pageLayer = rememberGraphicsLayer()
    var pageCaptureRequested by remember { mutableStateOf(false) }
    val pageCaptureDone = remember { booleanArrayOf(false) }

    suspend fun recordNavHostPage(): Bitmap? {
        withFrameNanos { }
        pageCaptureDone[0] = false
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
            // Hide the live title before recording: the overlay's flying title
            // sits over the same spot from this frame on, so the page snapshot
            // comes out clean and the page itself never visibly loses it.
            WyuMorphPageState.titleHidden = true
            returnRunning.value = true
            returnMorph = CardReturn(
                toolRoute,
                snapshot,
                null,
                homeSharpSnapshots[toolRoute],
            )
            scope.launch {
                val page = recordNavHostPage()
                if (page != null) {
                    returnMorph = returnMorph?.copy(page = page)
                    val blurred = withContext(Dispatchers.Default) {
                        runCatching { createBlurBackdrop(page, scale = 10) }.getOrNull()
                    }
                    if (blurred != null) {
                        returnMorph = returnMorph?.copy(pageBlur = blurred)
                    }
                } else {
                    returnMorph = null
                    returnRunning.value = false
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
        if (!openPending.value && cardMorph == null && returnMorph == null) {
            if (!SharedViewTransition.launchTool(activity, toolRoute, card)) {
                if (card != null && activity != null) {
                    openPending.value = true
                    scope.launch {
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
                            morphRunning.value = true
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
                if (morphRunning.value) {
                    EnterTransition.None
                } else {
                    slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 3 } +
                        fadeIn(tween(220))
                }
            },
            exitTransition = {
                if (morphRunning.value) {
                    ExitTransition.None
                } else {
                    slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { -it / 6 } +
                        fadeOut(tween(180))
                }
            },
            popEnterTransition = {
                if (returnRunning.value) {
                    EnterTransition.None
                } else {
                    slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { -it / 6 } +
                        fadeIn(tween(220))
                }
            },
            popExitTransition = {
                if (returnRunning.value) {
                    ExitTransition.None
                } else {
                    slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { it / 3 } +
                        fadeOut(tween(180))
                }
            },
        ) {
            composable("home") {
                // Stay sharp while a morph is on screen: the return overlay
                // already crossfades to a sharp home, so the live page under it
                // must not be mid-unblur when the overlay comes off.
                WyuPageEffects(
                    active = currentRoute == "home" || cardMorph != null || returnMorph != null,
                    startBlurred = false,
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
                WyuPageEffects(
                    active = currentRoute == "schedule",
                    startBlurred = initialRoute != SharedViewTransition.ROUTE_SCHEDULE &&
                        cardMorph == null &&
                        returnMorph == null,
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
                WyuPageEffects(
                    active = currentRoute == "settings",
                    startBlurred = initialRoute != SharedViewTransition.ROUTE_SETTINGS &&
                        cardMorph == null &&
                        returnMorph == null,
                ) {
                    SettingsScreen(
                        authVm = authVm,
                        onBack = { closeTool(SharedViewTransition.ROUTE_SETTINGS) },
                    )
                }
            }
            composable("me/login") {
                WyuPageEffects(active = currentRoute == "me/login") {
                    AuthLoginScreen(
                        vm = authVm,
                        onBack = goBack,
                    )
                }
            }
            composable("me/jxgl") {
                WyuPageEffects(active = currentRoute == "me/jxgl") {
                    JxglWebScreen(
                        vm = authVm,
                        onBack = goBack,
                        onLogin = { nav.navigate("me/login") },
                    )
                }
            }
            composable("lxt") {
                val lxtVm = remember { app.lxtViewModel }
                WyuPageEffects(
                    active = currentRoute == "lxt",
                    startBlurred = initialRoute != SharedViewTransition.ROUTE_LXT &&
                        cardMorph == null &&
                        returnMorph == null,
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
                WyuPageEffects(active = currentRoute == "lxt/bind") {
                    LxtBindScreen(
                        vm = lxtVm,
                        onBack = goBack,
                    )
                }
            }
            composable("lxt/valve") {
                val lxtVm = remember { app.lxtViewModel }
                WyuPageEffects(active = currentRoute == "lxt/valve") {
                    LxtBindScreen(
                        vm = lxtVm,
                        onBack = goBack,
                        forValve = true,
                    )
                }
            }
            composable("elec") {
                val elecVm = remember { app.elecViewModel }
                WyuPageEffects(
                    active = currentRoute == "elec",
                    startBlurred = initialRoute != SharedViewTransition.ROUTE_ELEC &&
                        cardMorph == null &&
                        returnMorph == null,
                ) {
                    ElecScreen(
                        vm = elecVm,
                        onBack = { closeTool(SharedViewTransition.ROUTE_ELEC) },
                    )
                }
            }
            composable("gym") {
                val gymVm = remember { app.gymViewModel }
                WyuPageEffects(
                    active = currentRoute == "gym",
                    startBlurred = initialRoute != SharedViewTransition.ROUTE_GYM &&
                        cardMorph == null &&
                        returnMorph == null,
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
                onCovered = {
                    if (currentRoute == "home") nav.navigate(morph.route)
                },
                onFinished = {
                    morphRunning.value = false
                    openCancel.value = false
                    cardMorph = null
                    WyuMorphPageState.titleHidden = false
                    WyuMorphPageState.contentProgress = 1f
                    WyuMorphPageState.morphSettled = true
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
                        morphRunning.value = false
                        openCancel.value = false
                        WyuMorphPageState.titleHidden = false
                        WyuMorphPageState.contentProgress = 1f
                        WyuMorphPageState.morphSettled = true
                        returnRunning.value = false
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
                onTap = { returnReopen.value = true },
                onSwap = {
                    val popped =
                        if (nav.currentBackStackEntry?.destination?.route == morph.route) {
                            nav.popBackStack()
                        } else {
                            false
                        }
                    // The overlay already matches the live home, so it only has
                    // to stay up until the host has swapped the page out.
                    scope.launch {
                        if (popped) {
                            val settled = withTimeoutOrNull(400) {
                                nav.visibleEntries.first { entries ->
                                    entries.none { it.destination.route == morph.route }
                                }
                            }
                            if (settled == null) Log.w("WyuMorph", "return settle timed out")
                        }
                        withFrameNanos { }
                        withFrameNanos { }
                        returnMorph = null
                        returnRunning.value = false
                        returnReopen.value = false
                        WyuMorphPageState.titleHidden = false
                        WyuMorphPageState.contentProgress = 1f
                        cardSnapshots.remove(morph.route)
                        homeSharpSnapshots.remove(morph.route)
                    }
                },
                onReopened = {
                    // The page stayed composed under the overlay; just drop it.
                    scope.launch {
                        withFrameNanos { }
                        withFrameNanos { }
                        returnMorph = null
                        returnRunning.value = false
                        returnReopen.value = false
                        WyuMorphPageState.titleHidden = false
                        WyuMorphPageState.contentProgress = 1f
                    }
                },
            )
        }
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
