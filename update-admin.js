const fs = require('fs');
const filepath = 'C:/Users/yosef dahan/Documents/GitHub/sterni/client/src/pages/TetherAdminPage.jsx';
let text = fs.readFileSync(filepath, 'utf8');

text = text.replace(
/<\s*button\s*onClick=\{\(\) => removeDevice\(dev\.deviceId\)\}\s*className="text-red-400 hover:text-red-600 p-1 rounded"\s*>/,
  \<button
      title={dev.allowUninstall ? "אישור למחיקת האפליקציה פועל" : "אפשר למשתמש למחוק אפליקציה"}
      onClick={() => toggleAllowUninstall(dev)}
      className={\\\p-1 rounded \\\\}
    >
      <AppWindow size={14} />
    </button>
    <button
      onClick={() => removeDevice(dev.deviceId)}
      className="text-red-400 hover:text-red-600 p-1 rounded"
    >\
);

fs.writeFileSync(filepath, text, 'utf8');
console.log('Toggle UI patched');
