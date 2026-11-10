package com.wyutools.app.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.wyutools.app.auth.AuthViewModel
import com.wyutools.app.auth.JxglStatus
import com.wyutools.app.auth.SsoApi
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun JxglWebScreen(
    vm: AuthViewModel,
    onBack: () -> Unit,
    onLogin: () -> Unit,
) {
    val ui by vm.ui.collectAsState()
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }
    var webScrollPx by remember { mutableFloatStateOf(0f) }
    var authBlocked by remember { mutableStateOf(false) }
    var autoRecoveryAttempted by remember { mutableStateOf(false) }
    var awaitingJxgl by remember { mutableStateOf(false) }
    val rangePx = with(LocalDensity.current) { 64.dp.toPx() }
    val collapseFraction = remember(rangePx) {
        derivedStateOf { (webScrollPx / rangePx).coerceIn(0f, 1f) }
    }
    val cookies = remember { vm.cookieEntries() }

    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }
    }

    LaunchedEffect(authBlocked) {
        if (!authBlocked || autoRecoveryAttempted) return@LaunchedEffect
        autoRecoveryAttempted = true
        awaitingJxgl = true
        vm.recoverJxglSession()
    }

    LaunchedEffect(authBlocked, awaitingJxgl, ui.jxglStatus) {
        if (!authBlocked || !awaitingJxgl) return@LaunchedEffect
        if (ui.jxglStatus != JxglStatus.Ready) return@LaunchedEffect
        awaitingJxgl = false
        authBlocked = false
        loading = true
        val view = webView
        if (view != null) {
            resetWebViewCookies(vm.cookieEntries()) {
                view.post { view.loadUrl(JXGL_HOME) }
            }
        }
    }

    BackHandler(enabled = canGoBack && !authBlocked) {
        webView?.goBack()
    }

    WyuPageScaffold(
        progress = { collapseFraction.value },
        maskCollapseHeight = WyuLargeTitleHeight,
        maskBaseAlpha = 1f,
        topBar = {
            WyuActionBar(
                title = "教务系统",
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
                        label = "刷新",
                        onClick = { webView?.reload() },
                    )
                },
            )
        },
    ) { padding ->
        val contentTop = (padding.calculateTopPadding() - WyuLargeTitleHeight)
            .coerceAtLeast(0.dp)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = contentTop),
        ) {
            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        object : WebView(context) {
                            override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
                                super.onScrollChanged(l, t, oldl, oldt)
                                webScrollPx = t.toFloat()
                            }
                        }.apply {
                            // System WebView 154 + Vulkan SIGTRAPs in its functor draw
                            // path on this device; software layers sidestep it.
                            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.loadsImagesAutomatically = true
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.userAgentString = SsoApi.USER_AGENT
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance()
                                .setAcceptThirdPartyCookies(this, true)
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                ): Boolean {
                                    val target = request?.url?.toString()
                                    if (request?.isForMainFrame == true && isAuthServerUrl(target)) {
                                        authBlocked = true
                                        loading = false
                                        return true
                                    }
                                    return false
                                }

                                override fun onPageStarted(
                                    view: WebView?,
                                    url: String?,
                                    favicon: Bitmap?,
                                ) {
                                    if (isAuthServerUrl(url)) {
                                        view?.stopLoading()
                                        authBlocked = true
                                        loading = false
                                        return
                                    }
                                    loading = true
                                    webScrollPx = 0f
                                }

                                override fun onPageFinished(
                                    view: WebView?,
                                    url: String?,
                                ) {
                                    loading = false
                                    canGoBack = view?.canGoBack() == true
                                    if (url != null && !isAuthServerUrl(url)) {
                                        view?.evaluateJavascript(JXGL_LOGIN_PROBE) { result ->
                                            when (result?.trim()?.trim('"')) {
                                                "1" -> {
                                                    authBlocked = true
                                                    loading = false
                                                }
                                                "0" -> autoRecoveryAttempted = false
                                            }
                                        }
                                    }
                                    view?.let(::fixZeroViewportHeight)
                                }
                            }
                            webView = this
                            resetWebViewCookies(cookies) {
                                post { loadUrl(JXGL_HOME) }
                            }
                        }
                    },
                )
                if (authBlocked) {
                    JxglAuthOverlay(
                        loggedIn = ui.loggedIn,
                        autoLoggingIn = ui.autoLoggingIn,
                        connecting = ui.jxglStatus == JxglStatus.Connecting,
                        error = ui.jxglError,
                        onRetryConnect = {
                            awaitingJxgl = true
                            vm.retryJxgl()
                        },
                        onLogin = onLogin,
                        onBack = onBack,
                    )
                }
            }
        }
    }
}

@Composable
private fun JxglAuthOverlay(
    loggedIn: Boolean,
    autoLoggingIn: Boolean,
    connecting: Boolean,
    error: String?,
    onRetryConnect: () -> Unit,
    onLogin: () -> Unit,
    onBack: () -> Unit,
) {
    val recovering = autoLoggingIn || connecting
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "教务系统登录已失效",
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = when {
                        autoLoggingIn -> "正在自动重新登录…"
                        connecting -> "正在重新建立教务系统会话…"
                        loggedIn -> error ?: "教务系统会话建立失败，请重试。"
                        else -> "统一身份认证会话已过期，请重新登录后继续使用。"
                    },
                    style = MiuixTheme.textStyles.body2,
                    color = if (loggedIn && !recovering) {
                        MiuixTheme.colorScheme.error
                    } else {
                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                    },
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                when {
                    recovering -> {
                        CircularProgressIndicator(size = 26.dp, strokeWidth = 2.dp)
                    }

                    loggedIn -> {
                        Button(
                            onClick = onRetryConnect,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            minHeight = 48.dp,
                            colors = ButtonDefaults.buttonColorsPrimary(),
                        ) {
                            Text("重新连接")
                        }
                        Spacer(Modifier.height(4.dp))
                        TextButton(text = "返回", onClick = onBack)
                    }

                    else -> {
                        Button(
                            onClick = onLogin,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            minHeight = 48.dp,
                            colors = ButtonDefaults.buttonColorsPrimary(),
                        ) {
                            Text("重新登录")
                        }
                        Spacer(Modifier.height(4.dp))
                        TextButton(text = "返回", onClick = onBack)
                    }
                }
            }
        }
    }
}

// With the software layer this WebView resolves 100vh and percentage root
// heights to 0, collapsing the JXGL shell. Give html/body an explicit height.
private fun fixZeroViewportHeight(view: WebView) {
    view.evaluateJavascript(
        "(function(){if(window.__wyuVpFix)return;window.__wyuVpFix=1;" +
            "var h=document.documentElement,b=document.body;" +
            "function f(){var p=window.innerHeight+'px';" +
            "h.style.setProperty('height',p,'important');" +
            "b.style.setProperty('height',p,'important');}" +
            "f();window.addEventListener('resize',f);})()",
        null,
    )
}

private fun isAuthServerUrl(url: String?): Boolean =
    url?.contains("authserver.wyu.edu.cn", ignoreCase = true) == true

private fun resetWebViewCookies(
    cookies: List<com.wyutools.app.auth.CookieEntry>,
    onReady: () -> Unit,
) {
    val manager = CookieManager.getInstance()
    manager.removeAllCookies {
        manager.flush()
        val valid = cookies.filter { it.domain.removePrefix(".").isNotEmpty() }
        if (valid.isEmpty()) {
            onReady()
            return@removeAllCookies
        }
        var remaining = valid.size
        for (entry in valid) {
            val host = entry.domain.removePrefix(".")
            val cookie = buildString {
                append(entry.name).append('=').append(entry.value)
                append("; Path=").append(entry.path)
            }
            manager.setCookie("https://$host/", cookie) {
                remaining -= 1
                if (remaining == 0) {
                    manager.flush()
                    onReady()
                }
            }
        }
    }
}

private const val JXGL_HOME = "https://jxgl.wyu.edu.cn/"

private const val JXGL_LOGIN_PROBE =
    "(function(){return document.getElementById('login-form')?'1':'0';})()"
