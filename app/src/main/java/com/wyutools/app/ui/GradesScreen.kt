package com.wyutools.app.ui

import android.widget.Toast
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.School
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
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
    onOpenGpa: () -> Unit,
    onBack: () -> Unit,
) {
    val ui by vm.grades.collectAsState()
    val authUi by authVm.ui.collectAsState()
    val detail by vm.gradeDetail.collectAsState()
    val context = LocalContext.current
    // 分析单弹窗从底部滑入/滑出，关闭时先收起再清数据。
    var detailShow by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.onGradesEnter() }
    LaunchedEffect(authUi.loggedIn, authUi.restoring) {
        if (!authUi.restoring && authUi.loggedIn) vm.onGradesEnter()
    }
    LaunchedEffect(ui.notice) {
        val notice = ui.notice ?: return@LaunchedEffect
        Toast.makeText(context, notice, Toast.LENGTH_SHORT).show()
        vm.consumeGradesNotice()
    }

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
            onRefresh = { vm.refreshGrades() },
            onOpenGpa = onOpenGpa,
            onBack = onBack,
            onOpenEntry = { entry ->
                detailShow = true
                vm.loadGradeDetail(entry)
            },
        )

        authUi.restoring || authUi.autoLoggingIn || authUi.loggingOut ->
            AcademicStatePage("成绩", onBack) { CircularProgressIndicator() }

        !authUi.loggedIn -> AcademicStatePage("成绩", onBack) {
            AcademicLoginPrompt(
                icon = Icons.Outlined.School,
                title = "登录后查看成绩",
                body = "登录校园统一身份认证后，会从教务系统读取每学期的课程成绩、绩点与单科分析单。",
                error = ui.error,
                onLogin = onLogin,
            )
        }

        ui.loading || ui.error == null ->
            AcademicStatePage("成绩", onBack) { CircularProgressIndicator() }

        else -> AcademicStatePage(
            title = "成绩",
            onBack = onBack,
            showRefresh = true,
            onRefresh = { vm.refreshGrades() },
        ) {
            AcademicGlyph(Icons.Outlined.CloudOff, primary = false)
            Spacer(Modifier.height(20.dp))
            AcademicTitle("成绩加载失败")
            Spacer(Modifier.height(8.dp))
            AcademicBody(ui.error ?: "成绩加载失败，请稍后重试。", error = true)
            Spacer(Modifier.height(20.dp))
            ActionChip(text = "重试", onClick = { vm.refreshGrades() }, primary = true)
        }
    }
}

@Composable
private fun GradesContent(
    ui: GradesUiState,
    data: GradesData,
    onRefresh: () -> Unit,
    onOpenGpa: () -> Unit,
    onBack: () -> Unit,
    onOpenEntry: (GradeEntry) -> Unit,
) {
    val listState = rememberLazyListState()
    val fraction = rememberWyuCollapseFraction(listState)
    // 默认停在最新学期；用户切换后记住选择，但数据刷新时若该学期消失就回退。
    var selectedCode by rememberSaveable { mutableStateOf("") }
    val terms = remember(data) { data.terms.reversed() }
    val selected = terms.firstOrNull { it.code == selectedCode } ?: terms.firstOrNull()

    WyuPageScaffold(
        progress = { fraction.value },
        maskCollapseHeight = WyuLargeTitleHeight,
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
                    GpaGlassButton(onClick = onOpenGpa)
                    Spacer(Modifier.width(6.dp))
                    WyuTopBarAction(
                        icon = Icons.Filled.Refresh,
                        label = "刷新",
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
            item(key = "overview") {
                GradesOverview(data)
                Spacer(Modifier.height(14.dp))
            }
            item(key = "terms") {
                TermChips(
                    terms = terms.map { it.code to it.name },
                    selected = selected?.code.orEmpty(),
                    onSelect = { selectedCode = it },
                )
                Spacer(Modifier.height(12.dp))
            }
            if (selected != null) {
                item(key = "term-head-${selected.code}") {
                    TermSummary(selected)
                    Spacer(Modifier.height(10.dp))
                }
                items(selected.entries, key = { it.key }) { entry ->
                    GradeCard(entry = entry, onClick = { onOpenEntry(entry) })
                    Spacer(Modifier.height(10.dp))
                }
            }
            item(key = "footer") {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = buildString {
                        if (data.fetchedAt > 0) append(updatedAt(data.fetchedAt))
                        append("成绩与绩点均来自教务系统，以教务为准。")
                    },
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

/** 顶栏的玻璃胶囊：一眼能看出是查平均绩点的入口。 */
@Composable
private fun GpaGlassButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(34.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.fillMaxHeight(),
            cornerRadius = 17.dp,
            insideMargin = PaddingValues(horizontal = 13.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ShowChart,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MiuixTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "平均绩点",
                    style = MiuixTheme.textStyles.footnote1,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun GradesOverview(data: GradesData) {
    val primary = MiuixTheme.colorScheme.primary
    val gpa = data.overallGpa
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = WyuCardCornerRadius,
        samplePageContent = false,
        insideMargin = PaddingValues(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(rememberWyuIconShape(44.dp))
                    .background(primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.School,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = primary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "全部成绩",
                    style = MiuixTheme.textStyles.headline2,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${data.terms.size} 个学期 · ${data.all.size} 门课程",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = gpa?.let { String.format(Locale.US, "%.2f", it) } ?: "—",
                    style = MiuixTheme.textStyles.title2,
                    fontWeight = FontWeight.SemiBold,
                    color = primary,
                )
                Text(
                    text = "平均绩点",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = "总学分 ${trimNumber(data.totalCredit)} · 只统计已出成绩的课程",
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun TermSummary(term: GradeTerm) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${term.entries.size} 门课程",
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "学分 ${trimNumber(term.totalCredit)}",
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.weight(1f))
        term.gpa?.let { gpa ->
            Text(
                text = "学期绩点 ${String.format(Locale.US, "%.2f", gpa)}",
                style = MiuixTheme.textStyles.footnote2,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun GradeCard(entry: GradeEntry, onClick: () -> Unit) {
    val primary = MiuixTheme.colorScheme.primary
    val failed = entry.passed == false
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = WyuCardCornerRadius,
        insideMargin = PaddingValues(horizontal = 18.dp, vertical = 15.dp),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.courseName,
                    style = MiuixTheme.textStyles.headline2,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    entry.studyMode?.let { mode ->
                        TinyTag(mode)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = buildString {
                            entry.credit?.let { append("${trimNumber(it)} 学分") }
                            entry.scoreMode?.let {
                                if (isNotEmpty()) append(" · ")
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
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = entry.scoreText ?: "—",
                    style = MiuixTheme.textStyles.title2.copy(fontSize = 26.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = if (failed) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    text = entry.point?.let { String.format(Locale.US, "绩点 %.1f", it) } ?: "—",
                    style = MiuixTheme.textStyles.footnote2,
                    color = if (failed) {
                        MiuixTheme.colorScheme.error
                    } else {
                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                    },
                )
            }
        }
        entry.examNature?.takeIf { it != "正常考试" }?.let { nature ->
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .clip(rememberWyuSquircle(9.dp))
                    .background(primary.copy(alpha = 0.12f))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
                Text(
                    text = nature,
                    style = MiuixTheme.textStyles.footnote2.copy(fontSize = 11.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = primary,
                )
            }
        }
    }
}

@Composable
private fun TinyTag(text: String) {
    Box(
        modifier = Modifier
            .clip(rememberWyuSquircle(8.dp))
            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.10f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote2.copy(fontSize = 10.sp),
            color = MiuixTheme.colorScheme.primary,
            maxLines = 1,
        )
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
                    WyuTopBarAction(
                        icon = Icons.Filled.Refresh,
                        label = "刷新",
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
                        style = MiuixTheme.textStyles.footnote2.copy(fontSize = 11.sp),
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

private fun updatedAt(fetchedAt: Long): String {
    val time = runCatching {
        java.time.Instant.ofEpochMilli(fetchedAt)
            .atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.CHINA))
    }.getOrNull() ?: return ""
    return "更新于 $time · "
}
