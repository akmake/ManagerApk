package com.sterni.tether.ui.screens.emergency

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sterni.tether.admin.UninstallPinVerifier
import com.sterni.tether.admin.UninstallVerificationResult
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyCodeScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("קוד חירום") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Security,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(24.dp))

            Text(
                "שחרור חירום",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                "הזן קוד חירום כדי לפתוח חלון הסרה זמני למכשיר.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            OutlinedTextField(
                value = code,
                onValueChange = {
                    code = it
                    error = null
                },
                label = { Text("קוד חירום") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                enabled = !loading,
                isError = error != null,
                supportingText = { error?.let { Text(it) } }
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    scope.launch {
                        loading = true
                        val result = UninstallPinVerifier.verifyAndOpenWindow(context, code)
                        loading = false
                        when (result) {
                            UninstallVerificationResult.Success -> success = true
                            UninstallVerificationResult.InvalidPin -> error = "קוד שגוי."
                            UninstallVerificationResult.NotEnrolled -> error = "המכשיר לא מחובר לקהילה."
                            UninstallVerificationResult.NetworkError -> error = "נדרש חיבור אינטרנט לאימות הקוד."
                        }
                    }
                },
                enabled = code.isNotBlank() && !loading,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Default.LockOpen, null)
                    Spacer(Modifier.width(8.dp))
                    Text("שחרר מכשיר")
                }
            }

            if (success) {
                AlertDialog(
                    onDismissRequest = { success = false },
                    confirmButton = {
                        TextButton(onClick = {
                            success = false
                            onBack()
                        }) {
                            Text("אישור")
                        }
                    },
                    title = { Text("חלון הסרה נפתח") },
                    text = { Text("חלון הסרה פעיל לשעה. ניתן להסיר את האפליקציה מהגדרות המכשיר.") }
                )
            }
        }
    }
}

