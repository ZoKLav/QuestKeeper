package com.zoey.questkeeper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!QuestTuner.prefs(context).getBoolean(QuestTuner.KEY_BOOT_ENABLED, true)) {
            QuestTuner.log(context, "Boot event ignored; boot automation disabled");
            return;
        }
        QuestTuner.log(context, "Boot event received; starting keeper service");
        Intent svc = new Intent(context, KeeperService.class).setAction(KeeperService.ACTION_BOOT_APPLY);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(svc);
        else context.startService(svc);
    }
}
