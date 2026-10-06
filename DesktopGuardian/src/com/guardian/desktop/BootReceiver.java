package com.guardian.desktop;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            android.util.Log.i("DesktopGuardian", "BootReceiver: 开机自启");
            // 无障碍服务会自动启动，不需要手动启动 Service
        }
    }
}
