package com.wyutools.app.core

import com.wyutools.app.auth.SsoException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 访问校园服务的通用失败文案：统一身份认证、DNS、连接、超时、明文 HTTP。
 *
 * 以前每个 ViewModel 各写一份 `when`，同一类异常在五个页面有五种说法。这里
 * 收成一份，文案只改一处。
 *
 * 不认识的异常返回 `null`，交给调用方兜底——各模块仍保留自己的「课表加载失败」
 * 这类更具体的文案，以及自己的异常类型（解析失败等）。
 *
 * [campusOnly] 用于只在校园网内可达的服务（电费、热水、体育），提示里点明
 * 要连校园网，比泛泛的「网络连接失败」有用；[serverLabel] 是这些服务在提示
 * 里的自称（「电费服务器」「学校服务器」）。
 */
internal fun friendlyAccessMessage(
    error: Throwable,
    campusOnly: Boolean = false,
    serverLabel: String = "学校服务器",
): String? =
    when (error) {
        is SsoException -> error.message ?: "教务系统访问失败。"
        is UnknownHostException ->
            if (campusOnly) {
                "无法连接$serverLabel，请连接校园网后重试"
            } else {
                "网络连接失败，请检查网络后重试。"
            }
        is SocketTimeoutException ->
            if (campusOnly) {
                "连接${serverLabel}超时，请稍后重试"
            } else {
                "网络连接超时，请稍后重试。"
            }
        is ConnectException ->
            if (campusOnly) {
                "无法连接$serverLabel，请确认已连接校园网"
            } else {
                "无法连接服务器，请稍后重试。"
            }
        // 系统拦下明文 HTTP 时 IOException 的 message 里会带 CLEARTEXT，
        // 这不是网络问题，说成「连不上」会让人一直去查 Wi-Fi。
        is IOException ->
            when {
                error.message.orEmpty().contains("CLEARTEXT", ignoreCase = true) ->
                    "系统阻止了明文 HTTP 请求，请更新 App 后重试"
                campusOnly -> "网络请求失败，请检查校园网连接"
                else -> "网络连接失败，请稍后重试。"
            }
        else -> null
    }
