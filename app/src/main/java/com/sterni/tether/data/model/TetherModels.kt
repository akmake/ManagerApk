package com.sterni.tether.data.model

data class Community(
    val id: String,
    val name: String,
    val code: String,
    val policy: CommunityPolicy,
    val adminId: String
)

data class CommunityPolicy(
    val blockInstallApps: Boolean = true,
    val hideGooglePlay: Boolean = true,
    val blockSafeBoot: Boolean = true,
    val blockFactoryReset: Boolean = true,
    val blockUsbTransfer: Boolean = false,
    val maxInstalledApps: Int? = null,
    val allowedApps: List<String> = emptyList(),
    val blockedApps: List<String> = emptyList(),
    val blockedActionBehavior: BlockedActionBehavior = BlockedActionBehavior.SILENT,
    val logsEnabled: Boolean = false,
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
    val apps: List<InstalledAppInfo>
)

enum class BlockedActionBehavior { SILENT, SHOW_MESSAGE, REQUEST_APPROVAL }

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
    val type: String,   // SHOW_MESSAGE | FORCE_SYNC
    val payload: String = ""
)

data class ApprovalRequest(
    val id: String,
    val deviceId: String,
    val communityId: String,
    val action: String,
    val packageName: String?,
    val requestedAt: String,
    val status: String
)
