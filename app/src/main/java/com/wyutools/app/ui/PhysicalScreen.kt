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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyutools.app.academics.AcademicsViewModel
import com.wyutools.app.academics.PhysicalData
import com.wyutools.app.academics.PhysicalTest
import com.wyutools.app.academics.PhysicalUiState
import com.wyutools.app.auth.AuthViewModel
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 体测：每学年一张总分卡片，点开进入二级页面看各项成绩。
 *
 * 子项按体测表的结构分组（形态 / 肺活量 / 速度 / 耐力 / 柔韧 / 爆发力 / 力量 /
 * 加分 / 视力），加分项与左右眼视力、屈光不正、串镜都在里面。
 */
@Composable
fun PhysicalScreen(
    vm: AcademicsViewModel,
    authVm: AuthViewModel,
    onLogin: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onBack: () -> Unit,
) {
    val ui by vm.physical.collectAsState()
    val authUi by authVm.ui.collectAsState()

    LaunchedEffect(Unit) { vm.onPhysicalEnter() }
    LaunchedEffect(authUi.loggedIn, authUi.restoring) {
        if (!authUi.restoring && authUi.loggedIn) vm.onPhysicalEnter()
    }

    val data = ui.data
    when {
        data != null -> PhysicalContent(
            ui = ui,
            data = data,
            onRefresh = { vm.refreshPhysical() },
            onBack = onBack,
            onOpen = { onOpenDetail(it.termName) },
        )

        authUi.restoring || authUi.autoLoggingIn ->
            AcademicStatePage("体测", onBack) { CircularProgressIndicator() }

        !authUi.loggedIn -> AcademicStatePage("体测", onBack) {
            AcademicLoginPrompt(
                icon = Icons.Outlined.FitnessCenter,
                title = "登录后查看体测",
                body = "登录校园统一身份认证后，会从教务系统读取每年的体测总分与各项成绩。",
                error = ui.error,
                onLogin = onLogin,
            )
        }

        ui.loading || ui.error == null ->
            AcademicStatePage("体测", onBack) { CircularProgressIndicator() }

        else -> AcademicStatePage(
            title = "体测",
            onBack = onBack,
            showRefresh = true,
            onRefresh = { vm.refreshPhysical() },
        ) {
            AcademicGlyph(Icons.Outlined.CloudOff, primary = false)
            Spacer(Modifier.height(20.dp))
            AcademicTitle("体测成绩加载失败")
            Spacer(Modifier.height(8.dp))
            AcademicBody(ui.error ?: "体测成绩加载失败，请稍后重试。", error = true)
        }
    }
}

@Composable
private fun PhysicalContent(
    ui: PhysicalUiState,
    data: PhysicalData,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onOpen: (PhysicalTest) -> Unit,
) {
    val listState = rememberLazyListState()
    val fraction = rememberWyuCollapseFraction(listState)

    WyuPageScaffold(
        progress = { fraction.value },
        maskCollapseHeight = WyuLargeTitleHeight,
        topBar = {
            WyuActionBar(
                title = "体测",
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
            item(key = "head") {
                Text(
                    text = "全国大学生体质健康测试",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    // 列表 16dp 页边距 + 12dp，与顶栏大标题对齐。
                    modifier = Modifier.padding(start = 12.dp, bottom = 10.dp),
                )
            }
            if (data.tests.isEmpty()) {
                item(key = "empty") {
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
                                text = "还没有体测成绩",
                                style = MiuixTheme.textStyles.headline2,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(6.dp))
                            AcademicBody("体测一般每学年测一次，成绩录入后这里就会出现。")
                        }
                    }
                }
            }
            items(data.tests, key = { it.termName }) { test ->
                PhysicalTestCard(test = test, onClick = { onOpen(test) })
                Spacer(Modifier.height(10.dp))
            }
            item(key = "note") {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "数据来自教务系统，缓考、免测等特殊标记以教务处记录为准。",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun PhysicalTestCard(test: PhysicalTest, onClick: () -> Unit) {
    val primary = MiuixTheme.colorScheme.primary
    val score = test.total?.toDoubleOrNull()
    val failing = score != null && score < 60
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = WyuCardCornerRadius,
        insideMargin = PaddingValues(horizontal = 18.dp, vertical = 17.dp),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(primary.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.DirectionsRun,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = primary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = test.termName,
                    style = MiuixTheme.textStyles.headline2,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = test.groups.joinToString(" · ") { it.title }.ifEmpty { "暂无明细" },
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = test.total ?: "—",
                    style = MiuixTheme.textStyles.title2.copy(fontSize = 26.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = if (failing) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    text = test.level ?: "标准总分",
                    style = MiuixTheme.textStyles.footnote2,
                    color = if (failing) {
                        MiuixTheme.colorScheme.error
                    } else {
                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                    },
                )
            }
        }
    }
}
