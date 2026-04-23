import re
import io

with open('C:/Users/yosef dahan/Documents/GitHub/sterni/client/src/pages/TetherAdminPage.jsx', 'r', encoding='utf-8') as f:
    text = f.read()

btn = '''                            <button
                              title={dev.allowUninstall ? "הסרת מנהל מותרת" : "אפשר הסרת מנהל"}
                              onClick={() => toggleAllowUninstall(dev)}
                              className={\p-1 rounded \\}
                            >
                              <select className="hidden cursor-pointer" /> {/* icon placeholder */}
                              {dev.allowUninstall ? "V" : "X"}
                            </button>
'''

btn = btn.replace('select className="hidden cursor-pointer" /', 'AppWindow size={14} /')

text = re.sub(
    r'(<button\s*onClick=\{\(\) => removeDevice\(dev\.deviceId\)\}\s*className="text-red-400 hover:text-red-600 p-1 rounded"\s*>)',
    btn + r'\1',
    text
)

with open('C:/Users/yosef dahan/Documents/GitHub/sterni/client/src/pages/TetherAdminPage.jsx', 'w', encoding='utf-8') as f:
    f.write(text)