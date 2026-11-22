package uk.dnm.dothome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.AlarmClock;
import android.provider.MediaStore;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Every launchable app (all profiles), their icons, the home layout and launch history. */
final class Apps {

    static final class App {
        final String key;              // package/activity#userSerial
        final String label;
        final String lower;            // for search
        final ComponentName component;
        final UserHandle user;
        final boolean work;
        Bitmap icon;
        char section;

        App(String key, String label, ComponentName c, UserHandle u, boolean work) {
            this.key = key;
            this.label = label;
            this.lower = label.toLowerCase(Locale.ROOT);
            this.component = c;
            this.user = u;
            this.work = work;
            char ch = label.isEmpty() ? '#' : Character.toUpperCase(label.charAt(0));
            this.section = ch >= 'A' && ch <= 'Z' ? ch : '#';
        }

        String pkg() { return component.getPackageName(); }
    }

    interface Listener { void onAppsChanged(); }

    private static Apps instance;
    private final Context ctx;
    private final LauncherApps launcherApps;
    private final UserManager users;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<>();
    private List<App> all = new ArrayList<>();
    private Map<String, App> byKey = new HashMap<>();
    private boolean loading, dirty;
    private int iconPx;

    static Apps get(Context c) {
        if (instance == null) instance = new Apps(c.getApplicationContext());
        return instance;
    }

    private Apps(Context c) {
        ctx = c;
        launcherApps = c.getSystemService(LauncherApps.class);
        users = c.getSystemService(UserManager.class);
        iconPx = Ui.dpi(c, 48);
        launcherApps.registerCallback(new LauncherApps.Callback() {
            @Override public void onPackageRemoved(String p, UserHandle u) { reloadSoon(); }
            @Override public void onPackageAdded(String p, UserHandle u) { reloadSoon(); }
            @Override public void onPackageChanged(String p, UserHandle u) { reloadSoon(); }
            @Override public void onPackagesAvailable(String[] p, UserHandle u, boolean r) { reloadSoon(); }
            @Override public void onPackagesUnavailable(String[] p, UserHandle u, boolean r) { reloadSoon(); }
        }, main);
    }

    LauncherApps launcherApps() { return launcherApps; }

    void addListener(Listener l) { listeners.add(l); }
    void removeListener(Listener l) { listeners.remove(l); }

    boolean isLoaded() { return !all.isEmpty(); }

    /** All visible (not hidden) apps, A–Z. */
    List<App> visible() {
        Set<String> hidden = Prefs.get(ctx).hidden();
        List<App> out = new ArrayList<>();
        for (App a : all) if (!hidden.contains(a.key)) out.add(a);
        return out;
    }

    List<App> hiddenApps() {
        Set<String> hidden = Prefs.get(ctx).hidden();
        List<App> out = new ArrayList<>();
        for (App a : all) if (hidden.contains(a.key)) out.add(a);
        return out;
    }

    App find(String key) { return byKey.get(key); }

    List<App> resolve(List<String> keys) {
        List<App> out = new ArrayList<>();
        for (String k : keys) {
            App a = byKey.get(k);
            if (a != null) out.add(a);
        }
        return out;
    }

    private final Runnable reload = this::load;

    void reloadSoon() {
        main.removeCallbacks(reload);
        main.postDelayed(reload, 300);
    }

    void load() {
        if (loading) { dirty = true; return; }
        loading = true;
        new Thread(() -> {
            List<App> list = new ArrayList<>();
            UserHandle me = android.os.Process.myUserHandle();
            for (UserHandle u : users.getUserProfiles()) {
                List<LauncherActivityInfo> infos;
                try { infos = launcherApps.getActivityList(null, u); } catch (Exception e) { continue; }
                long serial = users.getSerialNumberForUser(u);
                for (LauncherActivityInfo info : infos) {
                    ComponentName cn = info.getComponentName();
                    if (cn.getPackageName().equals(ctx.getPackageName()) && cn.getClassName().endsWith("HomeActivity")) continue;
                    String label = String.valueOf(info.getLabel()).trim();
                    App a = new App(cn.flattenToShortString() + "#" + serial, label, cn, u, !u.equals(me));
                    try {
                        a.icon = IconFactory.make(ctx, info, iconPx);
                    } catch (Exception ignored) { }
                    list.add(a);
                }
            }
            Collator coll = Collator.getInstance();
            coll.setStrength(Collator.PRIMARY);
            Collections.sort(list, (x, y) -> coll.compare(x.label, y.label));
            Map<String, App> map = new HashMap<>();
            for (App a : list) map.put(a.key, a);
            main.post(() -> {
                all = list;
                byKey = map;
                loading = false;
                if (!Prefs.get(ctx).hasLayout()) makeDefaultLayout();
                for (Listener l : new ArrayList<>(listeners)) l.onAppsChanged();
                if (dirty) { dirty = false; load(); }
            });
        }, "apps-load").start();
    }

    /** Icons are redrawn at a new size when the icon-size setting changes. */
    void setIconSize(int px) {
        if (px == iconPx) return;
        iconPx = px;
        load();
    }

    // ------------------------------------------------------------------ first-run layout

    private void makeDefaultLayout() {
        List<String> dock = new ArrayList<>();
        addDefault(dock, new Intent(Intent.ACTION_DIAL));
        addDefault(dock, new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")));
        addDefault(dock, new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")));
        addDefault(dock, new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA));

        List<String> pinned = new ArrayList<>();
        addDefault(pinned, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC));
        addDefault(pinned, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MAPS));
        addDefault(pinned, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY));
        addDefault(pinned, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL));
        addDefault(pinned, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR));
        addDefault(pinned, new Intent(AlarmClock.ACTION_SHOW_ALARMS));
        addDefault(pinned, new Intent(Settings.ACTION_SETTINGS));
        pinned.removeAll(dock);

        Prefs.get(ctx).setDock(dock);
        Prefs.get(ctx).setPinned(pinned);
    }

    private void addDefault(List<String> into, Intent intent) {
        PackageManager pm = ctx.getPackageManager();
        String pkg = null;
        try {
            ResolveInfo ri = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
            if (ri != null && ri.activityInfo != null && !"android".equals(ri.activityInfo.packageName)) {
                pkg = ri.activityInfo.packageName;
            } else {
                List<ResolveInfo> l = pm.queryIntentActivities(intent, 0);
                if (!l.isEmpty()) pkg = l.get(0).activityInfo.packageName;
            }
        } catch (Exception ignored) { }
        if (pkg == null) return;
        UserHandle me = android.os.Process.myUserHandle();
        for (App a : all) {
            if (a.pkg().equals(pkg) && a.user.equals(me) && !into.contains(a.key)) {
                into.add(a.key);
                return;
            }
        }
    }

    // ------------------------------------------------------------------ launch history & suggestions

    void recordLaunch(App a) {
        if (!Prefs.get(ctx).memory()) return;
        try {
            JSONObject u = Prefs.get(ctx).usage();
            JSONObject e = u.optJSONObject(a.key);
            if (e == null) {
                e = new JSONObject();
                JSONArray h = new JSONArray();
                for (int i = 0; i < 24; i++) h.put(0);
                e.put("h", h);
            }
            e.put("n", e.optInt("n") + 1);
            e.put("t", System.currentTimeMillis());
            int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            JSONArray h = e.getJSONArray("h");
            h.put(hour, h.optInt(hour) + 1);
            u.put(a.key, e);
            Prefs.get(ctx).setUsage(u);
        } catch (Exception ignored) { }
    }

    /** Apps you're likely to want right now: opened around this hour before, or recently. */
    List<App> suggestions(int count) {
        JSONObject u = Prefs.get(ctx).usage();
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        long now = System.currentTimeMillis();
        List<App> vis = visible();
        List<Object[]> scored = new ArrayList<>();
        for (App a : vis) {
            JSONObject e = u.optJSONObject(a.key);
            if (e == null) continue;
            JSONArray h = e.optJSONArray("h");
            double s = 0;
            if (h != null) {
                s += 3 * h.optInt(hour) + 1.2 * (h.optInt((hour + 23) % 24) + h.optInt((hour + 1) % 24));
            }
            s += 0.25 * e.optInt("n");
            double ageH = (now - e.optLong("t")) / 3.6e6;
            s += 6 * Math.exp(-ageH / 6);
            scored.add(new Object[]{a, s});
        }
        scored.sort((x, y) -> Double.compare((double) y[1], (double) x[1]));
        List<App> out = new ArrayList<>();
        for (Object[] o : scored) {
            out.add((App) o[0]);
            if (out.size() == count) break;
        }
        // Not enough history yet: fill with pinned apps, then the first apps A–Z
        for (App a : resolve(Prefs.get(ctx).pinned())) {
            if (out.size() >= count) break;
            if (!out.contains(a)) out.add(a);
        }
        for (App a : vis) {
            if (out.size() >= count) break;
            if (!out.contains(a)) out.add(a);
        }
        return out;
    }

    double usageBoost(App a) {
        JSONObject e = Prefs.get(ctx).usage().optJSONObject(a.key);
        return e == null ? 0 : Math.log(1 + e.optInt("n"));
    }
}
