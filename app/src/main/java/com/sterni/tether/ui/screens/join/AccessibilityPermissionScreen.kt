package com.sterni.tether.ui.screens.join

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sterni.tether.admin.TetherAccessibilityService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

fun isAccessibilityEnabled(context: Context): Boolean {
    val service = "${context.packageName}/${TetherAccessibilityService::class.java.canonicalName}"
    return try {
        val enabled = Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
        if (enabled != 1) return false
        val services = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(services)
        splitter.any { it.equals(service, ignoreCase = true) }
    } catch (_: Exception) { false }
}

// Android 13+ blocks accessibility for sideloaded apps until the user explicitly
// allows "Restricted settings" via App Info ג†’ three-dot menu.
fun isRestrictedSettingsAllowed(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return try {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            "android:access_restricted_settings",
            context.applicationInfo.uid,
            context.packageName
        )
        mode == AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) { true }
}

@Composable
fun AccessibilityPermissionScreen(onGranted: () -> Unit) {
    val context = LocalContext.current
    val needsRestrictedCheck = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    var restrictedAllowed by remember { mutableStateOf(isRestrictedSettingsAllowed(context)) }
    val granted = remember { MutableStateFlow(isAccessibilityEnabled(context)) }
    val isGranted by granted.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        while (true) {
            restrictedAllowed = isRestrictedSettingsAllowed(context)
            if (restrictedAllowed) {
                granted.value = isAccessibilityEnabled(context)
                if (granted.value) {
                    delay(600)
                    onGranted()
                    break
                }
            }
            delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (needsRestrictedCheck && !restrictedAllowed) {
            // ── שלב 1: אפשר הגדרות מוגבלות ──
            Icon(
                Icons.Default.Settings, null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.height(24.dp))
            Text("שלב ראשון", fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text("אפשר הגדרות מוגבלות", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(
                "ב-Android 13 ומעלה, אפליקציות שלא הותקנו מ-Play Store דורשות אישור מיוחד לפני הפעלת שירות נגישות.",
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StepItem("1", "לחץ על הכפתור למטה — ייפתח מסך פרטי האפליקציה")
                    StepItem("2", "לחץ על שלוש הנקודות ⋮ בפינה הימנית העליונה")
                    StepItem("3", "בחר \"אפשר הגדרות מוגבלות\"")
                    StepItem("4", "חזור לכאן — האפליקציה תמשיך אוטומטית")
                }
            }
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Default.Settings, null)
                Spacer(Modifier.width(8.dp))
                Text("פתח פרטי אפליקציה", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }

        } else {
            // ── שלב 2: הפעל שירות נגישות ──
            Icon(
                imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Accessibility,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = if (isGranted) "הרשאה אושרה!" else "הרשאת נגישות",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "הרשאה זו מאפשרת ל-Tether להגן על עצמה מפני הסרה לא מורשית.",
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(32.dp))
            if (!isGranted) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StepItem("1", "לחץ על הכפתור למטה")
                        StepItem("2", "מצא \"Tether\" ברשימה")
                        StepItem("3", "הפעל את המתג")
                        StepItem("4", "חזור לכאן — האפליקציה תמשיך אוטומטית")
                    }
                }
                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Icon(Icons.Default.Accessibility, null)
                    Spacer(Modifier.width(8.dp))
                    Text("פתח הגדרות נגישות", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun StepItem(number: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primary) {
            Text(
                number,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
        Text(text, fontSize = 14.sp)
    }
}
