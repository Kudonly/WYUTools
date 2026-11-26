package com.wyutools.app.campus

import android.content.Context
import java.util.UUID
import kotlin.random.Random
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local state for the campus network tool: the saved accounts, which one is in
 * use, the log preference, and the last portal ticket.
 *
 * Accounts live in this app's private preferences, password included — saving an
 * account is the user's statement that this password belongs here, so there is no
 * second "remember password" switch to get out of sync with the list.
 *
 * The ticket is stored as flat key/value pairs, mirroring the reference client's
 * `auth_session` store: every field the portal needs to recognise a resumed
 * session is saved separately, and [CampusAuthSession.isValid] decides whether
 * there is anything to restore at all.
 */
class CampusStore(context: Context) {
    private val prefs = context.getSharedPreferences("campus", Context.MODE_PRIVATE)
    private val random = Random(System.currentTimeMillis())

    var identity: CampusIdentity = newIdentity()
        private set

    /** Saved accounts, in the order the user added them. */
    var accounts: List<CampusAccount>
        get() = decode(prefs.getString(KEY_ACCOUNTS, null))
        set(value) = prefs.edit().putString(KEY_ACCOUNTS, encode(value)).apply()

    var selectedId: String
        get() = prefs.getString(KEY_SELECTED, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_SELECTED, value).apply()

    /** Off by default: the log panel exists for troubleshooting, not for daily use. */
    var logEnabled: Boolean
        get() = prefs.getBoolean(KEY_LOG, false)
        set(value) = prefs.edit().putBoolean(KEY_LOG, value).apply()

    /**
     * The disk ticket. Writing null means the user logged out and the portal's
     * term request was sent (or attempted) — it must not come back next launch.
     * Reading migrates the old single-JSON format on first use.
     */
    var savedSession: CampusAuthSession?
        get() = readSession()
        set(value) = writeSession(value)

    /**
     * Adds the account or replaces the one with the same name, and points
     * [selectedId] at it. Returns the stored entry (with the id that was used).
     */
    fun save(account: String, password: String, select: Boolean = true): CampusAccount {
        val name = account.trim()
        val current = accounts
        val existing = current.firstOrNull { it.account.equals(name, ignoreCase = true) }
        val stored = CampusAccount(
            id = existing?.id ?: UUID.randomUUID().toString(),
            account = name,
            password = password,
        )
        val next = if (existing == null) {
            current + stored
        } else {
            current.map { if (it.id == existing.id) stored else it }
        }
        accounts = next
        if (select) selectedId = stored.id
        return stored
    }

    /** Removes one account; the caller decides which one to select next. */
    fun remove(id: String): List<CampusAccount> {
        val next = accounts.filterNot { it.id == id }
        accounts = next
        if (selectedId == id) selectedId = next.firstOrNull()?.id.orEmpty()
        return next
    }

    fun select(id: String) {
        if (accounts.any { it.id == id }) selectedId = id
    }

    /** A new authentication always gets a fresh client-id, host-name and MAC. */
    fun resetIdentity() {
        identity = newIdentity()
    }

    /** Puts back the identity the ticket was issued to, before a heartbeat. */
    fun restoreIdentity(stored: CampusIdentity) {
        identity = stored
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

    // ---- accounts --------------------------------------------------------

    private fun encode(list: List<CampusAccount>): String {
        val array = JSONArray()
        list.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("account", entry.account)
                    put("password", entry.password)
                },
            )
        }
        return array.toString()
    }

    private fun decode(raw: String?): List<CampusAccount> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val account = item.optString("account").trim()
                if (account.isEmpty()) return@mapNotNull null
                CampusAccount(
                    id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                    account = account,
                    password = item.optString("password"),
                )
            }
        }.getOrDefault(emptyList())
    }

    // ---- ticket session --------------------------------------------------

    private fun readSession(): CampusAuthSession? {
        val ticket = prefs.getString(KEY_TICKET, "").orEmpty()
        val keepUrl = prefs.getString(KEY_KEEP_URL, "").orEmpty()
        if (ticket.isEmpty() || keepUrl.isEmpty()) {
            // One-off migration from the old single-JSON session format.
            val legacy = legacySession() ?: return null
            writeSession(legacy)
            return legacy
        }
        return CampusAuthSession(
            accountId = prefs.getString(KEY_SESSION_ACCOUNT_ID, "").orEmpty(),
            accountName = prefs.getString(KEY_SESSION_ACCOUNT, "").orEmpty(),
            ticket = ticket,
            ticketUrl = prefs.getString(KEY_TICKET_URL, "").orEmpty(),
            authUrl = prefs.getString(KEY_AUTH_URL, "").orEmpty(),
            keepUrl = keepUrl,
            termUrl = prefs.getString(KEY_TERM_URL, "").orEmpty(),
            algoId = prefs.getString(KEY_ALGO_ID, "").orEmpty(),
            clientId = prefs.getString(KEY_CLIENT_ID, "").orEmpty(),
            hostName = prefs.getString(KEY_HOST_NAME, "").orEmpty(),
            mac = prefs.getString(KEY_MAC, "").orEmpty(),
            schoolId = prefs.getString(KEY_SCHOOL_ID, "").orEmpty(),
            domain = prefs.getString(KEY_DOMAIN, "").orEmpty(),
            area = prefs.getString(KEY_AREA, "").orEmpty(),
            clientIp = prefs.getString(KEY_CLIENT_IP, "").orEmpty(),
            acIp = prefs.getString(KEY_AC_IP, "").orEmpty(),
            keepSeconds = prefs.getLong(KEY_KEEP_SECONDS, 0L),
            loginAtMs = prefs.getLong(KEY_LOGIN_AT, 0L),
        )
    }

    private fun writeSession(value: CampusAuthSession?) {
        val editor = prefs.edit()
        if (value == null) {
            SESSION_KEYS.forEach(editor::remove)
            editor.remove(KEY_SESSION) // legacy blob
        } else {
            editor.putString(KEY_SESSION_ACCOUNT_ID, value.accountId)
            editor.putString(KEY_SESSION_ACCOUNT, value.accountName)
            editor.putString(KEY_TICKET, value.ticket)
            editor.putString(KEY_TICKET_URL, value.ticketUrl)
            editor.putString(KEY_AUTH_URL, value.authUrl)
            editor.putString(KEY_KEEP_URL, value.keepUrl)
            editor.putString(KEY_TERM_URL, value.termUrl)
            editor.putString(KEY_ALGO_ID, value.algoId)
            editor.putString(KEY_CLIENT_ID, value.clientId)
            editor.putString(KEY_HOST_NAME, value.hostName)
            editor.putString(KEY_MAC, value.mac)
            editor.putString(KEY_SCHOOL_ID, value.schoolId)
            editor.putString(KEY_DOMAIN, value.domain)
            editor.putString(KEY_AREA, value.area)
            editor.putString(KEY_CLIENT_IP, value.clientIp)
            editor.putString(KEY_AC_IP, value.acIp)
            editor.putLong(KEY_KEEP_SECONDS, value.keepSeconds)
            editor.putLong(KEY_LOGIN_AT, value.loginAtMs)
        }
        editor.apply()
    }

    private fun legacySession(): CampusAuthSession? {
        val raw = prefs.getString(KEY_SESSION, null) ?: return null
        return runCatching {
            val item = JSONObject(raw)
            val ticket = item.optString("ticket")
            val ticketUrl = item.optString("ticketUrl")
            val clientId = item.optString("clientId")
            if (ticket.isEmpty() || ticketUrl.isEmpty() || clientId.isEmpty()) {
                return@runCatching null
            }
            CampusAuthSession(
                accountId = item.optString("accountId"),
                ticket = ticket,
                ticketUrl = ticketUrl,
                authUrl = item.optString("authUrl"),
                keepUrl = item.optString("keepUrl"),
                termUrl = item.optString("termUrl"),
                algoId = item.optString("algoId"),
                clientId = clientId,
                hostName = item.optString("hostName"),
                mac = item.optString("mac"),
                clientIp = item.optString("clientIp"),
                acIp = item.optString("acIp"),
                keepSeconds = item.optLong("keepSeconds", 0L),
                loginAtMs = item.optLong("onlineSince", 0L),
            )
        }.getOrNull()
    }

    private companion object {
        const val KEY_ACCOUNTS = "accounts"
        const val KEY_SELECTED = "selected_id"
        const val KEY_LOG = "log_enabled"

        // Old single-blob format, kept only for migration.
        const val KEY_SESSION = "session"

        const val KEY_SESSION_ACCOUNT_ID = "auth_account_id"
        const val KEY_SESSION_ACCOUNT = "auth_account"
        const val KEY_TICKET = "auth_ticket"
        const val KEY_TICKET_URL = "auth_ticket_url"
        const val KEY_AUTH_URL = "auth_auth_url"
        const val KEY_KEEP_URL = "auth_keep_url"
        const val KEY_TERM_URL = "auth_term_url"
        const val KEY_ALGO_ID = "auth_algo_id"
        const val KEY_CLIENT_ID = "auth_client_id"
        const val KEY_HOST_NAME = "auth_host_name"
        const val KEY_MAC = "auth_mac"
        const val KEY_SCHOOL_ID = "auth_school_id"
        const val KEY_DOMAIN = "auth_domain"
        const val KEY_AREA = "auth_area"
        const val KEY_CLIENT_IP = "auth_client_ip"
        const val KEY_AC_IP = "auth_ac_ip"
        const val KEY_KEEP_SECONDS = "auth_keep_seconds"
        const val KEY_LOGIN_AT = "auth_login_at"

        val SESSION_KEYS = listOf(
            KEY_SESSION_ACCOUNT_ID, KEY_SESSION_ACCOUNT, KEY_TICKET, KEY_TICKET_URL,
            KEY_AUTH_URL, KEY_KEEP_URL, KEY_TERM_URL, KEY_ALGO_ID, KEY_CLIENT_ID,
            KEY_HOST_NAME, KEY_MAC, KEY_SCHOOL_ID, KEY_DOMAIN, KEY_AREA,
            KEY_CLIENT_IP, KEY_AC_IP, KEY_KEEP_SECONDS, KEY_LOGIN_AT,
        )
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
