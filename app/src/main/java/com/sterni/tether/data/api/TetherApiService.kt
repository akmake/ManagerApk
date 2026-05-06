package com.sterni.tether.data.api

import com.sterni.tether.data.model.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

data class AppVersionResponse(val versionCode: Int, val versionName: String)

// ── Request / response payloads ──────────────────────────────────────────────

data class VerifyPinRequest(val pin: String)
data class VerifyPinResponse(val success: Boolean, val allowUninstall: Boolean)

/** Sent every 5 minutes by TetherWatchdogService. */
data class HeartbeatRequest(
    val accessibilityEnabled: Boolean,
    val isDeviceAdmin: Boolean,
    val isDeviceOwner: Boolean,
    val vpnActive: Boolean
)

/** Logged on each intercepted security event (uninstall attempt, admin deactivation, etc.). */
data class SecurityEventRequest(
    val type: String,        // UNINSTALL_ATTEMPT | ADMIN_DEACTIVATE_ATTEMPT | BLOCKED_APP_OPENED | TIME_LOCK_BLOCKED
    val packageName: String? // relevant for BLOCKED_APP_OPENED
)

// ── API interface ─────────────────────────────────────────────────────────────

interface TetherApiService {

    @POST("tether/devices/join")
    suspend fun joinCommunity(@Body request: JoinCommunityRequest): Response<JoinCommunityResponse>

    @GET("tether/devices/{deviceId}/policy")
    suspend fun getPolicy(@Path("deviceId") deviceId: String): Response<PolicyUpdateResponse>

    @POST("tether/devices/{deviceId}/approval")
    suspend fun requestApproval(
        @Path("deviceId") deviceId: String,
        @Query("action") action: String,
        @Query("packageName") packageName: String?
    ): Response<ApprovalRequest>

    @GET("tether/communities/{code}/verify")
    suspend fun verifyCommunityCode(@Path("code") code: String): Response<Map<String, Any>>

    @POST("tether/devices/{deviceId}/apps")
    suspend fun reportApps(@Path("deviceId") deviceId: String, @Body request: ReportAppsRequest): Response<Any>

    @POST("tether/devices/{deviceId}/verify-uninstall-pin")
    suspend fun verifyUninstallPin(
        @Path("deviceId") deviceId: String,
        @Body request: VerifyPinRequest
    ): Response<VerifyPinResponse>

    /** Called by TetherWatchdogService every 5 minutes. */
    @POST("tether/devices/{deviceId}/heartbeat")
    suspend fun sendHeartbeat(
        @Path("deviceId") deviceId: String,
        @Body request: HeartbeatRequest
    ): Response<Any>

    /** Called by TetherAccessibilityService on each intercepted event. */
    @POST("tether/devices/{deviceId}/events")
    suspend fun logSecurityEvent(
        @Path("deviceId") deviceId: String,
        @Body request: SecurityEventRequest
    ): Response<Any>

    @GET("tether/app/version")
    suspend fun getAppVersion(): Response<AppVersionResponse>

    @Streaming
    @GET("tether/app/download")
    suspend fun downloadApk(): Response<ResponseBody>

    @GET("tether/apps/approved")
    suspend fun getApprovedApps(): Response<List<ApprovedApp>>

    @Streaming
    @GET
    suspend fun downloadExternalApk(@retrofit2.http.Url url: String): Response<ResponseBody>
}
