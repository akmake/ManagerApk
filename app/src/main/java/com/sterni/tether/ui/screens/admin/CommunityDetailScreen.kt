package com.sterni.tether.ui.screens.admin

import android.app.Application
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import com.sterni.tether.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€
// ViewModel
// ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€

class CommunityDetailViewModel(app: Application) : AndroidViewModel(app) {
    private val api = RetrofitClient.create(AdminApiService::class.java)

    private val _community = MutableStateFlow<AdminCommunity?>(null)
    val community: StateFlow<AdminCommunity?> = _community

    private val _devices = MutableStateFlow<List<AdminDevice>>(emptyList())
    val devices: StateFlow<List<AdminDevice>> = _devices

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar: StateFlow<String?> = _snackbar

    fun load(token: String, communityId: String, isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isRefresh) _refreshing.value = true else _loading.value = true
            try {
                val res = api.getCommunityDetail(token, communityId)
                if (res.isSuccessful) {
                    _community.value = res.body()?.community
                    _devices.value = res.body()?.devices ?: emptyList()
                }
            } catch (_: Exception) {}
            _loading.value = false
            _refreshing.value = false
        }
    }

    fun updatePolicy(token: String, communityId: String, policy: CommunityPolicy) {
        viewModelScope.launch {
            _saving.value = true
            try {
                val res = api.updatePolicy(token, communityId, policy)
                if (res.isSuccessful) {
                    _community.value = _community.value?.copy(policy = policy)
                    _snackbar.value = "המדיניות נשמרה ✓"
                } else {
                    _snackbar.value = "שגיאה בשמירה"
                }
            } catch (_: Exception) { _snackbar.value = "שגיאת רשת" }
            _saving.value = false
        }
    }

    fun removeDevice(token: String, deviceId: String) {
        viewModelScope.launch {
            try {
                api.removeDevice(token, deviceId)
                _devices.value = _devices.value.filter { it.deviceId != deviceId }
                _snackbar.value = "המכשיר הוסר"
            } catch (_: Exception) { _snackbar.value = "שגיאה בהסרה" }
        }
    }

    fun toggleAllowUninstall(token: String, deviceId: String, allow: Boolean) {
        viewModelScope.launch {
            try {
                api.setAllowUninstall(token, deviceId, mapOf("allowUninstall" to allow))
                _devices.value = _devices.value.map {
                    if (it.deviceId == deviceId) it.copy(allowUninstall = allow) else it
                }
            } catch (_: Exception) {}
        }
    }

    fun sendCommand(token: String, deviceId: String, type: String, payload: String = "") {
        viewModelScope.launch {
            try {
                api.sendCommand(token, deviceId, mapOf("type" to type, "payload" to payload))
                _snackbar.value = when (type) {
                    "FORCE_SYNC"   -> "פקודת סנכרון נשלחה"
                    "SHOW_MESSAGE" -> "ההודעה נשלחה"
                    "RELEASE_ALL"  -> "שחרור מלא נשלח"
                    else           -> "הפקודה נשלחה"
                }
            } catch (_: Exception) { _snackbar.value = "שגיאה בשליחת פקודה" }
        }
    }

    fun renameDevice(token: String, deviceId: String, nickname: String?) {
        viewModelScope.launch {
            try {
                api.renameDevice(token, deviceId, mapOf("nickname" to nickname))
                _devices.value = _devices.value.map {
                    if (it.deviceId == deviceId) it.copy(deviceNickname = nickname) else it
                }
            } catch (_: Exception) {}
        }
    }

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted

    fun deleteCommunity(token: String, communityId: String) {
        viewModelScope.launch {
            try {
                val res = api.deleteCommunity(token, communityId)
                if (res.isSuccessful) {
                    _deleted.value = true
                } else {
                    _snackbar.value = "שגיאה במחיקת קהילה"
                }
            } catch (_: Exception) { _snackbar.value = "שגיאת רשת" }
        }
    }

    fun clearSnackbar() { _snackbar.value = null }
}

// ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€
// Main Screen
// ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityDetailScreen(
    communityId: String,
    onShareClick: (String, String) -> Unit,
    onDeviceClick: (String) -> Unit = {},
    onBack: () -> Unit,
    onDeleted: () -> Unit = onBack,
    vm: CommunityDetailViewModel = viewModel()
) {
    val context = LocalContext.current
    val token = AdminSession.getToken(context) ?: ""
    val community by vm.community.collectAsStateWithLifecycle()
    val devices by vm.devices.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val snackbarMsg by vm.snackbar.collectAsStateWithLifecycle()
    val deleted by vm.deleted.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(deleted) { if (deleted) onDeleted() }
    LaunchedEffect(snackbarMsg) {
        snackbarMsg?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearSnackbar()
        }
    }
    LaunchedEffect(Unit) { vm.load(token, communityId) }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("מכשירים", "מדיניות", "שיתוף")
    var showDeleteCommunityDialog by remember { mutableStateOf(false) }

    if (showDeleteCommunityDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteCommunityDialog = false },
            icon = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("מחק קהילה") },
            text = { Text("למחוק את \"${community?.name}\"? כל המכשירים יאבדו את ההגנה ולא ניתן לשחזר.") },
            confirmButton = {
                Button(
                    onClick = { vm.deleteCommunity(token, communityId); showDeleteCommunityDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("מחק") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteCommunityDialog = false }) { Text("ביטול") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(community?.name ?: "קהילה", fontWeight = FontWeight.SemiBold)
                        Text(
                            "${devices.size} מכשירים",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowForward, "חזרה")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteCommunityDialog = true }) {
                        Icon(Icons.Default.DeleteForever, "מחק קהילה",
                            tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            )
        }
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { i, title ->
                    Tab(
                        selected = selectedTab == i,
                        onClick = { selectedTab = i },
                        text = { Text(title) },
                        icon = {
                            Icon(
                                when (i) {
                                    0 -> Icons.Outlined.PhoneAndroid
                                    1 -> Icons.Outlined.Shield
                                    else -> Icons.Outlined.Share
                                },
                                null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }

            when (selectedTab) {
                0 -> DevicesTab(
                    devices = devices,
                    isRefreshing = refreshing,
                    onRefresh = { vm.load(token, communityId, isRefresh = true) },
                    onDeviceClick = onDeviceClick,
                    onRemove = { vm.removeDevice(token, it) },
                    onToggleUninstall = { id, allow -> vm.toggleAllowUninstall(token, id, allow) },
                    onSendCommand = { id, type, payload -> vm.sendCommand(token, id, type, payload) },
                    onRename = { id, nick -> vm.renameDevice(token, id, nick) }
                )
                1 -> PolicyTab(
                    policy = community?.policy,
                    saving = saving,
                    onSave = { policy: CommunityPolicy ->
                        community?.let { vm.updatePolicy(token, it.id, policy) }
                    }
                )
                2 -> ShareTab(community = community, onShare = onShareClick)
            }
        }
    }
}

// ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€
// Devices Tab
// ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DevicesTab(
    devices: List<AdminDevice>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onDeviceClick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onToggleUninstall: (String, Boolean) -> Unit,
    onSendCommand: (String, String, String) -> Unit,
    onRename: (String, String?) -> Unit
) {
    var confirmRemove by remember { mutableStateOf<AdminDevice?>(null) }
    var showMessageDialog by remember { mutableStateOf<AdminDevice?>(null) }
    var confirmRelease by remember { mutableStateOf<AdminDevice?>(null) }

    if (devices.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.PhoneAndroid, null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Text("אין מכשירים עדיין",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 16.sp)
            }
        }
        return
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(devices) { device ->
                DeviceCard(
                    device = device,
                    onClick = { onDeviceClick(device.deviceId) },
                    onRemove = { confirmRemove = device },
                    onToggleUninstall = { onToggleUninstall(device.deviceId, it) },
                    onSync = { onSendCommand(device.deviceId, "FORCE_SYNC", "") },
                    onMessage = { showMessageDialog = device },
                    onRelease = { confirmRelease = device },
                    onRename = { onRename(device.deviceId, it) }
                )
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    // Confirm remove dialog
    confirmRemove?.let { device ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            icon = { Icon(Icons.Default.PersonRemove, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("הסר מכשיר") },
            text = { Text("להסיר את \"${device.deviceNickname ?: device.deviceModel}\"? המכשיר יאבד את ההגנה.") },
            confirmButton = {
                Button(
                    onClick = { onRemove(device.deviceId); confirmRemove = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("הסר") }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmRemove = null }) { Text("ביטול") }
            }
        )
    }

    // Send message dialog
    showMessageDialog?.let { device ->
        var message by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showMessageDialog = null },
            icon = { Icon(Icons.Default.Message, null) },
            title = { Text("שלח הודעה") },
            text = {
                Column {
                    Text("ל: ${device.deviceNickname ?: device.deviceModel}",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        label = { Text("תוכן ההודעה") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (message.isNotBlank()) {
                            onSendCommand(device.deviceId, "SHOW_MESSAGE", message)
                        }
                        showMessageDialog = null
                    },
                    enabled = message.isNotBlank()
                ) { Text("שלח") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showMessageDialog = null }) { Text("ביטול") }
            }
        )
    }

    // Confirm release dialog
    confirmRelease?.let { device ->
        AlertDialog(
            onDismissRequest = { confirmRelease = null },
            icon = { Icon(Icons.Default.LockOpen, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("שחרר ומחק") },
            text = { Text("פעולה זו תסיר את כל ההגנות מ\"${device.deviceNickname ?: device.deviceModel}\" ותפתח אפשרות מחיקה. לא ניתן לבטל.") },
            confirmButton = {
                Button(
                    onClick = { onSendCommand(device.deviceId, "RELEASE_ALL", ""); confirmRelease = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("שחרר") }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmRelease = null }) { Text("ביטול") }
            }
        )
    }
}

@Composable
private fun DeviceCard(
    device: AdminDevice,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onToggleUninstall: (Boolean) -> Unit,
    onSync: () -> Unit,
    onMessage: () -> Unit,
    onRelease: () -> Unit,
    onRename: (String?) -> Unit
) {
    var editingNickname by remember { mutableStateOf(false) }
    var nicknameInput by remember(device.deviceNickname) {
        mutableStateOf(device.deviceNickname ?: "")
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(16.dp)) {

            // ג”€ג”€ Header row ג”€ג”€
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (editingNickname) {
                        OutlinedTextField(
                            value = nicknameInput,
                            onValueChange = { nicknameInput = it },
                            label = { Text("שם מכשיר") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                onRename(nicknameInput.trim().ifEmpty { null })
                                editingNickname = false
                            }),
                            trailingIcon = {
                                IconButton(onClick = {
                                    onRename(nicknameInput.trim().ifEmpty { null })
                                    editingNickname = false
                                }) { Icon(Icons.Default.Check, "שמור") }
                            }
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                device.deviceNickname ?: device.deviceModel,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            )
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick = { editingNickname = true },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(Icons.Default.Edit, "ערוך שם",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (device.deviceNickname != null) {
                            Text(device.deviceModel,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Online / offline badge
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (device.isOnline)
                        MaterialTheme.colorScheme.secondaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .background(
                                    color = if (device.isOnline)
                                        MaterialTheme.colorScheme.secondary
                                    else
                                        MaterialTheme.colorScheme.outline,
                                    shape = MaterialTheme.shapes.small
                                )
                        )
                        Text(
                            if (device.isOnline) "מחובר" else "לא פעיל",
                            fontSize = 11.sp,
                            color = if (device.isOnline)
                                MaterialTheme.colorScheme.secondary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ג”€ג”€ Protection layer indicators ג”€ג”€
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ProtectionChip(
                    label = "נגישות",
                    active = device.protectionStatus.accessibilityEnabled,
                    modifier = Modifier.weight(1f)
                )
                ProtectionChip(
                    label = "מנהל",
                    active = device.protectionStatus.isDeviceAdmin,
                    modifier = Modifier.weight(1f)
                )
                ProtectionChip(
                    label = "VPN",
                    active = device.protectionStatus.vpnActive,
                    modifier = Modifier.weight(1f)
                )
                if (device.isDeviceOwner) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "DO",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 10.dp))

            // ג”€ג”€ Action buttons ג”€ג”€
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(onClick = onSync, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Sync, "סנכרן", modifier = Modifier.size(18.dp))
                }
                FilledTonalIconButton(onClick = onMessage, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Message, "הודעה", modifier = Modifier.size(18.dp))
                }
                FilledTonalIconButton(
                    onClick = onRelease,
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Icon(Icons.Default.LockOpen, "שחרר",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error)
                }

                Spacer(Modifier.weight(1f))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("הסר-התקנה",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Switch(
                        checked = device.allowUninstall,
                        onCheckedChange = onToggleUninstall,
                        modifier = Modifier.height(24.dp),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.error,
                            checkedTrackColor = MaterialTheme.colorScheme.errorContainer
                        )
                    )
                }

                Spacer(Modifier.width(4.dp))

                IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.PersonRemove, "הסר",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun ProtectionChip(label: String, active: Boolean, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (active) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.errorContainer,
        modifier = modifier
    ) {
        Row(
            Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                if (active) Icons.Default.Check else Icons.Default.Close,
                null,
                modifier = Modifier.size(10.dp),
                tint = if (active) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.error
            )
            Text(
                label,
                fontSize = 11.sp,
                color = if (active) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.error
            )
        }
    }
}

// ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€
// Policy Tab
// ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€

@Composable
private fun PolicyTab(
    policy: CommunityPolicy?,
    saving: Boolean,
    onSave: (CommunityPolicy) -> Unit
) {
    if (policy == null) return
    var edited by remember(policy) { mutableStateOf(policy) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                PolicySectionCard(title = "מצב חסימת אפליקציות", icon = Icons.Default.Security) {
                    val mode = detectAppControlMode(edited)
                    AppControlModeSelector(
                        selected = mode,
                        onSelect = { selectedMode ->
                            edited = applyAppControlMode(edited, selectedMode)
                        }
                    )
                }
            }

            // ── Section 1: חסימות ──
            item {
                PolicySectionCard(title = "חסימות", icon = Icons.Default.Block) {
                    PolicyRow(
                        label = "חסום התקנת אפליקציות",
                        desc = "מונע התקנה מ-Play Store ומהרשת",
                        value = edited.blockInstallApps
                    ) { edited = edited.copy(blockInstallApps = it) }
                    PolicyRow(
                        label = "הסתר Google Play",
                        desc = "מסתיר ומשבית את חנות Google",
                        value = edited.hideGooglePlay
                    ) { edited = edited.copy(hideGooglePlay = it) }
                    PolicyRow(
                        label = "חסום את כל החנויות",
                        desc = "כולל Samsung Store, MIUI ועוד",
                        value = edited.blockAllStores
                    ) { edited = edited.copy(blockAllStores = it) }
                    PolicyRow(
                        label = "חסום התקנת APK ידנית",
                        desc = "מונע sideloading של קבצי APK",
                        value = edited.blockApkInstall
                    ) { edited = edited.copy(blockApkInstall = it) }
                    PolicyRow(
                        label = "חסום מצב בטוח",
                        value = edited.blockSafeBoot
                    ) { edited = edited.copy(blockSafeBoot = it) }
                    PolicyRow(
                        label = "חסום איפוס יצרן",
                        value = edited.blockFactoryReset
                    ) { edited = edited.copy(blockFactoryReset = it) }
                    PolicyRow(
                        label = "חסום העברת קבצים USB",
                        value = edited.blockUsbTransfer
                    ) { edited = edited.copy(blockUsbTransfer = it) }
                }
            }

            // ── Section 2: אפליקציות ──
            item {
                PolicySectionCard(title = "אפליקציות", icon = Icons.Default.Apps) {
                    AppListSection(
                        title = "אפליקציות חסומות",
                        desc = "Package names לחסימה",
                        icon = Icons.Default.Block,
                        apps = edited.blockedApps,
                        onChange = { edited = edited.copy(blockedApps = it) }
                    )
                    Spacer(Modifier.height(12.dp))
                    AppListSection(
                        title = "אפליקציות מותרות תמיד",
                        desc = "Package names שלעולם לא ייחסמו",
                        icon = Icons.Default.CheckCircle,
                        apps = edited.allowedApps,
                        onChange = { edited = edited.copy(allowedApps = it) }
                    )
                }
            }

            // ── Section 3: נעילת זמן ──
            item {
                PolicySectionCard(title = "נעילת זמן", icon = Icons.Default.Schedule) {
                    TimeLockSection(
                        lockedUntilTs = edited.lockedUntilTs,
                        onChange = { edited = edited.copy(lockedUntilTs = it) }
                    )
                    if (edited.appTimeLocks.isNotEmpty() || true) {
                        Spacer(Modifier.height(12.dp))
                        AppTimeLocksSection(
                            locks = edited.appTimeLocks,
                            onChange = { edited = edited.copy(appTimeLocks = it) }
                        )
                    }
                }
            }

            // ── Section: אפליקציות (מצב רשימה שחורה / לבנה) ──
            item {
                PolicySectionCard(title = "אפליקציות", icon = Icons.Default.Apps) {
                    AppPolicyMode.values().forEach { mode ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = edited.appPolicyMode == mode,
                                onClick = { edited = edited.copy(appPolicyMode = mode) }
                            )
                            Column(Modifier.padding(start = 4.dp)) {
                                Text(
                                    text = when (mode) {
                                        AppPolicyMode.BLACKLIST -> "רשימה שחורה — חסום אפליקציות ספציפיות"
                                        AppPolicyMode.WHITELIST -> "רשימה לבנה — אפשר רק אפליקציות מאושרות"
                                    },
                                    fontSize = 14.sp
                                )
                                if (mode == AppPolicyMode.WHITELIST) {
                                    Text("מצב קיוסק — כל שאר האפליקציות חסומות",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            // ── Section 4: סינון אינטרנט ──
            item {
                PolicySectionCard(title = "סינון אינטרנט", icon = Icons.Default.Language) {
                    WebFilterMode.values().forEach { mode ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = edited.webFilterMode == mode,
                                onClick = { edited = edited.copy(webFilterMode = mode) }
                            )
                            Column(Modifier.padding(start = 4.dp)) {
                                Text(
                                    text = when (mode) {
                                        WebFilterMode.NONE      -> "ללא סינון"
                                        WebFilterMode.BLACKLIST -> "רשימה שחורה — חסום דומיינים"
                                        WebFilterMode.WHITELIST -> "רשימה לבנה — אפשר רק דומיינים"
                                    },
                                    fontSize = 14.sp
                                )
                                if (mode == WebFilterMode.WHITELIST) {
                                    Text("כל שאר האינטרנט חסום",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    AnimatedVisibility(edited.webFilterMode == WebFilterMode.BLACKLIST) {
                        DomainListSection(
                            title = "דומיינים חסומים",
                            domains = edited.blockedDomains,
                            onChange = { edited = edited.copy(blockedDomains = it) }
                        )
                    }
                    AnimatedVisibility(edited.webFilterMode == WebFilterMode.WHITELIST) {
                        DomainListSection(
                            title = "דומיינים מותרים",
                            domains = edited.allowedDomains,
                            onChange = { edited = edited.copy(allowedDomains = it) }
                        )
                    }
                }
            }

            // ── Section 5: מתקדם ──
            item {
                PolicySectionCard(title = "מתקדם", icon = Icons.Default.Tune) {
                    PolicyRow(
                        label = "אפשר לוגים",
                        desc = "שמירת רישום אירועי אבטחה",
                        value = edited.logsEnabled
                    ) { edited = edited.copy(logsEnabled = it) }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Who can resolve approvals",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ApprovalResolverMode.values().forEach { mode ->
                            FilterChip(
                                selected = edited.approvalResolverMode == mode,
                                onClick = { edited = edited.copy(approvalResolverMode = mode) },
                                label = {
                                    Text(
                                        when (mode) {
                                            ApprovalResolverMode.OWNER_ONLY -> "Manager"
                                            ApprovalResolverMode.SUPERADMIN_ONLY -> "Superadmin"
                                            ApprovalResolverMode.OWNER_OR_SUPERADMIN -> "Both"
                                        },
                                        fontSize = 11.sp
                                    )
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    var codeInput by remember(edited.adminEmergencyCode) {
                        mutableStateOf(edited.adminEmergencyCode ?: "")
                    }
                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = {
                            codeInput = it
                            edited = edited.copy(adminEmergencyCode = it.ifBlank { null })
                        },
                        label = { Text("קוד חירום להסרה") },
                        placeholder = { Text("0000") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Key, null) }
                    )
                }
            }
        }

        // ג”€ג”€ Save button (floating at bottom) ג”€ג”€
        Box(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
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
                    Text("שמור שינויים", fontSize = 16.sp)
                }
            }
        }
    }
}

// ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€
// Policy UI primitives
// ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€

private enum class AppControlMode {
    FULL_LOCKDOWN,
    PLAY_VISIBLE_APPROVAL,
    OPEN_INSTALLS
}

private fun detectAppControlMode(policy: CommunityPolicy): AppControlMode {
    return when {
        policy.hideGooglePlay && policy.blockAllStores && policy.blockInstallApps && policy.blockApkInstall ->
            AppControlMode.FULL_LOCKDOWN
        !policy.hideGooglePlay && !policy.blockAllStores && !policy.blockInstallApps && policy.blockApkInstall &&
            policy.blockedActionBehavior == BlockedActionBehavior.REQUEST_APPROVAL ->
            AppControlMode.PLAY_VISIBLE_APPROVAL
        !policy.hideGooglePlay && !policy.blockAllStores && !policy.blockInstallApps && !policy.blockApkInstall ->
            AppControlMode.OPEN_INSTALLS
        else -> AppControlMode.PLAY_VISIBLE_APPROVAL
    }
}

private fun applyAppControlMode(policy: CommunityPolicy, mode: AppControlMode): CommunityPolicy {
    return when (mode) {
        AppControlMode.FULL_LOCKDOWN -> policy.copy(
            hideGooglePlay = true,
            blockAllStores = true,
            blockInstallApps = true,
            blockApkInstall = true,
            blockedActionBehavior = BlockedActionBehavior.SILENT
        )
        AppControlMode.PLAY_VISIBLE_APPROVAL -> policy.copy(
            hideGooglePlay = false,
            blockAllStores = false,
            blockInstallApps = false,
            blockApkInstall = true,
            blockedActionBehavior = BlockedActionBehavior.REQUEST_APPROVAL
        )
        AppControlMode.OPEN_INSTALLS -> policy.copy(
            hideGooglePlay = false,
            blockAllStores = false,
            blockInstallApps = false,
            blockApkInstall = false
        )
    }
}

@Composable
private fun AppControlModeSelector(
    selected: AppControlMode,
    onSelect: (AppControlMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppControlModeCard(
            title = "חסימה מלאה",
            description = "כל חנויות האפליקציות חסומות ומוסתרות",
            selected = selected == AppControlMode.FULL_LOCKDOWN,
            onClick = { onSelect(AppControlMode.FULL_LOCKDOWN) }
        )
        AppControlModeCard(
            title = "Google Play פתוח + אישור מנהל",
            description = "אפשר להיכנס לחנות, התקנה תחסם עד אישור מנהל",
            selected = selected == AppControlMode.PLAY_VISIBLE_APPROVAL,
            onClick = { onSelect(AppControlMode.PLAY_VISIBLE_APPROVAL) }
        )
        AppControlModeCard(
            title = "פתוח",
            description = "ללא חסימת התקנות אפליקציות",
            selected = selected == AppControlMode.OPEN_INSTALLS,
            onClick = { onSelect(AppControlMode.OPEN_INSTALLS) }
        )
    }
}

@Composable
private fun AppControlModeCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(2.dp))
            Text(description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PolicySectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(icon, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
            HorizontalDivider(Modifier.padding(bottom = 12.dp))
            content()
        }
    }
}

@Composable
private fun PolicyRow(
    label: String,
    desc: String? = null,
    value: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp)
            if (desc != null) {
                Text(desc, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun AppListSection(
    title: String,
    desc: String,
    icon: ImageVector,
    apps: List<String>,
    onChange: (List<String>) -> Unit
) {
    var input by remember { mutableStateOf("") }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Column {
                Text(title, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("com.example.app", fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    val pkg = input.trim()
                    if (pkg.isNotEmpty() && pkg !in apps) {
                        onChange(apps + pkg)
                        input = ""
                    }
                })
            )
            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(onClick = {
                val pkg = input.trim()
                if (pkg.isNotEmpty() && pkg !in apps) {
                    onChange(apps + pkg)
                    input = ""
                }
            }) { Icon(Icons.Default.Add, "הוסף") }
        }
        if (apps.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            apps.forEach { pkg ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Android, null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Text(pkg, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = { onChange(apps - pkg) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Close, "הסר",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun DomainListSection(
    title: String,
    domains: List<String>,
    onChange: (List<String>) -> Unit
) {
    var input by remember { mutableStateOf("") }
    Column(Modifier.padding(top = 8.dp)) {
        Text(title, fontWeight = FontWeight.Medium, fontSize = 13.sp,
            color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("youtube.com", fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(onClick = {
                val d = input.trim().lowercase()
                if (d.isNotEmpty() && d !in domains) {
                    onChange(domains + d); input = ""
                }
            }) { Icon(Icons.Default.Add, "הוסף") }
        }
        domains.forEach { domain ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Language, null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(domain, fontSize = 13.sp, modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { onChange(domains - domain) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Close, "הסר",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun TimeLockSection(lockedUntilTs: Long?, onChange: (Long?) -> Unit) {
    val context = LocalContext.current
    val sdf = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val now = System.currentTimeMillis()
    val isLocked = lockedUntilTs != null && lockedUntilTs > now

    val showPicker = {
        val cal = Calendar.getInstance()
        if (lockedUntilTs != null) cal.timeInMillis = lockedUntilTs
        val timePicker = TimePickerDialog(
            context,
            { _, h, m -> cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, m); onChange(cal.timeInMillis) },
            cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true
        )
        DatePickerDialog(
            context,
            { _, y, mo, d -> cal.set(y, mo, d); timePicker.show() },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    Column {
        if (isLocked) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Lock, null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "כל המכשירים נעולים עד: ${sdf.format(Date(lockedUntilTs!!))}",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.Medium
                    )
                    IconButton(
                        onClick = { onChange(null) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Close, "בטל נעילה",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(
            onClick = showPicker,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Schedule, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (isLocked) "שנה תאריך ושעת שחרור" else "נעל את כל המכשירים עד...")
        }
    }
}

@Composable
private fun AppTimeLocksSection(locks: List<AppTimeLock>, onChange: (List<AppTimeLock>) -> Unit) {
    val context = LocalContext.current
    val sdf = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
    var newPkg by remember { mutableStateOf("") }

    Column {
        Text("נעילת אפליקציות ספציפיות",
            fontWeight = FontWeight.Medium, fontSize = 13.sp,
            color = MaterialTheme.colorScheme.secondary)
        Text("null = נעילה לצמיתות",
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newPkg,
                onValueChange = { newPkg = it },
                placeholder = { Text("com.example.app", fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(onClick = {
                val pkg = newPkg.trim()
                if (pkg.isNotEmpty() && locks.none { it.packageName == pkg }) {
                    onChange(locks + AppTimeLock(packageName = pkg, lockedUntilTs = null))
                    newPkg = ""
                }
            }) { Icon(Icons.Default.Add, "הוסף") }
        }
        locks.forEach { lock ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Block, null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text(lock.packageName, fontSize = 13.sp)
                    Text(
                        if (lock.lockedUntilTs == null) "חסום לצמיתות"
                        else "חסום עד: ${sdf.format(Date(lock.lockedUntilTs))}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Set unlock date
                IconButton(
                    onClick = {
                        val cal = Calendar.getInstance()
                        if (lock.lockedUntilTs != null) cal.timeInMillis = lock.lockedUntilTs
                        val timePicker = TimePickerDialog(
                            context,
                            { _, h, m ->
                                cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, m)
                                onChange(locks.map {
                                    if (it.packageName == lock.packageName) it.copy(lockedUntilTs = cal.timeInMillis) else it
                                })
                            },
                            cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true
                        )
                        DatePickerDialog(
                            context,
                            { _, y, mo, d -> cal.set(y, mo, d); timePicker.show() },
                            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.CalendarMonth, "קבע שחרור",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(
                    onClick = { onChange(locks.filter { it.packageName != lock.packageName }) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Close, "הסר",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

// ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€
// Share Tab
// ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€

@Composable
private fun ShareTab(community: AdminCommunity?, onShare: (String, String) -> Unit) {
    if (community == null) return
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Outlined.Groups, null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(community.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(24.dp))

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("קוד הצטרפות",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Text(
                    "${community.code.take(4)} - ${community.code.drop(4)}",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 4.sp
                )
                Spacer(Modifier.height(8.dp))
                Text("${community.deviceCount} מכשירים מחוברים",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { onShare(community.code, community.name) },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Icon(Icons.Default.QrCode, null)
            Spacer(Modifier.width(8.dp))
            Text("הצג QR לשיתוף", fontSize = 16.sp)
        }
    }
}
