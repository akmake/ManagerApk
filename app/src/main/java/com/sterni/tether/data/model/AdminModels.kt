package com.sterni.tether.data.model

import com.google.gson.annotations.SerializedName

// Auth
data class LoginRequest(val email: String, val password: String)
data class LoginResponse(val token: String, val user: AdminUser)
data class AdminUser(val id: String, val name: String, val email: String, val role: String)

// Dashboard
data class DashboardStats(
    @SerializedName("total_communities", alternate = ["totalCommunities"]) val totalCommunities: Int,
    @SerializedName("total_devices", alternate = ["totalDevices"]) val totalDevices: Int,
    @SerializedName("pending_approvals", alternate = ["pendingApprovals"]) val pendingApprovals: Int,
    @SerializedName("inactive_devices", alternate = ["inactiveDevices"]) val inactiveDevices: Int
)
data class ActivityItem(
    val type: String,
    val description: String,
    @SerializedName("community_name", alternate = ["communityName"]) val communityName: String?,
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
    @SerializedName("package_name", alternate = ["packageName"]) val packageName: String,
    @SerializedName("app_name", alternate = ["appName"]) val appName: String,
    @SerializedName("is_system_app", alternate = ["isSystemApp"]) val isSystemApp: Boolean = false
)

// Per-device policy overrides (null = inherit from community)
data class DevicePolicy(
    @SerializedName("block_install_apps", alternate = ["blockInstallApps"]) val blockInstallApps: Boolean? = null,
    @SerializedName("hide_google_play", alternate = ["hideGooglePlay"]) val hideGooglePlay: Boolean? = null,
    @SerializedName("block_all_stores", alternate = ["blockAllStores"]) val blockAllStores: Boolean? = null,
    @SerializedName("block_apk_install", alternate = ["blockApkInstall"]) val blockApkInstall: Boolean? = null,
    @SerializedName("block_safe_boot", alternate = ["blockSafeBoot"]) val blockSafeBoot: Boolean? = null,
    @SerializedName("block_factory_reset", alternate = ["blockFactoryReset"]) val blockFactoryReset: Boolean? = null,
    @SerializedName("block_usb_transfer", alternate = ["blockUsbTransfer"]) val blockUsbTransfer: Boolean? = null,
    @SerializedName("max_installed_apps", alternate = ["maxInstalledApps"]) val maxInstalledApps: Int? = null,
    @SerializedName("blocked_action_behavior", alternate = ["blockedActionBehavior"]) val blockedActionBehavior: BlockedActionBehavior? = null,
    @SerializedName("logs_enabled", alternate = ["logsEnabled"]) val logsEnabled: Boolean? = null,
    @SerializedName("block_whatsapp_channels", alternate = ["blockWhatsAppChannels"]) val blockWhatsAppChannels: Boolean? = null,
    @SerializedName("support_whatsapp", alternate = ["supportWhatsApp"]) val supportWhatsApp: String? = null,
    @SerializedName("support_email", alternate = ["supportEmail"]) val supportEmail: String? = null,
    @SerializedName("support_name", alternate = ["supportName"]) val supportName: String? = null,
    @SerializedName("web_filter_mode", alternate = ["webFilterMode"]) val webFilterMode: WebFilterMode? = null,
    @SerializedName("allowed_domains", alternate = ["allowedDomains"]) val allowedDomains: List<String>? = null,
    @SerializedName("blocked_domains", alternate = ["blockedDomains"]) val blockedDomains: List<String>? = null,
    @SerializedName("admin_emergency_code", alternate = ["adminEmergencyCode"]) val adminEmergencyCode: String? = null,
    @SerializedName("blocked_apps", alternate = ["blockedApps"]) val blockedApps: List<String> = emptyList(),
    @SerializedName("allowed_apps", alternate = ["allowedApps"]) val allowedApps: List<String> = emptyList(),
    @SerializedName("app_time_locks", alternate = ["appTimeLocks"]) val appTimeLocks: List<AppTimeLock> = emptyList(),
    @SerializedName("locked_until_ts", alternate = ["lockedUntilTs"]) val lockedUntilTs: Long? = null
)

// Full device detail returned by GET /admin/devices/:deviceId
data class DeviceDetail(
    val id: String,
    @SerializedName("device_id", alternate = ["deviceId"]) val deviceId: String,
    @SerializedName("device_model", alternate = ["deviceModel"]) val deviceModel: String,
    @SerializedName("device_nickname", alternate = ["deviceNickname"]) val deviceNickname: String? = null,
    @SerializedName("community_id", alternate = ["communityId"]) val communityId: String,
    @SerializedName("community_name", alternate = ["communityName"]) val communityName: String,
    @SerializedName("is_device_owner", alternate = ["isDeviceOwner"]) val isDeviceOwner: Boolean,
    @SerializedName("allow_uninstall", alternate = ["allowUninstall"]) val allowUninstall: Boolean = false,
    @SerializedName("last_seen", alternate = ["lastSeen"]) val lastSeen: String,
    val active: Boolean,
    @SerializedName("created_at", alternate = ["createdAt"]) val createdAt: String,
    @SerializedName("device_policy", alternate = ["devicePolicy"]) val devicePolicy: DevicePolicy = DevicePolicy(),
    @SerializedName("installed_apps", alternate = ["installedApps", "apps"]) val installedApps: List<InstalledApp> = emptyList(),
    @SerializedName("protection_status", alternate = ["protectionStatus"]) val protectionStatus: ProtectionStatus = ProtectionStatus(),
    @SerializedName("is_online", alternate = ["isOnline"]) val isOnline: Boolean = false
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

data class AdminApprovedApp(
    @SerializedName("_id", alternate = ["id"]) val id: String,
    @SerializedName("packageName") val packageName: String,
    @SerializedName("appName") val appName: String,
    @SerializedName("versionCode") val versionCode: Int,
    @SerializedName("downloadUrl") val downloadUrl: String,
    @SerializedName("iconUrl") val iconUrl: String? = null,
    @SerializedName("active") val active: Boolean = true
)
