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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyutools.app.academics.AcademicsViewModel
import com.wyutools.app.academics.PhysicalGroup
import com.wyutools.app.academics.PhysicalItem
import com.wyutools.app.academics.PhysicalTest
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 体测二级页面：某一学年的总分、等级与各项成绩。
 *
 * 布局参照 iOS 版：顶部一张「总分 / 等级」双栏卡，下面按体测表的分组
 * （形态 / 肺活量 / 速度 / 耐力 / 柔韧 / 爆发力 / 力量 / 加分 / 视力）各一张卡，
 * 每行是「项目名 —— 成绩 + 得分 + 等级」。
 */
@Composable
fun PhysicalDetailScreen(
    vm: AcademicsViewModel,
    onBack: () -> Unit,
) {
    val ui by vm.physical.collectAsState()
    val term by vm.physicalDetailTerm.collectAsState()
    // 进程被回收后回到这个路由时 ViewModel 里的学期名已经没了，退到最近一次体测，
    // 总比停在一个走不出去的空白页好。
    val termName = term ?: ui.data?.latest?.termName
    val test = ui.data?.tests?.firstOrNull { it.termName == termName }

    if (test == null) {
        AcademicStatePage("体测", onBack) {
            AcademicGlyph(Icons.Outlined.CloudOff, primary = false)
            Spacer(Modifier.height(20.dp))
            AcademicTitle("没有找到这一学年的体测")
            Spacer(Modifier.height(8.dp))
            AcademicBody("返回列表后重新打开一次，或下拉刷新体测成绩。")
            Spacer(Modifier.height(20.dp))
            ActionChip(
                text = "刷新",
                onClick = { vm.refreshPhysical() },
                primary = true,
            )
        }
        return
    }

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
                        onClick = { vm.refreshPhysical() },
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
            item(key = "term") {
                Text(
                    text = test.termName,
                    style = MiuixTheme.textStyles.footnote1,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    // 列表 16dp 页边距 + 12dp，与顶栏大标题对齐。
                    modifier = Modifier.padding(start = 12.dp, bottom = 10.dp),
                )
            }
            item(key = "summary") {
                PhysicalSummaryCard(test)
                Spacer(Modifier.height(10.dp))
            }
            items(test.groups, key = { it.title }) { group ->
                PhysicalGroupCard(group)
                Spacer(Modifier.height(10.dp))
            }
            test.remark?.takeIf { it.isNotBlank() }?.let { remark ->
                item(key = "remark") {
                    Text(
                        text = remark,
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
            item(key = "note") {
                Spacer(Modifier.height(2.dp))
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

/** 顶部双栏：总分（强调色）/ 等级。 */
@Composable
private fun PhysicalSummaryCard(test: PhysicalTest) {
    val primary = MiuixTheme.colorScheme.primary
    val failing = test.total?.toDoubleOrNull()?.let { it < 60 } == true
    val totalTint = if (failing) MiuixTheme.colorScheme.error else primary
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = WyuCardCornerRadius,
        samplePageContent = false,
        insideMargin = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "总分",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = test.total ?: "—",
                    style = MiuixTheme.textStyles.headline1.copy(fontSize = 32.sp),
                    fontWeight = WyuEmphasisWeight,
                    color = totalTint,
                    maxLines = 1,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "等级",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = test.level?.takeIf { it.isNotBlank() } ?: "—",
                    style = MiuixTheme.textStyles.headline1.copy(fontSize = 32.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = if (failing) {
                        MiuixTheme.colorScheme.error
                    } else {
                        MiuixTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                )
            }
        }
    }
}

/** 一个分组一张卡：蓝色组名 + 组内各项。 */
@Composable
private fun PhysicalGroupCard(group: PhysicalGroup) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = WyuCardCornerRadius,
        samplePageContent = false,
        insideMargin = PaddingValues(horizontal = 18.dp, vertical = 15.dp),
    ) {
        Text(
            text = group.title,
            style = MiuixTheme.textStyles.footnote1,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(9.dp))
        group.items.forEachIndexed { index, item ->
            if (index > 0) Spacer(Modifier.height(9.dp))
            PhysicalDetailRow(item)
        }
    }
}

/**
 * 一行：项目名（可换行）/ 成绩 / 得分 / 等级。
 *
 * 后三项靠右成一列，同一张卡里数字会排成一条竖线；得分用强调色，等级套一个
 * 浅灰胶囊，跟截图里的层级一致。
 */
@Composable
private fun PhysicalDetailRow(item: PhysicalItem) {
    val primary = MiuixTheme.colorScheme.primary
    val summary = MiuixTheme.colorScheme.onSurfaceVariantSummary
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = item.name,
            style = MiuixTheme.textStyles.footnote1,
            color = summary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = item.value?.takeIf { it.isNotBlank() } ?: "—",
            style = MiuixTheme.textStyles.footnote1,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
        item.score?.takeIf { it.isNotBlank() }?.let { score ->
            Spacer(Modifier.width(8.dp))
            Text(
                text = score,
                style = MiuixTheme.textStyles.footnote1,
                fontWeight = FontWeight.SemiBold,
                color = primary,
                maxLines = 1,
            )
        }
        item.level?.takeIf { it.isNotBlank() }?.let { level ->
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(rememberWyuSquircle(7.dp))
                    .background(summary.copy(alpha = 0.12f))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
                Text(
                    text = level,
                    style = MiuixTheme.textStyles.footnote2,
                    color = summary,
                    maxLines = 1,
                )
            }
        }
    }
}
