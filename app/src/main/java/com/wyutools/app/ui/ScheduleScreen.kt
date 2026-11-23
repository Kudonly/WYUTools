package com.wyutools.app.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wyutools.app.auth.AuthViewModel
import com.wyutools.app.schedule.ScheduleCourse
import com.wyutools.app.schedule.ScheduleData
import com.wyutools.app.schedule.ScheduleNotice
import com.wyutools.app.schedule.ScheduleSection
import com.wyutools.app.schedule.ScheduleSections
import com.wyutools.app.schedule.ScheduleUiState
import com.wyutools.app.schedule.ScheduleViewModel
import com.wyutools.app.schedule.WeekAnchor
import com.wyutools.app.schedule.scheduleWeekdayLabels
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// The bar never expands here, so the date capsule hangs 8dp under it and the
// grid starts right below the capsule.
private val ScheduleDateCapsuleTop = 56.dp + 8.dp
private val ScheduleDateCapsuleHeight = 40.dp
// A capsule: half the height, so both ends are perfect semicircles.
private val ScheduleDateCapsuleCorner = 20.dp
// Distance from the capsule's bottom edge to the first course row.
private val ScheduleDateCapsuleGap = 10.dp
// The inset handed to the content already carries the status bar and the
// collapsed bar (56dp); only the capsule band is left to add.
private val ScheduleGridTop =
    ScheduleDateCapsuleTop + ScheduleDateCapsuleHeight + ScheduleDateCapsuleGap
private val ScheduleWeeklyTopSpacer = ScheduleGridTop
// The slider sits on a live glass strip that samples the page content, with the
// 返回本周 pill beside it. Only this band plus the nav-bar inset has to stay
// clear under the grid.
private val ScheduleWeekBarHeight = 30.dp
// The bar now sits just above the gesture area: the navigation-bar inset is
// added on top of this gap.
private val ScheduleWeekBarGap = 3.dp
// Narrower than the grid on purpose, so the slider and its pill read as one
// floating control instead of another row of the timetable. The glass strip
// spans exactly this width, so this is also what keeps it from reading as a bar.
private val ScheduleWeekBarSideInset = 40.dp
// 玻璃条左右铺满滑条，只在上下各收 4dp：竖着薄一点，读作「滑条的底」，
// 又不会像之前那样两端比滑条短一截。圆角按收窄后的高度取半径。
private val ScheduleWeekBarGlassInset = 4.dp
// Fixed pill size: 本周 and 返回本周 are different lengths, and a control that
// resizes under the finger reads as a layout jump.
private val ScheduleWeekPillWidth = 86.dp
// Bar band plus breathing room; the navigation-bar inset is added by the tab.
private val ScheduleBottomSpacer = ScheduleWeekBarHeight + 10.dp
// The time axis uses a tighter corner than the app's 28dp cards: it sits flush
// against the grid and a big radius read as a pill.
private val ScheduleCardCorner = 16.dp
// The grid runs edge to edge apart from this gutter, and the capsule pads its
// own content by the same amount so dates stay aligned with the columns.
private val SchedulePageGutter = 10.dp
private val ScheduleWeekRowHeight = 64.dp
private val ScheduleAxisWidth = 42.dp
private const val CourseBlurRadius = 26f
internal const val ScheduleEmptyText = "好好休息吧！( ˘ω˘ )"

private val LightCourseColors = listOf(
    Color(0xFFDCE9FF) to Color(0xFF173A6B),
    Color(0xFFD9F2DE) to Color(0xFF14522A),
    Color(0xFFFFE8C7) to Color(0xFF7A3D00),
    Color(0xFFEADFFF) to Color(0xFF4A2578),
    Color(0xFFD2F2EE) to Color(0xFF0D4F47),
    Color(0xFFFFDEE8) to Color(0xFF7A1F3D),
)

private val DarkCourseColors = listOf(
    Color(0xFF233A5C) to Color(0xFFD6E6FF),
    Color(0xFF1E4431) to Color(0xFFC7F0D3),
    Color(0xFF4A3417) to Color(0xFFFFDFA8),
    Color(0xFF3A2A52) to Color(0xFFEAD9FF),
    Color(0xFF17453F) to Color(0xFFC6F3E9),
    Color(0xFF4B2333) to Color(0xFFFFD6E2),
)

/**
 * 课程色条。底色（LightCourseColors/DarkCourseColors）是给整块课程卡用的淡色，
 * 单独抽一条 4dp 的竖线时太不明显，所以另配一组饱和色：索引与 courseColors
 * 用同一套哈希，同一门课在课表和今日页里颜色保持一致。
 */
private val CourseStripeColors = listOf(
    Color(0xFF4F78E6),
    Color(0xFF34A853),
    Color(0xFFF5A623),
    Color(0xFF9B59D0),
    Color(0xFF17A2A2),
    Color(0xFFE2566F),
)

internal fun courseStripe(course: ScheduleCourse): Color =
    CourseStripeColors[abs(course.name.hashCode()) % CourseStripeColors.size]

internal data class SelectedCourse(
    val course: ScheduleCourse,
    val date: LocalDate,
    val week: Int,
)

@Composable
fun ScheduleScreen(
    vm: ScheduleViewModel,
    authVm: AuthViewModel,
    onLogin: () -> Unit,
    onBack: () -> Unit,
) {
    val ui by vm.ui.collectAsState()
    val authUi by authVm.ui.collectAsState()

    LaunchedEffect(Unit) { vm.onEnter() }
    LaunchedEffect(authUi.loggedIn, authUi.restoring) {
        if (!authUi.restoring && authUi.loggedIn) vm.onAuthReady()
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.syncCurrentWeek()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val data = ui.data
    when {
        // Cached timetable wins over everything: a missing session must never
        // replace data that is already on this device with a login prompt.
        data != null -> ScheduleContent(
            vm = vm,
            ui = ui,
            data = data,
            authLoggedIn = authUi.loggedIn,
            onBack = onBack,
            onLogin = onLogin,
        )

        // A silent re-login is still running; ask for a login only once it has
        // actually failed.
        authUi.restoring || authUi.autoLoggingIn || authUi.loggingOut ->
            ScheduleLoadingScreen(onBack = onBack)

        !authUi.loggedIn -> ScheduleLoginScreen(
            error = ui.error,
            onLogin = onLogin,
            onBack = onBack,
        )

        ui.loading || ui.error == null -> ScheduleLoadingScreen(onBack = onBack)

        else -> ScheduleErrorScreen(
            message = ui.error ?: "课表数据加载失败，请稍后重试。",
            onRetry = { vm.refresh(showNotice = true) },
            onBack = onBack,
        )
    }
}

@Composable
private fun ScheduleContent(
    vm: ScheduleViewModel,
    ui: ScheduleUiState,
    data: ScheduleData,
    authLoggedIn: Boolean,
    onBack: () -> Unit,
    onLogin: () -> Unit,
) {
    val weeklyScrollState = rememberScrollState()
    val context = LocalContext.current
    var showSettings by remember { mutableStateOf(false) }
    var selectedCourse by remember { mutableStateOf<SelectedCourse?>(null) }
    var showCourseDetail by remember { mutableStateOf(false) }
    var showWeekendHint by remember { mutableStateOf(vm.shouldShowWeekendHint()) }
    val now = rememberCurrentMinute()
    val today = now.toLocalDate()
    val currentWeek = data.currentWeek(ui.weekAnchor, today)
    val maxWeek = data.maxWeek.coerceAtLeast(1)
    // One horizontal page per week: the pager owns the drag, the fling and the
    // settle, so swipes chain without waiting for anything.
    val pagerState = rememberPagerState(
        initialPage = (ui.selectedWeek - 1).coerceIn(0, maxWeek - 1),
    ) { maxWeek }
    val liveSelectedWeek = rememberUpdatedState(ui.selectedWeek)
    LaunchedEffect(pagerState, maxWeek) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            val target = (page + 1).coerceIn(1, maxWeek)
            if (target != liveSelectedWeek.value) vm.selectWeek(target)
        }
    }
    // Slider / 返回本周 jump the pager without an animation: the timetable is
    // not what is being dragged, so snapping keeps the two in step.
    LaunchedEffect(ui.selectedWeek, maxWeek) {
        val target = (ui.selectedWeek - 1).coerceIn(0, maxWeek - 1)
        if (pagerState.currentPage != target) pagerState.scrollToPage(target)
    }

    LaunchedEffect(ui.notice) {
        val notice: ScheduleNotice = ui.notice ?: return@LaunchedEffect
        Toast.makeText(context, notice.message, Toast.LENGTH_SHORT).show()
        vm.consumeNotice()
    }

    WyuPageScaffold(
        // Collapsed bar, no large title and no progressive blur: the timetable
        // owns the whole screen.
        progress = { 0f },
        topBarBlur = false,
        overlay = {
            ScheduleOverlay(
                data = data,
                ui = ui,
                today = today,
                currentWeek = currentWeek,
                pagerState = pagerState,
                onSelectWeek = vm::selectWeek,
            )
        },
        topBar = {
            WyuActionBar(
                title = "课表",
                collapsedFraction = { 1f },
                largeTitle = false,
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
                        label = "刷新课表",
                        onClick = {
                            if (authLoggedIn) vm.refresh(showNotice = true) else onLogin()
                        },
                        enabled = !ui.refreshing,
                    )
                    WyuTopBarAction(
                        icon = Icons.Outlined.Settings,
                        label = "课表设置",
                        onClick = { showSettings = true },
                    )
                },
            )
        },
    ) { padding ->
        WeeklyTab(
            data = data,
            ui = ui,
            today = today,
            scrollState = weeklyScrollState,
            topInset = padding.calculateTopPadding() - 56.dp,
            bottomInset = WindowInsets.navigationBars
                .asPaddingValues()
                .calculateBottomPadding(),
            pagerState = pagerState,
            onCourseClick = { course, date ->
                selectedCourse = SelectedCourse(course, date, ui.selectedWeek)
                showCourseDetail = true
            },
        )
    }

    ScheduleSettingsDialog(
        show = showSettings,
        data = data,
        ui = ui,
        currentWeek = currentWeek,
        onToggleWeekend = vm::setShowWeekend,
        onCalibrateWeek = vm::calibrateWeek,
        onDismiss = { showSettings = false },
    )

    selectedCourse?.let { selection ->
        ScheduleCourseDialog(
            selection = selection,
            show = showCourseDetail,
            termName = data.termName,
            onDismiss = { showCourseDetail = false },
        )
    }

    WeekendHintDialog(
        show = showWeekendHint,
        onDismiss = {
            showWeekendHint = false
            vm.dismissWeekendHint()
        },
    )
}

@Composable
private fun ScheduleOverlay(
    data: ScheduleData,
    ui: ScheduleUiState,
    today: LocalDate,
    currentWeek: Int,
    pagerState: PagerState,
    onSelectWeek: (Int) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val morph = WyuMorphPageState.contentProgress
                val scale = 1f - WyuMorphPageState.ContentShrink * (1f - morph)
                scaleX = scale
                scaleY = scale
                translationY = (1f - morph) * WyuMorphPageState.ContentLift.toPx()
            },
    ) {
        // The dates get their own capsule right under the bar: they stay put
        // while the grid scrolls under them, and the bar keeps no title.
        ScheduleDateCapsule(
            data = data,
            ui = ui,
            today = today,
            currentWeek = currentWeek,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                // 6dp of capsule margin + 4dp inside it put the dates on the
                // same 10dp gutter the grid uses.
                .padding(start = 6.dp, end = 6.dp)
                .padding(top = rememberWyuStatusBarTopInset() + ScheduleDateCapsuleTop),
        )
        // The slider owns the bottom edge on a live glass strip, with the way
        // back to the current week as a pill beside it.
        ScheduleWeekBar(
            pagerState = pagerState,
            selectedWeek = ui.selectedWeek,
            maxWeek = data.maxWeek,
            currentWeek = currentWeek,
            onSelectWeek = onSelectWeek,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(
                    start = ScheduleWeekBarSideInset,
                    end = ScheduleWeekBarSideInset,
                    bottom = ScheduleWeekBarGap,
                )
                .navigationBarsPadding(),
        )
    }
}

@Composable
private fun ScheduleDateCapsule(
    data: ScheduleData,
    ui: ScheduleUiState,
    today: LocalDate,
    currentWeek: Int,
    modifier: Modifier = Modifier,
) {
    val selectedWeek = ui.selectedWeek
    val monday = data.mondayOfWeek(ui.weekAnchor, selectedWeek)
    val dayCount = if (ui.showWeekend) 7 else 5
    Card(
        modifier = modifier.height(ScheduleDateCapsuleHeight),
        cornerRadius = ScheduleDateCapsuleCorner,
        insideMargin = PaddingValues(start = 4.dp, end = 4.dp),
        samplePageContent = true,
        // 40dp tall: card-sized rim values would swallow the whole band and
        // leave the top edge unfilled.
        compactGlass = true,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScheduleWeekLabel(week = selectedWeek, isCurrentWeek = selectedWeek == currentWeek)
            for (day in 1..dayCount) {
                ScheduleWeekdayHeaderCell(
                    day = day,
                    date = monday.plusDays((day - 1).toLong()),
                    today = today,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Week number sitting in the axis column, so the dates stay over the columns
 * they belong to. The jump back to the current week is the glass pill next to
 * the slider, not this label.
 */
@Composable
private fun ScheduleWeekLabel(week: Int, isCurrentWeek: Boolean) {
    Box(
        modifier = Modifier.width(ScheduleAxisWidth),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "${week}周",
            style = TextStyle(fontSize = 12.sp, lineHeight = 15.sp),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            color = if (isCurrentWeek) {
                MiuixTheme.colorScheme.primary
            } else {
                MiuixTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun ScheduleWeekdayHeaderCell(
    day: Int,
    date: LocalDate,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    val isToday = date == today
    val primary = MiuixTheme.colorScheme.primary
    val secondary = MiuixTheme.colorScheme.onSurfaceVariantSummary
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = scheduleWeekdayLabels[day - 1],
            style = TextStyle(fontSize = 11.sp, lineHeight = 14.sp),
            fontWeight = FontWeight.Medium,
            color = if (isToday) primary else secondary,
        )
        Text(
            text = "${date.monthValue}/${date.dayOfMonth}",
            style = TextStyle(fontSize = 9.sp, lineHeight = 11.sp),
            color = if (isToday) primary else secondary.copy(alpha = 0.82f),
        )
    }
}

/**
 * The slider and the way back to the current week, sitting on the page itself.
 * Both are glass: the slider strip samples the live grid behind it, the pill
 * uses the same material as the top-bar buttons.
 */
@Composable
private fun ScheduleWeekBar(
    pagerState: PagerState,
    selectedWeek: Int,
    maxWeek: Int,
    currentWeek: Int,
    onSelectWeek: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCurrent = selectedWeek == currentWeek
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScheduleWeekSlider(
            pagerState = pagerState,
            selectedWeek = selectedWeek,
            maxWeek = maxWeek,
            onSelectWeek = onSelectWeek,
            // 右端收进一点：玻璃条与「返回本周」胶囊之间留出明确空隙，
            // 两种玻璃材质贴在一起会读成一条带子。
            modifier = Modifier.weight(1f).padding(end = 10.dp),
        )
        Spacer(Modifier.width(8.dp))
        WyuGlassPillButton(
            text = if (isCurrent) "本周" else "返回本周",
            contentDescription = if (isCurrent) "当前是本周" else "返回本周",
            enabled = !isCurrent,
            onClick = { onSelectWeek(currentWeek) },
            // Same width in both states, so the slider never shifts when the
            // week changes.
            width = ScheduleWeekPillWidth,
        )
    }
}

/**
 * The week slider tracks the pager while a page swipe is in flight, so the thumb
 * moves with the finger instead of waiting for the settle animation. It lives in
 * its own composable because that read happens every frame of a drag.
 */
@Composable
private fun ScheduleWeekSlider(
    pagerState: PagerState,
    selectedWeek: Int,
    maxWeek: Int,
    onSelectWeek: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val lastHapticWeek = remember { mutableIntStateOf(selectedWeek) }
    LaunchedEffect(selectedWeek) { lastHapticWeek.intValue = selectedWeek }
    val live = (pagerState.currentPage + pagerState.currentPageOffsetFraction + 1f)
        .coerceIn(1f, maxWeek.toFloat())
    // The track sits on a live glass strip: the grid keeps scrolling underneath,
    // so the band has to sample the page content instead of the wallpaper.
    // 玻璃条左右铺满、上下各收一点，滑条铺满并盖在上面。
    Box(modifier = modifier.height(ScheduleWeekBarHeight)) {
        WyuGlassCapsule(
            modifier = Modifier
                .matchParentSize()
                .padding(vertical = ScheduleWeekBarGlassInset),
            cornerRadius = (ScheduleWeekBarHeight - ScheduleWeekBarGlassInset * 2) / 2,
        ) {}
        // 自绘滑条：miuix Slider 在拖动时会硬编码叠一层 4.4% 的黑色条形高亮，
        // 无法通过 colors 关闭，这里用 Canvas 复刻「填充 + 圆形拇指」的视觉。
        var dragFraction by remember { mutableStateOf<Float?>(null) }
        var trackWidthPx by remember { mutableStateOf(0) } 
        val updateFraction: (Float, Boolean) -> Unit = { fraction, haptic ->
            dragFraction = fraction
            val week = (fraction * (maxWeek - 1)).roundToInt() + 1
            if (week != lastHapticWeek.intValue) {
                lastHapticWeek.intValue = week
                if (haptic) {
                    view.performHapticFeedback(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            HapticFeedbackConstants.SEGMENT_TICK
                        } else {
                            HapticFeedbackConstants.CLOCK_TICK
                        },
                    )
                }
            }
            onSelectWeek(week)
        }
        val liveFraction =
            ((pagerState.currentPage + pagerState.currentPageOffsetFraction + 1f) - 1f) /
                (maxWeek - 1).coerceAtLeast(1)
        val fraction = dragFraction
            ?: if (pagerState.isScrollInProgress) liveFraction
            else (selectedWeek - 1f) / (maxWeek - 1).coerceAtLeast(1)
        val filledColor = MiuixTheme.colorScheme.primary
        val thumbPaint = MiuixTheme.colorScheme.onPrimary
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { trackWidthPx = it.width }
                .pointerInput(maxWeek) {
                    detectTapGestures { pos ->
                        if (trackWidthPx > 0) {
                            updateFraction((pos.x / trackWidthPx).coerceIn(0f, 1f), false)
                        }
                    }
                }
                .pointerInput(maxWeek) {
                    detectHorizontalDragGestures(
                        onDragStart = { pos ->
                            if (trackWidthPx > 0) {
                                updateFraction((pos.x / trackWidthPx).coerceIn(0f, 1f), true)
                            }
                        },
                        onDragEnd = { dragFraction = null },
                        onDragCancel = { dragFraction = null },
                    ) { change, _ ->
                        change.consume()
                        if (trackWidthPx > 0) {
                            updateFraction((change.position.x / trackWidthPx).coerceIn(0f, 1f), true)
                        }
                    }
                },
        ) {
            val barHeight = size.height
            val centerX = fraction * size.width
            drawLine(
                color = filledColor,
                start = Offset(0f, barHeight / 2f),
                end = Offset(centerX, barHeight / 2f),
                strokeWidth = barHeight,
                cap = StrokeCap.Round,
            )
            drawCircle(
                color = thumbPaint,
                radius = barHeight / 2f * 0.72f,
                center = Offset(centerX, barHeight / 2f),
            )
        }
    }
}

@Composable
private fun WeeklyTab(
    data: ScheduleData,
    ui: ScheduleUiState,
    today: LocalDate,
    scrollState: androidx.compose.foundation.ScrollState,
    topInset: androidx.compose.ui.unit.Dp,
    bottomInset: androidx.compose.ui.unit.Dp,
    pagerState: PagerState,
    onCourseClick: (ScheduleCourse, LocalDate) -> Unit,
) {
    val sectionCount = data.sections.size.coerceAtLeast(1)
    val totalHeight = ScheduleWeekRowHeight * sectionCount
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
    ) {
        Spacer(Modifier.height(topInset + ScheduleWeeklyTopSpacer))
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(SchedulePageGutter))
            // The axis is outside the pager: it is the one column that must not
            // move while the weeks slide past it.
            ScheduleAxisCard(
                sections = data.sections,
                totalHeight = totalHeight,
            )
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .height(totalHeight),
            ) { page ->
                ScheduleWeekColumns(
                    data = data,
                    week = page + 1,
                    monday = data.mondayOfWeek(ui.weekAnchor, page + 1),
                    showWeekend = ui.showWeekend,
                    today = today,
                    onCourseClick = onCourseClick,
                    // The page runs to the screen edge so a swipe is never cut
                    // off by the page gutter; the columns keep the gutter.
                    modifier = Modifier.padding(end = SchedulePageGutter),
                )
            }
        }
        // The week header is drawn over the bottom of the page, so the grid has
        // to be able to scroll clear of it: card + gaps + navigation bar.
        Spacer(Modifier.height(bottomInset + ScheduleBottomSpacer))
    }
}

/** The frozen time column: a card, so it carries the same material as every
 *  other card instead of a tint of its own. */
@Composable
private fun ScheduleAxisCard(
    sections: List<ScheduleSection>,
    totalHeight: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier = Modifier
            .width(ScheduleAxisWidth)
            .height(totalHeight)
            .padding(horizontal = 1.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxSize(),
            cornerRadius = ScheduleCardCorner,
            insideMargin = PaddingValues(0.dp),
        ) {
            WeekTimeAxis(
                sections = sections,
                totalHeight = totalHeight,
            )
        }
    }
}

@Composable
private fun ScheduleWeekColumns(
    data: ScheduleData,
    week: Int,
    monday: LocalDate,
    showWeekend: Boolean,
    today: LocalDate,
    onCourseClick: (ScheduleCourse, LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dayCount = if (showWeekend) 7 else 5
    val sectionCount = data.sections.size.coerceAtLeast(1)
    val totalHeight = ScheduleWeekRowHeight * sectionCount
    val gridColor = MiuixTheme.colorScheme.outline.copy(alpha = 0.16f)
    // The today marker must not reuse a course-palette colour: a neutral wash
    // plus a primary outline keeps it distinct from every pastel course block.
    val todayTint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.045f)
    val todayOutline = MiuixTheme.colorScheme.primary.copy(alpha = 0.45f)

    // No container: the grid is drawn straight on the page and each course
    // block brings its own glass, so the wallpaper keeps showing between the
    // cells instead of sitting behind one big panel.
    Row(
        modifier
            .fillMaxWidth()
            .height(totalHeight),
    ) {
        for (day in 1..dayCount) {
            val date = monday.plusDays((day - 1).toLong())
            WeekDayColumn(
                courses = data.coursesOn(day, week),
                week = week,
                sectionCount = sectionCount,
                totalHeight = totalHeight,
                isToday = date == today,
                isLastDay = day == dayCount,
                gridColor = gridColor,
                todayTint = todayTint,
                todayOutline = todayOutline,
                modifier = Modifier.weight(1f),
                onCourseClick = { course -> onCourseClick(course, date) },
            )
        }
    }
}

@Composable
private fun WeekDayColumn(
    courses: List<ScheduleCourse>,
    week: Int,
    sectionCount: Int,
    totalHeight: androidx.compose.ui.unit.Dp,
    isToday: Boolean,
    isLastDay: Boolean,
    gridColor: Color,
    todayTint: Color,
    todayOutline: Color,
    modifier: Modifier = Modifier,
    onCourseClick: (ScheduleCourse) -> Unit,
) {
    val rowHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        ScheduleWeekRowHeight.toPx()
    }
    val blockShape = remember { RoundedCornerShape(11.dp) }
    Box(
        modifier = modifier
            .height(totalHeight)
            .drawBehind {
                if (isToday) {
                    val corner = CornerRadius(12.dp.toPx())
                    drawRoundRect(color = todayTint, cornerRadius = corner)
                    drawRoundRect(
                        color = todayOutline,
                        cornerRadius = corner,
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
                val stroke = 0.5.dp.toPx()
                for (index in 1 until sectionCount) {
                    val y = rowHeightPx * index
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = stroke,
                    )
                }
                // The last column ends at the screen edge; a rule there would
                // read as a border around the wallpaper.
                if (!isLastDay) {
                    drawLine(
                        color = gridColor,
                        start = Offset(size.width - stroke / 2f, 0f),
                        end = Offset(size.width - stroke / 2f, size.height),
                        strokeWidth = stroke,
                    )
                }
            },
    ) {
        for (course in courses) {
            val start = course.startSection.coerceIn(1, sectionCount)
            val end = course.endSection.coerceIn(start, sectionCount)
            val span = end - start + 1
            val (background, content) = courseColors(course)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = ScheduleWeekRowHeight * (start - 1) + 2.dp)
                    .height(ScheduleWeekRowHeight * span - 4.dp)
                    .padding(horizontal = 2.dp)
                    .clip(blockShape)
                    .courseGlass(blockShape, background)
                    .clickable { onCourseClick(course) }
                    .padding(horizontal = 4.dp, vertical = 3.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = course.name,
                    style = TextStyle(fontSize = 10.sp, lineHeight = 12.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = content,
                    maxLines = if (span >= 2) 4 else 2,
                    overflow = TextOverflow.Clip,
                )
                if (course.roomForWeek(week).isNotBlank()) {
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = course.roomForWeek(week),
                        style = TextStyle(fontSize = 8.sp),
                        color = content.copy(alpha = 0.82f),
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = 5.sp,
                            maxFontSize = 8.sp,
                            stepSize = 0.5.sp,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekTimeAxis(
    sections: List<ScheduleSection>,
    totalHeight: androidx.compose.ui.unit.Dp,
) {
    val secondary = MiuixTheme.colorScheme.onSurfaceVariantSummary
    // No row rules: they would cut across the card's rounded ends.
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        sections.forEach { section ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ScheduleWeekRowHeight)
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = section.number.toString(),
                    style = TextStyle(fontSize = 10.sp, lineHeight = 11.sp),
                    fontWeight = FontWeight.Medium,
                    color = secondary,
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = section.start,
                    style = TextStyle(fontSize = 8.sp, lineHeight = 9.sp),
                    color = secondary.copy(alpha = 0.78f),
                )
                Text(
                    text = section.end,
                    style = TextStyle(fontSize = 8.sp, lineHeight = 9.sp),
                    color = secondary.copy(alpha = 0.78f),
                )
            }
        }
    }
}

/**
 * The course block's own glass: the wallpaper's pre-blurred copy sits behind
 * the tint, so the block frosts whatever the page is showing without needing a
 * card container. Soft glass uses its underlay, plain mode the Gaussian one;
 * with a stock (gradient) wallpaper there is no bitmap to sample and only the
 * tint remains.
 */
@Composable
private fun Modifier.courseGlass(shape: Shape, tint: Color): Modifier {
    val softGlass by WyuAppearance.softGlass.collectAsState()
    val softGlassBlur by WyuAppearance.softGlassBlur.collectAsState()
    val tuning by WyuAppearance.softGlassTuning.collectAsState()
    val liquidTuning by WyuAppearance.liquidGlassTuning.collectAsState()
    val liquidBlur by WyuAppearance.liquidGlassBlur.collectAsState()
    val underlay = LocalWyuGlassUnderlay.current
    val gaussian = LocalWyuGaussianUnderlay.current
    val bakedBackdrop = LocalWyuBakedBackdrop.current
    val bakedUnderlayState = LocalWyuBakedUnderlayState.current
    val wallpaper = LocalWyuGlassBackdrop.current
    val dark = LocalWyuDarkTheme.current
    val currentCardTheme by WyuAppearance.cardTheme.collectAsState()
    val isLiquid = currentCardTheme == WyuCardTheme.Liquid
    val blur = when {
        // 课程块很小，液态玻璃用收窄的边缘参数，避免 rim 吃掉整块高度。
        // 烘焙底图可用时直接采样静态位图，与液态玻璃卡片的烘焙层一致。
        isLiquid && softGlass && wyuSoftGlassSupported && underlay != null ->
            wyuLiquidGlass(
                backdrop = null,
                underlay = underlay,
                shape = shape,
                cornerRadius = 11.dp,
                tuning = liquidTuning,
                dark = dark,
                baseTint = Color.Transparent,
                shapeEdgePx = 40f,
                shapeEdgePow = 2.2f,
                refractionThicknessPx = 56f,
                reflectionThicknessPx = 800f,
            )
        isLiquid && softGlass && wyuSoftGlassSupported && wallpaper != null ->
            wyuLiquidGlass(
                backdrop = wallpaper,
                shape = shape,
                cornerRadius = 11.dp,
                tuning = liquidTuning,
                dark = dark,
                baseTint = Color.Transparent,
                blurPercent = liquidBlur,
                shapeEdgePx = 40f,
                shapeEdgePow = 2.2f,
                refractionThicknessPx = 56f,
                reflectionThicknessPx = 800f,
            )
        softGlass && !isLiquid && wyuSoftGlassSupported &&
            bakedBackdrop != null && bakedUnderlayState != null ->
            wyuUpstreamStaticCardGlass(
                backdrop = bakedBackdrop,
                underlayState = bakedUnderlayState,
                cornerRadius = 11.dp,
                tuning = tuning.resolvedFor(dark),
                dark = dark,
                blurPercent = softGlassBlur,
                tint = Color.Transparent,
            )
        softGlass && !isLiquid && wyuSoftGlassSupported && bakedBackdrop != null ->
            wyuUpstreamCardGlass(
                backdrop = bakedBackdrop,
                cornerRadius = 11.dp,
                tuning = tuning.resolvedFor(dark),
                dark = dark,
                blurPercent = softGlassBlur,
                tint = Color.Transparent
            )
        softGlass && !isLiquid && wyuSoftGlassSupported && wallpaper != null && LocalWyuGlassReady.current ->
            wyuUpstreamCardGlass(
                backdrop = wallpaper,
                cornerRadius = 11.dp,
                tuning = tuning.resolvedFor(dark),
                dark = dark,
                blurPercent = softGlassBlur,
                tint = Color.Transparent,
            )
        softGlass && underlay != null -> wyuWallpaperBlur(underlay, shape)
        !softGlass && gaussian != null -> wyuWallpaperBlur(gaussian, shape)
        wallpaper != null ->
            textureBlur(backdrop = wallpaper, shape = shape, blurRadius = CourseBlurRadius)
        else -> this
    }
    val rim = Color.White.copy(alpha = if (dark) 0.10f else 0.32f)
    return blur
        .background(tint.copy(alpha = if (dark) 0.70f else 0.68f), shape)
        .border(
            width = 0.7.dp,
            brush = Brush.verticalGradient(
                listOf(rim, rim.copy(alpha = rim.alpha * 0.2f)),
            ),
            shape = shape,
        )
}

@Composable
private fun courseColors(course: ScheduleCourse): Pair<Color, Color> {
    val palette = if (LocalWyuDarkTheme.current) DarkCourseColors else LightCourseColors
    val seed = course.name
    return palette[abs(seed.hashCode()) % palette.size]
}

@Composable
private fun WeekendHintDialog(show: Boolean, onDismiss: () -> Unit) {
    WyuOverlayDialog(
        show = show,
        title = "小提示",
        onDismissRequest = onDismiss,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = "周末课表",
                style = MiuixTheme.textStyles.headline2,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "右上角设置可以调节周末课表显示，默认不显示周六、周日课程。",
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(20.dp))
            WyuDialogButton(text = "知道了", onClick = onDismiss)
        }
    }
}

@Composable
internal fun ScheduleCourseDialog(
    selection: SelectedCourse,
    show: Boolean,
    termName: String,
    onDismiss: () -> Unit,
) {
    val course = selection.course
    WyuOverlayDialog(
        show = show,
        title = "课程详情",
        onDismissRequest = onDismiss,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = course.name,
                style = MiuixTheme.textStyles.headline1,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${weekdayName(selection.date)} · " +
                    "${selection.date.monthValue}月${selection.date.dayOfMonth}日 · " +
                    "第 ${selection.week} 周",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(14.dp))
            CourseDetailRow(label = "时间", value = courseTimeText(course))
            CourseDetailDivider()
            CourseDetailRow(
                label = "地点",
                value = course.roomSummary().ifBlank { "未填写" },
            )
            CourseDetailDivider()
            CourseDetailRow(label = "教师", value = course.teacher.ifBlank { "未填写" })
            if (course.className.isNotBlank()) {
                CourseDetailDivider()
                CourseDetailRow(label = "班级", value = course.className)
            }
            CourseDetailDivider()
            CourseDetailRow(label = "周次", value = formatCourseWeeks(course.weeks))
            CourseDetailDivider()
            CourseDetailRow(label = "学期", value = termName)
            Spacer(Modifier.height(20.dp))
            WyuDialogButton(text = "知道了", onClick = onDismiss)
        }
    }
}

@Composable
private fun CourseDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier
                .width(42.dp)
                .padding(top = 2.dp),
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun CourseDetailDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(MiuixTheme.colorScheme.outline.copy(alpha = 0.16f)),
    )
}

internal fun courseTimeText(course: ScheduleCourse): String {
    val start = course.startTime.ifBlank {
        ScheduleSections.defaultStart(course.startSection).orEmpty()
    }
    val end = course.endTime.ifBlank {
        ScheduleSections.defaultEnd(course.endSection).orEmpty()
    }
    val sections = if (course.startSection == course.endSection) {
        "第 ${course.startSection} 节"
    } else {
        "第 ${course.startSection}-${course.endSection} 节"
    }
    return if (start.isBlank() && end.isBlank()) {
        sections
    } else {
        "$sections\n${start.ifBlank { "—" }} – ${end.ifBlank { "—" }}"
    }
}

internal fun formatCourseWeeks(weeks: Set<Int>): String {
    val sorted = weeks.sorted()
    if (sorted.isEmpty()) return "未填写"
    val arithmeticByTwo = sorted.size >= 3 &&
        (sorted.first()..sorted.last() step 2).toList() == sorted
    if (arithmeticByTwo) {
        val parity = if (sorted.first() % 2 == 1) "单周" else "双周"
        return "${sorted.first()}-${sorted.last()}$parity"
    }
    val ranges = mutableListOf<IntRange>()
    var rangeStart = sorted.first()
    var previous = rangeStart
    for (week in sorted.drop(1)) {
        if (week == previous + 1) {
            previous = week
        } else {
            ranges += rangeStart..previous
            rangeStart = week
            previous = week
        }
    }
    ranges += rangeStart..previous
    val label = ranges.joinToString("、") { range ->
        if (range.first == range.last) "${range.first}" else "${range.first}-${range.last}"
    }
    return "${label}周"
}

@Composable
private fun ScheduleSettingsDialog(
    show: Boolean,
    data: ScheduleData,
    ui: ScheduleUiState,
    currentWeek: Int,
    onToggleWeekend: (Boolean) -> Unit,
    onCalibrateWeek: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    WyuOverlayDialog(
        show = show,
        title = "课表设置",
        onDismissRequest = onDismiss,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "显示周六日",
                    style = MiuixTheme.textStyles.headline2,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "关闭时只显示周一到周五",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Spacer(Modifier.width(16.dp))
            Switch(
                checked = ui.showWeekend,
                onCheckedChange = onToggleWeekend,
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(MiuixTheme.colorScheme.outline.copy(alpha = 0.24f)),
        )
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "当前周次",
                    style = MiuixTheme.textStyles.headline2,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "把本周设为第 $currentWeek 周",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            WeekStepperButton(
                text = "−",
                enabled = currentWeek > 1,
                onClick = { onCalibrateWeek(currentWeek - 1) },
            )
            Text(
                text = currentWeek.toString(),
                style = MiuixTheme.textStyles.headline2,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(40.dp),
            )
            WeekStepperButton(
                text = "+",
                enabled = currentWeek < data.maxWeek,
                onClick = { onCalibrateWeek(currentWeek + 1) },
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "教务系统不返回当前周次，日期与今日课程会按这里的设置推算。",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "学期：${data.termName}",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Text(
            text = "更新于 ${formatUpdatedAt(data.fetchedAt)}",
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(18.dp))
        WyuDialogButton(
            text = "完成",
            onClick = onDismiss,
        )
    }
}

@Composable
private fun WeekStepperButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        minWidth = 36.dp,
        minHeight = 36.dp,
        cornerRadius = 18.dp,
        insideMargin = PaddingValues(0.dp),
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.headline2,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
internal fun ScheduleLoginScreen(
    error: String?,
    onLogin: () -> Unit,
    onBack: () -> Unit,
    title: String = "课表",
) {
    ScheduleStatePage(title = title, onBack = onBack) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MiuixTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.CalendarMonth,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = "登录后导入课表",
            style = MiuixTheme.textStyles.title2,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "登录校园统一身份认证后，会从教务系统读取本学期课表。",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
        )
        if (error != null) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = error,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(28.dp))
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Button(
                onClick = onLogin,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(
                    text = "登录统一身份认证",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = "课表只缓存在本机；不会上传到其他服务。",
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun ScheduleLoadingScreen(onBack: () -> Unit, title: String = "课表") {
    ScheduleStatePage(title = title, onBack = onBack) {
        CircularProgressIndicator()
    }
}

@Composable
internal fun ScheduleErrorScreen(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    title: String = "课表",
) {
    ScheduleStatePage(title = title, onBack = onBack) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MiuixTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.CloudOff,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(34.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = "课表加载失败",
            style = MiuixTheme.textStyles.title2,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            Text(
                text = "重试",
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
internal fun ScheduleStatePage(
    onBack: () -> Unit,
    title: String = "课表",
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    WyuPageScaffold(
        progress = { 0f },
        topBarBlur = false,
        topBar = {
            WyuActionBar(
                title = title,
                collapsedFraction = { 1f },
                largeTitle = false,
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
            content = content,
        )
    }
}

internal fun weekdayName(date: LocalDate): String = when (date.dayOfWeek.value) {
    1 -> "星期一"
    2 -> "星期二"
    3 -> "星期三"
    4 -> "星期四"
    5 -> "星期五"
    6 -> "星期六"
    else -> "星期日"
}

private fun formatUpdatedAt(timestamp: Long): String {
    if (timestamp <= 0L) return "—"
    return runCatching {
        Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.CHINA))
    }.getOrDefault("—")
}
