/*
 * Ionity VPN — the Android VPN service that runs and supervises the tunnel.
 * Copyright (c) 2018-2026 Antwerp Designs | Ionity (Pty) Ltd — AEDI. GPL-3.0-or-later.
 */
package today.ionity.opensourcevpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import today.ionity.opensourcevpn.ovpn3.ClientAPI_Status;

/**
 * Keeps one VPN connection up until the user stops it.
 *
 * The app hands over the best few servers of the chosen place. A supervisor thread runs one
 * OpenVPN 3 session at a time:
 *  - first connect: tries each server once; if none answers, reports ERROR and stops;
 *  - once connected: if the session drops for any reason it reconnects by itself (same server
 *    first, then the others) with a short back-off, for as long as the user wants the VPN on;
 *  - between sessions it holds a placeholder interface, so the VPN key stays up and no traffic
 *    leaves outside the tunnel while it reconnects;
 *  - when the phone changes network (Wi-Fi ↔ mobile) it tells the engine to reconnect at once
 *    instead of waiting for a timeout, and pauses while there is no network at all.
 * All of this runs natively, so it keeps working with the screen off or the app closed.
 */
public class IonityVpnService extends VpnService {
    static final String ACTION_CONNECT = "today.ionity.opensourcevpn.CONNECT";
    static final String ACTION_DISCONNECT = "today.ionity.opensourcevpn.DISCONNECT";
    static final String EXTRA_CONFIGS = "configs";
    static final String EXTRA_HOSTS = "hosts";
    static final String EXTRA_NAME = "name";
    private static final String CHANNEL = "ionity_vpn";
    private static final int NOTE_ID = 1;
    private static final int[] BACKOFF_S = { 0, 2, 4, 8, 15, 30 };

    /** state: CONNECTING | CONNECTED | RECONNECTING | DISCONNECTED | ERROR */
    interface StateListener { void onState(String state, String detail, String message, String server); }

    private static volatile StateListener listener;
    private static volatile String state = "DISCONNECTED";
    private static volatile String detail = "";
    private static volatile String server = "";
    private static volatile long since = 0;
    private static final ArrayDeque<String> LOG = new ArrayDeque<>();

    static void setListener(StateListener l) { listener = l; }
    static String state() { return state; }
    static String detail() { return detail; }
    static String server() { return server; }
    static long since() { return since; }
    static List<String> log() { synchronized (LOG) { return new ArrayList<>(LOG); } }

    private final Object lock = new Object();      // start/stop of the supervisor
    private final Object holdLock = new Object();  // the placeholder interface
    private final Object clientLock = new Object(); // calls into the native session vs. freeing it
    private Thread supervisor;
    private volatile boolean wanted;           // the user wants the VPN on
    private volatile EngineClient client;      // the session running right now
    private ParcelFileDescriptor hold;         // placeholder interface between sessions
    private String sessionName = "Ionity VPN";
    private final Handler main = new Handler(Looper.getMainLooper());

    /* ---------------- start / stop ---------------- */

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_DISCONNECT.equals(action)) { shutdown("STOPPED"); return START_NOT_STICKY; }
        if (ACTION_CONNECT.equals(action)) {
            ArrayList<String> configs = intent.getStringArrayListExtra(EXTRA_CONFIGS);
            ArrayList<String> hosts = intent.getStringArrayListExtra(EXTRA_HOSTS);
            String name = intent.getStringExtra(EXTRA_NAME);
            sessionName = name == null ? "Ionity VPN" : name;
            goForeground("Connecting · " + place());
            List<Entry> list = new ArrayList<>();
            if (configs != null) for (int i = 0; i < configs.size(); i++) {
                String c = configs.get(i);
                if (c != null && !c.trim().isEmpty()) list.add(new Entry(c, hosts != null && i < hosts.size() ? hosts.get(i) : "server " + (i + 1)));
            }
            if (list.isEmpty()) {
                addLog("No connection file was passed to the VPN service.");
                publish("ERROR", "CONFIG", "No connection file");
                shutdown(null);
                return START_NOT_STICKY;
            }
            begin(list);
            return START_NOT_STICKY;
        }
        // Started by the system (always-on VPN) or restarted without a request: nothing to run.
        if (!wanted) { stopForegroundCompat(); stopSelf(); }
        return START_NOT_STICKY;
    }

    private void begin(List<Entry> list) {
        boolean switching = "CONNECTED".equals(state) || "RECONNECTING".equals(state);
        synchronized (lock) {
            // Switching place while connected: keep the key up while the old session is replaced.
            if (switching) holdInterface();
            stopSupervisor();
            clearLog();
            wanted = true;
            final boolean keepUp = switching;
            supervisor = new Thread(() -> supervise(list, keepUp), "IonityVpnSupervisor");
            supervisor.start();
        }
        watchNetwork(true);
    }

    private void shutdown(String why) {
        synchronized (lock) {
            wanted = false;
            stopSupervisor();
            releaseHold();
        }
        watchNetwork(false);
        if (why != null) { since = 0; publish("DISCONNECTED", why, ""); }
        stopForegroundCompat();
        stopSelf();
    }

    /** Stops the running session and waits for the supervisor to finish. Caller holds lock. */
    private void stopSupervisor() {
        Thread t = supervisor; supervisor = null;
        synchronized (clientLock) { EngineClient c = client; if (c != null) { try { c.stop(); } catch (Throwable ignored) { } } }
        if (t != null) { t.interrupt(); try { t.join(4000); } catch (InterruptedException ignored) { } }
        client = null;
    }

    /* ---------------- supervisor ---------------- */

    private static final class Entry {
        final String config, host;
        Entry(String config, String host) { this.config = config; this.host = host; }
    }

    private void supervise(List<Entry> list, boolean everUp) {
        final Thread me = Thread.currentThread();
        int idx = 0, failsInRow = 0, round = 0;
        while (wanted && supervisor == me) {
            Entry e = list.get(idx);
            server = e.host;
            publish(everUp ? "RECONNECTING" : "CONNECTING", "STARTING", "");
            addLog((everUp ? "Reconnecting to " : "Connecting to ") + e.host + " (" + (idx + 1) + "/" + list.size() + ")");
            if (everUp) goForeground("Reconnecting · " + place());

            EngineClient c = new EngineClient(this, sessionName, new EngineClient.Listener() {
                @Override public void onEvent(String name, String info, boolean error, boolean fatal) { handleEvent(name, info, error, fatal); }
                @Override public void onLog(String line) { addLog(line); }
                @Override public void onEstablished() { releaseHold(); }
            });
            String bad;
            try { bad = c.load(e.config); } catch (Throwable t) { bad = String.valueOf(t); }
            boolean wasUp = false;
            if (bad != null) { addLog("Config error (" + e.host + "): " + bad); try { c.delete(); } catch (Throwable ignored) { } }
            else if (!wanted || supervisor != me) { try { c.delete(); } catch (Throwable ignored) { } break; }
            else {
                client = c;
                try {
                    ClientAPI_Status st = c.run();   // blocks for the whole session
                    if (st.getError()) addLog("Session ended: " + st.getStatus() + " " + st.getMessage());
                    else addLog("Session ended.");
                } catch (Throwable t) { addLog("Engine error: " + t); }
                wasUp = c.wasConnected();
                // Free the native session now (closes its tun) instead of waiting for the GC.
                synchronized (clientLock) { if (client == c) client = null; try { c.delete(); } catch (Throwable ignored) { } }
            }
            if (!wanted || supervisor != me) break;

            if (wasUp) { everUp = true; failsInRow = 0; }         // it worked: retry the same server first
            else { failsInRow++; idx = (idx + 1) % list.size(); if (idx == 0) round++; }

            if (!everUp && failsInRow >= list.size()) {            // first connect, nothing answered
                publish("ERROR", "NO_SERVER", "None of the servers answered");
                break;
            }
            // Keep the key up and traffic inside while we wait and reconnect.
            holdInterface();
            int wait = BACKOFF_S[Math.min(Math.max(failsInRow, round), BACKOFF_S.length - 1)];
            if (wait > 0) {
                publish("RECONNECTING", "WAIT", "Retrying in " + wait + " s");
                try { Thread.sleep(wait * 1000L); } catch (InterruptedException ie) { break; }
            }
        }
        if (supervisor == me && wanted) {
            // Gave up on a first connect: leave the service.
            wanted = false;
            releaseHold();
            main.post(() -> { watchNetwork(false); stopForegroundCompat(); stopSelf(); });
        }
    }

    private void handleEvent(String name, String info, boolean error, boolean fatal) {
        addLog("EVENT " + name + (info == null || info.isEmpty() ? "" : ": " + info));
        if ("CONNECTED".equals(name)) {
            if (!"CONNECTED".equals(state) || since == 0) since = System.currentTimeMillis();
            publish("CONNECTED", name, info);
            goForeground("Connected · " + place());
        } else if ("RECONNECTING".equals(name) || "PAUSE".equals(name)) {
            publish("RECONNECTING", name, info);
            goForeground("Reconnecting · " + place());
        } else if ("RESUME".equals(name) || "RESOLVE".equals(name) || "WAIT".equals(name) || "CONNECTING".equals(name)
                || "GET_CONFIG".equals(name) || "ASSIGN_IP".equals(name) || "ADD_ROUTES".equals(name)) {
            if (!"CONNECTED".equals(state)) publish(state.equals("RECONNECTING") ? "RECONNECTING" : "CONNECTING", name, info);
        } else if (fatal || (error && !"WARN".equals(name))) {
            // The supervisor decides what happens next; report it as progress, not as the end.
            publish("CONNECTED".equals(state) || "RECONNECTING".equals(state) ? "RECONNECTING" : "CONNECTING", name, info);
        }
    }

    /* ---------------- placeholder interface ---------------- */

    /** A tun with no way out: keeps the VPN up and blocks traffic until the next session is ready. */
    private void holdInterface() {
        synchronized (holdLock) {
            if (hold != null) return;
            try {
                Builder b = new Builder()
                        .setSession(sessionName)
                        .addAddress("10.255.255.1", 32)
                        .addRoute("0.0.0.0", 0)
                        .addAddress("fd00:1:fd00:1:fd00:1:fd00:1", 128)
                        .addRoute("::", 0)
                        .setMtu(1400);
                hold = b.establish();
            } catch (Exception e) { addLog("hold: " + e.getMessage()); }
        }
    }
    private void releaseHold() {
        synchronized (holdLock) {
            ParcelFileDescriptor h = hold; hold = null;
            if (h != null) { try { h.close(); } catch (Exception ignored) { } }
        }
    }

    /* ---------------- network changes ---------------- */

    private ConnectivityManager.NetworkCallback netCb;
    private final Set<Network> nets = new LinkedHashSet<>();
    private Network lastNet;
    private boolean paused;
    private final Runnable netCheck = this::onNetworkSettled;

    private void watchNetwork(boolean on) {
        main.post(() -> {
            ConnectivityManager cm = getSystemService(ConnectivityManager.class);
            if (cm == null) return;
            if (on && netCb == null) {
                NetworkRequest req = new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                        .build();
                netCb = new ConnectivityManager.NetworkCallback() {
                    @Override public void onAvailable(Network n) { main.post(() -> { nets.add(n); schedule(); }); }
                    @Override public void onLost(Network n) { main.post(() -> { nets.remove(n); schedule(); }); }
                };
                nets.clear(); lastNet = null; paused = false;
                try { cm.registerNetworkCallback(req, netCb); } catch (Exception e) { netCb = null; }
            } else if (!on && netCb != null) {
                try { cm.unregisterNetworkCallback(netCb); } catch (Exception ignored) { }
                netCb = null; nets.clear(); lastNet = null; paused = false;
                main.removeCallbacks(netCheck);
            }
        });
    }
    private void schedule() { main.removeCallbacks(netCheck); main.postDelayed(netCheck, 1500); }

    /** Runs on the main thread once the network has been stable for a moment. */
    private void onNetworkSettled() {
        if (!wanted) return;
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        Network now = pickNetwork(cm);
        if (now == null) {
            if (!paused && client != null) { paused = true; addLog("No network — waiting for one."); engine(c -> c.pause("no network")); }
            return;
        }
        try { setUnderlyingNetworks(new Network[] { now }); } catch (Exception ignored) { }
        if (paused) {
            paused = false; addLog("Network is back.");
            engine(EngineClient::resume);
        } else if (lastNet != null && !now.equals(lastNet) && client != null) {
            addLog("Network changed — reconnecting.");
            engine(c -> c.reconnect(0));
        }
        lastNet = now;
    }
    private interface EngineCall { void run(EngineClient c); }
    private void engine(EngineCall call) {
        synchronized (clientLock) { EngineClient c = client; if (c != null) { try { call.run(c); } catch (Throwable ignored) { } } }
    }
    private Network pickNetwork(ConnectivityManager cm) {
        if (nets.isEmpty()) return null;
        // Prefer an unmetered network (Wi-Fi) the way Android does; otherwise the newest one.
        Network pick = null;
        for (Network n : nets) {
            NetworkCapabilities nc = cm == null ? null : cm.getNetworkCapabilities(n);
            if (nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) pick = n;
        }
        if (pick != null) return pick;
        Network last = null; for (Network n : nets) last = n; return last;
    }

    /* ---------------- state, log, notification ---------------- */

    private String place() { return sessionName.replace("Ionity · ", ""); }

    private static void publish(String s, String d, String msg) {
        state = s; detail = d == null ? "" : d;
        StateListener l = listener;
        if (l != null) l.onState(s, detail, msg == null ? "" : msg, server);
    }
    private static void addLog(String line) {
        Log.i("IonityVPN", line);
        synchronized (LOG) { LOG.addLast(line); while (LOG.size() > 400) LOG.removeFirst(); }
    }
    private static void clearLog() { synchronized (LOG) { LOG.clear(); } }

    private void goForeground(String text) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null)
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "VPN status", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, IonityVpnService.class).setAction(ACTION_DISCONNECT),
                PendingIntent.FLAG_IMMUTABLE);
        Notification n = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_vpn_key)
                .setContentTitle("Ionity VPN")
                .setContentText(text)
                .setContentIntent(open)
                .addAction(0, "Disconnect", stop)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build();
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(NOTE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(NOTE_ID, n);
        } catch (Exception e) { addLog("notification: " + e.getMessage()); }
    }
    private void stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE); else stopForeground(true);
    }

    @Override public void onRevoke() { shutdown("REVOKED"); }
    @Override public void onDestroy() {
        synchronized (lock) { wanted = false; stopSupervisor(); releaseHold(); }
        if (!"DISCONNECTED".equals(state) && !"ERROR".equals(state)) { since = 0; publish("DISCONNECTED", "STOPPED", ""); }
        super.onDestroy();
    }
}
