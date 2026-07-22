package com.zoey.questkeeper;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(13, 5, 7);
    private static final int PANEL = Color.rgb(27, 10, 14);
    private static final int PANEL_2 = Color.rgb(42, 14, 20);
    private static final int PANEL_PRESS = Color.rgb(82, 18, 29);
    private static final int TEXT = Color.rgb(255, 244, 246);
    private static final int MUTED = Color.rgb(203, 164, 171);
    private static final int ACCENT = Color.rgb(229, 28, 58);
    private static final int ACCENT_SOFT = Color.argb(145, 229, 28, 58);
    private static final int GOOD = Color.rgb(255, 104, 124);
    private static final int WARN = Color.rgb(255, 178, 92);
    private static final int BAD = Color.rgb(255, 66, 80);

    private SharedPreferences prefs;
    private TextView statusText;
    private TextView settingText;
    private TextView logText;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = QuestTuner.prefs(this);
        QuestTuner.seedDefaults(this);

        Window w = getWindow();
        w.setStatusBarColor(BG);
        w.setNavigationBarColor(BG);
        w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        startKeeper(null);
        setContentView(buildUi());
        refreshStatus();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(18), dp(22), dp(22));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(24), dp(24), dp(24), dp(22));
        hero.setBackground(gradient(new int[]{Color.rgb(69, 13, 24), Color.rgb(24, 7, 11)}, dp(26)));
        root.addView(hero, lp(-1, -2, 0, 0, 0, dp(16)));

        TextView title = label("QuestKeeper", 31, TEXT, true);
        hero.addView(title);
        TextView sub = label("Boot-time prox_close, timeout hardening, and Quest ADB-over-Wi‑Fi with modern random-port discovery plus a TCP/IP :5555 fallback path.", 15, MUTED, false);
        sub.setPadding(0, dp(8), 0, 0);
        hero.addView(sub);

        statusText = label("Checking status…", 15, TEXT, false);
        statusText.setPadding(0, dp(14), 0, 0);
        hero.addView(statusText);

        LinearLayout actions = card(root, "Controls");
        actions.addView(button("Apply everything now", () -> {
            startKeeper(KeeperService.ACTION_APPLY_NOW);
            refreshStatusSoon();
        }));
        actions.addView(button("Send prox_close now", () -> {
            QuestTuner.sendProxClose(this, -1);
            refreshStatusSoon();
        }));
        actions.addView(button("Enable modern ADB Wi-Fi + discover port", () -> {
            boolean ok = AdbWireless.enableWirelessAdb(this, prefs.getBoolean(QuestTuner.KEY_ADB_REQUIRE_USB, true));
            if (ok) discoverAdbPort();
            refreshStatusSoon();
        }));
        actions.addView(button("Enable TCP/IP fallback :5555", () -> {
            enableTcpipFallback();
            refreshStatusSoon();
        }));
        actions.addView(button("Wireless first, then TCP/IP :5555 handoff", () -> {
            enableWirelessThenTcpipHandoff();
            refreshStatusSoon();
        }));
        actions.addView(button("Discover current ADB Wi-Fi port", this::discoverAdbPort));
        actions.addView(button("Copy last adb command", () -> {
            AdbWireless.copyLastCommand(this);
            refreshStatusSoon();
        }));
        actions.addView(button("Disable ADB Wi-Fi", () -> {
            AdbWireless.disableWirelessAdb(this);
            refreshStatusSoon();
        }));
        actions.addView(button("Restore sane timeout / prox behavior", () -> {
            QuestTuner.restoreSaneTimeouts(this);
            refreshStatusSoon();
        }));

        LinearLayout toggles = card(root, "Automation");
        toggles.addView(toggle("Run 30 seconds after boot", QuestTuner.KEY_BOOT_ENABLED, true, (buttonView, isChecked) -> refreshStatus()));
        toggles.addView(toggle("Reapply every 15 minutes", QuestTuner.KEY_REAPPLY, true, (buttonView, isChecked) -> startKeeper(isChecked ? KeeperService.ACTION_APPLY_NOW : KeeperService.ACTION_REFRESH_WAKELOCK)));
        toggles.addView(toggle("Persistent screen wake lock", QuestTuner.KEY_SCREEN_WAKELOCK, true, (buttonView, isChecked) -> startKeeper(KeeperService.ACTION_REFRESH_WAKELOCK)));
        toggles.addView(toggle("Stay awake when AC/USB/wireless charging", QuestTuner.KEY_STAY_AWAKE_CHARGING, true, (buttonView, isChecked) -> {
            if (isChecked) QuestTuner.applyStayAwakeWhilePlugged(this);
            refreshStatusSoon();
        }));
        toggles.addView(toggle("Auto-enable ADB Wi-Fi during profile apply", QuestTuner.KEY_ADB_WIFI_AUTO, true, (buttonView, isChecked) -> {
            if (isChecked) {
                boolean ok = AdbWireless.enableWirelessAdb(this, prefs.getBoolean(QuestTuner.KEY_ADB_REQUIRE_USB, true));
                if (ok) discoverAdbPort();
            }
            refreshStatusSoon();
        }));
        toggles.addView(toggle("Prefer TCP/IP :5555 fallback during profile apply", QuestTuner.KEY_ADB_TCPIP_FALLBACK, false, (buttonView, isChecked) -> refreshStatusSoon()));
        toggles.addView(toggle("Require USB power before enabling ADB Wi-Fi", QuestTuner.KEY_ADB_REQUIRE_USB, true, (buttonView, isChecked) -> refreshStatusSoon()));

        LinearLayout perms = card(root, "Permissions");
        perms.addView(button("Open Modify System Settings screen", () -> {
            try { startActivity(QuestTuner.writeSettingsIntent(this)); }
            catch (Exception e) { QuestTuner.log(this, "Could not open WRITE_SETTINGS page: " + e.getMessage()); }
        }));
        perms.addView(button("Request battery optimization exemption", () -> {
            try { startActivity(QuestTuner.batteryIntent(this)); }
            catch (Exception e) { QuestTuner.log(this, "Could not open battery exemption page: " + e.getMessage()); }
        }));

        LinearLayout info = card(root, "Live readout");
        settingText = label("", 14, MUTED, false);
        settingText.setTypeface(Typeface.MONOSPACE);
        info.addView(settingText);

        LinearLayout logs = card(root, "Log");
        ScrollView logScroll = new ScrollView(this);
        logScroll.setFillViewport(false);
        logScroll.setBackground(solid(Color.rgb(14, 5, 8), dp(14), Color.argb(80, 229, 28, 58), 1));
        logScroll.setPadding(dp(10), dp(10), dp(10), dp(10));
        if (Build.VERSION.SDK_INT >= 21) logScroll.setNestedScrollingEnabled(true);
        logText = label("", 13, MUTED, false);
        logText.setTypeface(Typeface.MONOSPACE);
        logScroll.addView(logText, new ScrollView.LayoutParams(-1, -2));
        logs.addView(logScroll, lp(-1, dp(230), 0, 0, 0, dp(10)));
        logs.addView(button("Refresh log", this::refreshStatus));
        logs.addView(button("Clear log", () -> {
            prefs.edit().remove(QuestTuner.KEY_LAST_LOG).apply();
            refreshStatus();
        }));

        TextView footer = label("TCP/IP :5555 mode is included as a fallback. The app tries local adb/setprop first; if Android refuses that, it copies the PC-side command you can run from an already authorized ADB session.", 13, MUTED, false);
        footer.setPadding(dp(2), dp(12), dp(2), 0);
        root.addView(footer);
        return scroll;
    }

    private LinearLayout card(LinearLayout root, String heading) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(18));
        card.setBackground(solid(PANEL, dp(22), Color.argb(80, 229, 28, 58), 1));
        root.addView(card, lp(-1, -2, 0, 0, 0, dp(14)));
        TextView h = label(heading, 18, TEXT, true);
        h.setPadding(0, 0, 0, dp(10));
        card.addView(h);
        return card;
    }

    private Switch toggle(String text, String key, boolean def, CompoundButton.OnCheckedChangeListener after) {
        Switch s = new Switch(this);
        s.setText(text);
        s.setTextColor(TEXT);
        s.setTextSize(15);
        s.setPadding(0, dp(8), 0, dp(8));
        s.setChecked(prefs.getBoolean(key, def));
        s.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(key, isChecked).apply();
            QuestTuner.log(this, text + ": " + (isChecked ? "enabled" : "disabled"));
            if (after != null) after.onCheckedChanged(buttonView, isChecked);
        });
        return s;
    }

    private Button button(String text, final Runnable action) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(TEXT);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(10), dp(11), dp(10), dp(11));
        b.setBackground(buttonBackground());
        LinearLayout.LayoutParams p = lp(-1, -2, 0, dp(7), 0, dp(7));
        b.setLayoutParams(p);
        b.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                v.setAlpha(0.72f);
                v.setScaleX(0.985f);
                v.setScaleY(0.985f);
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                v.setAlpha(1f);
                v.setScaleX(1f);
                v.setScaleY(1f);
            }
            return false;
        });
        b.setOnClickListener(v -> {
            statusText.setText("Pressed: " + text);
            statusText.setTextColor(ACCENT);
            action.run();
        });
        return b;
    }

    private StateListDrawable buttonBackground() {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, solid(PANEL_PRESS, dp(16), Color.argb(255, 255, 74, 96), 2));
        states.addState(new int[]{android.R.attr.state_focused}, solid(Color.rgb(63, 17, 26), dp(16), Color.argb(220, 255, 74, 96), 2));
        states.addState(new int[]{}, solid(PANEL_2, dp(16), ACCENT_SOFT, 1));
        return states;
    }

    private void discoverAdbPort() {
        statusText.setText("Discovering ADB mDNS service…");
        statusText.setTextColor(ACCENT);
        AdbWireless.discoverPort(this, 12_000L, new AdbWireless.Listener() {
            @Override public void onUpdate(String status) {
                runOnUiThread(() -> {
                    statusText.setText(status);
                    refreshStatusSoon();
                });
            }

            @Override public void onFound(String host, int port, String command) {
                runOnUiThread(() -> {
                    statusText.setText("ADB Wi-Fi ready: " + command);
                    statusText.setTextColor(GOOD);
                    refreshStatusSoon();
                });
            }
        });
    }

    private void enableTcpipFallback() {
        statusText.setText("Trying TCP/IP fallback on :5555…");
        statusText.setTextColor(ACCENT);
        AdbWireless.enableTcpipFallback(this, prefs.getBoolean(QuestTuner.KEY_ADB_REQUIRE_USB, true), new AdbWireless.Listener() {
            @Override public void onUpdate(String status) {
                runOnUiThread(() -> {
                    statusText.setText(status);
                    refreshStatusSoon();
                });
            }

            @Override public void onFound(String host, int port, String command) {
                runOnUiThread(() -> {
                    statusText.setText("TCP/IP fallback ready: " + command);
                    statusText.setTextColor(GOOD);
                    refreshStatusSoon();
                });
            }
        });
    }

    private void enableWirelessThenTcpipHandoff() {
        statusText.setText("Preparing TCP/IP handoff…");
        statusText.setTextColor(ACCENT);
        AdbWireless.enableWirelessThenTcpipHandoff(this, prefs.getBoolean(QuestTuner.KEY_ADB_REQUIRE_USB, true), new AdbWireless.Listener() {
            @Override public void onUpdate(String status) {
                runOnUiThread(() -> {
                    statusText.setText(status);
                    refreshStatusSoon();
                });
            }

            @Override public void onFound(String host, int port, String command) {
                runOnUiThread(() -> {
                    statusText.setText("TCP/IP handoff command ready: " + command);
                    statusText.setTextColor(GOOD);
                    refreshStatusSoon();
                });
            }
        });
    }

    private void startKeeper(String action) {
        Intent i = new Intent(this, KeeperService.class);
        if (action != null) i.setAction(action);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
    }

    private void refreshStatusSoon() {
        statusText.postDelayed(this::refreshStatus, 500);
    }

    private void refreshStatus() {
        boolean write = QuestTuner.hasWriteSettings(this);
        boolean secure = QuestTuner.hasWriteSecureSettings(this);
        boolean battery = QuestTuner.isIgnoringBatteryOptimizations(this);
        long last = prefs.getLong(QuestTuner.KEY_LAST_APPLY, 0);
        String lastText = last == 0 ? "never" : new SimpleDateFormat("MMM d, HH:mm:ss", Locale.US).format(new Date(last));
        statusText.setText("System write: " + dot(write) + "   Secure write: " + dot(secure) + "   Battery exempt: " + dot(battery) + "\nLast apply: " + lastText);
        statusText.setTextColor(write && secure ? GOOD : WARN);

        StringBuilder sb = new StringBuilder();
        sb.append("screen_off_timeout = ").append(QuestTuner.readSystemSetting(this, "system", Settings.System.SCREEN_OFF_TIMEOUT)).append('\n');
        sb.append("sleep_timeout      = ").append(QuestTuner.readSystemSetting(this, "secure", "sleep_timeout")).append('\n');
        sb.append("stay_on_plugged    = ").append(QuestTuner.readSystemSetting(this, "global", "stay_on_while_plugged_in")).append('\n');
        sb.append("adb_enabled        = ").append(QuestTuner.readSystemSetting(this, "global", "adb_enabled")).append('\n');
        sb.append("adb_wifi_enabled   = ").append(QuestTuner.readSystemSetting(this, "global", "adb_wifi_enabled")).append('\n');
        sb.append("usb_powered        = ").append(AdbWireless.isUsbPowered(this) ? "1" : "0").append('\n');
        sb.append("wifi_ip            = ").append(nullText(AdbWireless.getWifiAddressString(this))).append('\n');
        sb.append("prefer_tcpip       = ").append(prefs.getBoolean(QuestTuner.KEY_ADB_TCPIP_FALLBACK, false) ? "1" : "0").append('\n');
        sb.append("last_adb_command   = ").append(prefs.getString(QuestTuner.KEY_LAST_ADB_COMMAND, "none"));
        settingText.setText(sb.toString());
        logText.setText(prefs.getString(QuestTuner.KEY_LAST_LOG, "No log entries yet."));
    }

    private String nullText(String s) { return s == null || s.isEmpty() ? "unknown" : s; }
    private String dot(boolean b) { return b ? "OK" : "MISSING"; }

    private TextView label(String text, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setLineSpacing(0, 1.08f);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private GradientDrawable solid(int color, int radius, int stroke, int strokeWidth) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        if (strokeWidth > 0) g.setStroke(dp(strokeWidth), stroke);
        return g;
    }

    private GradientDrawable gradient(int[] colors, int radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        g.setCornerRadius(radius);
        g.setStroke(dp(1), Color.argb(120, 229, 28, 58));
        return g;
    }

    private LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(l, t, r, b);
        return p;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
