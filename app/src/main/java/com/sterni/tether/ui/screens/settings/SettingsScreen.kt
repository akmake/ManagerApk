package com.sterni.tether.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Shield
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
    onEmergencyClick: () -> Unit
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
            title = { Text("אימות זהות", fontFamily = BaHaYetzira, fontWeight = FontWeight.Bold) },
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
            title = { Text("עדכון", fontFamily = BaHaYetzira, fontWeight = FontWeight.Bold) },
            text = { Text(message, fontFamily = SblHebrew) }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("מרכז הגנה", fontFamily = BaHaYetzira, fontWeight = FontWeight.Bold, fontSize = 22.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowForward, "חזרה", tint = Ink)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item { Spacer(Modifier.height(16.dp)) }

            item { SectionTitle("מכשיר זה") }

            item {
                InfoRow(
                    icon = Icons.Default.People,
                    label = "קהילה",
                    value = communityName
                )
            }
            item {
                InfoRow(
                    icon = Icons.Default.Shield,
                    label = "רמת הגנה",
                    value = if (isDeviceOwner) "הגנה מלאה (Tether)" else "הגנה חלקית"
                )
            }

            item { Spacer(Modifier.height(28.dp)) }
            item { SectionTitle("הגנה אישית") }

            item {
                ToggleRow(
                    icon = Icons.Default.Shield,
                    label = "מגן סטטוס וערוצים בוואטסאפ",
                    description = "חוסם כניסה לסטטוסים ולערוצים בוואטסאפ",
                    checked = whatsappShield,
                    onCheckedChange = {
                        whatsappShield = it
                        TetherPolicyManager.setWhatsAppShieldEnabled(context, it)
                    }
                )
            }

            item { Spacer(Modifier.height(28.dp)) }
            item { SectionTitle("תמיכה וניהול") }

            item {
                SettingsButton(
                    icon = Icons.Default.Mail,
                    label = "צור קשר עם המנהל",
                    onClick = { /* TODO */ }
                )
            }

            item {
                SettingsButton(
                    icon = Icons.Default.Shield,
                    label = "שחרור בחירום",
                    onClick = onEmergencyClick
                )
            }

            item { Spacer(Modifier.height(28.dp)) }
            item { SectionTitle("אודות") }

            item {
                InfoRow(
                    icon = Icons.Default.Info,
                    label = "גרסה",
                    value = BuildConfig.VERSION_NAME
                )
            }

            item { Spacer(Modifier.height(40.dp)) }

            item {
                if (isUninstallWindowOpen) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "המנהל אישר הסרת האפליקציה",
                                    fontFamily = SblHebrew,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontSize = 16.sp
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    context.startActivity(
                                        Intent(Intent.ACTION_DELETE).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("הסר עכשיו", fontFamily = SblHebrew, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    Button(
                        onClick = { showUninstallDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        elevation = null,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("פתיחת חלון הסרה", fontFamily = SblHebrew, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = BaHaYetzira,
        color = Primary,
        modifier = Modifier.padding(vertical = 12.dp)
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
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, fontSize = 16.sp, fontFamily = SblHebrew, modifier = Modifier.weight(1f), color = Ink)
        Text(value, fontSize = 14.sp, fontFamily = SblHebrew, color = Muted)
    }
    HorizontalDivider(color = LineColor.copy(alpha = 0.6f), thickness = 0.5.dp)
}

@Composable
private fun ToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 16.sp, fontFamily = SblHebrew, color = Ink)
            Text(
                description,
                fontSize = 12.sp,
                fontFamily = SblHebrew,
                color = Muted
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Primary,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = LineColor
            )
        )
    }
    HorizontalDivider(color = LineColor.copy(alpha = 0.6f), thickness = 0.5.dp)
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
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = Muted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(16.dp))
            Text(label, fontSize = 16.sp, fontFamily = SblHebrew, modifier = Modifier.weight(1f), color = Ink)
            Icon(Icons.Default.ChevronLeft, null, tint = LineColor, modifier = Modifier.size(16.dp))
        }
    }
    HorizontalDivider(color = LineColor.copy(alpha = 0.6f), thickness = 0.5.dp)
}

