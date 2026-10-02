package com.retro.launcher;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Stores reminders in the launcher's settings file and schedules them with the system alarm clock.
public class Reminders {
    public static class Item {
        public long id;
        public long time;
        public String text = "";
        public boolean fired;
    }

    static final String CHANNEL = "reminders";

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("retro", 0); }

    public static List<Item> load(Context c) {
        List<Item> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString("rem", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Item it = new Item();
                it.id = o.getLong("id");
                it.time = o.getLong("time");
                it.text = o.optString("text", "");
                it.fired = o.optBoolean("fired", false);
                out.add(it);
            }
        } catch (Exception e) { }
        Collections.sort(out, (a, b) -> a.fired != b.fired ? (a.fired ? 1 : -1) : Long.compare(a.time, b.time));
        return out;
    }

    public static void save(Context c, List<Item> l) {
        JSONArray a = new JSONArray();
        try {
            for (Item it : l) {
                JSONObject o = new JSONObject();
                o.put("id", it.id);
                o.put("time", it.time);
                o.put("text", it.text);
                o.put("fired", it.fired);
                a.put(o);
            }
        } catch (Exception e) { }
        prefs(c).edit().putString("rem", a.toString()).apply();
    }

    static PendingIntent firePi(Context c, long id) {
        Intent i = new Intent(c, ReminderReceiver.class);
        i.setAction("com.retro.launcher.REMIND");
        i.setData(Uri.parse("retro://reminder/" + id));
        i.putExtra("id", id);
        return PendingIntent.getBroadcast(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static PendingIntent openPi(Context c) {
        return PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    // setAlarmClock is exact and needs no special permission (it also lights the alarm icon)
    public static void schedule(Context c, Item it) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(it.time, openPi(c)), firePi(c, it.id));
        } catch (Exception e) { }
    }

    public static void cancel(Context c, long id) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            am.cancel(firePi(c, id));
        } catch (Exception e) { }
    }

    static void post(Context c, Item it) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH));
            Notification n = new Notification.Builder(c, CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_popup_reminder)
                    .setContentTitle("Reminder")
                    .setContentText(it.text)
                    .setStyle(new Notification.BigTextStyle().bigText(it.text))
                    .setCategory(Notification.CATEGORY_REMINDER)
                    .setContentIntent(openPi(c))
                    .setAutoCancel(true)
                    .build();
            nm.notify((int) (it.id & 0x7fffffff), n);
        } catch (Exception e) { }
    }

    public static void fire(Context c, long id) {
        List<Item> l = load(c);
        boolean changed = false;
        for (Item it : l) {
            if (it.id == id && !it.fired) { post(c, it); it.fired = true; changed = true; }
        }
        if (changed) save(c, l);
    }

    // After a reboot or app update: re-arm future reminders, and show any that were missed
    public static void rescheduleAll(Context c) {
        List<Item> l = load(c);
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (Item it : l) {
            if (it.fired) continue;
            if (it.time > now) schedule(c, it);
            else { post(c, it); it.fired = true; changed = true; }
        }
        if (changed) save(c, l);
    }
}
