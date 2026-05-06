package com.sterni.tether.admin

import android.content.Context
import com.sterni.tether.data.api.RetrofitClient
import com.sterni.tether.data.api.TetherApiService
import com.sterni.tether.data.api.VerifyPinRequest

sealed class UninstallVerificationResult {
    object Success : UninstallVerificationResult()
    object InvalidPin : UninstallVerificationResult()
    object NotEnrolled : UninstallVerificationResult()
    object NetworkError : UninstallVerificationResult()
}

object UninstallPinVerifier {

    suspend fun verifyAndOpenWindow(context: Context, pin: String): UninstallVerificationResult {
        val deviceId = TetherPolicyManager.getDeviceId(context)
            ?: return UninstallVerificationResult.NotEnrolled

        return try {
            val response = RetrofitClient.create(TetherApiService::class.java)
                .verifyUninstallPin(deviceId, VerifyPinRequest(pin.trim()))

            if (!response.isSuccessful) return UninstallVerificationResult.NetworkError

            val body = response.body() ?: return UninstallVerificationResult.NetworkError
            if (!body.success || !body.allowUninstall) {
                UninstallVerificationResult.InvalidPin
            } else {
                TetherPolicyManager.startUninstallWindow(context)
                TetherPolicyManager.applyStoredPolicy(context)
                UninstallVerificationResult.Success
            }
        } catch (_: Exception) {
            UninstallVerificationResult.NetworkError
        }
    }
}

