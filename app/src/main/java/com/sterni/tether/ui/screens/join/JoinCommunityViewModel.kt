package com.sterni.tether.ui.screens.join

import android.app.Application
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sterni.tether.admin.TetherPolicyManager
import com.sterni.tether.data.api.RetrofitClient
import com.sterni.tether.data.api.TetherApiService
import com.sterni.tether.data.model.JoinCommunityRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class JoinState {
    object Idle : JoinState()
    object Loading : JoinState()
    data class CommunityFound(val code: String, val name: String) : JoinState()
    data class Success(val communityName: String) : JoinState()
    data class Error(val message: String) : JoinState()
}

class JoinCommunityViewModel(app: Application) : AndroidViewModel(app) {

    private val api = RetrofitClient.create(TetherApiService::class.java)

    private val _state = MutableStateFlow<JoinState>(JoinState.Idle)
    val state: StateFlow<JoinState> = _state

    fun verifyCode(code: String) {
        if (code.length < 8) return
        viewModelScope.launch {
            _state.value = JoinState.Loading
            try {
                val res = api.verifyCommunityCode(code.trim().uppercase())
                if (res.isSuccessful) {
                    val name = res.body()?.get("name")?.toString() ?: ""
                    _state.value = JoinState.CommunityFound(code.trim().uppercase(), name)
                } else {
                    _state.value = JoinState.Error("קוד לא נמצא")
                }
            } catch (e: Exception) {
                _state.value = JoinState.Error("שגיאת רשת — בדוק חיבור")
            }
        }
    }

    fun joinCommunity(code: String) {
        val context = getApplication<Application>()
        // Use ANDROID_ID as hardware fingerprint but generate a fresh UUID as the
        // actual device identity so every join creates a truly new record on the server.
        val hardwareId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val deviceId = java.util.UUID.randomUUID().toString()
        val deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"

        viewModelScope.launch {
            _state.value = JoinState.Loading
            try {
                val res = api.joinCommunity(
                    JoinCommunityRequest(
                        code = code.trim().uppercase(),
                        deviceId = deviceId,
                        hardwareId = hardwareId,
                        deviceModel = deviceModel
                    )
                )
                if (res.isSuccessful && res.body()?.success == true) {
                    val body = res.body()!!
                    TetherPolicyManager.saveEnrollment(
                        context,
                        deviceId,
                        body.community!!.id,
                        body.community.name
                    )
                    TetherPolicyManager.saveDeviceToken(context, body.deviceToken)
                    TetherPolicyManager.applyPolicy(context, body.community.policy)
                    _state.value = JoinState.Success(body.community.name)
                } else {
                    _state.value = JoinState.Error(res.body()?.message ?: "שגיאה בהצטרפות")
                }
            } catch (e: Exception) {
                _state.value = JoinState.Error("שגיאת רשת — בדוק חיבור")
            }
        }
    }

    fun reset() { _state.value = JoinState.Idle }
}
