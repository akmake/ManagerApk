# Android Code Review

Review the selected Kotlin code. Focus on:

1. **DPM Safety** — every `dpm.*` call must be inside `if (dpm.isDeviceOwnerApp(packageName))` guard
2. **Null safety** — policy can be null (device not enrolled / offline), always handle with `policy ?: return` or defaults
3. **Coroutine scope** — suspend functions must be called from coroutine scope; don't block main thread
4. **Service lifecycle** — Accessibility Service and VPN Service must re-read policy from SharedPrefs on restart, not rely on in-memory state
5. **SharedPrefs consistency** — policy written and read with the same keys; use `apply()` not `commit()` unless synchronous write is needed
6. **Deprecated APIs** — note deprecated calls but don't fix unless they cause warnings that block build

Report: list issues with file:line, severity, one-line fix. Say "clean" if nothing found.
