package com.sterni.tether.admin

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.sterni.tether.data.model.InstalledAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppScanner {
    suspend fun getInstalledApps(context: Context): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val packages = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        }

        val relevantPackages = packages.filter { appInfo ->
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            
            if (!isSystem) return@filter true // אפליקציה שהמשתמש התקין - תמיד רלוונטי
            
            // אפליקציית מערכת - נדווח רק אם היא "מעניינת" (דפדפנים, חנויות, מדיה)
            val pkg = appInfo.packageName.lowercase()
            val isInteresting = pkg.contains("chrome") || 
                               pkg.contains("browser") || 
                               pkg.contains("vending") || // Play Store
                               pkg.contains("youtube") || 
                               pkg.contains("gallery") || 
                               pkg.contains("camera") ||
                               pkg.contains("video") ||
                               pkg.contains("music") ||
                               pkg.contains("facebook") ||
                               pkg.contains("instagram") ||
                               pkg.contains("whatsapp") ||
                               pkg.contains("tiktok")
            
            isInteresting
        }

        relevantPackages.map { appInfo ->
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            val appName = appInfo.loadLabel(pm).toString()
            InstalledAppInfo(
                packageName = appInfo.packageName,
                appName = appName,
                isSystemApp = isSystem
            )
        }
    }
}
