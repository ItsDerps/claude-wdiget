package uk.dnm.dothome;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.provider.Settings;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HomeActivity extends Activity
        implements HomeView.Host, DrawerView.Host, AppMenu.Actions, StackPanel.Host, Apps.Listener,
        BigStackView.Listener {

    private static final int REQ_BIND = 11, REQ_CONFIG = 12;

    private Root root;
    private DotGridView grid;
    private AppIconView lastLaunched;
    private boolean returning;
    private HomeView home;
    private DrawerView drawer;
    private TodayView today;
    private View editFrame;
    private LinearLayout editTop, editTray;
    private TextView editTitle, editDone;
    private Apps apps;
    private Prefs prefs;
    private Widgets widgets;

    private AppMenu menu;
    private StackPanel stackPanel;
    private Sheet sheet;

    private float progress;            // drawer: 0 = home, 1 = open
    private float todayP;              // Today page: 0 = hidden, 1 = shown
    private float editP;               // edit mode: 0 = off, 1 = on
    private boolean edit;
    private Spring drawerSpring, todaySpring, editSpring;
    private ValueAnimator calmAnim;
    private boolean assembleOnResume = true;
    private String builtFingerprint;
    private int appsVersion;
    private long eventId = -1;
    private int insetTop, insetBottom;
    private int pendingWidgetId = -1;
    private final Set<String> badges = new HashSet<>();

    // What's on home right now, in display order
    private List<String> homeKeys = new ArrayList<>(), dockKeys = new ArrayList<>();

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        prefs = Prefs.get(this);
        apps = Apps.get(this);
        widgets = new Widgets(this);

        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }

        root = new Root(this);
        home = new HomeView(this, this);
        drawer = new DrawerView(this, this);
        today = new TodayView(this);
        editFrame = new View(this);
        GradientDrawable fb = new GradientDrawable();
        fb.setColor(0);
        fb.setStroke(Ui.dpi(this, 1), 0xFF3A3A3A, Ui.dp(this, 5), Ui.dp(this, 5));
        fb.setCornerRadius(Ui.dp(this, 40));
        editFrame.setBackground(fb);
        editFrame.setVisibility(View.INVISIBLE);
        grid = new DotGridView(this);
        root.addView(grid, new FrameLayout.LayoutParams(-1, -1));
        root.addView(editFrame, new FrameLayout.LayoutParams(-1, -1));
        home.setBigListener(this);
        home.setWidgets(widgets);
        root.addView(home, new FrameLayout.LayoutParams(-1, -1));
        root.addView(today, new FrameLayout.LayoutParams(-1, -1));
        root.addView(drawer, new FrameLayout.LayoutParams(-1, -1));
        buildEditChrome();
        drawer.setVisibility(View.INVISIBLE);
        today.setVisibility(View.INVISIBLE);
        drawer.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        today.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        root.setFocusableInTouchMode(true);
        setContentView(root);
        root.requestFocus();

        drawerSpring = new Spring(400, 0.85f, 0f, new Spring.Listener() {
            @Override public void onUpdate(float v) { applyProgress(v); }
            @Override public void onSettled(float v) { settled(v); }
        });
        todaySpring = new Spring(400, 0.88f, 0f, new Spring.Listener() {
            @Override public void onUpdate(float v) { applyToday(v); }
            @Override public void onSettled(float v) { todaySettled(v); }
        });
        editSpring = new Spring(500, 0.9f, 0f, this::applyEdit);

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top, bottom, ime = 0;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                top = bars.top;
                bottom = bars.bottom;
                ime = insets.getInsets(WindowInsets.Type.ime()).bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            insetTop = top;
            insetBottom = bottom;
            home.setInsets(top, bottom);
            drawer.setInsets(top, Math.max(bottom, ime));
            today.setInsets(top, Math.max(bottom, ime));
            ((FrameLayout.LayoutParams) editTop.getLayoutParams()).topMargin = top + Ui.dpi(this, 4);
            ((FrameLayout.LayoutParams) editTray.getLayoutParams()).bottomMargin = bottom + Ui.dpi(this, 8);
            editTop.requestLayout();
            editTray.requestLayout();
            return insets;
        });
        root.post(() -> { applyProgress(0); applyToday(0); applyEdit(0); });

        apps.addListener(this);
        if (apps.isLoaded()) onAppsChanged(); else apps.load();

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_USER_PRESENT);
        f.addAction(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        f.addAction(Intent.ACTION_DATE_CHANGED);
        f.addAction(Intent.ACTION_BATTERY_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, f);

        DotsListener.listener = pkgs -> runOnUiThread(() -> {
            badges.clear();
            badges.addAll(pkgs);
            home.updateBadges(badges);
            drawer.updateBadges(badges);
        });
        badges.addAll(DotsListener.current());
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(receiver);
        apps.removeListener(this);
        DotsListener.listener = null;
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        super.onStart();
        widgets.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyBackground();
        String fp = fingerprint();
        if (!fp.equals(builtFingerprint) && apps.isLoaded()) rebuild();
        else if (apps.isLoaded()) {
            refreshSuggestions();
            rebuildWidgets();
        }
        home.updateDate();
        home.setShowTop(prefs.clock(), prefs.glance());
        refreshEvent();
        updateWeatherChip();
        Weather.refresh(this, false, () -> { updateWeatherChip(); rebuildWidgets(); });
        if (returning && lastLaunched != null && !assembleOnResume) {
            AppIconView v = lastLaunched;
            root.post(() -> landOn(v));
        }
        returning = false;
        if (assembleOnResume) {
            assembleOnResume = false;
            home.clock.postDelayed(home.clock::assemble, 60);
            home.postDelayed(home::cascade, 40);
        }
    }

    /** Back from an app: home settles in, the icon lands with a bounce, and the dots light up under it. */
    private void landOn(AppIconView v) {
        if (Ui.motion(this) != Ui.MOTION_FULL || edit) return;
        if (progress == 0 && todayP == 0) {
            home.setScaleX(1.04f);
            home.setScaleY(1.04f);
            home.animate().scaleX(1f).scaleY(1f).setDuration(420)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(1.2f)).start();
        }
        if (!v.isAttachedToWindow() || !v.isShown()) return;
        v.pop();
        int[] p = new int[2], r = new int[2];
        v.getLocationOnScreen(p);
        root.getLocationOnScreen(r);
        RectF d = v.discBounds();
        grid.burst(p[0] - r[0] + d.centerX(), p[1] - r[1] + d.centerY());
    }

    @Override
    protected void onStop() {
        super.onStop();
        widgets.stop();
        returning = lastLaunched != null;
        // Coming back home should always show home, not whatever you left open
        closeOverlays();
        if (edit) setEdit(false, false);
        if (progress > 0) setDrawer(0, false);
        if (todayP > 0) { todaySpring.set(0); todaySettled(0); }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (closeOverlays()) return;
        if (edit) { setEdit(false, true); return; }
        if (progress > 0) animateDrawer(0, 0);
        if (todayP > 0) animateToday(0, 0);
    }

    @Override
    public void onBackPressed() {
        if (closeOverlays()) return;
        if (edit) { setEdit(false, true); return; }
        if (progress > 0) { animateDrawer(0, 0); return; }
        if (todayP > 0) animateToday(0, 0);
    }

    /** Close a menu, stack or sheet if one is open. Returns true if something closed. */
    private boolean closeOverlays() {
        boolean any = false;
        if (menu != null && menu.isShowing()) { menu.dismiss(); any = true; }
        if (stackPanel != null && stackPanel.isOpen()) { stackPanel.close(); any = true; }
        if (sheet != null && sheet.isShowing()) { sheet.dismiss(); any = true; }
        return any;
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            String a = i.getAction();
            if (Intent.ACTION_USER_PRESENT.equals(a)) {
                assembleOnResume = true;
                if (hasWindowFocus()) { assembleOnResume = false; home.clock.assemble(); home.cascade(); }
            } else if (Intent.ACTION_BATTERY_CHANGED.equals(a)) {
                int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
                if (level >= 0) home.setBattery(Math.round(level * 100f / scale), charging);
            } else {
                home.updateDate();
                if (Intent.ACTION_TIME_TICK.equals(a) && Calendar.getInstance().get(Calendar.MINUTE) % 10 == 0) {
                    refreshEvent();
                    Weather.refresh(HomeActivity.this, false, () -> { updateWeatherChip(); rebuildWidgets(); });
                }
            }
        }
    };

    // ------------------------------------------------------------------ building

    private String fingerprint() {
        return prefs.labels() + "|" + prefs.pinned() + "|" + prefs.dock() + "|" + prefs.hidden() + "|"
                + prefs.raw().getString("stacks", "") + "|" + Widgets.list(this) + "|" + prefs.big() + "|"
                + prefs.raw().getString("wsize", "") + "|" + Theme.get(this).id + "|" + appsVersion;
    }

    @Override
    public void onAppsChanged() {
        appsVersion++;
        rebuild();
    }

    private List<HomeView.Slot> homeSlots() {
        migrateWidgets();
        List<HomeView.Slot> out = new ArrayList<>();
        Map<String, Stacks.Stack> stacks = Stacks.load(this);
        Set<String> hidden = prefs.hidden();
        Set<String> big = prefs.big();
        for (String key : prefs.pinned()) {
            if (key.startsWith("w:")) {
                String w = key.substring(2);
                out.add(HomeView.Slot.widget(key, w, Widgets.size(this, w)));
                continue;
            }
            if (Stacks.isStack(key)) {
                Stacks.Stack s = stacks.get(key.substring(Stacks.PREFIX.length()));
                if (s == null) continue;
                List<Apps.App> l = new ArrayList<>();
                for (Apps.App a : apps.resolve(s.apps)) if (!hidden.contains(a.key)) l.add(a);
                if (l.isEmpty()) continue;
                HomeView.Slot slot = new HomeView.Slot(key, s.name, l);
                slot.big = big.contains(key);
                out.add(slot);
            } else {
                Apps.App a = apps.find(key);
                if (a != null && !hidden.contains(key)) {
                    HomeView.Slot slot = new HomeView.Slot(key, a);
                    slot.big = big.contains(key);
                    out.add(slot);
                }
            }
        }
        return out;
    }

    private List<Apps.App> dockApps() {
        Set<String> hidden = prefs.hidden();
        List<Apps.App> out = new ArrayList<>();
        for (Apps.App a : apps.resolve(prefs.dock())) if (!hidden.contains(a.key)) out.add(a);
        return out;
    }

    private void rebuild() {
        boolean labels = prefs.labels();
        List<HomeView.Slot> slots = homeSlots();
        List<Apps.App> dock = dockApps();
        homeKeys = new ArrayList<>();
        for (HomeView.Slot s : slots) homeKeys.add(s.key);
        dockKeys = new ArrayList<>();
        for (Apps.App a : dock) dockKeys.add(a.key);
        home.bind(slots, dock, labels, badges, this, edit);
        rebuildWidgets();
        drawer.build(apps.visible(), apps.suggestions(4), labels);
        drawer.setReveal(progress);
        builtFingerprint = fingerprint();
    }

    /** Fresh data for built-in widgets (battery, next event, weather) without rebuilding home. */
    private void rebuildWidgets() {
        home.refreshWidgets();
    }

    /** Widgets used to sit above the grid; move any old ones into it once. */
    private void migrateWidgets() {
        List<String> old = Widgets.list(this);
        if (old.isEmpty()) return;
        List<String> h = new ArrayList<>(prefs.pinned());
        int at = 0;
        for (String w : old) if (!h.contains("w:" + w)) h.add(at++, "w:" + w);
        prefs.setPinned(h);
        Widgets.save(this, new ArrayList<>());
    }

    /** Put a widget on home in the first free spot, if there's room. */
    private boolean addWidgetToHome(String w) {
        int[] size = Widgets.size(this, w);
        if (!HomeView.fits(homeSlots(), size[0], size[1])) {
            Toast.makeText(this, "No room on home for that size. Remove or shrink something first.", Toast.LENGTH_LONG).show();
            widgets.release(w);
            return false;
        }
        List<String> h = new ArrayList<>(prefs.pinned());
        h.add("w:" + w);
        prefs.setPinned(h);
        rebuild();
        return true;
    }

    /** Edit mode: tap a widget to change its size or remove it. */
    private void showWidgetSheet(String w) {
        sheet = new Sheet(root, Widgets.name(this, w).toUpperCase(), insetBottom);
        int[] cur = Widgets.size(this, w);
        sheet.section("SIZE");
        for (int[] sz : Widgets.sizes(this, w)) {
            boolean on = sz[0] == cur[0] && sz[1] == cur[1];
            String label = (on ? "✓  " : "") + sz[0] + " × " + sz[1];
            String sub = sz[0] == 4 ? (sz[1] == 1 ? "Full width, one row" : "Full width, " + sz[1] + " rows")
                    : sz[0] == 2 ? (sz[1] == 2 ? "Square, half width" : "Half width, " + sz[1] + " row" + (sz[1] > 1 ? "s" : "")) : "One cell";
            sheet.row(new Glyph(Glyph.BIG, Ui.dpi(this, 22), Ui.WHITE), label, sub, () -> {
                if (on) return;
                int[] before = Widgets.size(this, w);
                Widgets.setSize(this, w, sz);
                // Check it still fits; if not, put the old size back
                List<HomeView.Slot> slots = homeSlots();
                boolean[][] used = new boolean[HomeView.ROWS][4];
                boolean ok = true;
                for (HomeView.Slot s2 : slots) if (HomeView.GridBox.place(used, s2.spanX(), s2.spanY()) == null) ok = false;
                if (!ok) {
                    Widgets.setSize(this, w, before);
                    Toast.makeText(this, "Not enough room for that size", Toast.LENGTH_SHORT).show();
                }
                rebuild();
            });
        }
        sheet.section("WIDGET");
        sheet.row(new Glyph(Glyph.REMOVE, Ui.dpi(this, 22), Ui.RED), "Remove from home", null, () -> {
            List<String> h = new ArrayList<>(prefs.pinned());
            h.remove("w:" + w);
            prefs.setPinned(h);
            widgets.release(w);
            Ui.detent(root);
            rebuild();
        });
        sheet.show();
    }

    private void refreshSuggestions() {
        drawer.build(apps.visible(), apps.suggestions(4), prefs.labels());
        drawer.setReveal(progress);
    }

    @Override
    public Set<String> badges() { return badges; }

    /** Apply the theme: halftone background (or the wallpaper), colours, and status bar icons. */
    private void applyBackground() {
        // Old setting from before themes
        if (prefs.raw().getInt("background", 0) == 1 && !prefs.raw().contains("theme")) Theme.set(this, Theme.WALLPAPER);
        Theme t = Theme.get(this);
        boolean wall = t.id == Theme.WALLPAPER;
        if (wall) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
            root.setBackgroundColor(0x59000000);
            grid.setVisibility(View.GONE);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
            root.setBackgroundColor(t.bg);
            grid.setVisibility(View.VISIBLE);
            grid.refreshTheme();
        }
        grid.setGlowEnabled(!wall && Ui.motion(this) == Ui.MOTION_FULL);
        home.applyTheme();
        drawer.applyTheme();
        if (editTray != null) themeEditChrome();
        if (Build.VERSION.SDK_INT >= 30 && getWindow().getInsetsController() != null) {
            int light = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            getWindow().getInsetsController().setSystemBarsAppearance(t.light ? light : 0, light);
        }
    }

    // ------------------------------------------------------------------ drawer motion

    private void applyProgress(float p) {
        progress = p;
        int h = root.getHeight();
        drawer.setTranslationY((1 - Ui.clamp(p, -0.1f, 1.1f)) * h);
        drawer.setVisibility(p > 0.001f ? View.VISIBLE : View.INVISIBLE);
        float s = 1 - 0.06f * Ui.clamp(p, 0, 1);
        if (!edit) {
            home.setScaleX(s);
            home.setScaleY(s);
            home.setAlpha((1 - 0.7f * Ui.clamp(p, 0, 1)) * (1 - 0.6f * todayP));
        }
        drawer.setReveal(p);
    }

    private void animateDrawer(float target, float velocity) {
        if (calmAnim != null) calmAnim.cancel();
        int motion = Ui.motion(this);
        if (target == 0) drawer.hideKeyboard();
        if (motion == Ui.MOTION_FULL) {
            drawerSpring.value = progress;
            drawerSpring.animateTo(target, velocity);
        } else if (motion == Ui.MOTION_CALM) {
            drawerSpring.cancel();
            calmAnim = ValueAnimator.ofFloat(progress, target);
            calmAnim.setDuration(160);
            calmAnim.addUpdateListener(a -> applyProgress((float) a.getAnimatedValue()));
            calmAnim.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator a) { settled(target); }
            });
            calmAnim.start();
        } else {
            setDrawer(target, true);
        }
    }

    private void setDrawer(float target, boolean notify) {
        drawerSpring.cancel();
        if (calmAnim != null) calmAnim.cancel();
        drawerSpring.value = target;
        applyProgress(target);
        if (notify) settled(target);
        else if (target == 0) {
            drawer.closed();
            drawer.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        }
    }

    private void settled(float v) {
        if (v >= 1) {
            Ui.detent(root);
            drawer.setDescendantFocusability(android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS);
            drawer.opened(prefs.keyboardOnOpen());
        } else if (v <= 0) {
            drawer.closed();
            drawer.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            root.requestFocus();
        }
    }

    // ------------------------------------------------------------------ Today page

    private void applyToday(float p) {
        todayP = p;
        int w = root.getWidth();
        today.setTranslationX((Ui.clamp(p, 0, 1) - 1) * w);
        today.setVisibility(p > 0.001f ? View.VISIBLE : View.INVISIBLE);
        if (!edit) {
            home.setTranslationX(Ui.clamp(p, 0, 1) * w * 0.25f);
            home.setAlpha((1 - 0.6f * Ui.clamp(p, 0, 1)) * (1 - 0.7f * progress));
        }
    }

    private void animateToday(float target, float velocity) {
        if (target > 0 && todayP == 0) today.refresh();
        if (target == 0) today.hideKeyboard();
        if (Ui.motion(this) == Ui.MOTION_OFF) {
            todaySpring.set(target);
            todaySettled(target);
        } else {
            todaySpring.value = todayP;
            todaySpring.animateTo(target, velocity);
        }
    }

    private void todaySettled(float v) {
        if (v >= 1) {
            Ui.detent(root);
            today.setDescendantFocusability(android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS);
        } else if (v <= 0) {
            today.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            root.requestFocus();
        }
    }

    // ------------------------------------------------------------------ edit mode

    private void buildEditChrome() {
        // Top: a quiet label, and a red Done pill
        editTop = new LinearLayout(this);
        editTop.setGravity(Gravity.CENTER_VERTICAL);
        editTop.setPadding(Ui.dpi(this, 22), Ui.dpi(this, 6), Ui.dpi(this, 14), Ui.dpi(this, 6));
        editTitle = new TextView(this);
        editTitle.setText("EDIT HOME");
        editTitle.setTypeface(Ui.mono(this));
        editTitle.setLetterSpacing(0.16f);
        editTitle.setTextSize(11);
        editTitle.setGravity(Gravity.CENTER_VERTICAL);
        editTop.addView(editTitle, new LinearLayout.LayoutParams(0, Ui.dpi(this, 36), 1f));
        editDone = new TextView(this);
        editDone.setText("Done");
        editDone.setTextColor(Ui.WHITE);
        editDone.setTextSize(14);
        editDone.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        editDone.setPadding(Ui.dpi(this, 18), Ui.dpi(this, 8), Ui.dpi(this, 18), Ui.dpi(this, 8));
        GradientDrawable db = new GradientDrawable();
        db.setColor(Ui.RED);
        db.setCornerRadius(Ui.dp(this, 20));
        editDone.setBackground(db);
        editDone.setOnClickListener(v -> { Ui.tap(v); setEdit(false, true); });
        editTop.addView(editDone);
        root.addView(editTop, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        // Bottom: one compact floating pill
        editTray = new LinearLayout(this);
        editTray.setGravity(Gravity.CENTER);
        editTray.setPadding(Ui.dpi(this, 6), Ui.dpi(this, 6), Ui.dpi(this, 6), Ui.dpi(this, 6));
        editTray.addView(pillButton(Glyph.WIDGETS, "Widgets", this::showWidgetPicker));
        editTray.addView(pillButton(Glyph.WALLPAPER, "Theme", this::showThemeSheet));
        editTray.addView(pillButton(Glyph.SETTINGS, "Settings", () -> startActivity(new Intent(this, SettingsActivity.class))));
        root.addView(editTray, new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        editTop.setVisibility(View.INVISIBLE);
        editTray.setVisibility(View.INVISIBLE);
        themeEditChrome();
    }

    private void themeEditChrome() {
        Theme t = Theme.get(this);
        editTitle.setTextColor(t.textMute);
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(t.surface);
        pill.setStroke(Ui.dpi(this, 1), t.surfaceLine);
        pill.setCornerRadius(Ui.dp(this, 30));
        editTray.setBackground(pill);
        editTray.setElevation(Ui.dp(this, 10));
        for (int i = 0; i < editTray.getChildCount(); i++) {
            LinearLayout b = (LinearLayout) editTray.getChildAt(i);
            ImageView ic = (ImageView) b.getChildAt(0);
            TextView tx = (TextView) b.getChildAt(1);
            ic.setImageDrawable(new Glyph(((Glyph) ic.getDrawable()).kind(), Ui.dpi(this, 18), t.text));
            tx.setTextColor(t.text);
        }
    }

    private View pillButton(int glyph, String label, Runnable run) {
        LinearLayout b = new LinearLayout(this);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(Ui.dpi(this, 16), Ui.dpi(this, 10), Ui.dpi(this, 18), Ui.dpi(this, 10));
        ImageView ic = new ImageView(this);
        ic.setImageDrawable(new Glyph(glyph, Ui.dpi(this, 18), Ui.WHITE));
        b.addView(ic, new LinearLayout.LayoutParams(Ui.dpi(this, 18), Ui.dpi(this, 18)));
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(13.5f);
        t.setPadding(Ui.dpi(this, 8), 0, 0, 0);
        b.addView(t);
        b.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x22888888), null, new GradientDrawable() {{ setCornerRadius(Ui.dp(HomeActivity.this, 24)); setColor(Ui.WHITE); }}));
        b.setOnClickListener(v -> { Ui.tap(v); run.run(); });
        return b;
    }

    private void setEdit(boolean on, boolean animate) {
        if (on == edit) return;
        edit = on;
        if (on) {
            if (progress > 0) setDrawer(0, false);
            if (todayP > 0) { todaySpring.set(0); todaySettled(0); }
            Ui.firm(root);
        }
        rebuild();
        if (animate && Ui.motion(this) != Ui.MOTION_OFF) {
            editSpring.value = editP;
            editSpring.animateTo(on ? 1 : 0, 0);
        } else {
            editSpring.set(on ? 1 : 0);
        }
    }

    private void applyEdit(float e) {
        editP = e;
        float s = 1 - 0.16f * e;
        home.setScaleX(s);
        home.setScaleY(s);
        home.setTranslationY(-Ui.dp(this, 22) * e);
        home.setTranslationX(0);
        home.setAlpha(1);
        editFrame.setScaleX(s * 1.01f);
        editFrame.setScaleY(s * 1.005f);
        editFrame.setTranslationY(-Ui.dp(this, 22) * e);
        editFrame.setAlpha(e);
        editFrame.setVisibility(e > 0.01f ? View.VISIBLE : View.INVISIBLE);
        float chrome = Ui.clamp((e - 0.3f) / 0.7f, 0, 1);
        editTop.setAlpha(chrome);
        editTray.setAlpha(chrome);
        editTop.setTranslationY(-(1 - chrome) * Ui.dp(this, 20));
        editTray.setTranslationY((1 - chrome) * Ui.dp(this, 20));
        editTop.setVisibility(e > 0.01f ? View.VISIBLE : View.INVISIBLE);
        editTray.setVisibility(e > 0.01f ? View.VISIBLE : View.INVISIBLE);
    }

    // ---- drag & drop in edit mode

    private static final int Z_NONE = -1, Z_HOME = HomeView.ZONE_HOME, Z_DOCK = HomeView.ZONE_DOCK, Z_REMOVE = 9;
    private View dragSrc;
    private View dragFloat;
    private int floatW, floatH;
    private int dragZone = Z_NONE, dragIndex = -1;
    private int hoverZone = Z_NONE, hoverIndex = -1;
    private boolean hoverMerge;
    private View hoverCell;

    private View cellAt(float x, float y, int[] zoneOut, int[] indexOut) {
        int[] rp = new int[2];
        root.getLocationOnScreen(rp);
        float s = home.getScaleX();
        for (int z = 0; z < 2; z++) {
            List<? extends View> cells = z == 0 ? home.homeCells : home.dockCells;
            for (int i = 0; i < cells.size(); i++) {
                View v = cells.get(i);
                int[] p = new int[2];
                v.getLocationOnScreen(p);
                float l = p[0] - rp[0], t = p[1] - rp[1];
                if (x >= l && x <= l + v.getWidth() * s && y >= t && y <= t + v.getHeight() * s) {
                    zoneOut[0] = z;
                    indexOut[0] = i;
                    return v;
                }
            }
        }
        return null;
    }

    /** How close the finger is to the disc centre of a cell (0 = centre, 1 = edge of disc). */
    private float discDistance(View v, float x, float y) {
        int[] rp = new int[2], p = new int[2];
        root.getLocationOnScreen(rp);
        v.getLocationOnScreen(p);
        float s = home.getScaleX();
        RectF d = ((Cell) v).discBounds();
        float cx = p[0] - rp[0] + d.centerX() * s, cy = p[1] - rp[1] + d.centerY() * s;
        float r = Math.min(d.width(), d.height()) / 2 * s;
        return (float) Math.hypot(x - cx, y - cy) / r;
    }

    private boolean beginAppDrag(float x, float y) {
        int[] z = new int[1], i = new int[1];
        View src = cellAt(x, y, z, i);
        if (src == null || !((Cell) src).hasContent()) return false;
        List<String> keys = z[0] == Z_HOME ? homeKeys : dockKeys;
        if (i[0] >= keys.size()) return false;
        String key = keys.get(i[0]);
        dragSrc = src;
        dragZone = z[0];
        dragIndex = i[0];
        if (key.startsWith("w:") || src instanceof BigStackView) {
            // Widgets and large folders: drag a small picture of them
            float k = 0.55f * home.getScaleX();
            floatW = Math.max(1, (int) (src.getWidth() * k));
            floatH = Math.max(1, (int) (src.getHeight() * k));
            android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(floatW, floatH, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas cv = new android.graphics.Canvas(b);
            cv.scale(k, k);
            src.draw(cv);
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(b);
            iv.setAlpha(0.92f);
            iv.setElevation(Ui.dp(this, 16));
            dragFloat = iv;
        } else {
            AppIconView fl = new AppIconView(this, 48, false);
            if (Stacks.isStack(key)) {
                Stacks.Stack st = Stacks.get(this, key);
                fl.bindStack(key, st == null ? "" : st.name, st == null ? new ArrayList<>() : apps.resolve(st.apps), null);
            } else {
                fl.bind(apps.find(key), null);
            }
            fl.setLifted(true);
            fl.setClickable(false);
            floatW = floatH = Ui.dpi(this, 66);
            dragFloat = fl;
        }
        root.addView(dragFloat, new FrameLayout.LayoutParams(floatW, floatH));
        moveFloat(x, y);
        ((Cell) src).setGhost(true);
        editTitle.setText("Drag here to remove from home");
        editTitle.setTextColor(0xFFAAAAAA);
        editDone.setVisibility(View.INVISIBLE);
        Ui.firm(root);
        return true;
    }

    private void moveFloat(float x, float y) {
        if (dragFloat == null) return;
        dragFloat.setTranslationX(x - floatW / 2f);
        dragFloat.setTranslationY(y - floatH / 2f - Ui.dp(this, 24));
    }

    private void dragMove(float x, float y) {
        moveFloat(x, y);
        float fy = y - Ui.dp(this, 24);
        int zone = Z_NONE, index = -1;
        boolean merge = false;
        View cell = null;
        int[] tl = new int[2];
        editTop.getLocationOnScreen(tl);
        int[] rp = new int[2];
        root.getLocationOnScreen(rp);
        if (fy < tl[1] - rp[1] + editTop.getHeight() + Ui.dp(this, 10)) {
            zone = Z_REMOVE;
        } else {
            int[] z = new int[1], i = new int[1];
            cell = cellAt(x, fy, z, i);
            if (cell != null && cell != dragSrc) {
                zone = z[0];
                index = i[0];
                // Dropping right on top of another app (home only) makes or grows a stack
                boolean widgetInvolved = cell instanceof Widgets.WidgetCell || dragSrc instanceof Widgets.WidgetCell;
                merge = !widgetInvolved && zone == Z_HOME && ((Cell) cell).hasContent() && discDistance(cell, x, fy) < 0.62f;
            }
        }
        if (zone != hoverZone || index != hoverIndex || merge != hoverMerge) {
            if (hoverCell != null) ((Cell) hoverCell).setDropTarget(false, false);
            hoverZone = zone;
            hoverIndex = index;
            hoverMerge = merge;
            hoverCell = cell;
            if (cell != null) ((Cell) cell).setDropTarget(true, merge);
            boolean remove = zone == Z_REMOVE;
            editTitle.setText(remove ? "Let go to remove from home" : merge ? "Let go to make a stack" : "Drag here to remove from home");
            editTitle.setTextColor(remove ? Ui.RED : 0xFFAAAAAA);
            if (zone != Z_NONE) Ui.tick(root);
        }
    }

    private void dragEnd(boolean drop) {
        if (dragFloat != null) root.removeView(dragFloat);
        if (hoverCell != null) ((Cell) hoverCell).setDropTarget(false, false);
        if (dragSrc != null) ((Cell) dragSrc).setGhost(false);
        editTitle.setText("Edit home");
        editTitle.setTextColor(0xFFAAAAAA);
        editDone.setVisibility(View.VISIBLE);
        if (drop && dragSrc != null && hoverZone != Z_NONE) applyDrop(dragZone, dragIndex, hoverZone, hoverIndex, hoverMerge);
        dragSrc = null;
        dragFloat = null;
        hoverCell = null;
        hoverZone = Z_NONE;
        hoverIndex = -1;
        hoverMerge = false;
    }

    private void applyDrop(int srcZone, int srcIdx, int dstZone, int dstIdx, boolean merge) {
        List<String> h = new ArrayList<>(homeKeys), d = new ArrayList<>(dockKeys);
        List<String> src = srcZone == Z_HOME ? h : d;
        if (srcIdx >= src.size()) return;
        String item = src.get(srcIdx);
        boolean itemIsStack = Stacks.isStack(item);

        boolean itemIsWidget = item.startsWith("w:");
        if (dstZone == Z_REMOVE) {
            src.remove(srcIdx);
            if (itemIsStack) Stacks.delete(this, item);
            if (itemIsWidget) widgets.release(item.substring(2));
            Ui.detent(root);
        } else if (merge) {
            String target = h.get(dstIdx);
            List<String> moving = new ArrayList<>();
            if (itemIsStack) {
                Stacks.Stack s = Stacks.get(this, item);
                if (s != null) moving.addAll(s.apps);
            } else {
                moving.add(item);
            }
            if (Stacks.isStack(target)) {
                for (String k : moving) Stacks.addApp(this, target, k);
                if (itemIsStack) Stacks.delete(this, item);
                src.remove(srcIdx);
            } else {
                List<String> both = new ArrayList<>();
                both.add(target);
                both.addAll(moving);
                String ns = Stacks.create(this, both);
                if (itemIsStack) Stacks.delete(this, item);
                h.set(dstIdx, ns);
                src.remove(srcIdx);
            }
            Ui.firm(root);
        } else {
            List<String> dst = dstZone == Z_HOME ? h : d;
            if (dstZone == Z_DOCK && (itemIsStack || itemIsWidget)) {
                Toast.makeText(this, "Only apps go in the dock", Toast.LENGTH_SHORT).show();
                return;
            }
            if (dstIdx < dst.size()) {
                // Swap with whatever is there
                String other = dst.get(dstIdx);
                if (srcZone == Z_DOCK && (Stacks.isStack(other) || other.startsWith("w:"))) {
                    Toast.makeText(this, "Only apps go in the dock", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (src == dst) Collections.swap(dst, srcIdx, dstIdx);
                else {
                    dst.set(dstIdx, item);
                    src.set(srcIdx, other);
                }
            } else {
                // An empty cell: move it there
                int max = dstZone == Z_HOME ? HomeView.HOME_SLOTS : HomeView.DOCK_SLOTS;
                if (src != dst && dst.size() >= max) return;
                src.remove(srcIdx);
                dst.add(Math.min(dstIdx, dst.size()), item);
            }
            Ui.detent(root);
        }
        while (h.size() > 24) h.remove(h.size() - 1);
        while (d.size() > HomeView.DOCK_SLOTS) d.remove(d.size() - 1);
        prefs.setPinned(h);
        prefs.setDock(d);
        rebuild();
    }

    // ------------------------------------------------------------------ sheets

    /** Widgets from Nothing's own apps (Weather, Clock, Recorder, Nothing X, ...). */
    private static boolean isNothing(AppWidgetProviderInfo info) {
        String pkg = info.provider.getPackageName();
        return pkg.startsWith("com.nothing") || pkg.startsWith("com.nt.");
    }

    private void showWidgetPicker() {
        sheet = new Sheet(root, "WIDGETS", insetBottom);
        List<String> have = new ArrayList<>();
        for (String k : prefs.pinned()) if (k.startsWith("w:")) have.add(k.substring(2));

        AppWidgetManager wm = AppWidgetManager.getInstance(this);
        List<AppWidgetProviderInfo> providers = new ArrayList<>();
        for (android.os.UserHandle u : getSystemService(android.os.UserManager.class).getUserProfiles()) {
            try { providers.addAll(wm.getInstalledProvidersForProfile(u)); } catch (Exception ignored) { }
        }
        PackageManager pm = getPackageManager();
        providers.sort((a, b) -> {
            int c = appLabel(pm, a).compareToIgnoreCase(appLabel(pm, b));
            return c != 0 ? c : a.loadLabel(pm).compareToIgnoreCase(b.loadLabel(pm));
        });
        int density = getResources().getDisplayMetrics().densityDpi;

        // Nothing's own widgets first
        sheet.section("NOTHING");
        boolean anyNothing = false;
        for (AppWidgetProviderInfo info : providers) {
            if (!isNothing(info)) continue;
            anyNothing = true;
            widgetRow(info, pm, density, true);
        }
        if (!anyNothing) {
            sheet.row(null, "No Nothing widgets found",
                    "Widgets built into the Nothing launcher only work there. Widgets from Nothing apps show up here.", null);
        }

        sheet.section("DOT HOME");
        boolean any = false;
        for (String[] b : Widgets.BUILT_IN) {
            if (have.contains("b:" + b[0])) continue;
            any = true;
            sheet.row(new Glyph(Glyph.WIDGETS, Ui.dpi(this, 22), Ui.WHITE), b[1], b[2], () -> {
                if (addWidgetToHome("b:" + b[0]) && b[0].equals("weather") && !Weather.enabled(this)) {
                    Toast.makeText(this, "Turn on weather in settings to fill it in", Toast.LENGTH_LONG).show();
                }
            });
        }
        if (!any) sheet.row(null, "All of Dot Home's widgets are on home", null, null);

        sheet.section("OTHER APPS");
        for (AppWidgetProviderInfo info : providers) {
            if (!isNothing(info)) widgetRow(info, pm, density, false);
        }
        sheet.show();
    }

    private void widgetRow(AppWidgetProviderInfo info, PackageManager pm, int density, boolean bigPreview) {
        Drawable preview = null;
        try { preview = info.loadPreviewImage(this, density); } catch (Exception ignored) { }
        if (preview == null) try { preview = info.loadIcon(this, density); } catch (Exception ignored) { }
        int[] size = defaultCells(info);
        String sub = appLabel(pm, info) + " · " + size[0] + " × " + size[1];
        View row = sheet.row(preview, info.loadLabel(pm), sub, () -> addAndroidWidget(info));
        if (bigPreview && preview != null && row instanceof LinearLayout && ((LinearLayout) row).getChildAt(0) instanceof ImageView) {
            // Nothing's widgets get a larger preview, so you can see what you're adding
            ImageView iv = (ImageView) ((LinearLayout) row).getChildAt(0);
            iv.setLayoutParams(new LinearLayout.LayoutParams(Ui.dpi(this, 96), Ui.dpi(this, 96)));
        }
    }

    private int[] defaultCells(AppWidgetProviderInfo info) {
        float d = getResources().getDisplayMetrics().density;
        int cw = (int) Math.ceil(info.minWidth / (80 * d)), ch = (int) Math.ceil(info.minHeight / (80 * d));
        cw = cw <= 1 ? 1 : cw <= 2 ? 2 : 4;
        return new int[]{cw, Math.max(1, Math.min(4, ch))};
    }

    private static String appLabel(PackageManager pm, AppWidgetProviderInfo info) {
        try {
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(info.provider.getPackageName(), 0)));
        } catch (Exception e) {
            return info.provider.getPackageName();
        }
    }

    private void addAndroidWidget(AppWidgetProviderInfo info) {
        int id = widgets.host.allocateAppWidgetId();
        boolean bound;
        try {
            bound = widgets.manager.bindAppWidgetIdIfAllowed(id, info.getProfile(), info.provider, null);
        } catch (Exception e) {
            bound = false;
        }
        if (bound) {
            configureOrAdd(id);
        } else {
            pendingWidgetId = id;
            Intent i = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.getProfile());
            try {
                startActivityForResult(i, REQ_BIND);
            } catch (Exception e) {
                widgets.host.deleteAppWidgetId(id);
                Toast.makeText(this, "Couldn't add that widget", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void configureOrAdd(int id) {
        AppWidgetProviderInfo info = widgets.manager.getAppWidgetInfo(id);
        if (info != null && info.configure != null) {
            pendingWidgetId = id;
            try {
                widgets.host.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIG, null);
                return;
            } catch (Exception ignored) { }
        }
        finishAddWidget(id);
    }

    private void finishAddWidget(int id) {
        addWidgetToHome("a:" + id);
    }

    @Override
    protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req == REQ_BIND) {
            if (result == RESULT_OK && pendingWidgetId != -1) configureOrAdd(pendingWidgetId);
            else if (pendingWidgetId != -1) widgets.host.deleteAppWidgetId(pendingWidgetId);
        } else if (req == REQ_CONFIG) {
            if (result == RESULT_OK && pendingWidgetId != -1) finishAddWidget(pendingWidgetId);
            else if (pendingWidgetId != -1) widgets.host.deleteAppWidgetId(pendingWidgetId);
            pendingWidgetId = -1;
        }
    }

    private void showThemeSheet() {
        sheet = new Sheet(root, "THEME", insetBottom);
        int cur = Theme.get(this).id;
        sheet.row(null, (cur == Theme.DOT ? "✓  " : "") + "Dot", "Black, with a halftone field and the Glyph ring. Kind to OLED.", () -> switchTheme(Theme.DOT));
        sheet.row(null, (cur == Theme.PAPER ? "✓  " : "") + "Paper", "Nothing's off-white: light dots, white icons, black glyphs.", () -> switchTheme(Theme.PAPER));
        sheet.row(null, (cur == Theme.WALLPAPER ? "✓  " : "") + "Your wallpaper", "Your own picture behind Dot's colours, dimmed a little.", () -> switchTheme(Theme.WALLPAPER));
        sheet.row(null, "Change wallpaper…", "Opens your phone's wallpaper picker", () -> {
            try {
                startActivity(Intent.createChooser(new Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper"));
            } catch (Exception ignored) { }
        });
        sheet.show();
    }

    private void switchTheme(int id) {
        Theme.set(this, id);
        applyBackground();
        rebuild();
    }

    private void showStackSheet(View anchor, RectF disc, String slot, String name, List<Apps.App> stackApps) {
        sheet = new Sheet(root, name == null ? "STACK" : name.toUpperCase(), insetBottom);
        sheet.row(new Glyph(Glyph.STACK, Ui.dpi(this, 22), Ui.WHITE), "Open", null, () -> openStack(anchor, disc, slot, name, stackApps));
        sheet.row(null, "Rename", null, () -> renameDialog(slot, name));
        boolean isBig = prefs.big().contains(slot);
        sheet.row(new Glyph(Glyph.BIG, Ui.dpi(this, 22), Ui.WHITE), isBig ? "Make small" : "Make large",
                isBig ? "Back to a normal icon" : "A 2 × 2 folder: open its four apps directly", () -> toggleBigSlot(slot));
        sheet.row(null, "Break apart", "Put each app back on home", () -> breakStack(slot));
        sheet.row(null, "Remove from home", "The apps stay in the drawer", () -> {
            List<String> h = new ArrayList<>(prefs.pinned());
            h.remove(slot);
            prefs.setPinned(h);
            Stacks.delete(this, slot);
            rebuild();
        });
        sheet.row(new Glyph(Glyph.HOME_ADD, Ui.dpi(this, 22), Ui.WHITE), "Edit home", "Drag things around", () -> setEdit(true, true));
        sheet.show();
    }

    private void renameDialog(String slot, String current) {
        EditText input = new EditText(this);
        input.setText(current);
        input.setSelection(current == null ? 0 : current.length());
        input.setSingleLine(true);
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(Ui.dpi(this, 20), Ui.dpi(this, 8), Ui.dpi(this, 20), 0);
        wrap.addView(input);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Name this stack")
                .setView(wrap)
                .setPositiveButton("Save", (d, w) -> renameStack(slot, input.getText().toString()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void breakStack(String slot) {
        Stacks.Stack s = Stacks.get(this, slot);
        List<String> h = new ArrayList<>(prefs.pinned());
        int at = h.indexOf(slot);
        if (at < 0 || s == null) return;
        h.remove(at);
        for (String k : s.apps) {
            if (h.size() >= HomeView.HOME_SLOTS) break;
            if (!h.contains(k)) h.add(Math.min(at++, h.size()), k);
        }
        prefs.setPinned(h);
        Stacks.delete(this, slot);
        rebuild();
    }

    // ------------------------------------------------------------------ stacks

    private void openStack(View anchor, RectF disc, String slot, String name, List<Apps.App> stackApps) {
        if (stackApps == null) return;
        stackPanel = StackPanel.show(root, anchor, disc, slot, name, stackApps, prefs.labels(), this);
    }

    @Override
    public void onBigStackOpen(BigStackView v) {
        if (edit) return;
        openStack(v, v.discBounds(), v.slot, v.name, v.apps);
    }

    @Override
    public void onBigStackHold(BigStackView v) {
        if (edit) return;
        showStackSheet(v, v.discBounds(), v.slot, v.name, v.apps);
    }

    private void toggleBigSlot(String slot) {
        Set<String> b = prefs.big();
        if (!b.remove(slot)) b.add(slot);
        prefs.setBig(b);
        rebuild();
    }

    @Override public boolean isBig(Apps.App a) { return prefs.big().contains(a.key); }
    @Override public void toggleBig(Apps.App a) { toggleBigSlot(a.key); }


    @Override
    public void renameStack(String slot, String name) {
        Stacks.rename(this, slot, name);
        rebuild();
    }

    @Override
    public void onStackClosed() { }

    // ------------------------------------------------------------------ launching

    @Override
    public void onAppClick(AppIconView v) {
        if (v.isStack()) { openStack(v, v.discBounds(), v.slot, v.stackName, v.stackApps); return; }
        launchApp(v.app, v);
    }

    @Override
    public void onAppLongClick(AppIconView v) {
        if (v.isStack()) { showStackSheet(v, v.discBounds(), v.slot, v.stackName, v.stackApps); return; }
        if (v.app == null) return;
        drawer.hideKeyboard();
        menu = AppMenu.show(root, v, this);
    }

    @Override
    public void launchApp(Apps.App a, View from) {
        if (a == null) return;
        Rect bounds = new Rect();
        Bundle opts = null;
        if (from != null) {
            from.getGlobalVisibleRect(bounds);
            if (from instanceof AppIconView) {
                RectF d = ((AppIconView) from).discBounds();
                int[] pos = new int[2];
                from.getLocationOnScreen(pos);
                bounds.set((int) (pos[0] + d.left), (int) (pos[1] + d.top), (int) (pos[0] + d.right), (int) (pos[1] + d.bottom));
                if (Ui.motion(this) != Ui.MOTION_OFF) {
                    opts = ActivityOptions.makeScaleUpAnimation(from, (int) d.left, (int) d.top, (int) d.width(), (int) d.height()).toBundle();
                }
            } else if (Ui.motion(this) != Ui.MOTION_OFF) {
                opts = ActivityOptions.makeScaleUpAnimation(from, 0, 0, from.getWidth(), from.getHeight()).toBundle();
            }
        }
        try {
            apps.launcherApps().startMainActivity(a.component, a.user, bounds, opts);
            lastLaunched = from instanceof AppIconView ? (AppIconView) from : null;
            apps.recordLaunch(a);
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't open " + a.label, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void launchIntent(Intent i, View from) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Bundle opts = from != null && Ui.motion(this) != Ui.MOTION_OFF
                ? ActivityOptions.makeScaleUpAnimation(from, 0, 0, from.getWidth(), from.getHeight()).toBundle() : null;
        try {
            startActivity(i, opts);
        } catch (ActivityNotFoundException e) {
            if (Intent.ACTION_WEB_SEARCH.equals(i.getAction())) {
                String q = i.getStringExtra(android.app.SearchManager.QUERY);
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q)))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    return;
                } catch (ActivityNotFoundException ignored) { }
            }
            Toast.makeText(this, "No app on this phone can do that", Toast.LENGTH_SHORT).show();
        } catch (SecurityException e) {
            Toast.makeText(this, "That isn't allowed on this phone", Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------------------------------------ glance chips

    @Override public void onClockTap() { if (!edit) launchIntent(new Intent(AlarmClock.ACTION_SHOW_ALARMS), home.clock); }

    @Override
    public void onDateTap() {
        if (edit) return;
        Uri.Builder b = CalendarContract.CONTENT_URI.buildUpon().appendPath("time");
        ContentUris.appendId(b, System.currentTimeMillis());
        launchIntent(new Intent(Intent.ACTION_VIEW).setData(b.build()), null);
    }

    @Override public void onBatteryTap() { if (!edit) launchIntent(new Intent(Intent.ACTION_POWER_USAGE_SUMMARY), null); }

    @Override public void onWeatherTap() { if (!edit) animateToday(1, 0); }

    @Override
    public void onEventTap() {
        if (edit) return;
        if (eventId < 0) { onDateTap(); return; }
        launchIntent(new Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)), null);
    }

    private void updateWeatherChip() {
        Weather.Data d = prefs.glance() && Weather.enabled(this) ? Weather.cached(this) : null;
        home.setWeather(d == null ? null : Weather.temp(this, d.temp) + " " + Weather.describe(d.code));
    }

    /** The next event in the coming 24 hours, if calendar access was allowed. */
    private void refreshEvent() {
        if (!prefs.glance() || checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            home.setEvent(null);
            return;
        }
        new Thread(() -> {
            String text = null;
            long id = -1;
            long now = System.currentTimeMillis();
            Uri.Builder b = CalendarContract.Instances.CONTENT_URI.buildUpon();
            ContentUris.appendId(b, now - 30 * 60 * 1000L);
            ContentUris.appendId(b, now + 24 * 3600 * 1000L);
            String[] proj = {CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END, CalendarContract.Instances.EVENT_ID};
            try (Cursor c = getContentResolver().query(b.build(), proj,
                    CalendarContract.Instances.ALL_DAY + "=0 AND " + CalendarContract.Instances.END + ">?",
                    new String[]{String.valueOf(now)}, CalendarContract.Instances.BEGIN + " ASC")) {
                if (c != null && c.moveToFirst()) {
                    String title = c.getString(0);
                    long begin = c.getLong(1);
                    id = c.getLong(3);
                    Calendar cb = Calendar.getInstance();
                    cb.setTimeInMillis(begin);
                    boolean isToday = cb.get(Calendar.DAY_OF_YEAR) == Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
                    String time = android.text.format.DateFormat.getTimeFormat(this).format(new Date(begin));
                    String name = title == null || title.isEmpty() ? "Event" : title;
                    text = begin <= now ? name + " · now" : name + " " + (isToday ? time : "tmrw " + time);
                }
            } catch (Exception ignored) { }
            String t = text;
            long eid = id;
            runOnUiThread(() -> {
                eventId = eid;
                home.setEvent(t);
            });
        }, "next-event").start();
    }

    // ------------------------------------------------------------------ touch & hold actions

    @Override
    public void info(Apps.App a, View from) {
        try {
            Rect r = new Rect();
            if (from != null) from.getGlobalVisibleRect(r);
            apps.launcherApps().startAppDetailsActivity(a.component, a.user, r, null);
        } catch (Exception e) {
            launchIntent(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", a.pkg(), null)), null);
        }
    }

    @Override public boolean onHome(Apps.App a) { return prefs.pinned().contains(a.key); }
    @Override public boolean inDock(Apps.App a) { return prefs.dock().contains(a.key); }
    @Override public boolean dockFull() { return dockApps().size() >= 4; }

    @Override
    public String openStackSlot() {
        return stackPanel != null && stackPanel.isOpen() ? stackPanel.slot : null;
    }

    @Override
    public void removeFromStack(Apps.App a) {
        String slot = openStackSlot();
        if (slot == null) return;
        String now = Stacks.removeApp(this, slot, a.key);
        List<String> h = new ArrayList<>(prefs.pinned());
        int at = h.indexOf(slot);
        if (at >= 0) {
            if (now == null) h.remove(at);
            else h.set(at, now);
            // The app taken out goes back on home next to where the stack was
            if (!h.contains(a.key) && h.size() < HomeView.HOME_SLOTS) h.add(Math.min(at + 1, h.size()), a.key);
        }
        prefs.setPinned(h);
        if (stackPanel != null) stackPanel.close();
        rebuild();
    }

    @Override
    public void toggleHome(Apps.App a) {
        List<String> l = new ArrayList<>(prefs.pinned());
        if (l.remove(a.key)) {
            prefs.setPinned(l);
        } else {
            if (l.size() >= HomeView.HOME_SLOTS) { Toast.makeText(this, "Home is full (12 spots). Unpin one first.", Toast.LENGTH_SHORT).show(); return; }
            l.add(a.key);
            prefs.setPinned(l);
            List<String> d = new ArrayList<>(prefs.dock());
            if (d.remove(a.key)) prefs.setDock(d);
        }
        rebuild();
    }

    @Override
    public void toggleDock(Apps.App a) {
        List<String> d = new ArrayList<>(prefs.dock());
        if (!d.remove(a.key)) {
            d.add(a.key);
            List<String> l = new ArrayList<>(prefs.pinned());
            if (l.remove(a.key)) prefs.setPinned(l);
        }
        prefs.setDock(d);
        rebuild();
    }

    @Override
    public void hide(Apps.App a) {
        Set<String> h = prefs.hidden();
        h.add(a.key);
        prefs.setHidden(h);
        Toast.makeText(this, a.label + " hidden. Find it in Dot Home settings.", Toast.LENGTH_SHORT).show();
        if (stackPanel != null && stackPanel.isOpen()) stackPanel.close();
        rebuild();
    }

    @Override
    public void uninstall(Apps.App a) {
        Intent i = new Intent(Intent.ACTION_DELETE, Uri.fromParts("package", a.pkg(), null));
        i.putExtra(Intent.EXTRA_USER, a.user);
        launchIntent(i, null);
    }

    @Override
    public void setBackdrop(boolean blurred) {
        if (Build.VERSION.SDK_INT >= 31) {
            RenderEffect blur = blurred ? RenderEffect.createBlurEffect(Ui.dp(this, 9), Ui.dp(this, 9), Shader.TileMode.CLAMP) : null;
            home.setRenderEffect(progress < 1 ? blur : null);
            drawer.setRenderEffect(progress > 0 ? blur : null);
        }
    }

    // ------------------------------------------------------------------ gestures on empty space

    private void expandNotifications() {
        try {
            Object sb = getSystemService("statusbar");
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb);
        } catch (Exception ignored) { }
    }

    private void doubleTap() {
        if (!prefs.doubleTapLock()) return;
        if (LockService.lock()) return;
        Toast.makeText(this, "To lock with a double-tap, turn it on in Dot Home settings", Toast.LENGTH_SHORT).show();
    }

    private boolean overlayOpen() {
        return (menu != null && menu.isShowing()) || (stackPanel != null && stackPanel.isOpen()) || (sheet != null && sheet.isShowing());
    }

    /**
     * The root: swipes anywhere on home (up = drawer, down = notifications, right = Today),
     * on the drawer (down = close), on Today (left = close); pinch for edit mode; and drag & drop while editing.
     */
    private final class Root extends FrameLayout {
        private final int slop;
        private final GestureDetector taps;
        private final ScaleGestureDetector pinch;
        private VelocityTracker vt;
        private float downX, downY, originY, originX, originProgress;
        private boolean dragging, draggingToday, swiped, editDown, editDragging;
        private float pinchScale = 1;
        // Edit mode needs a deliberate hold on empty space: 650 ms with the finger kept still
        private final Runnable emptyHold = () -> {
            if (progress == 0 && todayP == 0 && !dragging && !draggingToday && !swiped && !edit && !overlayOpen()) setEdit(true, true);
        };
        private final Runnable editLongPress = () -> {
            if (editDown && !editDragging && beginAppDrag(downX, downY)) editDragging = true;
        };

        Root(Context c) {
            super(c);
            setBackgroundColor(Ui.BLACK);
            slop = ViewConfiguration.get(c).getScaledTouchSlop();
            taps = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
                @Override public boolean onDown(MotionEvent e) { return true; }
                @Override public boolean onDoubleTap(MotionEvent e) {
                    if (progress == 0 && todayP == 0 && !edit) doubleTap();
                    return true;
                }
            });
            taps.setIsLongpressEnabled(false); // our own, longer and stricter hold opens edit mode
            pinch = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override public boolean onScaleBegin(ScaleGestureDetector d) { pinchScale = 1; return true; }
                @Override public boolean onScale(ScaleGestureDetector d) {
                    pinchScale *= d.getScaleFactor();
                    if (pinchScale < 0.8f && !edit && progress == 0 && todayP == 0 && !overlayOpen()) setEdit(true, true);
                    return true;
                }
            });
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent e) {
            grid.touch(e);
            if (!edit && progress == 0 && todayP == 0 && !overlayOpen()) pinch.onTouchEvent(e);
            return super.dispatchTouchEvent(e);
        }

        private boolean inside(View v, MotionEvent e) {
            if (v.getVisibility() != VISIBLE) return false;
            int[] p = new int[2], r = new int[2];
            v.getLocationOnScreen(p);
            getLocationOnScreen(r);
            float x = e.getX() + r[0], y = e.getY() + r[1];
            return x >= p[0] && x <= p[0] + v.getWidth() * v.getScaleX() && y >= p[1] && y <= p[1] + v.getHeight() * v.getScaleY();
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent e) {
            if (overlayOpen()) return false;
            if (edit) {
                // While editing, the home area is ours (for drag & drop); the bars and widget buttons are not
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    return !(inside(editTop, e) || inside(editTray, e));
                }
                return false;
            }
            track(e);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getX();
                    downY = e.getY();
                    dragging = draggingToday = swiped = false;
                    // Catch the drawer or Today while they're still moving
                    if (drawerSpring.isRunning() && progress > 0.02f && progress < 0.98f) {
                        startDrawerDrag(e.getY());
                        return true;
                    }
                    if (todaySpring.isRunning() && todayP > 0.02f && todayP < 0.98f) {
                        startTodayDrag(e.getX());
                        return true;
                    }
                    return false;
                case MotionEvent.ACTION_MOVE:
                    if (e.getPointerCount() > 1) return false;
                    return decide(e);
                default:
                    return dragging || draggingToday || swiped;
            }
        }

        /** Turns a move into a drawer drag, a Today drag or a notification pull. */
        private boolean decide(MotionEvent e) {
            if (dragging || draggingToday || swiped) return true;
            float dx = e.getX() - downX, dy = e.getY() - downY;
            boolean vertical = Math.abs(dy) >= slop && Math.abs(dy) > Math.abs(dx) * 1.2f;
            boolean horizontal = Math.abs(dx) >= Ui.dp(getContext(), 30) && Math.abs(dx) > Math.abs(dy) * 1.6f;
            if (todayP >= 0.999f) {
                if (horizontal && dx < 0) { today.hideKeyboard(); startTodayDrag(e.getX()); return true; }
                return false;
            }
            if (progress <= 0.001f && todayP <= 0.001f) {
                if (horizontal && dx > 0) { startTodayDrag(e.getX()); return true; }
                if (vertical && dy < 0) { startDrawerDrag(e.getY()); return true; }
                // Notifications only on a deliberate pull, not a nudge
                if (vertical && dy > Ui.dp(getContext(), 72) && Math.abs(dy) > Math.abs(dx) * 2f && prefs.swipeDownNotifications()) {
                    swiped = true;
                    expandNotifications();
                    return true;
                }
                return false;
            }
            if (progress >= 0.999f && vertical && dy > 0 && drawer.atTop()) {
                drawer.hideKeyboard();
                startDrawerDrag(e.getY());
                return true;
            }
            return false;
        }

        private void startDrawerDrag(float y) {
            dragging = true;
            drawerSpring.cancel();
            if (calmAnim != null) calmAnim.cancel();
            originY = y;
            originProgress = progress;
            drawer.setVisibility(VISIBLE);
            getParent().requestDisallowInterceptTouchEvent(true);
        }

        private void startTodayDrag(float x) {
            draggingToday = true;
            todaySpring.cancel();
            if (todayP == 0) today.refresh();
            originX = x;
            originProgress = todayP;
            today.setVisibility(VISIBLE);
            getParent().requestDisallowInterceptTouchEvent(true);
        }

        private void track(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                if (vt != null) vt.recycle();
                vt = VelocityTracker.obtain();
            }
            if (vt != null) vt.addMovement(e);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (overlayOpen()) return false;
            if (edit) return editTouch(e);
            track(e);
            if (!dragging && !draggingToday && progress == 0 && todayP == 0) taps.onTouchEvent(e);
            if (e.getActionMasked() == MotionEvent.ACTION_MOVE && Math.hypot(e.getX() - downX, e.getY() - downY) > slop) removeCallbacks(emptyHold);
            if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL
                    || e.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) removeCallbacks(emptyHold);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getX();
                    downY = e.getY();
                    if (progress == 0 && todayP == 0) postDelayed(emptyHold, 650);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!dragging && !draggingToday && !swiped && e.getPointerCount() == 1) decide(e);
                    if (dragging) {
                        float p = originProgress - (e.getY() - originY) / Math.max(1, getHeight());
                        applyProgress(Ui.clamp(p, 0, 1));
                    } else if (draggingToday) {
                        float p = originProgress + (e.getX() - originX) / Math.max(1, getWidth());
                        applyToday(Ui.clamp(p, 0, 1));
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (dragging) {
                        vt.computeCurrentVelocity(1000);
                        float vy = vt.getYVelocity();
                        float vel = -vy / Math.max(1, getHeight());
                        boolean open;
                        if (Math.abs(vy) > Ui.dp(getContext(), 500)) open = vy < 0;
                        else open = originProgress < 0.5f ? progress > 0.3f : progress > 0.7f;
                        animateDrawer(open ? 1 : 0, vel);
                    } else if (draggingToday) {
                        vt.computeCurrentVelocity(1000);
                        float vx = vt.getXVelocity();
                        boolean open;
                        if (Math.abs(vx) > Ui.dp(getContext(), 500)) open = vx > 0;
                        else open = originProgress < 0.5f ? todayP > 0.3f : todayP > 0.7f;
                        animateToday(open ? 1 : 0, vx / Math.max(1, getWidth()));
                    }
                    dragging = draggingToday = swiped = false;
                    return true;
                default:
                    return true;
            }
        }

        /** Edit mode: hold or drag an app to move it; drop on another app to stack; drop at the top to remove. */
        private boolean editTouch(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getX();
                    downY = e.getY();
                    editDown = true;
                    editDragging = false;
                    postDelayed(editLongPress, 220);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!editDragging && Math.hypot(e.getX() - downX, e.getY() - downY) > slop) {
                        removeCallbacks(editLongPress);
                        editDragging = beginAppDrag(downX, downY);
                    }
                    if (editDragging) dragMove(e.getX(), e.getY());
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    removeCallbacks(editLongPress);
                    if (editDragging) dragEnd(e.getActionMasked() == MotionEvent.ACTION_UP);
                    else if (e.getActionMasked() == MotionEvent.ACTION_UP && Math.hypot(e.getX() - downX, e.getY() - downY) < slop) {
                        // A tap on empty space ends editing; a tap on an app does nothing
                        int[] z = new int[1], i = new int[1];
                        View cell = cellAt(e.getX(), e.getY(), z, i);
                        if (cell instanceof Widgets.WidgetCell) showWidgetSheet(((Widgets.WidgetCell) cell).key);
                        else if (cell == null || !((Cell) cell).hasContent()) setEdit(false, true);
                    }
                    editDown = false;
                    editDragging = false;
                    return true;
                default:
                    return true;
            }
        }
    }
}
