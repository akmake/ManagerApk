package com.sterni.tether.ui.screens.admin

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.SwitchDefaults
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
import com.sterni.tether.data.model.AdminCommunity
import com.sterni.tether.data.model.AdminDevice
import com.sterni.tether.data.model.CommunityPolicy
import com.sterni.tether.data.model.WebFilterMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CommunityDetailViewModel(app: Application) : AndroidViewModel(app) {
    private val api = RetrofitClient.create(AdminApiService::class.java)

    private val _community = MutableStateFlow<AdminCommunity?>(null)
    val community: StateFlow<AdminCommunity?> = _community

    private val _devices = MutableStateFlow<List<AdminDevice>>(emptyList())
    val devices: StateFlow<List<AdminDevice>> = _devices

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    fun load(token: String, communityId: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val res = api.getCommunityDetail(token, communityId)
                if (res.isSuccessful) {
                    _community.value = res.body()?.community
                    _devices.value = res.body()?.devices ?: emptyList()
                }
            } catch (_: Exception) {}
            _loading.value = false
        }
    }

    fun updatePolicy(token: String, communityId: String, policy: CommunityPolicy) {
        viewModelScope.launch {
            try {
                api.updatePolicy(token, communityId, policy)
                _community.value = _community.value?.copy(policy = policy)
            } catch (_: Exception) {}
        }
    }

    fun removeDevice(token: String, deviceId: String) {
        viewModelScope.launch {
            try {
                api.removeDevice(token, deviceId)
                _devices.value = _devices.value.filter { it.deviceId != deviceId }
            } catch (_: Exception) {}
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityDetailScreen(
    communityId: String,
    onShareClick: (String, String) -> Unit,
    onBack: () -> Unit,
    vm: CommunityDetailViewModel = viewModel()
) {
    val context = LocalContext.current
    val token = AdminSession.getToken(context) ?: ""
    val community by vm.community.collectAsStateWithLifecycle()
    val devices by vm.devices.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("מכשירים", "מדיניות", "שיתוף")

    LaunchedEffect(Unit) { vm.load(token, communityId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(community?.name ?: "קהילה") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowForward, "חזרה") } }
            )
        }
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { i, title ->
                    Tab(selected = selectedTab == i, onClick = { selectedTab = i }, text = { Text(title) })
                }
            }

            when (selectedTab) {
                0 -> DevicesTab(
                    devices = devices,
                    onRemove = { vm.removeDevice(token, it) },
                    onToggleUninstall = { deviceId, allow -> vm.toggleAllowUninstall(token, deviceId, allow) }
                )
                1 -> PolicyTab(
                    policy = community?.policy,
                    onSave = { policy -> community?.let { vm.updatePolicy(token, it.id, policy) } }
                )
                2 -> ShareTab(community = community, onShare = { code, name -> onShareClick(code, name) })
            }
        }
    }
}

@Composable
private fun DevicesTab(
    devices: List<AdminDevice>,
    onRemove: (String) -> Unit,
    onToggleUninstall: (String, Boolean) -> Unit
) {
    var confirmDevice by remember { mutableStateOf<AdminDevice?>(null) }

    if (devices.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("אין מכשירים עדיין", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { Spacer(Modifier.height(8.dp)) }
        items(devices) { device ->
            DeviceCard(
                device = device,
                onRemove = { confirmDevice = device },
                onToggleUninstall = { allow -> onToggleUninstall(device.deviceId, allow) }
            )
        }
        item { Spacer(Modifier.height(80.dp)) }
    }

    confirmDevice?.let { device ->
        AlertDialog(
            onDismissRequest = { confirmDevice = null },
            title = { Text("הסר מכשיר") },
            text = { Text("להסיר את ${device.deviceModel}? המדיניות תוסר ממנו.") },
            confirmButton = {
                TextButton(onClick = { onRemove(device.deviceId); confirmDevice = null }) {
                    Text("הסר", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDevice = null }) { Text("ביטול") }
            }
        )
    }
}

@Composable
private fun DeviceCard(device: AdminDevice, onRemove: () -> Unit, onToggleUninstall: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(device.deviceModel, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (device.isDeviceOwner) {
                            Surface(shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.secondaryContainer) {
                                Text("Device Owner",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                        Text("נראה: ${device.lastSeen}", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.PersonRemove, "הסר", tint = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("אפשר הסרת אפליקציה", fontSize = 13.sp, modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Switch(
                    checked = device.allowUninstall,
                    onCheckedChange = onToggleUninstall,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.error,
                        checkedTrackColor = MaterialTheme.colorScheme.errorContainer
                    )
                )
            }
        }
    }
}

@Composable
private fun PolicyTab(policy: CommunityPolicy?, onSave: (CommunityPolicy) -> Unit) {
    if (policy == null) return
    var edited by remember(policy) { mutableStateOf(policy) }
    var saved by remember { mutableStateOf(false) }
    var newDomain by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                PolicySection("חסימות") {
                    PolicyToggle("חסום התקנת אפליקציות", edited.blockInstallApps) { edited = edited.copy(blockInstallApps = it) }
                    PolicyToggle("הסתר Google Play", edited.hideGooglePlay) { edited = edited.copy(hideGooglePlay = it) }
                    PolicyToggle("חסום מצב בטוח", edited.blockSafeBoot) { edited = edited.copy(blockSafeBoot = it) }
                    PolicyToggle("חסום איפוס יצרן", edited.blockFactoryReset) { edited = edited.copy(blockFactoryReset = it) }
                    PolicyToggle("חסום העברת קבצים USB", edited.blockUsbTransfer) { edited = edited.copy(blockUsbTransfer = it) }
                    PolicyToggle("אפשר לוגים", edited.logsEnabled) { edited = edited.copy(logsEnabled = it) }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text("סינון דפדפן", fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        WebFilterMode.values().forEach { mode ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                RadioButton(
                                    selected = edited.webFilterMode == mode,
                                    onClick = { edited = edited.copy(webFilterMode = mode) }
                                )
                                Text(
                                    text = when (mode) {
                                        WebFilterMode.NONE -> "ללא סינון"
                                        WebFilterMode.BLACKLIST -> "רשימה שחורה (חסום דומיינים)"
                                        WebFilterMode.WHITELIST -> "רשימה לבנה (אפשר רק דומיינים)"
                                    },
                                    fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (edited.webFilterMode == WebFilterMode.BLACKLIST) {
                item {
                    DomainListSection(
                        title = "דומיינים חסומים",
                        domains = edited.blockedDomains,
                        newDomain = newDomain,
                        onNewDomainChange = { newDomain = it },
                        onAdd = {
                            val d = newDomain.trim().lowercase()
                            if (d.isNotEmpty() && d !in edited.blockedDomains) {
                                edited = edited.copy(blockedDomains = edited.blockedDomains + d)
                                newDomain = ""
                            }
                        },
                        onRemove = { d -> edited = edited.copy(blockedDomains = edited.blockedDomains - d) }
                    )
                }
            }

            if (edited.webFilterMode == WebFilterMode.WHITELIST) {
                item {
                    DomainListSection(
                        title = "דומיינים מותרים",
                        domains = edited.allowedDomains,
                        newDomain = newDomain,
                        onNewDomainChange = { newDomain = it },
                        onAdd = {
                            val d = newDomain.trim().lowercase()
                            if (d.isNotEmpty() && d !in edited.allowedDomains) {
                                edited = edited.copy(allowedDomains = edited.allowedDomains + d)
                                newDomain = ""
                            }
                        },
                        onRemove = { d -> edited = edited.copy(allowedDomains = edited.allowedDomains - d) }
                    )
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }

        if (saved) {
            Text("נשמר!", color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(8.dp))
        }

        Button(
            onClick = { onSave(edited); saved = true },
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).height(52.dp)
        ) { Text("שמור שינויים", fontSize = 16.sp) }
    }
}

@Composable
private fun DomainListSection(
    title: String,
    domains: List<String>,
    newDomain: String,
    onNewDomainChange: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit
) {
    Spacer(Modifier.height(8.dp))
    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 4.dp))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newDomain,
                    onValueChange = onNewDomainChange,
                    label = { Text("דומיין (לדוגמה: youtube.com)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onAdd) {
                    Icon(Icons.Default.Add, "הוסף", tint = MaterialTheme.colorScheme.primary)
                }
            }
            if (domains.isEmpty()) {
                Text("אין דומיינים ברשימה", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp))
            } else {
                domains.forEach { domain ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(domain, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onRemove(domain) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, "הסר", tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareTab(community: AdminCommunity?, onShare: (String, String) -> Unit) {
    if (community == null) return
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("קוד הצטרפות", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Spacer(Modifier.height(24.dp))

        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.padding(8.dp)
        ) {
            Text(
                text = "${community.code.take(4)}-${community.code.drop(4)}",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp)
            )
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
