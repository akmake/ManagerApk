package com.sterni.tether.ui.screens.join

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sterni.tether.admin.TetherOverlayService
import com.sterni.tether.admin.TetherVpnService

@Composable
fun VpnPermissionScreen(onGranted: () -> Unit) {
    val context = LocalContext.current

    val vpnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startServices(context)
            onGranted()
        }
    }

    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // After overlay permission, request VPN
        requestVpnPermission(context, vpnLauncher)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("נ", fontSize = 64.sp)

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "הגנת אינטרנט",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Tether צריך הרשאה לסנן תכנים באינטרנט לפי הגדרות הקהילה שלך.",
            textAlign = TextAlign.Center,
            color = Color.Gray,
            fontSize = 15.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "בלחיצה על 'הפעל' יופיעו שני דיאלוגים קצרים — אשר את שניהם.",
            textAlign = TextAlign.Center,
            color = Color.Gray,
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = {
                // First request SYSTEM_ALERT_WINDOW, then VPN
                if (!Settings.canDrawOverlays(context)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:${context.packageName}")
                    )
                    overlayLauncher.launch(intent)
                } else {
                    requestVpnPermission(context, vpnLauncher)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1A237E)
            )
        ) {
            Text("הפעל הגנה", fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(onClick = onGranted) {
            Text("דלג (ללא סינון אינטרנט)", color = Color.Gray, fontSize = 13.sp)
        }
    }
}

private fun requestVpnPermission(
    context: android.content.Context,
    launcher: androidx.activity.result.ActivityResultLauncher<Intent>
) {
    val intent = VpnService.prepare(context)
    if (intent != null) {
        launcher.launch(intent)
    } else {
        // Permission already granted
        startServices(context)
    }
}

private fun startServices(context: android.content.Context) {
    context.startForegroundService(Intent(context, TetherVpnService::class.java))
    context.startForegroundService(Intent(context, TetherOverlayService::class.java))
}
