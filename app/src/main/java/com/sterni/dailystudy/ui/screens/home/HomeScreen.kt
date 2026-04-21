package com.sterni.dailystudy.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sterni.dailystudy.ui.theme.TetherGreen

@Composable
fun HomeScreen(
    communityName: String,
    isDeviceOwner: Boolean,
    onCalendarClick: () -> Unit,
    onNewsClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
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

            Text(
                text = "Tether",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
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
                            text = if (isDeviceOwner) "הגנה מלאה פעילה" else "הגנה חלקית — מומלץ להפעיל Device Owner",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                ServiceCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.CalendarMonth,
                    label = "לוח שנה",
                    onClick = onCalendarClick
                )
                ServiceCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Newspaper,
                    label = "חדשות",
                    onClick = onNewsClick
                )
            }
        }
    }
}

@Composable
private fun ServiceCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(10.dp))
            Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}
