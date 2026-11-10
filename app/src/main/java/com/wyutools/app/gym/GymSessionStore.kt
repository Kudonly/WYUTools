package com.wyutools.app.gym

import android.content.Context

/**
 * The gym session is nothing but a copy of the SSO cookie, cached in its own
 * preferences so the gym page can restore itself across restarts.
 *
 * Because it is a copy, signing out of SSO has to delete it as well: otherwise
 * the stale cookie outlives the logout, survives a process restart, and the gym
 * page keeps restoring a user that is no longer signed in. [com.wyutools.app.auth.AuthViewModel]
 * calls this on logout, [GymViewModel] uses it for its own session resets.
 */
object GymSessionStore {
    private const val PREFS_NAME = "gym"
    private const val KEY_COOKIE = "cookie"
    private const val KEY_NAME = "name"
    private const val KEY_ACCOUNT = "account"

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_COOKIE)
            .remove(KEY_NAME)
            .remove(KEY_ACCOUNT)
            .apply()
    }
}
