package today.ionity.opensourcevpn;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.VpnService;
import android.os.Build;
import android.os.IBinder;

import androidx.activity.result.ActivityResult;

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

import java.io.StringReader;

import de.blinkt.openvpn.R;
import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.ConfigParser;
import de.blinkt.openvpn.core.ConnectionStatus;
import de.blinkt.openvpn.core.IOpenVPNServiceInternal;
import de.blinkt.openvpn.core.LogItem;
import de.blinkt.openvpn.core.OpenVPNService;
import de.blinkt.openvpn.core.ProfileManager;
import de.blinkt.openvpn.core.VPNLaunchHelper;
import de.blinkt.openvpn.core.VpnStatus;

/**
 * Ionity VPN tunnel engine bridge. Runs an OpenVPN connection inside this app
 * (Android VpnService, via the embedded ics-openvpn engine, GPLv2).
 *
 * JS: connect({config, name}) · disconnect() · getStatus() · addListener('state', e)
 * e.state is one of CONNECTING | CONNECTED | DISCONNECTED | ERROR; e.detail is OpenVPN's own state word.
 */
@CapacitorPlugin(
    name = "IonityVpn",
    permissions = { @Permission(alias = "notifications", strings = { Manifest.permission.POST_NOTIFICATIONS }) }
)
public class IonityVpnPlugin extends Plugin implements VpnStatus.StateListener {
    private volatile String lastState = "DISCONNECTED";
    private volatile String lastDetail = "";
    private VpnProfile pending;

    @Override
    public void load() {
        VpnStatus.addStateListener(this);
    }

    @Override
    protected void handleOnDestroy() {
        VpnStatus.removeStateListener(this);
    }

    @PluginMethod
    public void connect(PluginCall call) {
        String config = call.getString("config");
        if (config == null || config.trim().isEmpty()) { call.reject("No connection file was given."); return; }
        VpnProfile vp;
        try {
            ConfigParser cp = new ConfigParser();
            cp.parseConfig(new StringReader(config));
            vp = cp.convertProfile();
        } catch (Exception e) {
            call.reject("Could not read the connection file: " + e.getMessage());
            return;
        }
        vp.mName = call.getString("name", "Ionity VPN");
        int check = vp.checkProfile(getContext());
        if (check != R.string.no_error_found) { call.reject(getContext().getString(check)); return; }
        pending = vp;
        // Android 13+: the tunnel's ongoing notification needs this; the VPN works without it.
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
        startPending(call);
    }

    @ActivityCallback
    private void afterVpnPermission(PluginCall call, ActivityResult result) {
        if (result.getResultCode() == Activity.RESULT_OK) startPending(call);
        else { pending = null; call.reject("VPN permission denied"); }
    }

    private void startPending(PluginCall call) {
        VpnProfile vp = pending; pending = null;
        if (vp == null) { call.reject("Nothing to connect."); return; }
        try {
            ProfileManager.setTemporaryProfile(getContext(), vp);
            VPNLaunchHelper.startOpenVpn(vp, getContext(), "Ionity VPN", true);
            call.resolve();
        } catch (Exception e) {
            call.reject("Could not start the VPN: " + e.getMessage());
        }
    }

    @PluginMethod
    public void disconnect(PluginCall call) {
        final Context ctx = getContext();
        ProfileManager.setConntectedVpnProfileDisconnected(ctx);
        Intent i = new Intent(ctx, OpenVPNService.class);
        i.setAction(OpenVPNService.START_SERVICE);
        ServiceConnection conn = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder binder) {
                try { IOpenVPNServiceInternal.Stub.asInterface(binder).stopVPN(false); } catch (Exception ignored) { }
                try { ctx.unbindService(this); } catch (Exception ignored) { }
                call.resolve();
            }
            @Override public void onServiceDisconnected(ComponentName name) { }
        };
        if (!ctx.bindService(i, conn, Context.BIND_AUTO_CREATE)) call.resolve();
    }

    @PluginMethod
    public void getStatus(PluginCall call) {
        JSObject o = new JSObject();
        o.put("state", lastState);
        o.put("detail", lastDetail);
        o.put("engine", "openvpn2");
        call.resolve(o);
    }

    /** Last lines of the engine's log, for the in-app "Connection log" view. */
    @PluginMethod
    public void getLog(PluginCall call) {
        JSArray lines = new JSArray();
        try {
            LogItem[] items = VpnStatus.getlogbuffer();
            int from = Math.max(0, items.length - 60);
            for (int i = from; i < items.length; i++) lines.put(items[i].getString(getContext()));
        } catch (Exception e) {
            lines.put("Could not read the log: " + e.getMessage());
        }
        JSObject o = new JSObject();
        o.put("lines", lines);
        call.resolve(o);
    }

    @Override
    public void updateState(String state, String logmessage, int localizedResId, ConnectionStatus level, Intent intent) {
        String s;
        switch (level) {
            case LEVEL_CONNECTED: s = "CONNECTED"; break;
            case LEVEL_NOTCONNECTED:
            case LEVEL_VPNPAUSED: s = "DISCONNECTED"; break;
            case LEVEL_AUTH_FAILED: s = "ERROR"; break;
            default: s = "CONNECTING";
        }
        lastState = s;
        lastDetail = state == null ? "" : state;
        JSObject o = new JSObject();
        o.put("state", s);
        o.put("detail", lastDetail);
        o.put("message", logmessage == null ? "" : logmessage);
        notifyListeners("state", o, true);
    }

    @Override
    public void setConnectedVPN(String uuid) { }
}
