package com.sterni.tether.data.model

// Auth
data class LoginRequest(val email: String, val password: String)
data class LoginResponse(val token: String, val user: AdminUser)
data class AdminUser(val id: String, val name: String, val email: String, val role: String)

// Dashboard
data class DashboardStats(
    val totalCommunities: Int,
    val totalDevices: Int,
    val pendingApprovals: Int,
    val inactiveDevices: Int
)
data class ActivityItem(
    val type: String,
    val description: String,
    val communityName: String?,
    val timestamp: String
)

// Community (admin view)
data class AdminCommunity(
    val id: String,
    val name: String,
    val code: String,
    val policy: CommunityPolicy,
    val deviceCount: Int,
    val active: Boolean,
    val createdAt: String
)

// Create community
data class CreateCommunityRequest(val name: String, val policy: CommunityPolicy)

// Device (admin view)
data class AdminDevice(
    val id: String,
    val deviceId: String,
    val deviceModel: String,
    val communityId: String,
    val isDeviceOwner: Boolean,
    val allowUninstall: Boolean = false,
    val lastSeen: String,
    val active: Boolean,
    val createdAt: String
)

// Logs
data class LogEntry(
    val id: String,
    val deviceId: String,
    val deviceModel: String?,
    val action: String,
    val result: String,
    val packageName: String?,
    val timestamp: String
)

// Manage admins
data class AdminMember(
    val id: String,
    val name: String,
    val email: String,
    val communities: List<String>
)
data class InviteAdminRequest(val email: String, val communityIds: List<String>)
