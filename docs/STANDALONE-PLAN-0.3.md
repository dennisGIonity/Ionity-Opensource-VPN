# Ionity VPN 0.3 — standalone plan

Doc: IOV-PLAN-0.3.0 · Updated 2026-10-09 SAST · Ionity (Pty) Ltd — AEDI · Policy 986 AED

## Decision (2026-10-09, supersedes "Backend = Gate^Flame companion")
Ionity VPN is a **standalone Android VPN**: whatever and whoever you connect to sees you in the
country you choose. No Gate^Flame box, no discovery, no pairing. The phone fetches its own server list
and runs its own tunnel.

| Topic | Decided |
|---|---|
| Servers | **Both**: VPN Gate (read directly by the app) now; Ionity's own WireGuard regions when the servers exist |
| Picking | Continent tiles (each resolves to its best country right now) + exact-country list, as many continents as there are live servers |
| Tunnel | **One tap inside the app** — Android `VpnService` + embedded OpenVPN engine (ics-openvpn, skeleton/ovpn2) |
| Desktop | "Send to desktop" shares a self-contained `.html`: snapshot of the best servers per country, the desktop downloads its **own** `.ovpn` for OpenVPN Connect |
| Web build | Dropped as a product |
| Licence | **GPL-3.0-or-later** (from 0.4.0: engine is OpenVPN 3 core, MPL-2.0). Ionity name and logo stay trademarks |

## Licence notes
- WireGuard's Android tunnel library is Apache-2.0 (treated as GPLv2-incompatible by the FSF): pick the engine for Ionity regions when those servers exist (wireguard-go is MIT).
- ics-openvpn: open-source GPL use is fine; closed-source UIs need the author's paid licence.

## Phases
1. App without a box (JS) — done in 0.3.0.
2. Desktop share page — done in 0.3.0.
3. In-app tunnel — `IonityVpn` Capacitor plugin over ics-openvpn (`connect(config,name)`, `disconnect()`, `getStatus()`, `state` events: CONNECTING / CONNECTED / DISCONNECTED / ERROR).
4. Ionity regions — WireGuard control API + servers.
5. Release signing + Play listing (VpnService declaration).
