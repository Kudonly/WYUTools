package com.wyutools.app.ui

import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyutools.app.auth.AuthViewModel
import com.wyutools.app.exam.ExamData
import com.wyutools.app.exam.ExamEntry
import com.wyutools.app.exam.ExamUiState
import com.wyutools.app.exam.ExamViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * 考试安排：数据来自教务系统的「学生考试安排」接口，与课表共用同一个会话，
 * 所以这页只负责把 [ExamData] 排成时间轴 —— 顶部一张总览，下面按日期分组的
 * 考场卡片。
 */
@Composable
fun ExamScreen(
    vm: ExamViewModel,
    authVm: AuthViewModel,
    onLogin: () -> Unit,
    onBack: () -> Unit,
) {
    val ui by vm.ui.collectAsState()
    val authUi by authVm.ui.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { vm.onEnter() }
    LaunchedEffect(authUi.loggedIn, authUi.restoring) {
        if (!authUi.restoring && authUi.loggedIn) vm.onAuthReady()
    }
    LaunchedEffect(ui.notice) {
        val notice = ui.notice ?: return@LaunchedEffect
        Toast.makeText(context, notice, Toast.LENGTH_SHORT).show()
        vm.consumeNotice()
    }

    val data = ui.data
    when {
        // 有缓存就先给缓存：会话掉了也不该把已经查到的考场信息换成登录页。
        data != null -> ExamContent(
            ui = ui,
            data = data,
            onRefresh = { vm.refresh(showNotice = true) },
            onBack = onBack,
        )

        authUi.restoring || authUi.autoLoggingIn || authUi.loggingOut ->
            ExamStatePage(onBack = onBack) { CircularProgressIndicator() }

        !authUi.loggedIn -> ExamStatePage(onBack = onBack) {
            StateIcon(Icons.Outlined.CalendarMonth, primary = true)
            Spacer(Modifier.height(20.dp))
            StateTitle("登录后查看考试")
            Spacer(Modifier.height(8.dp))
            StateBody("登录校园统一身份认证后，会从教务系统读取本学期的考试时间、考场与座位。")
            ui.error?.let {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = it,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onLogin,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(text = "登录统一身份认证", fontWeight = FontWeight.SemiBold)
            }
        }

        ui.loading || ui.error == null ->
            ExamStatePage(onBack = onBack) { CircularProgressIndicator() }

        else -> ExamStatePage(onBack = onBack) {
            StateIcon(Icons.Outlined.CloudOff, primary = false)
            Spacer(Modifier.height(20.dp))
            StateTitle("考试安排加载失败")
            Spacer(Modifier.height(8.dp))
            StateBody(ui.error ?: "考试安排加载失败，请稍后重试。")
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = { vm.refresh(showNotice = true) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(text = "重试", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ExamContent(
    ui: ExamUiState,
    data: ExamData,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    val listState = rememberLazyListState()
    val fraction = rememberWyuCollapseFraction(listState)
    val today = remember { LocalDate.now() }
    // 数据本来就是按日期排好序的，groupBy 保持插入顺序，所以 null 日期（没有
    // 具体考试日、只有周次的安排）自然落在最后。
    val groups = remember(data) { data.entries.groupBy { it.date } }
    val next = remember(data, today) { data.nextFrom(today) }

    WyuPageScaffold(
        progress = { fraction.value },
        maskCollapseHeight = WyuLargeTitleHeight,
        topBar = {
            WyuActionBar(
                title = "考试",
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
            item(key = "overview") {
                ExamOverview(data = data, next = next, today = today)
                Spacer(Modifier.height(20.dp))
            }

            if (data.entries.isEmpty()) {
                item(key = "empty") {
                    ExamEmptyCard(onRefresh = onRefresh)
                }
            }

            groups.forEach { (date, entries) ->
                item(key = "day-${date ?: "unknown"}") {
                    DayHeader(date = date, today = today, count = entries.size)
                }
                itemsIndexed(
                    items = entries,
                    // 同一门课可能有多条安排，key 里带上序号，避免懒加载列表
                    // 因为重复 key 抛异常。
                    key = { index, entry -> "$index-${entry.key}" },
                ) { _, entry ->
                    ExamCard(entry = entry, today = today)
                    Spacer(Modifier.height(10.dp))
                }
                item(key = "day-gap-${date ?: "unknown"}") { Spacer(Modifier.height(8.dp)) }
            }

            item(key = "footer") {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = buildString {
                        if (data.fetchedAt > 0) append(updatedLabel(data.fetchedAt))
                        append("数据来自教务系统，考试安排以教务系统为准。")
                    },
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
private fun ExamOverview(data: ExamData, next: ExamEntry?, today: LocalDate) {
    val primary = MiuixTheme.colorScheme.primary
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
                    imageVector = Icons.Outlined.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = primary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = data.termName,
                    style = MiuixTheme.textStyles.headline2,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (data.entries.isEmpty()) {
                        "本学期暂无考试安排"
                    } else {
                        "共 ${data.entries.size} 场考试"
                    },
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        if (next != null) {
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(rememberWyuSquircle(16.dp))
                    .background(primary.copy(alpha = 0.10f))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "下一场",
                            style = MiuixTheme.textStyles.footnote2,
                            fontWeight = FontWeight.SemiBold,
                            color = primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = countdownLabel(next.date, today),
                            style = MiuixTheme.textStyles.footnote2,
                            fontWeight = FontWeight.SemiBold,
                            color = primary,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = next.courseName,
                        style = MiuixTheme.textStyles.headline2,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = listOfNotNull(
                            next.date?.let { dayLabel(it) },
                            next.timeLabel,
                            next.room,
                        ).joinToString(" · "),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        } else if (data.entries.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "本学期考试已经全部结束。",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate?, today: LocalDate, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = date?.let(::dayLabel) ?: "时间待定",
            style = MiuixTheme.textStyles.footnote1,
            fontWeight = FontWeight.SemiBold,
        )
        if (date != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = weekdayLabel(date),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            val badge = countdownLabel(date, today)
            if (!date.isBefore(today)) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(rememberWyuSquircle(9.dp))
                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = badge,
                        style = MiuixTheme.textStyles.footnote2.copy(fontSize = 11.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (count > 1) {
            Text(
                text = "$count 场",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun ExamCard(entry: ExamEntry, today: LocalDate) {
    val primary = MiuixTheme.colorScheme.primary
    val soon = entry.date != null && entry.date == today
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = WyuCardCornerRadius,
        samplePageContent = false,
        insideMargin = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = entry.courseName,
                style = MiuixTheme.textStyles.headline2,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            entry.kind?.let { kind ->
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .clip(rememberWyuSquircle(9.dp))
                        .background(primary.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = kind,
                        style = MiuixTheme.textStyles.footnote2.copy(fontSize = 11.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = primary,
                        maxLines = 1,
                    )
                }
            }
        }
        val time = entry.timeLabel
        val section = entry.sectionLabel
        if (time != null || section != null) {
            Spacer(Modifier.height(10.dp))
            ExamInfoRow(
                icon = Icons.Outlined.Schedule,
                text = listOfNotNull(time, section).joinToString(" · "),
                highlight = soon,
            )
        }
        val room = entry.room
        val seat = entry.seat
        if (room != null || seat != null) {
            Spacer(Modifier.height(6.dp))
            ExamInfoRow(
                icon = Icons.Outlined.Place,
                text = listOfNotNull(
                    room ?: "考场待定",
                    seat?.let { "座位 $it" },
                ).joinToString(" · "),
            )
        }
        entry.methodLabel?.let { method ->
            Spacer(Modifier.height(6.dp))
            ExamInfoRow(icon = Icons.AutoMirrored.Outlined.Assignment, text = method)
        }
        entry.invigilators?.let { invigilators ->
            Spacer(Modifier.height(6.dp))
            ExamInfoRow(icon = Icons.Outlined.Person, text = "监考 $invigilators")
        }
        entry.remark?.let { remark ->
            Spacer(Modifier.height(6.dp))
            ExamInfoRow(icon = Icons.Outlined.Info, text = remark)
        }
    }
}

@Composable
private fun ExamInfoRow(icon: ImageVector, text: String, highlight: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (highlight) {
                MiuixTheme.colorScheme.primary
            } else {
                MiuixTheme.colorScheme.onSurfaceVariantSummary
            },
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote1,
            fontWeight = if (highlight) FontWeight.Medium else FontWeight.Normal,
            color = if (highlight) {
                MiuixTheme.colorScheme.primary
            } else {
                MiuixTheme.colorScheme.onSurfaceVariantSummary
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ExamEmptyCard(onRefresh: () -> Unit) {
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
                text = "教务系统里还没有本学期的考试安排",
                style = MiuixTheme.textStyles.headline2,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "考试安排一般在期中、期末前几周由教务处统一发布，发布后下拉刷新就能看到。",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onRefresh,
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(text = "刷新", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun StateIcon(icon: ImageVector, primary: Boolean) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(
                if (primary) {
                    MiuixTheme.colorScheme.primaryContainer
                } else {
                    MiuixTheme.colorScheme.errorContainer
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (primary) {
                MiuixTheme.colorScheme.onPrimaryContainer
            } else {
                MiuixTheme.colorScheme.onErrorContainer
            },
            modifier = Modifier.size(34.dp),
        )
    }
}

@Composable
private fun StateTitle(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.title2,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun StateBody(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun ExamStatePage(
    onBack: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    WyuPageScaffold(
        progress = { 0f },
        maskCollapseHeight = WyuLargeTitleHeight,
        topBar = {
            WyuActionBar(
                title = "考试",
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

private val DayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("M 月 d 日", Locale.CHINA)
private val ClockFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.CHINA)

private fun dayLabel(date: LocalDate): String = date.format(DayFormat).replace(" ", "")

private fun weekdayLabel(date: LocalDate): String = when (date.dayOfWeek.value) {
    1 -> "周一"
    2 -> "周二"
    3 -> "周三"
    4 -> "周四"
    5 -> "周五"
    6 -> "周六"
    else -> "周日"
}

private fun countdownLabel(date: LocalDate?, today: LocalDate): String {
    if (date == null) return "待定"
    val days = ChronoUnit.DAYS.between(today, date)
    return when {
        days < 0L -> "已结束"
        days == 0L -> "今天"
        days == 1L -> "明天"
        days == 2L -> "后天"
        days <= 7L -> "$days 天后"
        else -> "${days / 7} 周后"
    }
}

private fun updatedLabel(fetchedAt: Long): String {
    val time = runCatching {
        java.time.Instant.ofEpochMilli(fetchedAt)
            .atZone(java.time.ZoneId.systemDefault())
            .format(ClockFormat)
    }.getOrNull() ?: return ""
    return "更新于 $time · "
}
