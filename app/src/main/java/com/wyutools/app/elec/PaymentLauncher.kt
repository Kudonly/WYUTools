package com.wyutools.app.elec

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

object PaymentLauncher {
    private const val WECHAT_PACKAGE = "com.tencent.mm"
    private const val ALIPAY_PACKAGE = "com.eg.android.AlipayGphone"

    sealed interface LaunchResult {
        data class Launched(val appLabel: String) : LaunchResult
        data class Failed(val message: String) : LaunchResult
    }

    fun appLabel(channel: ElecPayChannel): String = when (channel) {
        ElecPayChannel.WECHAT -> "微信"
        ElecPayChannel.ALIPAY -> "支付宝"
    }

    fun launch(context: Context, channel: ElecPayChannel, qrContent: String): LaunchResult {
        val content = qrContent.trim()
        if (content.isEmpty()) {
            return LaunchResult.Failed("二维码内容为空，请重新生成二维码")
        }
        val uri = runCatching { Uri.parse(content) }.getOrNull()
        val scheme = uri?.scheme?.lowercase()
        if (uri == null || scheme.isNullOrBlank()) {
            return LaunchResult.Failed("二维码内容无法识别，请使用扫一扫支付")
        }
        val target = resolveTarget(channel, scheme, uri.host?.lowercase())
            ?: return LaunchResult.Failed("无法识别该支付二维码，请使用扫一扫支付")
        if (!isInstalled(context, target.packageName)) {
            return LaunchResult.Failed(
                "未安装${target.label}，请使用其他设备扫码或保存二维码后支付"
            )
        }
        val started = buildIntents(uri, target).any { intent ->
            start(context, intent)
        }
        return if (started) {
            LaunchResult.Launched(target.label)
        } else {
            LaunchResult.Failed("无法自动打开${target.label}，请使用扫一扫扫描二维码支付")
        }
    }

    private data class Target(val packageName: String, val label: String)

    private val WECHAT = Target(WECHAT_PACKAGE, "微信")
    private val ALIPAY = Target(ALIPAY_PACKAGE, "支付宝")

    private fun resolveTarget(channel: ElecPayChannel, scheme: String, host: String?): Target? {
        return when (scheme) {
            "weixin", "wechat", "wxp" -> WECHAT
            "alipays", "alipay", "alipayqr" -> ALIPAY
            "http", "https" -> when {
                isAlipayHost(host) -> ALIPAY
                isWechatHost(host) -> WECHAT
                channel == ElecPayChannel.WECHAT -> WECHAT
                else -> ALIPAY
            }
            else -> null
        }
    }

    private fun buildIntents(uri: Uri, target: Target): List<Intent> {
        val list = mutableListOf(
            Intent(Intent.ACTION_VIEW, uri).setPackage(target.packageName)
        )
        if (target == ALIPAY && (uri.scheme == "http" || uri.scheme == "https")) {
            val scanUri = Uri.parse(
                "alipays://platformapi/startapp?saId=10000007&qrcode=${Uri.encode(uri.toString())}"
            )
            list += Intent(Intent.ACTION_VIEW, scanUri).setPackage(target.packageName)
        }
        return list
    }

    private fun start(context: Context, intent: Intent): Boolean {
        if (context.findActivity() == null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private fun Context.findActivity(): Activity? {
        var current: Context = this
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }

    private fun isAlipayHost(host: String?): Boolean =
        host == "alipay.com" || host?.endsWith(".alipay.com") == true

    private fun isWechatHost(host: String?): Boolean =
        host == "weixin.qq.com" || host?.endsWith(".weixin.qq.com") == true ||
            host == "tenpay.com" || host?.endsWith(".tenpay.com") == true ||
            host == "wechat.com" || host?.endsWith(".wechat.com") == true

    private fun isInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
