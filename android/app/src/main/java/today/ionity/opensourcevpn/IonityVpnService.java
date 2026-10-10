/*
 * Ionity VPN — the Android VPN service that runs the tunnel.
 * Copyright (c) 2018-2026 Antwerp Designs | Ionity (Pty) Ltd — AEDI. GPL-3.0-or-later.
 */
package today.ionity.opensourcevpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.VpnService;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import today.ionity.opensourcevpn.ovpn3.ClientAPI_Status;

/**
 * Runs one OpenVPN 3 session at a time on its own thread, in the app's own process.
 * State goes to whoever registered with setListener (the IonityVpn Capacitor plugin).
 */
public class IonityVpnService extends VpnService {
    static final String ACTION_CONNECT = "today.ionity.opensourcevpn.CONNECT";
    static final String ACTION_DISCONNECT = "today.ionity.opensourcevpn.DISCONNECT";
    static final String EXTRA_CONFIG = "config";
    static final String EXTRA_NAME = "name";
    private static final String CHANNEL = "ionity_vpn";
    private static final int NOTE_ID = 1;

    /** CONNECTING | CONNECTED | DISCONNECTED | ERROR */
    interface StateListener { void onState(String state, String detail, String message); }

    private static volatile StateListener listener;
    private static volatile String state = "DISCONNECTED";
    private static volatile String detail = "";
    private static final ArrayDeque<String> LOG = new ArrayDeque<>();

    static void setListener(StateListener l) { listener = l; }
    static String state() { return state; }
    static String detail() { return detail; }
    static List<String> log() { synchronized (LOG) { return new ArrayList<>(LOG); } }

    private Thread worker;
    private volatile EngineClient client;
    private String sessionName = "Ionity VPN";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_DISCONNECT.equals(action)) { stopSession(); stopForegroundCompat(); stopSelf(); return START_NOT_STICKY; }
        if (ACTION_CONNECT.equals(action)) {
            String config = intent.getStringExtra(EXTRA_CONFIG);
            String name = intent.getStringExtra(EXTRA_NAME);
            sessionName = name == null ? "Ionity VPN" : name;
            goForeground("Connecting…");
            if (config == null || config.trim().isEmpty()) {
                addLog("No connection file was passed to the VPN service.");
                publish("ERROR", "CONFIG", "No connection file");
                stopForegroundCompat(); stopSelf();
                return START_NOT_STICKY;
            }
            startSession(config);
            return START_NOT_STICKY;
        }
        // Started by the system (always-on VPN) or restarted without a request: nothing to run.
        stopSelf();
        return START_NOT_STICKY;
    }

    private synchronized void startSession(String config) {
        stopSession();
        clearLog();
        publish("CONNECTING", "STARTING", "");
        addLog("Starting session: " + sessionName);
        final EngineClient c = new EngineClient(this, sessionName, new EngineClient.Listener() {
            @Override public void onEvent(String name, String info, boolean error, boolean fatal) { handleEvent(name, info, error, fatal); }
            @Override public void onLog(String line) { addLog(line); }
        });
        String bad;
        try { bad = c.load(config); } catch (Throwable t) { bad = String.valueOf(t); }
        if (bad != null) { addLog("Config error: " + bad); publish("ERROR", "CONFIG", bad); stopForegroundCompat(); stopSelf(); return; }
        client = c;
        worker = new Thread(() -> {
            ClientAPI_Status st;
            try { st = c.run(); }
            catch (Throwable t) { addLog("Engine crashed: " + t); publish("ERROR", "CRASH", String.valueOf(t)); return; }
            if (client == c) {
                client = null;
                if (st.getError()) { addLog("Session ended: " + st.getStatus() + " " + st.getMessage()); publish("ERROR", st.getStatus(), st.getMessage()); }
                else publish("DISCONNECTED", "EXITING", "");
                stopForegroundCompat(); stopSelf();
            }
        }, "IonityVpnEngine");
        worker.start();
    }

    private synchronized void stopSession() {
        EngineClient c = client; client = null;
        if (c != null) { try { c.stop(); } catch (Throwable ignored) { } }
        Thread w = worker; worker = null;
        if (w != null) { try { w.join(3000); } catch (InterruptedException ignored) { } }
        if (c != null) publish("DISCONNECTED", "STOPPED", "");
    }

    private void handleEvent(String name, String info, boolean error, boolean fatal) {
        addLog("EVENT " + name + (info == null || info.isEmpty() ? "" : ": " + info));
        if ("CONNECTED".equals(name)) { publish("CONNECTED", name, info); goForeground("Connected · " + sessionName.replace("Ionity · ", "")); }
        else if ("DISCONNECTED".equals(name)) publish("DISCONNECTED", name, info);
        else if (fatal || (error && !"WARN".equals(name))) publish("ERROR", name, info);
        else if ("RECONNECTING".equals(name) || "RESOLVE".equals(name) || "WAIT".equals(name) || "CONNECTING".equals(name)
                || "GET_CONFIG".equals(name) || "ASSIGN_IP".equals(name) || "ADD_ROUTES".equals(name) || "AUTH_PENDING".equals(name))
            publish(state.equals("CONNECTED") && !"RECONNECTING".equals(name) ? "CONNECTED" : "CONNECTING", name, info);
    }

    private static void publish(String s, String d, String msg) {
        state = s; detail = d == null ? "" : d;
        StateListener l = listener;
        if (l != null) l.onState(s, detail, msg == null ? "" : msg);
    }
    private static void addLog(String line) {
        Log.i("IonityVPN", line);
        synchronized (LOG) { LOG.addLast(line); while (LOG.size() > 300) LOG.removeFirst(); }
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
                .build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(NOTE_ID, n);
    }
    private void stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE); else stopForeground(true);
    }

    @Override public void onRevoke() { stopSession(); publish("DISCONNECTED", "REVOKED", ""); stopForegroundCompat(); stopSelf(); }
    @Override public void onDestroy() { stopSession(); super.onDestroy(); }
}
