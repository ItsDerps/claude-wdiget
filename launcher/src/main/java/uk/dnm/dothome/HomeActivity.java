package uk.dnm.dothome;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityOptions;
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
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.provider.Settings;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HomeActivity extends Activity
        implements HomeView.Host, DrawerView.Host, AppMenu.Actions, Apps.Listener {

    private Root root;
    private HomeView home;
    private DrawerView drawer;
    private Apps apps;
    private Prefs prefs;
    private AppMenu menu;

    private float progress;            // 0 = home, 1 = drawer open
    private Spring drawerSpring;
    private ValueAnimator calmAnim;
    private boolean assembleOnResume = true;
    private String builtFingerprint;
    private long eventId = -1;
    private final Set<String> badges = new HashSet<>();

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        prefs = Prefs.get(this);
        apps = Apps.get(this);

        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }

        root = new Root(this);
        home = new HomeView(this, this);
        drawer = new DrawerView(this, this);
        root.addView(home, new FrameLayout.LayoutParams(-1, -1));
        root.addView(drawer, new FrameLayout.LayoutParams(-1, -1));
        drawer.setVisibility(View.INVISIBLE);
        drawer.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        root.setFocusableInTouchMode(true);
        setContentView(root);
        root.requestFocus();

        drawerSpring = new Spring(400, 0.85f, 0f, new Spring.Listener() {
            @Override public void onUpdate(float v) { applyProgress(v); }
            @Override public void onSettled(float v) { settled(v); }
        });

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
            home.setInsets(top, bottom);
            drawer.setInsets(top, Math.max(bottom, ime));
            return insets;
        });
        root.post(() -> applyProgress(0));

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
    protected void onResume() {
        super.onResume();
        String fp = fingerprint();
        if (!fp.equals(builtFingerprint) && apps.isLoaded()) rebuild();
        else if (apps.isLoaded()) refreshSuggestions();
        home.updateDate();
        home.setShowTop(prefs.clock(), prefs.glance());
        refreshEvent();
        if (assembleOnResume) {
            assembleOnResume = false;
            home.clock.postDelayed(home.clock::assemble, 60);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Coming back home should always show home, not the drawer you left
        if (menu != null) menu.dismiss();
        if (progress > 0) setDrawer(0, false);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (menu != null && menu.isShowing()) { menu.dismiss(); return; }
        if (progress > 0) animateDrawer(0, 0);
    }

    @Override
    public void onBackPressed() {
        if (menu != null && menu.isShowing()) { menu.dismiss(); return; }
        if (progress > 0) animateDrawer(0, 0);
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            String a = i.getAction();
            if (Intent.ACTION_USER_PRESENT.equals(a)) {
                assembleOnResume = true;
                if (hasWindowFocus()) { assembleOnResume = false; home.clock.assemble(); }
            } else if (Intent.ACTION_BATTERY_CHANGED.equals(a)) {
                int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
                if (level >= 0) home.setBattery(Math.round(level * 100f / scale), charging);
            } else {
                home.updateDate();
                if (Intent.ACTION_TIME_TICK.equals(a) && Calendar.getInstance().get(Calendar.MINUTE) % 10 == 0) refreshEvent();
            }
        }
    };

    // ------------------------------------------------------------------ building

    private String fingerprint() {
        return prefs.labels() + "|" + prefs.pinned() + "|" + prefs.dock() + "|" + prefs.hidden() + "|" + appsVersion;
    }

    private int appsVersion;

    @Override
    public void onAppsChanged() {
        appsVersion++;
        rebuild();
    }

    private void rebuild() {
        boolean labels = prefs.labels();
        home.bind(apps.resolve(prefs.pinned()), apps.resolve(prefs.dock()), labels, badges, this);
        drawer.build(apps.visible(), apps.suggestions(4), labels);
        drawer.setReveal(progress);
        builtFingerprint = fingerprint();
    }

    private void refreshSuggestions() {
        drawer.build(apps.visible(), apps.suggestions(4), prefs.labels());
        drawer.setReveal(progress);
    }

    @Override
    public Set<String> badges() { return badges; }

    // ------------------------------------------------------------------ drawer motion

    private void applyProgress(float p) {
        progress = p;
        int h = root.getHeight();
        drawer.setTranslationY((1 - Ui.clamp(p, -0.1f, 1.1f)) * h);
        drawer.setVisibility(p > 0.001f ? View.VISIBLE : View.INVISIBLE);
        float s = 1 - 0.06f * Ui.clamp(p, 0, 1);
        home.setScaleX(s);
        home.setScaleY(s);
        home.setAlpha(1 - 0.7f * Ui.clamp(p, 0, 1));
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
        if (notify) settled(target); else if (target == 0) drawer.closed();
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

    // ------------------------------------------------------------------ launching

    @Override
    public void onAppClick(AppIconView v) {
        launchApp(v.app, v);
    }

    @Override
    public void onAppLongClick(AppIconView v) {
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

    @Override public void onClockTap() { launchIntent(new Intent(AlarmClock.ACTION_SHOW_ALARMS), home.clock); }

    @Override
    public void onDateTap() {
        Uri.Builder b = CalendarContract.CONTENT_URI.buildUpon().appendPath("time");
        ContentUris.appendId(b, System.currentTimeMillis());
        launchIntent(new Intent(Intent.ACTION_VIEW).setData(b.build()), null);
    }

    @Override public void onBatteryTap() { launchIntent(new Intent(Intent.ACTION_POWER_USAGE_SUMMARY), null); }

    @Override
    public void onEventTap() {
        if (eventId < 0) { onDateTap(); return; }
        launchIntent(new Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)), null);
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
                    boolean today = cb.get(Calendar.DAY_OF_YEAR) == Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
                    String time = android.text.format.DateFormat.getTimeFormat(this).format(new Date(begin));
                    text = (title == null || title.isEmpty() ? "Event" : title) + " " + (today ? time : "tmrw " + time);
                    if (begin <= now) text = (title == null ? "Event" : title) + " · now";
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
    @Override public boolean dockFull() { return apps.resolve(prefs.dock()).size() >= 4; }

    @Override
    public void toggleHome(Apps.App a) {
        List<String> l = new ArrayList<>(prefs.pinned());
        if (l.remove(a.key)) {
            prefs.setPinned(l);
        } else {
            if (l.size() >= 12) { Toast.makeText(this, "Home is full (12 apps). Unpin one first.", Toast.LENGTH_SHORT).show(); return; }
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

    /** Pure black with a barely-there dot grid, like the design. Black pixels stay off on OLED. */
    private static android.graphics.drawable.Drawable dotGrid(Context c) {
        int step = Ui.dpi(c, 13);
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(step, step, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas cv = new android.graphics.Canvas(b);
        cv.drawColor(Ui.BLACK);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFF121212);
        cv.drawCircle(step / 2f, step / 2f, Ui.dp(c, 1.1f), p);
        android.graphics.drawable.BitmapDrawable d = new android.graphics.drawable.BitmapDrawable(c.getResources(), b);
        d.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        return d;
    }

    /** The root: tracks swipes anywhere on home (up = drawer, down = notifications) and on the drawer (down = close). */
    private final class Root extends FrameLayout {
        private final int slop;
        private final GestureDetector taps;
        private VelocityTracker vt;
        private float downX, downY, originY, originProgress;
        private boolean dragging, swiped;

        Root(Context c) {
            super(c);
            setBackground(dotGrid(c));
            slop = ViewConfiguration.get(c).getScaledTouchSlop();
            taps = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
                @Override public boolean onDown(MotionEvent e) { return true; }
                @Override public boolean onDoubleTap(MotionEvent e) {
                    if (progress == 0) doubleTap();
                    return true;
                }
                @Override public void onLongPress(MotionEvent e) {
                    if (progress == 0 && !dragging) {
                        Ui.firm(Root.this);
                        startActivity(new Intent(HomeActivity.this, SettingsActivity.class));
                    }
                }
            });
        }

        private boolean menuOpen() { return menu != null && menu.isShowing(); }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent e) {
            if (menuOpen()) return false;
            track(e);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getX();
                    downY = e.getY();
                    dragging = false;
                    swiped = false;
                    // Catch the drawer while it's still moving
                    if (drawerSpring.isRunning() && progress > 0.02f && progress < 0.98f) {
                        startDrag(e.getY());
                        return true;
                    }
                    return false;
                case MotionEvent.ACTION_MOVE:
                    return decide(e);
                default:
                    return dragging || swiped;
            }
        }

        /** Turns a vertical move into a drawer drag or a notification pull. */
        private boolean decide(MotionEvent e) {
            if (dragging || swiped) return true;
            float dx = e.getX() - downX, dy = e.getY() - downY;
            if (Math.abs(dy) < slop || Math.abs(dy) < Math.abs(dx) * 1.2f) return false;
            if (progress <= 0.001f && dy < 0) { startDrag(e.getY()); return true; }
            if (progress <= 0.001f && dy > 0 && prefs.swipeDownNotifications()) {
                swiped = true;
                expandNotifications();
                return true;
            }
            if (progress >= 0.999f && dy > 0 && drawer.atTop()) {
                drawer.hideKeyboard();
                startDrag(e.getY());
                return true;
            }
            return false;
        }

        private void startDrag(float y) {
            dragging = true;
            drawerSpring.cancel();
            if (calmAnim != null) calmAnim.cancel();
            originY = y;
            originProgress = progress;
            drawer.setVisibility(VISIBLE);
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
            if (menuOpen()) return false;
            track(e);
            if (!dragging && progress == 0) taps.onTouchEvent(e);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getX();
                    downY = e.getY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!dragging && !swiped) decide(e);
                    if (dragging) {
                        float p = originProgress - (e.getY() - originY) / Math.max(1, getHeight());
                        applyProgress(Ui.clamp(p, 0, 1));
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
                    }
                    dragging = false;
                    swiped = false;
                    return true;
                default:
                    return true;
            }
        }
    }
}
