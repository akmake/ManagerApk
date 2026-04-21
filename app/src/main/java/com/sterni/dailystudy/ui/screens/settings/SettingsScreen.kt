package com.sterni.dailystudy.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sterni.dailystudy.BuildConfig
import com.sterni.dailystudy.admin.TetherPolicyManager

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val communityName = TetherPolicyManager.getCommunityName(context) ?: "—"
    val isDeviceOwner = TetherPolicyManager.isDeviceOwner(context)

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
            SectionTitle("תמיכה")

            SettingsButton(
                icon = Icons.Default.Mail,
                label = "צור קשר עם המנהל",
                onClick = { /* פתיחת מייל */ }
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
