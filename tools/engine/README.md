# Tunnel engine

`android/app/libs/ics-openvpn-0.7.58-skeleton-ovpn2.aar` is **OpenVPN for Android (ics-openvpn) v0.7.58**
by Arne Schwabe, GPLv2, built from <https://github.com/schwabe/ics-openvpn/tree/v0.7.58> with
`patch_ics.py` (turns the `:main` app module into a library; one source change: the keep-alive job in `keepVPNAlive.java` is non-persisted and cannot crash the VPN process), flavour
`skeleton` + `ovpn2` (OpenVPN 2.x + OpenSSL, no UI), ABIs arm64-v8a, armeabi-v7a, x86_64.

Rebuild: `tools\engine\build-engine.cmd` (writes the AAR into `android/app/libs/`).

Ionity's side is `android/app/src/main/java/today/ionity/opensourcevpn/IonityVpnPlugin.java`:
parse the `.ovpn` with `ConfigParser`, ask for VPN permission, start `OpenVPNService`, and relay
`VpnStatus` state to the web layer as `state` events.
