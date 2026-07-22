package com.zoey.questkeeper;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.provider.Settings;

import java.net.InetAddress;

final class AdbWireless {
    interface Listener {
        void onUpdate(String status);
        void onFound(String host, int port, String command);
    }

    private AdbWireless() {}

    static boolean isDebugEnabled(Context context) {
        return "1".equals(QuestTuner.readSystemSetting(context, "global", "adb_enabled"));
    }

    static boolean isWirelessAdbEnabled(Context context) {
        return "1".equals(QuestTuner.readSystemSetting(context, "global", "adb_wifi_enabled"));
    }

    static boolean isUsbPowered(Context context) {
        try {
            Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (battery == null) return false;
            int plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            return (plugged & BatteryManager.BATTERY_PLUGGED_USB) != 0;
        } catch (Exception e) {
            QuestTuner.log(context, "USB power check failed: " + e.getMessage());
            return false;
        }
    }

    static boolean enableWirelessAdb(Context context, boolean requireUsbPower) {
        if (!QuestTuner.hasWriteSecureSettings(context)) {
            QuestTuner.log(context, "ADB Wi-Fi not changed; WRITE_SECURE_SETTINGS is missing");
            return false;
        }
        if (!isDebugEnabled(context)) {
            QuestTuner.log(context, "ADB Wi-Fi not changed; USB debugging / adb_enabled is not already active");
            return false;
        }
        if (requireUsbPower && !isUsbPowered(context)) {
            QuestTuner.log(context, "ADB Wi-Fi not changed; USB power/PC connection is required by current setting");
            return false;
        }
        try {
            Settings.Global.putInt(context.getContentResolver(), "adb_wifi_enabled", 1);
            QuestTuner.log(context, "Set global adb_wifi_enabled to 1");
            return true;
        } catch (Exception e) {
            QuestTuner.log(context, "adb_wifi_enabled write failed: " + e.getMessage());
            return false;
        }
    }

    static boolean disableWirelessAdb(Context context) {
        if (!QuestTuner.hasWriteSecureSettings(context)) {
            QuestTuner.log(context, "ADB Wi-Fi disable failed; WRITE_SECURE_SETTINGS is missing");
            return false;
        }
        try {
            Settings.Global.putInt(context.getContentResolver(), "adb_wifi_enabled", 0);
            QuestTuner.prefs(context).edit()
                    .remove(QuestTuner.KEY_LAST_ADB_HOST)
                    .remove(QuestTuner.KEY_LAST_ADB_PORT)
                    .remove(QuestTuner.KEY_LAST_ADB_COMMAND)
                    .apply();
            QuestTuner.log(context, "Set global adb_wifi_enabled to 0");
            return true;
        } catch (Exception e) {
            QuestTuner.log(context, "adb_wifi_enabled disable failed: " + e.getMessage());
            return false;
        }
    }

    static void discoverPort(Context context, long timeoutMs, Listener listener) {
        final Context app = context.getApplicationContext();
        if (listener != null) listener.onUpdate("Discovering ADB mDNS service…");
        QuestTuner.log(app, "Starting ADB mDNS discovery");
        new AdbNsdDiscovery(app).discover(timeoutMs, new AdbNsdDiscovery.Callback() {
            @Override public void onStatus(String status) {
                QuestTuner.log(app, status);
                if (listener != null) listener.onUpdate(status);
            }

            @Override public void onFound(String host, int port, String serviceType) {
                String command = "adb connect " + host + ":" + port;
                saveConnectCommand(app, host, port, command);
                QuestTuner.log(app, "ADB mDNS found " + serviceType + " at " + host + ":" + port);
                if (listener != null) listener.onFound(host, port, command);
            }

            @Override public void onFinished(boolean found) {
                if (!found) {
                    QuestTuner.log(app, "ADB mDNS discovery timed out");
                    if (listener != null) listener.onUpdate("ADB mDNS discovery timed out");
                }
            }
        });
    }

    static void enableTcpipFallback(Context context, boolean requireUsbPower, Listener listener) {
        final Context app = context.getApplicationContext();
        if (listener != null) listener.onUpdate("Trying TCP/IP fallback on :5555…");
        QuestTuner.log(app, "TCP/IP fallback requested");

        String host = getWifiAddressString(app);
        if (host == null || "0.0.0.0".equals(host)) {
            QuestTuner.log(app, "TCP/IP fallback: Wi-Fi IP unavailable");
            if (listener != null) listener.onUpdate("Wi-Fi IP unavailable; connect headset to Wi-Fi first");
            return;
        }

        if (requireUsbPower && !isUsbPowered(app)) {
            QuestTuner.log(app, "TCP/IP fallback blocked; USB power/PC connection required by current setting");
            if (listener != null) listener.onUpdate("USB power/PC connection required by current setting");
            return;
        }

        // Best-effort only: normal APKs do not automatically have an adb client binary or shell UID.
        // If the user has provided/installed one in PATH, this can work. If not, the app still copies
        // the exact PC-side fallback command.
        Shell.Result r = Shell.run("adb tcpip 5555", 7000);
        if (r.ok) {
            String command = "adb connect " + host + ":5555";
            saveConnectCommand(app, host, 5555, command);
            QuestTuner.log(app, "Local adb tcpip 5555 succeeded; copied/connect command is " + command);
            if (listener != null) listener.onFound(host, 5555, command);
            return;
        }

        Shell.Result prop = Shell.run("setprop service.adb.tcp.port 5555; stop adbd; start adbd", 7000);
        if (prop.ok) {
            String command = "adb connect " + host + ":5555";
            saveConnectCommand(app, host, 5555, command);
            QuestTuner.log(app, "service.adb.tcp.port fallback succeeded; copied/connect command is " + command);
            if (listener != null) listener.onFound(host, 5555, command);
            return;
        }

        String pcCommand = "adb tcpip 5555 && adb connect " + host + ":5555";
        saveConnectCommand(app, host, 5555, pcCommand);
        QuestTuner.log(app, "Local TCP/IP switch not available: adb=" + r.compact());
        QuestTuner.log(app, "Property TCP/IP switch not available: " + prop.compact());
        QuestTuner.log(app, "Copied PC-side TCP/IP fallback command: " + pcCommand);
        if (listener != null) listener.onFound(host, 5555, pcCommand);
    }

    static void enableWirelessThenTcpipHandoff(Context context, boolean requireUsbPower, Listener listener) {
        final Context app = context.getApplicationContext();
        boolean ok = enableWirelessAdb(app, requireUsbPower);
        if (!ok) {
            enableTcpipFallback(app, requireUsbPower, listener);
            return;
        }
        if (listener != null) listener.onUpdate("Wireless debugging enabled; discovering TLS port for TCP/IP handoff…");
        discoverPort(app, 12_000L, new Listener() {
            @Override public void onUpdate(String status) {
                if (listener != null) listener.onUpdate(status);
            }

            @Override public void onFound(String host, int port, String ignoredCommand) {
                String command = "adb connect " + host + ":" + port + " && adb tcpip 5555 && adb connect " + host + ":5555";
                saveConnectCommand(app, host, 5555, command);
                QuestTuner.log(app, "Prepared TCP/IP handoff command: " + command);
                if (listener != null) listener.onFound(host, 5555, command);
            }
        });
    }

    static String getWifiAddressString(Context context) {
        try {
            WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifi == null || wifi.getConnectionInfo() == null) return null;
            int ipInt = wifi.getConnectionInfo().getIpAddress();
            if (ipInt == 0) return null;
            byte[] ipBytes = new byte[]{
                    (byte) (ipInt & 0xff),
                    (byte) ((ipInt >> 8) & 0xff),
                    (byte) ((ipInt >> 16) & 0xff),
                    (byte) ((ipInt >> 24) & 0xff)
            };
            InetAddress addr = InetAddress.getByAddress(ipBytes);
            return addr.getHostAddress();
        } catch (Exception ignored) {
            return null;
        }
    }

    static void saveConnectCommand(Context context, String host, int port, String command) {
        QuestTuner.prefs(context).edit()
                .putString(QuestTuner.KEY_LAST_ADB_HOST, host)
                .putInt(QuestTuner.KEY_LAST_ADB_PORT, port)
                .putString(QuestTuner.KEY_LAST_ADB_COMMAND, command)
                .apply();
    }

    static void copyLastCommand(Context context) {
        String command = QuestTuner.prefs(context).getString(QuestTuner.KEY_LAST_ADB_COMMAND, "");
        if (command.isEmpty()) {
            QuestTuner.log(context, "No ADB connect command to copy yet");
            return;
        }
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("QuestKeeper ADB command", command));
            QuestTuner.log(context, "Copied ADB command to clipboard");
        }
    }
}
