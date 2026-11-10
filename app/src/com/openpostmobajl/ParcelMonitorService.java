package com.openpostmobajl;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Foreground service that polls the parcel list every 5 minutes and posts a
 * notification when a parcel's status changes.
 *
 * A foreground service keeps the process alive while the app is in the
 * background, so the checks keep running after the UI is closed. It shows a
 * persistent "Monitoring parcels" notification, as Android requires.
 *
 * The service type is {@code dataSync}; because the app targets API 34, the
 * Android 15 dataSync time limit (which applies to apps targeting API 35+)
 * does not apply. Doze can still delay network access while the device is
 * idle, so the 5-minute cadence is best effort when the device is sleeping.
 */
public class ParcelMonitorService extends Service {

    /**
     * Shared lock so the service and the UI never refresh the same OAuth token
     * at the same time (a refresh may rotate the refresh token).
     */
    static final Object AUTH_LOCK = new Object();

    private static final long INTERVAL_MINUTES = 5;
    private static final String CHANNEL_ONGOING = "parcel_monitor";
    private static final String CHANNEL_CHANGES = "parcel_status";
    private static final int ONGOING_NOTIFICATION_ID = 1;

    private ScheduledExecutorService executor;
    private final Map<String, String> known = new HashMap<String, String>();
    private boolean seeded = false;

    /** Starts (or re-uses) the foreground service. */
    static void start(Context ctx) {
        Intent intent = new Intent(ctx, ParcelMonitorService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            ctx.startForegroundService(intent);
        } else {
            ctx.startService(intent);
        }
    }

    /** Stops the foreground service and removes its ongoing notification. */
    static void stop(Context ctx) {
        ctx.stopService(new Intent(ctx, ParcelMonitorService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannels();

        Notification ongoing = ongoingNotification();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(ONGOING_NOTIFICATION_ID, ongoing,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(ONGOING_NOTIFICATION_ID, ongoing);
        }

        executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "parcel-monitor");
                t.setDaemon(true);
                return t;
            }
        });
        executor.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                try {
                    poll();
                } catch (Throwable ignored) {
                    // Best effort: a failed poll must not kill the scheduler.
                }
            }
        }, 0, INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Restart if the system kills the service; the UI can also start it
        // again explicitly from the foreground.
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        super.onDestroy();
    }

    private void poll() throws Exception {
        AuthState st;
        try {
            st = AuthStore.load(this);
        } catch (Exception e) {
            return;
        }
        boolean noAuth = (st.authToken == null || st.authToken.length() == 0)
                && (st.refreshToken == null || st.refreshToken.length() == 0);
        if (noAuth) {
            return;
        }

        ApiClient.ParcelResult res;
        synchronized (AUTH_LOCK) {
            res = ApiClient.ensureAuth(st);
            try {
                AuthStore.save(this, st);
            } catch (Exception ignored) {
            }
        }

        Map<String, String> current = new HashMap<String, String>();
        JSONArray parcels = res.parcels;
        for (int i = 0; i < parcels.length(); i++) {
            JSONObject p = parcels.optJSONObject(i);
            if (p == null) {
                continue;
            }
            String id = parcelId(p);
            if (id.length() > 0) {
                current.put(id, statusOf(p));
            }
        }

        if (!seeded) {
            seedFromCache();
            seeded = true;
        }

        for (Map.Entry<String, String> e : current.entrySet()) {
            String before = known.get(e.getKey());
            String after = e.getValue();
            if (before != null && after != null && after.length() > 0
                    && !before.equals(after)) {
                postChangeNotification(e.getKey(), before, after);
            }
        }

        known.clear();
        known.putAll(current);
        ParcelCache.save(this, res);
    }

    private void seedFromCache() {
        ApiClient.ParcelResult cached = ParcelCache.load(this);
        if (cached == null) {
            return;
        }
        for (int i = 0; i < cached.parcels.length(); i++) {
            JSONObject p = cached.parcels.optJSONObject(i);
            if (p == null) {
                continue;
            }
            String id = parcelId(p);
            if (id.length() > 0) {
                known.put(id, statusOf(p));
            }
        }
    }

    private static String parcelId(JSONObject p) {
        return p.optString("shipmentNumber", p.optString("primaryParcelNumber", ""));
    }

    private static String statusOf(JSONObject p) {
        return p.optString("status", p.optString("statusGroup", ""));
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationManager nm = (NotificationManager)
                getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }
        NotificationChannel ongoing = new NotificationChannel(CHANNEL_ONGOING,
                "Parcel monitoring", NotificationManager.IMPORTANCE_LOW);
        nm.createNotificationChannel(ongoing);
        NotificationChannel changes = new NotificationChannel(CHANNEL_CHANGES,
                "Parcel status", NotificationManager.IMPORTANCE_DEFAULT);
        nm.createNotificationChannel(changes);
    }

    @SuppressWarnings("deprecation")
    private static Notification.Builder builder(Context ctx, String channelId, int priority) {
        if (Build.VERSION.SDK_INT >= 26) {
            return new Notification.Builder(ctx, channelId);
        }
        Notification.Builder b = new Notification.Builder(ctx);
        b.setPriority(priority);
        return b;
    }

    private Notification ongoingNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = builder(this, CHANNEL_ONGOING, Notification.PRIORITY_LOW);
        b.setSmallIcon(android.R.drawable.stat_notify_sync);
        b.setContentTitle("Monitoring parcels");
        b.setContentText("Checking for status changes every 5 minutes");
        b.setContentIntent(pi);
        b.setOngoing(true);
        b.setOnlyAlertOnce(true);
        return b.build();
    }

    @SuppressWarnings("deprecation")
    private void postChangeNotification(String shipment, String before, String after) {
        NotificationManager nm = (NotificationManager)
                getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }

        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, shipment.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = builder(this, CHANNEL_CHANGES, Notification.PRIORITY_DEFAULT);
        b.setSmallIcon(android.R.drawable.stat_notify_sync);
        b.setContentTitle("Parcel " + last6(shipment) + " status changed");
        b.setContentText(before + " \u2192 " + after);
        b.setContentIntent(pi);
        b.setAutoCancel(true);
        b.setOnlyAlertOnce(true);
        nm.notify(shipment.hashCode(), b.build());
    }

    private static String last6(String n) {
        return n.length() > 6 ? n.substring(n.length() - 6) : n;
    }
}
