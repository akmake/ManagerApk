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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AppRegistration
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.sterni.tether.data.model.ApprovalRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ApprovalsViewModel(app: Application) : AndroidViewModel(app) {
    private val api = RetrofitClient.create(AdminApiService::class.java)
    private val _approvals = MutableStateFlow<List<ApprovalRequest>>(emptyList())
    val approvals: StateFlow<List<ApprovalRequest>> = _approvals
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    fun load(token: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val res = api.getAllApprovals(token)
                if (res.isSuccessful) _approvals.value = res.body() ?: emptyList()
            } catch (_: Exception) {}
            _loading.value = false
        }
    }

    fun resolve(
        token: String,
        id: String,
        approved: Boolean,
        grantMinutes: Int? = null,
        permanentAllow: Boolean = false
    ) {
        viewModelScope.launch {
            try {
                val status = if (approved) "approved" else "rejected"
                val body = mutableMapOf<String, Any>("status" to status)
                if (approved && grantMinutes != null) body["grantMinutes"] = grantMinutes
                if (approved) body["permanentAllow"] = permanentAllow
                api.resolveApproval(token, id, body)
                _approvals.value = _approvals.value.filter { it.id != id }
            } catch (_: Exception) {}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApprovalRequestsScreen(
    onBack: () -> Unit,
    vm: ApprovalsViewModel = viewModel()
) {
    val context = LocalContext.current
    val token = AdminSession.getToken(context) ?: ""
    val approvals by vm.approvals.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.load(token) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("בקשות אישור")
                        if (approvals.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            Badge { Text("${approvals.size}") }
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowForward, "חזרה") } }
            )
        }
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }

        if (approvals.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.height(16.dp))
                    Text("אין בקשות ממתינות", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            items(approvals) { approval ->
                ApprovalCard(
                    approval = approval,
                    onApproveTemporary = { grantMinutes -> vm.resolve(token, approval.id, true, grantMinutes, permanentAllow = false) },
                    onApprovePermanent = { vm.resolve(token, approval.id, true, 120, permanentAllow = true) },
                    onReject = { vm.resolve(token, approval.id, false) }
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ApprovalCard(
    approval: ApprovalRequest,
    onApproveTemporary: (Int?) -> Unit,
    onApprovePermanent: () -> Unit,
    onReject: () -> Unit
) {
    val isAppAccess = approval.action == "APP_ACCESS"
    val grantOptions = listOf(15, 30, 60)
    var selectedGrantMinutes by remember(approval.id) { mutableIntStateOf(30) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AppRegistration,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = approval.packageName ?: approval.action,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }

            Spacer(Modifier.height(4.dp))
            Text("מכשיר: ${approval.deviceId.take(8)}...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("פעולה: ${approval.action}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(approval.requestedAt, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (isAppAccess) {
                Spacer(Modifier.height(10.dp))
                Text("אישור זמני (בדקות)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    grantOptions.forEach { minutes ->
                        FilterChip(
                            selected = selectedGrantMinutes == minutes,
                            onClick = { selectedGrantMinutes = minutes },
                            label = { Text("$minutes", fontSize = 11.sp) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("דחה")
                }
                if (isAppAccess) {
                    Button(
                        onClick = { onApproveTemporary(selectedGrantMinutes) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("אשר זמנית")
                    }
                    Button(
                        onClick = onApprovePermanent,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("אשר תמיד")
                    }
                } else {
                    Button(
                        onClick = { onApproveTemporary(null) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("אשר")
                    }
                }
            }
        }
    }
}
