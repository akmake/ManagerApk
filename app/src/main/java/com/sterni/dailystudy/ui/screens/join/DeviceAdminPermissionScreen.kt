package com.sterni.dailystudy.ui.screens.join

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sterni.dailystudy.admin.TetherDeviceAdminReceiver
import com.sterni.dailystudy.admin.TetherPolicyManager

@Composable
fun DeviceAdminPermissionScreen(
    onGranted: () -> Unit
) {
    val context = LocalContext.current
    var denied by remember { mutableStateOf(false) }

    // Skip if already device owner
    LaunchedEffect(Unit) {
        if (TetherPolicyManager.isDeviceOwner(context)) onGranted()
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        val component = ComponentName(context, TetherDeviceAdminReceiver::class.java)
        if (dpm.isAdminActive(component)) {
            onGranted()
        } else {
            denied = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = if (denied)
                MaterialTheme.colorScheme.errorContainer
            else
                MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(100.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (denied) Icons.Default.Warning else Icons.Default.AdminPanelSettings,
                    contentDescription = null,
                    tint = if (denied)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = if (denied) "ההרשאה לא אושרה" else "הפעל הגנה",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = if (denied)
                "ללא הרשאת מנהל מכשיר, Tether לא יכול להגן. ניתן להמשיך עם הגנה חלקית בלבד."
            else
                "כדי להגן על המכשיר, Tether צריך הרשאת מנהל מכשיר. זה מאפשר הגנה ברמת מערכת ההפעלה.",
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 22.sp
        )

        Spacer(Modifier.height(32.dp))

        // What this permission allows
        if (!denied) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("מה ההרשאה מאפשרת:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    PermItem(Icons.Default.Lock, "חסימת התקנות לא מורשות")
                    PermItem(Icons.Default.Shield, "הגנה מפני מחיקת האפליקציה")
                    PermItem(Icons.Default.AdminPanelSettings, "אכיפת מדיניות הקהילה")
                }
            }
            Spacer(Modifier.height(32.dp))
        }

        Button(
            onClick = {
                if (denied) {
                    // Retry
                    denied = false
                    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(
                            DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                            ComponentName(context, TetherDeviceAdminReceiver::class.java)
                        )
                        putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Tether זקוק להרשאה זו כדי להגן על המכשיר")
                    }
                    launcher.launch(intent)
                } else {
                    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(
                            DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                            ComponentName(context, TetherDeviceAdminReceiver::class.java)
                        )
                        putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Tether זקוק להרשאה זו כדי להגן על המכשיר")
                    }
                    launcher.launch(intent)
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text(
                text = if (denied) "נסה שוב" else "הפעל הגנה",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (denied) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onGranted,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("המשך עם הגנה חלקית", fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun PermItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 3.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 13.sp)
    }
}
