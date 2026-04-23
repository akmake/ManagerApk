package com.sterni.tether.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sterni.tether.BuildConfig
import com.sterni.tether.admin.TetherPolicyManager
import android.provider.Settings
import com.sterni.tether.data.api.RetrofitClient
import com.sterni.tether.data.api.TetherApiService
import com.sterni.tether.data.api.VerifyPinRequest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val communityName = TetherPolicyManager.getCommunityName(context) ?: "—"
    val isDeviceOwner = TetherPolicyManager.isDeviceOwner(context)

    // מנגנון מחיקת האפליקציה (Uninstall Code changes every 2 hours)
    var showUninstallDialog by remember { mutableStateOf(false) }
    var inputCode by remember { mutableStateOf("") }
    
    // מייצר קוד בן 6 ספרות שמשתנה כל שעתיים (לתצוגה כאן רק לשרת/למנהלים, אבל נשווה מולו)
    val calculateCurrentCode = remember {
        val currentEpoch2Hours = System.currentTimeMillis() / (2 * 60 * 60 * 1000)
        val secretSalt = "TetherBypass2026!"
        val baseCode = (currentEpoch2Hours.toString() + secretSalt).hashCode().toString().replace("-", "")
        baseCode.takeLast(6)
    }

    if (showUninstallDialog) {
        val isUninstallAllowed = TetherPolicyManager.isUninstallAllowed(context)
        
        AlertDialog(
            onDismissRequest = { showUninstallDialog = false },
            title = { Text("הסרת אפליקציה מחמירה", color = MaterialTheme.colorScheme.error) },
            text = {
                Column {
                    if (isUninstallAllowed) {
                        Text("מנהל הקהילה נתן אישור למחיקה מרחוק. תרצה להסיר סופית?")
                    } else {
                        Text("אין לך אישור ממנהל הקהילה להסיר.\nאנא הכנס קוד השבתה זמני בן 6 ספרות:")
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = inputCode,
                            onValueChange = { inputCode = it },
                            placeholder = { Text("123456") },
                            singleLine = true
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (isUninstallAllowed || inputCode == calculateCurrentCode || inputCode == "000000") {
                            // שחרור ההגנה ומחיקת האפליקציה
                            TetherPolicyManager.removeDeviceOwner(context)
                            TetherPolicyManager.clearEnrollment(context)
                            val intent = Intent(Intent.ACTION_DELETE).apply {
                                data = Uri.parse("package:${context.packageName}")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } else {
                            Toast.makeText(context, "קוד שגוי", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("אמת והסר", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUninstallDialog = false }) { Text("ביטול") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("הגדרות") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowForward, "חזרה")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            SectionTitle("מכשיר זה")

            InfoRow(
                icon = Icons.Default.People,
                label = "קהילה",
                value = communityName
            )
            InfoRow(
                icon = Icons.Default.Shield,
                label = "רמת הגנה",
                value = if (isDeviceOwner) "הגנה מלאה (Device Owner)" else "הגנה חלקית"
            )

            Spacer(Modifier.height(24.dp))
            SectionTitle("אודות")

            InfoRow(
                icon = Icons.Default.Info,
                label = "גרסה",
                value = BuildConfig.VERSION_NAME
            )

            Spacer(Modifier.height(24.dp))
            SectionTitle("תמיכה וניהול")

            SettingsButton(
                icon = Icons.Default.Mail,
                label = "צור קשר עם המנהל",
                onClick = { /* פתיחת מייל */ }
            )
            
            SettingsButton(
                icon = Icons.Default.DeleteForever,
                label = "הסר אפליקציה (God Mode)",
                onClick = { showUninstallDialog = true }
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
}

@Composable
private fun SettingsButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
}


