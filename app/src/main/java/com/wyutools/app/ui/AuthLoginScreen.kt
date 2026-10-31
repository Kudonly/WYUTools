package com.wyutools.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.wyutools.app.auth.AuthViewModel
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AuthLoginScreen(vm: AuthViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val scrollState = rememberScrollState()
    val collapseFraction = rememberWyuCollapseFraction(scrollState)
    var username by rememberSaveable { mutableStateOf(vm.savedUsername) }
    var password by rememberSaveable { mutableStateOf(vm.savedPassword) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var rememberPassword by rememberSaveable {
        mutableStateOf(vm.savedPassword.isNotEmpty())
    }

    val toggleRememberPassword: (Boolean) -> Unit = { checked ->
        rememberPassword = checked
        vm.setRememberPasswordOption(checked)
    }

    val baselineSuccessCount = remember { ui.loginSuccessCount }
    LaunchedEffect(ui.loginSuccessCount) {
        if (ui.loginSuccessCount > baselineSuccessCount) {
            onBack()
        }
    }

    val canSubmit = !ui.loggingIn &&
        !ui.autoLoggingIn &&
        ui.captcha == null &&
        username.isNotBlank() &&
        password.isNotEmpty()

    WyuPageScaffold(
        progress = { collapseFraction.value },
        maskCollapseHeight = WyuLargeTitleHeight,
        topBar = {
            WyuActionBar(
                title = "登录教务",
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
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(padding.calculateTopPadding() + 24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MiuixTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.School,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "五邑大学统一身份认证",
                        style = MiuixTheme.textStyles.title3,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "登录后可建立教务系统会话，并直接打开教务系统。",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            Spacer(Modifier.height(28.dp))

            TextField(
                value = username,
                onValueChange = { username = it.trim() },
                label = "学号",
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            TextField(
                value = password,
                onValueChange = { password = it },
                label = "密码",
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) {
                                Icons.Outlined.VisibilityOff
                            } else {
                                Icons.Outlined.Visibility
                            },
                            contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                },
                singleLine = true,
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (canSubmit) {
                            vm.login(username, password, rememberPassword)
                        }
                    },
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Checkbox) { toggleRememberPassword(!rememberPassword) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    state = if (rememberPassword) ToggleableState.On else ToggleableState.Off,
                    onClick = null,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "记住密码",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurface,
                )
            }

            val loginError = ui.loginError
            if (loginError != null) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = loginError,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { vm.login(username, password, rememberPassword) },
                enabled = canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                minHeight = 52.dp,
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                if (ui.loggingIn || ui.autoLoggingIn) {
                    CircularProgressIndicator(
                        size = 20.dp,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("登录", fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "账号即学号，密码与学校门户一致。勾选记住密码后，密码使用系统密钥库加密保存在本机。",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
            if (ui.captcha != null) {
                Text(
                    text = "当前网络环境需要完成安全验证，请在弹窗中拖动滑块。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    val challenge = ui.captcha
    if (challenge != null) {
        SliderCaptchaDialog(
            challenge = challenge,
            verifying = ui.captchaVerifying,
            error = ui.captchaError,
            onVerify = vm::submitCaptcha,
            onRefresh = vm::refreshCaptcha,
            onDismiss = vm::cancelCaptcha,
        )
    }
}
