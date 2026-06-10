package com.sterni.tether

import android.app.Application
import com.sterni.tether.admin.TetherPolicyManager
import com.sterni.tether.data.api.RetrofitClient
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TetherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Supply the per-device secret token to the HTTP layer (sent as X-Device-Token).
        RetrofitClient.setDeviceTokenProvider { TetherPolicyManager.getDeviceToken(this) }
    }
}
