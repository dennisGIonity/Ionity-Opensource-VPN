package today.ionity.opensourcevpn;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.net.Inet4Address;

/**
 * Tells the web layer which local networks this phone is on, so "Find my box" can
 * sweep the right /24 instead of guessing addresses. A browser cannot learn its own
 * LAN address; the APK can. Read-only, needs only ACCESS_NETWORK_STATE (a normal,
 * install-time permission). Reports Wi-Fi and Ethernet only — never mobile data,
 * where no Gate^Flame box can be.
 */
@CapacitorPlugin(name = "LanInfo")
public class LanInfoPlugin extends Plugin {

    @PluginMethod
    public void get(PluginCall call) {
        JSArray out = new JSArray();
        try {
            ConnectivityManager cm = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                for (Network n : cm.getAllNetworks()) {
                    NetworkCapabilities caps = cm.getNetworkCapabilities(n);
                    if (caps == null) continue;
                    String transport;
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) transport = "wifi";
                    else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) transport = "ethernet";
                    else continue;
                    LinkProperties lp = cm.getLinkProperties(n);
                    if (lp == null) continue;
                    for (LinkAddress la : lp.getLinkAddresses()) {
                        if (!(la.getAddress() instanceof Inet4Address) || la.getAddress().isLoopbackAddress()) continue;
                        JSObject o = new JSObject();
                        o.put("address", la.getAddress().getHostAddress());
                        o.put("prefix", la.getPrefixLength());
                        o.put("iface", lp.getInterfaceName());
                        o.put("transport", transport);
                        out.put(o);
                    }
                }
            }
        } catch (Exception e) {
            call.reject("Could not read network state: " + e.getMessage());
            return;
        }
        JSObject ret = new JSObject();
        ret.put("addresses", out);
        call.resolve(ret);
    }
}
