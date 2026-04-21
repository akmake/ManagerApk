package com.sterni.dailystudy.data.api

import com.sterni.dailystudy.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface AuthApiService {
    @POST("auth/login")
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

    // Logs
    @GET("tether/admin/communities/{id}/logs")
    suspend fun getLogs(
        @Header("Authorization") token: String,
        @Path("id") communityId: String
    ): Response<List<LogEntry>>

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
