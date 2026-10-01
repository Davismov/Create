package com.retro.launcher;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends Activity {
    static final int BLUE = 0xFF1E88C8, LIGHT = 0xFFA6DCF5, DARK = 0xFF0B3C6E, GOLD = 0xFFFFC107;
    int mode = 0; // 0 home, 1 menu, 2 notifications
    TextView batt, noteCount;
    ListView list;

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    TextView tv(String s, int sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    LinearLayout page() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{BLUE, LIGHT}));
        return l;
    }

    LinearLayout bar(String left, View.OnClickListener lc, String right, View.OnClickListener rc) {
        LinearLayout b = new LinearLayout(this);
        b.setBackgroundColor(DARK);
        TextView a = tv(left, 18, true, Color.WHITE);
        TextView c = tv(right, 18, true, Color.WHITE);
        a.setPadding(dp(12), dp(12), dp(12), dp(12));
        c.setPadding(dp(12), dp(12), dp(12), dp(12));
        c.setGravity(Gravity.RIGHT);
        a.setOnClickListener(lc);
        c.setOnClickListener(rc);
        b.addView(a, new LinearLayout.LayoutParams(0, -2, 1));
        b.addView(c, new LinearLayout.LayoutParams(0, -2, 1));
        return b;
    }

    ListView newList() {
        ListView l = new ListView(this);
        l.setDivider(new ColorDrawable(0x55FFFFFF));
        l.setDividerHeight(1);
        l.setSelector(new ColorDrawable(GOLD));
        return l;
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(DARK);
        getWindow().setNavigationBarColor(DARK);
        showHome();
    }

    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); showHome(); }
    @Override protected void onResume() { super.onResume(); if (mode == 0) refresh(); }
    @Override public void onBackPressed() { if (mode != 0) showHome(); }

    void showHome() {
        mode = 0;
        LinearLayout p = page();
        LinearLayout top = new LinearLayout(this);
        top.setBackgroundColor(DARK);
        top.setPadding(dp(10), dp(6), dp(10), dp(6));
        batt = tv("", 14, true, Color.WHITE);
        top.addView(batt, new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(tv("Retro", 14, true, Color.WHITE));
        p.addView(top);

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setGravity(Gravity.CENTER);
        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("HH:mm");
        clock.setTextSize(64);
        clock.setTextColor(Color.WHITE);
        clock.setTypeface(Typeface.DEFAULT_BOLD);
        clock.setGravity(Gravity.CENTER);
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE d MMMM");
        date.setFormat24Hour("EEEE d MMMM");
        date.setTextSize(18);
        date.setTextColor(Color.WHITE);
        date.setGravity(Gravity.CENTER);
        noteCount = tv("", 16, true, DARK);
        noteCount.setGravity(Gravity.CENTER);
        noteCount.setPadding(0, dp(16), 0, 0);
        mid.addView(clock);
        mid.addView(date);
        mid.addView(noteCount);
        p.addView(mid, new LinearLayout.LayoutParams(-1, 0, 1));

        p.addView(bar("Menu", v -> showMenu(), "Notes", v -> showNotes()));
        setContentView(p);
        refresh();
    }

    void refresh() {
        try {
            Intent i = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            int lvl = i.getIntExtra("level", 0), sc = i.getIntExtra("scale", 100);
            batt.setText("Batt " + (lvl * 100 / sc) + "%");
        } catch (Exception e) { batt.setText(""); }
        String s = "";
        try {
            if (NotifService.inst != null) {
                int n = NotifService.inst.getActiveNotifications().length;
                s = n == 0 ? "No new notifications" : n + " notification" + (n == 1 ? "" : "s");
            }
        } catch (Exception e) { }
        noteCount.setText(s);
    }

    void listPage(String title, ListView l) {
        LinearLayout p = page();
        TextView t = tv(title, 18, true, Color.WHITE);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundColor(DARK);
        t.setPadding(dp(8), dp(8), dp(8), dp(8));
        p.addView(t, new LinearLayout.LayoutParams(-1, -2));
        p.addView(l, new LinearLayout.LayoutParams(-1, 0, 1));
        p.addView(bar("Select", v -> {
            int pos = list.getSelectedItemPosition();
            if (pos >= 0 && list.getOnItemClickListener() != null)
                list.getOnItemClickListener().onItemClick(list, null, pos, pos);
        }, "Back", v -> showHome()));
        setContentView(p);
    }

    void showMenu() {
        mode = 1;
        final PackageManager pm = getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        final List<ResolveInfo> apps = new ArrayList<>(pm.queryIntentActivities(q, 0));
        Collections.sort(apps, new ResolveInfo.DisplayNameComparator(pm));
        list = newList();
        list.setAdapter(new ArrayAdapter<ResolveInfo>(this, 0, apps) {
            @Override public View getView(int pos, View v, ViewGroup parent) {
                TextView t = (v instanceof TextView) ? (TextView) v : new TextView(MainActivity.this);
                ResolveInfo ri = getItem(pos);
                t.setText(ri.loadLabel(pm));
                t.setTextSize(18);
                t.setTextColor(DARK);
                t.setGravity(Gravity.CENTER_VERTICAL);
                t.setPadding(dp(10), dp(8), dp(10), dp(8));
                Drawable d = ri.loadIcon(pm);
                d.setBounds(0, 0, dp(36), dp(36));
                t.setCompoundDrawables(d, null, null, null);
                t.setCompoundDrawablePadding(dp(12));
                return t;
            }
        });
        list.setOnItemClickListener((a, v, pos, id) -> {
            ResolveInfo ri = apps.get(pos);
            Intent li = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(li);
        });
        listPage("Menu", list);
    }

    void showNotes() {
        mode = 2;
        final List<String> rows = new ArrayList<>();
        if (NotifService.inst == null) {
            rows.add("Tap here to allow notification access");
        } else {
            try {
                for (StatusBarNotification n : NotifService.inst.getActiveNotifications()) {
                    Bundle e = n.getNotification().extras;
                    CharSequence t = e.getCharSequence("android.title");
                    CharSequence x = e.getCharSequence("android.text");
                    if (t == null && x == null) continue;
                    rows.add((t == null ? "" : t) + (x == null ? "" : "\n" + x));
                }
            } catch (Exception ex) { }
            if (rows.isEmpty()) rows.add("No notifications");
        }
        list = newList();
        list.setAdapter(new ArrayAdapter<String>(this, 0, rows) {
            @Override public View getView(int pos, View v, ViewGroup parent) {
                TextView t = (v instanceof TextView) ? (TextView) v : new TextView(MainActivity.this);
                t.setText(getItem(pos));
                t.setTextSize(16);
                t.setTextColor(DARK);
                t.setPadding(dp(12), dp(10), dp(12), dp(10));
                return t;
            }
        });
        list.setOnItemClickListener((a, v, pos, id) -> {
            if (NotifService.inst == null)
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        });
        listPage("Notifications", list);
    }
}
