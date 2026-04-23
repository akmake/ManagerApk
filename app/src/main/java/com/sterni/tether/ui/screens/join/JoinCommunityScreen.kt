package com.sterni.tether.ui.screens.join

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun JoinCommunityScreen(
    onCommunityFound: (code: String, communityName: String) -> Unit,
    onEnterAdmin: () -> Unit = {},
    vm: JoinCommunityViewModel = viewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showQrScanner by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var logoTapCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(state) {
        if (state is JoinState.CommunityFound) {
            val s = state as JoinState.CommunityFound
            onCommunityFound(s.code, s.name)
            vm.reset()
        }
    }

    if (showQrScanner) {
        QrScannerScreen(
            onCodeScanned = { code ->
                showQrScanner = false
                vm.verifyCode(code)
            },
            onBack = { showQrScanner = false }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))

        Text(
            text = "Tether",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.combinedClickable(
                onClick = {
                    logoTapCount++
                    if (logoTapCount >= 7) {
                        logoTapCount = 0
                        onEnterAdmin()
                    }
                },
                onLongClick = {}
            )
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "הצטרף לקהילה שלך",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(40.dp))

        // Tabs
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                icon = { Icon(Icons.Default.Edit, null) },
                text = { Text("הזנת קוד") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1; showQrScanner = true },
                icon = { Icon(Icons.Default.QrCodeScanner, null) },
                text = { Text("סריקת QR") }
            )
        }

        Spacer(Modifier.height(32.dp))

        CodeEntryTab(
            state = state,
            onVerify = { vm.verifyCode(it) }
        )
    }
}

@Composable
private fun CodeEntryTab(
    state: JoinState,
    onVerify: (String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current

    OutlinedTextField(
        value = formatCode(code),
        onValueChange = { raw ->
            val clean = raw.replace("-", "").uppercase().take(8)
            code = clean
            if (clean.length == 8) onVerify(clean)
        },
        label = { Text("קוד קהילה (לדוגמה: ABCD-1234)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
            onDone = {
                keyboard?.hide()
                onVerify(code)
            }
        ),
        modifier = Modifier.fillMaxWidth(),
        enabled = state !is JoinState.Loading,
        isError = state is JoinState.Error
    )

    Spacer(Modifier.height(12.dp))

    when (state) {
        is JoinState.Loading -> {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(36.dp))
            }
        }
        is JoinState.Error -> {
            Text(
                text = state.message,
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp
            )
        }
        else -> {}
    }
}

private fun formatCode(raw: String): String {
    return if (raw.length > 4) "${raw.take(4)}-${raw.drop(4)}" else raw
}
