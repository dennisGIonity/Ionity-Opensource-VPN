# Changelog

## 0.4.0 — 2026-10-10 — GPL-3.0, own engine
- **Licence → GPL-3.0-or-later.** ics-openvpn (GPL-2.0-only) is removed.
- **New tunnel engine: OpenVPN 3 core** (release/3.11, used under MPL-2.0) + OpenSSL 3.5.9 + asio + LZ4,
  built into `libionityvpn.so` by `engine/build-engine.sh`; SWIG bindings in `android/.../ovpn3/`.
- Ionity's own `EngineClient` (tun builder, socket protect, events, log), `IonityVpnService` (VpnService,
  foreground notification with Disconnect) and a rewritten `IonityVpnPlugin` (same JS API).
- Runs in the app's own process; no keep-alive job, no boot receiver, no exported control API.
- All 98 live VPN Gate configs checked against the OpenVPN 3 parser (0 errors).

## 0.3.1 — 2026-10-10 — connects on the phone
- **Fixed: the VPN engine crashed on every connect** ("app keeps failing"). ics-openvpn's keep-alive job was scheduled as *persisted*; Samsung's JobScheduler rejected it and killed the `:openvpn` process. `tools/engine/patch_ics.py` now makes it non-persisted and non-fatal; engine AAR rebuilt.
- In-app engine configs add `tls-cert-profile insecure` so OpenSSL 3 accepts VPN Gate's SoftEther certificates (desktop / hand-off files unchanged).
- Connection log: **Details** on a failed connect and **Settings → Connection log** (`IonityVpn.getLog()`).
- 30 s per server before trying the next.
- Verified on a Galaxy S10e (Android 12): connected in-app to a VPN Gate server in Japan.

## 0.3.0 — 2026-10-09 — standalone
- **No Gate^Flame box any more.** Box discovery, pairing, the household device list and the native `LanInfo` subnet scanner are gone.
- **Reads VPN Gate directly** (native HTTP, no CORS), parses the CSV, groups countries into six continents; continent tiles resolve to the best-scoring country right now; searchable country list. Last good list is cached and used when the feed is down, with its age shown.
- **One-tap connect.** Uses the native `IonityVpn` plugin when the build has the tunnel engine; tries the 3 best servers in the chosen country in turn; changing place while connected switches live. Without the engine, the `.ovpn` is handed to the OpenVPN app.
- **`.ovpn` patched for OpenVPN 2.6 / Connect 3** (`data-ciphers` + fallback for VPN Gate's AES-128-CBC).
- **Send to desktop**: a standalone HTML page (no network needed) with the best servers per country; Download / Next server per country; OpenVPN Connect steps.
- **Licence → GPL-2.0** (was CC BY-NC-SA 4.0), so the GPLv2 OpenVPN engine can be embedded.
- Cleartext HTTP now allowed only for `vpngate.net`. Web scheme `https`.
- Tests rewritten: 13 e2e cases against a mock VPN Gate feed and a mock engine.

## 0.2.0 — 2026-10-09
- Gate^Flame Shield companion: 16-point parity audit, native subnet discovery, Ionity redesign, APK hardening, 14 e2e tests.

## 0.1.0 — 2026-10-09
- First build: single-file web app + Capacitor APK.
