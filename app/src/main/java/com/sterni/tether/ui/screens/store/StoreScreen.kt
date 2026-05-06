package com.sterni.tether.ui.screens.store

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sterni.tether.admin.TetherUpdater
import com.sterni.tether.data.api.RetrofitClient
import com.sterni.tether.data.api.TetherApiService
import com.sterni.tether.data.model.ApprovedApp
import com.sterni.tether.ui.theme.HebrewFont
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var approvedApps by remember { mutableStateOf<List<ApprovedApp>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val loadApps = {
        scope.launch {
            isLoading = true
            try {
                val api = RetrofitClient.create(TetherApiService::class.java)
                val response = api.getApprovedApps()
                if (response.isSuccessful) {
                    approvedApps = response.body() ?: emptyList()
                }
            } catch (e: Exception) {
                // Error handling
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadApps()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("האפליקציות שלי", fontFamily = HebrewFont) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowForward, "חזרה")
                    }
                },
                actions = {
                    IconButton(onClick = { loadApps() }) {
                        Icon(Icons.Default.Refresh, "רענן")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(approvedApps) { app ->
                    AppStoreItem(app)
                }
            }
        }
    }
}

@Composable
fun AppStoreItem(app: ApprovedApp) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pm = context.packageManager
    
    var isInstalled by remember { mutableStateOf(false) }
    var needsUpdate by remember { mutableStateOf(false) }
    var isInstalling by remember { mutableStateOf(false) }

    LaunchedEffect(app) {
        try {
            val info = pm.getPackageInfo(app.packageName, 0)
            isInstalled = true
            needsUpdate = app.versionCode > info.versionCode
        } catch (e: PackageManager.NameNotFoundException) {
            isInstalled = false
            needsUpdate = false
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Icon Placeholder (could use Coil for real icons)
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(app.appName.take(1), fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f)) {
                Text(app.appName, fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = HebrewFont)
                Text(
                    if (isInstalled) {
                        if (needsUpdate) "עדכון זמין (v${app.versionCode})" else "מותקן"
                    } else "לא מותקן",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isInstalling) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else if (!isInstalled || needsUpdate) {
                Button(
                    onClick = {
                        scope.launch {
                            isInstalling = true
                            TetherUpdater.checkAndUpdateAll(context) // For now, triggers all updates
                            isInstalling = false
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (needsUpdate) "עדכן" else "התקן", fontSize = 14.sp, fontFamily = HebrewFont)
                }
            }
        }
    }
}
