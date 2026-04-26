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

// Protection status reported by device heartbeat
data class ProtectionStatus(
    val accessibilityEnabled: Boolean = false,
    val isDeviceAdmin: Boolean = false,
    val isDeviceOwner: Boolean = false,
    val vpnActive: Boolean = false
)

// Device (admin view)
data class AdminDevice(
    val id: String,
    val deviceId: String,
    val deviceModel: String,
    val deviceNickname: String? = null,
    val communityId: String,
    val isDeviceOwner: Boolean,
    val allowUninstall: Boolean = false,
    val lastSeen: String,
    val active: Boolean,
    val createdAt: String,
    val protectionStatus: ProtectionStatus = ProtectionStatus(),
    val isOnline: Boolean = false
)

// Installed app reported by device
data class InstalledApp(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean = false
)

// Per-device policy overrides (null = inherit from community)
data class DevicePolicy(
    val blockInstallApps: Boolean? = null,
    val hideGooglePlay: Boolean? = null,
    val blockAllStores: Boolean? = null,
    val blockApkInstall: Boolean? = null,
    val blockedApps: List<String> = emptyList(),
    val allowedApps: List<String> = emptyList(),
    val appTimeLocks: List<AppTimeLock> = emptyList(),
    val lockedUntilTs: Long? = null
)

// Full device detail returned by GET /admin/devices/:deviceId
data class DeviceDetail(
    val id: String,
    val deviceId: String,
    val deviceModel: String,
    val deviceNickname: String? = null,
    val communityId: String,
    val communityName: String,
    val isDeviceOwner: Boolean,
    val allowUninstall: Boolean = false,
    val lastSeen: String,
    val active: Boolean,
    val createdAt: String,
    val devicePolicy: DevicePolicy = DevicePolicy(),
    val installedApps: List<InstalledApp> = emptyList(),
    val protectionStatus: ProtectionStatus = ProtectionStatus(),
    val isOnline: Boolean = false
)

// Security event reported by device
data class SecurityEvent(
    val type: String,
    val packageName: String? = null,
    val timestamp: String
)

data class SecurityEventsResponse(
    val protectionStatus: ProtectionStatus,
    val events: List<SecurityEvent>
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

// Global overview — all devices/communities regardless of status
data class GlobalDevice(
    val id: String,
    val deviceId: String,
    val deviceModel: String,
    val deviceNickname: String? = null,
    val communityId: String? = null,
    val communityName: String,
    val communityCode: String = "",
    val isDeviceOwner: Boolean = false,
    val allowUninstall: Boolean = false,
    val active: Boolean = true,
    val lastSeen: String,
    val createdAt: String,
    val isOnline: Boolean = false,
    val protectionStatus: ProtectionStatus = ProtectionStatus()
)

data class GlobalCommunity(
    val id: String,
    val name: String,
    val code: String,
    val active: Boolean = true,
    val adminName: String,
    val adminEmail: String = "",
    val deviceCount: Int = 0,
    val activeDeviceCount: Int = 0,
    val createdAt: String
)

// Manage admins
data class AdminMember(
    val id: String,
    val name: String,
    val email: String,
    val role: String = "admin",
    val active: Boolean = true
)
data class InviteAdminRequest(val name: String, val email: String, val password: String)
