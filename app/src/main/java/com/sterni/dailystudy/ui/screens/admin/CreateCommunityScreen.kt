package com.sterni.dailystudy.ui.screens.admin

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
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
import com.sterni.dailystudy.admin.AdminSession
import com.sterni.dailystudy.data.api.AdminApiService
import com.sterni.dailystudy.data.api.RetrofitClient
import com.sterni.dailystudy.data.model.AdminCommunity
import com.sterni.dailystudy.data.model.BlockedActionBehavior
import com.sterni.dailystudy.data.model.CommunityPolicy
import com.sterni.dailystudy.data.model.CreateCommunityRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class CreateState {
    object Idle : CreateState()
    object Loading : CreateState()
    data class Success(val community: AdminCommunity) : CreateState()
    data class Error(val message: String) : CreateState()
}

class CreateCommunityViewModel(app: Application) : AndroidViewModel(app) {
    private val api = RetrofitClient.create(AdminApiService::class.java)
    private val _state = MutableStateFlow<CreateState>(CreateState.Idle)
    val state: StateFlow<CreateState> = _state

    fun create(token: String, name: String, policy: CommunityPolicy) {
        viewModelScope.launch {
            _state.value = CreateState.Loading
            try {
                val res = api.createCommunity(token, CreateCommunityRequest(name, policy))
                if (res.isSuccessful) _state.value = CreateState.Success(res.body()!!)
                else _state.value = CreateState.Error("שגיאה ביצירת הקהילה")
            } catch (e: Exception) {
                _state.value = CreateState.Error("שגיאת רשת")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCommunityScreen(
    onCreated: (AdminCommunity) -> Unit,
    onBack: () -> Unit,
    vm: CreateCommunityViewModel = viewModel()
) {
    val context = LocalContext.current
    val token = AdminSession.getToken(context) ?: ""
    val state by vm.state.collectAsStateWithLifecycle()

    var step by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var policy by remember { mutableStateOf(CommunityPolicy()) }

    LaunchedEffect(state) {
        if (state is CreateState.Success) onCreated((state as CreateState.Success).community)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(listOf("פרטי קהילה", "הגדרות מדיניות", "סיום")[step]) },
                navigationIcon = {
                    IconButton(onClick = { if (step > 0) step-- else onBack() }) {
                        Icon(Icons.Default.ArrowForward, "חזרה")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
        ) {
            // Step indicator
            LinearProgressIndicator(
                progress = { (step + 1) / 3f },
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
            )

            when (step) {
                0 -> StepName(name = name, onChange = { name = it }, onNext = { if (name.isNotBlank()) step = 1 })
                1 -> StepPolicy(policy = policy, onChange = { policy = it }, onNext = { step = 2 })
                2 -> StepConfirm(
                    name = name,
                    policy = policy,
                    state = state,
                    onCreate = { vm.create(token, name, policy) }
                )
            }
        }
    }
}

@Composable
private fun StepName(name: String, onChange: (String) -> Unit, onNext: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.height(16.dp))
        Text("שם הקהילה", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        Text("הכנס שם מזהה לקהילה", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = name,
            onValueChange = onChange,
            label = { Text("שם קהילה (לדוגמה: משפחת כהן)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = name.isNotBlank()
        ) { Text("הבא", fontSize = 16.sp) }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StepPolicy(policy: CommunityPolicy, onChange: (CommunityPolicy) -> Unit, onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(8.dp))

        PolicySection("חסימות") {
            PolicyToggle("חסום התקנת אפליקציות", policy.blockInstallApps) {
                onChange(policy.copy(blockInstallApps = it))
            }
            PolicyToggle("הסתר Google Play", policy.hideGooglePlay) {
                onChange(policy.copy(hideGooglePlay = it))
            }
            PolicyToggle("חסום מצב בטוח (Safe Mode)", policy.blockSafeBoot) {
                onChange(policy.copy(blockSafeBoot = it))
            }
            PolicyToggle("חסום איפוס יצרן", policy.blockFactoryReset) {
                onChange(policy.copy(blockFactoryReset = it))
            }
            PolicyToggle("חסום העברת קבצים USB", policy.blockUsbTransfer) {
                onChange(policy.copy(blockUsbTransfer = it))
            }
        }

        PolicySection("כשפעולה חסומה") {
            Column {
                BlockedActionBehavior.values().forEach { behavior ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = policy.blockedActionBehavior == behavior,
                            onClick = { onChange(policy.copy(blockedActionBehavior = behavior)) }
                        )
                        Text(
                            text = when (behavior) {
                                BlockedActionBehavior.SILENT -> "חסימה שקטה (ללא הודעה)"
                                BlockedActionBehavior.SHOW_MESSAGE -> "הצג הודעת חסימה"
                                BlockedActionBehavior.REQUEST_APPROVAL -> "אפשר בקשת אישור"
                            },
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
        }

        PolicySection("לוגים") {
            PolicyToggle("שמור לוג פעולות חסומות", policy.logsEnabled) {
                onChange(policy.copy(logsEnabled = it))
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("הבא", fontSize = 16.sp)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StepConfirm(
    name: String,
    policy: CommunityPolicy,
    state: CreateState,
    onCreate: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.height(16.dp))
        Text("סיכום", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                SummaryRow("שם", name)
                SummaryRow("חסום התקנות", if (policy.blockInstallApps) "כן" else "לא")
                SummaryRow("מסתיר Google Play", if (policy.hideGooglePlay) "כן" else "לא")
                SummaryRow("חסום Safe Mode", if (policy.blockSafeBoot) "כן" else "לא")
                SummaryRow("חסום איפוס יצרן", if (policy.blockFactoryReset) "כן" else "לא")
                SummaryRow(
                    "כשפעולה חסומה", when (policy.blockedActionBehavior) {
                        BlockedActionBehavior.SILENT -> "שקט"
                        BlockedActionBehavior.SHOW_MESSAGE -> "הודעה"
                        BlockedActionBehavior.REQUEST_APPROVAL -> "בקשת אישור"
                    }
                )
            }
        }

        if (state is CreateState.Error) {
            Spacer(Modifier.height(12.dp))
            Text((state as CreateState.Error).message, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onCreate,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = state !is CreateState.Loading
        ) {
            if (state is CreateState.Loading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            } else {
                Text("צור קהילה", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun PolicySection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp), content = content)
    }
}

@Composable
fun PolicyToggle(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Text(value, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    }
}
