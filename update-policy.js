const fs = require('fs');
const filepath = 'app/src/main/java/com/sterni/dailystudy/admin/TetherPolicyManager.kt';
let text = fs.readFileSync(filepath, 'utf8');

text = text.replace(
    'dpm.setUninstallBlocked(admin, context.packageName, true)',
    \al allowUninstall = isUninstallAllowed(context)
            dpm.setUninstallBlocked(admin, context.packageName, !allowUninstall)
            if (allowUninstall) {
                try {
                    dpm.clearDeviceOwnerApp(context.packageName)
                    Log.i(TAG, "Cleared DO app because uninstall is allowed")
                } catch(e: Exception) {
                    Log.w(TAG, "Could not clear DO", e)
                }
            }\
);

fs.writeFileSync(filepath, text, 'utf8');
console.log('Updated TetherPolicyManager.kt');
