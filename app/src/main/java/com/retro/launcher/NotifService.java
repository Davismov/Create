package com.retro.launcher;

import android.service.notification.NotificationListenerService;

public class NotifService extends NotificationListenerService {
    public static NotifService inst;

    @Override public void onListenerConnected() { inst = this; }
    @Override public void onListenerDisconnected() { inst = null; }
}
