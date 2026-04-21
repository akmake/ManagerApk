package com.sterni.dailystudy.ui.screens.home

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sterni.dailystudy.ui.screens.join.isAccessibilityEnabled

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    communityName: String,
    isDeviceOwner: Boolean,
    onCalendarClick: () -> Unit,
    onNewsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onEnterAdmin: () -> Unit
) {
    val context = LocalContext.current
    var adminHoldCount by remember { mutableIntStateOf(0) }
    var showAdminDialog by remember { mutableStateOf(false) }
    val accessibilityEnabled = remember { isAccessibilityEnabled(context) }

    if (showAdminDialog) {
        AlertDialog(
            onDismissRequest = { showAdminDialog = false; adminHoldCount = 0 },
            title = { Text("כניסת מנהל") },
            text = { Text("האם ברצונך להיכנס לממשק הניהול?") },
            confirmButton = {
                TextButton(onClick = { showAdminDialog = false; onEnterAdmin() }) {
                    Text("כניסה")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdminDialog = false; adminHoldCount = 0 }) {
                    Text("ביטול")
                }
            }
        )
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = true,
                    onClick = {},
                    icon = { Icon(Icons.Default.Home, null) },
                    label = { Text("בית") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onCalendarClick,
                    icon = { Icon(Icons.Default.CalendarMonth, null) },
                    label = { Text("לוח שנה") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNewsClick,
                    icon = { Icon(Icons.Default.Newspaper, null) },
                    label = { Text("חדשות") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onSettingsClick,
                    icon = { Icon(Icons.Default.Settings, null) },
                    label = { Text("הגדרות") }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(40.dp))

            // Long-press 5x on logo → admin entry
            Text(
                text = "Tether",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = {
                        adminHoldCount++
                        if (adminHoldCount >= 5) showAdminDialog = true
                    }
                )
            )

            Spacer(Modifier.height(24.dp))

            // Status card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDeviceOwner)
                        MaterialTheme.colorScheme.secondaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isDeviceOwner) Icons.Default.Shield else Icons.Default.ShieldMoon,
                        contentDescription = null,
                        tint = if (isDeviceOwner)
                            MaterialTheme.colorScheme.secondary
                        else
                            MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = communityName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (isDeviceOwner) "הגנה מלאה פעילה"
                            else "הגנה חלקית — מומלץ להפעיל Device Owner",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Warning banner if accessibility is disabled
            if (!accessibilityEnabled) {
                Spacer(Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "הגנת מחיקה כבויה — לחץ להפעלה",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(Modifier.height(36.dp))

            Text(
                text = "שירותים",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ServiceCard(Modifier.weight(1f), Icons.Default.CalendarMonth, "לוח שנה", onCalendarClick)
                ServiceCard(Modifier.weight(1f), Icons.Default.Newspaper, "חדשות", onNewsClick)
            }
        }
    }
}

@Composable
private fun ServiceCard(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Card(modifier = modifier, onClick = onClick, shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, label, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}
