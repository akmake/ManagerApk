package com.sterni.tether.ui.screens.diagnostics

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sterni.tether.util.DevLog

/**
 * In-app diagnostic console (F12-like): shows this app's own captured logcat, live. Lets the
 * user/manager see the internal "detected / not detected / error" stream, filter it, clear it and
 * share it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val entries by DevLog.entries.collectAsStateWithLifecycle()

    var levelFilter by remember { mutableStateOf<Char?>(null) } // null = all
    var query by remember { mutableStateOf("") }

    val filtered = remember(entries, levelFilter, query) {
        entries.asReversed().filter { e ->
            (levelFilter == null || e.level == levelFilter) &&
                (query.isBlank() ||
                    e.message.contains(query, true) ||
                    e.tag.contains(query, true))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("יומן אבחון") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowForward, "חזרה") }
                },
                actions = {
                    IconButton(onClick = { DevLog.clear() }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "נקה")
                    }
                    IconButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, DevLog.exportText())
                        }
                        runCatching { context.startActivity(Intent.createChooser(send, "שיתוף יומן")) }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "שיתוף")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LevelChip("הכל", levelFilter == null) { levelFilter = null }
                LevelChip("מידע", levelFilter == 'I') { levelFilter = 'I' }
                LevelChip("אזהרה", levelFilter == 'W') { levelFilter = 'W' }
                LevelChip("שגיאה", levelFilter == 'E') { levelFilter = 'E' }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("סינון טקסט / תג") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            )

            Text(
                "${filtered.size} רשומות • מתעדכן בזמן אמת",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("אין רשומות עדיין", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                    items(filtered) { e -> LogLine(e) }
                }
            }
        }
    }
}

@Composable
private fun LevelChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label, fontSize = 12.sp) })
}

@Composable
private fun LogLine(e: DevLog.Entry) {
    val color = when (e.level) {
        'E' -> Color(0xFFD32F2F)
        'W' -> Color(0xFFF9A825)
        'I' -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            "${e.time}  ${e.level}/${e.tag}",
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            e.message,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = color
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
    }
}
