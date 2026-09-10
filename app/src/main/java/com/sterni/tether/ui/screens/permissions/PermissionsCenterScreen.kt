package com.sterni.tether.ui.screens.permissions

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.sterni.tether.admin.TetherDeviceAdminReceiver
import com.sterni.tether.ui.screens.join.isAccessibilityEnabled

private data class PermItem(
    val title: String,
    val description: String,
    val granted: Boolean,
    val onGrant: () -> Unit
)

/**
 * Permissions center — lets whoever holds the device (including the manager, from the admin area)
 * grant every critical permission the protection depends on, and see at a glance which are missing.
 * Auto-refreshes so status updates right after returning from a system settings screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsCenterScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableStateOf(0) }

    // Re-check statuses periodically so returning from system settings reflects immediately.
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1500)
            tick++
        }
    }

    val items = remember(tick) { buildPermItems(context) }
    val grantedCount = items.count { it.granted }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("הרשאות הגנה") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowForward, "חזרה") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)
        ) {
            item {
                Text(
                    "אשר את כל ההרשאות כדי שההגנה תפעל במלואה. חסרות ${items.size - grantedCount}.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 14.dp)
                )
            }
            item {
                val allGranted = grantedCount == items.size
                Text(
                    "$grantedCount / ${items.size} מאושרות",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (allGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            items(items) { PermRow(it) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun PermRow(item: PermItem) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (item.granted) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = if (item.granted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    item.description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            if (item.granted) {
                Text("מאושר", color = Color(0xFF2E7D32), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            } else {
                Button(onClick = item.onGrant) { Text("אשר") }
            }
        }
    }
}

private fun buildPermItems(context: Context): List<PermItem> {
    val pkg = context.packageName
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val adminComponent = ComponentName(context, TetherDeviceAdminReceiver::class.java)
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    fun open(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    val list = mutableListOf<PermItem>()

    list += PermItem(
        title = "שירות נגישות",
        description = "הליבה של החסימה בזמן אמת (חנויות, אפליקציות, שורטס).",
        granted = isAccessibilityEnabled(context),
        onGrant = { open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
    )

    list += PermItem(
        title = "VPN (סינון רשת)",
        description = "חסימת דומיינים ו-DNS של חנויות.",
        granted = VpnService.prepare(context) == null,
        onGrant = { VpnService.prepare(context)?.let { open(it) } }
    )

    list += PermItem(
        title = "מנהל התקן",
        description = "אכיפת מדיניות, מניעת הסרה ואיפוס.",
        granted = dpm.isAdminActive(adminComponent) || dpm.isDeviceOwnerApp(pkg),
        onGrant = {
            open(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent))
        }
    )

    list += PermItem(
        title = "חלון על גבי אפליקציות",
        description = "הצגת התראות חסימה מעל אפליקציות אחרות.",
        granted = Settings.canDrawOverlays(context),
        onGrant = { open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$pkg"))) }
    )

    val listeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: ""
    list += PermItem(
        title = "גישה להתראות",
        description = "קריאת התראות של חנויות/מתקינים לחסימה.",
        granted = listeners.contains(pkg),
        onGrant = { open(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) }
    )

    list += PermItem(
        title = "ללא אופטימיזציית סוללה",
        description = "כדי שהשירותים לא ייהרגו ברקע.",
        granted = pm.isIgnoringBatteryOptimizations(pkg),
        onGrant = { open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val granted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        list += PermItem(
            title = "התראות",
            description = "הצגת התראות סטטוס ההגנה.",
            granted = granted,
            onGrant = {
                open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, pkg))
            }
        )
    }

    return list
}
