# Ionity's Opensource VPN — design

Doc: IOV-DESIGN-0.1.0 · Ionity (Pty) Ltd — AEDI · Policy 986 AED · CC BY-NC-SA 4.0
Source of the design: Gate^Flame Shield (`node-agent/gateflame/vpn.py`, `vpngate.py`,
`docs/VPN-SHIELD-DESIGN.md`, `src/mobile/screens/ShieldScreen.tsx` in the Gate^Flame repo).

## What this is

A standalone, Ionity-branded front end for the per-device VPN that a Gate^Flame box already
serves. It ships as two packages from **one source file**:

- `src/app.html` — the whole app: HTML, CSS and JS in one file. `scripts/build.mjs` inlines the fonts and the Ionity wordmark into `www/index.html`, so the result has no network dependencies.
- `android/` — a Capacitor shell that wraps the same file into an APK.

The backend is the Gate^Flame **node-agent**. No server code lives in this repo.

## The rule everything rests on

**The box never carries tunnelled traffic.** It records which region each device should
use and hands that device a connection file; the tunnel runs from the device straight to
the exit server. That is what keeps ADR-001 intact (the standard box is a side-car) and why
"per device, per region, toggleable" costs nothing: each device is choosing independently.

## API used (node-agent, `http://<box>:8080/api/v1`)

| Call | Scope | Purpose |
|---|---|---|
| `GET /system/status` | none, LAN only | Confirms an address is a Gate^Flame box (`nodeId`) |
| `POST /pair/claim` `{code, deviceName}` | none | Exchanges the code on the box screen for a bearer token |
| `GET /vpn/regions` | read | Merged region list, `provider` = `headscale` or `vpngate` |
| `GET /vpn/continents` | read | VPN Gate grouped by continent, each resolved to its best country |
| `GET /vpn/devices` | read | Each device's stored choice |
| `PUT /vpn/devices/{mac}` `{region, enabled, provider}` | control | Set one device. Returns `lastError` rather than failing |
| `GET /vpn/devices/{mac}/vpngate-config` | read | Fresh `.ovpn` text for that device |
| `GET /clients`, `PUT /clients/{mac}/name` | read / control | The household device list and owner names |

A 401 to a request that carried the token means this handset was revoked: the app forgets
the box and returns to pairing.

## Why devices are picked from a list, not "this phone"

Mobile operating systems hide a phone's real MAC from app/web code. The box already knows
every device on the LAN by MAC, so the owner picks from that list — the same identity every
other Gate^Flame control uses.

## Honesty rules carried over from Gate^Flame

- Every VPN Gate region is labelled **community** and the app explains, one tap away, that
  it is best-effort and not a privacy guarantee (VPN Gate logs connection metadata; published
  research describes a MitM risk on volunteer nodes). Never call it "no-logs", "audited",
  "anonymous" or "private".
- "Could not ask the box", "the box is still fetching the list" and "the list came back
  empty" are three different messages, never one.
- A region list that fails never hides the device list; they load independently.
- The `.ovpn` is fetched fresh every time — VPN Gate's servers rotate.
- Demo mode exists so the app can be shown without a box. It carries a permanent
  "Demo — sample data" banner and its connection file says it will not connect.
- No bearer token is ever sent over plain HTTP to a non-private address (`assertPrivateHost`).

## Finding the box

1. The address it was found at last time. 2. The Gate^Flame candidate list, raced in parallel.
3. In the APK only: `LanInfoPlugin` (Java, `android/app/src/main/java/.../LanInfoPlugin.java`) reports
the phone's Wi-Fi/Ethernet IPv4 addresses, and the app probes all 254 hosts of that /24 on :8080,
40 at a time, accepting only a `/system/status` that returns `nodeId` and `agentVersion`.
A browser cannot learn its own LAN address, so the web build offers "scan a network range" instead.

## Not built (yet)

- **One-tap in-app tunnel.** Connecting hands the file to the device's OpenVPN app. A native
  tunnel needs an Android `VpnService` plugin with an embedded WireGuard/OpenVPN core.
- **Headscale onboarding UI.** When an Ionity region is chosen the box issues a pre-auth key
  (`peerRegistered: true`); the app reports it but does not yet render the key as a QR code.
- **Release signing / Play upload.** Only a debug APK is built here.
