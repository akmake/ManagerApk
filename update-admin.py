import re

with open('C:/Users/yosef dahan/Documents/GitHub/sterni/client/src/pages/TetherAdminPage.jsx', 'r', encoding='utf-8') as f:
    text = f.read()

actionFuncDef = 'const removeDevice = async (deviceId) => {'
newActionFunc = '''const toggleAllowUninstall = async (device) => {
    if (!confirm(device.allowUninstall ? 'לבטל את אישור המחיקה למכשיר זה?' : 'לאשר למכשיר זה להסיר את האפליקציה?')) return;
    try {
      await tetherApi.put(\/admin/devices/\/allow-uninstall\, { allowUninstall: !device.allowUninstall }, { headers: authHeader() });
      setDetail(d => ({
        ...d,
        devices: d.devices.map(dev => dev.deviceId === device.deviceId ? { ...dev, allowUninstall: !dev.allowUninstall } : dev)
      }));
      toast.success(device.allowUninstall ? 'אישור מחיקה בוטל' : 'אישור מחיקה ניתן');
    } catch(err) { toast.error('שגיאה: ' + err.message); }
  };
''' + actionFuncDef

if 'toggleAllowUninstall' not in text:
    text = text.replace(actionFuncDef, newActionFunc)

replacement = '''<button onClick={() => toggleAllowUninstall(device)} className={\lex items-center gap-1 \\} title="אפשר מחיקה מהתקן"><AppWindow size={16} /> {device.allowUninstall ? 'מחיקה מותרת' : 'אפשר מחיקה'}</button>
                            <button onClick={() => removeDevice(device.deviceId)} className="text-red-600 hover:text-red-800" title="מחק מכשיר">'''

if 'toggleAllowUninstall(device)' not in text:
    text = re.sub(r'<button onClick=\{\(\) => removeDevice\(device\.deviceId\)\} className=\"text-red-600 hover:text-red-800\" title=\"מחק מכשיר\">', replacement, text)

with open('C:/Users/yosef dahan/Documents/GitHub/sterni/client/src/pages/TetherAdminPage.jsx', 'w', encoding='utf-8') as f:
    f.write(text)
