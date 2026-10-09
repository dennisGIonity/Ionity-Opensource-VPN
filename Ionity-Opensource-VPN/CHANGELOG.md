# Changelog

## 0.2.0 — 2026-10-09
- **Parity with Gate^Flame Shield** — 16-point audit, every gap fixed (`docs/PARITY-WITH-GATEFLAME.md`).
- **Discovery**: last-known address, then the Gate^Flame candidate race, then (APK) a sweep of the phone's own subnet through a native `LanInfo` plugin. Manual "scan a network range" in both builds. Several boxes on one network can be chosen between.
- **Redesign** in the Ionity identity: Ionity wordmark, token colours, Outfit / Plus Jakarta Sans / JetBrains Mono bundled offline, `01 //`-numbered sections, status hero, community-coverage chart, desktop two-column layout with a side panel. No `color-mix()`/`light-dark()` so older Android WebViews render it correctly.
- **APK**: edge-to-edge margins for Android 15, dark status/navigation bars, Android 12+ system splash with the mark, themed (monochrome) launcher icon, haptic ticks, hardware back closes panels then backgrounds the app, refresh on resume, cloud backup disabled so the pairing token can't be restored onto another phone, release-signing hook (`android/keystore.properties`), versionCode 2.
- **Tests**: `tests/mock_node.py` (node-agent stand-in) + `tests/e2e.mjs` — 14 cases.

## 0.1.0 — 2026-10-09
- First build: single-file web app + Capacitor APK.
