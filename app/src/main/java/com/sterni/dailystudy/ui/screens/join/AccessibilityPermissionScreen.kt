package com.sterni.dailystudy.ui.screens.join

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.CheckCircle
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
import com.sterni.dailystudy.admin.TetherAccessibilityService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

fun isAccessibilityEnabled(context: Context): Boolean {
    val service = "${context.packageName}/${TetherAccessibilityService::class.java.canonicalName}"
    return try {
        val enabled = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED
        )
        if (enabled != 1) return false
        val services = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(services)
        colonSplitter.any { it.equals(service, ignoreCase = true) }
    } catch (_: Exception) {
        false
    }
}

@Composable
fun AccessibilityPermissionScreen(onGranted: () -> Unit) {
    val context = LocalContext.current
    val granted = remember { MutableStateFlow(isAccessibilityEnabled(context)) }
    val isGranted by granted.collectAsStateWithLifecycle()

    // Poll every second while waiting
    LaunchedEffect(Unit) {
        while (true) {
            granted.value = isAccessibilityEnabled(context)
            if (granted.value) {
                delay(600)
                onGranted()
                break
            }
            delay(1000)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
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
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
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
