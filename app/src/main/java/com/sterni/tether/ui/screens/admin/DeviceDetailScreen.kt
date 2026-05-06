package com.sterni.tether.ui.screens.admin

import android.app.Application
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sterni.tether.admin.AdminSession
import com.sterni.tether.data.api.AdminApiService
import com.sterni.tether.data.api.RetrofitClient
import com.sterni.tether.data.model.AppTimeLock
import com.sterni.tether.data.model.BlockedActionBehavior
import com.sterni.tether.data.model.DeviceDetail
import com.sterni.tether.data.model.DevicePolicy
import com.sterni.tether.data.model.InstalledApp
import com.sterni.tether.data.model.SecurityEvent
import com.sterni.tether.data.model.WebFilterMode
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€
// ViewModel
// ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€

class DeviceDetailViewModel(app: Application) : AndroidViewModel(app) {
    private val api = RetrofitClient.create(AdminApiService::class.java)

    private val _device = MutableStateFlow<DeviceDetail?>(null)
    val device: StateFlow<DeviceDetail?> = _device

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar: StateFlow<String?> = _snackbar

    private val _removed = MutableStateFlow(false)
    val removed: StateFlow<Boolean> = _removed

    private val _events = MutableStateFlow<List<SecurityEvent>>(emptyList())
    val events: StateFlow<List<SecurityEvent>> = _events

    fun load(token: String, deviceId: String, isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isRefresh) _refreshing.value = true else _loading.value = true
            try {
                val deviceRes = api.getDeviceDetail(token, deviceId)
                if (deviceRes.isSuccessful) {
                    val deviceData = deviceRes.body()
                    _device.value = deviceData
                    android.util.Log.d("DeviceDetail", "Loaded device: ${deviceData?.deviceModel}, Apps: ${deviceData?.installedApps?.size}")
                    deviceData?.installedApps?.forEach { 
                         android.util.Log.d("DeviceDetail", "App: ${it.appName} (${it.packageName}) isSystem: ${it.isSystemApp}")
                    }
                } else {
                    android.util.Log.e("DeviceDetail", "Error loading device: ${deviceRes.code()} ${deviceRes.errorBody()?.string()}")
                }
                
                val eventsRes = api.getDeviceEvents(token, deviceId)
                if (eventsRes.isSuccessful) _events.value = eventsRes.body()?.events ?: emptyList()
            } catch (e: Exception) {
                android.util.Log.e("DeviceDetail", "Exception loading device", e)
            }
            _loading.value = false
            _refreshing.value = false
        }
    }

    fun saveDevicePolicy(token: String, deviceId: String, policy: DevicePolicy) {
        viewModelScope.launch {
            _saving.value = true
            try {
                val res = api.updateDevicePolicy(token, deviceId, policy)
                if (res.isSuccessful) {
                    _device.value = _device.value?.copy(devicePolicy = policy)
                    _snackbar.value = "הגדרות המכשיר נשמרו ✓"
                } else {
                    _snackbar.value = "שגיאה בשמירה"
                }
            } catch (_: Exception) { _snackbar.value = "שגיאת רשת" }
            _saving.value = false
        }
    }

    fun sendCommand(token: String, deviceId: String, type: String, payload: String = "") {
        viewModelScope.launch {
            try {
                api.sendCommand(token, deviceId, mapOf("type" to type, "payload" to payload))
                _snackbar.value = when (type) {
                    "FORCE_SYNC"   -> "סנכרון נשלח"
                    "SHOW_MESSAGE" -> "הודעה נשלחה"
                    "RELEASE_ALL"  -> "שחרור מלא נשלח"
                    else -> "הפקודה נשלחה"
                }
            } catch (_: Exception) { _snackbar.value = "שגיאה בשליחת פקודה" }
        }
    }

    fun removeDevice(token: String, deviceId: String) {
        viewModelScope.launch {
            try {
                api.removeDevice(token, deviceId)
                _removed.value = true
            } catch (_: Exception) { _snackbar.value = "שגיאה בהסרה" }
        }
    }

    fun toggleAllowUninstall(token: String, deviceId: String, allow: Boolean) {
        viewModelScope.launch {
            try {
                api.setAllowUninstall(token, deviceId, mapOf("allowUninstall" to allow))
                _device.value = _device.value?.copy(allowUninstall = allow)
            } catch (_: Exception) {}
        }
    }

    fun renameDevice(token: String, deviceId: String, nickname: String?) {
        viewModelScope.launch {
            try {
                api.renameDevice(token, deviceId, mapOf("nickname" to nickname))
                _device.value = _device.value?.copy(deviceNickname = nickname)
                _snackbar.value = "שם עודכן"
            } catch (_: Exception) {}
        }
    }

    fun clearSnackbar() { _snackbar.value = null }
}

// ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€
// Screen
// ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    deviceId: String,
    onBack: () -> Unit,
    vm: DeviceDetailViewModel = viewModel()
) {
    val context = LocalContext.current
    val token = AdminSession.getToken(context) ?: ""
    val device by vm.device.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val snackbarMsg by vm.snackbar.collectAsStateWithLifecycle()
    val removed by vm.removed.collectAsStateWithLifecycle()
    val events by vm.events.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showMessageDialog by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var confirmRelease by remember { mutableStateOf(false) }
    var confirmAllowUninstall by remember { mutableStateOf(false) }
    var editingNickname by remember { mutableStateOf(false) }
    var nicknameInput by remember(device?.deviceNickname) { mutableStateOf(device?.deviceNickname ?: "") }

    LaunchedEffect(Unit) { vm.load(token, deviceId) }
    LaunchedEffect(removed) { if (removed) onBack() }
    LaunchedEffect(snackbarMsg) {
        snackbarMsg?.let { snackbarHostState.showSnackbar(it); vm.clearSnackbar() }
    }

    // Dialogs
    if (showMessageDialog) {
        var msg by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showMessageDialog = false },
            icon = { Icon(Icons.Default.Message, null) },
            title = { Text("שלח הודעה") },
            text = {
                OutlinedTextField(
                    value = msg, onValueChange = { msg = it },
                    label = { Text("תוכן ההודעה") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2
                )
            },
            confirmButton = {
                Button(onClick = { vm.sendCommand(token, deviceId, "SHOW_MESSAGE", msg); showMessageDialog = false }, enabled = msg.isNotBlank()) { Text("שלח") }
            },
            dismissButton = { OutlinedButton(onClick = { showMessageDialog = false }) { Text("ביטול") } }
        )
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            icon = { Icon(Icons.Default.PersonRemove, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("הסר מכשיר") },
            text = { Text("להסיר את \"${device?.deviceNickname ?: device?.deviceModel}\"?") },
            confirmButton = {
                Button(onClick = { vm.removeDevice(token, deviceId); confirmRemove = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("הסר") }
            },
            dismissButton = { OutlinedButton(onClick = { confirmRemove = false }) { Text("ביטול") } }
        )
    }

    if (confirmRelease) {
        AlertDialog(
            onDismissRequest = { confirmRelease = false },
            icon = { Icon(Icons.Default.LockOpen, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("שחרר ומחק") },
            text = { Text("פעולה זו תסיר את כל ההגנות ותפתח אפשרות מחיקה. לא ניתן לבטל.") },
            confirmButton = {
                Button(onClick = { vm.sendCommand(token, deviceId, "RELEASE_ALL"); confirmRelease = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("שחרר") }
            },
            dismissButton = { OutlinedButton(onClick = { confirmRelease = false }) { Text("ביטול") } }
        )
    }

    if (confirmAllowUninstall) {
        AlertDialog(
            onDismissRequest = { confirmAllowUninstall = false },
            icon = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("אפשר הסרת אפליקציה?") },
            text = { Text("פעולה זו תאפשר למשתמש להסיר את Tether מהמכשיר. הגנות יבוטלו תוך דקה.") },
            confirmButton = {
                Button(
                    onClick = { vm.toggleAllowUninstall(token, deviceId, true); confirmAllowUninstall = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("אשר") }
            },
            dismissButton = { OutlinedButton(onClick = { confirmAllowUninstall = false }) { Text("ביטול") } }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (editingNickname) {
                        OutlinedTextField(
                            value = nicknameInput,
                            onValueChange = { nicknameInput = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                            trailingIcon = {
                                IconButton(onClick = {
                                    vm.renameDevice(token, deviceId, nicknameInput.trim().ifEmpty { null })
                                    editingNickname = false
                                }) { Icon(Icons.Default.Check, null) }
                            }
                        )
                    } else {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(device?.deviceNickname ?: device?.deviceModel ?: "מכשיר", fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.width(4.dp))
                                IconButton(onClick = { editingNickname = true }, modifier = Modifier.size(20.dp)) {
                                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (device?.deviceNickname != null) {
                                Text(device!!.deviceModel, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowForward, "חזרה") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            )
        }
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val d = device ?: return@Scaffold

        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { vm.load(token, deviceId, isRefresh = true) },
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
        Column(Modifier.fillMaxSize()) {
            // ג”€ג”€ Status header ג”€ג”€
            DeviceStatusHeader(d, onSync = { vm.sendCommand(token, deviceId, "FORCE_SYNC") },
                onMessage = { showMessageDialog = true },
                onRelease = { confirmRelease = true },
                onRemove = { confirmRemove = true },
                allowUninstall = d.allowUninstall,
                onToggleUninstall = { allow ->
                    if (allow) confirmAllowUninstall = true
                    else vm.toggleAllowUninstall(token, deviceId, false)
                })

            // ג”€ג”€ Tabs ג”€ג”€
            val tabs = listOf("אפליקציות", "הגדרות אישיות", "אירועים")
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { i, title ->
                    Tab(selected = selectedTab == i, onClick = { selectedTab = i },
                        text = { Text(title) },
                        icon = {
                            Icon(when (i) {
                                0 -> Icons.Outlined.Apps
                                1 -> Icons.Outlined.Tune
                                else -> Icons.Outlined.Security
                            }, null, modifier = Modifier.size(18.dp))
                        }
                    )
                }
            }

            when (selectedTab) {
                0 -> AppsTab(
                    apps = d.installedApps,
                    blockedByDevice = d.devicePolicy.blockedApps,
                    allowedByDevice = d.devicePolicy.allowedApps,
                    onToggleBlock = { pkg, block ->
                        val current = d.devicePolicy
                        val newPolicy = if (block) {
                            current.copy(
                                blockedApps = (current.blockedApps + pkg).distinct(),
                                allowedApps = current.allowedApps - pkg
                            )
                        } else {
                            current.copy(blockedApps = current.blockedApps - pkg)
                        }
                        vm.saveDevicePolicy(token, deviceId, newPolicy)
                    }
                )
                1 -> DevicePolicyTab(
                    policy = d.devicePolicy,
                    saving = saving,
                    onSave = { vm.saveDevicePolicy(token, deviceId, it) }
                )
                2 -> SecurityEventsTab(events = events)
            }
        }
        } // end PullToRefreshBox
    }
}

// ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€
// Shared UI helpers (local copies — ProtectionChip is private in its origin file)
// ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€

@Composable
private fun ProtectionChip(label: String, active: Boolean, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
        modifier = modifier
    ) {
        Row(
            Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                if (active) Icons.Default.Check else Icons.Default.Close, null,
                modifier = Modifier.size(10.dp),
                tint = if (active) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
            )
            Text(label, fontSize = 11.sp,
                color = if (active) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error)
        }
    }
}

// ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€
// Status header
// ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€

@Composable
private fun DeviceStatusHeader(
    device: DeviceDetail,
    onSync: () -> Unit,
    onMessage: () -> Unit,
    onRelease: () -> Unit,
    onRemove: () -> Unit,
    allowUninstall: Boolean,
    onToggleUninstall: (Boolean) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            // Protection chips
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                ProtectionChip("נגישות", device.protectionStatus.accessibilityEnabled, Modifier.weight(1f))
                ProtectionChip("מנהל", device.protectionStatus.isDeviceAdmin, Modifier.weight(1f))
                ProtectionChip("VPN", device.protectionStatus.vpnActive, Modifier.weight(1f))
                if (device.isDeviceOwner) {
                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.weight(1f)) {
                        Text("DO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(Modifier.height(10.dp))
            // Actions
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = onSync, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Sync, "סנכרן", modifier = Modifier.size(18.dp))
                }
                FilledTonalIconButton(onClick = onMessage, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Message, "הודעה", modifier = Modifier.size(18.dp))
                }
                FilledTonalIconButton(onClick = onRelease, modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Icon(Icons.Default.LockOpen, "שחרר", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.weight(1f))
                Text("הסר-התקנה", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Switch(checked = allowUninstall, onCheckedChange = onToggleUninstall,
                    modifier = Modifier.height(24.dp),
                    colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.error, checkedTrackColor = MaterialTheme.colorScheme.errorContainer))
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.PersonRemove, "הסר", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

// ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€
// Apps Tab
// ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€

@Composable
private fun AppsTab(
    apps: List<InstalledApp>,
    blockedByDevice: List<String>,
    allowedByDevice: List<String>,
    onToggleBlock: (String, Boolean) -> Unit
) {
    // זמנית מציגים הכל כדי להבין מה קורה
    val displayedApps = remember(apps) { apps.sortedBy { it.appName.lowercase() } }
    var searchQuery by remember { mutableStateOf("") }

    val filtered = remember(displayedApps, searchQuery) {
        if (searchQuery.isBlank()) displayedApps
        else displayedApps.filter {
            it.appName.contains(searchQuery, ignoreCase = true) ||
            it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery, onValueChange = { searchQuery = it },
            placeholder = { Text("חפש אפליקציה...") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, null) }
                }
            }
        )

        if (apps.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Apps, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("אין נתוני אפליקציות ברשימה שהגיעה מהשרת", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("נא לבצע סנכרון במכשיר היעד", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Text("${filtered.size} אפליקציות", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp))
            }
            items(filtered) { app ->
                val isBlocked = app.packageName in blockedByDevice
                AppRow(app = app, isBlocked = isBlocked, onToggleBlock = { onToggleBlock(app.packageName, it) })
            }
        }
    }
}

@Composable
private fun AppRow(app: InstalledApp, isBlocked: Boolean, onToggleBlock: (Boolean) -> Unit) {
    Surface(
        color = if (isBlocked) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isBlocked) Icons.Default.Block else Icons.Default.Android,
                null,
                modifier = Modifier.size(20.dp),
                tint = if (isBlocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(app.appName.ifEmpty { app.packageName }, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(app.packageName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = isBlocked,
                onCheckedChange = onToggleBlock,
                modifier = Modifier.height(24.dp),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.error,
                    checkedTrackColor = MaterialTheme.colorScheme.errorContainer
                )
            )
        }
    }
}

// ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€
// Device Policy Tab (per-device overrides)
// ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€

@Composable
private fun DevicePolicyTab(
    policy: DevicePolicy,
    saving: Boolean,
    onSave: (DevicePolicy) -> Unit
) {
    var edited by remember(policy) { mutableStateOf(policy) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "הגדרות כאן מחליפות את מדיניות הקהילה למכשיר הזה בלבד. ירושה = שימוש בהגדרת קהילה.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                            Icon(Icons.Default.Block, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("חסימות למכשיר", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        }
                        HorizontalDivider(Modifier.padding(bottom = 12.dp))

                        NullablePolicyRow("חסום התקנת אפליקציות", edited.blockInstallApps) {
                            edited = edited.copy(blockInstallApps = it)
                        }
                        NullablePolicyRow("הסתר Google Play", edited.hideGooglePlay) {
                            edited = edited.copy(hideGooglePlay = it)
                        }
                        NullablePolicyRow("חסום כל החנויות", edited.blockAllStores) {
                            edited = edited.copy(blockAllStores = it)
                        }
                        NullablePolicyRow("חסום התקנת APK", edited.blockApkInstall) {
                            edited = edited.copy(blockApkInstall = it)
                        }
                        NullablePolicyRow("חסום Safe Boot", edited.blockSafeBoot) {
                            edited = edited.copy(blockSafeBoot = it)
                        }
                        NullablePolicyRow("חסום Factory Reset", edited.blockFactoryReset) {
                            edited = edited.copy(blockFactoryReset = it)
                        }
                        NullablePolicyRow("חסום USB Transfer", edited.blockUsbTransfer) {
                            edited = edited.copy(blockUsbTransfer = it)
                        }
                        NullablePolicyRow("איסוף לוגים", edited.logsEnabled) {
                            edited = edited.copy(logsEnabled = it)
                        }
                        NullablePolicyRow("חסום ערוצי WhatsApp", edited.blockWhatsAppChannels) {
                            edited = edited.copy(blockWhatsAppChannels = it)
                        }
                    }
                }
            }

            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 2.dp)) {
                            Icon(Icons.Default.Tune, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("התנהגות ופרטי ניהול", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        }
                        HorizontalDivider(Modifier.padding(bottom = 6.dp))

                        Text("Blocked action behavior", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        NullableEnumChipRow(
                            current = edited.blockedActionBehavior,
                            options = listOf(
                                "ירושה" to null,
                                "SILENT" to BlockedActionBehavior.SILENT,
                                "MESSAGE" to BlockedActionBehavior.SHOW_MESSAGE,
                                "APPROVAL" to BlockedActionBehavior.REQUEST_APPROVAL
                            ),
                            onSelect = { edited = edited.copy(blockedActionBehavior = it) }
                        )

                        OutlinedTextField(
                            value = edited.maxInstalledApps?.toString() ?: "",
                            onValueChange = { value ->
                                edited = edited.copy(maxInstalledApps = value.toIntOrNull())
                            },
                            label = { Text("Max installed apps (ריק = ירושה)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = edited.adminEmergencyCode ?: "",
                            onValueChange = { edited = edited.copy(adminEmergencyCode = it.ifBlank { null }) },
                            label = { Text("קוד חירום למכשיר (ריק = ירושה)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = edited.supportName ?: "",
                            onValueChange = { edited = edited.copy(supportName = it.ifBlank { null }) },
                            label = { Text("שם תמיכה (ריק = ירושה)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = edited.supportWhatsApp ?: "",
                            onValueChange = { edited = edited.copy(supportWhatsApp = it.ifBlank { null }) },
                            label = { Text("WhatsApp תמיכה (ריק = ירושה)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = edited.supportEmail ?: "",
                            onValueChange = { edited = edited.copy(supportEmail = it.ifBlank { null }) },
                            label = { Text("אימייל תמיכה (ריק = ירושה)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                            Icon(Icons.Default.Apps, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("אפליקציות", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        }
                        HorizontalDivider(Modifier.padding(bottom = 12.dp))
                        SimpleAppListSection(
                            title = "חסום לפי package",
                            apps = edited.blockedApps,
                            onChange = { edited = edited.copy(blockedApps = it) }
                        )
                        Spacer(Modifier.height(12.dp))
                        SimpleAppListSection(
                            title = "אפשר תמיד (override)",
                            apps = edited.allowedApps,
                            onChange = { edited = edited.copy(allowedApps = it) }
                        )
                    }
                }
            }

            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 2.dp)) {
                            Icon(Icons.Default.Language, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("סינון רשת", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        }
                        HorizontalDivider(Modifier.padding(bottom = 6.dp))

                        NullableEnumChipRow(
                            current = edited.webFilterMode,
                            options = listOf(
                                "ירושה" to null,
                                "NONE" to WebFilterMode.NONE,
                                "BLACKLIST" to WebFilterMode.BLACKLIST,
                                "WHITELIST" to WebFilterMode.WHITELIST
                            ),
                            onSelect = { mode ->
                                edited = edited.copy(
                                    webFilterMode = mode,
                                    blockedDomains = if (mode == WebFilterMode.BLACKLIST) edited.blockedDomains else null,
                                    allowedDomains = if (mode == WebFilterMode.WHITELIST) edited.allowedDomains else null
                                )
                            }
                        )

                        AnimatedVisibility(edited.webFilterMode == WebFilterMode.BLACKLIST) {
                            DomainListSection(
                                title = "דומיינים חסומים",
                                domains = edited.blockedDomains ?: emptyList(),
                                onChange = { edited = edited.copy(blockedDomains = it) }
                            )
                        }
                        AnimatedVisibility(edited.webFilterMode == WebFilterMode.WHITELIST) {
                            DomainListSection(
                                title = "דומיינים מותרים",
                                domains = edited.allowedDomains ?: emptyList(),
                                onChange = { edited = edited.copy(allowedDomains = it) }
                            )
                        }
                    }
                }
            }

            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                            Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("נעילות זמן", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        }
                        HorizontalDivider(Modifier.padding(bottom = 12.dp))
                        DeviceTimeLockSection(
                            lockedUntilTs = edited.lockedUntilTs,
                            onChange = { edited = edited.copy(lockedUntilTs = it) }
                        )
                        Spacer(Modifier.height(12.dp))
                        DeviceAppTimeLocksSection(
                            locks = edited.appTimeLocks,
                            onChange = { edited = edited.copy(appTimeLocks = it) }
                        )
                    }
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Button(
                onClick = { onSave(edited) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !saving
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Save, null)
                    Spacer(Modifier.width(8.dp))
                    Text("שמור הגדרות מכשיר", fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun NullablePolicyRow(label: String, value: Boolean?, onChange: (Boolean?) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
        val options = listOf(null, true, false)
        val labels = listOf("ירושה", "כן", "לא")
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEachIndexed { index, option ->
                FilterChip(
                    selected = value == option,
                    onClick = { onChange(option) },
                    label = { Text(labels[index], fontSize = 11.sp) },
                    modifier = Modifier.height(28.dp)
                )
            }
        }
    }
}

@Composable
private fun <T> NullableEnumChipRow(
    current: T?,
    options: List<Pair<String, T?>>,
    onSelect: (T?) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        options.forEach { (label, option) ->
            FilterChip(
                selected = current == option,
                onClick = { onSelect(option) },
                label = { Text(label, fontSize = 11.sp) }
            )
        }
    }
}

@Composable
private fun DomainListSection(title: String, domains: List<String>, onChange: (List<String>) -> Unit) {
    var input by remember { mutableStateOf("") }
    Column {
        Text(title, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("youtube.com", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(onClick = {
                val domain = input.trim().lowercase()
                if (domain.isNotEmpty() && domain !in domains) {
                    onChange(domains + domain)
                    input = ""
                }
            }) { Icon(Icons.Default.Add, null) }
        }
        domains.forEach { domain ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Language, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(domain, fontSize = 12.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = { onChange(domains - domain) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun SecurityEventsTab(events: List<SecurityEvent>) {
    if (events.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Security, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text("אין אירועי אבטחה", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item { Text("${events.size} אירועים (חדשים ראשונים)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(events) { event ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        when (event.type) {
                            "UNINSTALL_ATTEMPT" -> Icons.Default.DeleteForever
                            "ADMIN_DEACTIVATE_ATTEMPT" -> Icons.Default.AdminPanelSettings
                            "BLOCKED_APP_OPENED" -> Icons.Default.Block
                            "TIME_LOCK_BLOCKED" -> Icons.Default.Lock
                            else -> Icons.Default.Warning
                        },
                        null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when (event.type) {
                                "UNINSTALL_ATTEMPT" -> "ניסיון הסרת אפליקציה"
                                "ADMIN_DEACTIVATE_ATTEMPT" -> "ניסיון ביטול הרשאות מנהל"
                                "BLOCKED_APP_OPENED" -> "ניסיון פתיחת אפליקציה חסומה"
                                "TIME_LOCK_BLOCKED" -> "חסימת זמן פעלה"
                                else -> event.type
                            },
                            fontSize = 13.sp, fontWeight = FontWeight.Medium
                        )
                        if (!event.packageName.isNullOrEmpty()) {
                            Text(event.packageName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(event.timestamp.take(19).replace("T", " "),
                            fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceTimeLockSection(lockedUntilTs: Long?, onChange: (Long?) -> Unit) {
    val context = LocalContext.current
    val sdf = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val now = System.currentTimeMillis()
    val isLocked = lockedUntilTs != null && lockedUntilTs > now

    val showPicker = {
        val cal = Calendar.getInstance()
        if (lockedUntilTs != null) cal.timeInMillis = lockedUntilTs
        val timePicker = TimePickerDialog(context,
            { _, h, m -> cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, m); onChange(cal.timeInMillis) },
            cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true)
        DatePickerDialog(context,
            { _, y, mo, d -> cal.set(y, mo, d); timePicker.show() },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    Column {
        if (isLocked) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("נעול עד: ${sdf.format(Date(lockedUntilTs!!))}", color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onChange(null) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(onClick = showPicker, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Schedule, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (isLocked) "שנה תאריך שחרור" else "נעל מכשיר זה עד...")
        }
    }
}

@Composable
private fun DeviceAppTimeLocksSection(locks: List<AppTimeLock>, onChange: (List<AppTimeLock>) -> Unit) {
    val context = LocalContext.current
    val sdf = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
    var newPkg by remember { mutableStateOf("") }

    Column {
        Text("נעילת אפליקציות ספציפיות", fontWeight = FontWeight.Medium, fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = newPkg, onValueChange = { newPkg = it },
                placeholder = { Text("com.example.app", fontSize = 12.sp) },
                singleLine = true, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(onClick = {
                val pkg = newPkg.trim()
                if (pkg.isNotEmpty() && locks.none { it.packageName == pkg }) {
                    onChange(locks + AppTimeLock(packageName = pkg, lockedUntilTs = null)); newPkg = ""
                }
            }) { Icon(Icons.Default.Add, null) }
        }
        locks.forEach { lock ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Block, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text(lock.packageName, fontSize = 13.sp)
                    Text(if (lock.lockedUntilTs == null) "חסום לצמיתות" else "חסום עד: ${sdf.format(Date(lock.lockedUntilTs))}",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = {
                    val cal = Calendar.getInstance()
                    if (lock.lockedUntilTs != null) cal.timeInMillis = lock.lockedUntilTs
                    val tp = TimePickerDialog(context, { _, h, m ->
                        cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, m)
                        onChange(locks.map { if (it.packageName == lock.packageName) it.copy(lockedUntilTs = cal.timeInMillis) else it })
                    }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true)
                    DatePickerDialog(context, { _, y, mo, d -> cal.set(y, mo, d); tp.show() },
                        cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { onChange(locks.filter { it.packageName != lock.packageName }) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun SimpleAppListSection(title: String, apps: List<String>, onChange: (List<String>) -> Unit) {
    var input by remember { mutableStateOf("") }
    Column {
        Text(title, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                placeholder = { Text("com.example.app", fontSize = 12.sp) },
                singleLine = true, modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(onClick = {
                val pkg = input.trim()
                if (pkg.isNotEmpty() && pkg !in apps) { onChange(apps + pkg); input = "" }
            }) { Icon(Icons.Default.Add, null) }
        }
        apps.forEach { pkg ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Android, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(pkg, fontSize = 12.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = { onChange(apps - pkg) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

