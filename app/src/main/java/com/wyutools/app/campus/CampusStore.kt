package com.wyutools.app.campus

import android.content.Context
import java.util.UUID
import kotlin.random.Random

/**
 * Local state for the campus network tool.
 *
 * The account is always kept so the page opens pre-filled; the password only when
 * the user leaves "记住密码" on. Both live in this app's private preferences —
 * there is no keystore-worthy secret here, but nothing is ever logged.
 *
 * The portal identifies a client by (client-id, host-name, mac), and each of them
 * is regenerated per authentication attempt just like the reference client does:
 * reusing a stale identity makes the portal reject the session instead of
 * handing out a ticket.
 */
class CampusStore(context: Context) {
    private val prefs = context.getSharedPreferences("campus", Context.MODE_PRIVATE)
    private val random = Random(System.currentTimeMillis())

    var identity: CampusIdentity = newIdentity()
        private set

    var account: String
        get() = prefs.getString(KEY_ACCOUNT, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_ACCOUNT, value).apply()

    var password: String
        get() = prefs.getString(KEY_PASSWORD, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_PASSWORD, value).apply()

    var rememberPassword: Boolean
        get() = prefs.getBoolean(KEY_REMEMBER, true)
        set(value) = prefs.edit().putBoolean(KEY_REMEMBER, value).apply()

    /** Off by default: the log panel exists for troubleshooting, not for daily use. */
    var logEnabled: Boolean
        get() = prefs.getBoolean(KEY_LOG, false)
        set(value) = prefs.edit().putBoolean(KEY_LOG, value).apply()

    fun saveCredentials(account: String, password: String, remember: Boolean) {
        prefs.edit().apply {
            putString(KEY_ACCOUNT, account)
            if (remember) putString(KEY_PASSWORD, password) else remove(KEY_PASSWORD)
            putBoolean(KEY_REMEMBER, remember)
        }.apply()
    }

    fun resetIdentity() {
        identity = newIdentity()
    }

    private fun newIdentity(): CampusIdentity {
        val hostBytes = ByteArray(5).also { random.nextBytes(it) }
        val macBytes = ByteArray(6).also { random.nextBytes(it) }
        macBytes[0] = (macBytes[0].toInt() and 0xFE).toByte() // unicast
        val host = hostBytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        val mac = macBytes.joinToString(":") { "%02x".format(it.toInt() and 0xFF) }
        // UUID v4 shape, lower case — the portal compares it as text.
        val clientId = UUID.randomUUID().toString().lowercase()
        return CampusIdentity(clientId = clientId, hostName = host, mac = mac)
    }

    private companion object {
        const val KEY_ACCOUNT = "account"
        const val KEY_PASSWORD = "password"
        const val KEY_REMEMBER = "remember_password"
        const val KEY_LOG = "log_enabled"
    }
}

data class CampusIdentity(
    val clientId: String,
    val hostName: String,
    val mac: String,
)

/** Fixed values of the Android channel; they decide which Algo-IDs the portal offers. */
object CampusChannel {
    const val USER_AGENT = "CCTP/android11_64/2104"
    const val OSTAG = "Android 14"
    const val PORT = 7001
    val SERVERS = listOf("14.146.227.141", "121.8.177.212")
}
