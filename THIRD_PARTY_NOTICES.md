# Third-party notices

| Component | Licence | Used for |
|---|---|---|
| Capacitor (`@capacitor/core`, `/android`, `/cli`) incl. CapacitorHttp | MIT | Android shell, native HTTP |
| `@capacitor/app`, `/filesystem`, `/share`, `/haptics`, `/status-bar`, `/splash-screen` | MIT | Back button, files, share sheet, haptics, system bars, splash |
| Outfit, Plus Jakarta Sans, JetBrains Mono (latin subset) | SIL OFL 1.1 | Typography, embedded in `www/index.html` |
| OpenVPN for Android (ics-openvpn) v0.7.58 — `android/app/libs/*.aar`, see `tools/engine/` | GPL-2.0 (with additional terms, doc/LICENSE.txt) | In-app tunnel engine (`OpenVPNService`) |
| OpenVPN 2.x | GPL-2.0 (with OpenSSL linking exception) | Tunnel protocol |
| OpenSSL 3 | Apache-2.0 | Crypto for OpenVPN |
| LZO / LZ4 | GPL-2.0 / BSD-2-Clause | Compression support in OpenVPN |
| Playwright (dev only) | Apache-2.0 | e2e tests |
| AndroidX / Android Gradle Plugin | Apache-2.0 | Android build |

Services the app talks to directly:

- **VPN Gate** (University of Tsukuba) — public server list and volunteer servers behind "community" regions. Not affiliated with Ionity.
