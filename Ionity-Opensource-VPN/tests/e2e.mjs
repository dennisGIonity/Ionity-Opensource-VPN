// End-to-end tests: the built www/index.html against tests/mock_node.py.
// Each case pins one Gate^Flame parity rule or one bug that has already happened.
// Run: python3 tests/mock_node.py 8080 &  then  node tests/e2e.mjs
// (Chromium from Playwright; set CHROMIUM=/path to override.)
import { chromium } from 'playwright';
import assert from 'node:assert/strict';
import { existsSync } from 'node:fs';

const APP = new URL('../www/index.html', import.meta.url).href;
const NODE = 'http://127.0.0.1:8080';
const ctl = async (p, body) => (await fetch(NODE + p, { method: body ? 'POST' : 'GET', body: body && JSON.stringify(body) })).json();
const puts = async (re) => (await ctl('/__log')).filter((x) => x.m === 'PUT' && re.test(x.p));

const exe = process.env.CHROMIUM || (existsSync('/opt/pw-browsers/chromium') ? '/opt/pw-browsers/chromium' : undefined);
const browser = await chromium.launch(exe ? { executablePath: exe } : {});
let pass = 0, fail = 0;
async function test(name, fn, viewport = { width: 400, height: 860 }) {
  await ctl('/__reset', {});
  const ctx = await browser.newContext({ viewport, acceptDownloads: true });
  const page = await ctx.newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push(e.message));
  try {
    await page.goto(APP);
    await fn(page);
    assert.deepEqual(errors, [], 'no page errors');
    console.log('  ✓', name); pass++;
  } catch (e) {
    console.log('  ✗', name, '\n     ', e.message.split('\n')[0]); fail++;
    await page.screenshot({ path: `/tmp/e2e-fail-${pass + fail}.png` }).catch(() => {});
  } finally { await ctx.close(); }
}
async function pair(page, code = '482913') {
  await page.fill('[name=address]', '127.0.0.1');
  await page.click('form[data-form=address] button');
  await page.waitForSelector('#code');
  await page.fill('#code', code);
  await page.click('form[data-form=claim] .btn.pri');
}
const row = (page, mac) => page.locator(`.dev:has([data-mac="${mac}"])`);

console.log('Ionity Open VPN — e2e');

await test('discovery: "Find my box" races the Gate^Flame candidate list and finds the node', async (p) => {
  await p.click('[data-act=discover]');
  await p.waitForSelector('#code', { timeout: 15000 });
  assert.match(await p.textContent('.found'), /GF-TEST01/);
});

await test('discovery: a network-range scan finds the node by sweeping a /24', async (p) => {
  await p.fill('[name=prefix]', '127.0.0');
  await p.click('form[data-form=scan] button');
  await p.waitForSelector('#code', { timeout: 30000 });
  assert.match(await p.textContent('.found'), /127\.0\.0\.1:8080/);
});

await test('pairing: a wrong code says so and does not pair; the right code pairs', async (p) => {
  await pair(p, '000000');
  await p.waitForSelector('.note.bad');
  assert.match(await p.textContent('.note.bad'), /not accepted/);
  await p.fill('#code', '482913');
  await p.click('form[data-form=claim] .btn.pri');
  await p.waitForSelector('.dev');
  const claim = (await ctl('/__log')).find((x) => x.p.endsWith('/pair/claim') && x.b.code === '482913');
  assert.ok(claim.b.deviceName && claim.b.deviceName.length <= 64, 'deviceName sent');
  assert.equal(claim.auth, null, 'claim is anonymous');
});

await test('[GF] toggle on with no region falls back to the FIRST offered region (headscale first)', async (p) => {
  await pair(p); await p.waitForSelector('.dev');
  await row(p, 'aa:bb:cc:00:00:01').locator('.sw').click();
  await p.waitForFunction(() => document.querySelector('.dev.on'));
  const [put] = await puts(/aa%3Abb/);
  assert.deepEqual(put.b, { region: 'za', enabled: true, provider: 'headscale' });
  assert.match(await row(p, 'aa:bb:cc:00:00:01').textContent(), /South Africa/);
});

await test('[GF] with no Ionity regions the fallback is the first community country', async (p) => {
  await ctl('/__set', { headscale: false });
  await pair(p); await p.waitForSelector('.dev');
  await row(p, 'aa:bb:cc:00:00:01').locator('.sw').click();
  await p.waitForFunction(() => document.querySelector('.dev.on'));
  const [put] = await puts(/aa%3Abb/);
  assert.deepEqual(put.b, { region: 'DE', enabled: true, provider: 'vpngate' });
});

await test('[GF] picking a continent stores its best country on the vpngate provider; toggling off keeps the provider', async (p) => {
  await ctl('/__set', { headscale: false });
  await pair(p); await p.waitForSelector('.dev');
  await row(p, 'aa:bb:cc:00:00:01').locator('.sw').click();
  await p.waitForFunction(() => document.querySelector('.dev.on'));
  await row(p, 'aa:bb:cc:00:00:01').locator('.tap').click();
  await p.click('[data-act=tab][data-v=continent]');
  await p.click('.opt:has-text("Asia")');
  await p.waitForFunction(() => /Japan/.test(document.querySelector('.state')?.textContent || ''));
  await p.locator('#sheet .state .sw').click();
  await p.waitForFunction(() => !document.querySelector('.dev.on'));
  const all = await puts(/aa%3Abb/);
  assert.deepEqual(all[1].b, { region: 'JP', enabled: true, provider: 'vpngate' });
  assert.deepEqual(all[2].b, { region: 'JP', enabled: false, provider: 'vpngate' });
});

await test('[GF] lastError on a 200 is shown, not swallowed (offline Ionity region)', async (p) => {
  await pair(p); await p.waitForSelector('.dev');
  await row(p, 'aa:bb:cc:00:00:01').locator('.sw').click();
  await p.waitForFunction(() => document.querySelector('.dev.on'));
  await row(p, 'aa:bb:cc:00:00:01').locator('.tap').click();
  // "uk" is listed but offline -> disabled tile; force the request the way an old UI would
  await p.evaluate(() => setDevice('aa:bb:cc:00:00:01', 'uk', true, 'headscale'));
  await p.waitForSelector('.note.bad');
  assert.match(await p.textContent('.note.bad'), /not reachable right now/);
});

await test('connection file: fetched fresh and handed off (download in a browser)', async (p) => {
  await ctl('/__set', { headscale: false });
  await pair(p); await p.waitForSelector('.dev');
  await row(p, 'aa:bb:cc:00:00:01').locator('.sw').click();
  await p.waitForFunction(() => document.querySelector('.dev.on'));
  await row(p, 'aa:bb:cc:00:00:01').locator('.tap').click();
  const [dl] = await Promise.all([p.waitForEvent('download'), p.click('[data-act=config]')]);
  assert.equal(dl.suggestedFilename(), 'ionity-vpn-de.ovpn');
  await p.waitForSelector('.spec');
  assert.match(await p.textContent('.spec'), /vpn123\.opengw\.net/);
});

await test('rename: sends the owner name and the list re-reads the box', async (p) => {
  await pair(p); await p.waitForSelector('.dev');
  await row(p, 'da:aa:bb:00:00:02').locator('.tap').click();
  await p.fill('#sheet [name=name]', "Kyle's phone");
  await p.click('#sheet form[data-form=rename] button');
  await p.waitForFunction(() => /Kyle's phone/.test(document.querySelector('.panel').textContent));
  const [put] = await puts(/\/name$/);
  assert.deepEqual(put.b, { name: "Kyle's phone" });
});

await test('[GF] a failed region list never blanks the device list', async (p) => {
  await ctl('/__set', { regionsFail: true });
  await pair(p); await p.waitForSelector('.dev');
  assert.equal(await p.locator('.dev').count(), 2);
  await row(p, 'aa:bb:cc:00:00:01').locator('.sw').click(); // still usable
  await p.waitForFunction(() => document.querySelector('.dev.on'));
});

await test('[GF] a 401 on a request that carried the token returns the app to pairing', async (p) => {
  await pair(p); await p.waitForSelector('.dev');
  await ctl('/__set', { token: 'rotated' });
  await p.click('[data-act=refresh]');
  await p.waitForSelector('[data-act=discover]');
  assert.equal(await p.evaluate(() => localStorage.getItem('iov.token')), null);
});

await test('security: a token is never sent over http to a public address', async (p) => {
  await p.evaluate(() => { S.mode = 'live'; S.token = 't'; S.base = 'http://8.8.8.8:8080'; });
  const err = await p.evaluate(() => api('/vpn/devices').then(() => 'sent', (e) => e.message));
  assert.match(err, /not on a private network/);
});

await test('desktop: choosing a device opens it in the side panel, not a sheet', async (p) => {
  await pair(p); await p.waitForSelector('.dev');
  await row(p, 'aa:bb:cc:00:00:01').locator('.tap').click();
  await p.waitForSelector('#side .ch-h');
  assert.equal(await p.evaluate(() => document.querySelector('#sheet').open), false);
}, { width: 1360, height: 900 });

await test('demo mode is always labelled as sample data', async (p) => {
  await p.click('[data-act=demo]');
  await p.waitForSelector('.dev');
  assert.match(await p.textContent('.note.warn'), /Demo — sample data/);
});

await browser.close();
console.log(`\n${pass} passed, ${fail} failed`);
process.exit(fail ? 1 : 0);
