# Tunnel engine (0.4.0+)

`android/app/src/main/jniLibs/<abi>/libionityvpn.so` = **OpenVPN 3 core** (`release/3.11`, used under
MPL-2.0) + **OpenSSL 3.5.9** (static) + asio + LZ4 + the SWIG JNI glue. The Java bindings it exports
are generated into `android/app/src/main/java/today/ionity/opensourcevpn/ovpn3/`.

Ionity's side (GPL-3.0-or-later):
- `EngineClient.java` — subclasses `ClientAPI_OpenVPNClient`; builds the tun interface with
  `VpnService.Builder`, protects the engine's sockets, relays events and log lines.
- `IonityVpnService.java` — the `VpnService`: runs one session on a worker thread, foreground notification
  with Disconnect, state + log for the app.
- `IonityVpnPlugin.java` — Capacitor bridge: `connect`, `disconnect`, `getStatus`, `getLog`, `state` events.

Rebuild (Linux or WSL, with an Android NDK r27+, cmake, ninja, perl, swig 4):

```
ANDROID_NDK_ROOT=/path/to/android-ndk engine/build-engine.sh
```

Replaces ics-openvpn (GPL-2.0-only), which was used up to 0.3.1.
