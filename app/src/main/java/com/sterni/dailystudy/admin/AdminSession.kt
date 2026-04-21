package com.sterni.dailystudy.admin

import android.content.Context

object AdminSession {
    private const val PREFS = "tether_admin"
    private const val KEY_TOKEN = "admin_token"
    private const val KEY_NAME = "admin_name"
    private const val KEY_ROLE = "admin_role"

    fun save(context: Context, token: String, name: String, role: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_NAME, name)
            .putString(KEY_ROLE, role)
            .apply()
    }

    fun getToken(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TOKEN, null)

    fun getName(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_NAME, "") ?: ""

    fun getRole(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ROLE, "admin") ?: "admin"

    fun isSuperAdmin(context: Context): Boolean = getRole(context) == "superadmin"

    fun isLoggedIn(context: Context): Boolean = getToken(context) != null

    fun logout(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
