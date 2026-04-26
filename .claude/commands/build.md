# Build APK

Build the debug APK using the Android Studio JBR (Java is not in PATH by default on this machine).

```bash
JAVA_HOME="C:\Program Files\Android\Android Studio\jbr" ./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

To install on connected device (USB debugging must be on):
```bash
ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
"$ADB" install app/build/outputs/apk/debug/app-debug.apk
```

Warnings about deprecated APIs (`recycle()`, `clearDeviceOwnerApp`, `LocalBroadcastManager`) are expected and do not block the build.
