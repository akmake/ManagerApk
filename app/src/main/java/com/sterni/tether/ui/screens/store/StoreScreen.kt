package com.sterni.tether.ui.screens.store

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sterni.tether.admin.TetherUpdater
import com.sterni.tether.data.api.RetrofitClient
import com.sterni.tether.data.api.TetherApiService
import com.sterni.tether.data.model.ApprovedApp
import com.sterni.tether.ui.theme.*
import kotlinx.coroutines.launch

private val BgOffWhite = Color(0xFFF8F6F2)

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
                val api = RetrofitClient.tetherApi
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
        containerColor = BgOffWhite,
        topBar = {
            TopAppBar(
                title = { Text("כלים", fontFamily = BaHaYetzira, fontSize = 22.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, "חזרה")
                    }
                },
                actions = {
                    IconButton(onClick = { loadApps() }) {
                        Icon(Icons.Default.Refresh, "רענן", tint = Primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgOffWhite)
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
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
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(10.dp),
                color = BgOffWhite
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(app.appName.take(1), fontWeight = FontWeight.Bold, color = Primary, fontFamily = BaHaYetzira)
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f)) {
                Text(app.appName, fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = SblHebrew, color = Ink)
                Text(
                    if (isInstalled) {
                        if (needsUpdate) "עדכון זמין" else "מותקן"
                    } else "ניתן להתקנה",
                    fontSize = 12.sp,
                    fontFamily = SblHebrew,
                    color = Muted
                )
            }

            if (isInstalling) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Primary)
            } else if (!isInstalled || needsUpdate) {
                Button(
                    onClick = {
                        scope.launch {
                            isInstalling = true
                            TetherUpdater.checkAndUpdateAll(context)
                            isInstalling = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text(if (needsUpdate) "עדכן" else "התקן", fontSize = 13.sp, fontFamily = SblHebrew)
                }
            }
        }
    }
}
