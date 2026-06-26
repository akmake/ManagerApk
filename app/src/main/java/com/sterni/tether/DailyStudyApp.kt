package com.sterni.tether

import android.app.Application
import com.sterni.tether.admin.TetherPolicyManager
import com.sterni.tether.data.api.RetrofitClient
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TetherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Supply the per-device secret token to the HTTP layer (sent as X-Device-Token).
        RetrofitClient.setDeviceTokenProvider { TetherPolicyManager.getDeviceToken(this) }
        // Required once before PDFBox is used (local article PDF extraction).
        PDFBoxResourceLoader.init(applicationContext)
    }
}
