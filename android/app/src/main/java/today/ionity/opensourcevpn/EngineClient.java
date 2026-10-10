/*
 * Ionity VPN — OpenVPN 3 client bridge.
 * Copyright (c) 2018-2026 Antwerp Designs | Ionity (Pty) Ltd — AEDI. GPL-3.0-or-later.
 */
package today.ionity.opensourcevpn;

import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import java.net.InetAddress;
import java.util.Map;

import today.ionity.opensourcevpn.ovpn3.ClientAPI_AppCustomControlMessageEvent;
import today.ionity.opensourcevpn.ovpn3.ClientAPI_Config;
import today.ionity.opensourcevpn.ovpn3.ClientAPI_EvalConfig;
import today.ionity.opensourcevpn.ovpn3.ClientAPI_ExternalPKICertRequest;
import today.ionity.opensourcevpn.ovpn3.ClientAPI_ExternalPKISignRequest;
import today.ionity.opensourcevpn.ovpn3.ClientAPI_Event;
import today.ionity.opensourcevpn.ovpn3.ClientAPI_LogInfo;
import today.ionity.opensourcevpn.ovpn3.ClientAPI_OpenVPNClient;
import today.ionity.opensourcevpn.ovpn3.ClientAPI_Status;
import today.ionity.opensourcevpn.ovpn3.DnsAddress;
import today.ionity.opensourcevpn.ovpn3.DnsDomain;
import today.ionity.opensourcevpn.ovpn3.DnsOptions;
import today.ionity.opensourcevpn.ovpn3.DnsServer;

/**
 * One tunnel session. OpenVPN 3 calls back into this object to build the Android tun
 * interface (VpnService.Builder), to protect its own sockets from the tunnel, and to
 * report events and log lines. connect() blocks until the session ends.
 */
final class EngineClient extends ClientAPI_OpenVPNClient {
    static { System.loadLibrary("ionityvpn"); }

    interface Listener {
        void onEvent(String name, String info, boolean error, boolean fatal);
        void onLog(String line);
    }

    private final VpnService service;
    private final Listener listener;
    private final String sessionName;
    private VpnService.Builder builder;
    private boolean hasV6;

    EngineClient(VpnService service, String sessionName, Listener listener) {
        this.service = service;
        this.sessionName = sessionName;
        this.listener = listener;
    }

    /** Parses the .ovpn; returns null when it is usable, otherwise the reason. */
    String load(String ovpn) {
        ClientAPI_Config c = new ClientAPI_Config();
        c.setContent(ovpn);
        c.setGuiVersion("Ionity VPN");
        c.setConnTimeout(30);
        c.setTunPersist(false);
        c.setCompressionMode("asym");
        // VPN Gate (SoftEther) uses AES-128-CBC / SHA1 and older certificates. Those are in OpenSSL's default
        // provider; "legacy algorithms" (BF-CBC etc.) would need the legacy provider, which this build leaves out.
        c.setEnableLegacyAlgorithms(false);
        c.setEnableNonPreferredDCAlgorithms(true);
        c.setTlsCertProfileOverride("insecure");
        c.setAllowLocalLanAccess(false);
        c.setDco(false);
        ClientAPI_EvalConfig ev = eval_config(c);
        return ev.getError() ? ev.getMessage() : null;
    }

    /** Runs the session; returns when it ends. */
    ClientAPI_Status run() { return connect(); }

    /* ---------- tun builder ---------- */
    @Override public boolean tun_builder_new() {
        builder = service.new Builder();
        hasV6 = false;
        return true;
    }
    @Override public boolean tun_builder_set_remote_address(String address, boolean ipv6) { return true; }
    @Override public boolean tun_builder_add_address(String address, int prefix, String gateway, boolean ipv6, boolean net30) {
        try { builder.addAddress(address, prefix); if (ipv6) hasV6 = true; return true; }
        catch (Exception e) { log("add_address " + address + "/" + prefix + ": " + e.getMessage()); return false; }
    }
    @Override public boolean tun_builder_reroute_gw(boolean ipv4, boolean ipv6, long flags) {
        try {
            if (ipv4) builder.addRoute("0.0.0.0", 0);
            // Route all IPv6 into the tunnel as well, so IPv6 cannot go around it.
            if (ipv6 || ipv4) builder.addRoute("::", 0);
            return true;
        } catch (Exception e) { log("reroute_gw: " + e.getMessage()); return false; }
    }
    @Override public boolean tun_builder_add_route(String address, int prefix, int metric, boolean ipv6) {
        try { builder.addRoute(address, prefix); return true; }
        catch (Exception e) { log("add_route " + address + "/" + prefix + ": " + e.getMessage()); return true; }
    }
    @Override public boolean tun_builder_exclude_route(String address, int prefix, int metric, boolean ipv6) {
        if (Build.VERSION.SDK_INT >= 33) {
            try { builder.excludeRoute(new android.net.IpPrefix(InetAddress.getByName(address), prefix)); }
            catch (Exception e) { log("exclude_route " + address + ": " + e.getMessage()); }
        }
        return true;
    }
    @Override public boolean tun_builder_set_dns_options(DnsOptions dns) {
        try {
            for (Map.Entry<Integer, DnsServer> e : dns.getServers().entrySet()) {
                for (DnsAddress a : e.getValue().getAddresses()) {
                    try { builder.addDnsServer(a.getAddress()); } catch (Exception ex) { log("dns " + a.getAddress() + ": " + ex.getMessage()); }
                }
            }
            for (DnsDomain d : dns.getSearch_domains()) {
                try { builder.addSearchDomain(d.getDomain()); } catch (Exception ignored) { }
            }
        } catch (Exception e) { log("dns options: " + e.getMessage()); }
        return true;
    }
    @Override public boolean tun_builder_set_mtu(int mtu) { builder.setMtu(mtu); return true; }
    @Override public boolean tun_builder_set_session_name(String name) { builder.setSession(sessionName); return true; }
    @Override public boolean tun_builder_add_proxy_bypass(String host) { return true; }
    @Override public boolean tun_builder_set_proxy_auto_config_url(String url) { return true; }
    @Override public boolean tun_builder_set_proxy_http(String host, int port) { return true; }
    @Override public boolean tun_builder_set_proxy_https(String host, int port) { return true; }
    @Override public boolean tun_builder_add_wins_server(String address) { return true; }
    @Override public int tun_builder_establish() {
        try {
            builder.setSession(sessionName);
            if (Build.VERSION.SDK_INT >= 29) builder.setMetered(false);
            ParcelFileDescriptor pfd = builder.establish();
            if (pfd == null) { log("Android refused to create the VPN interface (VPN permission revoked?)"); return -1; }
            return pfd.detachFd(); // OpenVPN 3 owns and closes the fd from here

        } catch (Exception e) { log("establish: " + e.getMessage()); return -1; }
    }

    /* ---------- the rest ---------- */
    @Override public boolean socket_protect(int socket, String remote, boolean ipv6) { return service.protect(socket); }
    @Override public void event(ClientAPI_Event ev) { listener.onEvent(ev.getName(), ev.getInfo(), ev.getError(), ev.getFatal()); }
    @Override public void log(ClientAPI_LogInfo li) { listener.onLog(li.getText().trim()); }
    @Override public boolean pause_on_connection_timeout() { return false; }
    // Pure virtual in OpenVPN 3: must be implemented even though this app uses none of them.
    @Override public void acc_event(ClientAPI_AppCustomControlMessageEvent ev) { }
    @Override public void external_pki_cert_request(ClientAPI_ExternalPKICertRequest req) { req.setError(true); req.setErrorText("not supported"); }
    @Override public void external_pki_sign_request(ClientAPI_ExternalPKISignRequest req) { req.setError(true); req.setErrorText("not supported"); }

    private void log(String s) { listener.onLog(s); }
}
