# Ionity VPN

Pick a continent or a country — whatever you connect to sees you there.

Ionity VPN is a standalone Android VPN. It downloads the live public server list from
[VPN Gate](https://www.vpngate.net) itself, picks the best server in the place you choose, and
runs the tunnel itself (Android `VpnService` + embedded OpenVPN engine). No account, no box,
no server of ours in the middle. A **Send to desktop** button gives a computer its own
connection file for OpenVPN Connect.

| | |
|---|---|
| App | `today.ionity.opensourcevpn` · "Ionity VPN" · Android 6.0+ |
| Source | `src/app.html` (UI) · `android/` (Capacitor shell + native tunnel plugin) |
| Licence | GPL-2.0 — see `LICENSE`. Ionity name and logo are trademarks (TM2). |
| Owner | Ionity (Pty) Ltd — AEDI · www.ionity.today · Policy 986 AED |

## Build
```
npm ci
npm test                    # e2e against a mock VPN Gate feed (Playwright)
npm run build:apk-debug     # android/app/build/outputs/apk/debug/app-debug.apk
tools\install-apk.cmd       # install + launch on the phone connected over adb
```

## How it works
1. **List** — `GET http://www.vpngate.net/api/iphone/` (CSV, one OpenVPN config per server), read natively.
2. **Pick** — continent tiles resolve to that continent's best-scoring country right now; or pick an exact country.
3. **Connect** — the best server in that country; if it does not answer within 25 s, the next best (up to 3).
4. **Desktop** — a self-contained HTML page with a snapshot of the best servers per country.

See `docs/STANDALONE-PLAN-0.3.md` and `CHANGELOG.md`.
