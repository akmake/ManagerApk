package com.sterni.tether.data.api

import com.sterni.tether.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface AuthApiService {
    @POST("tether/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>
}

interface AdminApiService {

    // Dashboard
    @GET("tether/admin/dashboard")
    suspend fun getDashboard(@Header("Authorization") token: String): Response<DashboardStats>

    @GET("tether/admin/activity")
    suspend fun getActivity(@Header("Authorization") token: String): Response<List<ActivityItem>>

    // Communities
    @GET("tether/admin/communities")
    suspend fun getCommunities(@Header("Authorization") token: String): Response<List<AdminCommunity>>

    @POST("tether/admin/communities")
    suspend fun createCommunity(
        @Header("Authorization") token: String,
        @Body request: CreateCommunityRequest
    ): Response<AdminCommunity>

    @GET("tether/admin/communities/{id}")
    suspend fun getCommunityDetail(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Response<CommunityDetailResponse>

    @PUT("tether/admin/communities/{id}/policy")
    suspend fun updatePolicy(
        @Header("Authorization") token: String,
        @Path("id") id: String,
        @Body policy: CommunityPolicy
    ): Response<PolicyUpdateResponse>

    @DELETE("tether/admin/communities/{id}")
    suspend fun deleteCommunity(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Response<Unit>

    // Devices
    @GET("tether/admin/devices/{deviceId}")
    suspend fun getDeviceDetail(
        @Header("Authorization") token: String,
        @Path("deviceId") deviceId: String
    ): Response<DeviceDetail>

    @GET("tether/admin/devices/{deviceId}/events")
    suspend fun getDeviceEvents(
        @Header("Authorization") token: String,
        @Path("deviceId") deviceId: String
    ): Response<SecurityEventsResponse>

    @PUT("tether/admin/devices/{deviceId}/device-policy")
    suspend fun updateDevicePolicy(
        @Header("Authorization") token: String,
        @Path("deviceId") deviceId: String,
        @Body policy: DevicePolicy
    ): Response<Unit>

    @PUT("tether/admin/devices/{deviceId}/allow-uninstall")
    suspend fun setAllowUninstall(
        @Header("Authorization") token: String,
        @Path("deviceId") deviceId: String,
        @Body body: Map<String, Boolean>
    ): Response<Map<String, Boolean>>

    @DELETE("tether/admin/devices/{deviceId}")
    suspend fun removeDevice(
        @Header("Authorization") token: String,
        @Path("deviceId") deviceId: String
    ): Response<Unit>

    // Approvals
    @GET("tether/admin/communities/{id}/approvals")
    suspend fun getApprovals(
        @Header("Authorization") token: String,
        @Path("id") communityId: String
    ): Response<List<ApprovalRequest>>

    @GET("tether/admin/approvals/all")
    suspend fun getAllApprovals(@Header("Authorization") token: String): Response<List<ApprovalRequest>>

    @PUT("tether/admin/approvals/{id}")
    suspend fun resolveApproval(
        @Header("Authorization") token: String,
        @Path("id") id: String,
        @Body body: Map<String, String>
    ): Response<ApprovalRequest>

    // Per-device commands
    @POST("tether/admin/devices/{deviceId}/commands")
    suspend fun sendCommand(
        @Header("Authorization") token: String,
        @Path("deviceId") deviceId: String,
        @Body body: Map<String, String>
    ): Response<Map<String, Any>>

    @PUT("tether/admin/devices/{deviceId}/nickname")
    suspend fun renameDevice(
        @Header("Authorization") token: String,
        @Path("deviceId") deviceId: String,
        @Body body: Map<String, String?>
    ): Response<Map<String, String?>>

    // Logs
    @GET("tether/admin/communities/{id}/logs")
    suspend fun getLogs(
        @Header("Authorization") token: String,
        @Path("id") communityId: String
    ): Response<List<LogEntry>>

    // Global overview (all devices + communities, no filters)
    @GET("tether/admin/global/devices")
    suspend fun getAllDevicesGlobal(@Header("Authorization") token: String): Response<List<com.sterni.tether.data.model.GlobalDevice>>

    @GET("tether/admin/global/communities")
    suspend fun getAllCommunitiesGlobal(@Header("Authorization") token: String): Response<List<com.sterni.tether.data.model.GlobalCommunity>>

    @DELETE("tether/admin/global/devices/{id}")
    suspend fun deleteDeviceGlobal(@Header("Authorization") token: String, @Path("id") id: String): Response<Unit>

    @DELETE("tether/admin/global/communities/{id}")
    suspend fun deleteCommunityGlobal(@Header("Authorization") token: String, @Path("id") id: String): Response<Unit>

    @PUT("tether/admin/global/communities/{id}/rename")
    suspend fun renameCommunityGlobal(
        @Header("Authorization") token: String,
        @Path("id") id: String,
        @Body body: Map<String, String>
    ): Response<Map<String, String>>

    // Admins (super admin only)
    @GET("tether/admin/members")
    suspend fun getAdmins(@Header("Authorization") token: String): Response<List<AdminMember>>

    @POST("tether/admin/members/invite")
    suspend fun inviteAdmin(
        @Header("Authorization") token: String,
        @Body request: InviteAdminRequest
    ): Response<Unit>

    @DELETE("tether/admin/members/{id}")
    suspend fun removeAdmin(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Response<Unit>
}

data class CommunityDetailResponse(
    val community: AdminCommunity,
    val devices: List<AdminDevice>
)
