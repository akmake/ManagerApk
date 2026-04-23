const fs = require('fs');
const filepath = 'C:/Users/yosef dahan/Documents/GitHub/sterni/client/src/pages/TetherAdminPage.jsx';
let content = fs.readFileSync(filepath, 'utf8');

// Just target community._id inside fetches
content = content.replace(/\$\{community\._id\}/g, '\');

fs.writeFileSync(filepath, content, 'utf8');
console.log('Done');
