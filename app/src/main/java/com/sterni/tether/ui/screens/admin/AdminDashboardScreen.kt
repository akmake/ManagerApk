package com.sterni.tether.ui.screens.admin

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.sterni.tether.admin.AdminSession
import com.sterni.tether.data.api.AdminApiService
import com.sterni.tether.data.api.RetrofitClient
import com.sterni.tether.data.model.ActivityItem
import com.sterni.tether.data.model.DashboardStats
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
    onProvisioningClick: () -> Unit,
    onGlobalOverviewClick: () -> Unit = {},
    onApprovedAppsClick: () -> Unit = {},
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
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "מרכז בקרה",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            AdminSession.logout(context)
                            onLogout()
                        }
                    ) {
                        Icon(
                            Icons.Default.Logout,
                            contentDescription = "יציאה",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
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
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    "שלום, $adminName",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Overview Stats Card
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "סיכום מכשירים",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Icon(
                                Icons.Default.BarChart,
                                null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            StatMiniItem(
                                label = "סה\"כ",
                                value = "${stats?.totalDevices ?: 0}",
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            StatDivider()
                            StatMiniItem(
                                label = "פעילים",
                                value = "${(stats?.totalDevices ?: 0) - (stats?.inactiveDevices ?: 0)}",
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            StatDivider()
                            StatMiniItem(
                                label = "ממתינים",
                                value = "${stats?.pendingApprovals ?: 0}",
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    "פעולות מהירות",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickActionCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.QrCodeScanner,
                        label = "חיבור מכשיר",
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        onClick = onProvisioningClick
                    )
                    QuickActionCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Groups,
                        label = "ניהול קהילות",
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        onClick = onCommunitiesClick
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickActionCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Apps,
                        label = "Approved Apps",
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        onClick = onApprovedAppsClick
                    )
                    Spacer(modifier = Modifier.weight(1f))
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickActionCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Domain,
                        label = "סקירה כללית",
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        onClick = onGlobalOverviewClick
                    )
                    QuickActionCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.VerifiedUser,
                        label = "אישורים",
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        onClick = onApprovalsClick,
                        badge = (stats?.pendingApprovals ?: 0) > 0
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "פעילות אחרונה",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { /* View all activity */ }) {
                        Text("הצג הכל")
                    }
                }
            }

            if (activity.isEmpty()) {
                item {
                    EmptyActivityPlaceholder()
                }
            } else {
                items(activity.take(5)) { item ->
                    ActivityListItem(item)
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun StatMiniItem(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = color
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = color.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun StatDivider() {
    VerticalDivider(
        modifier = Modifier.height(40.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
    )
}

@Composable
private fun QuickActionCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    badge: Boolean = false
) {
    OutlinedCard(
        onClick = onClick,
        modifier = modifier.height(110.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = color.copy(alpha = 0.2f)),
        border = CardDefaults.outlinedCardBorder(enabled = true)
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Column(
                modifier = Modifier.align(Alignment.BottomStart)
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (badge) {
                Surface(
                    modifier = Modifier.size(12.dp).align(Alignment.TopEnd),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.error
                ) {}
            }
        }
    }
}

@Composable
private fun ActivityListItem(item: ActivityItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            ) {
                Icon(
                    when (item.type) {
                        "DEVICE_ENROLLED" -> Icons.Default.PhoneAndroid
                        "COMMUNITY_CREATED" -> Icons.Default.Groups
                        "APPROVAL_PENDING" -> Icons.Default.NotificationImportant
                        else -> Icons.Default.History
                    },
                    null,
                    modifier = Modifier.padding(10.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.description,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (!item.communityName.isNullOrBlank()) {
                    Text(
                        item.communityName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                item.timestamp,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyActivityPlaceholder() {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder(enabled = true)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Inbox,
                null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "אין פעילות להצגה",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

