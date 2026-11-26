package com.wyutools.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyutools.app.campus.CampusAccount
import com.wyutools.app.campus.CampusNetState
import com.wyutools.app.campus.CampusPhase
import com.wyutools.app.campus.CampusUiState
import com.wyutools.app.campus.CampusViewModel
import java.util.Locale
import kotlin.math.pow
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Campus network: session clock first, network state underneath, accounts and
 * settings moved into the top bar so the page itself stays one glance wide.
 */
@Composable
fun CampusScreen(
    vm: CampusViewModel,
    onBack: () -> Unit,
    onOpenAccounts: () -> Unit,
) {
    val ui by vm.ui.collectAsState()
    val scrollState = rememberScrollState()
    val collapseFraction = rememberWyuCollapseFraction(scrollState)

    // Probing is cheap and decides what the button does, so it runs on every visit.
    LaunchedEffect(Unit) {
        vm.probeNetwork()
    }

    WyuPageScaffold(
        progress = { collapseFraction.value },
        maskCollapseHeight = WyuLargeTitleHeight,
        topBar = {
            WyuActionBar(
                title = "校园网",
                collapsedFraction = { collapseFraction.value },
                navigationIcon = {
                    WyuTopBarIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        onClick = onBack,
                    )
                },
                actions = {
                    WyuTopBarIconButton(
                        icon = Icons.Filled.Person,
                        contentDescription = "账号管理",
                        onClick = onOpenAccounts,
                    )
                    WyuTopBarIconButton(
                        icon = Icons.Filled.Settings,
                        contentDescription = "设置",
                        onClick = vm::openSettings,
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(padding.calculateTopPadding() + 8.dp))
            StatusCard(ui = ui, vm = vm)
            Spacer(Modifier.height(12.dp))
            ActionCard(ui = ui, vm = vm, onOpenAccounts = onOpenAccounts)
            if (ui.logEnabled) {
                Spacer(Modifier.height(12.dp))
                LogCard(ui = ui, vm = vm)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // Composed unconditionally: the dialog owns its enter/exit animation, so it
    // has to stay in the tree while the exit plays.
    CampusSettingsDialog(ui = ui, vm = vm)
}

/**
 * One card answers both questions — "is it connected?" and "for how long?".
 *
 * The network verdict used to live in a card of its own, which made the eye
 * compare two places for a single state. Both now share this card and swap in
 * place: the outgoing reading blurs and fades out while the incoming one
 * sharpens under it, so a state change never leaves the card momentarily blank.
 */
@Composable
private fun StatusCard(ui: CampusUiState, vm: CampusViewModel) {
    // The clock is derived from the moment the portal accepted the session, so it
    // survives recomposition without the ViewModel ticking once per second.
    val since = ui.onlineSince
    var now by remember(since) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(since) {
        if (since == null) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            delay(1000L)
        }
    }
    val elapsed = if (since == null) 0L else ((now - since) / 1000L).coerceAtLeast(0L)

    Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "连接状态",
                style = MiuixTheme.textStyles.title3,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(enabled = !ui.busy) { vm.probeNetwork(showChecking = true) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "重新检测网络",
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        AnimatedContent(
            targetState = ui.online,
            transitionSpec = {
                // Both readings are on screen at once during the swap, which is
                // what keeps the blur readable as a defocus rather than a blink.
                (fadeIn(tween(StatusSwapMs, delayMillis = 60)) togetherWith
                    fadeOut(tween(StatusSwapMs)))
                    // clip = false: the taller reading keeps drawing past the
                    // shrinking bounds instead of being cut off at the edge.
                    .using(SizeTransform(clip = false))
            },
            contentAlignment = Alignment.TopStart,
            label = "campusStatus",
        ) { online ->
            val reveal by transition.animateFloat(
                transitionSpec = { tween(StatusSwapMs) },
                label = "campusStatusReveal",
            ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
            val f = reveal.coerceIn(0f, 1f)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = f
                        // Sub-linear: at half alpha the blur is already mostly
                        // gone, so the motion reads as focus snapping in.
                        val px = (StatusSwapBlur * (1f - f).pow(0.6f)).toPx()
                        renderEffect = if (px > 0.2f) BlurEffect(px, px) else null
                    },
            ) {
                Text(
                    text = if (online) formatDuration(elapsed) else phaseLabel(ui.phase),
                    // Largest, heaviest type on the page: this is the line the
                    // page exists to show. Tabular figures keep the digits from
                    // hopping as the seconds change.
                    style = StatusHeroStyle,
                    maxLines = 1,
                )
                // Only what the dot line below cannot already say: a failure reason,
                // or what the client is doing while it works. Repeating the network
                // verdict here just printed the same sentence twice in one card.
                val subtitle = when {
                    ui.notice != null -> ui.notice
                    online -> "保持校园 WiFi 连接即可持续在线。"
                    ui.phase == CampusPhase.Checking -> "正在检测当前网络…"
                    ui.phase == CampusPhase.Authenticating -> "正在获取票据并认证…"
                    else -> null
                }
                if (subtitle != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = subtitle,
                        style = MiuixTheme.textStyles.footnote1,
                        color = if (ui.phase == CampusPhase.Failed) {
                            MiuixTheme.colorScheme.error
                        } else {
                            MiuixTheme.colorScheme.onSurfaceVariantSummary
                        },
                    )
                }
                // The portal host and Algo-ID stay out of the card: it is
                // infrastructure detail the user cannot act on, and it named the
                // campus subnet on screen. The log panel still records both.
            }
        }
        Spacer(Modifier.height(10.dp))
        // What the gateway said about this WLAN — the answer to "do I need to log
        // in?", kept quiet under the hero line.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(netStateColor(ui.netState)),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = buildString {
                    append(netStateLabel(ui.netState))
                    if (ui.networkLabel.isNotBlank()) append(" · ").append(ui.networkLabel)
                },
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Portal host / probe reply: diagnostic, so it only earns a line while
        // there is no session to report instead.
        if (!ui.online && ui.netDetail.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = ui.netDetail,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ActionCard(ui: CampusUiState, vm: CampusViewModel, onOpenAccounts: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(20.dp)) {
        if (!ui.hasAccount) {
            Text(text = "还没有保存账号", style = MiuixTheme.textStyles.title3)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "点右上角「账号」按钮添加宽带账号，保存后即可认证。",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onOpenAccounts,
                modifier = Modifier.fillMaxWidth(),
                minHeight = 48.dp,
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text("添加账号")
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = ui.account, style = MiuixTheme.textStyles.body2)
                    Text(
                        text = "宽带账号",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                if (ui.online) {
                    Text(
                        text = "在线",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { if (ui.online) vm.disconnect() else vm.connect() },
                modifier = Modifier.fillMaxWidth(),
                minHeight = 48.dp,
                enabled = !ui.busy && !ui.cipherMissing,
                colors = if (ui.online) {
                    ButtonDefaults.buttonColors()
                } else {
                    ButtonDefaults.buttonColorsPrimary()
                },
            ) {
                Text(text = actionLabel(ui))
            }
            if (ui.cipherMissing) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "当前设备架构缺少密码库，无法认证。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun LogCard(ui: CampusUiState, vm: CampusViewModel) {
    Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "运行日志",
                style = MiuixTheme.textStyles.title3,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "清空",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { vm.clearLogs() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        val tail = ui.logs.takeLast(LOG_VISIBLE_LINES)
        if (tail.isEmpty()) {
            Text(
                text = "还没有日志。",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        } else {
            // Only the newest lines are shown: the page itself already scrolls, and
            // a second scrollable box inside it fights the gesture.
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                tail.forEach { entry ->
                    Text(
                        text = entry,
                        style = MiuixTheme.textStyles.footnote2.copy(fontFamily = FontFamily.Monospace),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}

// ---- accounts page (second level) -----------------------------------------

/**
 * Account management. A real route rather than an overlay: it enters and leaves
 * with exactly the same transition as every other second-level page in the app.
 */
@Composable
fun CampusAccountsScreen(vm: CampusViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val scrollState = rememberScrollState()
    val collapseFraction = rememberWyuCollapseFraction(scrollState)

    // Driven by [editorVisible] rather than by the target being non-null: the
    // dialog must stay composed while its own exit animation plays. [editing]
    // only carries the account, and is cleared once that animation finished.
    var editorVisible by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CampusAccount?>(null) }

    WyuPageScaffold(
        progress = { collapseFraction.value },
        maskCollapseHeight = WyuLargeTitleHeight,
        topBar = {
            WyuActionBar(
                title = "账号管理",
                collapsedFraction = { collapseFraction.value },
                navigationIcon = {
                    WyuTopBarIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        // Back closes the open editor first, so the dialog gets its
                        // exit animation instead of vanishing with the page.
                        onClick = { if (editorVisible) editorVisible = false else onBack() },
                    )
                },
                actions = {
                    WyuTopBarIconButton(
                        icon = Icons.Filled.Add,
                        contentDescription = "添加账号",
                        onClick = {
                            editing = null
                            editorVisible = true
                        },
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(padding.calculateTopPadding() + 8.dp))
            AccountsSectionHeader()
            Spacer(Modifier.height(10.dp))
            if (ui.accounts.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(20.dp)) {
                    Text(
                        text = "还没有账号，点右上角「+」添加。",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            } else {
                ui.accounts.forEachIndexed { index, account ->
                    if (index > 0) Spacer(Modifier.height(10.dp))
                    AccountCard(
                        account = account,
                        selected = account.id == ui.selected?.id,
                        onSelect = { vm.selectAccount(account.id) },
                        onEdit = {
                            editing = account
                            editorVisible = true
                        },
                        onDelete = { vm.removeAccount(account.id) },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    AccountEditorDialog(
        visible = editorVisible,
        initial = editing,
        onDismiss = { editorVisible = false },
        onDismissFinished = { editing = null },
        onSave = { name, password ->
            vm.saveAccount(name, password)
            editorVisible = false
        },
        onDelete = editing?.let { target ->
            {
                vm.removeAccount(target.id)
                editorVisible = false
            }
        },
    )
}

/** Section label outside the cards — the list itself is one card per account. */
@Composable
private fun AccountsSectionHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp),
    ) {
        Text(
            text = "已保存账号",
            style = MiuixTheme.textStyles.headline2,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "认证时使用选中的账号，点卡片即可切换。",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun AccountCard(
    account: CampusAccount,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        onClick = onSelect,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SelectRadio(selected = selected)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = account.account, style = MiuixTheme.textStyles.body2)
                Text(
                    text = if (account.password.isEmpty()) "未保存密码" else "已保存密码",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            RowIconButton(icon = Icons.Filled.Edit, description = "编辑", onClick = onEdit)
            RowIconButton(icon = Icons.Filled.Delete, description = "删除", onClick = onDelete)
        }
    }
}

/**
 * Selection disc, drawn for every account rather than only for the chosen one:
 * the empty ring is what tells the user the card can be picked at all, and the
 * fill animates so the tap visibly lands. Only one account is selected at a time —
 * the group is the whole list, there is nothing to un-select into.
 */
@Composable
private fun SelectRadio(selected: Boolean) {
    val scheme = MiuixTheme.colorScheme
    val ring by animateColorAsState(
        targetValue = if (selected) scheme.primary else scheme.outline.copy(alpha = 0.55f),
        animationSpec = tween(180),
        label = "radioRing",
    )
    val fill by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(180),
        label = "radioFill",
    )
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .border(width = 1.6.dp, color = ring, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .graphicsLayer {
                    alpha = fill
                    scaleX = fill
                    scaleY = fill
                }
                .clip(CircleShape)
                .background(scheme.primary),
        )
    }
}

@Composable
private fun RowIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun AccountEditorDialog(
    visible: Boolean,
    initial: CampusAccount?,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    onSave: (String, String) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val nameState = rememberTextFieldState("")
    val passwordState = rememberTextFieldState("")

    // Fill on entry only. Resetting while the exit animation runs would blank the
    // fields the user is still looking at.
    LaunchedEffect(visible, initial?.id) {
        if (!visible) return@LaunchedEffect
        name = initial?.account.orEmpty()
        password = initial?.password.orEmpty()
        nameState.setTextAndPlaceCursorAtEnd(name)
        passwordState.setTextAndPlaceCursorAtEnd(password)
    }

    val canSave = name.isNotBlank()

    WyuOverlayDialog(
        show = visible,
        title = if (initial == null) "添加账号" else "编辑账号",
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
    ) {
        Column(Modifier.fillMaxWidth()) {
            GlassTextField(
                state = nameState,
                label = "宽带账号",
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                ),
                onValueChange = { name = it },
            )
            Spacer(Modifier.height(12.dp))
            GlassTextField(
                state = passwordState,
                label = "宽带密码",
                secure = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                onValueChange = { password = it },
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    minHeight = 48.dp,
                    colors = ButtonDefaults.buttonColors(),
                ) {
                    Text("取消")
                }
                if (onDelete != null) {
                    Button(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                        minHeight = 48.dp,
                        colors = ButtonDefaults.buttonColors(),
                    ) {
                        Text("删除")
                    }
                }
                Button(
                    onClick = { onSave(name, password) },
                    enabled = canSave,
                    modifier = Modifier.weight(1.2f),
                    minHeight = 48.dp,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text("保存")
                }
            }
        }
    }
}

// ---- settings dialog ------------------------------------------------------

@Composable
private fun CampusSettingsDialog(ui: CampusUiState, vm: CampusViewModel) {
    WyuOverlayDialog(
        show = ui.settingsOpen,
        title = "设置",
        onDismissRequest = vm::closeSettings,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "运行日志", style = MiuixTheme.textStyles.body2)
                    Text(
                        text = "记录认证与心跳过程，排查失败时再打开",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                Switch(checked = ui.logEnabled, onCheckedChange = vm::setLogEnabled)
            }
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = vm::closeSettings,
                modifier = Modifier.fillMaxWidth(),
                minHeight = 48.dp,
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text("完成")
            }
        }
    }
}

// ---- labels ---------------------------------------------------------------

private fun phaseLabel(phase: CampusPhase): String = when (phase) {
    CampusPhase.Idle -> "未连接"
    CampusPhase.Checking -> "检测中"
    CampusPhase.Authenticating -> "认证中"
    CampusPhase.Online -> "已连接"
    CampusPhase.Failed -> "连接失败"
}

private fun netStateLabel(state: CampusNetState): String = when (state) {
    CampusNetState.Unknown -> "检测中…"
    CampusNetState.NoNetwork -> "未连接校园网络"
    CampusNetState.NeedLogin -> "需要登录"
    CampusNetState.Online -> "已可直接上网"
    CampusNetState.Failed -> "检测失败"
}

/** Colour of the status dot: amber-free, the verdict is either good, bad, or unknown. */
@Composable
private fun netStateColor(state: CampusNetState): Color = when (state) {
    CampusNetState.NeedLogin, CampusNetState.Online -> MiuixTheme.colorScheme.primary
    CampusNetState.NoNetwork, CampusNetState.Failed -> MiuixTheme.colorScheme.error
    CampusNetState.Unknown -> MiuixTheme.colorScheme.onSurfaceVariantSummary
}

/**
 * The clock face of the status card. Hoisted out of the composable so the online
 * and offline readings cannot drift apart in weight or size — the swap animation
 * relies on both occupying the same line box.
 */
private val StatusHeroStyle = TextStyle(
    fontSize = 46.sp,
    lineHeight = 54.sp,
    fontWeight = FontWeight.W800,
    letterSpacing = (-1).sp,
    fontFeatureSettings = "tnum",
    fontFamily = FontFamily.Default,
    textAlign = TextAlign.Start,
)

/** How soft the incoming reading starts while it fades in, in dp. */
private val StatusSwapBlur = 7.dp
private const val StatusSwapMs = 300

private fun actionLabel(ui: CampusUiState): String = when {
    ui.busy && ui.online -> "断开中…"
    ui.busy -> "连接中…"
    ui.online -> "断开连接"
    ui.netState == CampusNetState.Online -> "已可上网，重新认证"
    else -> "连接校园网"
}

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, secs)
}

private const val LOG_VISIBLE_LINES = 12
