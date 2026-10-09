# Ionity's Opensource VPN

**Pick a country, one device at a time.** Every phone, laptop and TV in the house gets its own
VPN region — set from your phone, served by your Gate^Flame box.

Ionity (Pty) Ltd — AEDI · Policy 986 AED · CC BY-NC-SA 4.0 · https://www.ionity.today
(reference: https://www.ionity.co.za) · *Building Tomorrow, Today.*

| | |
|---|---|
| Web app | `www/index.html` — one self-contained file. Open it in a browser on the same network as the box. |
| Android | [Releases](https://github.com/dennisGIonity/Ionity-Opensource-VPN/releases) — debug-signed APK for sideload testing; CI builds one on every push |
| Backend | A Gate^Flame box running node-agent (`/api/v1/vpn/*`) — not in this repo |
| Design | `docs/DESIGN.md` · parity audit `docs/PARITY-WITH-GATEFLAME.md` · `CHANGELOG.md` |

## Features

- Pair with a Gate^Flame box using the code on its screen (or explore with clearly-labelled sample data).
- See every device on the network; switch a region on or off per device.
- Choose **Ionity regions** (Ionity-run exit servers, when deployed) or **community regions**
  (VPN Gate) by continent or exact country, with search.
- Get a connection file for a device — shared straight to the OpenVPN app on Android, downloaded in a browser.
- Rename devices; dark and light themes; works offline-first as a single file.

## Build

Requirements: Node 20+, JDK 21, Android SDK (platform 35, build-tools 35). Unset `NODE_ENV`
first, or `npm install` skips devDependencies (the Capacitor CLI).

```bash
npm install
npm run build:apk-debug     # src/app.html -> www/index.html -> cap sync -> app-debug.apk
npm test                    # needs: python3 tests/mock_node.py 8080   (in another shell)
tools\install-apk.cmd       # Windows: adb install + launch on the connected phone
```

Edit **`src/app.html`**, never `www/index.html` — `scripts/build.mjs` inlines the fonts and
wordmark and writes `www/`. A change is only real in the APK after `cap sync`: compare the hash
of `assets/public/index.html` inside the APK with `www/index.html`.

## Release (Google Play)

Not done yet: create `android/keystore.properties`, add a `signingConfigs.release` block, bump
`versionCode`/`versionName` in `android/app/build.gradle`, build `bundleRelease`, enrol in Play
App Signing at first upload.

## Licence

CC BY-NC-SA 4.0 — see `LICENSE`. Note that this licence restricts commercial use and is therefore
source-available rather than OSI open source. Ionity and Gate^Flame names and marks are not licensed.
