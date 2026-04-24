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
import com.sterni.tether.ui.theme.HebrewFont

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val communityName = TetherPolicyManager.getCommunityName(context) ?: "—"
    val isDeviceOwner = TetherPolicyManager.isDeviceOwner(context)

    var showUninstallDialog by remember { mutableStateOf(false) }
    var enteredCode by remember { mutableStateOf("") }
    var codeError by remember { mutableStateOf(false) }

    if (showUninstallDialog) {
        AlertDialog(
            onDismissRequest = {
                showUninstallDialog = false
                enteredCode = ""
                codeError = false
            },
            title = { Text("אימות זהות", fontFamily = HebrewFont) },
            text = {
                Column {
                    Text("הזן קוד אישי להסרת ההגנות ומחיקת האפליקציה:", fontFamily = HebrewFont)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = enteredCode,
                        onValueChange = { enteredCode = it; codeError = false },
                        label = { Text("קוד", fontFamily = HebrewFont) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        isError = codeError,
                        supportingText = if (codeError) {
                            { Text("קוד שגוי", fontFamily = HebrewFont, color = MaterialTheme.colorScheme.error) }
                        } else null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (enteredCode == "11213") {
                            showUninstallDialog = false
                            TetherPolicyManager.releaseAllAndUninstall(context)
                        } else {
                            codeError = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("הסר הגנות ומחק", fontFamily = HebrewFont)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showUninstallDialog = false
                    enteredCode = ""
                    codeError = false
                }) {
                    Text("ביטול", fontFamily = HebrewFont)
                }
            }
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
                onClick = { /* פתיחת מייל תמיכה */ }
            )

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = { showUninstallDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Warning, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("הורד הגנות ומחק אפליקציה", fontFamily = HebrewFont)
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
