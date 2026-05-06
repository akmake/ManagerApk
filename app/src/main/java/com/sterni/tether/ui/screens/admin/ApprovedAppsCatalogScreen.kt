package com.sterni.tether.ui.screens.admin

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sterni.tether.admin.AdminSession
import com.sterni.tether.data.api.AdminApiService
import com.sterni.tether.data.api.RetrofitClient
import com.sterni.tether.data.model.AdminApprovedApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ApprovedAppsCatalogViewModel(app: Application) : AndroidViewModel(app) {
    private val api = RetrofitClient.create(AdminApiService::class.java)

    private val _apps = MutableStateFlow<List<AdminApprovedApp>>(emptyList())
    val apps: StateFlow<List<AdminApprovedApp>> = _apps

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun clearMessage() {
        _message.value = null
    }

    fun load(token: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val res = api.getApprovedAppsCatalog(token)
                if (res.isSuccessful) _apps.value = res.body() ?: emptyList()
                else _message.value = "Failed to load approved apps"
            } catch (_: Exception) {
                _message.value = "Network error while loading approved apps"
            }
            _loading.value = false
        }
    }

    fun upsert(token: String, payload: Map<String, Any>) {
        viewModelScope.launch {
            try {
                val res = api.upsertApprovedApp(token, payload)
                if (res.isSuccessful) {
                    _message.value = "App saved"
                    load(token)
                } else {
                    _message.value = "Failed to save app"
                }
            } catch (_: Exception) {
                _message.value = "Network error while saving app"
            }
        }
    }

    fun update(token: String, id: String, payload: Map<String, Any>) {
        viewModelScope.launch {
            try {
                val res = api.updateApprovedApp(token, id, payload)
                if (res.isSuccessful) {
                    _message.value = "App updated"
                    load(token)
                } else {
                    _message.value = "Failed to update app"
                }
            } catch (_: Exception) {
                _message.value = "Network error while updating app"
            }
        }
    }

    fun deactivate(token: String, id: String) {
        viewModelScope.launch {
            try {
                val res = api.deactivateApprovedApp(token, id)
                if (res.isSuccessful) {
                    _message.value = "App deactivated"
                    load(token)
                } else {
                    _message.value = "Failed to deactivate app"
                }
            } catch (_: Exception) {
                _message.value = "Network error while deactivating app"
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApprovedAppsCatalogScreen(
    onBack: () -> Unit,
    vm: ApprovedAppsCatalogViewModel = viewModel()
) {
    val context = LocalContext.current
    val token = AdminSession.getToken(context) ?: ""
    val apps by vm.apps.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var showCreateDialog by remember { mutableStateOf(false) }
    var editingApp by remember { mutableStateOf<AdminApprovedApp?>(null) }

    LaunchedEffect(Unit) {
        vm.load(token)
    }

    LaunchedEffect(message) {
        val msg = message ?: return@LaunchedEffect
        snackbar.showSnackbar(msg)
        vm.clearMessage()
    }

    if (showCreateDialog) {
        ApprovedAppEditDialog(
            title = "Add Approved App",
            initial = null,
            onDismiss = { showCreateDialog = false },
            onSave = { payload ->
                vm.upsert(token, payload)
                showCreateDialog = false
            }
        )
    }

    editingApp?.let { app ->
        ApprovedAppEditDialog(
            title = "Edit Approved App",
            initial = app,
            onDismiss = { editingApp = null },
            onSave = { payload ->
                vm.update(token, app.id, payload)
                editingApp = null
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Approved Apps Catalog") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                    }
                }
            )
        }
    ) { padding ->
        if (loading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (apps.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No approved apps configured")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }
            items(apps) { app ->
                ApprovedAppRow(
                    app = app,
                    onEdit = { editingApp = app },
                    onToggleActive = { active ->
                        vm.update(token, app.id, mapOf("active" to active))
                    },
                    onDeactivate = { vm.deactivate(token, app.id) }
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ApprovedAppRow(
    app: AdminApprovedApp,
    onEdit: () -> Unit,
    onToggleActive: (Boolean) -> Unit,
    onDeactivate: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    app.appName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
                IconButton(onClick = onDeactivate) {
                    Icon(Icons.Default.Delete, contentDescription = "Deactivate", tint = MaterialTheme.colorScheme.error)
                }
            }
            Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Version: ${app.versionCode}", style = MaterialTheme.typography.bodySmall)
            Text(app.downloadUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Active", modifier = Modifier.weight(1f))
                Switch(
                    checked = app.active,
                    onCheckedChange = onToggleActive
                )
            }
        }
    }
}

@Composable
private fun ApprovedAppEditDialog(
    title: String,
    initial: AdminApprovedApp?,
    onDismiss: () -> Unit,
    onSave: (Map<String, Any>) -> Unit
) {
    var packageName by remember(initial?.id) { mutableStateOf(initial?.packageName ?: "") }
    var appName by remember(initial?.id) { mutableStateOf(initial?.appName ?: "") }
    var versionCode by remember(initial?.id) { mutableStateOf(initial?.versionCode?.toString() ?: "1") }
    var downloadUrl by remember(initial?.id) { mutableStateOf(initial?.downloadUrl ?: "") }
    var iconUrl by remember(initial?.id) { mutableStateOf(initial?.iconUrl ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    label = { Text("Package Name") },
                    enabled = initial == null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = appName,
                    onValueChange = { appName = it },
                    label = { Text("App Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = versionCode,
                    onValueChange = { versionCode = it.filter { c -> c.isDigit() } },
                    label = { Text("Version Code") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = downloadUrl,
                    onValueChange = { downloadUrl = it },
                    label = { Text("Download URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = iconUrl,
                    onValueChange = { iconUrl = it },
                    label = { Text("Icon URL (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = packageName.isNotBlank() && appName.isNotBlank() && downloadUrl.isNotBlank() && versionCode.toIntOrNull() != null,
                onClick = {
                    val payload = mutableMapOf<String, Any>(
                        "packageName" to packageName.trim(),
                        "appName" to appName.trim(),
                        "versionCode" to (versionCode.toIntOrNull() ?: 1),
                        "downloadUrl" to downloadUrl.trim()
                    )
                    if (iconUrl.isNotBlank()) payload["iconUrl"] = iconUrl.trim()
                    onSave(payload)
                }
            ) { Text("Save") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
