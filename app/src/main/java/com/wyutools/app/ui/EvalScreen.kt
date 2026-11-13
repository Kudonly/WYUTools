package com.wyutools.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.RateReview
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyutools.app.academics.AcademicsViewModel
import com.wyutools.app.academics.EvalData
import com.wyutools.app.academics.EvalDetail
import com.wyutools.app.academics.EvalDetailState
import com.wyutools.app.academics.EvalTask
import com.wyutools.app.academics.EvalUiState
import com.wyutools.app.auth.AuthViewModel
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 教评：按学期列结课评价，点开看这门课的评价明细。
 *
 * 可选学期从入学（或最早有成绩的学期）开始，教务默认给出的 2003 年那种空
 * 学期不会出现。
 */
@Composable
fun EvalScreen(
    vm: AcademicsViewModel,
    authVm: AuthViewModel,
    onLogin: () -> Unit,
    onBack: () -> Unit,
) {
    val ui by vm.eval.collectAsState()
    val authUi by authVm.ui.collectAsState()
    val detail by vm.evalDetail.collectAsState()
    // 明细弹窗从底部滑入/滑出，收起动画跑完再清 VM 里的数据。
    var detailShow by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.onEvalEnter() }
    LaunchedEffect(authUi.loggedIn, authUi.restoring) {
        if (!authUi.restoring && authUi.loggedIn) vm.onEvalEnter()
    }

    if (detail.key != null) {
        EvalDetailDialog(
            state = detail,
            show = detailShow,
            onDismiss = { detailShow = false },
            onDismissed = { vm.clearEvalDetail() },
        )
    }

    val data = ui.data
    when {
        data != null -> EvalContent(
            ui = ui,
            data = data,
            onRefresh = { vm.refreshEval() },
            onBack = onBack,
            onOpen = { task ->
                detailShow = true
                vm.loadEvalDetail(task)
            },
        )

        authUi.restoring || authUi.autoLoggingIn ->
            AcademicStatePage("教评", onBack) { CircularProgressIndicator() }

        !authUi.loggedIn -> AcademicStatePage("教评", onBack) {
            AcademicLoginPrompt(
                icon = Icons.Outlined.RateReview,
                title = "登录后查看教评",
                body = "登录校园统一身份认证后，会从教务系统读取每学期的结课评价任务与已提交的评价明细。",
                error = ui.error,
                onLogin = onLogin,
            )
        }

        ui.loading || ui.error == null ->
            AcademicStatePage("教评", onBack) { CircularProgressIndicator() }

        else -> AcademicStatePage(
            title = "教评",
            onBack = onBack,
            showRefresh = true,
            onRefresh = { vm.refreshEval() },
        ) {
            AcademicGlyph(Icons.Outlined.CloudOff, primary = false)
            Spacer(Modifier.height(20.dp))
            AcademicTitle("教评加载失败")
            Spacer(Modifier.height(8.dp))
            AcademicBody(ui.error ?: "结课评价加载失败，请稍后重试。", error = true)
        }
    }
}

@Composable
private fun EvalContent(
    ui: EvalUiState,
    data: EvalData,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onOpen: (EvalTask) -> Unit,
) {
    val listState = rememberLazyListState()
    val fraction = rememberWyuCollapseFraction(listState)
    var selectedCode by rememberSaveable { mutableStateOf("") }
    // 未评价的课程教务没有开放提交入口，点开只做说明，不去请求明细。
    var blocked by remember { mutableStateOf<EvalTask?>(null) }
    var blockedShow by remember { mutableStateOf(false) }
    // 学期按新到旧排，默认停在最新的、有评价任务的那一学期。
    val terms = remember(data) { data.terms.sortedByDescending { it.code } }
    val selected = terms.firstOrNull { it.code == selectedCode } ?: terms.firstOrNull()

    blocked?.let { task ->
        EvalSubmitBlockedDialog(
            task = task,
            show = blockedShow,
            onDismiss = { blockedShow = false },
            onDismissed = { blocked = null },
        )
    }

    WyuPageScaffold(
        progress = { fraction.value },
        maskCollapseHeight = WyuLargeTitleHeight,
        topBar = {
            WyuActionBar(
                title = "教评",
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
            if (terms.isEmpty()) {
                item(key = "empty") {
                    EvalEmptyCard()
                }
            } else {
                item(key = "terms") {
                    TermChips(
                        terms = terms.map { it.code to it.name },
                        selected = selected?.code.orEmpty(),
                        onSelect = { selectedCode = it },
                    )
                    Spacer(Modifier.height(12.dp))
                }
                if (selected != null) {
                    val done = selected.tasks.count { it.pjdm != null }
                    item(key = "head-${selected.code}") {
                        Text(
                            text = if (done == selected.tasks.size) {
                                "共 ${selected.tasks.size} 门课程 · 全部已评价 · 点开可看明细"
                            } else {
                                "共 ${selected.tasks.size} 门课程 · 已评价 $done 门 · 点开可看明细"
                            },
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(start = 6.dp, bottom = 10.dp),
                        )
                    }
                    items(selected.tasks, key = { it.key }) { task ->
                        EvalTaskCard(
                            task = task,
                            onClick = {
                                if (task.pjdm != null) {
                                    onOpen(task)
                                } else {
                                    blocked = task
                                    blockedShow = true
                                }
                            },
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
            item(key = "note") {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "结课评价由教务系统发布，评价内容与得分以教务为准。教务当前未开放学生端提交评价的入口，" +
                        "因此这里只能查看已提交的评价明细。",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
private fun EvalEmptyCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = WyuCardCornerRadius,
        samplePageContent = false,
        insideMargin = PaddingValues(24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "还没有结课评价",
                style = MiuixTheme.textStyles.headline2,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            AcademicBody("教务发布评价任务后，这里会按学期列出来。")
        }
    }
}

@Composable
private fun EvalTaskCard(task: EvalTask, onClick: () -> Unit) {
    val primary = MiuixTheme.colorScheme.primary
    val evaluated = task.pjdm != null
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = WyuCardCornerRadius,
        insideMargin = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.courseName,
                    style = MiuixTheme.textStyles.headline2,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = listOfNotNull(task.teacher, task.session).joinToString(" · "),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                task.closeDate?.let { date ->
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "结课 $date",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .clip(rememberWyuSquircle(10.dp))
                    .background(
                        if (evaluated) {
                            primary.copy(alpha = 0.12f)
                        } else {
                            MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.12f)
                        },
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = if (evaluated) "已评价" else "未开放",
                    style = MiuixTheme.textStyles.footnote2.copy(fontSize = 11.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = if (evaluated) primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

/**
 * 未评价的课程教务没有给提交入口（结课评价页的按钮生成逻辑只输出「已评价」或
 * 「不能评价」），这里把原因说清楚，避免用户以为点不动是 App 的问题。
 */
@Composable
private fun EvalSubmitBlockedDialog(
    task: EvalTask,
    show: Boolean,
    onDismiss: () -> Unit,
    onDismissed: () -> Unit,
) {
    AcademicDetailDialog(
        show = show,
        title = task.courseName,
        subtitle = listOfNotNull(task.teacher, task.session, task.termName).joinToString(" · "),
        onDismiss = onDismiss,
        onDismissed = onDismissed,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "状态",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "教务未开放提交入口",
                style = MiuixTheme.textStyles.footnote1,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Spacer(Modifier.height(12.dp))
        AcademicBody(
            "这门课在教务系统里没有生成评价问卷，结课评价页面显示「未安排评价时间」，" +
                "列表里也只会给已提交的评价提供查看入口。所以这里看不到、也无法代交评价。",
        )
        Spacer(Modifier.height(8.dp))
        AcademicBody(
            "需要评价时请到教务系统网页端确认本学期是否已发布评价任务。",
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun EvalDetailDialog(
    state: EvalDetailState,
    show: Boolean,
    onDismiss: () -> Unit,
    onDismissed: () -> Unit,
) {
    val detail = state.detail
    AcademicDetailDialog(
        show = show,
        title = detail?.task?.courseName ?: "评价明细",
        subtitle = detail?.let { parsed ->
            listOfNotNull(
                parsed.task.teacher,
                parsed.task.session,
                parsed.task.termName,
            ).joinToString(" · ")
        },
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

            detail == null -> AcademicBody(state.error ?: "没有拿到评价明细。", error = true)

            else -> EvalDetailBody(detail)
        }
    }
}

@Composable
private fun EvalDetailBody(detail: EvalDetail) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "评价得分",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = detail.total ?: "—",
                style = MiuixTheme.textStyles.title2,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.primary,
            )
        }
        if (detail.answers.isEmpty()) {
            Spacer(Modifier.height(12.dp))
            AcademicBody("这门课没有评价题目记录。")
            return@Column
        }
        Spacer(Modifier.height(6.dp))
        detail.answers.forEachIndexed { index, answer ->
            Spacer(Modifier.height(10.dp))
            Row {
                Text(
                    text = "${index + 1}.",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.width(22.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = answer.question,
                        style = MiuixTheme.textStyles.footnote1,
                    )
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = answer.answer?.let { "答案 $it" } ?: "—",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.primary,
                        )
                        answer.score?.let { weight ->
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "分值 $weight",
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                    answer.feedback?.takeIf { it.isNotBlank() }?.let { feedback ->
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "意见：$feedback",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            textAlign = TextAlign.Start,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
