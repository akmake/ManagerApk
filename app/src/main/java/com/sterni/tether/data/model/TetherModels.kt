package com.sterni.tether.data.model

data class Community(
    val id: String,
    val name: String,
    val code: String,
    val policy: CommunityPolicy,
    val adminId: String
)

data class AppTimeLock(
    val packageName: String,
    val lockedUntilTs: Long? = null  // null = permanent block
)

data class ApprovedApp(
    val packageName: String,
    val appName: String,
    val versionCode: Int,
    val downloadUrl: String,
    val iconUrl: String? = null
)

data class CommunityPolicy(
    val blockInstallApps: Boolean = true,
    val hideGooglePlay: Boolean = true,
    val blockAllStores: Boolean = false,   // per-device: block all store UIs
    val blockApkInstall: Boolean = true,   // per-device: block APK sideloading
    val blockSafeBoot: Boolean = true,
    val blockFactoryReset: Boolean = true,
    val blockUsbTransfer: Boolean = false,
    val maxInstalledApps: Int? = null,
    val allowedApps: List<String> = emptyList(),
    val blockedApps: List<String> = emptyList(),
    val appTimeLocks: List<AppTimeLock> = emptyList(),
    val blockedActionBehavior: BlockedActionBehavior = BlockedActionBehavior.SILENT,
    val approvalResolverMode: ApprovalResolverMode = ApprovalResolverMode.OWNER_ONLY,
    val logsEnabled: Boolean = false,
    val blockWhatsAppChannels: Boolean = false,
    val supportWhatsApp: String? = null,
    val supportEmail: String? = null,
    val supportName: String? = "מנהל הקהילה",
    val webFilterMode: WebFilterMode = WebFilterMode.NONE,
    val allowedDomains: List<String> = emptyList(),
    val blockedDomains: List<String> = emptyList(),
    val lockedUntilTs: Long? = null,
    val adminEmergencyCode: String? = null
)

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean
)

data class ReportAppsRequest(
    val deviceId: String,
    val installedApps: List<InstalledAppInfo>
)

enum class BlockedActionBehavior { SILENT, SHOW_MESSAGE, REQUEST_APPROVAL }
enum class ApprovalResolverMode { OWNER_ONLY, SUPERADMIN_ONLY, OWNER_OR_SUPERADMIN }

enum class WebFilterMode { NONE, BLACKLIST, WHITELIST }

data class TetherDevice(
    val id: String,
    val deviceId: String,
    val communityId: String,
    val communityName: String,
    val isDeviceOwner: Boolean,
    val enrolledAt: String
)

data class JoinCommunityRequest(
    val code: String,
    val deviceId: String,
    val hardwareId: String,
    val deviceModel: String
)

data class JoinCommunityResponse(
    val success: Boolean,
    val device: TetherDevice?,
    val community: Community?,
    val message: String?
)

/**
 * Returned by GET /devices/{deviceId}/policy.
 * [pendingCommands] contains commands queued by the admin (cleared after delivery).
 */
data class PolicyUpdateResponse(
    val policy: CommunityPolicy,
    val allowUninstall: Boolean = false,
    val pendingCommands: List<RemoteCommand> = emptyList()
)

/** A command pushed from the admin dashboard to a specific device. */
data class RemoteCommand(
    val type: String,   // SHOW_MESSAGE | FORCE_SYNC | RELEASE_ALL | APPROVAL_GRANTED
    val payload: String = ""
)

data class ApprovalRequest(
    val id: String,
    val deviceId: String,
    val communityId: String,
    val action: String,
    val packageName: String?,
    val requestedAt: String,
    val status: String,
    val resolvedByAdminId: String? = null,
    val resolvedAt: String? = null,
    val grantExpiresAt: Long? = null
)
