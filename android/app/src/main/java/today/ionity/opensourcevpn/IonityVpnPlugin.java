/*
 * Ionity VPN — Capacitor bridge between the app's web layer and IonityVpnService.
 * Copyright (c) 2018-2026 Antwerp Designs | Ionity (Pty) Ltd — AEDI. GPL-3.0-or-later.
 */
package today.ionity.opensourcevpn;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;

import androidx.activity.result.ActivityResult;
import androidx.core.content.ContextCompat;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

/**
 * JS: connect({config, name}) · disconnect() · getStatus() · getLog() · addListener('state', e)
 * e.state: CONNECTING | CONNECTED | DISCONNECTED | ERROR; e.detail: OpenVPN 3 event name.
 */
@CapacitorPlugin(
    name = "IonityVpn",
    permissions = { @Permission(alias = "notifications", strings = { Manifest.permission.POST_NOTIFICATIONS }) }
)
public class IonityVpnPlugin extends Plugin {
    private String pendingConfig, pendingName;

    @Override
    public void load() {
        IonityVpnService.setListener((state, detail, message) -> {
            JSObject o = new JSObject();
            o.put("state", state); o.put("detail", detail); o.put("message", message);
            notifyListeners("state", o, true);
        });
    }

    @Override
    protected void handleOnDestroy() { IonityVpnService.setListener(null); }

    @PluginMethod
    public void connect(PluginCall call) {
        String config = call.getString("config");
        if (config == null || config.trim().isEmpty()) { call.reject("No connection file was given."); return; }
        pendingConfig = config;
        pendingName = call.getString("name", "Ionity VPN");
        if (Build.VERSION.SDK_INT >= 33 && getPermissionState("notifications") != PermissionState.GRANTED) {
            requestPermissionForAlias("notifications", call, "afterNotifications");
            return;
        }
        askVpnPermission(call);
    }

    @PermissionCallback
    private void afterNotifications(PluginCall call) { askVpnPermission(call); }

    private void askVpnPermission(PluginCall call) {
        Intent ask = VpnService.prepare(getContext());
        if (ask != null) { startActivityForResult(call, ask, "afterVpnPermission"); return; }
        start(call);
    }

    @ActivityCallback
    private void afterVpnPermission(PluginCall call, ActivityResult result) {
        if (result.getResultCode() == Activity.RESULT_OK) start(call);
        else call.reject("VPN permission denied");
    }

    private void start(PluginCall call) {
        Context ctx = getContext();
        Intent i = new Intent(ctx, IonityVpnService.class)
                .setAction(IonityVpnService.ACTION_CONNECT)
                .putExtra(IonityVpnService.EXTRA_CONFIG, pendingConfig)
                .putExtra(IonityVpnService.EXTRA_NAME, pendingName);
        pendingConfig = null;
        try { ContextCompat.startForegroundService(ctx, i); call.resolve(); }
        catch (Exception e) { call.reject("Could not start the VPN: " + e.getMessage()); }
    }

    @PluginMethod
    public void disconnect(PluginCall call) {
        Context ctx = getContext();
        try { ctx.startService(new Intent(ctx, IonityVpnService.class).setAction(IonityVpnService.ACTION_DISCONNECT)); }
        catch (Exception ignored) { }
        call.resolve();
    }

    @PluginMethod
    public void getStatus(PluginCall call) {
        JSObject o = new JSObject();
        o.put("state", IonityVpnService.state());
        o.put("detail", IonityVpnService.detail());
        o.put("engine", "openvpn3");
        call.resolve(o);
    }

    @PluginMethod
    public void getLog(PluginCall call) {
        JSArray lines = new JSArray();
        java.util.List<String> all = IonityVpnService.log();
        for (int i = Math.max(0, all.size() - 80); i < all.size(); i++) lines.put(all.get(i));
        JSObject o = new JSObject(); o.put("lines", lines); call.resolve(o);
    }
}
