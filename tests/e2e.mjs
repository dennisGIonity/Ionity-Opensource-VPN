// End-to-end tests: the built www/index.html against a mock VPN Gate feed (served from this file).
// Run: node tests/e2e.mjs   (Chromium from Playwright; set CHROMIUM=/path to override.)
import { chromium } from 'playwright';
import assert from 'node:assert/strict';
import { existsSync, readFileSync, writeFileSync, mkdtempSync } from 'node:fs';
import { createServer } from 'node:http';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

/* ---------- mock VPN Gate feed ---------- */
const cfg = (ip, proto = 'tcp') => Buffer.from(
  `dev tun\nproto ${proto}\nremote ${ip} 443\ncipher AES-128-CBC\nauth SHA1\nresolv-retry infinite\nnobind\npersist-key\npersist-tun\nclient\nverb 3\n<ca>\n-----BEGIN CERTIFICATE-----\n${'MIIB'.repeat(60)}\n-----END CERTIFICATE-----\n</ca>\n`).toString('base64');
const SERVERS = [ // host, ip, score, ping, country, cc
  ['jp-a', '203.0.113.11', 900000, 12, 'Japan', 'JP'], ['jp-b', '203.0.113.12', 800000, 15, 'Japan', 'JP'], ['jp-c', '203.0.113.13', 700000, 20, 'Japan', 'JP'], ['jp-d', '203.0.113.14', 10, 99, 'Japan', 'JP'],
  ['kr-a', '203.0.113.21', 600000, 30, 'Korea Republic of', 'KR'], ['kr-b', '203.0.113.22', 500000, 31, 'Korea Republic of', 'KR'],
  ['us-a', '198.51.100.31', 400000, 120, 'United States', 'US'], ['us-b', '198.51.100.32', 300000, 130, 'United States', 'US'],
  ['de-a', '198.51.100.41', 350000, 180, 'Germany', 'DE'], ['gb-a', '198.51.100.51', 250000, 170, 'United Kingdom', 'GB'],
  ['br-a', '192.0.2.61', 150000, 300, 'Brazil', 'BR'], ['za-a', '192.0.2.71', 100000, 40, 'South Africa', 'ZA'],
];
const feed = () => ['*vpn_servers', '#HostName,IP,Score,Ping,Speed,CountryLong,CountryShort,NumVpnSessions,Uptime,TotalUsers,TotalTraffic,LogType,Operator,Message,OpenVPN_ConfigData_Base64',
  ...SERVERS.map(([h, ip, sc, p, cl, c]) => [h, ip, sc, p, 50000000, cl, c, 10, 86400000, 1000, 1e9, '2weeks', "Some, owner's PC", '', cfg(ip)].join(',')), '*'].join('\r\n');
let mode = 'ok', hits = 0;
const srv = createServer((req, res) => {
  hits++;
  res.setHeader('Access-Control-Allow-Origin', '*');
  if (mode === 'fail') { res.writeHead(503); return res.end('down'); }
  res.writeHead(200, { 'Content-Type': 'text/plain' }); res.end(feed());
}).listen(0, '127.0.0.1');
await new Promise((r) => srv.on('listening', r));
const FEED = `http://127.0.0.1:${srv.address().port}/api/iphone/`;
const APP = new URL('../www/index.html', import.meta.url).href + '?feed=' + encodeURIComponent(FEED);

/* ---------- mock native engine (IonityVpn plugin) ---------- */
const ENGINE = () => {
  const calls = (window.__vpn = { connect: [], disconnect: 0, listeners: [] });
  window.__iovTestPlugins = { IonityVpn: {
    connect: async (o) => { calls.connect.push(o); },
    disconnect: async () => { calls.disconnect++; calls.listeners.forEach((f) => f({ state: 'DISCONNECTED' })); },
    getStatus: async () => ({ state: 'DISCONNECTED' }),
    getLog: async () => ({ lines: ['TLS Error: TLS handshake failed', 'SIGUSR1[soft,tls-error] received'] }),
    addListener: (ev, f) => { calls.listeners.push(f); return { remove() {} }; },
  } };
  window.__emit = (s) => calls.listeners.forEach((f) => f(s));
};

const exe = process.env.CHROMIUM || (existsSync('/opt/pw-browsers/chromium') ? '/opt/pw-browsers/chromium' : undefined);
const browser = await chromium.launch(exe ? { executablePath: exe } : {});
let pass = 0, fail = 0;
async function test(name, fn, { engine = false, viewport = { width: 400, height: 860 }, prep } = {}) {
  mode = 'ok';
  const ctx = await browser.newContext({ viewport, acceptDownloads: true });
  if (engine) await ctx.addInitScript(ENGINE);
  const page = await ctx.newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push(e.message));
  try {
    if (prep) await prep(page, ctx);
    await page.goto(APP);
    await fn(page, ctx);
    assert.deepEqual(errors, [], 'no page errors');
    console.log('  ✓', name); pass++;
  } catch (e) {
    console.log('  ✗', name, '\n     ', e.message.split('\n')[0]); fail++;
    await page.screenshot({ path: join(tmpdir(), `e2e-fail-${pass + fail}.png`) }).catch(() => {});
  } finally { await ctx.close(); }
}
const ready = (p) => p.waitForSelector('.opt:not(:disabled)');
const tile = (p, label) => p.locator('.opt', { hasText: label });
const read = async (dl) => readFileSync(await dl.path(), 'utf8');

console.log('Ionity VPN — e2e');

await test('standalone: reads VPN Gate itself; no box, no pairing, no discovery anywhere', async (p) => {
  await ready(p);
  const txt = await p.textContent('body');
  assert.doesNotMatch(txt, /Gate\^Flame|pair|Find my box|box address/i);
  assert.equal(await p.locator('.opt').count(), 6, 'six continent tiles');
  assert.match(await p.textContent('.stats'), /12/, '12 servers counted');
});

await test('the Capacitor runtime is in the page, so native plugins are reachable in the APK', async (p) => {
  await ready(p);
  assert.equal(await p.evaluate(() => typeof window.Capacitor?.registerPlugin), 'function');
  assert.equal(await p.evaluate(() => window.Capacitor.getPlatform()), 'web');
});

await test('continents: Asia resolves to its best-scoring country; empty continents are disabled', async (p) => {
  await ready(p);
  assert.match(await tile(p, 'Asia').textContent(), /Best now: Japan[\s\S]*2 countries · 6 servers/);
  assert.match(await tile(p, 'Europe').textContent(), /Best now: Germany/);
  assert.ok(await tile(p, 'Oceania').isDisabled(), 'no Oceania server in the feed');
  await tile(p, 'Asia').click();
  assert.match(await p.textContent('.hero'), /appear in Japan/);
});

await test('countries: search finds "South Korea" (VPN Gate calls it "Korea Republic of") and picking it sets the location', async (p) => {
  await ready(p);
  await p.fill('[data-input=q]', 'kor');
  assert.equal(await p.locator('.li').count(), 1);
  await p.click('.li');
  assert.match(await p.textContent('.hero'), /appear in South Korea/);
  assert.match(await p.textContent('.stats'), /KR/);
});

await test('choice survives a restart', async (p) => {
  await ready(p);
  await p.fill('[data-input=q]', 'brazil'); await p.click('.li');
  await p.reload(); await ready(p);
  assert.match(await p.textContent('.hero'), /appear in Brazil/);
});

await test('no engine in the build: connect hands over a fresh .ovpn for the best server, patched for OpenVPN 2.6', async (p) => {
  await ready(p);
  await tile(p, 'Asia').click();
  const [dl] = await Promise.all([p.waitForEvent('download'), p.click('[data-act=power]')]);
  assert.equal(dl.suggestedFilename(), 'ionity-vpn-jp.ovpn');
  const t = await read(dl);
  assert.match(t, /remote 203\.0\.113\.11 443/);
  assert.match(t, /^data-ciphers AES-128-CBC:AES-256-GCM/m);
  assert.match(t, /^data-ciphers-fallback AES-128-CBC/m);
  assert.doesNotMatch(t, /tls-cert-profile/, 'files for other OpenVPN apps stay standard');
  assert.match(await p.textContent('.hero'), /OpenVPN Connect/);
});

await test('engine: one tap connects in-app to the best server; status shows the country; tap again disconnects', async (p) => {
  await ready(p);
  await tile(p, 'Asia').click();
  await p.click('[data-act=power]');
  await p.waitForFunction(() => window.__vpn.connect.length === 1);
  assert.match((await p.evaluate(() => window.__vpn.connect[0].config)), /remote 203\.0\.113\.11 443/);
  assert.match((await p.evaluate(() => window.__vpn.connect[0].config)), /^tls-cert-profile insecure$/m, 'engine accepts VPN Gate certificates');
  assert.match(await p.textContent('.hero'), /Connecting/);
  await p.evaluate(() => window.__emit({ state: 'CONNECTED' }));
  await p.waitForSelector('.power.on');
  assert.match(await p.textContent('#big'), /Japan/);
  assert.match(await p.textContent('.pill'), /JP/);
  await p.click('[data-act=power]');
  await p.waitForSelector('.power:not(.on)');
  assert.equal(await p.evaluate(() => window.__vpn.disconnect), 1);
  assert.match(await p.textContent('#big'), /Not connected/);
}, { engine: true });

await test('engine: a server that fails is replaced by the next best in the same country, then a clear error after 3', async (p) => {
  await ready(p);
  await tile(p, 'Asia').click();
  await p.click('[data-act=power]');
  for (const ip of ['203.0.113.11', '203.0.113.12', '203.0.113.13']) {
    await p.waitForFunction((ip) => window.__vpn.connect.at(-1)?.config.includes(ip), ip);
    await p.evaluate(() => window.__emit({ state: 'ERROR', detail: 'TLS handshake failed' }));
  }
  await p.waitForSelector('.dot.bad');
  assert.equal(await p.evaluate(() => window.__vpn.connect.length), 3, 'never tries the 4th (low-score) server');
  assert.match(await p.textContent('.hero'), /None of the 3 servers tried in Japan answered/);
  await p.click('[data-act=log]');
  await p.waitForSelector('#sheet[open]');
  assert.match(await p.textContent('#sheet'), /TLS handshake failed/);
}, { engine: true });

await test('engine: changing the place while connected switches the tunnel live', async (p) => {
  await ready(p);
  await tile(p, 'Asia').click();
  await p.click('[data-act=power]');
  await p.evaluate(() => window.__emit({ state: 'CONNECTED' }));
  await p.waitForSelector('.power.on');
  await p.fill('[data-input=q]', 'united states'); await p.click('.li');
  await p.waitForFunction(() => window.__vpn.connect.length === 2);
  assert.match(await p.evaluate(() => window.__vpn.connect[1].config), /remote 198\.51\.100\.31 443/);
  await p.evaluate(() => window.__emit({ state: 'CONNECTED' }));
  await p.waitForSelector('.power.on');
  assert.match(await p.textContent('#big'), /United States/);
}, { engine: true });

await test('feed down with nothing cached: says so, Retry recovers', async (p) => {
  await p.waitForSelector('.note.bad');
  assert.match(await p.textContent('.note.bad'), /Could not refresh the server list: the server list answered 503/);
  mode = 'ok';
  await p.click('.note [data-act=reload]');
  await ready(p);
  assert.equal(await p.locator('.note').count(), 0);
}, { prep: () => { mode = 'fail'; } });

await test('feed down with a cached list: keeps working from the cache and says how old it is', async (p) => {
  await ready(p);
  mode = 'fail';
  await p.reload();
  await p.waitForSelector('.note');
  assert.match(await p.textContent('.note'), /Using the list from/);
  assert.ok(!(await tile(p, 'Asia').isDisabled()));
});

await test('desktop: "Send to desktop" produces a standalone page that hands out .ovpn files per continent/country', async (p, ctx) => {
  await ready(p);
  const [dl] = await Promise.all([p.waitForEvent('download'), p.click('[data-act=desktop]')]);
  assert.match(dl.suggestedFilename(), /^ionity-vpn-desktop-\d{4}-\d\d-\d\d\.html$/);
  const file = join(mkdtempSync(join(tmpdir(), 'iov-')), 'desk.html');
  writeFileSync(file, await read(dl));
  const before = hits;
  const d = await ctx.newPage();
  const errs = []; d.on('pageerror', (e) => errs.push(e.message));
  await d.goto('file://' + file);
  assert.equal(hits, before, 'the desktop page needs no network');
  assert.match(await d.textContent('#tabs'), /All 7/);
  await d.click('[data-tab=asia]');
  assert.match(await d.textContent('#best'), /Best in Asia right now: Japan/);
  const [a] = await Promise.all([d.waitForEvent('download'), d.click('#best [data-dl]')]);
  assert.equal(a.suggestedFilename(), 'ionity-vpn-jp.ovpn');
  const t = await read(a);
  assert.match(t, /remote 203\.0\.113\.11 443/); assert.match(t, /^data-ciphers /m);
  const [b] = await Promise.all([d.waitForEvent('download'), d.click('.r [data-dl=JP]')]);
  assert.equal(b.suggestedFilename(), 'ionity-vpn-jp-2.ovpn');
  assert.match(await read(b), /remote 203\.0\.113\.12 443/);
  assert.deepEqual(errs, []);
});

await test('desktop layout: two columns, nothing overflows', async (p) => {
  await ready(p);
  const w = await p.evaluate(() => document.documentElement.scrollWidth);
  assert.ok(w <= 1280, `no horizontal scroll (${w})`);
  assert.ok(await p.locator('.cola [data-act=desktop]').count(), 'desktop card in the left column');
}, { viewport: { width: 1280, height: 900 } });

await test('phone width 360: nothing overflows', async (p) => {
  await ready(p);
  assert.ok(await p.evaluate(() => document.documentElement.scrollWidth <= 360));
}, { viewport: { width: 360, height: 780 } });

await (async () => { // desktop/browser version: built-in list, no live fetch, .ovpn download
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 }, acceptDownloads: true });
  const p = await ctx.newPage(); const errs = []; p.on('pageerror', (e) => errs.push(e.message)); p.on('console', (m) => m.type() === 'error' && errs.push(m.text()));
  try {
    await p.goto(new URL('../www/index.html', import.meta.url).href);
    const snap = await p.evaluate(() => typeof SNAPSHOT !== 'undefined' && SNAPSHOT && SNAPSHOT.countries.length);
    if (!snap) { console.log('  - browser version: no built-in list in this build (run scripts/snapshot.mjs) — skipped'); return; }
    await p.waitForSelector('.opt:not(:disabled)');
    assert.match(await p.textContent('.note'), /Desktop version/);
    assert.equal(await p.locator('[data-act=reload]').count(), 0, 'no reload in a browser');
    const [dl] = await Promise.all([p.waitForEvent('download'), p.click('[data-act=power]')]);
    assert.match(dl.suggestedFilename(), /^ionity-vpn-[a-z]{2}\.ovpn$/);
    assert.doesNotMatch(await read(dl), /tls-cert-profile/);
    assert.deepEqual(errs, [], 'no errors (no blocked VPN Gate fetch)');
    console.log('  ✓ browser version: built-in list, no live fetch, hands out .ovpn files'); pass++;
  } catch (e) { console.log('  ✗ browser version', '\n     ', e.message.split('\n')[0]); fail++; } finally { await ctx.close(); }
})();

await browser.close(); srv.close();
console.log(`\n${pass} passed, ${fail} failed`);
process.exit(fail ? 1 : 0);
