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
import com.sterni.tether.data.model.AdminMember
import com.sterni.tether.data.model.InviteAdminRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ManageAdminsViewModel(app: Application) : AndroidViewModel(app) {
    private val api = RetrofitClient.create(AdminApiService::class.java)
    private val _admins = MutableStateFlow<List<AdminMember>>(emptyList())
    val admins: StateFlow<List<AdminMember>> = _admins
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    fun load(token: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val res = api.getAdmins(token)
                if (res.isSuccessful) _admins.value = res.body() ?: emptyList()
            } catch (_: Exception) {}
            _loading.value = false
        }
    }

    fun invite(token: String, name: String, email: String, password: String) {
        viewModelScope.launch {
            try {
                api.inviteAdmin(token, InviteAdminRequest(name, email, password))
                load(token)
            } catch (_: Exception) {}
        }
    }

    fun remove(token: String, id: String) {
        viewModelScope.launch {
            try {
                api.removeAdmin(token, id)
                _admins.value = _admins.value.filter { it.id != id }
            } catch (_: Exception) {}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageAdminsScreen(
    onBack: () -> Unit,
    vm: ManageAdminsViewModel = viewModel()
) {
    val context = LocalContext.current
    val token = AdminSession.getToken(context) ?: ""
    val admins by vm.admins.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    var showInviteDialog by remember { mutableStateOf(false) }
    var inviteName by remember { mutableStateOf("") }
    var inviteEmail by remember { mutableStateOf("") }
    var invitePassword by remember { mutableStateOf("") }
    var confirmRemove by remember { mutableStateOf<AdminMember?>(null) }

    LaunchedEffect(Unit) { vm.load(token) }

    if (showInviteDialog) {
        AlertDialog(
            onDismissRequest = { showInviteDialog = false },
            title = { Text("הוסף מנהל") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = inviteName,
                        onValueChange = { inviteName = it },
                        label = { Text("שם מלא") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = inviteEmail,
                        onValueChange = { inviteEmail = it },
                        label = { Text("אימייל") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = invitePassword,
                        onValueChange = { invitePassword = it },
                        label = { Text("סיסמה") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.invite(token, inviteName, inviteEmail, invitePassword)
                        inviteName = ""; inviteEmail = ""; invitePassword = ""
                        showInviteDialog = false
                    },
                    enabled = inviteName.isNotBlank() && inviteEmail.isNotBlank() && invitePassword.isNotBlank()
                ) { Text("הוסף") }
            },
            dismissButton = { TextButton(onClick = { showInviteDialog = false }) { Text("ביטול") } }
        )
    }

    confirmRemove?.let { admin ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("הסר מנהל") },
            text = { Text("להסיר את ${admin.name} מהמנהלים?") },
            confirmButton = {
                TextButton(onClick = { vm.remove(token, admin.id); confirmRemove = null }) {
                    Text("הסר", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("ביטול") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ניהול מנהלים") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowForward, "חזרה") } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showInviteDialog = true }) {
                Icon(Icons.Default.PersonAdd, "הוסף מנהל")
            }
        }
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            items(admins) { admin ->
                AdminMemberCard(admin = admin, onRemove = { confirmRemove = admin })
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun AdminMemberCard(admin: AdminMember, onRemove: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(admin.name.first().toString(), fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary, fontSize = 18.sp)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(admin.name, fontWeight = FontWeight.SemiBold)
                Text(admin.email, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (admin.role == "superadmin") "מנהל-על" else "מנהל",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.PersonRemove, "הסר", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
