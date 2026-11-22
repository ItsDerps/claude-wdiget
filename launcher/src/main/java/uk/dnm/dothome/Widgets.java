package uk.dnm.dothome;

import android.Manifest;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.CalendarContract;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Widgets on home: Dot Home's own (drawn in dots, made to fit) and any app's Android widget.
 * Stored as a list: "b:battery", "b:clock", "b:next", "b:music", "b:weather", "b:torch", "a:&lt;id&gt;".
 */
final class Widgets {

    static final String[][] BUILT_IN = {
            {"battery", "Battery", "A ring of dots, red while charging"},
            {"clock", "Dot clock", "Sixty dots, hands made of dots"},
            {"tiles", "Quick tiles", "Torch, Wi‑Fi, Bluetooth and ring mode"},
            {"torch", "Torch", "One tap on, one tap off"},
            {"next", "Next up", "Your next event, one tap to open"},
            {"music", "Now playing", "What's playing, with play and pause"},
            {"weather", "Weather", "Temperature in dots and the day's trend"},
    };

    static final int HOST_ID = 0x0D07;

    private final Context ctx;
    final AppWidgetHost host;
    final AppWidgetManager manager;

    Widgets(Context c) {
        ctx = c;
        host = new AppWidgetHost(c.getApplicationContext(), HOST_ID);
        manager = AppWidgetManager.getInstance(c);
    }

    static List<String> list(Context c) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(Prefs.get(c).raw().getString("widgets", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (Exception ignored) { }
        return out;
    }

    static void save(Context c, List<String> l) {
        JSONArray a = new JSONArray();
        for (String s : l) a.put(s);
        Prefs.get(c).raw().edit().putString("widgets", a.toString()).apply();
    }

    static void add(Context c, String w) {
        List<String> l = list(c);
        if (w.startsWith("b:") && l.contains(w)) return;
        l.add(w);
        save(c, l);
    }

    void remove(String w) {
        List<String> l = list(ctx);
        l.remove(w);
        save(ctx, l);
        if (w.startsWith("a:")) {
            try { host.deleteAppWidgetId(Integer.parseInt(w.substring(2))); } catch (Exception ignored) { }
        }
    }

    static boolean isHalf(String w) {
        return w.equals("b:battery") || w.equals("b:clock") || w.equals("b:torch") || w.equals("b:tiles");
    }

    // ------------------------------------------------------------------ building

    interface Remover { void remove(String w); }

    /** Fill the home widget area. In edit mode each widget gets a remove button. */
    void build(LinearLayout box, boolean edit, Remover remover) {
        box.removeAllViews();
        List<String> l = list(ctx);
        LinearLayout pairRow = null;
        for (String w : l) {
            View v = make(w);
            if (v == null) continue;
            View framed = frame(w, v, edit, remover, isHalf(w));
            if (isHalf(w)) {
                if (pairRow == null) {
                    pairRow = new LinearLayout(ctx);
                    LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
                    rlp.bottomMargin = Ui.dpi(ctx, 10);
                    box.addView(pairRow, rlp);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                    lp.rightMargin = Ui.dpi(ctx, 5);
                    pairRow.addView(framed, lp);
                } else {
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                    lp.leftMargin = Ui.dpi(ctx, 5);
                    pairRow.addView(framed, lp);
                    pairRow = null;
                }
            } else {
                pairRow = null;
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, heightFor(w));
                lp.bottomMargin = Ui.dpi(ctx, 10);
                box.addView(framed, lp);
            }
        }
        if (pairRow != null && pairRow.getChildCount() == 1) {
            View spacer = new View(ctx);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 1, 1f);
            lp.leftMargin = Ui.dpi(ctx, 5);
            pairRow.addView(spacer, lp);
        }
    }

    private int heightFor(String w) {
        switch (w) {
            case "b:next": return Ui.dpi(ctx, 84);
            case "b:music": return Ui.dpi(ctx, 88);
            case "b:weather": return Ui.dpi(ctx, 124);
            default:
                if (w.startsWith("a:")) {
                    try {
                        AppWidgetProviderInfo info = manager.getAppWidgetInfo(Integer.parseInt(w.substring(2)));
                        if (info != null) {
                            int h = Math.max(info.minHeight, info.minResizeHeight > 0 ? info.minResizeHeight : 0);
                            return (int) Ui.clamp(Math.max(h, Ui.dp(ctx, 90)), Ui.dp(ctx, 70), Ui.dp(ctx, 280));
                        }
                    } catch (Exception ignored) { }
                }
                return Ui.dpi(ctx, 120);
        }
    }

    private View make(String w) {
        switch (w) {
            case "b:battery": return new BatteryW(ctx);
            case "b:clock": return new ClockW(ctx);
            case "b:torch": return new TorchW(ctx);
            case "b:tiles": return new TilesW(ctx);
            case "b:next": return new NextW(ctx);
            case "b:music": return new MusicW(ctx);
            case "b:weather": return new WeatherW(ctx);
            default:
                if (!w.startsWith("a:")) return null;
                try {
                    int id = Integer.parseInt(w.substring(2));
                    AppWidgetProviderInfo info = manager.getAppWidgetInfo(id);
                    if (info == null) return null;
                    AppWidgetHostView hv = host.createView(ctx, id, info);
                    hv.setPadding(0, 0, 0, 0);
                    return hv;
                } catch (Exception e) {
                    return null;
                }
        }
    }

    /** Nothing's widgets are squares: a frame whose height follows its width. */
    static final class Square extends FrameLayout {
        Square(Context c) { super(c); }
        @Override protected void onMeasure(int w, int h) {
            super.onMeasure(w, MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(w), MeasureSpec.EXACTLY));
        }
    }

    private View frame(String w, View content, boolean edit, Remover remover, boolean square) {
        FrameLayout f = square ? new Square(ctx) : new FrameLayout(ctx);
        f.setClipChildren(false);
        FrameLayout clip = new FrameLayout(ctx);
        boolean builtIn = w.startsWith("b:");
        if (builtIn) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(w.equals("b:torch") && TorchW.isOn() ? fg(ctx) : card(ctx));
            bg.setStroke(Ui.dpi(ctx, 1), Theme.get(ctx).surfaceLine);
            bg.setCornerRadius(Ui.dp(ctx, 24));
            clip.setBackground(bg);
        }
        clip.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), Ui.dp(ctx, 24));
            }
        });
        clip.setClipToOutline(true);
        clip.addView(content, new FrameLayout.LayoutParams(-1, -1));
        f.addView(clip, new FrameLayout.LayoutParams(-1, -1));
        if (content instanceof AppWidgetHostView) {
            content.post(() -> {
                float d = ctx.getResources().getDisplayMetrics().density;
                int wdp = (int) (content.getWidth() / d), hdp = (int) (content.getHeight() / d);
                try {
                    //noinspection deprecation
                    ((AppWidgetHostView) content).updateAppWidgetSize(new Bundle(), wdp, hdp, wdp, hdp);
                } catch (Exception ignored) { }
            });
        }
        if (edit) {
            TextView x = new TextView(ctx);
            x.setText("−");
            x.setTextColor(Ui.WHITE);
            x.setTextSize(18);
            x.setGravity(Gravity.CENTER);
            GradientDrawable xb = new GradientDrawable();
            xb.setShape(GradientDrawable.OVAL);
            xb.setColor(Ui.RED);
            x.setBackground(xb);
            FrameLayout.LayoutParams xl = new FrameLayout.LayoutParams(Ui.dpi(ctx, 26), Ui.dpi(ctx, 26), Gravity.TOP | Gravity.START);
            xl.leftMargin = -Ui.dpi(ctx, 6);
            xl.topMargin = -Ui.dpi(ctx, 6);
            f.addView(x, xl);
            x.setOnClickListener(v -> {
                Ui.tap(v);
                remover.remove(w);
            });
            // Don't let taps reach the widget itself while editing
            View shield = new View(ctx);
            shield.setClickable(true);
            f.addView(shield, 1, new FrameLayout.LayoutParams(-1, -1));
        }
        return f;
    }

    void start() { try { host.startListening(); } catch (Exception ignored) { } }
    void stop() { try { host.stopListening(); } catch (Exception ignored) { } }

    // ================================================================== widgets in the home grid

    /** Built-in widgets that can refresh their data without being rebuilt. */
    interface Live { void refresh(); }

    /** A widget as a home grid cell: rounded, clipped, and draggable in edit mode. */
    static final class WidgetCell extends FrameLayout implements Cell {
        final String key;                 // "b:battery" or "a:<id>"
        final View content;
        private boolean ghost, target;
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();

        WidgetCell(Context c, String key, View content) {
            super(c);
            this.key = key;
            this.content = content;
            setWillNotDraw(false);
            setPadding(Ui.dpi(c, 5), Ui.dpi(c, 5), Ui.dpi(c, 5), Ui.dpi(c, 5));
            FrameLayout clip = new FrameLayout(c);
            if (key.startsWith("b:")) {
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(key.equals("b:torch") && TorchW.isOn() ? fg(c) : card(c));
                bg.setStroke(Ui.dpi(c, 1), Theme.get(c).surfaceLine);
                bg.setCornerRadius(Ui.dp(c, 26));
                clip.setBackground(bg);
            }
            clip.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View v, Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), Ui.dp(c, 26));
                }
            });
            clip.setClipToOutline(true);
            clip.addView(content, new FrameLayout.LayoutParams(-1, -1));
            addView(clip, new FrameLayout.LayoutParams(-1, -1));
            line.setStyle(Paint.Style.STROKE);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            super.onLayout(changed, l, t, r, b);
            if (changed && content instanceof AppWidgetHostView) {
                float d = getResources().getDisplayMetrics().density;
                int wdp = (int) ((r - l) / d), hdp = (int) ((b - t) / d);
                try {
                    //noinspection deprecation
                    ((AppWidgetHostView) content).updateAppWidgetSize(new Bundle(), wdp, hdp, wdp, hdp);
                } catch (Exception ignored) { }
            }
        }

        @Override
        protected void dispatchDraw(Canvas c) {
            float r = Ui.dp(getContext(), 26);
            box.set(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());
            if (ghost) {
                line.setPathEffect(new android.graphics.DashPathEffect(new float[]{Ui.dp(getContext(), 4), Ui.dp(getContext(), 4)}, 0));
                line.setColor(target ? Ui.RED : Theme.get(getContext()).textFaint);
                line.setStrokeWidth(Ui.dp(getContext(), 1.2f));
                c.drawRoundRect(box, r, r, line);
                line.setPathEffect(null);
                return;
            }
            super.dispatchDraw(c);
            if (target) {
                line.setColor(Ui.RED);
                line.setStrokeWidth(Ui.dp(getContext(), 2));
                c.drawRoundRect(box, r, r, line);
            }
        }

        @Override public boolean hasContent() { return true; }
        @Override public void setGhost(boolean on) { ghost = on; invalidate(); }
        @Override public void setDropTarget(boolean on, boolean makesStack) { target = on; invalidate(); }
        @Override public RectF discBounds() { return new RectF(box); }
    }

    /** The widget as a home cell, or null if it can't be shown (e.g. its app was removed). */
    View cell(String w) {
        View v = make(w);
        return v == null ? null : new WidgetCell(ctx, w, v);
    }

    static String name(Context c, String w) {
        for (String[] b : BUILT_IN) if (w.equals("b:" + b[0])) return b[1];
        if (w.startsWith("a:")) {
            try {
                AppWidgetProviderInfo info = AppWidgetManager.getInstance(c).getAppWidgetInfo(Integer.parseInt(w.substring(2)));
                if (info != null) return info.loadLabel(c.getPackageManager());
            } catch (Exception ignored) { }
        }
        return "Widget";
    }

    // ---- sizes, in grid cells (columns × rows)

    private static final int CELL_DP = 80;

    static List<int[]> sizes(Context c, String w) {
        List<int[]> out = new ArrayList<>();
        switch (w) {
            case "b:battery": case "b:clock": case "b:torch": case "b:tiles":
                out.add(new int[]{2, 2});
                break;
            case "b:next": case "b:music":
                out.add(new int[]{4, 1});
                out.add(new int[]{4, 2});
                break;
            case "b:weather":
                out.add(new int[]{2, 2});
                out.add(new int[]{4, 2});
                break;
            default:
                AppWidgetProviderInfo info = null;
                try { info = AppWidgetManager.getInstance(c).getAppWidgetInfo(Integer.parseInt(w.substring(2))); } catch (Exception ignored) { }
                int[][] all = {{1, 1}, {2, 1}, {2, 2}, {4, 1}, {4, 2}, {4, 3}, {4, 4}};
                if (info == null) { out.add(new int[]{4, 2}); break; }
                float d = c.getResources().getDisplayMetrics().density;
                int minW = (int) ((info.resizeMode & AppWidgetProviderInfo.RESIZE_HORIZONTAL) != 0 && info.minResizeWidth > 0 ? info.minResizeWidth : info.minWidth);
                int minH = (int) ((info.resizeMode & AppWidgetProviderInfo.RESIZE_VERTICAL) != 0 && info.minResizeHeight > 0 ? info.minResizeHeight : info.minHeight);
                int[] def = defaultSize(c, w);
                for (int[] s : all) {
                    boolean fitsW = s[0] * CELL_DP * d >= minW * 0.9f, fitsH = s[1] * CELL_DP * d >= minH * 0.9f;
                    boolean canW = s[0] == def[0] || (info.resizeMode & AppWidgetProviderInfo.RESIZE_HORIZONTAL) != 0;
                    boolean canH = s[1] == def[1] || (info.resizeMode & AppWidgetProviderInfo.RESIZE_VERTICAL) != 0;
                    if (fitsW && fitsH && canW && canH) out.add(s);
                }
                if (out.isEmpty()) out.add(def);
        }
        return out;
    }

    static int[] defaultSize(Context c, String w) {
        if (w.startsWith("b:")) return sizes(c, w).get(0);
        try {
            AppWidgetProviderInfo info = AppWidgetManager.getInstance(c).getAppWidgetInfo(Integer.parseInt(w.substring(2)));
            if (info != null) {
                float d = c.getResources().getDisplayMetrics().density;
                int cw = (int) Math.ceil(info.minWidth / (CELL_DP * d)), ch = (int) Math.ceil(info.minHeight / (CELL_DP * d));
                cw = cw <= 1 ? 1 : cw <= 2 ? 2 : 4;
                return new int[]{cw, Math.max(1, Math.min(4, ch))};
            }
        } catch (Exception ignored) { }
        return new int[]{4, 2};
    }

    static int[] size(Context c, String w) {
        try {
            org.json.JSONObject o = new org.json.JSONObject(Prefs.get(c).raw().getString("wsize", "{}"));
            String v = o.optString(w, null);
            if (v != null) {
                String[] p = v.split("x");
                return new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1])};
            }
        } catch (Exception ignored) { }
        return defaultSize(c, w);
    }

    static void setSize(Context c, String w, int[] s) {
        try {
            org.json.JSONObject o = new org.json.JSONObject(Prefs.get(c).raw().getString("wsize", "{}"));
            o.put(w, s[0] + "x" + s[1]);
            Prefs.get(c).raw().edit().putString("wsize", o.toString()).apply();
        } catch (Exception ignored) { }
    }

    /** Free an app widget's id when it's removed from home. */
    void release(String w) {
        if (w.startsWith("a:")) {
            try { host.deleteAppWidgetId(Integer.parseInt(w.substring(2))); } catch (Exception ignored) { }
        }
    }

    // ================================================================== built-in widgets

    private static Paint paint() { return new Paint(Paint.ANTI_ALIAS_FLAG); }

    /** A ring of 24 dots for the phone battery. */
    static final class BatteryW extends View implements Live {
        private final Paint p = paint(), t = paint(), l = paint();
        BatteryW(Context c) {
            super(c);
            t.setTypeface(Ui.dots(c));
            t.setTextAlign(Paint.Align.CENTER);
            t.setColor(fg(c));
            l.setTypeface(Ui.mono(c));
            l.setTextAlign(Paint.Align.CENTER);
            l.setColor(sub(c));
            l.setTextSize(Ui.sp(c, 8.5f));
            l.setLetterSpacing(0.16f);
            setOnClickListener(v -> start(c, new Intent(Intent.ACTION_POWER_USAGE_SUMMARY)));
        }
        @Override public void refresh() { invalidate(); }
        @Override protected void onDraw(Canvas c) {
            Intent b = getContext().registerReceiver(null, new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            int level = 0;
            boolean charging = false;
            if (b != null) {
                level = Math.round(b.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100f / b.getIntExtra(BatteryManager.EXTRA_SCALE, 100));
                int st = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL;
            }
            float cx = getWidth() / 2f, cy = getHeight() / 2f - Ui.dp(getContext(), 6);
            float r = Math.min(getWidth(), getHeight()) * 0.30f;
            int n = 24, lit = Math.round(level / 100f * n);
            for (int i = 0; i < n; i++) {
                double a = -Math.PI / 2 + i * Math.PI * 2 / n;
                p.setColor(i < lit ? (i == lit - 1 && (charging || level <= 15) ? Ui.RED : fg(getContext())) : off(getContext()));
                c.drawCircle(cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r, Ui.dp(getContext(), 2.2f), p);
            }
            t.setTextSize(r * 0.75f);
            c.drawText(String.valueOf(level), cx, cy + r * 0.27f, t);
            c.drawText(charging ? "CHARGING" : "BATTERY", cx, getHeight() - Ui.dp(getContext(), 12), l);
        }
    }

    /** Sixty dots around the edge, hands made of dots, seconds in red. */
    static final class ClockW extends View {
        private final Paint p = paint();
        ClockW(Context c) {
            super(c);
            setOnClickListener(v -> start(c, new Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)));
        }
        @Override protected void onDraw(Canvas c) {
            Calendar now = Calendar.getInstance();
            float cx = getWidth() / 2f, cy = getHeight() / 2f, R = Math.min(getWidth(), getHeight()) * 0.40f;
            int sec = now.get(Calendar.SECOND);
            for (int i = 0; i < 60; i++) {
                double a = -Math.PI / 2 + i * Math.PI * 2 / 60;
                boolean big = i % 5 == 0;
                p.setColor(i == sec ? Ui.RED : big ? fg(getContext()) : off(getContext()));
                c.drawCircle(cx + (float) Math.cos(a) * R, cy + (float) Math.sin(a) * R, Ui.dp(getContext(), big ? 2 : 1), p);
            }
            float min = now.get(Calendar.MINUTE) + sec / 60f;
            float hour = now.get(Calendar.HOUR) + min / 60f;
            hand(c, cx, cy, hour / 12f, R * 0.5f, 5, fg(getContext()), 2.2f);
            hand(c, cx, cy, min / 60f, R * 0.78f, 7, sub(getContext()), 1.8f);
            p.setColor(Ui.RED);
            c.drawCircle(cx, cy, Ui.dp(getContext(), 2.4f), p);
            postInvalidateDelayed(1000 - (System.currentTimeMillis() % 1000));
        }
        private void hand(Canvas c, float cx, float cy, float turn, float len, int dots, int color, float rDp) {
            double a = -Math.PI / 2 + turn * Math.PI * 2;
            p.setColor(color);
            for (int k = 1; k <= dots; k++) {
                float d = len * k / dots;
                c.drawCircle(cx + (float) Math.cos(a) * d, cy + (float) Math.sin(a) * d, Ui.dp(getContext(), rDp), p);
            }
        }
    }

    /** The torch: white when on. */
    static final class TorchW extends View {
        static boolean on;
        private final Paint p = paint();
        static boolean isOn() { return on; }
        TorchW(Context c) {
            super(c);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            setOnClickListener(v -> {
                try {
                    CameraManager cm = c.getSystemService(CameraManager.class);
                    for (String id : cm.getCameraIdList()) {
                        Boolean flash = cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                        if (flash != null && flash) {
                            on = !on;
                            cm.setTorchMode(id, on);
                            Ui.tap(v);
                            View parent = (View) getParent();
                            GradientDrawable bg = (GradientDrawable) parent.getBackground();
                            if (bg != null) bg.setColor(on ? fg(c) : card(c));
                            invalidate();
                            break;
                        }
                    }
                } catch (Exception ignored) { }
            });
        }
        @Override protected void onDraw(Canvas c) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f, u = Ui.dp(getContext(), 1.3f);
            p.setColor(on ? card(getContext()) : fg(getContext()));
            p.setStrokeWidth(Ui.dp(getContext(), 2));
            android.graphics.Path path = new android.graphics.Path();
            path.moveTo(cx - 8 * u, cy - 16 * u); path.lineTo(cx + 8 * u, cy - 16 * u); path.lineTo(cx + 8 * u, cy - 8 * u);
            path.lineTo(cx + 4 * u, cy - 2 * u); path.lineTo(cx + 4 * u, cy + 18 * u); path.lineTo(cx - 4 * u, cy + 18 * u);
            path.lineTo(cx - 4 * u, cy - 2 * u); path.lineTo(cx - 8 * u, cy - 8 * u); path.close();
            c.drawPath(path, p);
            c.drawLine(cx, cy + 4 * u, cx, cy + 8 * u, p);
        }
    }

    /** Four round quick tiles: torch, Wi‑Fi, Bluetooth and ring/vibrate. */
    static final class TilesW extends LinearLayout {
        TilesW(Context c) {
            super(c);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER);
            setPadding(Ui.dpi(c, 10), Ui.dpi(c, 10), Ui.dpi(c, 10), Ui.dpi(c, 10));
            LinearLayout r1 = new LinearLayout(c), r2 = new LinearLayout(c);
            r1.setGravity(Gravity.CENTER);
            r2.setGravity(Gravity.CENTER);
            android.media.AudioManager am = c.getSystemService(android.media.AudioManager.class);
            r1.addView(tile(c, Glyph.TORCH, TorchW.isOn(), (v, setOn) -> {
                if (toggleTorch(c)) setOn.accept(TorchW.isOn());
            }));
            r1.addView(tile(c, Glyph.WIFI, false, (v, setOn) ->
                    start(c, new Intent(android.os.Build.VERSION.SDK_INT >= 29 ? android.provider.Settings.Panel.ACTION_WIFI
                            : android.provider.Settings.ACTION_WIFI_SETTINGS))));
            r2.addView(tile(c, Glyph.BLUETOOTH, false, (v, setOn) ->
                    start(c, new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))));
            boolean ring = am != null && am.getRingerMode() == android.media.AudioManager.RINGER_MODE_NORMAL;
            r2.addView(tile(c, ring ? Glyph.BELL : Glyph.VIBRATE, ring, (v, setOn) -> {
                if (am == null) return;
                try {
                    boolean nowRing = am.getRingerMode() != android.media.AudioManager.RINGER_MODE_NORMAL;
                    am.setRingerMode(nowRing ? android.media.AudioManager.RINGER_MODE_NORMAL : android.media.AudioManager.RINGER_MODE_VIBRATE);
                    ((android.widget.ImageView) v).setImageDrawable(new Glyph(nowRing ? Glyph.BELL : Glyph.VIBRATE, Ui.dpi(c, 22), nowRing ? card(c) : fg(c)));
                    setOn.accept(nowRing);
                } catch (Exception e) {
                    start(c, new Intent(android.provider.Settings.ACTION_SOUND_SETTINGS));
                }
            }));
            addView(r1, new LayoutParams(-1, 0, 1f));
            addView(r2, new LayoutParams(-1, 0, 1f));
        }

        interface Tap { void run(View v, java.util.function.Consumer<Boolean> setOn); }

        private View tile(Context c, int glyph, boolean on, Tap tap) {
            android.widget.ImageView iv = new android.widget.ImageView(c);
            iv.setScaleType(android.widget.ImageView.ScaleType.CENTER);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            java.util.function.Consumer<Boolean> setOn = o -> {
                bg.setColor(o ? fg(c) : off(c));
                Drawable d = iv.getDrawable();
                if (d instanceof Glyph) iv.setImageDrawable(new Glyph(((Glyph) d).kind(), Ui.dpi(c, 22), o ? card(c) : fg(c)));
            };
            iv.setImageDrawable(new Glyph(glyph, Ui.dpi(c, 22), on ? card(c) : fg(c)));
            bg.setColor(on ? fg(c) : off(c));
            iv.setBackground(bg);
            iv.setOnClickListener(v -> {
                Ui.tap(v);
                v.animate().scaleX(0.88f).scaleY(0.88f).setDuration(70).withEndAction(() ->
                        v.animate().scaleX(1f).scaleY(1f).setInterpolator(new android.view.animation.OvershootInterpolator(3f)).setDuration(260).start()).start();
                tap.run(v, setOn);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
            int m = Ui.dpi(c, 5);
            lp.setMargins(m, m, m, m);
            iv.setLayoutParams(lp);
            // Keep the tiles round whatever the space
            iv.setOutlineProvider(null);
            return new SquareTile(c, iv);
        }
    }

    /** Centres a round tile at the largest square that fits. */
    static final class SquareTile extends FrameLayout {
        private final View inner;
        SquareTile(Context c, View inner) {
            super(c);
            this.inner = inner;
            addView(inner);
            setLayoutParams(new LinearLayout.LayoutParams(0, -1, 1f));
        }
        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int w = r - l, h = b - t, s = (int) (Math.min(w, h) - Ui.dp(getContext(), 8));
            int x = (w - s) / 2, y = (h - s) / 2;
            inner.layout(x, y, x + s, y + s);
        }
        @Override protected void onMeasure(int w, int h) {
            super.onMeasure(w, h);
            int s = Math.min(getMeasuredWidth(), getMeasuredHeight());
            inner.measure(MeasureSpec.makeMeasureSpec(s, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(s, MeasureSpec.EXACTLY));
        }
    }

    /** Toggle the torch; returns false if this phone has none. */
    static boolean toggleTorch(Context c) {
        try {
            CameraManager cm = c.getSystemService(CameraManager.class);
            for (String id : cm.getCameraIdList()) {
                Boolean flash = cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (flash != null && flash) {
                    TorchW.on = !TorchW.on;
                    cm.setTorchMode(id, TorchW.on);
                    return true;
                }
            }
        } catch (Exception ignored) { }
        return false;
    }

    /** The next event, with a one-tap open. */
    static final class NextW extends LinearLayout implements Live {
        private long eventId = -1;
        private TextView titleV, subV;
        @Override public void refresh() { if (titleV != null) load(getContext()); }

        private void load(Context c) {
            new Thread(() -> {
                String[] r = next(c);
                post(() -> {
                    if (r != null) {
                        titleV.setText(r[0]);
                        subV.setText(r[1]);
                        eventId = Long.parseLong(r[2]);
                    } else {
                        titleV.setText("Nothing else today");
                        subV.setText("Enjoy the quiet");
                        eventId = -1;
                    }
                });
            }).start();
        }
        NextW(Context c) {
            super(c);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(Ui.dpi(c, 14), 0, Ui.dpi(c, 16), 0);
            TextView day = new TextView(c);
            Calendar now = Calendar.getInstance();
            day.setText(String.format(java.util.Locale.getDefault(), "%tb%n%d", now, now.get(Calendar.DAY_OF_MONTH)).toUpperCase());
            day.setGravity(Gravity.CENTER);
            day.setTypeface(Ui.dots(c));
            day.setTextSize(17);
            day.setTextColor(fg(c));
            day.setLineSpacing(0, 0.9f);
            GradientDrawable db = new GradientDrawable();
            db.setColor(off(c));
            db.setCornerRadius(Ui.dp(c, 14));
            day.setBackground(db);
            addView(day, new LayoutParams(Ui.dpi(c, 54), Ui.dpi(c, 54)));
            LinearLayout text = new LinearLayout(c);
            text.setOrientation(VERTICAL);
            text.setPadding(Ui.dpi(c, 14), 0, 0, 0);
            TextView k = mono(c, "NEXT");
            TextView title = new TextView(c);
            title.setTextColor(fg(c));
            title.setTextSize(15);
            title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            title.setSingleLine(true);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            TextView sub = new TextView(c);
            sub.setTextColor(sub(c));
            sub.setTextSize(11.5f);
            sub.setSingleLine(true);
            text.addView(k);
            text.addView(title);
            text.addView(sub);
            addView(text, new LayoutParams(0, -2, 1f));
            if (c.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
                title.setText("Calendar");
                sub.setText("Allow calendar access in Dot Home settings");
                setOnClickListener(v -> start(c, new Intent(c, SettingsActivity.class)));
                return;
            }
            title.setText("Nothing else today");
            sub.setText("Enjoy the quiet");
            titleV = title;
            subV = sub;
            load(c);
            setOnClickListener(v -> {
                if (eventId >= 0) start(c, new Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)));
                else {
                    Uri.Builder b = CalendarContract.CONTENT_URI.buildUpon().appendPath("time");
                    ContentUris.appendId(b, System.currentTimeMillis());
                    start(c, new Intent(Intent.ACTION_VIEW).setData(b.build()));
                }
            });
        }

        static String[] next(Context c) {
            long now = System.currentTimeMillis();
            Uri.Builder b = CalendarContract.Instances.CONTENT_URI.buildUpon();
            ContentUris.appendId(b, now - 3600_000L);
            ContentUris.appendId(b, now + 36 * 3600_000L);
            try (Cursor cur = c.getContentResolver().query(b.build(),
                    new String[]{CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
                            CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.EVENT_LOCATION},
                    CalendarContract.Instances.ALL_DAY + "=0 AND " + CalendarContract.Instances.END + ">?",
                    new String[]{String.valueOf(now)}, CalendarContract.Instances.BEGIN + " ASC")) {
                if (cur == null || !cur.moveToFirst()) return null;
                long begin = cur.getLong(1), end = cur.getLong(2);
                java.text.DateFormat tf = android.text.format.DateFormat.getTimeFormat(c);
                String when;
                if (begin <= now) when = "Now · until " + tf.format(new java.util.Date(end));
                else {
                    long mins = (begin - now) / 60000;
                    String in = mins < 60 ? "in " + mins + " min" : "in " + (mins / 60) + " h" + (mins % 60 >= 10 ? " " + (mins % 60) + " m" : "");
                    when = tf.format(new java.util.Date(begin)) + " · " + in;
                }
                String loc = cur.getString(4);
                if (loc != null && !loc.isEmpty()) when += " · " + loc;
                String title = cur.getString(0);
                return new String[]{title == null || title.isEmpty() ? "Event" : title, when, String.valueOf(cur.getLong(3))};
            } catch (Exception e) {
                return null;
            }
        }
    }

    /** What's playing, from any app, with a dot progress bar. Uses the notification-dots permission. */
    static final class MusicW extends LinearLayout {
        private MediaController ctrl;
        private final TextView title, artist;
        private final android.widget.ImageView play;
        private final Glyph playG, pauseG, moreG;
        private final DotBar bar;
        private final Runnable tick = new Runnable() {
            @Override public void run() {
                update();
                if (isAttachedToWindow()) postDelayed(this, 1000);
            }
        };

        MusicW(Context c) {
            super(c);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(Ui.dpi(c, 14), 0, Ui.dpi(c, 12), 0);
            View art = new ArtDots(c);
            addView(art, new LayoutParams(Ui.dpi(c, 56), Ui.dpi(c, 56)));
            LinearLayout text = new LinearLayout(c);
            text.setOrientation(VERTICAL);
            text.setPadding(Ui.dpi(c, 12), 0, Ui.dpi(c, 8), 0);
            title = new TextView(c);
            title.setTextColor(fg(c));
            title.setTextSize(14);
            title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            title.setSingleLine(true);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            artist = new TextView(c);
            artist.setTextColor(sub(c));
            artist.setTextSize(11.5f);
            artist.setSingleLine(true);
            artist.setEllipsize(android.text.TextUtils.TruncateAt.END);
            bar = new DotBar(c);
            text.addView(title);
            text.addView(artist);
            LayoutParams blp = new LayoutParams(-1, Ui.dpi(c, 10));
            blp.topMargin = Ui.dpi(c, 6);
            text.addView(bar, blp);
            addView(text, new LayoutParams(0, -2, 1f));
            playG = new Glyph(Glyph.PLAY, Ui.dpi(c, 18), card(c));
            pauseG = new Glyph(Glyph.PAUSE, Ui.dpi(c, 18), card(c));
            moreG = new Glyph(Glyph.CHEVRON, Ui.dpi(c, 18), card(c));
            android.widget.ImageView prev = button(c, new Glyph(Glyph.PREV, Ui.dpi(c, 14), sub(c)), false);
            play = button(c, playG, true);
            android.widget.ImageView nxt = button(c, new Glyph(Glyph.NEXT, Ui.dpi(c, 14), sub(c)), false);
            addView(prev);
            addView(play);
            addView(nxt);
            prev.setOnClickListener(v -> { if (ctrl != null) { Ui.tap(v); ctrl.getTransportControls().skipToPrevious(); } });
            nxt.setOnClickListener(v -> { if (ctrl != null) { Ui.tap(v); ctrl.getTransportControls().skipToNext(); } });
            play.setOnClickListener(v -> {
                if (ctrl == null) { start(c, new Intent(c, SettingsActivity.class)); return; }
                Ui.tap(v);
                PlaybackState st = ctrl.getPlaybackState();
                if (st != null && st.getState() == PlaybackState.STATE_PLAYING) ctrl.getTransportControls().pause();
                else ctrl.getTransportControls().play();
            });
            setOnClickListener(v -> {
                if (ctrl != null && ctrl.getSessionActivity() != null) {
                    try { ctrl.getSessionActivity().send(); } catch (Exception ignored) { }
                }
            });
        }

        private android.widget.ImageView button(Context c, Glyph g, boolean big) {
            android.widget.ImageView t = new android.widget.ImageView(c);
            t.setImageDrawable(g);
            t.setScaleType(android.widget.ImageView.ScaleType.CENTER);
            if (big) {
                GradientDrawable d = new GradientDrawable();
                d.setShape(GradientDrawable.OVAL);
                d.setColor(fg(c));
                t.setBackground(d);
            }
            int s2 = Ui.dpi(c, big ? 40 : 32);
            t.setLayoutParams(new LayoutParams(s2, s2));
            return t;
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            post(tick);
        }

        @Override protected void onDetachedFromWindow() {
            removeCallbacks(tick);
            super.onDetachedFromWindow();
        }

        private void update() {
            Context c = getContext();
            ctrl = null;
            try {
                MediaSessionManager msm = c.getSystemService(MediaSessionManager.class);
                List<MediaController> list = msm.getActiveSessions(new ComponentName(c, DotsListener.class));
                for (MediaController m : list) {
                    PlaybackState st = m.getPlaybackState();
                    if (st != null && st.getState() == PlaybackState.STATE_PLAYING) { ctrl = m; break; }
                }
                if (ctrl == null && !list.isEmpty()) ctrl = list.get(0);
            } catch (SecurityException e) {
                title.setText("Now playing");
                artist.setText("Turn on notification dots in settings to see it");
                play.setImageDrawable(moreG);
                bar.set(0);
                return;
            }
            if (ctrl == null) {
                title.setText("Nothing playing");
                artist.setText("Start some music and it shows here");
                play.setImageDrawable(playG);
                bar.set(0);
                return;
            }
            MediaMetadata md = ctrl.getMetadata();
            PlaybackState st = ctrl.getPlaybackState();
            CharSequence t = md == null ? null : md.getText(MediaMetadata.METADATA_KEY_TITLE);
            CharSequence a = md == null ? null : md.getText(MediaMetadata.METADATA_KEY_ARTIST);
            title.setText(t == null ? "Playing" : t);
            artist.setText(a == null ? "" : a);
            boolean playing = st != null && st.getState() == PlaybackState.STATE_PLAYING;
            play.setImageDrawable(playing ? pauseG : playG);
            long dur = md == null ? 0 : md.getLong(MediaMetadata.METADATA_KEY_DURATION);
            if (st != null && dur > 0) {
                long pos = st.getPosition();
                if (playing) pos += (long) ((SystemClock.elapsedRealtime() - st.getLastPositionUpdateTime()) * st.getPlaybackSpeed());
                bar.set(Ui.clamp(pos / (float) dur, 0, 1));
            } else {
                bar.set(0);
            }
        }
    }

    /** A row of dots used as a progress bar; the playhead is red. */
    static final class DotBar extends View {
        private float v;
        private final Paint p = paint();
        DotBar(Context c) { super(c); }
        void set(float v) { this.v = v; invalidate(); }
        @Override protected void onDraw(Canvas c) {
            float step = Ui.dp(getContext(), 5.5f);
            int n = (int) (getWidth() / step);
            int at = Math.round(v * (n - 1));
            for (int i = 0; i < n; i++) {
                p.setColor(v > 0 && i == at ? Ui.RED : i < at ? fg(getContext()) : off(getContext()));
                c.drawCircle(i * step + step / 2, getHeight() / 2f, Ui.dp(getContext(), 1.5f), p);
            }
        }
    }

    /** A small dot pattern in place of album art. */
    static final class ArtDots extends View {
        private final Paint p = paint();
        ArtDots(Context c) {
            super(c);
            GradientDrawable d = new GradientDrawable();
            d.setColor(off(c));
            d.setCornerRadius(Ui.dp(c, 14));
            setBackground(d);
        }
        @Override protected void onDraw(Canvas c) {
            int n = 5;
            float pitch = getWidth() / (n + 1f);
            for (int r = 0; r < n; r++) for (int col = 0; col < n; col++) {
                double v = Math.abs(Math.sin(r * 1.7 + col * 2.3));
                p.setColor(r == 2 && col == 3 ? Ui.RED : 0xFF666666);
                c.drawCircle(pitch * (col + 1), pitch * (r + 1), (float) (Ui.dp(getContext(), 1) + v * Ui.dp(getContext(), 1.6f)), p);
            }
        }
    }

    /** Temperature in dots, the condition, and the next hours as bars (now in red). */
    static final class WeatherW extends View implements Live {
        private final Paint p = paint(), t = paint(), s = paint(), k = paint();
        WeatherW(Context c) {
            super(c);
            t.setTypeface(Ui.dots(c));
            t.setColor(fg(c));
            s.setColor(sub(c));
            s.setTextSize(Ui.sp(c, 11.5f));
            k.setTypeface(Ui.mono(c));
            k.setColor(sub(c));
            k.setTextSize(Ui.sp(c, 9));
            k.setLetterSpacing(0.16f);
            setOnClickListener(v -> {
                if (!Weather.enabled(c)) start(c, new Intent(c, SettingsActivity.class));
                else Weather.refresh(c, true, this::invalidate);
            });
        }
        @Override public void refresh() { invalidate(); }
        @Override protected void onDraw(Canvas c) {
            Context ctx = getContext();
            float pad = Ui.dp(ctx, 16);
            Weather.Data d = Weather.enabled(ctx) ? Weather.cached(ctx) : null;
            if (d == null) {
                c.drawText("WEATHER", pad, pad + Ui.sp(ctx, 9), k);
                s.setColor(fg(ctx));
                s.setTextSize(Ui.sp(ctx, 14));
                c.drawText(Weather.enabled(ctx) ? "Getting the forecast…" : "Turn on weather in settings", pad, getHeight() / 2f + Ui.sp(ctx, 5), s);
                s.setColor(sub(ctx));
                s.setTextSize(Ui.sp(ctx, 11.5f));
                return;
            }
            String place = d.place == null ? "HERE" : d.place.toUpperCase();
            c.drawText(place, pad, pad + Ui.sp(ctx, 9), k);
            t.setTextSize(Ui.sp(ctx, 46));
            c.drawText(Weather.temp(ctx, d.temp), pad - Ui.dp(ctx, 2), pad + Ui.sp(ctx, 9) + Ui.dp(ctx, 50), t);
            c.drawText(Weather.describe(d.code) + " · H " + Weather.temp(ctx, d.hi) + " L " + Weather.temp(ctx, d.lo),
                    pad, getHeight() - pad, s);
            if (d.hours.length > 1) {
                double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
                for (double h : d.hours) { lo = Math.min(lo, h); hi = Math.max(hi, h); }
                float bw = Ui.dp(ctx, 5), gap = Ui.dp(ctx, 5);
                float right = getWidth() - pad, bottom = getHeight() / 2f + Ui.dp(ctx, 14);
                float maxH = Ui.dp(ctx, 46), minH = Ui.dp(ctx, 8);
                for (int i = 0; i < d.hours.length; i++) {
                    float x = right - (d.hours.length - i) * (bw + gap) + gap;
                    float h = (float) (minH + (hi == lo ? 0.5 : (d.hours[i] - lo) / (hi - lo)) * (maxH - minH));
                    p.setColor(i == 0 ? Ui.RED : off(ctx));
                    c.drawRoundRect(new RectF(x, bottom - h, x + bw, bottom), bw / 2, bw / 2, p);
                }
            }
        }
    }

    private static TextView mono(Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTypeface(Ui.mono(c));
        t.setTextSize(9);
        t.setLetterSpacing(0.16f);
        t.setTextColor(sub(c));
        return t;
    }

    // Theme colours for the built-in widgets
    static int fg(Context c) { return Theme.get(c).text; }
    static int sub(Context c) { return Theme.get(c).textMute; }
    static int card(Context c) { return Theme.get(c).surface; }
    static int off(Context c) { return Theme.get(c).light ? 0xFFE3E3DE : 0xFF262626; }

    static void start(Context c, Intent i) {
        try {
            c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) { }
    }
}
