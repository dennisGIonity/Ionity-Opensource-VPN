# Parity with Gate^Flame Shield — audit 2026-10-09 (v0.2.0)

Doc: IOV-PARITY-0.2.0 · Ionity (Pty) Ltd — AEDI · Policy 986 AED · CC BY-NC-SA 4.0

v0.1.0 was rebuilt from Gate^Flame's Shield screen but did **not** behave identically.
This pass compared it line by line with the Gate^Flame sources it replaces and fixed every
difference. Each fix marked ✅test is pinned by a case in `tests/e2e.mjs`; the suite was checked
for non-vacuity by re-introducing the provider bug (the matching test fails).

| # | Behaviour | Gate^Flame source | v0.1.0 | v0.2.0 |
|---|---|---|---|---|
| 1 | Finding the box | `nodeDiscovery.ts`: last-known address, then a `Promise.any` race over 12 candidates (`config/env.ts`) | tried 2 `.local` names, one after the other | last-known → the same 12 candidates raced in parallel (+ lab `192.168.124.3`) → **APK: sweeps the phone's own /24** via a native plugin. ✅test |
| 2 | Probe validity | must return string `nodeId` **and** `agentVersion` | checked `nodeId` only | both, 2.5 s timeout as GF |
| 3 | Switching on a device with no region | uses `regionList[0]` immediately | opened a picker instead | first offered region, Ionity before community, exactly as GF. ✅test |
| 4 | Provider when a device has no row yet | `'headscale'` | `'vpngate'` on switch-off | `'headscale'`. ✅test |
| 5 | Switching off | sends the row's own provider | could send the wrong provider | row's provider. ✅test |
| 6 | Error text from the box | `detail` string → `advisory` → `applied.error` → `message` → `error` | handled 2 of 5 | all 5, in GF order |
| 7 | `lastError` on a 200 | shown to the owner | shown | shown. ✅test |
| 8 | 401 with a token sent | returns to pairing, token dropped | yes | yes. ✅test |
| 9 | Regions vs devices | independent polls; region failure never blanks devices | yes | yes, plus "showing what it last reported" banner. ✅test |
| 10 | Four empty states | could-not-ask / loading / fetching / empty, never merged | yes | yes, same wording |
| 11 | Default device name | `deviceName.ts` regex + UA-CH model refinement | simplified regex | GF regex + `getHighEntropyValues(['model'])` |
| 12 | Manual address | `http://` + `:8080` defaults, trailing slashes stripped | `URL()` port logic | GF string rules, GF error wording |
| 13 | Renaming a device not on the LAN now | allowed | disabled | allowed. ✅test (rename) |
| 14 | Connection file | fresh fetch each time; share sheet in app, download in browser | yes | yes. ✅test |
| 15 | Poll interval | 20 s, paused when hidden | yes | yes, plus refresh on app resume |
| 16 | Token to a public http address | refused (`assertPrivateHost`) | yes | yes. ✅test |

Deliberate differences (not bugs): Ionity branding and file names (`ionity-vpn-xx.ovpn`); request
timeout 8 s instead of 4 s (GF's own vpngate notes record `/vpn/regions` taking 24 s on a cold cache);
multiple boxes found on one network are listed to choose from instead of taking the first.

Not testable from here: a real handset against the real lab box. On 2026-10-09 the lab box
(`192.168.124.3:8080`) did not answer from the laptop, and no phone was attached to adb.
