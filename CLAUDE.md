# Tether Android App — Working Rules

## Project Overview

Lightweight MDM (Mobile Device Management) Android app for religious communities.
Companion to the Tether module in the `sterni` web project.

- **Language:** Kotlin
- **DI:** Hilt
- **Min SDK:** 26 | **Target:** 34
- **Package:** `com.sterni.tether`

## Architecture — Two-Layer Protection

1. **Accessibility Service** (`TetherAccessibilityService.kt`) — intercepts UI events, closes blocked apps/stores in real time
2. **VPN Service** (`TetherVpnService.kt`) — DNS-level blocking of Play Store CDN domains, web filter (blacklist/whitelist)

These run simultaneously. VPN is always-on when enrolled.

## Key Files

| File | Responsibility |
|------|----------------|
| `admin/TetherPolicyManager.kt` | Applies policy: DPM restrictions, app suspend/unsuspend, time locks |
| `admin/TetherAccessibilityService.kt` | Real-time UI blocking, APK install blocking, store blocking |
| `admin/TetherVpnService.kt` | DNS-level blocking, web filter |
| `admin/TetherWatchdogService.kt` | Ensures services stay alive, re-starts them if killed |
| `sync/PolicySyncWorker.kt` | WorkManager worker: polls server every 5 min, applies policy, reports heartbeat |
| `data/model/TetherModels.kt` | All data classes: `CommunityPolicy`, `AppTimeLock`, `PolicyUpdateResponse`, `RemoteCommand` |
| `ui/screens/home/HomeScreen.kt` | Main user-facing screen |

## Policy System

```kotlin
data class CommunityPolicy(
    val blockInstallApps: Boolean = true,
    val hideGooglePlay: Boolean = true,
    val blockAllStores: Boolean = false,
    val blockApkInstall: Boolean = true,
    val blockSafeBoot: Boolean = true,
    val blockFactoryReset: Boolean = true,
    val blockUsbTransfer: Boolean = false,
    val allowedApps: List<String> = emptyList(),
    val blockedApps: List<String> = emptyList(),
    val appTimeLocks: List<AppTimeLock> = emptyList(),  // null lockedUntilTs = permanent
    val lockedUntilTs: Long? = null,                    // device-level lock
    val webFilterMode: WebFilterMode = WebFilterMode.NONE,
    val allowedDomains: List<String> = emptyList(),
    val blockedDomains: List<String> = emptyList(),
)
```

`PolicyUpdateResponse` also contains:
- `allowUninstall: Boolean` — whether user can remove app
- `pendingCommands: List<RemoteCommand>` — `SHOW_MESSAGE`, `FORCE_SYNC`, `RELEASE_ALL`

## Server API (base URL in `TetherApi.kt`)

| Endpoint | Description |
|----------|-------------|
| `POST /api/tether/devices/join` | Enroll device |
| `GET /api/tether/devices/{deviceId}/policy` | Fetch policy + pending commands |
| `POST /api/tether/devices/{deviceId}/heartbeat` | Report protection status |
| `POST /api/tether/devices/{deviceId}/apps` | Report installed apps |

## Build

```bash
JAVA_HOME="C:\Program Files\Android\Android Studio\jbr" ./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Device Owner vs Non-Owner

- **Device Owner (DO):** Full DPM control — can hide apps, block factory reset, etc.
- **Non-DO:** Relies on Accessibility Service + VPN only
- `TetherPolicyManager.applyStoredPolicy()` checks `isDeviceOwner` before calling DPM methods

## Sync Map — Coupled Files

| Change | Also update |
|--------|-------------|
| `TetherModels.kt` | `TetherPolicyManager.kt`, `PolicySyncWorker.kt`, server models |
| `TetherPolicyManager.kt` | `TetherAccessibilityService.kt` (shares policy logic) |
| `TetherVpnService.kt` | `TetherWatchdogService.kt` (restarts it) |
| Any new policy field | `TetherModels.kt` + server `tetherRoutes.js` + `TetherAdminPage` |
