package com.wyutools.app.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.wyutools.app.schedule.scheduleWeekdayLabels
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Slider
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

private val ScheduleTabTitles = listOf("壹日", "邑周")
private val ScheduleTabsAreaHeight = 50.dp
private val ScheduleContentTopSpacer = WyuLargeTitleHeight + ScheduleTabsAreaHeight - 6.dp
private val ScheduleWeekHeaderHeight = 118.dp
private val ScheduleWeeklyTopSpacer =
    ScheduleContentTopSpacer + ScheduleWeekHeaderHeight + 16.dp
private val ScheduleWeekRowHeight = 64.dp
private val ScheduleAxisWidth = 42.dp
private val ScheduleWeekdayHeaderHeight = 36.dp
private const val ScheduleEmptyText = "好好休息吧！( ˘ω˘ )"

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

private data class SelectedCourse(
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
        data != null -> ScheduleContent(
            vm = vm,
            ui = ui,
            data = data,
            authLoggedIn = authUi.loggedIn,
            onBack = onBack,
            onLogin = onLogin,
        )

        authUi.restoring -> ScheduleLoadingScreen(onBack = onBack)

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
    val pagerState = rememberPagerState(pageCount = { 2 })
    val dailyListState = rememberLazyListState()
    val weeklyScrollState = rememberScrollState()
    val dailyFraction = rememberWyuCollapseFraction(dailyListState)
    val weeklyFraction = rememberWyuCollapseFraction(weeklyScrollState)
    val collapseFraction = if (pagerState.currentPage == 0) dailyFraction else weeklyFraction
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showSettings by remember { mutableStateOf(false) }
    var selectedCourse by remember { mutableStateOf<SelectedCourse?>(null) }
    var showCourseDetail by remember { mutableStateOf(false) }
    var showWeekendHint by remember { mutableStateOf(vm.shouldShowWeekendHint()) }
    val now = rememberCurrentMinute()
    val today = now.toLocalDate()
    val currentWeek = data.currentWeek(ui.weekAnchor, today)

    LaunchedEffect(ui.notice) {
        val notice: ScheduleNotice = ui.notice ?: return@LaunchedEffect
        Toast.makeText(context, notice.message, Toast.LENGTH_SHORT).show()
        vm.consumeNotice()
    }

    WyuPageScaffold(
        progress = { collapseFraction.value },
        maskCollapseHeight = WyuLargeTitleHeight,
        maskExtension = 34.dp,
        overlay = {
            ScheduleOverlay(
                pagerState = pagerState,
                collapsedFraction = collapseFraction,
                data = data,
                ui = ui,
                today = today,
                currentWeek = currentWeek,
                onSelectTab = { index ->
                    if (index != pagerState.currentPage) {
                        scope.launch {
                            pagerState.animateScrollToPage(
                                page = index,
                                animationSpec = tween(450, easing = WyuDecelerateEasing),
                            )
                        }
                    }
                },
                onSelectWeek = vm::selectWeek,
            )
        },
        topBar = {
            WyuActionBar(
                title = "课表",
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
        val listTopInset = padding.calculateTopPadding() - WyuLargeTitleHeight
        Box(Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
            ) { page ->
                if (page == 0) {
                    DailyTab(
                        data = data,
                        now = now,
                        today = today,
                        currentWeek = currentWeek,
                        listState = dailyListState,
                        topInset = listTopInset,
                        onCourseClick = { course ->
                            selectedCourse = SelectedCourse(course, today, currentWeek)
                            showCourseDetail = true
                        },
                    )
                } else {
                    WeeklyTab(
                        data = data,
                        ui = ui,
                        today = today,
                        scrollState = weeklyScrollState,
                        topInset = listTopInset,
                        onCourseClick = { course, date ->
                            selectedCourse = SelectedCourse(course, date, ui.selectedWeek)
                            showCourseDetail = true
                        },
                    )
                }
            }
        }
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
    pagerState: PagerState,
    collapsedFraction: State<Float>,
    data: ScheduleData,
    ui: ScheduleUiState,
    today: LocalDate,
    currentWeek: Int,
    onSelectTab: (Int) -> Unit,
    onSelectWeek: (Int) -> Unit,
) {
    val selectionFraction = remember(pagerState) {
        { pagerState.currentPage + pagerState.currentPageOffsetFraction }
    }
    val pageFraction = selectionFraction().coerceIn(0f, 1f)
    val showWeekHeader = pageFraction > 0.01f
    val boost = LocalWyuCollapseBoost.current
    Column(
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
            .padding(top = 62.dp, start = 16.dp, end = 16.dp)
            .graphicsLayer {
                val collapsed = (collapsedFraction.value + boost()).coerceIn(0f, 1f)
                translationY = WyuLargeTitleHeight.toPx() * (1f - collapsed)
            },
    ) {
        WyuFloatingTabs(
            options = ScheduleTabTitles,
            selectedIndex = pagerState.currentPage,
            onSelect = onSelectTab,
            selectionFraction = selectionFraction,
        )
        if (showWeekHeader) {
            Column(
                modifier = Modifier.graphicsLayer { alpha = pageFraction },
            ) {
                Spacer(Modifier.height(8.dp))
                ScheduleWeekHeader(
                    data = data,
                    ui = ui,
                    today = today,
                    currentWeek = currentWeek,
                    onSelectWeek = onSelectWeek,
                )
            }
        }
    }
}

@Composable
private fun ScheduleWeekHeader(
    data: ScheduleData,
    ui: ScheduleUiState,
    today: LocalDate,
    currentWeek: Int,
    onSelectWeek: (Int) -> Unit,
) {
    val selectedWeek = ui.selectedWeek
    val view = LocalView.current
    val lastHapticWeek = remember { mutableIntStateOf(selectedWeek) }
    LaunchedEffect(selectedWeek) { lastHapticWeek.intValue = selectedWeek }
    val monday = data.mondayOfWeek(ui.weekAnchor, selectedWeek)
    val dayCount = if (ui.showWeekend) 7 else 5
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(ScheduleWeekHeaderHeight),
        cornerRadius = 22.dp,
        insideMargin = PaddingValues(start = 10.dp, top = 10.dp, end = 12.dp, bottom = 10.dp),
        samplePageContent = true,
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "第 ${selectedWeek} 周",
                    style = MiuixTheme.textStyles.headline2,
                    fontWeight = FontWeight.SemiBold,
                )
                if (selectedWeek == currentWeek) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MiuixTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "本周",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (selectedWeek != currentWeek) {
                    Button(
                        onClick = { onSelectWeek(currentWeek) },
                        minWidth = 0.dp,
                        minHeight = 28.dp,
                        cornerRadius = 14.dp,
                        insideMargin = PaddingValues(horizontal = 10.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = "回到本周",
                            style = MiuixTheme.textStyles.footnote2,
                        )
                    }
                }
            }
            Slider(
                value = selectedWeek.toFloat(),
                onValueChange = { value ->
                    val week = value.roundToInt()
                    if (week != lastHapticWeek.intValue) {
                        lastHapticWeek.intValue = week
                        view.performHapticFeedback(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                HapticFeedbackConstants.SEGMENT_TICK
                            } else {
                                HapticFeedbackConstants.CLOCK_TICK
                            },
                        )
                    }
                    onSelectWeek(week)
                },
                valueRange = 1f..data.maxWeek.toFloat(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp),
            )
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ScheduleWeekdayHeaderHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.width(ScheduleAxisWidth))
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

@Composable
private fun DailyTab(
    data: ScheduleData,
    now: LocalDateTime,
    today: LocalDate,
    currentWeek: Int,
    listState: LazyListState,
    topInset: androidx.compose.ui.unit.Dp,
    onCourseClick: (ScheduleCourse) -> Unit,
) {
    val nowMinutes = now.hour * 60 + now.minute
    val dayCourses = remember(data, today, currentWeek) {
        data.coursesOn(today.dayOfWeek.value, currentWeek)
    }
    val courses = remember(dayCourses, nowMinutes) {
        dayCourses.filterNot { it.hasEndedAt(nowMinutes) }
    }
    val nextCourseId = if (dayCourses.none { it.isOngoingAt(nowMinutes) }) {
        courses.firstOrNull()?.id
    } else {
        null
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "schedule-daily-spacer") {
            Spacer(Modifier.height(topInset + ScheduleContentTopSpacer))
        }
        item(key = "schedule-daily-date") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
            ) {
                Text(
                    text = "${today.monthValue}月${today.dayOfMonth}日",
                    style = MiuixTheme.textStyles.headline1,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${today.year}年 · ${weekdayName(today)} · 第 $currentWeek 周",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        if (courses.isEmpty()) {
            item(key = "schedule-daily-empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillParentMaxHeight(0.56f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = ScheduleEmptyText,
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            items(courses, key = { it.id }) { course ->
                DailyCourseCard(
                    course = course,
                    week = currentWeek,
                    ongoing = course.isOngoingAt(nowMinutes),
                    upcoming = course.id == nextCourseId,
                    onClick = { onCourseClick(course) },
                )
            }
            item(key = "schedule-daily-tail") {
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun DailyCourseCard(
    course: ScheduleCourse,
    week: Int,
    ongoing: Boolean,
    upcoming: Boolean,
    onClick: () -> Unit,
) {
    val room = course.roomForWeek(week)
    val primary = MiuixTheme.colorScheme.primary
    val onPrimary = MiuixTheme.colorScheme.onPrimary
    val content = if (ongoing) onPrimary else MiuixTheme.colorScheme.onSurface
    val secondary = if (ongoing) {
        onPrimary.copy(alpha = 0.82f)
    } else {
        MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
    val statusLabel = when {
        ongoing -> "正在上课"
        upcoming -> "接下来"
        else -> null
    }
    val startTimeText = course.startTime.ifBlank {
        ScheduleSections.defaultStart(course.startSection).orEmpty()
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(20.dp),
        colors = if (ongoing) {
            CardDefaults.defaultColors(color = primary, contentColor = onPrimary)
        } else {
            CardDefaults.defaultColors()
        },
    ) {
        Column(Modifier.fillMaxWidth()) {
            if (statusLabel != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = statusLabel,
                        style = MiuixTheme.textStyles.footnote1,
                        fontWeight = FontWeight.Medium,
                        color = secondary,
                    )
                    if (upcoming && startTimeText.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = startTimeText,
                            style = MiuixTheme.textStyles.footnote1,
                            fontWeight = FontWeight.Medium,
                            color = secondary,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            Text(
                text = course.name,
                style = MiuixTheme.textStyles.headline2,
                fontWeight = FontWeight.SemiBold,
                color = content,
            )
            if (room.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = room,
                    style = MiuixTheme.textStyles.body2,
                    color = secondary,
                )
            }
            if (course.teacher.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = course.teacher,
                    style = MiuixTheme.textStyles.footnote1,
                    color = secondary,
                )
            }
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
    onCourseClick: (ScheduleCourse, LocalDate) -> Unit,
) {
    val monday = data.mondayOfWeek(ui.weekAnchor, ui.selectedWeek)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(topInset + ScheduleWeeklyTopSpacer))
        WeekGrid(
            data = data,
            selectedWeek = ui.selectedWeek,
            monday = monday,
            showWeekend = ui.showWeekend,
            today = today,
            onCourseClick = onCourseClick,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun WeekGrid(
    data: ScheduleData,
    selectedWeek: Int,
    monday: LocalDate,
    showWeekend: Boolean,
    today: LocalDate,
    onCourseClick: (ScheduleCourse, LocalDate) -> Unit,
) {
    val dayCount = if (showWeekend) 7 else 5
    val sectionCount = data.sections.size.coerceAtLeast(1)
    val totalHeight = ScheduleWeekRowHeight * sectionCount
    val gridColor = MiuixTheme.colorScheme.outline.copy(alpha = 0.18f)
    // The today marker must not reuse a course-palette colour: a neutral wash
    // plus a primary outline keeps it distinct from every pastel course block.
    val todayTint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    val todayOutline = MiuixTheme.colorScheme.primary.copy(alpha = 0.45f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 22.dp,
        insideMargin = PaddingValues(start = 10.dp, top = 8.dp, end = 12.dp, bottom = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            WeekTimeAxis(
                sections = data.sections,
                totalHeight = totalHeight,
                gridColor = gridColor,
            )
            for (day in 1..dayCount) {
                val date = monday.plusDays((day - 1).toLong())
                WeekDayColumn(
                    courses = data.coursesOn(day, selectedWeek),
                    week = selectedWeek,
                    sectionCount = sectionCount,
                    totalHeight = totalHeight,
                    isToday = date == today,
                    gridColor = gridColor,
                    todayTint = todayTint,
                    todayOutline = todayOutline,
                    modifier = Modifier.weight(1f),
                    onCourseClick = { course -> onCourseClick(course, date) },
                )
            }
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
    gridColor: Color,
    todayTint: Color,
    todayOutline: Color,
    modifier: Modifier = Modifier,
    onCourseClick: (ScheduleCourse) -> Unit,
) {
    val rowHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        ScheduleWeekRowHeight.toPx()
    }
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
                drawLine(
                    color = gridColor,
                    start = Offset(size.width - stroke / 2f, 0f),
                    end = Offset(size.width - stroke / 2f, size.height),
                    strokeWidth = stroke,
                )
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
                    .clip(RoundedCornerShape(10.dp))
                    .background(background)
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
    gridColor: Color,
) {
    val secondary = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val axisTint = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
    Column(
        modifier = Modifier
            .width(ScheduleAxisWidth)
            .height(totalHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(axisTint)
            .drawBehind {
                val stroke = 0.5.dp.toPx()
                for (index in 1 until sections.size) {
                    val y = ScheduleWeekRowHeight.toPx() * index
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = stroke,
                    )
                }
            },
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
private fun ScheduleCourseDialog(
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

private fun courseTimeText(course: ScheduleCourse): String {
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

private fun formatCourseWeeks(weeks: Set<Int>): String {
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
                    text = "关闭时邑周只显示周一到周五",
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
            text = "教务系统不返回当前周次，日期与“壹日”课程会按这里的设置推算。",
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
private fun ScheduleLoginScreen(
    error: String?,
    onLogin: () -> Unit,
    onBack: () -> Unit,
) {
    ScheduleStatePage(onBack = onBack) {
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
private fun ScheduleLoadingScreen(onBack: () -> Unit) {
    ScheduleStatePage(onBack = onBack) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ScheduleErrorScreen(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    ScheduleStatePage(onBack = onBack) {
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
private fun ScheduleStatePage(
    onBack: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    WyuPageScaffold(
        progress = { 0f },
        maskCollapseHeight = WyuLargeTitleHeight,
        topBar = {
            WyuActionBar(
                title = "课表",
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
            content = content,
        )
    }
}

private fun weekdayName(date: LocalDate): String = when (date.dayOfWeek.value) {
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
