package com.wyutools.app.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * 每次回到前台跑一次 [onResume]。
 *
 * 课表、今日、热水三页都要在回到前台时重新对照系统日期（跨天后「本周」会变），
 * 各自抄了一遍 `DisposableEffect` + `LifecycleEventObserver` + 反注册。
 *
 * [onResume] 通过 [rememberUpdatedState] 保鲜：重组换了 lambda 也不用重挂观察者。
 */
@Composable
internal fun OnResumeEffect(onResume: () -> Unit) {
    val current by rememberUpdatedState(onResume)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) current()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

/**
 * 一次性提示：状态里带出来的 notice 显示成 Toast，随后由 [onShown] 清掉它。
 *
 * 不清理的话，同一条提示在下次重组（甚至下次进页面）还会再弹一遍。
 * [message] 为 null 表示没有待显示的提示。
 */
@Composable
internal fun WyuNoticeToast(message: String?, onShown: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        onShown()
    }
}
