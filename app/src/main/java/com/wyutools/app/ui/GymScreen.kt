package com.wyutools.app.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.SportsBasketball
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.wyutools.app.auth.AuthViewModel
import com.wyutools.app.gym.GymRecord
import com.wyutools.app.gym.GymSlot
import com.wyutools.app.gym.GymUiState
import com.wyutools.app.gym.GymVenue
import com.wyutools.app.gym.GymViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.LocalDate
import java.util.Locale

@Composable
fun GymScreen(
    vm: GymViewModel,
    authVm: AuthViewModel,
    onLogin: () -> Unit,
    onBack: () -> Unit,
) {
    val ui by vm.ui.collectAsState()
    val authUi by authVm.ui.collectAsState()
    var ssoAttempted by rememberSaveable { mutableStateOf(false) }

    val openNativeLogin: () -> Unit = {
        ssoAttempted = false
        onLogin()
    }
    val continueWithSso: () -> Unit = {
        val cookie = authVm.ssoCookieHeader()
        if (cookie.isNullOrBlank()) {
            openNativeLogin()
        } else {
            ssoAttempted = true
            vm.loginWithCookie(cookie)
        }
    }

    LaunchedEffect(ui.user) {
        if (ui.user != null) ssoAttempted = false
    }

    // A manual (or silent) login success re-arms the SSO hand-off, so the gym
    // session is picked up without the user tapping anything else.
    LaunchedEffect(authUi.loginSuccessCount) {
        if (authUi.loginSuccessCount > 0) ssoAttempted = false
    }

    // The account is remembered on this device but the SSO session may simply
    // not have come up yet (cold start, silent re-login in flight). That is not
    // a reason to ask for a login: wait, try silently, and only show the prompt
    // once a login has actually failed.
    val hasSavedAccount = authVm.hasSavedAccount && !authUi.loggedOut
    val loginFailed = ui.loginError != null ||
        ui.loginExpired ||
        authUi.loginError != null
    var silentLoginTried by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(ui.user, ui.loginNeeded, authUi.restoring, authUi.loggedIn, authUi.loggedOut) {
        if (ui.user != null || !ui.loginNeeded) return@LaunchedEffect
        if (authUi.restoring || authUi.loggedIn || authUi.loggedOut) return@LaunchedEffect
        if (!hasSavedAccount || silentLoginTried || authUi.autoLoggingIn) return@LaunchedEffect
        silentLoginTried = true
        authVm.trySilentLogin()
    }

    LaunchedEffect(ui.loginNeeded, authUi.restoring, authUi.loggedIn, authUi.loggingOut) {
        if (!ui.loginNeeded || authUi.restoring || authUi.loggingOut) return@LaunchedEffect
        // Login stays manual: the auth-required screen waits for the button
        // tap instead of jumping to the login page. A remembered SSO session
        // still continues automatically.
        if (authUi.loggedIn && !ssoAttempted) continueWithSso()
    }

    // The gym session is a copy of the SSO cookie, so signing out of SSO has to
    // take the gym side with it. Without this the copied cookie and the cached
    // user survive a logout and the page keeps rendering a session that is
    // already gone. Guarded on `loggedOut` (not on "not logged in") so a plain
    // cold start never wipes a healthy gym session.
    val signedOut = authUi.loggedOut
    LaunchedEffect(signedOut) {
        if (signedOut) vm.logout()
    }

    // A loading page is only honest while a session hand-off is actually in
    // flight: the device remembers an account *and* still holds a local login
    // (either confirmed, or a stored session that is being revalidated right
    // now). Merely remembering an account — after a logout, or with no session
    // left on this device — is not a reason to spin: show the prompt instead.
    val waitingForSession = hasSavedAccount &&
        (authUi.loggedIn || authUi.restoring) &&
        !loginFailed

    when {
        ui.user != null && !signedOut -> GymContent(
            vm = vm,
            ui = ui,
            onBack = onBack,
            onLogout = {
                vm.logout()
                authVm.logout()
            },
        )

        ui.loginNeeded || signedOut -> {
            if (waitingForSession) {
                GymLoadingScreen(onBack = onBack)
            } else {
                GymAuthRequiredScreen(
                    ui = ui,
                    authLoggedIn = authUi.loggedIn,
                    onLogin = openNativeLogin,
                    onSsoLogin = continueWithSso,
                    onBack = onBack,
                    authError = authUi.loginError,
                )
            }
        }

        else -> GymLoadingScreen(onBack = onBack)
    }
}

@Composable
private fun GymContent(
    vm: GymViewModel,
    ui: GymUiState,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    var slotPickerTarget by remember { mutableStateOf<GymVenue?>(null) }
    var bookingTarget by remember { mutableStateOf<Pair<GymVenue, GymSlot>?>(null) }
    var cancelTarget by remember { mutableStateOf<GymRecord?>(null) }
    var showLogout by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val venueListState = rememberLazyListState()
    val recordsListState = rememberLazyListState()
    val venueFraction = rememberWyuCollapseFraction(venueListState)
    val recordsFraction = rememberWyuCollapseFraction(recordsListState)
    val collapseFraction = rememberWyuPagerCollapseFraction(
        pagerState = pagerState,
        firstFraction = venueFraction,
        secondFraction = recordsFraction,
    )
    // The bookings tab is a heavy neighbour: keep it out of the opening frame,
    // then compose it once the morph has landed so later swipes stay smooth.
    var prefetchRecords by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        WyuMorphPageState.awaitSettled()
        delay(400)
        prefetchRecords = true
    }

    // One toast per pending notice. The notice is taken out of the state by the
    // view model (atomically), so a duplicate composition cannot show it again,
    // and a message that repeats within a moment is dropped there as well.
    LaunchedEffect(ui.notice) {
        if (ui.notice == null) return@LaunchedEffect
        val notice = vm.takeNotice()
        // A finished booking closes the dialog whether or not its own message
        // was the one shown, so a dropped duplicate never strands the sheet.
        if (bookingTarget != null && !ui.booking) {
            bookingTarget = null
        }
        if (notice != null) {
            Toast.makeText(context, notice.message, Toast.LENGTH_SHORT).show()
        }
    }

    WyuPageScaffold(
        progress = { collapseFraction.value },
        maskCollapseHeight = WyuLargeTitleHeight,
        maskExtension = 34.dp,
        overlay = {
            GymTabsOverlay(
                pagerState = pagerState,
                collapsedFraction = collapseFraction,
                onSelect = { index ->
                    if (index != pagerState.currentPage) {
                        scope.launch {
                            pagerState.animateScrollToPage(
                                page = index,
                                animationSpec = tween(380, easing = WyuTabEasing),
                            )
                        }
                    }
                },
            )
        },
        topBar = {
            WyuActionBar(
                title = "体育馆预约",
                collapsedFraction = { collapseFraction.value },
                navigationIcon = {
                    WyuTopBarIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        onClick = onBack,
                    )
                },
                actions = {
                    WyuTopBarAction(
                        icon = Icons.Filled.Refresh,
                        label = "刷新",
                        onClick = vm::refreshAll,
                    )
                    WyuTopBarAction(
                        icon = Icons.AutoMirrored.Filled.Logout,
                        label = "退出",
                        onClick = { showLogout = true },
                    )
                },
            )
        },
    ) { padding ->
        val listTopInset = padding.calculateTopPadding() - WyuLargeTitleHeight
        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = if (prefetchRecords) 1 else 0,
            ) { page ->
                if (page == 0) {
                    VenueTab(
                        ui = ui,
                        listState = venueListState,
                        topInset = listTopInset,
                        onSelectDate = vm::selectDate,
                        onSelectSportFilter = vm::selectSportFilter,
                        onRefresh = vm::refreshAll,
                        onSelectSlots = { slotPickerTarget = it },
                    )
                } else {
                    RecordsTab(
                        ui = ui,
                        listState = recordsListState,
                        topInset = listTopInset,
                        onCancel = { cancelTarget = it },
                    )
                }
            }
        }
    }

    slotPickerTarget?.let { venue ->
        SlotPickerDialog(
            venue = venue,
            date = ui.selectedDate,
            onDismiss = { slotPickerTarget = null },
            onPick = { slot ->
                slotPickerTarget = null
                bookingTarget = venue to slot
            },
        )
    }

    bookingTarget?.let { (venue, slot) ->
        BookingDialog(
            venue = venue,
            slot = slot,
            date = ui.selectedDate,
            initialPhone = ui.savedPhone,
            booking = ui.booking,
            onDismiss = { if (!ui.booking) bookingTarget = null },
            onConfirm = { phone -> vm.book(venue, slot, phone) },
        )
    }

    cancelTarget?.let { record ->
        MiuixConfirmDialog(
            show = true,
            title = "取消预约",
            message = "确定取消「${record.venueName}」${record.dateRaw} ${record.slot} 吗？取消后名额会释放。",
            confirmText = "确认取消",
            dismissText = "保留预约",
            destructive = true,
            onDismiss = {
                if (ui.cancellingWid == null) cancelTarget = null
            },
            onConfirm = {
                if (ui.cancellingWid == null) {
                    cancelTarget = null
                    vm.cancel(record)
                }
            },
        )
    }

    MiuixConfirmDialog(
        show = showLogout,
        title = "退出登录",
        message = "将退出校园统一身份认证并清除本机登录会话（“我”页也会退出），已提交的预约不受影响。",
        confirmText = "退出",
        destructive = true,
        onDismiss = { showLogout = false },
        onConfirm = {
            showLogout = false
            onLogout()
        },
    )
}

private val GymTabsAreaHeight = 50.dp
private val GymContentTopSpacer = WyuLargeTitleHeight + GymTabsAreaHeight - 6.dp
private val GymTabTitles = listOf("场地预约", "我的预约")

private const val VenueChipRevealMillis = 160
private const val VenueChipStaggerMillis = 16L
private const val VenueChipInitialScale = 0.9f
private const val VenueCardEnterMillis = 300
private const val VenueCardExitMillis = 160
private const val VenueCardEnterScale = 0.92f

// Semester deck: the container unfolds at a calm pace and each card gets its
// own beat, so a long semester reads as a stack being dealt instead of one
// block appearing. Folding back is the opposite: the old cards fade out while
// the container gives its height back straight away, so the semesters below
// climb the moment the title is tapped.
private const val SemesterExpandMillis = 300
private const val SemesterCollapseMillis = 200
private const val SemesterCardMillis = 300
private const val SemesterCardFoldMillis = 120
private const val SemesterCardStaggerMillis = 55
private const val SemesterCardInitialScale = 0.94f
private val SemesterCardGap = 3.dp
private val SemesterDeckEasing = CubicBezierEasing(0.24f, 0f, 0.18f, 1f)

// What the semesters under a closing deck do. They are not given a placement
// animation on purpose: a placement spec is retargeted on every frame of the
// collapse, so its overshoot phase never gets to play — by the time the layout
// stops moving there is no distance left to overshoot. Instead every block
// below rides the collapse frame for frame (they all shift by the same amount,
// so the pace is identical no matter how far down the list a block sits), and
// the rebound is added on top by hand: a short hop past the landing spot while
// the deck is still folding, then a soft settle back down. The lift is smaller
// than the 12dp gap between blocks, so a bouncing block never overlaps its
// neighbour above.
private const val SemesterReboundLeadMillis = 130
private const val SemesterReboundRiseMillis = 110
private const val SemesterReboundFallMillis = 300
private const val SemesterReboundMillis =
    SemesterReboundLeadMillis + SemesterReboundRiseMillis + SemesterReboundFallMillis
private val SemesterReboundLift = 9.dp
private val SemesterReboundFallEasing = CubicBezierEasing(0.25f, 0.55f, 0.3f, 1f)
private const val SemesterChevronMillis = 220

/** Slim glass tabs floating over the pager; they ride up with the collapsing bar. */
@Composable
private fun GymTabsOverlay(
    pagerState: PagerState,
    collapsedFraction: State<Float>,
    onSelect: (Int) -> Unit,
) {
    val selectionFraction = remember(pagerState) {
        { pagerState.currentPage + pagerState.currentPageOffsetFraction }
    }
    val boost = LocalWyuCollapseBoost.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val morph = WyuMorphPageState.contentProgress
                val scale = 1f - WyuMorphPageState.ContentShrink * (1f - morph)
                scaleX = scale
                scaleY = scale
                translationY = (1f - morph) * WyuMorphPageState.ContentLift.toPx()
            }
            .statusBarsPadding()
            .padding(top = 62.dp),
    ) {
        WyuFloatingTabs(
            options = GymTabTitles,
            selectedIndex = pagerState.currentPage,
            onSelect = onSelect,
            selectionFraction = selectionFraction,
            modifier = Modifier
                .graphicsLayer {
                    val collapsed = (collapsedFraction.value + boost()).coerceIn(0f, 1f)
                    translationY = WyuLargeTitleHeight.toPx() * (1f - collapsed)
                }
                .padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun VenueTab(
    ui: GymUiState,
    listState: LazyListState,
    topInset: Dp,
    onSelectDate: (LocalDate) -> Unit,
    onSelectSportFilter: (String) -> Unit,
    onRefresh: () -> Unit,
    onSelectSlots: (GymVenue) -> Unit,
) {
    val today = LocalDate.now()
    val dates = remember(today) { (0L..4L).map { today.plusDays(it) } }
    val bookable = remember(ui.venues) { ui.venues.filter { it.slots.isNotEmpty() } }
    val sportOptions = remember(ui.venues) {
        ui.venues.mapNotNull { it.sportName?.trim()?.takeIf { name -> name.isNotEmpty() } }
            .distinct()
            .sorted()
    }
    val sportFilter = ui.sportFilter

    LaunchedEffect(sportOptions, sportFilter) {
        if (sportOptions.isNotEmpty() && sportFilter.isNotEmpty() && sportFilter !in sportOptions) {
            onSelectSportFilter("")
        }
    }

    val venues = bookable.filter { venue ->
        sportFilter.isEmpty() || venue.sportName == sportFilter
    }

    // One shared reveal value drives every card layer, so first paint and
    // date/filter switches use the same tween without recomposing the list.
    val venuesSignature = remember(venues, ui.selectedDate, sportFilter) {
        buildString {
            append(ui.selectedDate.toEpochDay()).append('|').append(sportFilter)
            venues.forEach { append('|').append(it.wid).append(it.name).append(it.slots.size) }
        }
    }
    val cardProgress = remember(venuesSignature) { Animatable(0f) }
    LaunchedEffect(cardProgress) {
        cardProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = VenueCardEnterMillis,
                easing = FastOutSlowInEasing,
            ),
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, top = topInset, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "tabs-spacer") {
            Spacer(Modifier.height(GymContentTopSpacer))
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                dates.forEach { date ->
                    DateChip(
                        date = date,
                        selected = ui.selectedDate == date,
                        onClick = { onSelectDate(date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (sportOptions.size > 1) {
            item {
                VenueFilterRow(
                    title = "体育项目",
                    options = sportOptions,
                    selected = sportFilter,
                    onSelect = onSelectSportFilter,
                )
            }
        }
        if (ui.venuesLoaded && !ui.loadingVenues && venues.isEmpty()) {
            item {
                val filtered = sportFilter.isNotEmpty()
                EmptyHint(
                    message = if (filtered) {
                        "没有符合筛选条件的场地。"
                    } else {
                        "当天暂无可约场地，试试其他日期。"
                    },
                    onRetry = if (filtered) {
                        { onSelectSportFilter("") }
                    } else {
                        onRefresh
                    },
                    retryLabel = if (filtered) "清除筛选" else "刷新",
                )
            }
        }
        items(venues, key = { "${it.wid}|${it.name}" }) { venue ->
            Box(
                modifier = Modifier
                    .animateItem(
                        fadeInSpec = null,
                        placementSpec = null,
                        fadeOutSpec = tween(VenueCardExitMillis),
                    )
                    .graphicsLayer {
                        val progress = cardProgress.value
                        alpha = progress
                        val scale = VenueCardEnterScale +
                            (1f - VenueCardEnterScale) * progress
                        scaleX = scale
                        scaleY = scale
                    },
            ) {
                VenueCard(
                    venue = venue,
                    enabled = !ui.booking,
                    onSelectSlots = { onSelectSlots(venue) },
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun DateChip(
    date: LocalDate,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        minHeight = 54.dp,
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        colors = if (selected) {
            ButtonDefaults.buttonColorsPrimary()
        } else {
            ButtonDefaults.buttonColors()
        },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = datePrimaryLabel(date),
                style = MiuixTheme.textStyles.footnote1,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "${date.monthValue}/${date.dayOfMonth}",
                style = MiuixTheme.textStyles.footnote2,
            )
        }
    }
}

@Composable
private fun VenueFilterRow(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column {
        Text(
            text = title,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = "all") {
                VenueChipReveal(index = 0) {
                    ActionChip(
                        text = "全部",
                        onClick = { onSelect("") },
                        primary = selected.isEmpty(),
                    )
                }
            }
            itemsIndexed(options, key = { _, option -> "option|$option" }) { index, option ->
                VenueChipReveal(index = index + 1) {
                    ActionChip(
                        text = option,
                        onClick = { onSelect(option) },
                        primary = selected == option,
                    )
                }
            }
        }
    }
}

/** Fades each chip in from transparent while it grows to full size. */
@Composable
private fun VenueChipReveal(
    index: Int,
    content: @Composable () -> Unit,
) {
    var revealed by rememberSaveable { mutableStateOf(false) }
    val progress = animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(
            durationMillis = VenueChipRevealMillis,
            easing = FastOutSlowInEasing,
        ),
        label = "venueChipReveal",
    )
    LaunchedEffect(Unit) {
        if (index > 0) delay(index * VenueChipStaggerMillis)
        revealed = true
    }
    Box(
        modifier = Modifier.graphicsLayer {
            val value = progress.value
            alpha = value
            val scale = VenueChipInitialScale + (1f - VenueChipInitialScale) * value
            scaleX = scale
            scaleY = scale
        },
    ) {
        content()
    }
}

@Composable
private fun VenueCard(
    venue: GymVenue,
    enabled: Boolean,
    onSelectSlots: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = venue.name,
                    style = MiuixTheme.textStyles.title3,
                    fontWeight = FontWeight.SemiBold,
                )
                val meta = listOfNotNull(
                    venue.sportName,
                    venue.venueType,
                    venue.address,
                ).distinct().joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            val openStatus = venue.openStatus
            if (!openStatus.isNullOrBlank()) {
                StatusPill(text = openStatus, statusCode = null)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "可约时段 · ${venue.slots.size} 个",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.primary,
            )
            Spacer(Modifier.weight(1f))
            venue.maxDurationMinutes?.let { minutes ->
                Text(
                    text = "单次最长 $minutes 分钟",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        // One button per card: the slots (often dozens) only compose inside
        // the picker dialog, keeping the page's first frame light.
        Button(
            onClick = onSelectSlots,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            cornerRadius = 16.dp,
            insideMargin = PaddingValues(vertical = 10.dp),
            colors = ButtonDefaults.buttonColors(),
        ) {
            Icon(
                imageVector = Icons.Filled.Schedule,
                contentDescription = null,
                tint = LocalContentColor.current,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "选择时段",
                style = MiuixTheme.textStyles.footnote1,
            )
        }
    }
}

/** Slot picker for one venue; the phone confirmation follows after it closes. */
@Composable
private fun SlotPickerDialog(
    venue: GymVenue,
    date: LocalDate,
    onDismiss: () -> Unit,
    onPick: (GymSlot) -> Unit,
) {
    var visible by remember { mutableStateOf(true) }
    var picked by remember { mutableStateOf<GymSlot?>(null) }
    WyuOverlayDialog(
        show = visible,
        title = "选择时段",
        summary = "${venue.name}\n${date.monthValue}月${date.dayOfMonth}日 · " +
            "共 ${venue.slots.size} 个可约时段",
        onDismissRequest = { visible = false },
        onDismissFinished = {
            val slot = picked
            if (slot != null) onPick(slot) else onDismiss()
        },
    ) {
        Spacer(Modifier.height(12.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            venue.slots.forEach { slot ->
                WyuDialogOption(
                    text = slot.time,
                    onClick = {
                        picked = slot
                        visible = false
                    },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        WyuDialogCancelButton(onClick = { visible = false })
    }
}

@Composable
private fun RecordsTab(
    ui: GymUiState,
    listState: LazyListState,
    topInset: Dp,
    onCancel: (GymRecord) -> Unit,
) {
    val groups = remember(ui.records) { groupRecordsBySemester(ui.records) }
    // Any number of semesters can stay unfolded: each title toggles only its
    // own deck, so records from two terms can be compared side by side. The
    // open labels survive rotation.
    var openSemesters by rememberSaveable(
        stateSaver = listSaver(
            save = { it.toList() },
            restore = { it.toSet() },
        ),
    ) { mutableStateOf(setOf<String>()) }
    // Which semester is folding right now, and a counter that ticks once per
    // fold. Only the blocks *below* the folding one rebound: they are the ones
    // the collapse drags upwards. Expanding leaves them alone — a block hopping
    // around while a deck opens above it would only get in the way.
    var foldingLabel by remember { mutableStateOf<String?>(null) }
    var foldTick by remember { mutableStateOf(0) }
    LaunchedEffect(foldingLabel) {
        if (foldingLabel != null) {
            // Long enough for the collapse *and* the rebound to finish, so the
            // hop is never cut in half by this label going back to null.
            delay(SemesterReboundMillis.toLong() + 80)
            foldingLabel = null
        }
    }
    val foldingIndex = groups.indexOfFirst { it.label == foldingLabel }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, top = topInset, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "tabs-spacer") {
            Spacer(Modifier.height(GymContentTopSpacer))
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "按学期统计已使用预约时长，每 20 分钟折算 1 公里。",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.weight(1f),
                )
                if (ui.loadingRecords) {
                    Spacer(Modifier.width(10.dp))
                    CircularProgressIndicator(
                        size = 16.dp,
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
        if (ui.recordsLoaded && ui.records.isEmpty() && !ui.loadingRecords) {
            item {
                EmptyHint(message = "还没有预约记录。")
            }
        }
        groups.forEachIndexed { index, group ->
            val open = group.label in openSemesters
            val ridesUp = foldingIndex >= 0 && index > foldingIndex
            item(key = "semester|${group.label}") {
                // The hop past the landing spot. Layout handles the travel (the
                // block simply follows the shrinking deck), this only adds the
                // bounce on top, which is why the upward overshoot is actually
                // visible here instead of being swallowed by a retargeted
                // placement animation.
                val rebound = remember { Animatable(0f) }
                val lift = with(LocalDensity.current) { SemesterReboundLift.toPx() }
                LaunchedEffect(foldTick) {
                    if (foldTick == 0) return@LaunchedEffect
                    rebound.snapTo(0f)
                    if (!ridesUp) return@LaunchedEffect
                    delay(SemesterReboundLeadMillis.toLong())
                    rebound.animateTo(
                        targetValue = -lift,
                        animationSpec = tween(
                            durationMillis = SemesterReboundRiseMillis,
                            easing = FastOutSlowInEasing,
                        ),
                    )
                    rebound.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(
                            durationMillis = SemesterReboundFallMillis,
                            easing = SemesterReboundFallEasing,
                        ),
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(0, rebound.value.roundToInt()) },
                ) {
                    SemesterHeader(
                        group = group,
                        expanded = open,
                        onClick = {
                            if (open) {
                                openSemesters = openSemesters - group.label
                                foldingLabel = group.label
                                foldTick++
                            } else {
                                openSemesters = openSemesters + group.label
                            }
                        },
                    )
                    // The deck unfolds from under the header: the container
                    // gains height while the cards cascade in one after the
                    // other, top to bottom.
                    AnimatedVisibility(
                        visible = open,
                        enter = expandVertically(
                            animationSpec = tween(
                                durationMillis = SemesterExpandMillis,
                                easing = SemesterDeckEasing,
                            ),
                            expandFrom = Alignment.Top,
                        ),
                        // No delay on the way back: a fold must free the space
                        // immediately, so whatever sits below starts rising on
                        // the same frame as the tap.
                        exit = shrinkVertically(
                            animationSpec = tween(
                                durationMillis = SemesterCollapseMillis,
                                easing = SemesterDeckEasing,
                            ),
                            shrinkTowards = Alignment.Top,
                        ),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                            // Stacked: cards sit right under each other with a
                            // hairline gap, so an open semester reads as a deck.
                            verticalArrangement = Arrangement.spacedBy(SemesterCardGap),
                        ) {
                            group.records.forEachIndexed { index, record ->
                                SemesterDeckCard(
                                    index = index,
                                    open = open,
                                ) {
                                    RecordCard(
                                        record = record,
                                        cancelling = ui.cancellingWid == record.wid,
                                        onCancel = { onCancel(record) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

private data class SemesterGroup(
    val label: String,
    val records: List<GymRecord>,
    val totalMinutes: Int,
)

/**
 * One card of a semester deck.
 *
 * Opening: waits [index] beats, then fades in while growing from
 * [SemesterCardInitialScale] to full size, so the deck lands top card first.
 * Closing: no beats and no wait — every card leaves on the same frame, so the
 * container can give its space back at once. The alpha/scale live on a graphics
 * layer, so the stagger costs no layout and the cards never jitter each other.
 */
@Composable
private fun SemesterDeckCard(
    index: Int,
    open: Boolean,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(open) {
        progress.animateTo(
            targetValue = if (open) 1f else 0f,
            animationSpec = if (open) {
                tween(
                    durationMillis = SemesterCardMillis,
                    delayMillis = index * SemesterCardStaggerMillis,
                    easing = SemesterDeckEasing,
                )
            } else {
                tween(
                    durationMillis = SemesterCardFoldMillis,
                    easing = SemesterDeckEasing,
                )
            },
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val p = progress.value
                alpha = p
                val scale = SemesterCardInitialScale + (1f - SemesterCardInitialScale) * p
                scaleX = scale
                scaleY = scale
            },
    ) {
        content()
    }
}

/**
 * Folded semester title: the tap target for the deck below it, carrying the
 * count and the converted distance so a folded semester still reads at a glance.
 */
@Composable
private fun SemesterHeader(
    group: SemesterGroup,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = SemesterChevronMillis),
        label = "semesterChevron",
    )
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = group.label,
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "已使用时长 ${group.totalMinutes} 分钟 · 抵消 " +
                        "${offsetKm(group.totalMinutes)} 公里",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "${group.records.size} 场",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "收起" else "展开",
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer { rotationZ = chevron },
            )
        }
    }
}

private fun groupRecordsBySemester(records: List<GymRecord>): List<SemesterGroup> =
    records.groupBy { semesterLabel(it) }
        .map { (label, items) ->
            SemesterGroup(
                label = label,
                records = items,
                totalMinutes = items
                    .filter { it.statusCode == "002" }
                    .sumOf { it.durationMinutes ?: 0 },
            )
        }

private fun offsetKm(minutes: Int): String {
    val km = minutes / 20.0
    return if (km % 1.0 == 0.0) {
        km.toInt().toString()
    } else {
        String.format(Locale.US, "%.1f", km)
    }
}

private fun semesterLabel(record: GymRecord): String {
    val fromApi = record.semester?.trim().orEmpty()
    if (fromApi.isNotEmpty()) return fromApi
    val date = record.date ?: return "未知学期"
    val year = date.year
    return when (date.monthValue) {
        1 -> "${year - 1}-$year 学年第一学期"
        in 2..8 -> "${year - 1}-$year 学年第二学期"
        else -> "$year-${year + 1} 学年第一学期"
    }
}

@Composable
private fun RecordCard(
    record: GymRecord,
    cancelling: Boolean,
    onCancel: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = record.venueName,
                style = MiuixTheme.textStyles.title4,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            StatusPill(text = record.status, statusCode = record.statusCode)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${record.dateRaw} ${record.slot}",
                style = MiuixTheme.textStyles.body2,
                modifier = Modifier.weight(1f),
            )
            record.durationMinutes?.let { minutes ->
                Text(
                    text = "预约时长 $minutes 分钟",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        val submitted = record.submittedAt
        if (!submitted.isNullOrBlank()) {
            Text(
                text = "申请时间 $submitted",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        if (record.violation) {
            Text(
                text = "存在违约记录",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.error,
            )
        }
        if (record.canCancel) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    text = if (cancelling) "取消中…" else "取消预约",
                    onClick = onCancel,
                    enabled = !cancelling,
                )
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, statusCode: String?) {
    val (background, content) = when (statusCode) {
        "001" -> MiuixTheme.colorScheme.primaryContainer to
            MiuixTheme.colorScheme.onPrimaryContainer
        "002" -> MiuixTheme.colorScheme.surfaceVariant to
            MiuixTheme.colorScheme.onSurfaceVariantSummary
        "003" -> MiuixTheme.colorScheme.errorContainer to
            MiuixTheme.colorScheme.onErrorContainer
        else -> MiuixTheme.colorScheme.secondaryContainer to
            MiuixTheme.colorScheme.onSecondaryContainer
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = background,
        contentColor = content,
    ) {
        Text(
            text = text,
            color = content,
            style = MiuixTheme.textStyles.footnote2,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun EmptyHint(
    message: String,
    onRetry: (() -> Unit)? = null,
    retryLabel: String = "刷新",
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Outlined.SportsBasketball,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
            )
            if (onRetry != null) {
                Spacer(Modifier.height(8.dp))
                TextButton(text = retryLabel, onClick = onRetry)
            }
        }
    }
}

@Composable
private fun BookingDialog(
    venue: GymVenue,
    slot: GymSlot,
    date: LocalDate,
    initialPhone: String,
    booking: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var phone by rememberSaveable(initialPhone) { mutableStateOf(initialPhone) }
    var phoneError by remember { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf(true) }

    WyuOverlayDialog(
        show = visible,
        title = "确认预约",
        summary = "${venue.name}\n${date.monthValue}月${date.dayOfMonth}日 ${slot.time}",
        onDismissRequest = { if (!booking) visible = false },
        onDismissFinished = onDismiss,
    ) {
        val duration = slot.durationMinutes ?: venue.maxDurationMinutes
        if (duration != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "预约时长约 $duration 分钟",
                modifier = Modifier.fillMaxWidth(),
                style = MiuixTheme.textStyles.footnote2,
                textAlign = TextAlign.Center,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Spacer(Modifier.height(12.dp))
        TextField(
            value = phone,
            onValueChange = { value ->
                if (value.length <= 11 && value.all { it.isDigit() }) {
                    phone = value
                    phoneError = null
                }
            },
            label = "手机号",
            singleLine = true,
            enabled = !booking,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Done,
            ),
        )
        phoneError?.let { error ->
            Spacer(Modifier.height(6.dp))
            Text(
                text = error,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "当天只能预约一场；预约成功后请按时到场，违约可能影响后续预约。",
            modifier = Modifier.fillMaxWidth(),
            style = MiuixTheme.textStyles.footnote2,
            textAlign = TextAlign.Center,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(16.dp))
        WyuDialogButton(
            text = "确认预约",
            loading = booking,
            loadingText = "提交中…",
            onClick = {
                val value = phone.trim()
                if (!Regex("^1[3-9]\\d{9}$").matches(value)) {
                    phoneError = "请输入正确的 11 位手机号"
                } else {
                    phoneError = null
                    onConfirm(value)
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        WyuDialogCancelButton(
            onClick = { visible = false },
            enabled = !booking,
        )
    }
}

@Composable
private fun GymAuthRequiredScreen(
    ui: GymUiState,
    authLoggedIn: Boolean,
    onLogin: () -> Unit,
    onSsoLogin: () -> Unit,
    onBack: () -> Unit,
    authError: String? = null,
) {
    WyuPageScaffold(
        progress = { 0f },
        maskCollapseHeight = WyuLargeTitleHeight,
        topBar = {
            WyuActionBar(
                title = "体育馆预约",
                collapsedFraction = { 0f },
                navigationIcon = {
                    WyuTopBarIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        onClick = onBack,
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MiuixTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.School,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(36.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = "需要登录统一身份认证",
                style = MiuixTheme.textStyles.title2,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "体育馆预约已接入学校统一身份认证，登录后会自动建立预约系统会话。",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
            )

            if (ui.loggingIn) {
                Spacer(Modifier.height(28.dp))
                CircularProgressIndicator(
                    size = 28.dp,
                    strokeWidth = 3.dp,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "正在建立预约会话…",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            } else {
                // A silent login failure is the reason this screen exists, so
                // it has to say why instead of asking again out of nowhere.
                val loginError = ui.loginError ?: authError
                if (loginError != null) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = loginError,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
                val reuseSession = authLoggedIn && !ui.loginExpired
                Spacer(Modifier.height(28.dp))
                Button(
                    onClick = if (reuseSession) onSsoLogin else onLogin,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(
                        text = when {
                            reuseSession -> "使用当前账号继续"
                            authLoggedIn -> "重新登录统一身份认证"
                            else -> "登录统一身份认证"
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (reuseSession) {
                    TextButton(text = "使用其他账号登录", onClick = onLogin)
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = "登录信息仅保存在本机；若在登录页勾选记住密码，密码会加密保存在本机。",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun GymLoadingScreen(onBack: () -> Unit) {
    WyuPageScaffold(
        progress = { 0f },
        maskCollapseHeight = WyuLargeTitleHeight,
        topBar = {
            WyuActionBar(
                title = "体育馆预约",
                collapsedFraction = { 0f },
                navigationIcon = {
                    WyuTopBarIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        onClick = onBack,
                    )
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    }
}

private fun datePrimaryLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "今天"
        today.plusDays(1) -> "明天"
        else -> when (date.dayOfWeek.value) {
            1 -> "周一"
            2 -> "周二"
            3 -> "周三"
            4 -> "周四"
            5 -> "周五"
            6 -> "周六"
            else -> "周日"
        }
    }
}
