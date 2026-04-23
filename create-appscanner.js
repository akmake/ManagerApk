const fs = require('fs');
const filepath = 'app/src/main/java/com/sterni/dailystudy/admin/AppScanner.kt';
let content = \package com.sterni.dailystudy.admin

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.sterni.dailystudy.data.api.AppDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppScanner {
    suspend fun getInstalledApps(context: Context): List<AppDetail> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        packages.map { appInfo ->
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val appName = pm.getApplicationLabel(appInfo).toString()
            AppDetail(
                packageName = appInfo.packageName,
                appName = appName,
                isSystemApp = isSystem
            )
        }
    }
}
\;
fs.writeFileSync(filepath, content, 'utf8');
