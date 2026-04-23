package com.sterni.tether.ui.screens.admin

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sterni.tether.admin.AdminSession
import com.sterni.tether.data.api.AdminApiService
import com.sterni.tether.data.api.RetrofitClient
import com.sterni.tether.data.model.LogEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LogsViewModel(app: Application) : AndroidViewModel(app) {
    private val api = RetrofitClient.create(AdminApiService::class.java)
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    fun load(token: String, communityId: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val res = api.getLogs(token, communityId)
                if (res.isSuccessful) _logs.value = res.body() ?: emptyList()
            } catch (_: Exception) {}
            _loading.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    communityId: String,
    onBack: () -> Unit,
    vm: LogsViewModel = viewModel()
) {
    val context = LocalContext.current
    val token = AdminSession.getToken(context) ?: ""
    val logs by vm.logs.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.load(token, communityId) }

    val filtered = remember(logs, filter) {
        if (filter.isBlank()) logs
        else logs.filter { it.deviceModel?.contains(filter, true) == true || it.action.contains(filter, true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("לוג פעולות") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowForward, "חזרה") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                label = { Text("חיפוש") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Scaffold
            }

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("אין לוגים", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@Scaffold
            }

            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                items(filtered) { log -> LogRow(log) }
            }
        }
    }
}

@Composable
private fun LogRow(log: LogEntry) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = if (log.result == "blocked") Icons.Default.Block else Icons.Default.CheckCircle,
            contentDescription = null,
            tint = if (log.result == "blocked") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(18.dp).padding(top = 2.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(log.action, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            if (!log.packageName.isNullOrBlank()) {
                Text(log.packageName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${log.deviceModel ?: log.deviceId.take(8)}  •  ${log.timestamp}",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
}
