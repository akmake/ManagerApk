const fs = require("fs");
const file = "C:/Users/yosef dahan/Documents/GitHub/sterni/client/src/pages/TetherAdminPage.jsx";
let content = fs.readFileSync(file, "utf8");

const importPatch = `import {\n  Clock, List, AppWindow,`;
if(!content.includes("Clock,")) { content = content.replace("import {\n", importPatch + "\n"); }

const modalsCode = `
// ─── Apps & Lock Modals ──────────────────────────────────────────────
function DeviceAppsModal({ device, community, onClose }) {
  const [saving, setSaving] = React.useState(false);
  const apps = device.installedApps || [];
  
  const isAllowed = (pkg) => community.policy?.allowedApps?.includes(pkg);
  const isBlocked = (pkg) => community.policy?.blockedApps?.includes(pkg);

  const toggleApp = async (pkg, action) => {
    setSaving(true);
    try {
      let allowed = [...(community.policy?.allowedApps || [])];
      let blocked = [...(community.policy?.blockedApps || [])];
      
      if (action === 'allow') {
        if (!allowed.includes(pkg)) allowed.push(pkg);
        blocked = blocked.filter(a => a !== pkg);
      } else if (action === 'block') {
        if (!blocked.includes(pkg)) blocked.push(pkg);
        allowed = allowed.filter(a => a !== pkg);
      } else {
        allowed = allowed.filter(a => a !== pkg);
        blocked = blocked.filter(a => a !== pkg);
      }

      await tetherApi.put(\`/admin/communities/\${community._id}/policy\`, {
        ...community.policy,
        allowedApps: allowed,
        blockedApps: blocked
      }, { headers: { Authorization: \`Bearer \${localStorage.getItem('tetherToken')}\` } });
      
      if(!community.policy) community.policy = {};
      community.policy.allowedApps = allowed;
      community.policy.blockedApps = blocked;
      toast.success('עודכן בהצלחה');
    } catch(err) {
      toast.error('שגיאה בעדכון הרשאות');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4" dir="rtl">
      <div className="bg-white rounded-xl w-full max-w-2xl max-h-[85vh] flex flex-col">
        <div className="p-4 border-b flex items-center justify-between">
          <h2 className="text-xl font-bold flex items-center gap-2"><AppWindow /> מנהל אפליקציות חסומות/מותורת</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600"><XCircle /></button>
        </div>
        <div className="p-4 overflow-y-auto flex-1 bg-gray-50">
          {apps.length === 0 ? <p className="text-center py-10 text-gray-500">אין אפליקציות מהמכשיר</p> : (
            <ul className="space-y-2">
              {apps.map((app, i) => (
                <li key={i} className="bg-white p-3 rounded shadow-sm flex items-center justify-between">
                  <div>
                    <div className="font-semibold text-gray-800">{app.appName}</div>
                    <div className="text-xs text-gray-500">{app.packageName} {app.isSystemApp ? '(מערכת)' : ''}</div>
                  </div>
                  <div className="flex gap-2">
                    <button disabled={saving} onClick={() => toggleApp(app.packageName, 'allow')} className={\`px-3 py-1 rounded text-sm \${isAllowed(app.packageName) ? 'bg-green-600 text-white' : 'bg-gray-100 hover:bg-gray-200'}\`}>אפשר תמיד</button>
                    <button disabled={saving} onClick={() => toggleApp(app.packageName, 'block')} className={\`px-3 py-1 rounded text-sm \${isBlocked(app.packageName) ? 'bg-red-600 text-white' : 'bg-gray-100 hover:bg-gray-200'}\`}>חסום תמיד</button>
                    <button disabled={saving} onClick={() => toggleApp(app.packageName, 'reset')} className={\`px-3 py-1 rounded text-sm \${!isAllowed(app.packageName) && !isBlocked(app.packageName) ? 'bg-blue-600 text-white' : 'bg-gray-100 hover:bg-gray-200'}\`}>מחדל רגיל</button>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </div>
  );
}

function LockCommunityModal({ community, onClose }) {
  const [lockType, setLockType] = React.useState('30m');
  const [adminPassword, setAdminPassword] = React.useState('');
  const [loading, setLoading] = React.useState(false);

  const handleLock = async () => {
    if (!adminPassword) return toast.error('סיסמת מנהל נדרשת');
    setLoading(true);
    try {
      let lockedUntilTs = null;
      const now = new Date();
      if (lockType === '30m') lockedUntilTs = now.getTime() + 30 * 60 * 1000;
      else if (lockType === '8am') {
        const tmrw = new Date(now); tmrw.setDate(tmrw.getDate() + 1); tmrw.setHours(8,0,0,0); lockedUntilTs = tmrw.getTime();
      } else if (lockType === 'unlock') lockedUntilTs = 0;

      await tetherApi.post(\`/community/\${community._id}/lock\`, { lockedUntilTs, adminPassword }, 
        { headers: { Authorization: \`Bearer \${localStorage.getItem('tetherToken')}\` } });
      
      toast.success('פעולת הנעילה בוצעה בהצלחה ונשלחה למכשירים');
      onClose();
    } catch(err) {
      toast.error('שגיאה. ודא סיסמת מנהל ונסה שוב');
    } finally { setLoading(false); }
  };

  return (
    <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4" dir="rtl">
      <div className="bg-white rounded-xl max-w-sm w-full p-6">
        <h2 className="text-xl font-bold mb-4 flex items-center gap-2"><Lock /> נעילת קהילה זמנית</h2>
        <div className="space-y-4">
          <div>
            <label className="block text-sm mb-1">סוג הפעולה:</label>
            <select value={lockType} onChange={e=>setLockType(e.target.value)} className="w-full border p-2 rounded">
              <option value="30m">נעל לחצי שעה</option>
              <option value="8am">נעל עד 8:00 מחר בבוקר</option>
              <option value="unlock">בטל נעילה באופן מיידי (Unlock)</option>
            </select>
          </div>
          <div>
            <label className="block text-sm mb-1">אישור בסיסמת מנהל הקשור לחשבון:</label>
            <input type="password" value={adminPassword} onChange={e=>setAdminPassword(e.target.value)} className="w-full border p-2 rounded" />
          </div>
          <div className="flex justify-end gap-2 mt-4">
            <button onClick={onClose} className="px-4 py-2 bg-gray-200 rounded hover:bg-gray-300">ביטול</button>
            <button disabled={loading} onClick={handleLock} className="px-4 py-2 bg-red-600 text-white rounded hover:bg-red-700">
              {loading ? 'מבצע...' : 'אשר חסימה'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
`;

if (!content.includes('DeviceAppsModal')) {
  content = content.replace('export default function TetherAdminPage', modalsCode + '\nexport default function TetherAdminPage');
}

// React State in CommunityCard
const stateHookTarget = "const [editPolicy, setEditPolicy] = useState(false);";
const stateHookNew = "const [editPolicy, setEditPolicy] = useState(false);\n  const [selectedDeviceApps, setSelectedDeviceApps] = useState(null);\n  const [showLockModal, setShowLockModal] = useState(false);";
if (!content.includes('selectedDeviceApps')) {
  content = content.replace(stateHookTarget, stateHookNew);
}

// Lock button insertion
const lockTarget = "{loadingDetail ? (";
const lockNew = `{showLockModal && <LockCommunityModal community={community} onClose={() => setShowLockModal(false)} />}
{selectedDeviceApps && <DeviceAppsModal device={selectedDeviceApps} community={community} onClose={() => setSelectedDeviceApps(null)} />}
<div className="flex justify-end mb-4 pt-2 px-4"><button onClick={() => setShowLockModal(true)} className="flex items-center gap-1 bg-red-100 text-red-700 font-bold px-3 py-1.5 rounded"><Lock size={16}/> אפשרויות נעילת מכשירים</button></div>
            {loadingDetail ? (` ;
if(!content.includes('אפשרויות נעילת מכשירים')) {
  content = content.replace(lockTarget, lockNew);
}

// App button insertion
const devRenderTargetTarget = "<Trash2 size={14} />";
const devRenderNew = `<Trash2 size={14} />\n                            </button>\n                            <button onClick={() => setSelectedDeviceApps(dev)} className="p-1.5 bg-blue-50 text-blue-600 rounded hover:bg-blue-100" title="ניהול אפליקציות">\n                              <List size={14} />`;
if(!content.includes("ניהול אפליקציות")) {
  content = content.replace(devRenderTargetTarget, devRenderNew);
}

fs.writeFileSync(file, content, "utf8");
console.log("Patched successfully!");