package uk.dnm.dothome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Everything Dot Home remembers. All on the phone, in its private storage. */
final class Prefs {

    private static Prefs instance;
    private final SharedPreferences p;

    static Prefs get(Context c) {
        if (instance == null) instance = new Prefs(c.getApplicationContext());
        return instance;
    }

    private Prefs(Context c) { p = c.getSharedPreferences("dothome", Context.MODE_PRIVATE); }

    SharedPreferences raw() { return p; }

    // ---- look & feel
    int motion() { return p.getInt("motion", Ui.MOTION_FULL); }
    void setMotion(int v) { p.edit().putInt("motion", v).apply(); }
    int haptics() { return p.getInt("haptics", Ui.HAPTIC_CRISP); }
    void setHaptics(int v) { p.edit().putInt("haptics", v).apply(); }
    boolean labels() { return p.getBoolean("labels", true); }
    void setLabels(boolean v) { p.edit().putBoolean("labels", v).apply(); }
    boolean clock() { return p.getBoolean("clock", true); }
    void setClock(boolean v) { p.edit().putBoolean("clock", v).apply(); }
    boolean keyboardOnOpen() { return p.getBoolean("kb_on_open", true); }
    void setKeyboardOnOpen(boolean v) { p.edit().putBoolean("kb_on_open", v).apply(); }
    boolean doubleTapLock() { return p.getBoolean("dt_lock", true); }
    void setDoubleTapLock(boolean v) { p.edit().putBoolean("dt_lock", v).apply(); }
    boolean swipeDownNotifications() { return p.getBoolean("swipe_down", true); }
    void setSwipeDownNotifications(boolean v) { p.edit().putBoolean("swipe_down", v).apply(); }
    boolean glance() { return p.getBoolean("glance", true); }
    void setGlance(boolean v) { p.edit().putBoolean("glance", v).apply(); }
    boolean memory() { return p.getBoolean("memory", true); }
    void setMemory(boolean v) { p.edit().putBoolean("memory", v).apply(); }

    // ---- layout: apps are stored as "package/activity#user"
    boolean hasLayout() { return p.contains("pinned"); }
    List<String> pinned() { return list("pinned"); }
    void setPinned(List<String> l) { putList("pinned", l); }
    List<String> dock() { return list("dock"); }
    void setDock(List<String> l) { putList("dock", l); }
    Set<String> hidden() { return new HashSet<>(list("hidden")); }
    /** Home items shown large (2 × 2): large icons and large folders. */
    Set<String> big() { return new HashSet<>(list("big")); }
    void setBig(Set<String> s) { putList("big", new ArrayList<>(s)); }
    void setHidden(Set<String> s) { putList("hidden", new ArrayList<>(s)); }

    /** Launch history for suggestions: key -> {"n": count, "t": last time, "h": [24 hour counts]} */
    JSONObject usage() {
        try { return new JSONObject(p.getString("usage", "{}")); } catch (Exception e) { return new JSONObject(); }
    }
    void setUsage(JSONObject o) { p.edit().putString("usage", o.toString()).apply(); }
    void clearUsage() { p.edit().remove("usage").apply(); }

    private List<String> list(String key) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p.getString(key, "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (Exception ignored) { }
        return out;
    }

    private void putList(String key, List<String> l) {
        JSONArray a = new JSONArray();
        for (String s : l) a.put(s);
        p.edit().putString(key, a.toString()).apply();
    }
}
