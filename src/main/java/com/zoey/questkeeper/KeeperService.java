package com.zoey.questkeeper;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

public class KeeperService extends Service {
    static final String ACTION_BOOT_APPLY = "com.zoey.questkeeper.BOOT_APPLY";
    static final String ACTION_APPLY_NOW = "com.zoey.questkeeper.APPLY_NOW";
    static final String ACTION_REFRESH_WAKELOCK = "com.zoey.questkeeper.REFRESH_WAKELOCK";
    static final String ACTION_STOP = "com.zoey.questkeeper.STOP";

    private static final String CHANNEL = "quest_keeper_status";
    private static final int NOTIFICATION_ID = 1977;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock screenWakeLock;

    private final Runnable reapplyRunnable = new Runnable() {
        @Override public void run() {
            if (QuestTuner.prefs(KeeperService.this).getBoolean(QuestTuner.KEY_REAPPLY, true)) {
                QuestTuner.applyEverything(KeeperService.this, "periodic reapply");
                handler.postDelayed(this, 15L * 60L * 1000L);
            }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(NOTIFICATION_ID, buildNotification("Ready"));
        refreshWakeLock();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            QuestTuner.log(this, "Stopping service by request");
            releaseWakeLock();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_BOOT_APPLY.equals(action)) {
            refreshWakeLock();
            handler.removeCallbacks(reapplyRunnable);
            handler.postDelayed(() -> QuestTuner.applyEverything(this, "30 seconds after boot"), 30_000L);
            if (QuestTuner.prefs(this).getBoolean(QuestTuner.KEY_REAPPLY, true)) {
                handler.postDelayed(reapplyRunnable, 15L * 60L * 1000L);
            }
            QuestTuner.log(this, "Boot profile scheduled for 30 seconds from now");
        } else if (ACTION_APPLY_NOW.equals(action)) {
            refreshWakeLock();
            QuestTuner.applyEverything(this, "manual apply");
            handler.removeCallbacks(reapplyRunnable);
            if (QuestTuner.prefs(this).getBoolean(QuestTuner.KEY_REAPPLY, true)) {
                handler.postDelayed(reapplyRunnable, 15L * 60L * 1000L);
            }
        } else if (ACTION_REFRESH_WAKELOCK.equals(action)) {
            refreshWakeLock();
        } else {
            refreshWakeLock();
        }
        startForeground(NOTIFICATION_ID, buildNotification("Guarding timeout settings"));
        return START_STICKY;
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        releaseWakeLock();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private void refreshWakeLock() {
        boolean wanted = QuestTuner.prefs(this).getBoolean(QuestTuner.KEY_SCREEN_WAKELOCK, true);
        if (!wanted) {
            releaseWakeLock();
            QuestTuner.log(this, "Persistent screen wake lock disabled");
            return;
        }
        try {
            if (screenWakeLock == null) {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                if (pm == null) return;
                screenWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE,
                        "QuestKeeper:screen_on");
                screenWakeLock.setReferenceCounted(false);
            }
            if (!screenWakeLock.isHeld()) {
                screenWakeLock.acquire();
                QuestTuner.log(this, "Persistent screen wake lock acquired");
            }
        } catch (Exception e) {
            QuestTuner.log(this, "Wake lock failed: " + e.getMessage());
        }
    }

    private void releaseWakeLock() {
        try {
            if (screenWakeLock != null && screenWakeLock.isHeld()) screenWakeLock.release();
        } catch (Exception ignored) {}
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL, "QuestKeeper", NotificationManager.IMPORTANCE_LOW);
            c.setDescription("Keeps Quest timeout automation alive.");
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(c);
        }
    }

    private Notification buildNotification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        int flags = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, flags);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        return b.setContentTitle("QuestKeeper")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }
}
