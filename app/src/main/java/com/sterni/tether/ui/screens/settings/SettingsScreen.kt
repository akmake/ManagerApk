package com.sterni.tether.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.sterni.tether.ui.theme.HebrewFont
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onEmergencyClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val communityName = TetherPolicyManager.getCommunityName(context) ?: "-"
    val isDeviceOwner = TetherPolicyManager.isDeviceOwner(context)

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
            title = { Text("אימות זהות", fontFamily = HebrewFont) },
            text = {
                Column {
                    Text("הזן קוד אישי לפתיחת חלון הסרה זמני:", fontFamily = HebrewFont)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = enteredCode,
                        onValueChange = {
                            enteredCode = it
                            codeError = false
                        },
                        label = { Text("קוד", fontFamily = HebrewFont) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        isError = codeError,
                        supportingText = if (codeError) {
                            { Text("קוד שגוי או הרשאה לא זמינה", fontFamily = HebrewFont, color = MaterialTheme.colorScheme.error) }
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
                        Text("פתח חלון הסרה", fontFamily = HebrewFont)
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
                    Text("ביטול", fontFamily = HebrewFont)
                }
            }
        )
    }

    statusMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { statusMessage = null },
            confirmButton = {
                TextButton(onClick = { statusMessage = null }) {
                    Text("אישור", fontFamily = HebrewFont)
                }
            },
            title = { Text("עדכון", fontFamily = HebrewFont) },
            text = { Text(message, fontFamily = HebrewFont) }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("הגדרות", fontFamily = HebrewFont) },
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
                value = if (isDeviceOwner) "הגנה מלאה (Tether)" else "הגנה חלקית"
            )

            Spacer(Modifier.height(24.dp))
            SectionTitle("אודות")

            InfoRow(
                icon = Icons.Default.Info,
                label = "גרסה",
                value = BuildConfig.VERSION_NAME
            )

            Spacer(Modifier.height(24.dp))
            SectionTitle("תמיכה")

            SettingsButton(
                icon = Icons.Default.Mail,
                label = "צור קשר עם המנהל",
                onClick = { /* TODO */ }
            )

            SettingsButton(
                icon = Icons.Default.Shield,
                label = "שחרור בחירום",
                onClick = onEmergencyClick
            )

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = { showUninstallDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Warning, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("פתיחת חלון הסרה", fontFamily = HebrewFont)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = HebrewFont,
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
        Text(label, fontSize = 15.sp, fontFamily = HebrewFont, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, fontFamily = HebrewFont, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(16.dp))
            Text(label, fontSize = 15.sp, fontFamily = HebrewFont, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
}

