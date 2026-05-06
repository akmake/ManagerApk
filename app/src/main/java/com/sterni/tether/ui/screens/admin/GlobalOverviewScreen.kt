package com.sterni.tether.ui.screens.admin

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sterni.tether.admin.AdminSession
import com.sterni.tether.data.api.AdminApiService
import com.sterni.tether.data.api.RetrofitClient
import com.sterni.tether.data.model.GlobalCommunity
import com.sterni.tether.data.model.GlobalDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€
// ViewModel
// ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€

class GlobalOverviewViewModel(app: Application) : AndroidViewModel(app) {
    private val api = RetrofitClient.create(AdminApiService::class.java)

    private val _devices      = MutableStateFlow<List<GlobalDevice>>(emptyList())
    val devices: StateFlow<List<GlobalDevice>> = _devices

    private val _communities  = MutableStateFlow<List<GlobalCommunity>>(emptyList())
    val communities: StateFlow<List<GlobalCommunity>> = _communities

    private val _loading      = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    private val _refreshing   = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    private val _snackbar     = MutableStateFlow<String?>(null)
    val snackbar: StateFlow<String?> = _snackbar

    fun load(token: String, isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isRefresh) _refreshing.value = true else _loading.value = true
            try {
                val dRes = api.getAllDevicesGlobal(token)
                if (dRes.isSuccessful) _devices.value = dRes.body() ?: emptyList()
                val cRes = api.getAllCommunitiesGlobal(token)
                if (cRes.isSuccessful) _communities.value = cRes.body() ?: emptyList()
            } catch (_: Exception) { _snackbar.value = "שגיאת רשת" }
            _loading.value = false
            _refreshing.value = false
        }
    }

    fun deleteDevice(token: String, id: String) {
        viewModelScope.launch {
            try {
                val res = api.deleteDeviceGlobal(token, id)
                if (res.isSuccessful) {
                    _devices.value = _devices.value.filter { it.id != id }
                    _snackbar.value = "מכשיר נמחק"
                } else _snackbar.value = "שגיאה במחיקה"
            } catch (_: Exception) { _snackbar.value = "שגיאת רשת" }
        }
    }

    fun renameDevice(token: String, id: String, nickname: String?) {
        viewModelScope.launch {
            try {
                val res = api.renameDevice(token, id, mapOf("nickname" to nickname))
                if (res.isSuccessful) {
                    _devices.value = _devices.value.map { if (it.id == id) it.copy(deviceNickname = nickname) else it }
                    _snackbar.value = "שם עודכן"
                }
            } catch (_: Exception) { _snackbar.value = "שגיאת רשת" }
        }
    }

    fun deleteCommunity(token: String, id: String) {
        viewModelScope.launch {
            try {
                val res = api.deleteCommunityGlobal(token, id)
                if (res.isSuccessful) {
                    _communities.value = _communities.value.filter { it.id != id }
                    _snackbar.value = "קהילה נמחקה"
                } else _snackbar.value = "שגיאה במחיקה"
            } catch (_: Exception) { _snackbar.value = "שגיאת רשת" }
        }
    }

    fun renameCommunity(token: String, id: String, name: String) {
        viewModelScope.launch {
            try {
                val res = api.renameCommunityGlobal(token, id, mapOf("name" to name))
                if (res.isSuccessful) {
                    _communities.value = _communities.value.map { if (it.id == id) it.copy(name = name) else it }
                    _snackbar.value = "שם עודכן"
                }
            } catch (_: Exception) { _snackbar.value = "שגיאת רשת" }
        }
    }

    fun clearSnackbar() { _snackbar.value = null }
}

// ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€
// Screen
// ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalOverviewScreen(
    onBack: () -> Unit,
    vm: GlobalOverviewViewModel = viewModel()
) {
    val context       = LocalContext.current
    val token         = AdminSession.getToken(context) ?: ""
    val devices       by vm.devices.collectAsStateWithLifecycle()
    val communities   by vm.communities.collectAsStateWithLifecycle()
    val loading       by vm.loading.collectAsStateWithLifecycle()
    val refreshing    by vm.refreshing.collectAsStateWithLifecycle()
    val snackbarMsg   by vm.snackbar.collectAsStateWithLifecycle()
    val snackbarHost  = remember { SnackbarHostState() }

    var selectedTab   by remember { mutableIntStateOf(0) }
    var searchQuery   by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.load(token) }
    LaunchedEffect(snackbarMsg) {
        snackbarMsg?.let { snackbarHost.showSnackbar(it); vm.clearSnackbar() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("סקירה גלובלית", fontWeight = FontWeight.Bold)
                        Text(
                            "${devices.size} מכשירים · ${communities.size} קהילות",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "חזרה") }
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
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("חפש מכשיר, קהילה, מודל...") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                singleLine = true,
                leadingIcon  = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty())
                        IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, null) }
                }
            )

            // Tabs
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick  = { selectedTab = 0 },
                    text     = { Text("מכשירים (${devices.size})") },
                    icon     = { Icon(Icons.Outlined.PhoneAndroid, null, Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick  = { selectedTab = 1 },
                    text     = { Text("קהילות (${communities.size})") },
                    icon     = { Icon(Icons.Outlined.Groups, null, Modifier.size(18.dp)) }
                )
            }

            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh    = { vm.load(token, isRefresh = true) },
                modifier     = Modifier.fillMaxSize()
            ) {
                when (selectedTab) {
                    0 -> DevicesTab(
                        devices     = devices,
                        query       = searchQuery,
                        onDelete    = { vm.deleteDevice(token, it) },
                        onRename    = { id, nick -> vm.renameDevice(token, id, nick) }
                    )
                    1 -> CommunitiesTab(
                        communities = communities,
                        query       = searchQuery,
                        onDelete    = { vm.deleteCommunity(token, it) },
                        onRename    = { id, name -> vm.renameCommunity(token, id, name) }
                    )
                }
            }
        }
    }
}

// ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€
// Devices tab
// ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€

@Composable
private fun DevicesTab(
    devices:  List<GlobalDevice>,
    query:    String,
    onDelete: (String) -> Unit,
    onRename: (String, String?) -> Unit
) {
    val filtered = remember(devices, query) {
        if (query.isBlank()) devices
        else devices.filter {
            it.deviceModel.contains(query, ignoreCase = true) ||
            it.deviceNickname?.contains(query, ignoreCase = true) == true ||
            it.communityName.contains(query, ignoreCase = true) ||
            it.deviceId.contains(query, ignoreCase = true)
        }
    }

    if (filtered.isEmpty()) {
        EmptyState("אין מכשירים", Icons.Outlined.PhoneAndroid)
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "${filtered.size} מכשירים${if (query.isNotBlank()) " (מסונן)" else ""}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
        items(filtered, key = { it.id }) { device ->
            DeviceCard(device = device, onDelete = { onDelete(device.id) }, onRename = { onRename(device.id, it) })
        }
    }
}

@Composable
private fun DeviceCard(
    device:   GlobalDevice,
    onDelete: () -> Unit,
    onRename: (String?) -> Unit
) {
    var confirmDelete  by remember { mutableStateOf(false) }
    var editingName    by remember { mutableStateOf(false) }
    var nameInput      by remember(device.deviceNickname) { mutableStateOf(device.deviceNickname ?: "") }
    val sdf            = remember { SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon  = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("מחק מכשיר?") },
            text  = { Text("\"${device.deviceNickname ?: device.deviceModel}\" ימחק לצמיתות.") },
            confirmButton = {
                Button(onClick = { onDelete(); confirmDelete = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("מחק") }
            },
            dismissButton = { OutlinedButton(onClick = { confirmDelete = false }) { Text("ביטול") } }
        )
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Header row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.PhoneAndroid, null,
                    modifier = Modifier.size(18.dp),
                    tint = if (device.active) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    if (editingName) {
                        OutlinedTextField(
                            value         = nameInput,
                            onValueChange = { nameInput = it },
                            singleLine    = true,
                            placeholder   = { Text("שם המכשיר") },
                            modifier      = Modifier.fillMaxWidth(),
                            trailingIcon  = {
                                Row {
                                    IconButton(onClick = {
                                        onRename(nameInput.trim().ifEmpty { null })
                                        editingName = false
                                    }) { Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) }
                                    IconButton(onClick = { editingName = false }) {
                                        Icon(Icons.Default.Close, null)
                                    }
                                }
                            }
                        )
                    } else {
                        Text(
                            device.deviceNickname ?: device.deviceModel,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        if (device.deviceNickname != null) {
                            Text(device.deviceModel, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (!editingName) {
                    IconButton(onClick = { editingName = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { confirmDelete = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.DeleteOutline, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // Meta row
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Group, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(device.communityName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (device.communityCode.isNotEmpty()) {
                    Text("·", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(device.communityCode, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            }

            // Last seen
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AccessTime, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    try { sdf.format(SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(device.lastSeen.take(19))!!) }
                    catch (_: Exception) { device.lastSeen.take(16) },
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                // Status chips
                StatusChipSmall(if (device.active) "פעיל" else "לא פעיל", device.active)
                if (device.isOnline) StatusChipSmall("מחובר", true)
                if (device.isDeviceOwner) StatusChipSmall("DO", true)
                if (!device.active) StatusChipSmall("מושבת", false)
            }

            // Protection row
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                StatusChipSmall("נגישות", device.protectionStatus.accessibilityEnabled)
                StatusChipSmall("מנהל", device.protectionStatus.isDeviceAdmin)
                StatusChipSmall("VPN", device.protectionStatus.vpnActive)
            }
        }
    }
}

// ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€
// Communities tab
// ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€

@Composable
private fun CommunitiesTab(
    communities: List<GlobalCommunity>,
    query:       String,
    onDelete:    (String) -> Unit,
    onRename:    (String, String) -> Unit
) {
    val filtered = remember(communities, query) {
        if (query.isBlank()) communities
        else communities.filter {
            it.name.contains(query, ignoreCase = true) ||
            it.code.contains(query, ignoreCase = true) ||
            it.adminName.contains(query, ignoreCase = true)
        }
    }

    if (filtered.isEmpty()) {
        EmptyState("אין קהילות", Icons.Outlined.Groups)
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "${filtered.size} קהילות${if (query.isNotBlank()) " (מסונן)" else ""}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
        items(filtered, key = { it.id }) { community ->
            CommunityCard(
                community = community,
                onDelete  = { onDelete(community.id) },
                onRename  = { onRename(community.id, it) }
            )
        }
    }
}

@Composable
private fun CommunityCard(
    community: GlobalCommunity,
    onDelete:  () -> Unit,
    onRename:  (String) -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var editingName   by remember { mutableStateOf(false) }
    var nameInput     by remember(community.name) { mutableStateOf(community.name) }
    val sdf           = remember { SimpleDateFormat("dd/MM/yy", Locale.getDefault()) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon  = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("מחק קהילה?") },
            text  = { Text("\"${community.name}\" ומחק לצמיתות. כל המכשירים המשויכים ישארו ללא קהילה.") },
            confirmButton = {
                Button(onClick = { onDelete(); confirmDelete = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("מחק") }
            },
            dismissButton = { OutlinedButton(onClick = { confirmDelete = false }) { Text("ביטול") } }
        )
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Groups, null,
                    modifier = Modifier.size(18.dp),
                    tint = if (community.active) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    if (editingName) {
                        OutlinedTextField(
                            value         = nameInput,
                            onValueChange = { nameInput = it },
                            singleLine    = true,
                            placeholder   = { Text("שם הקהילה") },
                            modifier      = Modifier.fillMaxWidth(),
                            trailingIcon  = {
                                Row {
                                    IconButton(onClick = {
                                        if (nameInput.isNotBlank()) { onRename(nameInput.trim()); editingName = false }
                                    }) { Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) }
                                    IconButton(onClick = { editingName = false }) { Icon(Icons.Default.Close, null) }
                                }
                            }
                        )
                    } else {
                        Text(community.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            community.code,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
                if (!editingName) {
                    IconButton(onClick = { editingName = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { confirmDelete = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.DeleteOutline, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // Admin + device count
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AdminPanelSettings, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(community.adminName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.PhoneAndroid, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${community.activeDeviceCount}/${community.deviceCount} פעילים",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Status + date
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChipSmall(if (community.active) "פעילה" else "מושבתת", community.active)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Outlined.CalendarToday, null, Modifier.size(11.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(3.dp))
                Text(
                    try { sdf.format(SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(community.createdAt.take(19))!!) }
                    catch (_: Exception) { community.createdAt.take(10) },
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€
// Shared helpers
// ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€

@Composable
private fun StatusChipSmall(label: String, active: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = if (active) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            label,
            fontSize = 10.sp,
            color = if (active) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun EmptyState(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
