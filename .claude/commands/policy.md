# Tether Policy System — Android Context

Load this before working on policy, accessibility service, or VPN logic. Do NOT re-read all files from scratch.

## Flow

```
PolicySyncWorker (every 5 min)
  → GET /api/tether/devices/{deviceId}/policy
  → PolicyUpdateResponse { policy, allowUninstall, pendingCommands }
  → TetherPolicyManager.applyStoredPolicy(context)
      → applyTimeLock(dpm, policy.lockedUntilTs)       // device-level lock
      → applyAppTimeLocks(dpm, context, appTimeLocks)  // per-app time locks
      → setUserRestrictions(dpm)                       // factory reset, USB, safe boot
      → setKeyguardDisabledFeatures(dpm)
      → suspendBlockedApps(dpm, context)               // blockedApps list
      → allowExplicitApps(dpm, context)                // allowedApps list
  → Process pendingCommands (SHOW_MESSAGE | FORCE_SYNC | RELEASE_ALL)
```

## Key Classes

**`TetherPolicyManager`**
- `applyStoredPolicy(context)` — main entry, reads SharedPrefs, applies everything
- `applyAppTimeLocks(dpm, context, locks)` — suspends/unsuspends each app based on `lockedUntilTs`
- `releaseAll(context)` — clears all restrictions (used by RELEASE_ALL command)
- Requires `isDeviceOwner` for DPM calls — always check before calling DPM methods

**`TetherAccessibilityService`**
- `onAccessibilityEvent()` — intercepts window changes
- APK installer check: `if (policy == null || policy.blockApkInstall)` → close installer
- Store check: `if (policy == null || policy.blockInstallApps || policy.blockAllStores)` → close store

**`TetherVpnService`**
- Hardcoded Play Store CDN domains always blocked when `hideGooglePlay = true`
- Additional domains from `blockedDomains` (BLACKLIST mode) or all except `allowedDomains` (WHITELIST mode)

## SharedPrefs Keys

Policy is persisted locally so it survives reboots and offline periods. Keys are in `PolicySyncWorker` / `TetherPolicyManager`. Always read from SharedPrefs, not from network, when applying policy.

## AppTimeLock

```kotlin
data class AppTimeLock(
    val packageName: String,
    val lockedUntilTs: Long?  // null = permanent block, epoch ms = timed block
)
```

In `applyAppTimeLocks`: if `lockedUntilTs == null` OR `lockedUntilTs > System.currentTimeMillis()` → suspend app.
