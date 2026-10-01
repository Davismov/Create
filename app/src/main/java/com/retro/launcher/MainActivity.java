package com.retro.launcher;

import android.app.Activity;
import android.app.AlarmManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextClock;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.function.IntConsumer;
import java.util.Collections;
import java.util.List;

public class MainActivity extends Activity {
    // Colours: bars are slightly see-through so the phone wallpaper shows behind them
    static final int BAR = 0xE00B3C6E, PANEL = 0xCCFFFFFF, TXT = 0xFF0B3C6E, SEL = 0xFF35AEEA;
    static final Typeface F = Typeface.create("sans-serif-condensed", Typeface.NORMAL);
    static final Typeface FB = Typeface.create("sans-serif-condensed", Typeface.BOLD);

    int mode = 0; // 0 home, 1 menu, 2 letters
    int sig = 0;
    TextView batt, noteCount;
    Gauge sigG, batG;
    ListView list;
    Icon icoMail, icoAlarm, icoCharge;
    int selPos;
    ArrayAdapter<?> adapter;
    IntConsumer onOpen;
    TelephonyManager tm;
    Object cb;
    PhoneStateListener psl;
    final Handler h = new Handler(Looper.getMainLooper());
    final Runnable tick = new Runnable() {
        @Override public void run() {
            if (mode == 0) refresh();
            h.postDelayed(this, 30000);
        }
    };

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    TextView tv(String s, int sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(bold ? FB : F);
        return t;
    }

    // Segmented vertical bar (used for signal and battery)
    class Gauge extends View {
        final int segs;
        final boolean grow;
        float level;
        final Paint on = new Paint(), off = new Paint();

        Gauge(Context c, int segs, boolean grow) {
            super(c);
            this.segs = segs;
            this.grow = grow;
            on.setColor(0xFFBFF0FF);
            off.setColor(0x66062447);
        }

        void set(float l) { level = l; invalidate(); }

        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), hgt = getHeight(), gap = dp(3);
            float sh = (hgt - gap * (segs - 1)) / segs;
            int lit = Math.round(level * segs);
            for (int i = 0; i < segs; i++) {
                float bottom = hgt - i * (sh + gap), top = bottom - sh;
                float sw = grow ? w * (0.4f + 0.6f * (i + 1f) / segs) : w;
                float left = (w - sw) / 2f;
                cv.drawRect(left, top, left + sw, bottom, i < lit ? on : off);
            }
        }
    }

    // Small status icon that "lights up" when active
    class Icon extends View {
        final int type, onColor;
        boolean lit;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Icon(Context c, int type, int onColor) { super(c); this.type = type; this.onColor = onColor; }

        void set(boolean b) { if (b != lit) { lit = b; invalidate(); } }

        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight();
            int col = lit ? onColor : 0x44FFFFFF;
            p.setStrokeWidth(dp(2));
            if (lit) {
                p.setStyle(Paint.Style.FILL);
                p.setColor((onColor & 0xFFFFFF) | 0x33000000);
                cv.drawCircle(w / 2, h / 2, w / 2, p);
            }
            p.setColor(col);
            if (type == 0) { // letter
                p.setStyle(Paint.Style.FILL);
                cv.drawRoundRect(new RectF(w * 0.1f, h * 0.25f, w * 0.9f, h * 0.75f), dp(2), dp(2), p);
                p.setStyle(Paint.Style.STROKE);
                p.setColor(lit ? 0xFF0B3C6E : 0x33062447);
                Path f = new Path();
                f.moveTo(w * 0.1f, h * 0.27f);
                f.lineTo(w * 0.5f, h * 0.55f);
                f.lineTo(w * 0.9f, h * 0.27f);
                cv.drawPath(f, p);
            } else if (type == 1) { // alarm clock
                p.setStyle(Paint.Style.STROKE);
                cv.drawCircle(w / 2, h * 0.55f, w * 0.33f, p);
                p.setStyle(Paint.Style.FILL);
                cv.drawCircle(w * 0.2f, h * 0.2f, w * 0.12f, p);
                cv.drawCircle(w * 0.8f, h * 0.2f, w * 0.12f, p);
                p.setStyle(Paint.Style.STROKE);
                cv.drawLine(w / 2, h * 0.55f, w / 2, h * 0.35f, p);
                cv.drawLine(w / 2, h * 0.55f, w * 0.65f, h * 0.62f, p);
            } else { // charging bolt
                p.setStyle(Paint.Style.FILL);
                Path b = new Path();
                b.moveTo(w * 0.58f, h * 0.08f);
                b.lineTo(w * 0.25f, h * 0.55f);
                b.lineTo(w * 0.47f, h * 0.55f);
                b.lineTo(w * 0.38f, h * 0.92f);
                b.lineTo(w * 0.78f, h * 0.42f);
                b.lineTo(w * 0.56f, h * 0.42f);
                b.close();
                cv.drawPath(b, p);
            }
        }
    }

    // Glossy see-through blue selection bar: shiny pale top half, deeper blue bottom half
    class Gloss extends Drawable {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            float r = dp(4);
            RectF all = new RectF(b);
            RectF top = new RectF(b.left, b.top, b.right, b.top + b.height() / 2f);
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0, b.top, 0, b.bottom, 0xE03AB0EA, 0xE00A5CAF, Shader.TileMode.CLAMP));
            c.drawRoundRect(all, r, r, p);
            p.setShader(new LinearGradient(0, top.top, 0, top.bottom, 0xB0FFFFFF, 0x38FFFFFF, Shader.TileMode.CLAMP));
            Path path = new Path();
            path.addRoundRect(top, new float[]{r, r, r, r, 0, 0, 0, 0}, Path.Direction.CW);
            c.drawPath(path, p);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1));
            p.setColor(0xCCFFFFFF);
            c.drawRoundRect(new RectF(b.left + 0.5f, b.top + 0.5f, b.right - 0.5f, b.bottom - 0.5f), r, r, p);
        }
        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(ColorFilter f) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    class SigCb extends TelephonyCallback implements TelephonyCallback.SignalStrengthsListener {
        @Override public void onSignalStrengthsChanged(SignalStrength s) {
            sig = s.getLevel();
            updateGauges();
        }
    }

    void updateGauges() { if (sigG != null) sigG.set(sig / 4f); }

    void listenSignal() {
        try {
            tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
            if (Build.VERSION.SDK_INT >= 31) {
                SigCb c = new SigCb();
                cb = c;
                tm.registerTelephonyCallback(getMainExecutor(), c);
            } else {
                psl = new PhoneStateListener() {
                    @Override public void onSignalStrengthsChanged(SignalStrength s) {
                        sig = s.getLevel();
                        updateGauges();
                    }
                };
                tm.listen(psl, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
            }
        } catch (Throwable t) { }
    }

    LinearLayout page() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l; // transparent, so the wallpaper shows through
    }

    LinearLayout bar(String left, View.OnClickListener lc, String right, View.OnClickListener rc) {
        LinearLayout b = new LinearLayout(this);
        b.setBackgroundColor(BAR);
        TextView a = tv(left, 20, true, Color.WHITE);
        TextView c = tv(right, 20, true, Color.WHITE);
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
        l.setBackgroundColor(PANEL);
        l.setDivider(new ColorDrawable(0x330B3C6E));
        l.setDividerHeight(1);
        l.setSelector(new ColorDrawable(0));
        return l;
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN); // hides system status bar
        getWindow().setNavigationBarColor(0xFF0B3C6E);
        listenSignal();
        showHome();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        try {
            if (tm != null) {
                if (cb != null) tm.unregisterTelephonyCallback((TelephonyCallback) cb);
                else if (psl != null) tm.listen(psl, PhoneStateListener.LISTEN_NONE);
            }
        } catch (Throwable t) { }
    }

    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); showHome(); }
    @Override protected void onResume() { super.onResume(); h.removeCallbacks(tick); h.post(tick); }
    @Override protected void onPause() { super.onPause(); h.removeCallbacks(tick); }
    @Override public void onBackPressed() { if (mode != 0) showHome(); }

    void showHome() {
        mode = 0;
        LinearLayout p = page();

        LinearLayout top = new LinearLayout(this);
        top.setBackgroundColor(BAR);
        top.setPadding(dp(10), dp(6), dp(10), dp(6));
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(tv("Retro", 15, true, Color.WHITE), new LinearLayout.LayoutParams(0, -2, 1));
        icoCharge = new Icon(this, 2, 0xFF8BE04E);
        icoAlarm = new Icon(this, 1, 0xFFBFF0FF);
        icoMail = new Icon(this, 0, 0xFFFFD54F);
        Icon[] icos = {icoCharge, icoAlarm, icoMail};
        for (Icon ic : icos) {
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(24), dp(24));
            ip.setMargins(0, 0, dp(6), 0);
            top.addView(ic, ip);
        }
        batt = tv("", 15, true, Color.WHITE);
        top.addView(batt);
        p.addView(top);

        LinearLayout row = new LinearLayout(this);
        sigG = new Gauge(this, 4, true);
        batG = new Gauge(this, 8, false);
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(dp(16), -1);
        gl.setMargins(dp(8), dp(16), dp(8), dp(16));
        row.addView(sigG, gl);

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setGravity(Gravity.CENTER);
        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("HH:mm");
        clock.setTextSize(68);
        clock.setTextColor(Color.WHITE);
        clock.setTypeface(FB);
        clock.setShadowLayer(6, 2, 2, 0xAA000000);
        clock.setGravity(Gravity.CENTER);
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE d MMMM");
        date.setFormat24Hour("EEEE d MMMM");
        date.setTextSize(20);
        date.setTextColor(Color.WHITE);
        date.setTypeface(F);
        date.setShadowLayer(4, 1, 1, 0xAA000000);
        date.setGravity(Gravity.CENTER);
        noteCount = tv("", 17, true, Color.WHITE);
        noteCount.setShadowLayer(4, 1, 1, 0xAA000000);
        noteCount.setGravity(Gravity.CENTER);
        noteCount.setPadding(0, dp(16), 0, 0);
        mid.addView(clock);
        mid.addView(date);
        mid.addView(noteCount);
        // tap the middle for the calendar
        mid.setOnClickListener(v -> {
            Calendar n = Calendar.getInstance();
            showCalendar(n.get(Calendar.YEAR), n.get(Calendar.MONTH));
        });
        // long-press the middle to change wallpaper
        mid.setOnLongClickListener(v -> {
            try {
                startActivity(Intent.createChooser(new Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper"));
            } catch (Exception e) { }
            return true;
        });
        row.addView(mid, new LinearLayout.LayoutParams(0, -1, 1));
        row.addView(batG, gl);
        p.addView(row, new LinearLayout.LayoutParams(-1, 0, 1));

        p.addView(bar("Menu", v -> showMenu(), "Letters", v -> showLetters()));
        setContentView(p);
        updateGauges();
        refresh();
    }

    void refresh() {
        if (batt == null) return;
        try {
            Intent i = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            int lvl = i.getIntExtra("level", 0), sc = i.getIntExtra("scale", 100);
            batt.setText((lvl * 100 / sc) + "%");
            batG.set(lvl / (float) sc);
            icoCharge.set(i.getIntExtra("plugged", 0) > 0);
        } catch (Exception e) { batt.setText(""); }
        String s = "";
        try {
            if (NotifService.inst != null) {
                int n = NotifService.inst.getActiveNotifications().length;
                s = n == 0 ? "No new letters" : n + (n == 1 ? " new letter" : " new letters");
            }
        } catch (Exception e) { }
        noteCount.setText(s);
        try { icoMail.set(NotifService.inst != null && NotifService.inst.getActiveNotifications().length > 0); } catch (Exception e) { }
        try { icoAlarm.set(((AlarmManager) getSystemService(ALARM_SERVICE)).getNextAlarmClock() != null); } catch (Exception e) { }
    }

    void listPage(String title, ListView l, View bottom) {
        LinearLayout p = page();
        TextView t = tv(title, 20, true, Color.WHITE);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundColor(BAR);
        t.setPadding(dp(8), dp(8), dp(8), dp(8));
        p.addView(t, new LinearLayout.LayoutParams(-1, -2));
        p.addView(l, new LinearLayout.LayoutParams(-1, 0, 1));
        p.addView(bottom);
        setContentView(p);
    }

    // Tap a row to highlight it; tap it again (or press the middle key) to open it
    void setupList(ArrayAdapter<?> ad, IntConsumer open) {
        selPos = 0;
        adapter = ad;
        onOpen = open;
        list = newList();
        list.setAdapter(ad);
        list.setOnItemClickListener((a, v, pos, id) -> {
            if (pos == selPos) onOpen.accept(pos);
            else { selPos = pos; adapter.notifyDataSetChanged(); }
        });
    }

    void openSel() { if (adapter != null && selPos < adapter.getCount()) onOpen.accept(selPos); }

    void styleRow(TextView t, int pos) {
        boolean sel = pos == selPos;
        t.setBackground(sel ? new Gloss() : null);
        t.setTextColor(sel ? Color.WHITE : TXT);
        t.setShadowLayer(sel ? 2 : 0, 1, 1, 0x99062447);
    }

    LinearLayout bar3(String l, View.OnClickListener lc, String m, View.OnClickListener mc, String r, View.OnClickListener rc) {
        LinearLayout b = new LinearLayout(this);
        b.setBackgroundColor(BAR);
        String[] lab = {l, m, r};
        View.OnClickListener[] act = {lc, mc, rc};
        for (int i = 0; i < 3; i++) {
            TextView t = tv(lab[i], 20, i == 1, Color.WHITE);
            t.setPadding(dp(8), dp(12), dp(8), dp(12));
            t.setGravity(i == 0 ? Gravity.LEFT : i == 1 ? Gravity.CENTER : Gravity.RIGHT);
            t.setOnClickListener(act[i]);
            b.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        }
        return b;
    }

    void shiftMonth(int y, int m, int delta) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(y, m, 1);
        c.add(Calendar.MONTH, delta);
        showCalendar(c.get(Calendar.YEAR), c.get(Calendar.MONTH));
    }

    // Weeks start on Monday. Sundays are red, today is highlighted.
    void showCalendar(int y, int m) {
        mode = 3;
        Calendar first = Calendar.getInstance();
        first.clear();
        first.set(y, m, 1);
        int offset = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        int days = first.getActualMaximum(Calendar.DAY_OF_MONTH);
        Calendar pv = (Calendar) first.clone();
        pv.add(Calendar.MONTH, -1);
        int prevDays = pv.getActualMaximum(Calendar.DAY_OF_MONTH);
        Calendar now = Calendar.getInstance();
        boolean thisMonth = now.get(Calendar.YEAR) == y && now.get(Calendar.MONTH) == m;
        int today = now.get(Calendar.DAY_OF_MONTH);

        LinearLayout p = page();
        TextView title = tv(new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(first.getTime()), 20, true, Color.WHITE);
        title.setGravity(Gravity.CENTER);
        title.setBackgroundColor(BAR);
        title.setPadding(dp(8), dp(8), dp(8), dp(8));
        title.setOnClickListener(v -> {
            Calendar n = Calendar.getInstance();
            showCalendar(n.get(Calendar.YEAR), n.get(Calendar.MONTH));
        });
        p.addView(title, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setBackgroundColor(PANEL);
        grid.setPadding(dp(6), dp(6), dp(6), dp(6));
        String[] names = {"Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"};
        LinearLayout head = new LinearLayout(this);
        for (int i = 0; i < 7; i++) {
            TextView t = tv(names[i], 15, true, i == 6 ? 0xFFD32F2F : TXT);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(4), 0, dp(8));
            head.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        }
        grid.addView(head);
        for (int r = 0; r < 6; r++) {
            LinearLayout row = new LinearLayout(this);
            for (int col = 0; col < 7; col++) {
                int d = r * 7 + col - offset + 1;
                boolean inMonth = d >= 1 && d <= days;
                int shown = d < 1 ? prevDays + d : d > days ? d - days : d;
                int color = !inMonth ? 0x880B3C6E : col == 6 ? 0xFFD32F2F : TXT;
                TextView t = tv(String.valueOf(shown), 19, false, color);
                t.setGravity(Gravity.CENTER);
                if (thisMonth && inMonth && d == today) {
                    GradientDrawable g = new GradientDrawable();
                    g.setColor(0xFF2A8FE0);
                    g.setCornerRadius(dp(4));
                    t.setBackground(g);
                    t.setTextColor(Color.WHITE);
                    t.setTypeface(FB);
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
                lp.setMargins(dp(1), dp(1), dp(1), dp(1));
                row.addView(t, lp);
            }
            grid.addView(row, new LinearLayout.LayoutParams(-1, 0, 1));
        }
        p.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
        p.addView(bar3("Prev", v -> shiftMonth(y, m, -1), "Back", v -> showHome(), "Next", v -> shiftMonth(y, m, 1)));
        setContentView(p);
    }

    void showMenu() {
        mode = 1;
        final PackageManager pm = getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        final List<ResolveInfo> apps = new ArrayList<>(pm.queryIntentActivities(q, 0));
        Collections.sort(apps, new ResolveInfo.DisplayNameComparator(pm));
        setupList(new ArrayAdapter<ResolveInfo>(this, 0, apps) {
            @Override public View getView(int pos, View v, ViewGroup parent) {
                TextView t = (v instanceof TextView) ? (TextView) v : new TextView(MainActivity.this);
                ResolveInfo ri = getItem(pos);
                t.setText(ri.loadLabel(pm));
                t.setTextSize(20);
                t.setTypeface(F);
                t.setGravity(Gravity.CENTER_VERTICAL);
                t.setPadding(dp(10), dp(8), dp(10), dp(8));
                Drawable d = ri.loadIcon(pm);
                d.setBounds(0, 0, dp(36), dp(36));
                t.setCompoundDrawables(d, null, null, null);
                t.setCompoundDrawablePadding(dp(12));
                styleRow(t, pos);
                return t;
            }
        }, pos -> {
            ResolveInfo ri = apps.get(pos);
            Intent li = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(li);
        });
        listPage("Menu", list, bar("Select", v -> openSel(), "Back", v -> showHome()));
    }

    void showLetters() {
        mode = 2;
        final List<StatusBarNotification> items = new ArrayList<>();
        final List<String> rows = new ArrayList<>();
        NotifService s = NotifService.inst;
        if (s != null) {
            try {
                for (StatusBarNotification n : s.getActiveNotifications()) {
                    Bundle e = n.getNotification().extras;
                    CharSequence t = e.getCharSequence("android.title");
                    CharSequence x = e.getCharSequence("android.text");
                    if (t == null && x == null) continue;
                    items.add(n);
                    rows.add((t == null ? "" : t) + (x == null ? "" : "\n" + x));
                }
            } catch (Exception ex) { }
        }
        if (rows.isEmpty()) rows.add(s == null ? "Tap here to allow letter access" : "No letters");
        setupList(new ArrayAdapter<String>(this, 0, rows) {
            @Override public View getView(int pos, View v, ViewGroup parent) {
                TextView t = (v instanceof TextView) ? (TextView) v : new TextView(MainActivity.this);
                t.setText(getItem(pos));
                t.setTextSize(17);
                t.setTypeface(F);
                t.setMaxLines(3);
                t.setEllipsize(TextUtils.TruncateAt.END);
                t.setPadding(dp(12), dp(10), dp(12), dp(10));
                styleRow(t, pos);
                return t;
            }
        }, pos -> {
            if (NotifService.inst == null) {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                return;
            }
            if (pos < items.size()) {
                StatusBarNotification n = items.get(pos);
                try {
                    if (n.getNotification().contentIntent != null) n.getNotification().contentIntent.send();
                } catch (Exception e) { }
                try {
                    if (n.isClearable()) NotifService.inst.cancelNotification(n.getKey());
                } catch (Exception e) { }
                h.postDelayed(() -> { if (mode == 2) showLetters(); }, 500);
            }
        });
        listPage("Letters", list, bar3("Clear all", v -> {
            if (NotifService.inst != null) {
                NotifService.inst.cancelAllNotifications();
                h.postDelayed(() -> { if (mode == 2) showLetters(); }, 400);
            }
        }, "Open", v -> openSel(), "Back", v -> showHome()));
    }
}
