package au.lite.launcher;

import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    static final int ROWS = 8;
    static class Item { String n; Intent i; }
    ArrayList<Item> items = new ArrayList<>();
    boolean menu; int sel, top, lastKey = -1;
    Screen v;

    final BroadcastReceiver tick = new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) { v.invalidate(); }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        v = new Screen(this);
        setContentView(v);
    }
    @Override protected void onResume() {
        super.onResume();
        load(); menu = false;
        registerReceiver(tick, new IntentFilter(Intent.ACTION_TIME_TICK));
        v.invalidate();
    }
    @Override protected void onPause() { super.onPause(); unregisterReceiver(tick); }
    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); menu = false; v.invalidate(); }

    void load() {
        PackageManager pm = getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        items.clear();
        for (ResolveInfo r : pm.queryIntentActivities(q, 0)) {
            if (r.activityInfo.packageName.equals(getPackageName())) continue;
            Item it = new Item();
            it.n = r.loadLabel(pm).toString();
            it.i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(r.activityInfo.packageName, r.activityInfo.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            items.add(it);
        }
        Collections.sort(items, (a, b) -> a.n.compareToIgnoreCase(b.n));
        if (sel >= items.size()) sel = 0;
    }

    void go(Intent i) { try { startActivity(i); } catch (Exception e) { } }
    void run(int n) { if (n >= 0 && n < items.size()) go(items.get(n).i); }
    void dial(String d) { go(new Intent(Intent.ACTION_DIAL, d.isEmpty() ? null : Uri.parse("tel:" + Uri.encode(d)))); }
    void contacts() { go(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CONTACTS)); }

    // left softkey / right softkey
    void left()  { if (menu) run(sel); else { menu = true; sel = 0; top = 0; } v.invalidate(); }
    void right() { if (menu) menu = false; else contacts(); v.invalidate(); }

    @Override public boolean onKeyDown(int k, KeyEvent e) {
        lastKey = k;
        int n = items.size();
        if (k == 1 || k == 82 || k == 23 || k == 66) { left(); return true; }   // soft-left, menu, centre, enter
        if (k == 2) { right(); return true; }                                    // soft-right
        if (menu) {
            if (k == 4) menu = false;
            else if (k == 19) sel = Math.max(0, sel - 1);
            else if (k == 20) sel = Math.min(n - 1, sel + 1);
            else if (k == 21) sel = Math.max(0, sel - ROWS);
            else if (k == 22) sel = Math.min(n - 1, sel + ROWS);
            else if (k >= 8 && k <= 8 + ROWS - 1) run(top + (k - 8));          // keys 1-8 jump
            else return super.onKeyDown(k, e);
            if (sel < top) top = sel;
            if (sel >= top + ROWS) top = sel - ROWS + 1;
        } else {
            if (k == 19) go(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING));
            else if (k == 20) contacts();
            else if (k == 21) go(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA));
            else if (k == 22) go(new Intent(Settings.ACTION_SETTINGS));
            else if (k == 5) dial("");
            else if (k >= 7 && k <= 16) dial(String.valueOf(k - 7));
            else if (k == 17) dial("*");
            else if (k == 18) dial("#");
            else if (k != 4) return super.onKeyDown(k, e);
        }
        v.invalidate();
        return true;
    }

    class Screen extends View {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Screen(Context c) { super(c); }

        void t(Canvas c, String x, float px, float py, float size, Paint.Align a) {
            p.setTextSize(size); p.setColor(Color.WHITE); p.setTextAlign(a); c.drawText(x, px, py, p);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_UP && e.getY() > getHeight() * 0.9f) {
                if (e.getX() < getWidth() / 2f) left(); else right();
            }
            return true;
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), s = w / 240f;
            p.setShader(new LinearGradient(0, 0, 0, h, 0xFF1B1F4B, 0xFF6A4C93, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p); p.setShader(null);
            p.setColor(0xAA000000); c.drawRect(0, h - 30 * s, w, h, p);

            if (!menu) {
                Intent b = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                int lvl = b == null ? 0 : b.getIntExtra("level", 0) * 100 / Math.max(1, b.getIntExtra("scale", 100));
                Date d = new Date();
                t(c, lvl + "%", w - 6 * s, 16 * s, 14 * s, Paint.Align.RIGHT);
                t(c, new SimpleDateFormat("h:mm", Locale.getDefault()).format(d), w / 2, h * 0.42f, 72 * s, Paint.Align.CENTER);
                t(c, new SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(d), w / 2, h * 0.42f + 28 * s, 16 * s, Paint.Align.CENTER);
                t(c, "\u2191Msgs \u2193Contacts \u2190Cam \u2192Settings", w / 2, h - 40 * s, 12 * s, Paint.Align.CENTER);
                if (lastKey >= 0) t(c, "key " + lastKey, 4 * s, h - 40 * s, 9 * s, Paint.Align.LEFT);
                t(c, "Menu", 8 * s, h - 9 * s, 17 * s, Paint.Align.LEFT);
                t(c, "Contacts", w - 8 * s, h - 9 * s, 17 * s, Paint.Align.RIGHT);
            } else {
                p.setColor(0xAA000000); c.drawRect(0, 0, w, 24 * s, p);
                t(c, "Menu", 8 * s, 18 * s, 16 * s, Paint.Align.LEFT);
                t(c, (sel + 1) + "/" + items.size(), w - 8 * s, 18 * s, 12 * s, Paint.Align.RIGHT);
                float rh = (h - 54 * s) / ROWS;
                for (int i = 0; i < ROWS && top + i < items.size(); i++) {
                    int idx = top + i; float y = 24 * s + i * rh;
                    if (idx == sel) { p.setColor(0x77FFFFFF); c.drawRect(0, y, w, y + rh, p); }
                    t(c, (i + 1) + ". " + items.get(idx).n, 10 * s, y + rh * 0.70f, 17 * s, Paint.Align.LEFT);
                }
                t(c, "Select", 8 * s, h - 9 * s, 17 * s, Paint.Align.LEFT);
                t(c, "Back", w - 8 * s, h - 9 * s, 17 * s, Paint.Align.RIGHT);
            }
        }
    }
}
