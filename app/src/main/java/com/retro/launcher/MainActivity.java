package com.retro.launcher;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.net.Uri;
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
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.Xml;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;

public class MainActivity extends Activity {
    // Weather location (Melbourne). Change these two numbers for another city.
    static final double LAT = -37.81, LON = 144.96;

    int BAR, PANEL, TXT, SELB, BART, SELT; // theme colours, loaded from settings

    static final String[] PAL_N = {"Navy", "Sky", "Black", "Grey", "White", "Red", "Orange", "Yellow", "Green", "Teal", "Purple", "Pink"};
    static final int[] PAL_V = {0xFF0B3C6E, 0xFF35AEEA, 0xFF101820, 0xFF5A6470, 0xFFFFFFFF, 0xFFC62828,
            0xFFEF6C00, 0xFFF9A825, 0xFF2E7D32, 0xFF00838F, 0xFF6A1B9A, 0xFFD81B60};
    static final String[] FONT_N = {"Pixel", "Condensed", "Sans", "Serif", "Mono"};
    static final int[] HG = {Gravity.LEFT, Gravity.CENTER_HORIZONTAL, Gravity.RIGHT};
    static final int[] VG = {Gravity.TOP, Gravity.CENTER_VERTICAL, Gravity.BOTTOM};

    static class Opt {
        final String key, label, unit;
        final int type, min, max, step, def; // type 0 number, 1 choice, 2 colour
        final String[] ch;
        Opt(String key, String label, int type, int min, int max, int step, int def, String unit, String[] ch) {
            this.key = key; this.label = label; this.type = type; this.min = min; this.max = max;
            this.step = step; this.def = def; this.unit = unit; this.ch = ch;
        }
        static Opt num(String k, String l, int min, int max, int step, int def, String unit) { return new Opt(k, l, 0, min, max, step, def, unit, null); }
        static Opt choice(String k, String l, int def, String[] ch) { return new Opt(k, l, 1, 0, ch.length - 1, 1, def, "", ch); }
        static Opt col(String k, String l, int def) { return new Opt(k, l, 2, 0, 11, 1, def, "", null); }
    }

    static final Opt O_CSIZE = Opt.num("c_size", "Clock size", 40, 100, 4, 68, "");
    static final Opt O_CV = Opt.choice("c_v", "Clock height", 1, new String[]{"Top", "Centre", "Bottom"});
    static final Opt O_CH = Opt.choice("c_h", "Clock side", 1, new String[]{"Left", "Centre", "Right"});
    static final Opt O_CF = Opt.choice("c_f", "Clock font", 0, FONT_N);
    static final Opt O_CC = Opt.col("c_c", "Clock colour", 4);
    static final Opt O_BAR = Opt.col("t_bar", "Bar colour", 0);
    static final Opt O_BARO = Opt.num("t_baro", "Bar opacity", 20, 100, 5, 88, "%");
    static final Opt O_SEL = Opt.col("t_sel", "Highlight", 1);
    static final Opt O_TXT = Opt.col("t_txt", "Text colour", 0);
    static final Opt O_PAN = Opt.col("t_pan", "Panel colour", 4);
    static final Opt O_PANO = Opt.num("t_pano", "Panel opacity", 20, 100, 5, 80, "%");
    static final Opt O_UIF = Opt.choice("t_f", "Menu font", 0, FONT_N);
    static final Opt O_KS = Opt.num("k_s", "Calendar scale", 60, 130, 10, 100, "%");
    static final Opt O_KO = Opt.num("k_o", "Calendar opacity", 10, 100, 10, 60, "%");
    static final Opt[] CLOCK = {O_CSIZE, O_CV, O_CH, O_CF, O_CC};
    static final Opt[] COLOURS = {O_BAR, O_BARO, O_SEL, O_TXT, O_PAN, O_PANO, O_UIF};
    static final Opt[] CALENDAR = {O_KS, O_KO};
    // Pages: swipe or D-pad left/right between 0..3
    static final int M_MENU = 0, M_HOME = 1, M_LETTERS = 2, M_CAL = 3, M_OPT = 4, M_PACK = 5, M_SET = 6;

    // 9x9 pixel icons
    static final String[] MAIL = {"         ", "#########", "##     ##", "# #   # #", "#  # #  #", "#   #   #", "#       #", "#########", "         "};
    static final String[] BELL = {"    #    ", "   ###   ", "  #####  ", "  #####  ", " ####### ", " ####### ", "#########", "         ", "    #    "};
    static final String[] BOLT = {"      ## ", "     ##  ", "    ##   ", "   ##### ", "     ##  ", "    ##   ", "   ##    ", "  ##     ", "         "};

    Typeface F, FB, pixel;
    Opt[] curOpts;
    String curTitle = "";
    SharedPreferences sp;
    int mode = M_HOME, sig = 0, selPos = 0, calY, calM;
    long lastWx = 0;
    TextView batt, noteCount, wx;
    HGauge sigG, batG;
    Sprite icoMail, icoAlarm, icoCharge;
    ListView list;
    ArrayAdapter<?> adapter;
    IntConsumer onOpen;
    TelephonyManager tm;
    Object cb;
    PhoneStateListener psl;
    GestureDetector gd;
    Resources packRes;
    String packPkg = "";
    final Map<String, String> packMap = new HashMap<>();
    final Handler h = new Handler(Looper.getMainLooper());
    final Runnable tick = new Runnable() {
        @Override public void run() {
            if (mode == M_HOME) refresh();
            if (System.currentTimeMillis() - lastWx > 1800000L) { lastWx = System.currentTimeMillis(); fetchWeather(); }
            h.postDelayed(this, 30000);
        }
    };

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    int val(Opt o) { return sp.getInt(o.key, o.def); }

    static float lum(int c) { return (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f; }

    static int dark(int c) { return 0xFF000000 | ((int) (Color.red(c) * 0.62f) << 16) | ((int) (Color.green(c) * 0.62f) << 8) | (int) (Color.blue(c) * 0.62f); }

    void loadTheme() {
        int bar = PAL_V[val(O_BAR)], sel = PAL_V[val(O_SEL)], pan = PAL_V[val(O_PAN)];
        BAR = ((val(O_BARO) * 255 / 100) << 24) | (bar & 0xFFFFFF);
        PANEL = ((val(O_PANO) * 255 / 100) << 24) | (pan & 0xFFFFFF);
        TXT = PAL_V[val(O_TXT)];
        SELB = sel;
        BART = lum(bar) > 0.7f ? 0xFF0B3C6E : Color.WHITE;
        SELT = lum(sel) > 0.7f ? 0xFF0B3C6E : Color.WHITE;
    }

    Typeface font(int i, boolean bold) {
        Typeface b = i == 0 ? pixel : Typeface.create(i == 1 ? "sans-serif-condensed" : i == 2 ? "sans-serif" : i == 3 ? "serif" : "monospace", Typeface.NORMAL);
        return bold ? Typeface.create(b, Typeface.BOLD) : b;
    }

    void applyFonts() { F = font(val(O_UIF), false); FB = font(val(O_UIF), true); }

    TextView tv(String s, int sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(bold ? FB : F);
        return t;
    }

    // ---------- custom views ----------

    // Horizontal segmented bar (signal = growing bars, battery = even blocks with a nub)
    class HGauge extends View {
        final int segs;
        final boolean grow, nub;
        float level;
        final Paint on = new Paint(), off = new Paint(), line = new Paint();

        HGauge(Context c, int segs, boolean grow, boolean nub) {
            super(c);
            this.segs = segs;
            this.grow = grow;
            this.nub = nub;
            on.setColor(0xFFE8FBFF);
            off.setColor(0x66062447);
            line.setColor(0xFF062447);
        }

        void set(float l) {
            level = l;
            if (!grow) on.setColor(l < 0.21f ? 0xFFFF6B6B : 0xFFE8FBFF);
            invalidate();
        }

        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), hg = getHeight(), gap = dp(2);
            float body = nub ? w - dp(4) : w;
            float sw = (body - gap * (segs - 1)) / segs;
            int lit = Math.round(level * segs);
            for (int i = 0; i < segs; i++) {
                float left = i * (sw + gap);
                float top = grow ? hg * (1f - (0.3f + 0.7f * (i + 1f) / segs)) : 0;
                cv.drawRect(left - 1, top - 1, left + sw + 1, hg, line);
                cv.drawRect(left, top, left + sw, hg - 1, i < lit ? on : off);
            }
            if (nub) cv.drawRect(body + 1, hg * 0.3f, w, hg * 0.7f, line);
        }
    }

    // Pixel-art status icon that lights up
    class Sprite extends View {
        final String[] g;
        final int onColor;
        boolean lit;
        final Paint p = new Paint();

        Sprite(Context c, String[] g, int onColor) { super(c); this.g = g; this.onColor = onColor; }

        void set(boolean b) { if (b != lit) { lit = b; invalidate(); } }

        @Override protected void onDraw(Canvas cv) {
            int n = 9, cell = Math.max(1, Math.min(getWidth(), getHeight()) / n);
            int ox = (getWidth() - cell * n) / 2, oy = (getHeight() - cell * n) / 2;
            if (lit) blit(cv, ox + 1, oy + 1, cell, 0xAA062447);
            blit(cv, ox, oy, cell, lit ? onColor : 0x44FFFFFF);
        }

        void blit(Canvas cv, int ox, int oy, int cell, int col) {
            p.setColor(col);
            for (int r = 0; r < n9(); r++)
                for (int c = 0; c < n9(); c++)
                    if (g[r].charAt(c) == '#')
                        cv.drawRect(ox + c * cell, oy + r * cell, ox + (c + 1) * cell, oy + (r + 1) * cell, p);
        }

        int n9() { return 9; }
    }

    // Glossy see-through blue selection bar
    class Gloss extends Drawable {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            float r = dp(4);
            RectF all = new RectF(b);
            RectF top = new RectF(b.left, b.top, b.right, b.top + b.height() / 2f);
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0, b.top, 0, b.bottom, (0xE0000000 | (SELB & 0xFFFFFF)), (0xE0000000 | (dark(SELB) & 0xFFFFFF)), Shader.TileMode.CLAMP));
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
        @Override public void onSignalStrengthsChanged(SignalStrength s) { sig = s.getLevel(); updateGauges(); }
    }

    // ---------- lifecycle ----------

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("retro", 0);
        try {
            pixel = Typeface.createFromAsset(getAssets(), "font.ttf");
        } catch (Exception e) {
            pixel = Typeface.create("sans-serif-condensed", Typeface.NORMAL);
        }
        loadTheme();
        applyFonts();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setNavigationBarColor(0xFF0B3C6E);
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE}, 1);
        gd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onFling(MotionEvent a, MotionEvent e, float vx, float vy) {
                if (a == null || e == null || mode > M_CAL) return false;
                float dx = e.getX() - a.getX(), dy = e.getY() - a.getY();
                if (Math.abs(dx) > dp(70) && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                    final int to = dx < 0 ? mode + 1 : mode - 1;
                    h.post(() -> go(to));
                    return true;
                }
                return false;
            }
        });
        listenSignal();
        loadPack();
        showHome();
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        listenSignal();
    }

    @Override protected void onDestroy() { super.onDestroy(); stopSignal(); }
    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); showHome(); }
    @Override protected void onResume() { super.onResume(); h.removeCallbacks(tick); h.post(tick); }
    @Override protected void onPause() { super.onPause(); h.removeCallbacks(tick); }
    @Override public void onBackPressed() {
        if (mode == M_SET || mode == M_PACK) showOptions();
        else if (mode != M_HOME) showHome();
    }

    @Override public boolean dispatchTouchEvent(MotionEvent ev) {
        gd.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    // D-pad: up/down moves the highlight, left/right changes page, centre opens
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        boolean nav = k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN
                || k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT
                || k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER;
        if (!nav) return super.dispatchKeyEvent(e);
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            boolean centre = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER;
            if (!(centre && e.getRepeatCount() > 0)) navKey(k);
        }
        return true;
    }

    void navKey(int k) {
        boolean isList = mode == M_MENU || mode == M_LETTERS || mode == M_OPT || mode == M_PACK || mode == M_SET;
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (isList) moveSel(-1); else if (mode == M_CAL) shiftMonth(calY, calM, -1);
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (isList) moveSel(1); else if (mode == M_CAL) shiftMonth(calY, calM, 1);
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (mode <= M_CAL) go(mode - 1); else if (mode == M_SET) adjust(-1);
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (mode <= M_CAL) go(mode + 1); else if (mode == M_SET) adjust(1);
                break;
            default:
                if (isList) openSel(); else if (mode == M_HOME) go(M_MENU);
        }
    }

    void moveSel(int d) {
        if (adapter == null || adapter.getCount() == 0) return;
        selPos = Math.max(0, Math.min(adapter.getCount() - 1, selPos + d));
        adapter.notifyDataSetChanged();
        list.smoothScrollToPosition(selPos);
    }

    void go(int p) {
        if (p < 0 || p > M_CAL || p == mode) return;
        if (p == M_MENU) showMenu();
        else if (p == M_HOME) showHome();
        else if (p == M_LETTERS) showLetters();
        else { Calendar n = Calendar.getInstance(); showCalendar(n.get(Calendar.YEAR), n.get(Calendar.MONTH)); }
    }

    // ---------- signal ----------

    void updateGauges() { if (sigG != null) sigG.set(sig / 4f); }

    void stopSignal() {
        try {
            if (tm != null) {
                if (cb != null) tm.unregisterTelephonyCallback((TelephonyCallback) cb);
                else if (psl != null) tm.listen(psl, PhoneStateListener.LISTEN_NONE);
            }
        } catch (Throwable t) { }
        cb = null;
        psl = null;
    }

    void listenSignal() {
        stopSignal();
        try {
            tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
            if (Build.VERSION.SDK_INT >= 31) {
                SigCb c = new SigCb();
                cb = c;
                tm.registerTelephonyCallback(getMainExecutor(), c);
            } else {
                psl = new PhoneStateListener() {
                    @Override public void onSignalStrengthsChanged(SignalStrength s) { sig = s.getLevel(); updateGauges(); }
                };
                tm.listen(psl, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
            }
        } catch (Throwable t) { }
    }

    // ---------- weather ----------

    String wxText(int c) {
        if (c == 0) return "Clear";
        if (c <= 2) return "Fine";
        if (c == 3) return "Cloudy";
        if (c == 45 || c == 48) return "Fog";
        if (c >= 51 && c <= 57) return "Drizzle";
        if (c >= 61 && c <= 67) return "Rain";
        if (c >= 71 && c <= 77) return "Snow";
        if (c >= 80 && c <= 82) return "Showers";
        if (c >= 85 && c <= 86) return "Snow";
        if (c >= 95) return "Storm";
        return "";
    }

    void fetchWeather() {
        new Thread(() -> {
            try {
                URL u = new URL("https://api.open-meteo.com/v1/forecast?latitude=" + LAT + "&longitude=" + LON
                        + "&current=temperature_2m,weather_code");
                HttpURLConnection c = (HttpURLConnection) u.openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String ln;
                while ((ln = br.readLine()) != null) sb.append(ln);
                br.close();
                JSONObject cur = new JSONObject(sb.toString()).getJSONObject("current");
                final String txt = Math.round(cur.getDouble("temperature_2m")) + "\u00b0C " + wxText(cur.getInt("weather_code"));
                sp.edit().putString("wx", txt).apply();
                runOnUiThread(() -> { if (wx != null) wx.setText(txt); });
            } catch (Exception e) { }
        }).start();
    }

    // ---------- shared UI helpers ----------

    LinearLayout page() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l; // transparent so the wallpaper shows through
    }

    LinearLayout bar(String left, View.OnClickListener lc, String right, View.OnClickListener rc) {
        LinearLayout b = new LinearLayout(this);
        b.setBackgroundColor(BAR);
        TextView a = tv(left, 20, true, BART), c = tv(right, 20, true, BART);
        a.setPadding(dp(12), dp(12), dp(12), dp(12));
        c.setPadding(dp(12), dp(12), dp(12), dp(12));
        c.setGravity(Gravity.RIGHT);
        a.setOnClickListener(lc);
        c.setOnClickListener(rc);
        b.addView(a, new LinearLayout.LayoutParams(0, -2, 1));
        b.addView(c, new LinearLayout.LayoutParams(0, -2, 1));
        return b;
    }

    LinearLayout bar3(String l, View.OnClickListener lc, String m, View.OnClickListener mc, String r, View.OnClickListener rc) {
        LinearLayout b = new LinearLayout(this);
        b.setBackgroundColor(BAR);
        String[] lab = {l, m, r};
        View.OnClickListener[] act = {lc, mc, rc};
        for (int i = 0; i < 3; i++) {
            TextView t = tv(lab[i], 20, i == 1, BART);
            t.setPadding(dp(8), dp(12), dp(8), dp(12));
            t.setGravity(i == 0 ? Gravity.LEFT : i == 1 ? Gravity.CENTER : Gravity.RIGHT);
            t.setOnClickListener(act[i]);
            b.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        }
        return b;
    }

    ListView newList() {
        ListView l = new ListView(this);
        l.setBackgroundColor(PANEL);
        l.setDivider(new ColorDrawable(0x330B3C6E));
        l.setDividerHeight(1);
        l.setSelector(new ColorDrawable(0));
        l.setFocusable(false);
        return l;
    }

    void listPage(String title, ListView l, View bottom) { listPage(title, null, l, bottom); }

    void listPage(String title, View extra, ListView l, View bottom) {
        LinearLayout p = page();
        TextView t = tv(title, 20, true, BART);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundColor(BAR);
        t.setPadding(dp(8), dp(8), dp(8), dp(8));
        p.addView(t, new LinearLayout.LayoutParams(-1, -2));
        if (extra != null) p.addView(extra, new LinearLayout.LayoutParams(-1, -2));
        p.addView(l, new LinearLayout.LayoutParams(-1, 0, 1));
        p.addView(bottom);
        setContentView(p);
    }

    // Tap a row to highlight it; tap it again, or press centre/middle key, to open it
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
        t.setTextColor(sel ? SELT : TXT);
        t.setShadowLayer(sel ? 2 : 0, 1, 1, 0x99062447);
    }

    <T extends CharSequence> ArrayAdapter<T> textAdapter(List<T> rows) {
        return new ArrayAdapter<T>(this, 0, rows) {
            @Override public View getView(int pos, View v, ViewGroup parent) {
                TextView t = (v instanceof TextView) ? (TextView) v : new TextView(MainActivity.this);
                t.setText(getItem(pos));
                t.setTextSize(18);
                t.setTypeface(F);
                t.setMaxLines(3);
                t.setEllipsize(TextUtils.TruncateAt.END);
                t.setPadding(dp(12), dp(10), dp(12), dp(10));
                styleRow(t, pos);
                return t;
            }
        };
    }

    // ---------- home ----------

    void showHome() {
        mode = M_HOME;
        int cc = PAL_V[val(O_CC)], hg = HG[val(O_CH)];
        LinearLayout p = page();

        LinearLayout top = new LinearLayout(this);
        top.setBackgroundColor(BAR);
        top.setPadding(dp(10), dp(6), dp(10), dp(6));
        top.setGravity(Gravity.CENTER_VERTICAL);
        wx = tv(sp.getString("wx", "Weather"), 15, true, BART);
        wx.setSingleLine(true);
        wx.setEllipsize(TextUtils.TruncateAt.END);
        top.addView(wx, new LinearLayout.LayoutParams(0, -2, 1));
        icoCharge = new Sprite(this, BOLT, 0xFF8BE04E);
        icoAlarm = new Sprite(this, BELL, 0xFFBFF0FF);
        icoMail = new Sprite(this, MAIL, 0xFFFFD54F);
        Sprite[] icos = {icoCharge, icoAlarm, icoMail};
        for (Sprite ic : icos) {
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(20), dp(20));
            ip.setMargins(0, 0, dp(6), 0);
            top.addView(ic, ip);
        }
        batt = tv("", 15, true, BART);
        top.addView(batt);
        p.addView(top);

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setGravity(VG[val(O_CV)]);
        mid.setPadding(dp(14), dp(10), dp(14), dp(10));
        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("HH:mm");
        clock.setTextSize(val(O_CSIZE));
        clock.setTextColor(cc);
        clock.setTypeface(font(val(O_CF), true));
        clock.setShadowLayer(6, 2, 2, 0xAA000000);
        clock.setGravity(hg);
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE d MMMM");
        date.setFormat24Hour("EEEE d MMMM");
        date.setTextSize(20);
        date.setTextColor(cc);
        date.setTypeface(font(val(O_CF), false));
        date.setShadowLayer(4, 1, 1, 0xAA000000);
        date.setGravity(hg);
        noteCount = tv("", 17, true, cc);
        noteCount.setShadowLayer(4, 1, 1, 0xAA000000);
        noteCount.setGravity(hg);
        noteCount.setPadding(0, dp(12), 0, 0);

        LinearLayout bars = new LinearLayout(this);
        bars.setGravity(hg);
        bars.setPadding(0, dp(16), 0, 0);
        sigG = new HGauge(this, 5, true, false);
        batG = new HGauge(this, 8, false, true);
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(dp(90), dp(22));
        gl.setMargins(dp(12), 0, dp(12), 0);
        bars.addView(sigG, gl);
        bars.addView(batG, gl);

        mid.addView(clock);
        mid.addView(date);
        mid.addView(noteCount);
        mid.addView(bars);
        mid.setOnLongClickListener(v -> { // long-press the middle to change wallpaper
            pickWallpaper();
            return true;
        });
        p.addView(mid, new LinearLayout.LayoutParams(-1, 0, 1));

        p.addView(bar("Menu", v -> go(M_MENU), "Letters", v -> go(M_LETTERS)));
        setContentView(p);
        updateGauges();
        refresh();
    }

    void pickWallpaper() {
        try { startActivity(Intent.createChooser(new Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper")); } catch (Exception e) { }
    }

    void refresh() {
        if (mode != M_HOME || batt == null) return;
        try {
            Intent i = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            int lvl = i.getIntExtra("level", 0), sc = i.getIntExtra("scale", 100);
            batt.setText((lvl * 100 / sc) + "%");
            batG.set(lvl / (float) sc);
            icoCharge.set(i.getIntExtra("plugged", 0) > 0);
        } catch (Exception e) { batt.setText(""); }
        try {
            if (tm != null && Build.VERSION.SDK_INT >= 28) {
                SignalStrength ss = tm.getSignalStrength();
                if (ss != null) sig = ss.getLevel();
            }
        } catch (Throwable t) { }
        updateGauges();
        int n = letters().size();
        noteCount.setText(NotifService.inst == null ? "" : n == 0 ? "No new letters" : n + (n == 1 ? " new letter" : " new letters"));
        icoMail.set(n > 0);
        try { icoAlarm.set(((AlarmManager) getSystemService(ALARM_SERVICE)).getNextAlarmClock() != null); } catch (Exception e) { }
    }

    // ---------- letters ----------

    List<StatusBarNotification> letters() {
        List<StatusBarNotification> out = new ArrayList<>();
        NotifService s = NotifService.inst;
        if (s == null) return out;
        try {
            for (StatusBarNotification n : s.getActiveNotifications()) {
                if (!n.isClearable()) continue;
                if ((n.getNotification().flags & Notification.FLAG_GROUP_SUMMARY) != 0) continue;
                Bundle e = n.getNotification().extras;
                if (e.getCharSequence("android.title") == null && e.getCharSequence("android.text") == null) continue;
                out.add(n);
            }
            Collections.sort(out, (a, b) -> Long.compare(b.getPostTime(), a.getPostTime()));
        } catch (Exception ex) { }
        return out;
    }

    void showLetters() {
        mode = M_LETTERS;
        final List<StatusBarNotification> items = letters();
        final List<String> rows = new ArrayList<>();
        for (StatusBarNotification n : items) {
            Bundle e = n.getNotification().extras;
            CharSequence t = e.getCharSequence("android.title"), x = e.getCharSequence("android.text");
            rows.add((t == null ? "" : t) + (x == null ? "" : "\n" + x));
        }
        if (NotifService.inst == null) {
            rows.add("Tap here to allow letter access");
            rows.add("Greyed out? Tap here");
        } else if (rows.isEmpty()) {
            rows.add("No letters");
        }
        setupList(textAdapter(rows), pos -> {
            if (NotifService.inst == null) {
                if (pos == 1) {
                    toast("Tap the 3 dots (top right), then Allow restricted settings");
                    startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
                } else {
                    startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                }
                return;
            }
            if (pos < items.size()) {
                openLetter(items.get(pos));
                h.postDelayed(() -> { if (mode == M_LETTERS) showLetters(); }, 600);
            }
        });
        listPage("Letters", list, bar3("Clear all", v -> clearAll(items), "Open", v -> openSel(), "Back", v -> showHome()));
    }

    void openLetter(StatusBarNotification n) {
        boolean ok = false;
        PendingIntent pi = n.getNotification().contentIntent;
        if (pi != null) {
            try {
                if (Build.VERSION.SDK_INT >= 34) {
                    ActivityOptions o = ActivityOptions.makeBasic();
                    o.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                    pi.send(this, 0, null, null, null, null, o.toBundle());
                } else {
                    pi.send();
                }
                ok = true;
            } catch (Exception e) { }
        }
        if (!ok) {
            try {
                Intent li = getPackageManager().getLaunchIntentForPackage(n.getPackageName());
                if (li != null) startActivity(li);
            } catch (Exception e) { }
        }
        try {
            if (n.isClearable() && NotifService.inst != null) NotifService.inst.cancelNotification(n.getKey());
        } catch (Exception e) { }
    }

    void clearAll(List<StatusBarNotification> items) {
        NotifService s = NotifService.inst;
        if (s == null) { toast("Letter access is off"); return; }
        try {
            String[] keys = new String[items.size()];
            for (int i = 0; i < keys.length; i++) keys[i] = items.get(i).getKey();
            if (keys.length > 0) s.cancelNotifications(keys);
            toast("Cleared " + keys.length);
        } catch (Exception e) {
            try { s.cancelAllNotifications(); } catch (Exception e2) { toast("Couldn't clear"); }
        }
        h.postDelayed(() -> { if (mode == M_LETTERS) showLetters(); }, 500);
    }

    // ---------- menu, options, icon packs ----------

    Drawable iconFor(ResolveInfo ri, PackageManager pm) {
        if (packRes != null) {
            String key = "ComponentInfo{" + ri.activityInfo.packageName + "/" + ri.activityInfo.name + "}";
            String dr = packMap.get(key);
            if (dr != null) {
                try {
                    int id = packRes.getIdentifier(dr, "drawable", packPkg);
                    if (id != 0) {
                        Drawable d = packRes.getDrawable(id, null);
                        if (d != null) return d;
                    }
                } catch (Exception e) { }
            }
        }
        return ri.loadIcon(pm);
    }

    // Reads the appfilter.xml used by ADW/Nova-style icon packs
    void loadPack() {
        packMap.clear();
        packRes = null;
        packPkg = sp.getString("pack", "");
        if (packPkg.isEmpty()) return;
        try {
            Resources r = getPackageManager().getResourcesForApplication(packPkg);
            XmlPullParser xp;
            int id = r.getIdentifier("appfilter", "xml", packPkg);
            if (id != 0) {
                xp = r.getXml(id);
            } else {
                InputStream in = r.getAssets().open("appfilter.xml");
                xp = Xml.newPullParser();
                xp.setInput(in, "utf-8");
            }
            int ev = xp.getEventType();
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && "item".equals(xp.getName())) {
                    String comp = xp.getAttributeValue(null, "component");
                    String dr = xp.getAttributeValue(null, "drawable");
                    if (comp != null && dr != null) packMap.put(comp, dr);
                }
                ev = xp.next();
            }
            packRes = r;
        } catch (Exception e) { packRes = null; }
    }

    void showMenu() {
        mode = M_MENU;
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
                Drawable d = iconFor(ri, pm);
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
        listPage("Menu", list, bar3("Options", v -> showOptions(), "Select", v -> openSel(), "Back", v -> showHome()));
    }

    void showOptions() {
        mode = M_OPT;
        List<String> rows = new ArrayList<>();
        rows.add("Clock settings");
        rows.add("Colour settings");
        rows.add("Calendar settings");
        rows.add("Icon pack");
        rows.add("Wallpaper");
        rows.add("Calendar photo");
        rows.add("Reset calendar photo");
        setupList(textAdapter(rows), pos -> {
            if (pos == 0) showSettings("Clock", CLOCK, 0);
            else if (pos == 1) showSettings("Colours", COLOURS, 0);
            else if (pos == 2) showSettings("Calendar", CALENDAR, 0);
            else if (pos == 3) showPacks();
            else if (pos == 4) pickWallpaper();
            else if (pos == 5) pickPhoto();
            else {
                new File(getFilesDir(), "calbg.jpg").delete();
                toast("Calendar photo reset");
            }
        });
        listPage("Options", list, bar("Select", v -> openSel(), "Back", v -> showMenu()));
    }

    CharSequence rowText(Opt o) {
        int v = val(o);
        if (o.type == 0) return o.label + ": " + v + o.unit;
        if (o.type == 1) return o.label + ": " + o.ch[v];
        SpannableStringBuilder sb = new SpannableStringBuilder(o.label + ": \u25A0 " + PAL_N[v]);
        int i = o.label.length() + 2;
        sb.setSpan(new ForegroundColorSpan(PAL_V[v]), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }

    View clockPreview() {
        LinearLayout b = new LinearLayout(this);
        b.setBackgroundColor(BAR);
        b.setGravity(Gravity.CENTER);
        TextView t = new TextView(this);
        t.setText("12:34");
        t.setTextSize(val(O_CSIZE));
        t.setTextColor(PAL_V[val(O_CC)]);
        t.setTypeface(font(val(O_CF), true));
        b.addView(t);
        return b;
    }

    // Left/right (or - and +) change the highlighted setting; centre/second tap = +
    void showSettings(String title, Opt[] opts, int sel) {
        mode = M_SET;
        curTitle = title;
        curOpts = opts;
        List<CharSequence> rows = new ArrayList<>();
        for (Opt o : opts) rows.add(rowText(o));
        rows.add("Reset to defaults");
        setupList(textAdapter(rows), pos -> {
            if (pos >= curOpts.length) resetOpts(); else adjust(1);
        });
        selPos = Math.min(sel, rows.size() - 1);
        list.setSelection(selPos);
        listPage(title, opts == CLOCK ? clockPreview() : null, list,
                bar3("-", v -> adjust(-1), "Back", v -> showOptions(), "+", v -> adjust(1)));
    }

    void adjust(int dir) {
        if (curOpts == null || selPos >= curOpts.length) return;
        Opt o = curOpts[selPos];
        int v = val(o);
        if (o.type == 0) v = Math.max(o.min, Math.min(o.max, v + dir * o.step));
        else { int n = o.type == 1 ? o.ch.length : PAL_V.length; v = (v + dir + n) % n; }
        sp.edit().putInt(o.key, v).apply();
        loadTheme();
        applyFonts();
        showSettings(curTitle, curOpts, selPos);
    }

    void resetOpts() {
        SharedPreferences.Editor ed = sp.edit();
        for (Opt o : curOpts) ed.remove(o.key);
        ed.apply();
        loadTheme();
        applyFonts();
        toast("Defaults restored");
        showSettings(curTitle, curOpts, selPos);
    }

    void showPacks() {
        mode = M_PACK;
        PackageManager pm = getPackageManager();
        final List<String> pkgs = new ArrayList<>();
        List<String> rows = new ArrayList<>();
        pkgs.add("");
        rows.add("Default icons");
        Set<String> seen = new HashSet<>();
        String[] acts = {"org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.anddoes.launcher.THEME", "com.gau.go.launcherex.theme"};
        for (String a : acts) {
            try {
                for (ResolveInfo ri : pm.queryIntentActivities(new Intent(a), 0)) {
                    String pk = ri.activityInfo.packageName;
                    if (seen.add(pk)) { pkgs.add(pk); rows.add(String.valueOf(ri.loadLabel(pm))); }
                }
            } catch (Exception e) { }
        }
        if (rows.size() == 1) rows.add("(No icon packs installed)");
        setupList(textAdapter(rows), pos -> {
            if (pos >= pkgs.size()) return;
            sp.edit().putString("pack", pkgs.get(pos)).apply();
            loadPack();
            toast(pos == 0 ? "Default icons" : "Icon pack applied");
            showMenu();
        });
        listPage("Icon pack", list, bar("Select", v -> openSel(), "Back", v -> showOptions()));
    }

    // ---------- calendar ----------

    void shiftMonth(int y, int m, int delta) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(y, m, 1);
        c.add(Calendar.MONTH, delta);
        showCalendar(c.get(Calendar.YEAR), c.get(Calendar.MONTH));
    }

    void pickPhoto() {
        try {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("image/*");
            startActivityForResult(Intent.createChooser(i, "Calendar photo"), 11);
        } catch (Exception e) { toast("No gallery found"); }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != 11 || res != RESULT_OK || data == null || data.getData() == null) return;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            InputStream in = getContentResolver().openInputStream(data.getData());
            BitmapFactory.decodeStream(in, null, o);
            in.close();
            int ss = 1;
            while (Math.max(o.outWidth, o.outHeight) / ss > 1100) ss *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = ss;
            in = getContentResolver().openInputStream(data.getData());
            Bitmap bm = BitmapFactory.decodeStream(in, null, o);
            in.close();
            FileOutputStream fo = new FileOutputStream(new File(getFilesDir(), "calbg.jpg"));
            bm.compress(Bitmap.CompressFormat.JPEG, 85, fo);
            fo.close();
            toast("Calendar photo set");
        } catch (Exception e) { toast("Couldn't load photo"); }
        if (mode == M_CAL) showCalendar(calY, calM);
    }

    // Monday-first month grid with week numbers; Sundays red; today highlighted
    void showCalendar(int y, int m) {
        mode = M_CAL;
        calY = y;
        calM = m;
        Calendar first = Calendar.getInstance();
        first.clear();
        first.setFirstDayOfWeek(Calendar.MONDAY);
        first.setMinimalDaysInFirstWeek(4);
        first.set(y, m, 1);
        int offset = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        int days = first.getActualMaximum(Calendar.DAY_OF_MONTH);
        Calendar pv = (Calendar) first.clone();
        pv.add(Calendar.MONTH, -1);
        int prevDays = pv.getActualMaximum(Calendar.DAY_OF_MONTH);
        Calendar now = Calendar.getInstance();
        boolean thisMonth = now.get(Calendar.YEAR) == y && now.get(Calendar.MONTH) == m;
        int today = now.get(Calendar.DAY_OF_MONTH);
        float sc = val(O_KS) / 100f;

        LinearLayout p = page();
        TextView title = tv(new SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(first.getTime()), 18, true, BART);
        title.setBackgroundColor(BAR);
        title.setPadding(dp(10), dp(8), dp(10), dp(8));
        title.setOnClickListener(v -> {
            Calendar n = Calendar.getInstance();
            showCalendar(n.get(Calendar.YEAR), n.get(Calendar.MONTH));
        });
        p.addView(title, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setBackgroundColor(((val(O_KO) * 255 / 100) << 24) | 0xFFFFFF);
        grid.setPadding(dp(4), dp(4), dp(4), dp(4));
        String[] names = {"Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"};
        LinearLayout head = new LinearLayout(this);
        head.setBackgroundColor(0xAA9FD3F0);
        head.addView(new View(this), new LinearLayout.LayoutParams(dp(26), 1));
        for (int i = 0; i < 7; i++) {
            TextView t = tv(names[i], Math.round(15 * sc), true, i == 6 ? 0xFFD32F2F : TXT);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(4), 0, dp(4));
            head.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        }
        grid.addView(head);
        for (int r = 0; r < 6; r++) {
            LinearLayout row = new LinearLayout(this);
            Calendar rs = (Calendar) first.clone();
            rs.add(Calendar.DAY_OF_MONTH, r * 7 - offset);
            TextView wk = tv(String.valueOf(rs.get(Calendar.WEEK_OF_YEAR)), Math.round(12 * sc), false, 0x990B3C6E);
            wk.setGravity(Gravity.CENTER);
            row.addView(wk, new LinearLayout.LayoutParams(dp(26), -1));
            for (int col = 0; col < 7; col++) {
                int d = r * 7 + col - offset + 1;
                boolean inMonth = d >= 1 && d <= days;
                int shown = d < 1 ? prevDays + d : d > days ? d - days : d;
                int color = !inMonth ? ((TXT & 0xFFFFFF) | 0x88000000) : col == 6 ? 0xFFD32F2F : TXT;
                TextView t = tv(String.valueOf(shown), Math.round(18 * sc), false, color);
                t.setGravity(Gravity.CENTER);
                GradientDrawable g = new GradientDrawable();
                g.setStroke(1, 0x44506070);
                g.setColor(0x22FFFFFF);
                if (thisMonth && inMonth && d == today) {
                    g.setColor(0xFF2A8FE0);
                    g.setStroke(1, 0xFFFFFFFF);
                    t.setTextColor(Color.WHITE);
                    t.setTypeface(FB);
                }
                t.setBackground(g);
                row.addView(t, new LinearLayout.LayoutParams(0, -1, 1));
            }
            grid.addView(row, new LinearLayout.LayoutParams(-1, 0, 1));
        }
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(-1, 0, 1);
        float shrink = 1f - Math.min(1f, sc);
        int mx = (int) (shrink * getResources().getDisplayMetrics().widthPixels * 0.5f);
        int my = (int) (shrink * getResources().getDisplayMetrics().heightPixels * 0.25f);
        glp.setMargins(mx, my, mx, my);
        p.addView(grid, glp);
        p.addView(bar3("Prev", v -> shiftMonth(y, m, -1), "Photo", v -> pickPhoto(), "Next", v -> shiftMonth(y, m, 1)));

        FrameLayout fl = new FrameLayout(this);
        File f = new File(getFilesDir(), "calbg.jpg");
        if (f.exists()) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageBitmap(BitmapFactory.decodeFile(f.getAbsolutePath()));
            fl.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        }
        fl.addView(p, new FrameLayout.LayoutParams(-1, -1));
        setContentView(fl);
    }
}
