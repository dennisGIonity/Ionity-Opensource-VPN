# Gate^Flame Shield — design notes

Per-device, per-region VPN, on both editions, for R0. This is the "how and
why" doc `node-agent/gateflame/vpn.py` and `vpngate.py` both point at.

## The constraint everything else is built around

ADR-001 (`docs/ADR-001-DNS-AUTHORITY-MODEL.md`) commits the standard box to
being a side-car: household traffic never passes through it. A VPN feature
that routed some devices' traffic through the box to tunnel it out would
quietly break that promise for exactly the devices using it. So Shield never
carries a single packet of tunnelled traffic on either edition — the box only
tells a device WHICH region to use and hands it a config; the tunnel itself
runs device-to-exit-server directly. "Per device, per region, toggleable"
falls out of that for free, because each device is just picking its own
region independently of the box.

A second, separate constraint: a phone's own MAC address is hidden from
web/webview JavaScript by every current mobile OS, for privacy reasons. So
Shield cannot ask "protect this device" of the handset it's running on — the
mobile screen instead lets the owner pick from the box's own already-
discovered LAN client list (`clients.py`), the same MAC-keyed identity every
other per-device control in this app already uses.

## Two providers, one screen, honestly distinguished

Dennis's ask was explicit: a customer picks their own country, on either
edition, for zero budget. There is no way to get real, free, per-country
server presence without a real trade-off, so Shield ships with **two**
provider backends behind one UI, and the `provider` field on every region and
every device row is what keeps them from ever being confused with each other
in code, storage, or the API — even though the customer-facing name for the
whole feature stays "Gate^Flame Shield" on both.

### `headscale` — Ionity's own, once it exists

`vpn.py` talks to a self-hosted Headscale server (open source, MIT). It is
the control plane only — it hands out WireGuard peer configs and knows which
tagged exit node sits in which country. The actual exit nodes (real servers
with real public IPs) are not something this repo can stand up; that is real
infrastructure, under Ionity's own account, one small VPS per region — see
`infra/headscale/README.md` for the actual zero-budget path there (Oracle
Cloud's Always Free tier, one region per account, real caveats included).
Until at least one exists and is registered as a tagged exit node,
`vpn.list_regions()` returns `[]` honestly.

Onboarding a device onto this path needs the Tailscale-compatible client app
(not a plain WireGuard app — Headscale's protocol is Tailscale's own control
protocol), given a login-server URL and the pre-auth key `vpn.py` issues.

### `vpngate` — free today, real countries today, best-effort by design

`vpngate.py` reads the public, free VPN Gate relay list
(`http://www.vpngate.net/api/iphone/`), published in real time by the
University of Tsukuba as an ongoing academic research project. This is what
actually answers "pick a country, right now, for zero budget" — hundreds of
volunteer-run servers spread across dozens of countries, no signup, no
infrastructure Ionity runs or pays for.

**Read this before this ever reaches marketing copy.** VPN Gate is an
academic experiment, not audited privacy infrastructure, and its own
operators say so:

- VPN Gate's own published policy logs connection metadata centrally
  (timestamps, source/destination IPs, protocol, traffic volume, destination
  hostnames — not payloads) for a period measured in months, as an anti-abuse
  condition of the project existing at all on donated volunteer bandwidth.
- Peer-reviewed research, "On Man-in-the-Middle Attack Risks of the VPN Gate
  Relay System," documents that because VPN Gate volunteer nodes can share
  TLS material, a malicious node operator has a theoretical path to intercept
  a session. That is a published finding about this specific network, not a
  hypothetical concern.

Because safety and privacy is this product's actual sales pitch, Shield's
VPN Gate path must never be described anywhere — UI copy, marketing,
support scripts — as "audited," "no-logs," "anonymous," or "private." That
claim would be false about the network underneath it. The framing this repo
uses instead is **best-effort community region access**: genuinely useful
for a geography-restricted site or a quick country change, never sold as a
trust guarantee. The `headscale` path above is the honest answer for a
customer who needs the trust guarantee — once Ionity's own exit servers
exist.

Concretely, this repo prefers the highest-`Score` server VPN Gate reports
for a country (a rough long-run reliability signal VPN Gate itself computes),
never invents or caches a stale entry once the live list has moved on, and
labels every vpngate-sourced region in the API and UI with `"provider":
"vpngate"` plus a small "· community" tag in the region chip — small,
honest, not alarmist, not hidden.

Onboarding a device onto this path is a config handoff, not an in-app
tunnel: `GET /api/v1/vpn/devices/{mac}/vpngate-config` fetches the current
best server for that device's chosen country live (never cached — VPN Gate's
list genuinely rotates), decodes its OpenVPN config, and the mobile screen
hands the file to the OS's own share sheet (`@capacitor/filesystem` +
`@capacitor/share`, both official Capacitor plugins - no bespoke native code)
so the owner picks their installed OpenVPN app directly, rather than hunting
through Downloads for a file they don't recognise. Falls back to a plain
browser download if those plugins aren't available in the current context
(e.g. a desktop browser preview). See "What isn't built yet" below for why
this is a handoff rather than a fully embedded tunnel today.

**Picking a continent instead of a country.** Fifteen individual European
countries is a worse product than one "Europe" tile, so
`GET /api/v1/vpn/continents` groups VPN Gate's live country list by
continent and already resolves each one to its own current best-scoring
country (`bestCountryCode`). This is display-and-selection sugar only - no
new storage concept exists for it. Tapping a continent tile calls the exact
same `apply_device_region`/`setVpnDevice` path as tapping a specific country
directly; a "choose an exact country instead" toggle underneath still
reaches the full per-country list for anyone who needs a specific one (BBC
iPlayer wants the UK specifically, not "wherever in Europe scores highest").

## What isn't built yet, and why

**Note on verification:** the continent grouping and the storage/API changes
underneath it were exercised directly (real CSV parsing, real grouping, real
route registration) the same way the rest of this feature was. The share-
sheet handoff itself was only verified by type-checking
(`tsc --noEmit`) - `@capacitor/filesystem` and `@capacitor/share` were not
run on an actual Android/iOS build in this pass, so the on-device behaviour
(does the share sheet actually list an installed OpenVPN app, does the
cache-directory file get cleaned up sensibly) needs confirming on a real
device before this ships, not assumed from the code alone.

**A fully in-app, one-tap tunnel.** Gate^Flame's mobile shell is a Capacitor/
React app (see `src/mobile/`), not a native VPN client. Embedding a real
system-level tunnel (Android `VpnService`, iOS `NEPacketTunnelProvider`)
needs a genuine native plugin — platform entitlements, a foreground service
declaration, an iOS network extension target with its own App Group — which
is real native engineering, not something a webview can do on its own. What
ships today is the config handoff described above: Gate^Flame's own screen
does 100% of the picking, toggling, and branding, and only the last step
(actually opening the tunnel) hands off to the OS's own already-installed
WireGuard or OpenVPN client — both free, open source, and neutral system
plumbing the same way "Share via…" is, not a break in Gate^Flame's own
branding on the screen the customer actually uses.

**Cloudflare WARP / `wgcf` as a trusted always-on default.** WARP is
free, backed by Cloudflare's own audited anycast network rather than
volunteer nodes, and genuinely trustworthy in a way VPN Gate is not — but it
routes to the nearest Cloudflare edge automatically and does not let a free
account pick an arbitrary exit country, so it does not serve Dennis's actual
ask on its own. It is the natural next addition as a third, always-safe
"Shield: Fast" default sitting alongside the country picker, not a
replacement for VPN Gate — flagged here as a fast-follow, not built this
pass.

**A community/customer exit-node network.** Longer-term, once there are
paying customers in more than one country, their own Gate^Flame boxes could
volunteer (opt-in only) as Headscale exit nodes for each other — real zero
marginal cost, scaling with the customer base instead of Ionity's budget. Real
problems to solve before it ships, not after: a customer's home ISP's
acceptable-use policy may not permit acting as a VPN exit at all, and someone
else's traffic exiting through a customer's home IP is a different liability
conversation than a private point-to-point tunnel — needs its own consent
flow and an immediate kill switch, not a checkbox buried in settings.

## Where the code lives

```
node-agent/gateflame/vpn.py       Headscale provider + the merged region list
node-agent/gateflame/vpngate.py   VPN Gate provider (fetch, parse, cache, decode)
node-agent/gateflame/storage.py   vpn_devices table, keyed by MAC + provider
node-agent/gateflame/main.py      /api/v1/vpn/* routes
src/types/vpn.ts                  API contract, mirrors vpn.py exactly
src/components/kiosk/kioskClient.ts   setVpnDevice / getVpnGateConfig
src/mobile/screens/ControlsScreen.tsx ShieldCard — the actual owner-facing UI
infra/headscale/                  control-plane deploy + the honest R0 path for exit nodes
```
