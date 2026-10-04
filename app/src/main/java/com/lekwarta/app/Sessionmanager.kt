package com.lekwarta.app

import android.content.Context

object SessionManager {
    private const val PREF_NAME  = "lekwarta_session"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_NAME    = "name"
    private const val KEY_LAST_EMAIL = "last_email"

    private fun prefs(context: Context) = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun saveSession(context: Context, userId: Int, name: String, email: String) {
        prefs(context).edit()
            .putInt(KEY_USER_ID, userId)
            .putString(KEY_NAME, name)
            .putString(KEY_LAST_EMAIL, email)
            .apply()
    }

    fun getUserId(context: Context): Int = prefs(context).getInt(KEY_USER_ID, -1)

    fun getUserName(context: Context): String = prefs(context).getString(KEY_NAME, "User") ?: "User"

    /** Remembered after logout so switching back is quicker. */
    fun getLastEmail(context: Context): String = prefs(context).getString(KEY_LAST_EMAIL, "") ?: ""

    fun isLoggedIn(context: Context): Boolean = getUserId(context) != -1

    fun clearSession(context: Context) =
        prefs(context).edit().remove(KEY_USER_ID).remove(KEY_NAME).apply()
}
