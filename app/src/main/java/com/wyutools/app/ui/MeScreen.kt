package com.wyutools.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wyutools.app.auth.AuthViewModel
import com.wyutools.app.auth.JxglStatus
import com.wyutools.app.auth.MeUiState
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MeContent(
    vm: AuthViewModel,
    onLogin: () -> Unit,
    onOpenJxgl: () -> Unit,
    scrollState: ScrollState,
    topInset: Dp,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    val ui by vm.ui.collectAsState()
    val demoMode by vm.demoMode.collectAsState()
    val context = LocalContext.current
    var showLogout by remember { mutableStateOf(false) }

    LaunchedEffect(ui.notice) {
        val notice = ui.notice ?: return@LaunchedEffect
        Toast.makeText(context, notice, Toast.LENGTH_SHORT).show()
        vm.consumeNotice()
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            ui.restoring -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = topInset),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            ui.loggedIn -> {
                LoggedInContent(
                    ui = ui,
                    demoMode = demoMode,
                    onOpenJxgl = onOpenJxgl,
                    onRetryJxgl = vm::retryJxgl,
                    onLogout = { showLogout = true },
                    scrollState = scrollState,
                    topPadding = topInset,
                    bottomInset = bottomInset,
                )
            }

            else -> {
                LoggedOutContent(
                    loginError = ui.loginError,
                    autoLoggingIn = ui.autoLoggingIn,
                    onLogin = onLogin,
                    scrollState = scrollState,
                    topPadding = topInset,
                    bottomInset = bottomInset,
                )
            }
        }
    }

    MiuixConfirmDialog(
        show = showLogout,
        title = "退出登录",
        message = "退出后将清除本机保存的登录会话，已记住的密码会保留。",
        confirmText = "退出",
        destructive = true,
        onDismiss = { showLogout = false },
        onConfirm = {
            showLogout = false
            vm.logout()
        },
    )
}

@Composable
private fun LoggedOutContent(
    loginError: String?,
    autoLoggingIn: Boolean,
    onLogin: () -> Unit,
    scrollState: ScrollState,
    topPadding: Dp,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(topPadding))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MiuixTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = if (autoLoggingIn) "正在自动登录" else "尚未登录",
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (autoLoggingIn) {
                        "登录状态已失效，正在使用本机保存的密码重新登录…"
                    } else {
                        "登录统一身份认证后，可连接并打开教务系统"
                    },
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                )
                if (loginError != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = loginError,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(20.dp))
                if (autoLoggingIn) {
                    CircularProgressIndicator(
                        size = 26.dp,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Button(
                        onClick = onLogin,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        minHeight = 48.dp,
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text("登录")
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                InfoRow(
                    icon = Icons.Outlined.School,
                    title = "教务系统",
                    description = "登录后自动建立教务系统会话，可一键打开教务系统。",
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))
                InfoRow(
                    icon = Icons.Outlined.Lock,
                    title = "隐私说明",
                    description = "勾选记住密码后，密码使用系统密钥库加密保存在本机；不勾选则仅用于本次登录。",
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))
                InfoRow(
                    icon = Icons.Outlined.VerifiedUser,
                    title = "统一身份认证",
                    description = "使用学校统一认证账号，与学校门户、教务系统密码一致。",
                )
            }
        }

        Text(
            text = "登录状态过期且已记住密码时，会自动重新登录。",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(bottomInset + 24.dp))
    }
}

@Composable
private fun LoggedInContent(
    ui: MeUiState,
    demoMode: Boolean,
    onOpenJxgl: () -> Unit,
    onRetryJxgl: () -> Unit,
    onLogout: () -> Unit,
    scrollState: ScrollState,
    topPadding: Dp,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    val profile = ui.profile
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(topPadding))
        if (profile != null) {
            val name = if (demoMode) DemoName else profile.name
            val account = if (demoMode) DemoAccount else profile.account
            val dept = if (demoMode) DemoDept else condenseDept(profile.dept)
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MiuixTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = name.take(1),
                            style = MiuixTheme.textStyles.title2,
                            color = MiuixTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.size(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = name,
                            style = MiuixTheme.textStyles.title3,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "学号 $account",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        if (!dept.isNullOrBlank()) {
                            Text(
                                text = dept,
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MiuixTheme.colorScheme.tertiaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.School,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "教务系统",
                            style = MiuixTheme.textStyles.headline2,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = jxglStatusText(ui.jxglStatus),
                            style = MiuixTheme.textStyles.footnote1,
                            color = jxglStatusColor(ui.jxglStatus),
                        )
                    }
                    when (ui.jxglStatus) {
                        JxglStatus.Connecting -> {
                            CircularProgressIndicator(
                                size = 22.dp,
                                strokeWidth = 2.dp,
                            )
                        }

                        JxglStatus.Ready -> {
                            ActionChip(text = "打开", onClick = onOpenJxgl)
                        }

                        else -> {
                            ActionChip(text = "连接", onClick = onRetryJxgl)
                        }
                    }
                }
                when (ui.jxglStatus) {
                    JxglStatus.Failed -> {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = ui.jxglError ?: "教务系统连接失败。",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.error,
                        )
                    }

                    else -> Unit
                }
            }
        }

        Button(
            onClick = onLogout,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            minHeight = 48.dp,
            colors = ButtonDefaults.buttonColors(
                color = MiuixTheme.colorScheme.errorContainer,
                contentColor = MiuixTheme.colorScheme.error,
            ),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Logout,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text("退出登录", color = MiuixTheme.colorScheme.error)
        }
        Text(
            text = "退出后将清除本机保存的登录会话。",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(bottomInset + 24.dp))
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    title: String,
    description: String,
) {
    Row {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.size(12.dp))
        Column {
            Text(
                text = title,
                style = MiuixTheme.textStyles.headline2,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = description,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

private const val DemoName = "演示同学"
private const val DemoAccount = "20230001"
private const val DemoDept = "示例学院 · 示例专业"

/** Drops slash-separated department fragments already spelled out by a longer one. */
private fun condenseDept(raw: String?): String? {
    val parts = raw?.split('/')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.distinct()
        ?: return null
    if (parts.isEmpty()) return null
    return parts
        .filter { part ->
            parts.none { other -> other.length > part.length && other.contains(part) }
        }
        .joinToString(" · ")
}

private fun jxglStatusText(status: JxglStatus): String = when (status) {
    JxglStatus.Idle -> "尚未连接"
    JxglStatus.Connecting -> "正在建立会话…"
    JxglStatus.Ready -> "会话已建立"
    JxglStatus.Failed -> "连接失败"
}

@Composable
private fun jxglStatusColor(status: JxglStatus) = when (status) {
    JxglStatus.Ready -> MiuixTheme.colorScheme.primary
    JxglStatus.Failed -> MiuixTheme.colorScheme.error
    else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
}
