package com.example.myapplication;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class MyBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if(intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)){
            Intent serviceIntent = new Intent(context, MyService.class);
            context.startForegroundService(serviceIntent);
        }

        if (intent.getAction().equals(Intent.ACTION_SHUTDOWN) || intent.getAction().equals(Intent.ACTION_PACKAGE_RESTARTED)) {
            // Перезапустите сервис
            Intent serviceIntent = new Intent(context, MyService.class);
            context.startForegroundService(serviceIntent);
        }
    }
}
