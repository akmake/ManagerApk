package com.sterni.tether

import android.app.Application
import com.sterni.tether.admin.TetherPolicyManager
import com.sterni.tether.admin.AdminSession
import com.sterni.tether.data.api.RetrofitClient
import com.sterni.tether.util.DevLog
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TetherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Start capturing our own logcat into the in-app diagnostic console (F12-like), persisted to disk.
        DevLog.start(this)
        // Supply the per-device secret token to the HTTP layer (sent as X-Device-Token).
        RetrofitClient.setDeviceTokenProvider { TetherPolicyManager.getDeviceToken(this) }
        // An admin session ends only on an explicit logout — so a 401 on an admin call means the
        // token was revoked (logged out elsewhere / account deactivated). Drop it locally so the
        // manager is asked to sign in again instead of facing a dashboard that silently fails.
        RetrofitClient.setAdminUnauthorizedHandler { AdminSession.clearLocal(this) }
        // Required once before PDFBox is used (local article PDF extraction).
        PDFBoxResourceLoader.init(applicationContext)
    }
}
