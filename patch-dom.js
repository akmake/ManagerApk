const fs = require('fs');
const filepath = 'C:/Users/yosef dahan/Documents/GitHub/sterni/client/src/pages/TetherAdminPage.jsx';
let content = fs.readFileSync(filepath, 'utf8');

// Fix DOM nested buttons in CommunityCard
// We need to replace the outer <button> wrapper of CommunityCard's accordion header with a <div>
content = content.replace(/<button\s*onClick=\{handleToggle\}\s*className="w-full flex items-center justify-between p-4 hover:bg-gray-50 transition text-right"\s*>/, '<div\n        onClick={handleToggle}\n        className="w-full flex items-center justify-between p-4 hover:bg-gray-50 transition text-right cursor-pointer"\n      >');

// Since we replaced the opening tag, we need to replace its closing tag.
// We look for the closing </button> right before '{open &&'
content = content.replace(/<\/button>\s*\{open && \(/, '</div>\n\n      {open && (');

// Fix keys
content = content.replace(/communities\.map\(c => \(\s*<CommunityCard/g, 'communities.map((c, i) => (\n            <CommunityCard\n              key={c._id || i}');

fs.writeFileSync(filepath, content, 'utf8');
console.log('Patch complete.');
