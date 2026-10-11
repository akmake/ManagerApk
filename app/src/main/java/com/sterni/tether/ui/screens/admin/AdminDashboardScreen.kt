@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sterni.tether.ui.screens.admin

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sterni.tether.admin.AdminSession
import com.sterni.tether.data.api.AdminApiService
import com.sterni.tether.data.api.RetrofitClient
import com.sterni.tether.data.model.ActivityItem
import com.sterni.tether.data.model.DashboardStats
import com.sterni.tether.ui.components.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AdminDashboardViewModel(app: Application) : AndroidViewModel(app) {
    private val api = RetrofitClient.create(AdminApiService::class.java)
    private val _stats = MutableStateFlow<DashboardStats?>(null)
    val stats: StateFlow<DashboardStats?> = _stats
    private val _activity = MutableStateFlow<List<ActivityItem>>(emptyList())
    val activity: StateFlow<List<ActivityItem>> = _activity
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun load(token: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val statsRes = api.getDashboard(token)
                val activityRes = api.getActivity(token)
                if (!statsRes.isSuccessful || !activityRes.isSuccessful) _error.value = "לא ניתן לטעון את נתוני הקהילה. נסה לרענן."
                if (statsRes.isSuccessful) _stats.value = statsRes.body()
                if (activityRes.isSuccessful) _activity.value = activityRes.body().orEmpty()
            } catch (_: Exception) { _error.value = "אין חיבור לשרת. כלי הניהול נשארים זמינים." }
            finally { _loading.value = false }
        }
    }
}

@Composable
fun AdminDashboardScreen(
    onCommunitiesClick: () -> Unit, onApprovalsClick: () -> Unit, onProvisioningClick: () -> Unit,
    onGlobalOverviewClick: () -> Unit = {}, onApprovedAppsClick: () -> Unit = {},
    onSelfToolsClick: () -> Unit = {}, onDiagnosticsClick: () -> Unit = {}, onPermissionsClick: () -> Unit = {},
    onManageAdminsClick: () -> Unit = {}, onLogout: () -> Unit,
    vm: AdminDashboardViewModel = viewModel()
) {
    val context = LocalContext.current
    val token = AdminSession.getToken(context).orEmpty()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val activity by vm.activity.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    LaunchedEffect(token) { vm.load(token) }
    AdminDashboardContent(AdminSession.getName(context), AdminSession.isSuperAdmin(context), stats, activity,
        loading, error, onCommunitiesClick, onApprovalsClick, onProvisioningClick,
        onGlobalOverviewClick, onApprovedAppsClick, onSelfToolsClick, onDiagnosticsClick,
        onPermissionsClick, onManageAdminsClick,
        onLogout = { AdminSession.logout(context); onLogout() }, onRefresh = { vm.load(token) })
}

@Composable
fun AdminDashboardContent(
    adminName: String, isSuperAdmin: Boolean, stats: DashboardStats?, activity: List<ActivityItem>,
    loading: Boolean = false, error: String? = null,
    onCommunitiesClick: () -> Unit = {}, onApprovalsClick: () -> Unit = {}, onProvisioningClick: () -> Unit = {},
    onGlobalOverviewClick: () -> Unit = {}, onApprovedAppsClick: () -> Unit = {}, onSelfToolsClick: () -> Unit = {},
    onDiagnosticsClick: () -> Unit = {}, onPermissionsClick: () -> Unit = {}, onManageAdminsClick: () -> Unit = {},
    onLogout: () -> Unit = {}, onRefresh: () -> Unit = {}
) {
    TetherScaffold(topBar = {
        TetherTopAppBar(title = { Text("מרכז ניהול") }, actions = {
            IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "רענן") }
            IconButton(onClick = onLogout) { Icon(Icons.Default.Logout, "התנתק") }
        })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
            item {
                Text(if (adminName.isBlank()) "אזור מנהל" else "שלום, $adminName", style = MaterialTheme.typography.headlineSmall)
                Text(if (isSuperAdmin) "ניהול קהילות ומכשירים" else "הקהילות והמכשירים שבניהולך",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp))
            }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 20.dp)) }
            error?.let { message -> item {
                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 16.dp))
            } }
            item { TetherSectionTitle("סקירה") }
            item {
                TetherSettingsGroup {
                    TetherPreferenceRow("קהילות", Icons.Default.Groups, onClick = onCommunitiesClick,
                        trailing = { Text(stats?.totalCommunities?.toString() ?: "—", style = MaterialTheme.typography.titleMedium) })
                    TetherGroupDivider()
                    TetherPreferenceRow("מכשירים", Icons.Default.Smartphone, onClick = onCommunitiesClick,
                        trailing = { Text(stats?.totalDevices?.toString() ?: "—", style = MaterialTheme.typography.titleMedium) })
                    TetherGroupDivider()
                    TetherPreferenceRow("בקשות לאישור", Icons.Default.NotificationsNone, onClick = onApprovalsClick,
                        trailing = { Text(stats?.pendingApprovals?.toString() ?: "—", color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleMedium) })
                }
            }
            item { TetherSectionTitle("ניהול הקהילה") }
            item {
                TetherSettingsGroup {
                    TetherPreferenceRow("קהילות ומכשירים", Icons.Default.Groups, onClick = onCommunitiesClick)
                    TetherGroupDivider()
                    TetherPreferenceRow("חיבור מכשיר חדש", Icons.Default.QrCodeScanner, onClick = onProvisioningClick)
                    TetherGroupDivider()
                    TetherPreferenceRow("אישורים ובקשות", Icons.Default.CheckCircleOutline, onClick = onApprovalsClick)
                }
            }
            item { TetherSectionTitle("כלי מנהל") }
            item {
                TetherSettingsGroup {
                    TetherPreferenceRow("כלים עצמיים", Icons.Default.Timer, onClick = onSelfToolsClick)
                    TetherGroupDivider()
                    TetherPreferenceRow("הרשאות הגנה", Icons.Default.Shield, onClick = onPermissionsClick)
                    TetherGroupDivider()
                    TetherPreferenceRow("יומן אבחון", Icons.Default.BugReport, onClick = onDiagnosticsClick)
                }
            }
            if (isSuperAdmin) {
                item { TetherSectionTitle("מנהל־על") }
                item {
                    TetherSettingsGroup {
                        TetherPreferenceRow("מנהלי קהילות ושיוך קהילות", Icons.Default.ManageAccounts, onClick = onManageAdminsClick)
                        TetherGroupDivider()
                        TetherPreferenceRow("סקירה כללית", Icons.Default.Domain, onClick = onGlobalOverviewClick)
                        TetherGroupDivider()
                        TetherPreferenceRow("קטלוג אפליקציות", Icons.Default.Apps, onClick = onApprovedAppsClick)
                    }
                }
            }
            item { TetherSectionTitle("פעילות אחרונה") }
            item {
                TetherSettingsGroup {
                    if (activity.isEmpty()) TetherPreferenceRow(if (loading) "טוען פעילות..." else "אין פעילות להצגה", Icons.Default.History)
                    activity.take(5).forEachIndexed { index, event ->
                        TetherPreferenceRow(event.description, Icons.Default.History, subtitle = event.communityName)
                        if (index < activity.take(5).lastIndex) TetherGroupDivider()
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
