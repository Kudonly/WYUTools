package com.wyutools.app.ui

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wyutools.app.lxt.LxtApi
import com.wyutools.app.lxt.LxtViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val LevelTitles = listOf("选择校区", "选择楼栋", "选择楼层", "选择宿舍")

@Composable
fun LxtBindScreen(
    vm: LxtViewModel,
    onBack: () -> Unit,
    forValve: Boolean = false,
) {
    val ui by vm.ui.collectAsState()
    val context = LocalContext.current
    val bindListState = rememberLazyListState()
    val collapseFraction = rememberWyuCollapseFraction(bindListState)

    LaunchedEffect(Unit) { vm.startBind(forValve) }
    LaunchedEffect(ui.message) {
        ui.message?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            vm.consumeMessage()
        }
    }

    WyuPageScaffold(
        progress = { collapseFraction.value },
        maskCollapseHeight = WyuLargeTitleHeight,
        topBar = {
            WyuActionBar(
                title = if (forValve) "绑定热水设备" else "绑定宿舍",
                collapsedFraction = { collapseFraction.value },
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
                .padding(top = padding.calculateTopPadding()),
        ) {
            if (ui.bindPath.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ui.bindPath.forEachIndexed { index, node ->
                        ActionChip(
                            text = node.name,
                            onClick = { vm.goBindLevel(index) },
                        )
                    }
                }
            }

            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = LevelTitles[ui.bindLevel.coerceIn(0, LevelTitles.lastIndex)],
                    style = MiuixTheme.textStyles.title3,
                )
                if (ui.bindLevel == 3) {
                    Text(
                        text = if (forValve) {
                            "选择宿舍后将自动查询房间热水机"
                        } else {
                            "选择宿舍后将自动查询房间冷水表"
                        },
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }

            val selectedId = when (ui.bindLevel) {
                0 -> ui.bindPath.getOrNull(0)?.id
                1 -> ui.bindPath.getOrNull(1)?.id
                2 -> ui.bindPath.getOrNull(2)?.id
                else -> ui.bindRoom?.id
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when {
                    ui.bindLoading -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )

                    ui.bindItems.isEmpty() -> Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = ui.bindError ?: "该层级暂无可选内容",
                            style = MiuixTheme.textStyles.body2,
                            color = if (ui.bindError != null) {
                                MiuixTheme.colorScheme.error
                            } else {
                                MiuixTheme.colorScheme.onSurfaceVariantSummary
                            },
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            text = "重新加载",
                            onClick = { vm.goBindLevel(ui.bindLevel) },
                        )
                    }

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = bindListState,
                    ) {
                        items(ui.bindItems, key = { it.id }) { node ->
                            BasicComponent(
                                title = node.name,
                                endActions = {
                                    if (node.id == selectedId) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = MiuixTheme.colorScheme.primary,
                                        )
                                    }
                                },
                                onClick = { vm.chooseBindItem(node) },
                            )
                        }
                    }
                }
            }

            ui.bindRoom?.let { room ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    insideMargin = PaddingValues(16.dp),
                ) {
                    val machine = ui.bindMachine
                    val valveUnsupported = forValve && machine != null &&
                        LxtApi.channelWay(machine.deviceVer) == 0
                    val valveIncomplete = forValve && machine != null && machine.deviceVer.isBlank()
                    Text(
                        text = "已选宿舍：${room.name}",
                        style = MiuixTheme.textStyles.title4,
                    )
                    Spacer(Modifier.height(4.dp))
                    when {
                        !ui.bindMachineChecked -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                size = 16.dp,
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (forValve) "正在查询房间热水机…" else "正在查询房间水表…",
                                style = MiuixTheme.textStyles.footnote1,
                            )
                        }

                        machine != null -> Text(
                            text = buildString {
                                append(if (forValve) "热水机：" else "冷水表：").append(machine.machineId)
                                if (machine.deviceMac.isNotEmpty()) {
                                    append("  ·  ").append(machine.deviceMac)
                                }
                            },
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )

                        else -> Text(
                            text = ui.bindError ?: if (forValve) {
                                "该房间未查询到热水机，无法绑定"
                            } else {
                                "该房间未查询到冷水表，无法绑定"
                            },
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.error,
                        )
                    }
                    if (ui.bindError != null && machine != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = ui.bindError.orEmpty(),
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.error,
                        )
                    }
                    if (valveUnsupported || valveIncomplete) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (valveIncomplete) {
                                "设备信息不完整，无法绑定"
                            } else {
                                "该设备不支持远程开阀，请现场操作"
                            },
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.error,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { vm.confirmBind(onDone = onBack) },
                        enabled = ui.bindMachineChecked && machine != null &&
                            !valveUnsupported && !valveIncomplete,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text("确认绑定")
                    }
                }
            }
        }
    }
}
