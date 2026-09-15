// Offline fixture generator. Node is a development tool, never an APK dependency.
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const root = path.resolve(__dirname, '..');
const source = fs.readFileSync(path.join(root, '.reference/MediaCrawler/libs/zhihu.js'), 'utf8');
const deterministicMath = Object.create(Math);
deterministicMath.random = () => 0.5;
const sandbox = vm.createContext({ require, Math: deterministicMath });
vm.runInContext(source, sandbox);
const paths = ['/api/v4/me?include=email%2Cis_active%2Cis_bind_phone', '/api/v4/search_v3?q=%E5%92%96%E5%95%A1&offset=0', '/api/v4/search_v3?q=emoji%F0%9F%98%80%26%2B&offset=20'];
const cookie = 'd_c0="development-fixture"; z_c0=not-a-real-session';
const vectors = paths.map(route => ({ route, cookie, expected: vm.runInContext(`get_sign(${JSON.stringify(route)},${JSON.stringify(cookie)})`, sandbox) }));
const destination = path.join(root, 'app/src/androidTest/assets/zhihu-vectors.json');
fs.mkdirSync(path.dirname(destination), { recursive: true });
fs.writeFileSync(destination, JSON.stringify(vectors, null, 2) + '\n');
console.log(`Wrote ${vectors.length} offline vectors from reference 8773e47.`);
