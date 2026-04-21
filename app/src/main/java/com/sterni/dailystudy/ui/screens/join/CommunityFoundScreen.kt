package com.sterni.dailystudy.ui.screens.join

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun CommunityFoundScreen(
    code: String,
    communityName: String,
    onConfirmed: () -> Unit,
    onCancel: () -> Unit,
    vm: JoinCommunityViewModel = viewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(100.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.People,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "נמצאה קהילה!",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = communityName,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        // What will change
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "הצטרפות לקהילה זו תאפשר:",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                PolicyItem(Icons.Default.Shield, "ניהול המכשיר ע\"י מנהל הקהילה")
                PolicyItem(Icons.Default.Lock, "הגבלת תוכן לפי הגדרות הקהילה")
            }
        }

        Spacer(Modifier.height(40.dp))

        Button(
            onClick = { vm.joinCommunity(code); vm.state.value.let { if (it is JoinState.Success) onConfirmed() } },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = state !is JoinState.Loading
        ) {
            if (state is JoinState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text("אני מסכים — הצטרף", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = state !is JoinState.Loading
        ) {
            Text("ביטול", fontSize = 16.sp)
        }

        if (state is JoinState.Error) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = (state as JoinState.Error).message,
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun PolicyItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 14.sp)
    }
}
