/*
 * Ionity VPN — Capacitor bridge between the app's web layer and IonityVpnService.
 * Copyright (c) 2018-2026 Antwerp Designs | Ionity (Pty) Ltd — AEDI. GPL-3.0-or-later.
 */
package today.ionity.opensourcevpn;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import java.util.ArrayList;

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
 * JS: connect({servers:[{config, host}], name}) · disconnect() · getStatus() · getLog() · addListener('state', e)
 * e.state: CONNECTING | CONNECTED | RECONNECTING | DISCONNECTED | ERROR; e.detail: OpenVPN 3 event name;
 * e.server: the server in use; e.since: when the connection came up (ms).
 */
@CapacitorPlugin(
    name = "IonityVpn",
    permissions = { @Permission(alias = "notifications", strings = { Manifest.permission.POST_NOTIFICATIONS }) }
)
public class IonityVpnPlugin extends Plugin {
    // Static: Android may recreate the activity (and this plugin) while its VPN dialog is open.
    private static ArrayList<String> pendingConfigs, pendingHosts;
    private static String pendingName;

    @Override
    public void load() {
        IonityVpnService.setListener((state, detail, message, server) -> {
            JSObject o = new JSObject();
            o.put("state", state); o.put("detail", detail); o.put("message", message);
            o.put("server", server); o.put("since", IonityVpnService.since());
            notifyListeners("state", o, true);
        });
    }

    @Override
    protected void handleOnDestroy() { IonityVpnService.setListener(null); }

    @PluginMethod
    public void connect(PluginCall call) {
        ArrayList<String> configs = new ArrayList<>(), hosts = new ArrayList<>();
        try {
            JSArray list = call.getArray("servers");
            if (list != null) for (int i = 0; i < list.length(); i++) {
                org.json.JSONObject s = list.getJSONObject(i);
                String c = s.optString("config", "");
                if (!c.trim().isEmpty()) { configs.add(c); hosts.add(s.optString("host", "server " + (i + 1))); }
            }
        } catch (Exception ignored) { }
        String single = call.getString("config");
        if (configs.isEmpty() && single != null && !single.trim().isEmpty()) { configs.add(single); hosts.add("server"); }
        if (configs.isEmpty()) { call.reject("No connection file was given."); return; }
        pendingConfigs = configs; pendingHosts = hosts;
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
        if (pendingConfigs == null) { call.reject("The connection request was lost — tap Connect again."); return; }
        Intent i = new Intent(ctx, IonityVpnService.class)
                .setAction(IonityVpnService.ACTION_CONNECT)
                .putStringArrayListExtra(IonityVpnService.EXTRA_CONFIGS, pendingConfigs)
                .putStringArrayListExtra(IonityVpnService.EXTRA_HOSTS, pendingHosts)
                .putExtra(IonityVpnService.EXTRA_NAME, pendingName);
        pendingConfigs = null; pendingHosts = null;
        try { ContextCompat.startForegroundService(ctx, i); call.resolve(); }
        catch (Exception e) { call.reject("Could not start the VPN: " + e.getMessage()); return; }
        askBatteryOnce(ctx);
    }

    /**
     * Battery optimisation (Samsung's "sleeping apps" especially) can cut a long-running VPN.
     * Ask once to be left alone; the user can still say no.
     */
    private void askBatteryOnce(Context ctx) {
        try {
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            if (pm == null || pm.isIgnoringBatteryOptimizations(ctx.getPackageName())) return;
            android.content.SharedPreferences p = ctx.getSharedPreferences("ionity_vpn", Context.MODE_PRIVATE);
            if (p.getBoolean("askedBattery", false)) return;
            p.edit().putBoolean("askedBattery", true).apply();
            Intent ask = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.getPackageName()));
            if (getActivity() != null) getActivity().startActivity(ask);
        } catch (Exception ignored) { }
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
        o.put("server", IonityVpnService.server());
        o.put("since", IonityVpnService.since());
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
