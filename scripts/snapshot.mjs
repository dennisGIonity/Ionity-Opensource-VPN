// Downloads the live VPN Gate list and writes src/servers-snapshot.json: the best 3 servers per country.
// Built into the page by scripts/build.mjs — the app's first-run fallback and the list the desktop/browser
// version uses (browsers cannot read VPN Gate directly). Run before a release:  node scripts/snapshot.mjs
import { writeFileSync } from 'node:fs';
const CONT = {
  europe: 'AL AD AT BY BE BA BG HR CY CZ DK EE FO FI FR DE GI GR GG HU IS IE IM IT JE XK LV LI LT LU MT MD MC ME NL MK NO PL PT RO RU SM RS SK SI ES SE CH TR UA GB UK VA AX',
  asia: 'AF AM AZ BH BD BT BN KH CN GE HK IN ID IR IQ IL JP JO KZ KW KG LA LB MO MY MV MN MM NP KP OM PK PS PH QA SA SG KR LK SY TW TJ TH TL TM AE UZ VN YE',
  'north-america': 'AI AG AW BS BB BZ BM BQ VG CA KY CR CU CW DM DO SV GL GD GP GT HT HN JM MQ MX MS NI PA PR BL KN LC MF PM VC SX TT TC US VI',
  'south-america': 'AR BO BR CL CO EC FK GF GY PY PE SR UY VE',
  africa: 'DZ AO BJ BW BF BI CV CM CF TD KM CD CG CI DJ EG GQ ER SZ ET GA GM GH GN GW KE LS LR LY MG MW ML MR MU YT MA MZ NA NE NG RE RW SH ST SN SC SL SO ZA SS SD TZ TG TN UG EH ZM ZW',
  oceania: 'AS AU CK FJ PF GU KI MH FM NR NC NZ NU NF MP PW PG PN WS SB TK TO TV VU WF',
};
const contOf = (cc) => Object.keys(CONT).find((k) => CONT[k].split(' ').includes(cc)) || 'asia';
const FIX = { KR: 'South Korea', KP: 'North Korea', VN: 'Vietnam', RU: 'Russia', IR: 'Iran', TW: 'Taiwan', US: 'United States', GB: 'United Kingdom', MD: 'Moldova', LA: 'Laos', SY: 'Syria', TZ: 'Tanzania', BO: 'Bolivia', VE: 'Venezuela', CZ: 'Czechia' };
const text = await (await fetch(process.argv[2] || 'http://www.vpngate.net/api/iphone/')).text();
const by = new Map();
for (const line of text.split(/\r?\n/)) {
  if (!line || line[0] === '*' || line[0] === '#') continue;
  const f = line.split(','); if (f.length < 15) continue;
  const b64 = f[f.length - 1].trim(), cc = (f[6] || '').trim().toUpperCase();
  if (b64.length < 200 || !/^[A-Z]{2}$/.test(cc)) continue;
  if (!by.has(cc)) by.set(cc, { cc, name: FIX[cc] || f[5].trim() || cc, continent: contOf(cc), servers: [] });
  by.get(cc).servers.push({ host: f[0].trim(), ip: f[1].trim(), score: +f[2] || 0, ping: +f[3] || null, speed: +f[4] || 0, country: f[5].trim(), cc, sessions: +f[7] || 0, b64 });
}
const countries = [...by.values()].sort((a, b) => a.name.localeCompare(b.name));
countries.forEach((c) => { c.servers.sort((a, b) => b.score - a.score); c.servers = c.servers.slice(0, 3); });
if (!countries.length) throw new Error('empty list');
writeFileSync(new URL('../src/servers-snapshot.json', import.meta.url), JSON.stringify({ at: Date.now(), countries }));
console.log(`src/servers-snapshot.json: ${countries.length} countries, ${countries.reduce((n, c) => n + c.servers.length, 0)} servers`);
