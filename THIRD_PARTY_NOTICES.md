# Third-party notices

Ionity VPN is GPL-3.0-or-later. Everything below is GPL-3.0-compatible.

| Component | Licence | Used for |
|---|---|---|
| OpenVPN 3 core `release/3.11` (OpenVPN Inc.) — in `libionityvpn.so` | MPL-2.0 (dual MPL-2.0 / AGPL-3.0; used under MPL-2.0) | Tunnel engine |
| SWIG-generated JNI bindings (`android/.../ovpn3/`, from OpenVPN 3's `ovpncli.i`) | MPL-2.0 | Java ↔ engine |
| OpenSSL 3.5.9 — static in `libionityvpn.so` | Apache-2.0 | TLS / crypto |
| asio 1.38.2 (standalone) | BSL-1.0 | Networking in OpenVPN 3 |
| LZ4 1.10.0 | BSD-2-Clause | Compression support |
| Capacitor (`@capacitor/core`, `/android`, `/cli`) incl. CapacitorHttp | MIT | Android shell, native HTTP, JS runtime |
| `@capacitor/app`, `/filesystem`, `/share`, `/haptics`, `/status-bar`, `/splash-screen` | MIT | Back button, files, share sheet, haptics, system bars, splash |
| Outfit, Plus Jakarta Sans, JetBrains Mono (latin subset) | SIL OFL 1.1 | Typography |
| AndroidX / Android Gradle Plugin | Apache-2.0 | Android build |
| Playwright (dev only) | Apache-2.0 | e2e tests |

Source for the engine: `engine/build-engine.sh` fetches the exact tags above and builds them.

Services the app talks to directly:

- **VPN Gate** (University of Tsukuba) — public server list and volunteer servers behind "community" regions. Not affiliated with Ionity.
