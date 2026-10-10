// Inlines the fonts and wordmark into src/app.html and writes www/index.html.
// One self-contained file: it is the web app AND the APK's web layer (no network fetches).
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { createHash } from 'node:crypto';
const root = new URL('..', import.meta.url);
const b64 = (f) => readFileSync(new URL(`src/${f}`, root)).toString('base64');
let html = readFileSync(new URL('src/app.html', root), 'utf8');
const map = {
  __F_OUTFIT__: b64('outfit-latin-wght-normal.woff2'),
  __F_JAKARTA__: b64('plus-jakarta-sans-latin-wght-normal.woff2'),
  __F_MONO__: b64('jetbrains-mono-latin-wght-normal.woff2'),
  __WORDMARK__: b64('ionity-wordmark.png'),
};
// Capacitor's JS runtime. The native bridge alone has no registerPlugin(); without this, no plugin is reachable.
map.__CAPACITOR_CORE__ = readFileSync(new URL('node_modules/@capacitor/core/dist/capacitor.js', root), 'utf8')
  .replace(/\/\/# sourceMappingURL=.*$/m, '').replace(/<\/script/gi, '<\\/script');
for (const [k, v] of Object.entries(map)) {
  if (!html.includes(k)) throw new Error(`placeholder ${k} missing`);
  html = html.split(k).join(v);
}
if (/__[A-Z_]+__/.test(html)) throw new Error('unreplaced placeholder');
mkdirSync(new URL('www', root), { recursive: true });
writeFileSync(new URL('www/index.html', root), html);
console.log(`www/index.html ${(html.length / 1024).toFixed(0)} KB sha256=${createHash('sha256').update(html).digest('hex').slice(0, 16)}`);
