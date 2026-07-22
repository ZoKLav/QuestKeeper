package com.zoey.questkeeper;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class QuestTuner {
    static final String TAG = "QuestKeeper";
    static final String PREFS = "quest_keeper_prefs";
    static final String KEY_BOOT_ENABLED = "boot_enabled";
    static final String KEY_REAPPLY = "reapply";
    static final String KEY_SCREEN_WAKELOCK = "screen_wakelock";
    static final String KEY_STAY_AWAKE_CHARGING = "stay_awake_charging";
    static final String KEY_ADB_WIFI_AUTO = "adb_wifi_auto";
    static final String KEY_ADB_REQUIRE_USB = "adb_require_usb";
    static final String KEY_ADB_TCPIP_FALLBACK = "adb_tcpip_fallback";
    static final String KEY_LAST_ADB_HOST = "last_adb_host";
    static final String KEY_LAST_ADB_PORT = "last_adb_port";
    static final String KEY_LAST_ADB_COMMAND = "last_adb_command";
    static final String KEY_LAST_LOG = "last_log";
    static final String KEY_LAST_APPLY = "last_apply";

    static final int HUGE_TIMEOUT_MS = Integer.MAX_VALUE; // 24.8 days; Android setting is an int.
    static final int STAY_AWAKE_USB_AC_WIRELESS = 7;      // BatteryManager BATTERY_PLUGGED_* bitmask: AC|USB|WIRELESS.

    private QuestTuner() {}

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void seedDefaults(Context context) {
        SharedPreferences p = prefs(context);
        p.edit()
                .putBoolean(KEY_BOOT_ENABLED, p.getBoolean(KEY_BOOT_ENABLED, true))
                .putBoolean(KEY_REAPPLY, p.getBoolean(KEY_REAPPLY, true))
                .putBoolean(KEY_SCREEN_WAKELOCK, p.getBoolean(KEY_SCREEN_WAKELOCK, true))
                .putBoolean(KEY_STAY_AWAKE_CHARGING, p.getBoolean(KEY_STAY_AWAKE_CHARGING, true))
                .putBoolean(KEY_ADB_WIFI_AUTO, p.getBoolean(KEY_ADB_WIFI_AUTO, true))
                .putBoolean(KEY_ADB_REQUIRE_USB, p.getBoolean(KEY_ADB_REQUIRE_USB, true))
                .putBoolean(KEY_ADB_TCPIP_FALLBACK, p.getBoolean(KEY_ADB_TCPIP_FALLBACK, false))
                .apply();
    }

    static void log(Context context, String msg) {
        String stamped = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date()) + "  " + msg;
        Log.i(TAG, stamped);
        SharedPreferences p = prefs(context);
        String old = p.getString(KEY_LAST_LOG, "");
        String next = stamped + (old.isEmpty() ? "" : "\n" + old);
        if (next.length() > 8000) next = next.substring(0, 8000);
        p.edit().putString(KEY_LAST_LOG, next).apply();
    }

    static boolean hasWriteSettings(Context context) {
        return Build.VERSION.SDK_INT < 23 || Settings.System.canWrite(context);
    }

    static boolean hasWriteSecureSettings(Context context) {
        return context.checkCallingOrSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED;
    }

    static boolean isIgnoringBatteryOptimizations(Context context) {
        if (Build.VERSION.SDK_INT < 23) return true;
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(context.getPackageName());
    }

    static void applyEverything(Context context, String reason) {
        SharedPreferences p = prefs(context);
        log(context, "Applying profile (" + reason + ")");
        sendProxClose(context, -1);
        applySystemTimeout(context);
        applySecureSleepTimeout(context);
        if (p.getBoolean(KEY_STAY_AWAKE_CHARGING, true)) applyStayAwakeWhilePlugged(context);
        if (p.getBoolean(KEY_ADB_WIFI_AUTO, true)) {
            boolean requireUsb = p.getBoolean(KEY_ADB_REQUIRE_USB, true);
            if (p.getBoolean(KEY_ADB_TCPIP_FALLBACK, false)) {
                AdbWireless.enableWirelessThenTcpipHandoff(context, requireUsb, null);
            } else {
                boolean enabled = AdbWireless.enableWirelessAdb(context, requireUsb);
                if (enabled) AdbWireless.discoverPort(context, 10_000L, null);
            }
        }
        p.edit().putLong(KEY_LAST_APPLY, System.currentTimeMillis()).apply();
        log(context, "Profile apply finished");
    }

    static void sendProxClose(Context context, int durationMs) {
        try {
            Intent intent = new Intent("com.oculus.vrpowermanager.prox_close");
            if (durationMs > 0) intent.putExtra("duration", durationMs);
            context.sendBroadcast(intent);
            log(context, "Sent Quest prox_close broadcast" + (durationMs > 0 ? " for " + durationMs + " ms" : ""));
        } catch (Exception e) {
            log(context, "prox_close broadcast failed: " + e.getMessage());
        }

        Shell.Result r = Shell.run("taskset 0000000F am broadcast -a com.oculus.vrpowermanager.prox_close", 3000);
        if (r.ok) log(context, "prox_close shell fallback ok");
        else log(context, "prox_close shell fallback not available: " + r.compact());
    }

    static void sendProxRestore(Context context) {
        try {
            context.sendBroadcast(new Intent("com.oculus.vrpowermanager.automation_disable"));
            log(context, "Sent Quest automation_disable broadcast");
        } catch (Exception e) {
            log(context, "automation_disable broadcast failed: " + e.getMessage());
        }
        Shell.Result r = Shell.run("taskset 0000000F am broadcast -a com.oculus.vrpowermanager.automation_disable", 3000);
        if (r.ok) log(context, "automation_disable shell fallback ok");
        else log(context, "automation_disable shell fallback not available: " + r.compact());
    }

    static void applySystemTimeout(Context context) {
        if (!hasWriteSettings(context)) {
            log(context, "WRITE_SETTINGS missing; system screen timeout not changed");
            return;
        }
        try {
            Settings.System.putInt(context.getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, HUGE_TIMEOUT_MS);
            log(context, "Set system screen_off_timeout to " + HUGE_TIMEOUT_MS + " ms");
        } catch (Exception e) {
            log(context, "screen_off_timeout failed: " + e.getMessage());
        }
    }

    static void applySecureSleepTimeout(Context context) {
        if (!hasWriteSecureSettings(context)) {
            log(context, "WRITE_SECURE_SETTINGS missing; secure sleep_timeout not changed");
            return;
        }
        try {
            Settings.Secure.putInt(context.getContentResolver(), "sleep_timeout", 0);
            log(context, "Set secure sleep_timeout to 0");
        } catch (Exception e) {
            log(context, "sleep_timeout failed: " + e.getMessage());
        }
    }

    static void applyStayAwakeWhilePlugged(Context context) {
        if (!hasWriteSecureSettings(context)) {
            log(context, "WRITE_SECURE_SETTINGS missing; stay_on_while_plugged_in not changed");
            return;
        }
        try {
            Settings.Global.putInt(context.getContentResolver(), "stay_on_while_plugged_in", STAY_AWAKE_USB_AC_WIRELESS);
            log(context, "Set stay_on_while_plugged_in to AC|USB|WIRELESS");
        } catch (Exception e) {
            log(context, "stay_on_while_plugged_in failed: " + e.getMessage());
        }
    }

    static void restoreSaneTimeouts(Context context) {
        try {
            if (hasWriteSettings(context)) {
                Settings.System.putInt(context.getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, 600000);
                log(context, "Restored system screen_off_timeout to 10 minutes");
            }
            if (hasWriteSecureSettings(context)) {
                Settings.Secure.putInt(context.getContentResolver(), "sleep_timeout", -1);
                Settings.Global.putInt(context.getContentResolver(), "stay_on_while_plugged_in", 0);
                Settings.Global.putInt(context.getContentResolver(), "adb_wifi_enabled", 0);
                log(context, "Restored secure/global sleep helpers and disabled ADB Wi-Fi");
            }
            sendProxRestore(context);
        } catch (Exception e) {
            log(context, "Restore failed: " + e.getMessage());
        }
    }

    static String readSystemSetting(Context context, String table, String key) {
        try {
            if ("system".equals(table)) return Settings.System.getString(context.getContentResolver(), key);
            if ("secure".equals(table)) return Settings.Secure.getString(context.getContentResolver(), key);
            if ("global".equals(table)) return Settings.Global.getString(context.getContentResolver(), key);
        } catch (Exception ignored) {}
        return "?";
    }

    static Intent writeSettingsIntent(Context context) {
        return new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + context.getPackageName()))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    static Intent batteryIntent(Context context) {
        return new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:" + context.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
}
