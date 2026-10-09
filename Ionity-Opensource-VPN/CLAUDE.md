
This project is **Ionity's Opensource VPN** (app label "Ionity VPN", appId `today.ionity.opensourcevpn`).
Read `docs/00-RESUME-HERE.md` before anything else.

What it is: an Android app (.apk) and a single-file web app (.html) that give every device on a
home network its own VPN region, served by a **Gate^Flame box** (node-agent `/api/v1/vpn/*`,
`/clients`, `/pair/claim` on port 8080). It is a companion to Gate^Flame, not a standalone VPN.
The box never carries tunnelled traffic; it records each device's region and hands that device a
connection file.

Who is working: Dennis Grobler (Wabakipi), GitHub `dennisGIonity`. Johan Wilhelm van Antwerp is the
founder and appears in document headers only — never as a git author.

Decided — do not re-litigate:
- Backend = Gate^Flame companion. Licence = CC BY-NC-SA 4.0 (source-available, not OSI).
- Tunnel = hand the .ovpn to the phone's OpenVPN app. No native in-app tunnel yet.
- Behaviour must match Gate^Flame Shield exactly (`docs/PARITY-WITH-GATEFLAME.md`). Any change to that
  behaviour needs a test in `tests/e2e.mjs`.
- Community (VPN Gate) regions are never described as private, audited, no-logs or anonymous.

Working rules:
- Edit `src/app.html` only; `node scripts/build.mjs` writes `www/index.html`. A change is real in the
  APK only after `cap sync` — compare the hash of `assets/public/index.html` in the APK.
- Run `npm test` (with `python3 tests/mock_node.py 8080` running) before calling anything done.
- Keep CSS free of `color-mix()`/`light-dark()` (older Android WebViews).
- Lab box: `wabapi@192.168.124.3`, lab router `192.168.124.1`. Household TP-Link `192.168.0.1` is
  internet only — never test there.
- Never accept a router password, keystore password or token in chat.
- Company documents use the official template and the AEDI header block.
