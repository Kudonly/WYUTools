package com.wyutools.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.School
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyutools.app.academics.AcademicsViewModel
import com.wyutools.app.academics.GradeDetail
import com.wyutools.app.academics.GradeDetailState
import com.wyutools.app.academics.GradeEntry
import com.wyutools.app.academics.GradePart
import com.wyutools.app.academics.GradeTerm
import com.wyutools.app.academics.GradesData
import com.wyutools.app.academics.GradesUiState
import com.wyutools.app.academics.GpaData
import com.wyutools.app.academics.GpaRow
import com.wyutools.app.academics.GpaUiState
import com.wyutools.app.auth.AuthViewModel
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 成绩：按学期分组的课程卡片，点开是单科「成绩分析单」。
 *
 * 学期只从最早有成绩的那一学期开始列，所以入学前的空学期不会占位置。
 */
@Composable
fun GradesScreen(
    vm: AcademicsViewModel,
    authVm: AuthViewModel,
    onLogin: () -> Unit,
    onBack: () -> Unit,
) {
    val ui by vm.grades.collectAsState()
    val gpaUi by vm.gpa.collectAsState()
    val authUi by authVm.ui.collectAsState()
    val detail by vm.gradeDetail.collectAsState()
    // 分析单弹窗从底部滑入/滑出，关闭时先收起再清数据。
    var detailShow by remember { mutableStateOf(false) }

    // 成绩和绩点一起取：概览里的平均绩点用教务「历年」口径，不自己算一遍，
    // 免得跟绩点页签的数字对不上。
    LaunchedEffect(Unit) {
        vm.onGradesEnter()
        vm.onGpaEnter()
    }
    LaunchedEffect(authUi.loggedIn, authUi.restoring) {
        if (!authUi.restoring && authUi.loggedIn) {
            vm.onGradesEnter()
            vm.onGpaEnter()
        }
    }
    WyuNoticeToast(ui.notice) { vm.consumeGradesNotice() }

    if (detail.cjdm != null) {
        GradeDetailDialog(
            state = detail,
            show = detailShow,
            onDismiss = { detailShow = false },
            onDismissed = { vm.clearGradeDetail() },
        )
    }

    val data = ui.data
    when {
        data != null -> GradesContent(
            ui = ui,
            data = data,
            gpaUi = gpaUi,
            onRefreshGrades = { vm.refreshGrades() },
            onRefreshGpa = { vm.refreshGpa() },
            onBack = onBack,
            onOpenEntry = { entry ->
                detailShow = true
                vm.loadGradeDetail(entry)
            },
        )

        authUi.restoring || authUi.autoLoggingIn || authUi.loggingOut ->
            AcademicLoadingPage("成绩", onBack)

        !authUi.loggedIn -> AcademicStatePage("成绩", onBack) {
            AcademicLoginPrompt(
                icon = Icons.Outlined.School,
                title = "登录后查看成绩",
                body = "登录校园统一身份认证后，会从教务系统读取每学期的课程成绩、绩点与单科分析单。",
                error = ui.error,
                onLogin = onLogin,
            )
        }

        ui.loading || ui.error == null -> AcademicLoadingPage("成绩", onBack)

        else -> AcademicErrorPage(
            title = "成绩",
            heading = "成绩加载失败",
            message = ui.error ?: "成绩加载失败，请稍后重试。",
            onBack = onBack,
            onRefresh = { vm.refreshGrades() },
            onRetry = { vm.refreshGrades() },
        )
    }
}

@Composable
private fun GradesContent(
    ui: GradesUiState,
    data: GradesData,
    gpaUi: GpaUiState,
    onRefreshGrades: () -> Unit,
    onRefreshGpa: () -> Unit,
    onBack: () -> Unit,
    onOpenEntry: (GradeEntry) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val gradesListState = rememberLazyListState()
    val gpaListState = rememberLazyListState()
    val gradesFraction = rememberWyuCollapseFraction(gradesListState)
    val gpaFraction = rememberWyuCollapseFraction(gpaListState)
    val fraction = rememberWyuPagerCollapseFraction(
        pagerState = pagerState,
        firstFraction = gradesFraction,
        secondFraction = gpaFraction,
    )
    val page = pagerState.currentPage

    WyuPageScaffold(
        progress = { fraction.value },
        maskCollapseHeight = WyuLargeTitleHeight,
        // 与体育馆预约 / 教评一致：切换条悬浮在顶栏下方、内容层之外的 overlay
        // 槽里。放进列表里会变成「内容层采样内容层」，渲染线程栈溢出闪退。
        overlay = {
            GradesTabsOverlay(
                pagerState = pagerState,
                collapsedFraction = fraction,
                onSelect = { index ->
                    if (index != pagerState.currentPage) {
                        scope.launch { pagerState.animateScrollToPage(index) }
                    }
                },
            )
        },
        topBar = {
            WyuActionBar(
                title = "成绩",
                collapsedFraction = { fraction.value },
                navigationIcon = {
                    WyuTopBarIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        onClick = onBack,
                    )
                },
                actions = {
                    WyuTopBarIconButton(
                        icon = Icons.Filled.Refresh,
                        contentDescription = "刷新",
                        onClick = if (page == 0) onRefreshGrades else onRefreshGpa,
                        enabled = if (page == 0) !ui.refreshing else !gpaUi.refreshing,
                    )
                },
            )
        },
    ) { padding ->
        // 悬浮条占的是顶栏之外的高度，列表从折叠后的顶栏算起，再由首项的
        // 占位块把切换条让出来。
        val listTopInset = padding.calculateTopPadding() - WyuLargeTitleHeight
        val listBottomInset = padding.calculateBottomPadding() + 28.dp
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { index ->
            if (index == 0) {
                GradesTab(
                    data = data,
                    gpa = gpaUi.data?.overall?.gpa ?: data.overallGpa,
                    listState = gradesListState,
                    topInset = listTopInset,
                    bottomInset = listBottomInset,
                    onOpenEntry = onOpenEntry,
                )
            } else {
                GpaTab(
                    ui = gpaUi,
                    data = gpaUi.data,
                    listState = gpaListState,
                    topInset = listTopInset,
                    bottomInset = listBottomInset,
                )
            }
        }
    }
}

/**
 * 成绩页签：概览三栏 + 学期胶囊 + 该学期的课程单卡。
 *
 * [gpa] 用的是教务「历年」口径，和绩点页签里的那个数字同源，两处不会再打架。
 */
@Composable
private fun GradesTab(
    data: GradesData,
    gpa: Double?,
    listState: LazyListState,
    topInset: Dp,
    bottomInset: Dp,
    onOpenEntry: (GradeEntry) -> Unit,
) {
    // 默认停在最新学期；用户切换后记住选择，但数据刷新时若该学期消失就回退。
    var selectedCode by rememberSaveable { mutableStateOf("") }
    val terms = remember(data) { data.terms.reversed() }
    val selected = terms.firstOrNull { it.code == selectedCode } ?: terms.firstOrNull()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = topInset,
            bottom = bottomInset,
        ),
    ) {
        item(key = "tabs-spacer") {
            Spacer(Modifier.height(GradesContentTopSpacer))
        }
        item(key = "overview") {
            GradesOverview(data = data, gpa = gpa)
            Spacer(Modifier.height(12.dp))
        }
        item(key = "terms") {
            TermChips(
                terms = terms.map { it.code to it.name },
                selected = selected?.code.orEmpty(),
                onSelect = { selectedCode = it },
            )
            Spacer(Modifier.height(10.dp))
        }
        if (selected != null) {
            item(key = "term-head-${selected.code}") {
                TermSummary(selected)
                Spacer(Modifier.height(8.dp))
            }
            item(key = "term-list-${selected.code}") {
                GradeList(entries = selected.entries, onOpenEntry = onOpenEntry)
                Spacer(Modifier.height(10.dp))
            }
        }
        item(key = "footer") {
            Text(
                text = buildString {
                    if (data.fetchedAt > 0) append(updatedStampLabel(data.fetchedAt))
                    append("共 ${data.terms.size} 个学期 · ${data.all.size} 门课程")
                },
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

/** 绩点页签：历年总绩点大卡 + 各学期绩点单卡。 */
@Composable
private fun GpaTab(
    ui: GpaUiState,
    data: GpaData?,
    listState: LazyListState,
    topInset: Dp,
    bottomInset: Dp,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = topInset,
            bottom = bottomInset,
        ),
    ) {
        item(key = "tabs-spacer") {
            Spacer(Modifier.height(GradesContentTopSpacer))
        }
        item(key = "gpa-hero") {
            if (data != null) {
                GpaHero(data.overall)
            } else {
                GpaLoadingCard(ui.error)
            }
            Spacer(Modifier.height(12.dp))
        }
        if (data != null) {
            item(key = "gpa-terms") {
                GpaTermList(rows = data.terms, best = data.best?.label)
                Spacer(Modifier.height(10.dp))
            }
            item(key = "gpa-note") {
                Text(
                    text = "注：教务只统计修读方式为必修的课程；「历年」是教务处默认的统计口径，成绩页的概览也用它。",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

private val GradesTabTitles = listOf("成绩", "绩点")

/** 悬浮切换条占掉的区域：与教评 / 体育馆用同一组数字。 */
private val GradesTabsAreaHeight = 50.dp

// 悬浮条不占列表流，内容要为它让位：折叠后的顶栏高度由 contentPadding 给，
// 这里补上大标题高度、切换条高度，再留一点呼吸空间。
private val GradesContentTopSpacer = WyuLargeTitleHeight + GradesTabsAreaHeight + 12.dp

/**
 * 悬浮在顶栏下方的切换条，做法照搬体育馆预约 / 教评：放在 `WyuPageScaffold` 的
 * overlay 槽里，而不是塞进列表。overlay 是内容层的兄弟节点，采样页面内容层不会
 * 让 RenderNode 引用自己；塞进 `LazyColumn` 会直接把渲染线程递归到栈溢出。
 */
@Composable
private fun GradesTabsOverlay(
    pagerState: PagerState,
    collapsedFraction: State<Float>,
    onSelect: (Int) -> Unit,
) {
    // 滑块跟分页器的连续位置走，左右滑动时与页面同步，而不是等翻页才跳过去。
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
            .padding(top = rememberWyuStatusBarTopInset())
            .padding(top = 62.dp, start = 16.dp, end = 16.dp),
    ) {
        WyuFloatingTabs(
            options = GradesTabTitles,
            selectedIndex = pagerState.currentPage,
            onSelect = onSelect,
            selectionFraction = selectionFraction,
            lightThumb = true,
            modifier = Modifier.graphicsLayer {
                val collapsed = (collapsedFraction.value + boost()).coerceIn(0f, 1f)
                translationY = WyuLargeTitleHeight.toPx() * (1f - collapsed)
            },
        )
    }
}

/** 与 iOS 同款的三栏概览：平均绩点 / 已修学分 / 课程。 */
@Composable
private fun GradesOverview(data: GradesData, gpa: Double?) {
    val primary = MiuixTheme.colorScheme.primary
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = WyuCardCornerRadius,
        samplePageContent = false,
        insideMargin = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            StatTile(
                title = "平均绩点",
                value = gpa?.let { String.format(Locale.US, "%.2f", it) } ?: "—",
                tint = primary,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                title = "已修学分",
                value = trimNumber(data.totalCredit),
                unit = "分",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                title = "课程",
                value = data.all.size.toString(),
                unit = "门",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatTile(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    tint: androidx.compose.ui.graphics.Color = MiuixTheme.colorScheme.onSurface,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = MiuixTheme.textStyles.title2.copy(fontSize = 22.sp),
                fontWeight = WyuEmphasisWeight,
                color = tint,
                maxLines = 1,
            )
            unit?.let {
                Spacer(Modifier.width(2.dp))
                Text(
                    text = it,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
    }
}

/** 分组小标题：左侧灰字，右侧可带统计说明。 */
@Composable
private fun SectionTitle(text: String, trailing: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 左边补到顶栏大标题那条线（列表本身有 16dp 页边距）。
            .padding(start = 12.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote1,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
        )
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            Text(
                text = trailing,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TermSummary(term: GradeTerm) {
    SectionTitle(
        text = term.name,
        trailing = "${trimNumber(term.totalCredit)} 学分 · " +
            (term.gpa?.let { String.format(Locale.US, "%.2f", it) } ?: "—"),
    )
}

/** 一个学期一张卡，课程行之间用与 iOS 一致的内缩分隔线。 */
@Composable
private fun GradeList(entries: List<GradeEntry>, onOpenEntry: (GradeEntry) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = WyuCardCornerRadius,
        samplePageContent = false,
        insideMargin = PaddingValues(0.dp),
    ) {
        entries.forEachIndexed { index, entry ->
            GradeRow(entry = entry, onClick = { onOpenEntry(entry) })
            if (index != entries.lastIndex) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 18.dp))
            }
        }
    }
}

@Composable
private fun GradeRow(entry: GradeEntry, onClick: () -> Unit) {
    val failed = entry.passed == false
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.courseName,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = buildString {
                    entry.credit?.let { append("${trimNumber(it)} 学分") }
                    entry.examNature?.let {
                        if (isNotEmpty()) append("  ")
                        append(it)
                    }
                    entry.studyMode?.let {
                        if (isNotEmpty()) append("  ")
                        append(it)
                    }
                    if (isEmpty()) append(entry.courseCode.orEmpty())
                },
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = entry.scoreText ?: "—",
                style = MiuixTheme.textStyles.title2.copy(fontSize = 20.sp),
                fontWeight = WyuEmphasisWeight,
                color = if (failed) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurface,
            )
            entry.point?.let { point ->
                Spacer(Modifier.height(2.dp))
                Text(
                    text = String.format(Locale.US, "绩点 %.2f", point),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        if (entry.cjdm != null) {
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun GpaLoadingCard(error: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = WyuCardCornerRadius,
        samplePageContent = false,
        insideMargin = PaddingValues(20.dp),
    ) {
        if (error == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
        } else {
            Text(
                text = error,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.error,
            )
        }
    }
}

/** 各学期绩点列表：与课程列表同一套行式卡片。 */
@Composable
private fun GpaTermList(rows: List<GpaRow>, best: String?) {
    if (rows.isEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = WyuCardCornerRadius,
            samplePageContent = false,
            insideMargin = PaddingValues(20.dp),
        ) {
            Text(
                text = "教务没有返回按学期的绩点，只有总平均。",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        return
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = WyuCardCornerRadius,
        samplePageContent = false,
        insideMargin = PaddingValues(0.dp),
    ) {
        rows.forEachIndexed { index, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.label,
                    style = MiuixTheme.textStyles.footnote1,
                )
                if (best == row.label) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(rememberWyuSquircle(9.dp))
                            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "最高",
                            style = MiuixTheme.textStyles.footnote2,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = String.format(Locale.US, "%.2f", row.gpa),
                    style = MiuixTheme.textStyles.body1.copy(fontSize = 15.sp),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (index != rows.lastIndex) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 18.dp))
            }
        }
    }
}

/** 单科分析单：分项构成 + 课程/教学班排名。 */
@Composable
private fun GradeDetailDialog(
    state: GradeDetailState,
    show: Boolean,
    onDismiss: () -> Unit,
    onDismissed: () -> Unit,
) {
    val detail = state.detail
    AcademicDetailDialog(
        show = show,
        title = "成绩分析单",
        subtitle = state.error,
        onDismiss = onDismiss,
        onDismissed = onDismissed,
    ) {
        when {
            state.loading || (detail == null && state.error == null) -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }

            detail == null -> AcademicBody(state.error ?: "没有拿到分析单。", error = true)

            else -> GradeDetailBody(detail)
        }
    }
}

@Composable
private fun GradeDetailBody(detail: GradeDetail) {
    val primary = MiuixTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "总评",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = detail.total ?: "—",
                style = MiuixTheme.textStyles.title2,
                fontWeight = FontWeight.SemiBold,
                color = primary,
            )
        }
        if (detail.parts.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "成绩构成",
                style = MiuixTheme.textStyles.footnote1,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            detail.parts.forEach { part ->
                GradePartRow(part)
                Spacer(Modifier.height(8.dp))
            }
        }
        if (detail.ranks.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "排名",
                style = MiuixTheme.textStyles.footnote1,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            detail.ranks.forEach { rank ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = rank.type,
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.width(56.dp),
                    )
                    Text(
                        text = "第 ${rank.rank ?: "—"} 名 / ${rank.total ?: "—"} 人",
                        style = MiuixTheme.textStyles.footnote1,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
        if (detail.parts.isEmpty() && detail.ranks.isEmpty()) {
            AcademicBody("这门课没有分项成绩，教务只给了总评。")
        }
    }
}

@Composable
private fun GradePartRow(part: GradePart) {
    val primary = MiuixTheme.colorScheme.primary
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = part.name,
                style = MiuixTheme.textStyles.footnote1,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = part.score ?: "—",
                style = MiuixTheme.textStyles.footnote1,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = part.weight?.let { "${trimNumber(it)}%" } ?: "",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.width(44.dp),
                textAlign = TextAlign.End,
            )
        }
        if (part.weight != null) {
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((part.weight / 100.0).toFloat().coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(primary),
                )
            }
        }
    }
}

// -------------------- 平均绩点 --------------------

@Composable
fun GpaScreen(
    vm: AcademicsViewModel,
    authVm: AuthViewModel,
    onLogin: () -> Unit,
    onBack: () -> Unit,
) {
    val ui by vm.gpa.collectAsState()
    val authUi by authVm.ui.collectAsState()

    LaunchedEffect(Unit) { vm.onGpaEnter() }
    LaunchedEffect(authUi.loggedIn, authUi.restoring) {
        if (!authUi.restoring && authUi.loggedIn) vm.onGpaEnter()
    }

    val data = ui.data
    when {
        data != null -> GpaContent(ui = ui, data = data, onRefresh = { vm.refreshGpa() }, onBack = onBack)

        authUi.restoring || authUi.autoLoggingIn ->
            AcademicStatePage("平均绩点", onBack) { CircularProgressIndicator() }

        !authUi.loggedIn -> AcademicStatePage("平均绩点", onBack) {
            AcademicLoginPrompt(
                icon = Icons.AutoMirrored.Outlined.ShowChart,
                title = "登录后查看绩点",
                body = "平均绩点由教务按学分加权统计，这里展示「历年」口径的总绩点与每学期绩点。",
                error = ui.error,
                onLogin = onLogin,
            )
        }

        ui.loading || ui.error == null ->
            AcademicStatePage("平均绩点", onBack) { CircularProgressIndicator() }

        else -> AcademicStatePage(
            title = "平均绩点",
            onBack = onBack,
            showRefresh = true,
            onRefresh = { vm.refreshGpa() },
        ) {
            AcademicGlyph(Icons.Outlined.CloudOff, primary = false)
            Spacer(Modifier.height(20.dp))
            AcademicTitle("绩点加载失败")
            Spacer(Modifier.height(8.dp))
            AcademicBody(ui.error ?: "平均绩点加载失败，请稍后重试。", error = true)
        }
    }
}

@Composable
private fun GpaContent(
    ui: GpaUiState,
    data: GpaData,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    val listState = rememberLazyListState()
    val fraction = rememberWyuCollapseFraction(listState)

    WyuPageScaffold(
        progress = { fraction.value },
        maskCollapseHeight = WyuLargeTitleHeight,
        topBar = {
            WyuActionBar(
                title = "平均绩点",
                collapsedFraction = { fraction.value },
                navigationIcon = {
                    WyuTopBarIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        onClick = onBack,
                    )
                },
                actions = {
                    WyuTopBarIconButton(
                        icon = Icons.Filled.Refresh,
                        contentDescription = "刷新",
                        onClick = onRefresh,
                        enabled = !ui.refreshing,
                    )
                },
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 28.dp,
            ),
        ) {
            item(key = "hero") {
                GpaHero(data.overall)
                Spacer(Modifier.height(16.dp))
            }
            val best = data.best
            items(data.terms, key = { it.label }) { row ->
                GpaTermCard(row = row, best = best?.label == row.label)
                Spacer(Modifier.height(10.dp))
            }
            item(key = "note") {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "注：教务只统计修读方式为必修的课程；「历年」是教务处默认的统计口径。",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
private fun GpaHero(overall: GpaRow?) {
    val primary = MiuixTheme.colorScheme.primary
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = WyuCardCornerRadius,
        samplePageContent = false,
        insideMargin = PaddingValues(22.dp),
    ) {
        Text(
            text = "历年平均绩点",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = overall?.let { String.format(Locale.US, "%.2f", it.gpa) } ?: "—",
                style = MiuixTheme.textStyles.headline1.copy(fontSize = 52.sp),
                fontWeight = FontWeight.SemiBold,
                color = primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "/ 5.0",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        overall?.let { GpaBar(it.gpa) }
    }
}

@Composable
private fun GpaTermCard(row: GpaRow, best: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = WyuCardCornerRadius,
        samplePageContent = false,
        insideMargin = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = row.label,
                style = MiuixTheme.textStyles.headline2,
                fontWeight = FontWeight.SemiBold,
            )
            if (best) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(rememberWyuSquircle(9.dp))
                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "最高",
                        style = MiuixTheme.textStyles.footnote2,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = String.format(Locale.US, "%.2f", row.gpa),
                style = MiuixTheme.textStyles.title2,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(10.dp))
        GpaBar(row.gpa)
    }
}

@Composable
private fun GpaBar(gpa: Double) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth((gpa / 5.0).toFloat().coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(MiuixTheme.colorScheme.primary),
        )
    }
}

// -------------------- 小工具 --------------------

private fun trimNumber(value: Double): String =
    if (value == Math.floor(value) && !value.isInfinite()) {
        value.toInt().toString()
    } else {
        String.format(Locale.US, "%.1f", value)
    }
