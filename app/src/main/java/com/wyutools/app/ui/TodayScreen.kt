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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wyutools.app.auth.AuthViewModel
import com.wyutools.app.schedule.ScheduleCourse
import com.wyutools.app.schedule.ScheduleData
import com.wyutools.app.schedule.ScheduleNotice
import com.wyutools.app.schedule.ScheduleSections
import com.wyutools.app.schedule.ScheduleUiState
import com.wyutools.app.schedule.ScheduleViewModel
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 今日：从课表里拆出来的单日视图。
 *
 * 以前它是课表里的「壹日」分页，和「邑周」共用一套 pager；现在单独立成一页，
 * 因为它是「今天要做什么」的入口——课程和待办都在这里。页面按「日期 → 待办 →
 * 今日课程」分段，而不是继续沿用课表那套翻页结构。
 */
@Composable
fun TodayScreen(
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

    // 待办不依赖登录，所以这一页永远渲染；课表那一段自己按登录/加载状态退化成
    // 一张提示卡，而不是把整页换成登录页——否则未登录时连待办都进不去。
    TodayContent(
        vm = vm,
        ui = ui,
        data = ui.data,
        authLoggedIn = authUi.loggedIn,
        authBusy = authUi.restoring || authUi.autoLoggingIn || authUi.loggingOut,
        onBack = onBack,
        onLogin = onLogin,
    )
}

@Composable
private fun TodayContent(
    vm: ScheduleViewModel,
    ui: ScheduleUiState,
    data: ScheduleData?,
    authLoggedIn: Boolean,
    authBusy: Boolean,
    onBack: () -> Unit,
    onLogin: () -> Unit,
) {
    val listState = rememberLazyListState()
    val collapseFraction = rememberWyuCollapseFraction(listState)
    val context = LocalContext.current
    var selectedCourse by remember { mutableStateOf<SelectedCourse?>(null) }
    var showCourseDetail by remember { mutableStateOf(false) }
    val now = rememberCurrentMinute()
    val today = now.toLocalDate()
    val nowMinutes = now.hour * 60 + now.minute
    // 没登录时没有课表，也就没有「第几周」——日期头把这段省掉，而不是编一个 1。
    val currentWeek = data?.currentWeek(ui.weekAnchor, today)
    // 全天保留：今日页要能回看已经上完的课，课表卡片那边才是「还剩什么」。
    val courses = remember(data, today, currentWeek) {
        if (data == null || currentWeek == null) {
            emptyList()
        } else {
            data.coursesOn(today.dayOfWeek.value, currentWeek)
        }
    }
    // The checklist is day-scoped, so re-reading it whenever the date rolls over
    // is also what clears yesterday's list.
    val todos by WyuTodos.items.collectAsState()
    var draft by remember { mutableStateOf("") }
    LaunchedEffect(today) { WyuTodos.ensureLoaded(context, today) }

    LaunchedEffect(ui.notice) {
        val notice: ScheduleNotice = ui.notice ?: return@LaunchedEffect
        Toast.makeText(context, notice.message, Toast.LENGTH_SHORT).show()
        vm.consumeNotice()
    }

    WyuPageScaffold(
        progress = { collapseFraction.value },
        maskCollapseHeight = WyuLargeTitleHeight,
        maskExtension = 34.dp,
        topBar = {
            WyuActionBar(
                title = "今日",
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
                        label = "刷新今日课程",
                        onClick = {
                            if (authLoggedIn) vm.refresh(showNotice = true) else onLogin()
                        },
                        enabled = !ui.refreshing,
                    )
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding(),
            state = listState,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "today-top") {
                Spacer(Modifier.height(padding.calculateTopPadding() + 4.dp))
            }
            item(key = "today-date") {
                TodayDateHeader(
                    today = today,
                    week = currentWeek,
                    courseCount = if (data != null) courses.size else null,
                )
            }
            item(key = "today-todos") {
                TodayTodoCard(
                    todos = todos,
                    summary = if (todos.isEmpty()) {
                        null
                    } else {
                        "完成 ${todos.count { it.done }}/${todos.size}"
                    },
                    draft = draft,
                    onDraftChange = { draft = it },
                    onAdd = {
                        if (draft.isNotBlank()) {
                            WyuTodos.add(context, draft)
                            draft = ""
                        }
                    },
                    onToggle = { WyuTodos.toggle(context, it) },
                    onDelete = { WyuTodos.remove(context, it) },
                )
            }
            item(key = "today-section") {
                TodaySectionHeader(
                    title = "今天的课",
                    trailing = if (courses.isEmpty()) null else "${courses.size} 节",
                    compact = true,
                )
            }
            when {
                data != null && courses.isEmpty() -> item(key = "today-empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
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

                data != null -> items(courses, key = { it.id }) { course ->
                    TodayCourseCard(
                        course = course,
                        week = currentWeek ?: 1,
                        nowMinutes = nowMinutes,
                        onClick = {
                            selectedCourse = SelectedCourse(course, today, currentWeek ?: 1)
                            showCourseDetail = true
                        },
                    )
                }

                // 课表这一段的「进不来」都退化成本区块里的一行提示，日期和待办
                // 照常在上面，页面不会整页变成登录态。
                authBusy -> item(key = "today-course-loading") {
                    TodayCourseStateCard(message = "正在读取课表…", loading = true)
                }

                !authLoggedIn -> item(key = "today-course-login") {
                    TodayCourseLoginCard(onLogin = onLogin)
                }

                ui.error != null -> item(key = "today-course-error") {
                    TodayCourseStateCard(
                        message = ui.error,
                        loading = false,
                        onRetry = { vm.refresh(showNotice = true) },
                    )
                }

                else -> item(key = "today-course-loading") {
                    TodayCourseStateCard(message = "正在读取课表…", loading = true)
                }
            }
        }
    }

    selectedCourse?.let { selection ->
        ScheduleCourseDialog(
            selection = selection,
            show = showCourseDetail,
            termName = data?.termName.orEmpty(),
            onDismiss = { showCourseDetail = false },
        )
    }
}

@Composable
private fun TodayDateHeader(
    today: java.time.LocalDate,
    week: Int?,
    courseCount: Int?,
) {
    // 列表有 20dp 页边距，这里补 8dp 才能和顶栏大标题同一条左边线。
    Column(Modifier.fillMaxWidth().padding(start = 8.dp)) {
        // 日期和星期同号同粗：这是页面的第一落点，比导航栏标题还重要。
        Text(
            text = "${today.monthValue} 月 ${today.dayOfMonth} 日 ${weekdayName(today)}",
            style = MiuixTheme.textStyles.headline1.copy(
                fontSize = 30.sp,
                lineHeight = 36.sp,
            ),
            fontWeight = FontWeight.Bold,
            // 字号调到特大时「12 月 31 日 星期三」放不进一行，允许折成两行，
            // 但不要截断——日期读不全比换行难受得多。
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = buildString {
                append("周${weekdayShort(today)}")
                // 未登录时没有课表，说不出「几节课」；有课表时直接给数量。
                courseCount?.let { append(" · $it 节课") }
                week?.let { append(" · 第 $it 周") }
            },
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

private fun weekdayShort(date: java.time.LocalDate): String = when (date.dayOfWeek.value) {
    1 -> "一"
    2 -> "二"
    3 -> "三"
    4 -> "四"
    5 -> "五"
    6 -> "六"
    else -> "日"
}

/**
 * 分段小标题。[compact] 是课程那一段用的：缩成一行灰色小字贴在卡片外面，
 * 与待办卡的标题（卡内、字号更大）区分开，视觉上不会抢同一层。
 */
@Composable
private fun TodaySectionHeader(
    title: String,
    trailing: String?,
    compact: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                // 和顶栏大标题对齐：20dp 页边距 + 8dp。
                start = 8.dp,
                end = if (compact) 4.dp else 0.dp,
                top = if (compact) 10.dp else 6.dp,
                bottom = if (compact) 0.dp else 2.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = if (compact) {
                MiuixTheme.textStyles.footnote1
            } else {
                MiuixTheme.textStyles.headline2
            },
            fontWeight = FontWeight.SemiBold,
            color = if (compact) {
                MiuixTheme.colorScheme.onSurfaceVariantSummary
            } else {
                MiuixTheme.colorScheme.onSurface
            },
        )
        Spacer(Modifier.weight(1f))
        if (trailing != null) {
            Text(
                text = trailing,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

/**
 * 课表这半页在未登录时的样子：不是整页的登录墙，而是一张卡——上半页的待办
 * 照样能用，用户只是看不了课。
 */
@Composable
private fun TodayCourseLoginCard(onLogin: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(20.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = "登录后查看今天的课",
                style = MiuixTheme.textStyles.headline2,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "待办不用登录也能用；课程要在统一身份认证登录后从教务系统读取。",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onLogin,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text("去登录")
            }
        }
    }
}

/** 课表这一段的加载 / 失败状态，尽量轻，别把待办挤下去。 */
@Composable
private fun TodayCourseStateCard(
    message: String,
    loading: Boolean,
    onRetry: (() -> Unit)? = null,
) {
    val secondary = MiuixTheme.colorScheme.onSurfaceVariantSummary
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                CircularProgressIndicator(size = 18.dp, strokeWidth = 2.dp)
            } else {
                Icon(
                    imageVector = Icons.Outlined.CloudOff,
                    contentDescription = null,
                    tint = secondary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = message,
                style = MiuixTheme.textStyles.body2,
                color = secondary,
                modifier = Modifier.weight(1f),
            )
            if (onRetry != null) {
                Spacer(Modifier.width(8.dp))
                TextButton(text = "重试", onClick = onRetry)
            }
        }
    }
}

// 待办输入框的内边距：左右保持 16dp，竖向收窄到 11dp（默认 16dp），
// 同一行的添加按钮相应放大到 46dp。
private val TodoFieldInsideMargin = DpSize(16.dp, 11.dp)

/**
 * Today's checklist: one input on top, then the lines themselves.
 *
 * The whole list lives in a single card instead of one card per line — a
 * checklist reads as one object, and the rows stay compact enough that the
 * courses below are still visible without scrolling.
 */
@Composable
private fun TodayTodoCard(
    todos: List<TodayTodo>,
    summary: String?,
    draft: String,
    onDraftChange: (String) -> Unit,
    onAdd: () -> Unit,
    onToggle: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val primary = MiuixTheme.colorScheme.primary
    val onPrimary = MiuixTheme.colorScheme.onPrimary
    val view = LocalView.current
    val canAdd = draft.isNotBlank()
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "待办",
                style = MiuixTheme.textStyles.headline2,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassTextField(
                value = draft,
                onValueChange = onDraftChange,
                label = "写点今天要做的事",
                modifier = Modifier.weight(1f),
                // 竖向内边距比默认小一档：输入框的底色薄一点，不抢待办列表的版面。
                insideMargin = TodoFieldInsideMargin,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onAdd() }),
            )
            Spacer(Modifier.width(14.dp))
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(primary.copy(alpha = if (canAdd) 1f else 0.35f))
                    .clickable(enabled = canAdd) {
                        WyuHaptics.tap(view)
                        onAdd()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "添加待办",
                    tint = onPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        if (todos.isEmpty()) {
            Text(
                text = "今天还空着，加一条试试。",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = 4.dp, top = 12.dp),
            )
        } else {
            Spacer(Modifier.height(6.dp))
            todos.forEachIndexed { index, todo ->
                if (index > 0) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 34.dp)
                            .height(1.dp)
                            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
                    )
                }
                TodoRow(
                    todo = todo,
                    onToggle = { onToggle(todo.id) },
                    onDelete = { onDelete(todo.id) },
                )
            }
        }
    }
}

@Composable
private fun TodoRow(
    todo: TodayTodo,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val view = LocalView.current
    val scheme = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                WyuHaptics.tap(view)
                onToggle()
            }
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (todo.done) {
                Icons.Rounded.CheckCircle
            } else {
                Icons.Rounded.RadioButtonUnchecked
            },
            contentDescription = if (todo.done) "标记为未完成" else "标记为已完成",
            tint = if (todo.done) scheme.primary else scheme.onSurfaceVariantSummary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = todo.text,
            style = MiuixTheme.textStyles.body1,
            color = if (todo.done) scheme.onSurfaceVariantSummary else scheme.onSurface,
            textDecoration = if (todo.done) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable {
                    WyuHaptics.tap(view)
                    onDelete()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "删除待办",
                tint = scheme.onSurfaceVariantSummary.copy(alpha = 0.75f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun TodayCourseCard(
    course: ScheduleCourse,
    week: Int,
    nowMinutes: Int,
    onClick: () -> Unit,
) {
    val ongoing = course.isOngoingAt(nowMinutes)
    val finished = course.hasEndedAt(nowMinutes)
    val room = course.roomForWeek(week)
    val scheme = MiuixTheme.colorScheme
    val accent = scheme.primary
    // 上完的课不该和接下来的课一样显眼，整行压淡即可，不额外加标签。
    val rowAlpha = if (finished) 0.5f else 1f
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(courseStripe(course).copy(alpha = rowAlpha)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = course.name,
                        // 课程名是这一行里唯一要看的内容：字号比正文大一号、字重拉满，
                        // 一眼就能从时间和地点里跳出来。
                        style = MiuixTheme.textStyles.body1.copy(
                            fontSize = 18.sp,
                            lineHeight = 23.sp,
                        ),
                        fontWeight = WyuEmphasisWeight,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .alpha(rowAlpha),
                    )
                    if (ongoing) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.14f))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = "上课中",
                                style = MiuixTheme.textStyles.footnote2,
                                fontWeight = FontWeight.Medium,
                                color = accent,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    text = buildString {
                        append(courseTimeLabel(course))
                        if (room.isNotBlank()) {
                            append("  ")
                            append(room)
                        }
                    },
                    style = MiuixTheme.textStyles.footnote1,
                    color = scheme.onSurfaceVariantSummary.copy(alpha = rowAlpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 时间不加空格：14:30–16:05 这种写法在这一行里更紧凑，也不会被地点挤到换行。 */
private fun courseTimeLabel(course: ScheduleCourse): String {
    val start = course.startTime.ifBlank {
        ScheduleSections.defaultStart(course.startSection).orEmpty()
    }
    val end = course.endTime.ifBlank {
        ScheduleSections.defaultEnd(course.endSection).orEmpty()
    }
    return when {
        start.isNotBlank() && end.isNotBlank() -> "$start\u2013$end"
        start.isNotBlank() -> "$start 开始"
        course.startSection == course.endSection -> "第 ${course.startSection} 节"
        else -> "第 ${course.startSection}-${course.endSection} 节"
    }
}
