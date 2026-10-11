package com.sterni.tether.ui.screens.admin

import com.sterni.tether.ui.components.TetherScaffold as Scaffold
import com.sterni.tether.ui.components.TetherTopAppBar as TopAppBar
import com.sterni.tether.ui.components.TetherCard as Card
import com.sterni.tether.ui.components.TetherButton as Button
import com.sterni.tether.ui.components.TetherTextField as OutlinedTextField

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
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

data class ManagerDirectoryState(
    val admins: List<AdminMember> = emptyList(),
    val communities: List<AdminCommunity> = emptyList(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null
)

class ManageAdminsViewModel(app: Application) : AndroidViewModel(app) {
    private val api = RetrofitClient.create(AdminApiService::class.java)
    private val _state = MutableStateFlow(ManagerDirectoryState())
    val state: StateFlow<ManagerDirectoryState> = _state

    private suspend fun refresh(token: String) {
        val admins = api.getAdmins(token)
        val communities = api.getCommunities(token)
        check(admins.isSuccessful && communities.isSuccessful) { "לא ניתן לטעון את המנהלים והקהילות" }
        _state.value = _state.value.copy(admins = admins.body().orEmpty(), communities = communities.body().orEmpty())
    }

    fun load(token: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try { refresh(token) }
            catch (_: Exception) { _state.value = _state.value.copy(error = "לא ניתן לטעון. בדוק חיבור והרשאות ונסה שוב.") }
            finally { _state.value = _state.value.copy(loading = false) }
        }
    }

    fun invite(token: String, request: InviteAdminRequest, onSuccess: () -> Unit) =
        mutate(token, onSuccess) {
            val response = api.inviteAdmin(token, request)
            if (!response.isSuccessful) throw IllegalStateException(when (response.code()) {
                409 -> "האימייל כבר קיים או ששיוך הקהילה השתנה. יש לרענן ולנסות שוב."
                403 -> "רק מנהל־על יכול להוסיף מנהלי קהילות"
                404 -> "הקהילה לא נמצאה"
                else -> "יצירת המנהל נכשלה"
            })
        }

    fun assign(token: String, communityId: String, adminId: String, onSuccess: () -> Unit) =
        mutate(token, onSuccess) {
            check(api.assignCommunityManager(token, communityId, mapOf("adminId" to adminId)).isSuccessful) {
                "שיוך המנהל נכשל. לא בוצע שינוי בממשק."
            }
        }

    fun remove(token: String, id: String, onSuccess: () -> Unit) = mutate(token, onSuccess) {
        check(api.removeAdmin(token, id).isSuccessful) { "הסרת המנהל נכשלה" }
    }

    private fun mutate(token: String, onSuccess: () -> Unit, action: suspend () -> Unit) {
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true, error = null)
            try {
                action()
                onSuccess()
                try { refresh(token) }
                catch (_: Exception) { _state.value = _state.value.copy(error = "השינוי נשמר, אך רענון הרשימה נכשל. יש לרענן.") }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message ?: "הפעולה נכשלה")
            } finally { _state.value = _state.value.copy(saving = false) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageAdminsScreen(onBack: () -> Unit, vm: ManageAdminsViewModel = viewModel()) {
    val context = LocalContext.current
    val token = AdminSession.getToken(context).orEmpty()
    val state by vm.state.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var communityId by remember { mutableStateOf<String?>(null) }
    var assigning by remember { mutableStateOf<AdminCommunity?>(null) }
    var removing by remember { mutableStateOf<AdminMember?>(null) }

    LaunchedEffect(token) { if (AdminSession.isSuperAdmin(context)) vm.load(token) }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { if (!state.saving) { showCreate = false; password = "" } },
            title = { Text("חשבון למנהל קהילה") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("האימייל הוא שם המשתמש. המנהל יקבל שליטה בכל מכשירי הקהילה שתבחר.")
                    OutlinedTextField(name, { name = it }, label = { Text("שם המנהל") }, enabled = !state.saving, singleLine = true)
                    OutlinedTextField(email, { email = it }, label = { Text("אימייל להתחברות") }, enabled = !state.saving, singleLine = true)
                    OutlinedTextField(password, { password = it }, label = { Text("סיסמה — לפחות 8 תווים") },
                        visualTransformation = PasswordVisualTransformation(), enabled = !state.saving, singleLine = true)
                    Text("קהילה לניהול", fontWeight = FontWeight.SemiBold)
                    LazyColumn(Modifier.heightIn(max = 160.dp)) {
                        items(state.communities) { community ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(communityId == community.id, { communityId = community.id }, enabled = !state.saving)
                                Text(community.name)
                            }
                        }
                    }
                    Text("השיוך מחליף את מנהל הקהילה הקודם. המכשירים וההגדרות נשמרים.")
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = !state.saving && name.isNotBlank() && password.length >= 8 && communityId != null &&
                    android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches(), onClick = {
                    vm.invite(token, InviteAdminRequest(name.trim(), email.trim(), password, communityId)) {
                        showCreate = false; password = ""; name = ""; email = ""; communityId = null
                    }
                }) { Text(if (state.saving) "יוצר..." else "צור חשבון ושייך קהילה") }
            },
            dismissButton = { TextButton(enabled = !state.saving, onClick = { showCreate = false; password = "" }) { Text("ביטול") } }
        )
    }

    assigning?.let { community ->
        AlertDialog(onDismissRequest = { if (!state.saving) assigning = null },
            title = { Text("מנהל לקהילה ${community.name}") },
            text = {
                Column {
                    Text("בחר מנהל. השליטה בכל מכשירי הקהילה תועבר לחשבון שלו.")
                    LazyColumn(Modifier.heightIn(max = 260.dp)) {
                        items(state.admins.filter { it.active }) { admin ->
                            TextButton(enabled = !state.saving, onClick = {
                                vm.assign(token, community.id, admin.id) { assigning = null }
                            }) { Text("${admin.name} · ${admin.email}") }
                        }
                    }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }, confirmButton = {},
            dismissButton = { TextButton(enabled = !state.saving, onClick = { assigning = null }) { Text("סגור") } })
    }

    removing?.let { admin ->
        AlertDialog(onDismissRequest = { if (!state.saving) removing = null }, title = { Text("ביטול גישת מנהל") },
            text = { Text("לבטל את הגישה של ${admin.name}? הקהילות יישארו זמינות למנהל־העל לשיוך מחדש.") },
            confirmButton = { TextButton(enabled = !state.saving, onClick = {
                vm.remove(token, admin.id) { removing = null }
            }) { Text("בטל גישה") } },
            dismissButton = { TextButton(enabled = !state.saving, onClick = { removing = null }) { Text("ביטול") } })
    }

    Scaffold(topBar = { TopAppBar(title = { Text("מנהלי קהילות") }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowForward, "חזרה") }
    }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!AdminSession.isSuperAdmin(context)) {
                item { Text("ניהול חשבונות ושיוך קהילות זמין למנהל־על בלבד.") }
            } else {
                item {
                    Text("צור חשבון אישי למנהל ושייך אליו קהילה קיימת. הוא יוכל להתחבר לאזור המנהל ולנהל את מכשירי הקהילה שלו.")
                    Button(enabled = !state.loading && !state.saving && state.communities.isNotEmpty(), onClick = { showCreate = true }) {
                        Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(8.dp)); Text("צור מנהל קהילה")
                    }
                }
                if (state.loading || state.saving) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                state.error?.let { message -> item {
                    Text(message, color = MaterialTheme.colorScheme.error)
                    TextButton(enabled = !state.saving, onClick = { vm.load(token) }) { Text("רענן") }
                } }
                item { Text("שיוך קהילות", fontWeight = FontWeight.SemiBold) }
                if (!state.loading && state.communities.isEmpty()) item { Text("צור קהילה באזור הקהילות, ואז שייך לה מנהל.") }
                items(state.communities) { community ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(community.name, fontWeight = FontWeight.SemiBold)
                            Text("${community.deviceCount} מכשירים · מנהל: ${state.admins.firstOrNull { it.id == community.adminId }?.name ?: "לא זמין"}")
                            TextButton(enabled = !state.saving, onClick = { assigning = community }) { Text("בחר מנהל") }
                        }
                    }
                }
                item { Text("חשבונות מנהלים", fontWeight = FontWeight.SemiBold) }
                items(state.admins) { admin ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(admin.name, fontWeight = FontWeight.SemiBold); Text(admin.email)
                                Text(if (!admin.active) "הגישה בוטלה" else if (admin.role == "superadmin") "מנהל־על" else "מנהל קהילה")
                            }
                            if (admin.active && admin.role != "superadmin") IconButton(enabled = !state.saving, onClick = { removing = admin }) {
                                Icon(Icons.Default.PersonRemove, "בטל גישה", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}
