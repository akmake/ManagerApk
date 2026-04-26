package com.sterni.tether.ui.screens.admin

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import com.sterni.tether.data.model.DeviceDetail
import com.sterni.tether.data.model.DevicePolicy
import com.sterni.tether.data.model.InstalledApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

class DeviceDetailViewModel(app: Application) : AndroidViewModel(app) {
    private val api = RetrofitClient.create(AdminApiService::class.java)

    private val _device = MutableStateFlow<DeviceDetail?>(null)
    val device: StateFlow<DeviceDetail?> = _device

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar: StateFlow<String?> = _snackbar

    private val _removed = MutableStateFlow(false)
    val removed: StateFlow<Boolean> = _removed

    fun load(token: String, deviceId: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val res = api.getDeviceDetail(token, deviceId)
                if (res.isSuccessful) _device.value = res.body()
            } catch (_: Exception) {}
            _loading.value = false
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

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

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
    val saving by vm.saving.collectAsStateWithLifecycle()
    val snackbarMsg by vm.snackbar.collectAsStateWithLifecycle()
    val removed by vm.removed.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showMessageDialog by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var confirmRelease by remember { mutableStateOf(false) }
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

        Column(Modifier.fillMaxSize().padding(padding)) {
            // ── Status header ──
            DeviceStatusHeader(d, onSync = { vm.sendCommand(token, deviceId, "FORCE_SYNC") },
                onMessage = { showMessageDialog = true },
                onRelease = { confirmRelease = true },
                onRemove = { confirmRemove = true },
                allowUninstall = d.allowUninstall,
                onToggleUninstall = { vm.toggleAllowUninstall(token, deviceId, it) })

            // ── Tabs ──
            val tabs = listOf("אפליקציות", "הגדרות אישיות")
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { i, title ->
                    Tab(selected = selectedTab == i, onClick = { selectedTab = i },
                        text = { Text(title) },
                        icon = {
                            Icon(when (i) {
                                0 -> Icons.Outlined.Apps
                                else -> Icons.Outlined.Tune
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
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared UI helpers (local copies — ProtectionChip is private in its origin file)
// ─────────────────────────────────────────────────────────────────────────────

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

// ─────────────────────────────────────────────────────────────────────────────
// Status header
// ─────────────────────────────────────────────────────────────────────────────

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

// ─────────────────────────────────────────────────────────────────────────────
// Apps Tab
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AppsTab(
    apps: List<InstalledApp>,
    blockedByDevice: List<String>,
    allowedByDevice: List<String>,
    onToggleBlock: (String, Boolean) -> Unit
) {
    val nonSystemApps = remember(apps) { apps.filter { !it.isSystemApp } }
    var searchQuery by remember { mutableStateOf("") }

    val filtered = remember(nonSystemApps, searchQuery) {
        if (searchQuery.isBlank()) nonSystemApps
        else nonSystemApps.filter {
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

        if (nonSystemApps.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Apps, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("אין נתוני אפליקציות", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("האפליקציה תדווח בסנכרון הבא", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

// ─────────────────────────────────────────────────────────────────────────────
// Device Policy Tab (per-device overrides)
// ─────────────────────────────────────────────────────────────────────────────

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
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(8.dp))
                        Text("הגדרות כאן מחליפות את מדיניות הקהילה למכשיר זה בלבד. הגדרה ריקה = ירש מהקהילה.",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }

            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                            Icon(Icons.Default.Block, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("חסימות אישיות", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
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
                        NullablePolicyRow("חסום התקנת APK ידנית", edited.blockApkInstall) {
                            edited = edited.copy(blockApkInstall = it)
                        }
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
        }

        Box(Modifier.fillMaxWidth().align(Alignment.BottomCenter)
            .padding(horizontal = 16.dp, vertical = 12.dp)) {
            Button(onClick = { onSave(edited) }, modifier = Modifier.fillMaxWidth().height(52.dp), enabled = !saving) {
                if (saving) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                else { Icon(Icons.Default.Save, null); Spacer(Modifier.width(8.dp)); Text("שמור הגדרות מכשיר", fontSize = 16.sp) }
            }
        }
    }
}

@Composable
private fun NullablePolicyRow(label: String, value: Boolean?, onChange: (Boolean?) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
        // 3-state: null=inherit, true=on, false=off
        val options = listOf(null, true, false)
        val labels = listOf("ירש", "כן", "לא")
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEachIndexed { i, opt ->
                FilterChip(
                    selected = value == opt,
                    onClick = { onChange(opt) },
                    label = { Text(labels[i], fontSize = 11.sp) },
                    modifier = Modifier.height(28.dp)
                )
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
