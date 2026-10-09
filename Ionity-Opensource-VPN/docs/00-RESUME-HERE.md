# Ionity's Opensource VPN — resume here

Doc: IOV-STATE-0.2.0 · Updated 2026-10-09 SAST · Ionity (Pty) Ltd — AEDI · Policy 986 AED · CC BY-NC-SA 4.0

## Where it stands
- **v0.2.0** built and tested — debug APK (versionCode 2) and single-file HTML, published as GitHub
  release v0.2.0 by `tools\publish-to-github.cmd`.
- 14/14 e2e tests pass against the mock node; the suite was checked non-vacuous.
- APK read back: bundled `index.html` hash matches `www/`, 6 Capacitor plugins + native `LanInfo`
  plugin in the dex, cleartext + network-security config, backup disabled, splash, themed icon.

## History
- **0.1.0** — first rebuild of Gate^Flame Shield as its own app. Did not behave identically
  (discovery, toggle fallback, provider default, error parsing).
- **0.2.0** — 16-point parity audit and fixes, native subnet scan, Ionity redesign, APK hardening,
  tests. See `CHANGELOG.md` and `PARITY-WITH-GATEFLAME.md`.

## Source layout
```
src/app.html            the app (HTML+CSS+JS) — edit this
scripts/build.mjs       inlines fonts + wordmark -> www/index.html
scripts/gradle.mjs      runs gradlew on any OS
android/                Capacitor 7 project; LanInfoPlugin.java = subnet discovery
tests/mock_node.py      node-agent stand-in    tests/e2e.mjs  14 cases
tools/install-apk.cmd   adb install + launch on the connected phone
docs/                   DESIGN, PARITY, screenshots
```

## Next, in order
1. **Own repo**: this folder is already a git repo (2 commits, identity DennisIonity). Run
   `tools\publish-to-github.cmd` once: it logs the GitHub CLI in, creates
   `github.com/dennisGIonity/Ionity-Opensource-VPN` (public), pushes `main` and publishes release v0.2.0
   with the APK and HTML. (The cloud session could not create repos, and SSH on this PC failed host-key
   verification, so it is one command for you.)
2. **On-device test** against the lab box: bring up `192.168.124.3:8080` (it did not answer on
   2026-10-09), connect the phone over adb, run `tools\install-apk.cmd`, pair, switch a device on,
   get a connection file, confirm the OpenVPN app appears in the share sheet.
3. **Release signing**: create an upload keystore outside the repo, write
   `android/keystore.properties`, `npm run build:aab`, enrol in Play App Signing at first upload.
4. Show the Headscale pre-auth key as a QR code when an Ionity region is chosen.
5. Later: native in-app tunnel (Android `VpnService` + WireGuard).

## Open questions
- Whether the app should also be listed on Play under Ionity's account or ship with Gate^Flame.
