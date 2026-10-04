package com.lekwarta.app

import android.content.Context

object SessionManager {
    private const val PREF_NAME  = "lekwarta_session"
    private const val KEY_TOKEN   = "token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_NAME    = "name"

    fun saveSession(context: Context, token: String, userId: Int, name: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_TOKEN, token)
            .putInt(KEY_USER_ID, userId)
            .putString(KEY_NAME, name)
            .apply()
    }

    fun getToken(context: Context): String? =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_TOKEN, null)

    fun getUserId(context: Context): Int =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_USER_ID, -1)

    fun getUserName(context: Context): String =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_NAME, "User") ?: "User"

    fun isLoggedIn(context: Context): Boolean = getToken(context) != null

    fun clearSession(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().clear().apply()
}