const fs = require('fs');
const filepath = 'C:/Users/yosef dahan/Documents/GitHub/sterni/client/src/pages/TetherAdminPage.jsx';
let content = fs.readFileSync(filepath, 'utf8');

// הוספת אייקונים חסרים
if (!content.includes('Clock,')) {
    content = content.replace('import {', 'import {\n  Clock, List, AppWindow,');
}

// 1. הוספת LockModal & AppsModal לפני אקספורט הראשי
const modalsCode = 
// ??? App Manager Modal ??????????????????????????????????????????????
function DeviceAppsModal({ device, community, onClose }) {
  const [saving, setSaving] = useState(false);
  const apps = device.installedApps || [];
  
  const isAllowed = (pkg) => community.policy?.allowedApps?.includes(pkg);
  const isBlocked = (pkg) => community.policy?.blockedApps?.includes(pkg);

  const toggleApp = async (pkg, action) => {
    setSaving(true);
    try {
      let allowed = [...(community.policy.allowedApps || [])];
      let blocked = [...(community.policy.blockedApps || [])];
      
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

      await axios.put(\/api/tether/admin/communities/\/policy\, {
        ...community.policy,
        allowedApps: allowed,
        blockedApps: blocked
      }, { headers: { Authorization: \\\Bearer \\\\ } });
      
      community.policy.allowedApps = allowed;
      community.policy.blockedApps = blocked;
      toast.success('עודכן בהצלחה');
    } catch(err) {
      toast.error('שגיאה בעדכון ההרשאה');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4" dir="rtl">
      <div className="bg-white rounded-xl w-full max-w-2xl max-h-[85vh] flex flex-col">
        <div className="p-4 border-b flex items-center justify-between">
          <h2 className="text-xl font-bold flex items-center gap-2"><AppWindow /> אפליקציות מותקנות: {device.deviceId}</h2>
          <button onClick={onClose}><XCircle className="text-gray-400 hover:text-gray-600"/></button>
        </div>
        <div className="p-4 overflow-y-auto flex-1 bg-gray-50">
          {apps.length === 0 ? <p className="text-center text-gray-500 py-10">אין מידע מסורק אפליקציות מהמכשיר עדיין.</p> : (
            <ul className="space-y-2">
              {apps.map((app, i) => (
                <li key={i} className="bg-white p-3 rounded-lg shadow-sm flex items-center justify-between">
                  <div>
                    <div className="font-semibold text-gray-800">{app.appName}</div>
                    <div className="text-xs text-gray-400">{app.packageName} {app.isSystemApp && '(מערכת)'}</div>
                  </div>
                  <div className="flex gap-2">
                    <button disabled={saving} onClick={() => toggleApp(app.packageName, 'allow')} className={\px-3 py-1 rounded text-sm \\}>מותר</button>
                    <button disabled={saving} onClick={() => toggleApp(app.packageName, 'block')} className={\px-3 py-1 rounded text-sm \\}>חסום</button>
                    <button disabled={saving} onClick={() => toggleApp(app.packageName, 'reset')} className={\px-3 py-1 rounded text-sm \\}>ברירת מחדל</button>
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

// ??? Lock Modal ??????????????????????????????????????????????
function LockCommunityModal({ community, onClose }) {
  const [lockType, setLockType] = useState('30m');
  const [adminPassword, setAdminPassword] = useState('');
  const [loading, setLoading] = useState(false);

  const handleLock = async () => {
    if (!adminPassword) return toast.error('סיסמת מנהל חובה לפעולה זו');
    setLoading(true);
    try {
      let lockedUntilTs = null;
      const now = new Date();
      if (lockType === '30m') {
        lockedUntilTs = now.getTime() + 30 * 60 * 1000;
      } else if (lockType === '8am') {
        const tomorrow = new Date(now);
        tomorrow.setDate(tomorrow.getDate() + 1);
        tomorrow.setHours(8, 0, 0, 0);
        lockedUntilTs = tomorrow.getTime();
      } else if (lockType === 'unlock') {
        lockedUntilTs = 0;
      }

      await axios.post(\/api/tether/community/\/lock\, { 
        lockedUntilTs, 
        adminPassword 
      }, { headers: { Authorization: \\\Bearer \\\\ } });
      
      toast.success('הגדרת הנעילה נשלחה בהצלחה למכשירים');
      onClose();
    } catch(err) {
      toast.error(err.response?.data?.error || 'שגיאה בביצוע הנעילה - בדוק סיסמה');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4" dir="rtl">
      <div className="bg-white rounded-xl w-full max-w-sm p-6">
        <h2 className="text-xl font-bold mb-4 flex items-center gap-2"><Lock /> מנגנון נעילת מכשירים: {community.name}</h2>
        <div className="space-y-4">
          <div>
            <label className="block text-sm mb-1 text-gray-700">סוג נעילה להחלה על חברי הקהילה:</label>
            <select value={lockType} onChange={e => setLockType(e.target.value)} className="w-full border p-2 rounded">
              <option value="30m">נעל לחצי שעה</option>
              <option value="8am">נעל עד 8:00 בבוקר מחר</option>
              <option value="unlock">בטל נעילה עכשיו (Unlock)</option>
            </select>
          </div>
          <div>
            <label className="block text-sm mb-1 text-gray-700">הזן את סיסמת מנהל החשבון לאישור הפעולה:</label>
            <input type="password" value={adminPassword} onChange={e=>setAdminPassword(e.target.value)} className="w-full border p-2 rounded" placeholder="סיסמת מנהל" />
          </div>
          <div className="pt-4 flex justify-end gap-2">
            <button onClick={onClose} className="px-4 py-2 bg-gray-200 rounded hover:bg-gray-300">ביטול</button>
            <button disabled={loading} onClick={handleLock} className="px-4 py-2 bg-red-600 text-white rounded hover:bg-red-700">
              {loading ? 'מפעיל פקודה...' : (lockType === 'unlock' ? 'שחרר נעילה' : 'נעל מכשירים')}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
;
if (!content.includes('DeviceAppsModal')) {
    content = content.replace(/export default function TetherAdminPage/, modalsCode + '\nexport default function TetherAdminPage');
}

// 2. הוספת כפתורי ניהול ברמת הקהילה
if (content.includes('detail.devices?.length') && !content.includes('LockCommunityModal')) {
    const originalDevicesList = <div className="space-y-3">
                        {detail.devices?.length === 0 ?;
    
    const newDevicesList = 
const [selectedDeviceApps, setSelectedDeviceApps] = useState(null);
const [showLockModal, setShowLockModal] = useState(false);

{showLockModal && <LockCommunityModal community={community} onClose={() => setShowLockModal(false)} />}
{selectedDeviceApps && <DeviceAppsModal device={selectedDeviceApps} community={community} onClose={() => setSelectedDeviceApps(null)} />}

<div className="flex gap-2 mb-4 mt-2">
  <button onClick={() => setShowLockModal(true)} className="flex items-center gap-1 bg-red-100 text-red-700 px-3 py-1.5 rounded hover:bg-red-200 text-sm">
    <Lock size={14} /> נעל/פתיחת קהילה
  </button>
</div>
<div className="space-y-3">
  {detail.devices?.length === 0 ?;

    content = content.replace(originalDevicesList, newDevicesList);
}

// 3. הוספת כפתור "נהל אפליקציות" בתוך כרטיסיית מכשיר ב-Client
if (!content.includes('selectedDeviceApps')) {
    const deviceItem = <button onClick={() => removeDevice(dev.deviceId)} className="p-1.5 bg-red-50 text-red-600 rounded hover:bg-red-100" title="הסר מכשיר">
                              <Trash2 size={14} />
                            </button>;
    
    const newDeviceItem = <button onClick={() => setSelectedDeviceApps(dev)} className="p-1.5 bg-blue-50 text-blue-600 rounded hover:bg-blue-100" title="ניהול אפליקציות">
                              <List size={14} />
                            </button>
                            <button onClick={() => removeDevice(dev.deviceId)} className="p-1.5 bg-red-50 text-red-600 rounded hover:bg-red-100" title="הסר מכשיר">
                              <Trash2 size={14} />
                            </button>;
    content = content.replace(deviceItem, newDeviceItem);
}

fs.writeFileSync(filepath, content, 'utf8');
