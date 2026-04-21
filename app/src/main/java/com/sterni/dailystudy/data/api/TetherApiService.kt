package com.sterni.dailystudy.data.api

import com.sterni.dailystudy.data.model.ApprovalRequest
import com.sterni.dailystudy.data.model.JoinCommunityRequest
import com.sterni.dailystudy.data.model.JoinCommunityResponse
import com.sterni.dailystudy.data.model.PolicyUpdateResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

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
}
