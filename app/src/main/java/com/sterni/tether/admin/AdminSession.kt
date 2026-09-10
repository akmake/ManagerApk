package com.sterni.tether.admin

import android.content.Context
import android.util.Log
import com.sterni.tether.data.api.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The signed-in manager's session.
 *
 * The session is deliberately permanent: the token is kept in SharedPreferences with no expiry,
 * and the server issues admin tokens with no expiry either (revocation runs through the admin's
 * tokenVersion, not a clock). A manager therefore signs in once and stays signed in — until
 * [logout] is called, which is the only thing that ends a session.
 *
 * Note this session also drives `managerExempt` in [TetherAccessibilityService]: while an admin is
 * signed in on a device, the community protections don't apply to it. That is what makes an
 * abandoned session on a shared device worth ending explicitly, and why logout revokes server-side
 * rather than only clearing local storage.
 */
object AdminSession {
    private const val TAG = "AdminSession"
    private const val PREFS = "tether_admin"
    private const val KEY_TOKEN = "admin_token"
    private const val KEY_NAME = "admin_name"
    private const val KEY_ROLE = "admin_role"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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

    /**
     * Ends the session the user asked to end.
     *
     * Tells the server to revoke the token (fire-and-forget — this also signs the same admin out of
     * the web panel and any other device), then clears local storage immediately. Local state is
     * cleared whether or not the network call succeeds, so being offline can never trap a manager
     * in a session they asked to leave.
     */
    fun logout(context: Context) {
        val token = getToken(context)
        if (!token.isNullOrEmpty()) {
            scope.launch {
                runCatching { RetrofitClient.authApi.logout(token) }
                    .onFailure { Log.w(TAG, "Server logout failed (clearing locally anyway): ${it.message}") }
            }
        }
        clearLocal(context)
    }

    /**
     * Drops the local session WITHOUT calling the server — for when the server has already rejected
     * the token (401), so there is nothing left to revoke. See the unauthorized handler wired up in
     * [com.sterni.tether.TetherApp].
     */
    fun clearLocal(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
