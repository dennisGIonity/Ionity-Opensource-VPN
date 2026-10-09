# Third-party notices

| Component | Licence | Used for |
|---|---|---|
| Capacitor (`@capacitor/core`, `/android`, `/cli`) | MIT | Android shell around `www/index.html` |
| `@capacitor/app`, `/filesystem`, `/share`, `/haptics`, `/status-bar`, `/splash-screen` | MIT | Back button, connection file, share sheet, haptics, system bars, splash |
| Outfit, Plus Jakarta Sans, JetBrains Mono (via `@fontsource-variable`, latin subset) | SIL OFL 1.1 | Typography, embedded in `www/index.html` |
| Playwright (dev only) | Apache-2.0 | e2e tests |
| AndroidX / Android Gradle Plugin | Apache-2.0 | Android build |

Services the app does **not** bundle but talks to through the Gate^Flame box:

- **VPN Gate** (University of Tsukuba) — public relay list behind "community" regions. Not affiliated with Ionity.
- **Headscale** (BSD-3-Clause) — control plane behind "Ionity" regions, when deployed.
