package com.sterni.tether.ui.screens.settings

import com.sterni.tether.ui.components.TetherScaffold as Scaffold
import com.sterni.tether.ui.components.TetherCenteredTopAppBar as CenterAlignedTopAppBar
import com.sterni.tether.ui.components.TetherCard as Card
import com.sterni.tether.ui.components.TetherSwitch as Switch
import com.sterni.tether.ui.components.TetherButton as Button
import com.sterni.tether.ui.components.TetherTextField as OutlinedTextField

import com.sterni.tether.ui.components.*
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sterni.tether.BuildConfig
import com.sterni.tether.admin.TetherPolicyManager
import com.sterni.tether.admin.UninstallPinVerifier
import com.sterni.tether.admin.UninstallVerificationResult
import com.sterni.tether.ui.theme.BaHaYetzira
import com.sterni.tether.ui.theme.SblHebrew
import com.sterni.tether.ui.theme.Muted
import com.sterni.tether.ui.theme.Ink
import com.sterni.tether.ui.theme.LineColor
import com.sterni.tether.ui.theme.Primary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onEmergencyClick: () -> Unit,
    onSelfToolsClick: () -> Unit = {},
    onDiagnosticsClick: () -> Unit = {},
    onAdminClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val communityName = TetherPolicyManager.getCommunityName(context) ?: "-"
    val isDeviceOwner = TetherPolicyManager.isDeviceOwner(context)
    val isUninstallWindowOpen = remember { TetherPolicyManager.isUninstallWindowActive(context) }

    var whatsappShield by remember {
        mutableStateOf(TetherPolicyManager.isWhatsAppShieldEnabled(context))
    }

    var showUninstallDialog by remember { mutableStateOf(false) }
    var enteredCode by remember { mutableStateOf("") }
    var codeError by remember { mutableStateOf(false) }
    var isVerifying by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    if (showUninstallDialog) {
        AlertDialog(
            onDismissRequest = {
                showUninstallDialog = false
                enteredCode = ""
                codeError = false
                isVerifying = false
            },
            title = { Text("אימות זהות", fontFamily = BaHaYetzira, fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    Text("הזן קוד אישי לפתיחת חלון הסרה זמני:", fontFamily = SblHebrew)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = enteredCode,
                        onValueChange = {
                            enteredCode = it
                            codeError = false
                        },
                        label = { Text("קוד", fontFamily = SblHebrew) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        isError = codeError,
                        supportingText = if (codeError) {
                            { Text("קוד שגוי או הרשאה לא זמינה", fontFamily = SblHebrew, color = MaterialTheme.colorScheme.error) }
                        } else null,
                        singleLine = true,
                        enabled = !isVerifying,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            isVerifying = true
                            val result = UninstallPinVerifier.verifyAndOpenWindow(context, enteredCode)
                            isVerifying = false
                            when (result) {
                                UninstallVerificationResult.Success -> {
                                    showUninstallDialog = false
                                    enteredCode = ""
                                    codeError = false
                                    statusMessage = "חלון הסרה נפתח לשעה. להסרה בפועל יש להיכנס להגדרות המכשיר."
                                }
                                UninstallVerificationResult.InvalidPin -> {
                                    codeError = true
                                }
                                UninstallVerificationResult.NotEnrolled -> {
                                    codeError = true
                                    statusMessage = "המכשיר אינו מחובר לקהילה."
                                }
                                UninstallVerificationResult.NetworkError -> {
                                    codeError = true
                                    statusMessage = "שגיאת רשת. נדרש חיבור אינטרנט לאימות."
                                }
                            }
                        }
                    },
                    enabled = enteredCode.isNotBlank() && !isVerifying,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    if (isVerifying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onError
                        )
                    } else {
                        Text("פתח חלון הסרה", fontFamily = SblHebrew)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showUninstallDialog = false
                    enteredCode = ""
                    codeError = false
                    isVerifying = false
                }) {
                    Text("ביטול", fontFamily = SblHebrew)
                }
            }
        )
    }

    statusMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { statusMessage = null },
            confirmButton = {
                TextButton(onClick = { statusMessage = null }) {
                    Text("אישור", fontFamily = SblHebrew)
                }
            },
            title = { Text("עדכון", fontFamily = BaHaYetzira, fontWeight = FontWeight.SemiBold) },
            text = { Text(message, fontFamily = SblHebrew) }
        )
    }

    Scaffold(topBar = {
        CenterAlignedTopAppBar(title = { Text("מרכז הגנה") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowForward, "חזרה") }
        })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
            item { TetherSectionTitle("מכשיר זה") }
            item {
                TetherSettingsGroup {
                    TetherPreferenceRow("קהילה", Icons.Default.People, trailing = {
                        Text(communityName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    })
                    TetherGroupDivider()
                    TetherPreferenceRow("רמת הגנה", Icons.Default.Shield, trailing = {
                        Text(if (isDeviceOwner) "מלאה" else "חלקית", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    })
                }
            }
            item { TetherSectionTitle("הגנה אישית") }
            item {
                TetherSettingsGroup {
                    TetherPreferenceRow("חסום סטטוסים וערוצים", Icons.Default.Shield,
                        subtitle = "בוואטסאפ ובוואטסאפ Business", trailing = {
                            Switch(whatsappShield, {
                                whatsappShield = it
                                TetherPolicyManager.setWhatsAppShieldEnabled(context, it)
                            })
                        })
                    TetherGroupDivider()
                    TetherPreferenceRow("כלים עצמיים", Icons.Default.Timer,
                        subtitle = "חסימת וואטסאפ ומגבלת שורטס", onClick = onSelfToolsClick)
                }
            }
            item { TetherSectionTitle("תמיכה וניהול") }
            item {
                TetherSettingsGroup {
                    TetherPreferenceRow("אזור מנהל", Icons.Default.People, onClick = onAdminClick)
                    TetherGroupDivider()
                    TetherPreferenceRow("יומן אבחון", Icons.Default.BugReport, onClick = onDiagnosticsClick)
                    TetherGroupDivider()
                    TetherPreferenceRow("שחרור בחירום", Icons.Default.Shield, onClick = onEmergencyClick)
                }
            }
            item { TetherSectionTitle("אודות") }
            item {
                TetherSettingsGroup {
                    TetherPreferenceRow("גרסה", Icons.Default.Info, trailing = {
                        Text(BuildConfig.VERSION_NAME, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    })
                }
            }
            item { TetherSectionTitle("הסרה") }
            item {
                TetherSettingsGroup {
                    if (isUninstallWindowOpen) {
                        TetherPreferenceRow("המנהל אישר הסרה", Icons.Default.CheckCircle)
                        TetherGroupDivider()
                        TetherPreferenceRow("הסר את האפליקציה", Icons.Default.Warning, destructive = true,
                            onClick = {
                                context.startActivity(Intent(Intent.ACTION_DELETE).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                })
                            })
                    } else {
                        TetherPreferenceRow("פתיחת חלון הסרה", Icons.Default.Warning, destructive = true,
                            onClick = { showUninstallDialog = true })
                    }
                }
            }
            item { Spacer(Modifier.height(28.dp)) }
        }
    }
}
