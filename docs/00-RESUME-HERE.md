# Ionity VPN — resume here

Doc: IOV-STATE-0.3.0 · Updated 2026-10-09 SAST · Ionity (Pty) Ltd — AEDI · Policy 986 AED · GPL-2.0

## Where it stands
- **0.3.1 — standalone Android VPN, connecting.** Verified 2026-10-10 on the Galaxy S10e: in-app tunnel to a
  VPN Gate server in Japan (tun0 up, Android VPN key shown). Reads VPN Gate itself, continent/country picker,
  one-tap connect via the native `IonityVpn` plugin over ics-openvpn v0.7.58 (`tools/engine/`), Send to desktop page,
  connection log. 14/14 e2e pass.
- If the phone's Developer options has "Select debug app" = Ionity VPN with "Wait for debugger", nothing starts — clear it.
- Decisions and phases: `docs/STANDALONE-PLAN-0.3.md`. 0.2.0 docs (DESIGN, PARITY, GATEFLAME-VPN-SHIELD-DESIGN) are history.

## Next, in order
1. **Bump the in-app footer version** (`APP.version` in `src/app.html` still says 0.3.0) with the next build.
2. **Publish**: `tools\publish-to-github.cmd <release-folder>` (GitHub CLI login once).
3. **Ionity regions**: own WireGuard servers + control API; engine choice per the licence note.
4. **Release signing** + Play listing (VpnService declaration).
