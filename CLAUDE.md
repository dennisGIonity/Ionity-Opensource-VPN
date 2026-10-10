This project is **Ionity VPN** (appId `today.ionity.opensourcevpn`). Read `docs/00-RESUME-HERE.md` first.

What it is (from 0.3.0): a **standalone Android VPN**. The app reads the VPN Gate server list itself,
lets the user pick a continent (→ its best country) or an exact country, and runs the tunnel in-app
(Android `VpnService` + embedded OpenVPN engine, native plugin `IonityVpn`). No Gate^Flame box, no
discovery, no pairing. "Send to desktop" shares an HTML page that gives a computer its own `.ovpn`.
Ionity's own WireGuard regions come later.

Who is working: Dennis Grobler (Wabakipi), GitHub `dennisGIonity`. Johan Wilhelm van Antwerp is the
founder and appears in document headers only — never as a git author.

Decided:
- Standalone. Servers = VPN Gate now + Ionity's own later. Continent picker stays.
- Tunnel = one tap in-app. Licence = GPL-2.0 (OpenVPN 2.x is GPLv2-only).
- Android only; the only HTML besides the app layer is the desktop share page.

Working rules:
- Edit `src/app.html`; `node scripts/build.mjs` writes `www/index.html`; `npx cap sync android` copies it into the APK.
- `npm test` must pass before anything is called done.
- Keep CSS free of `color-mix()`/`light-dark()` (older Android WebViews).
- Test phone: Galaxy S10e (SM-G970F, Android 12) over wireless debugging, paired with wabakipi.
- Never accept a router password, keystore password or token in chat.
- Company documents use the official template and the AEDI header block.
