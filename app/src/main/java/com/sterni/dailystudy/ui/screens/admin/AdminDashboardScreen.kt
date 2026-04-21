package com.sterni.dailystudy.ui.screens.admin

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sterni.dailystudy.admin.AdminSession
import com.sterni.dailystudy.data.api.AdminApiService
import com.sterni.dailystudy.data.api.RetrofitClient
import com.sterni.dailystudy.data.model.ActivityItem
import com.sterni.dailystudy.data.model.DashboardStats
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

    fun load(token: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val statsRes = api.getDashboard(token)
                if (statsRes.isSuccessful) _stats.value = statsRes.body()

                val actRes = api.getActivity(token)
                if (actRes.isSuccessful) _activity.value = actRes.body() ?: emptyList()
            } catch (_: Exception) {}
            _loading.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    onCommunitiesClick: () -> Unit,
    onApprovalsClick: () -> Unit,
    onLogout: () -> Unit,
    vm: AdminDashboardViewModel = viewModel()
) {
    val context = LocalContext.current
    val token = AdminSession.getToken(context) ?: ""
    val adminName = AdminSession.getName(context)
    val stats by vm.stats.collectAsStateWithLifecycle()
    val activity by vm.activity.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.load(token) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("שלום, $adminName") },
                actions = {
                    IconButton(onClick = {
                        AdminSession.logout(context)
                        onLogout()
                    }) {
                        Icon(Icons.Default.Logout, "יציאה")
                    }
                }
            )
        }
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            // Stats grid
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.People,
                        label = "קהילות",
                        value = "${stats?.totalCommunities ?: 0}",
                        onClick = onCommunitiesClick
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.PhoneAndroid,
                        label = "מכשירים",
                        value = "${stats?.totalDevices ?: 0}"
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Pending,
                        label = "בקשות ממתינות",
                        value = "${stats?.pendingApprovals ?: 0}",
                        badge = (stats?.pendingApprovals ?: 0) > 0,
                        onClick = onApprovalsClick
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.WifiOff,
                        label = "לא פעילים 7+ ימים",
                        value = "${stats?.inactiveDevices ?: 0}",
                        warn = (stats?.inactiveDevices ?: 0) > 0
                    )
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                Text("פעילות אחרונה", fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (activity.isEmpty()) {
                item {
                    Text("אין פעילות עדיין", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp))
                }
            } else {
                items(activity) { item -> ActivityRow(item) }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    badge: Boolean = false,
    warn: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier,
        onClick = onClick ?: {},
        enabled = onClick != null,
        colors = CardDefaults.cardColors(
            containerColor = when {
                badge -> MaterialTheme.colorScheme.errorContainer
                warn  -> MaterialTheme.colorScheme.tertiaryContainer
                else  -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, null, modifier = Modifier.size(24.dp),
                tint = if (badge) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ActivityRow(item: ActivityItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Circle, null, modifier = Modifier.size(8.dp),
            tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.description, fontSize = 14.sp)
            if (!item.communityName.isNullOrBlank()) {
                Text(item.communityName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(item.timestamp, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
}
