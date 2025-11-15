package com.adonai.GsmNotify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Created by adonai on 08.02.15.
 */
public class ScreenOnReceiver extends BroadcastReceiver {
    private static final String SCREEN_ON_RECEIVED = "android.intent.action.SCREEN_ON";
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (action.equalsIgnoreCase(SCREEN_ON_RECEIVED)) {
            Intent broadcastIntent = new Intent(context, SMSReceiveService.class);
            broadcastIntent.putExtra("stop_alarm", true);
            SMSReceiveService.enqueueWork(context, broadcastIntent);
        }
    }
}
