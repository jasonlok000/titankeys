package com.titankeys.reference;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Shows a notification that pops up but makes no sound and no vibration, to test swiping while a notification shows.
 * Send it from a computer: adb shell am broadcast -n com.titankeys.reference/.NotifyReceiver
 */
public final class NotifyReceiver extends BroadcastReceiver {
    private static final String CHANNEL = "silent-test";
    private static final long SHOW_MILLIS = 12_000;

    @Override
    public void onReceive(Context context, Intent intent) {
        NotificationManager notifications = context.getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Silent test", NotificationManager.IMPORTANCE_HIGH);
        channel.setSound(null, null);
        channel.enableVibration(false);
        notifications.createNotificationChannel(channel);
        notifications.notify(1, new Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("TitanKeys test")
            .setContentText("Keep swiping")
            .setTimeoutAfter(SHOW_MILLIS)
            .build());
    }
}
