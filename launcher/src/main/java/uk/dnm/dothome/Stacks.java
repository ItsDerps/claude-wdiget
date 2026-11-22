package uk.dnm.dothome;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stacks: several apps behind one icon on home. A home slot holds either an app key or
 * "stack:&lt;id&gt;"; the stack's name and apps are stored here.
 */
final class Stacks {
    private Stacks() { }

    static final String PREFIX = "stack:";

    static final class Stack {
        final String id;
        String name;
        final List<String> apps = new ArrayList<>();
        Stack(String id, String name) { this.id = id; this.name = name; }
        String slot() { return PREFIX + id; }
    }

    static boolean isStack(String slot) { return slot != null && slot.startsWith(PREFIX); }

    static Map<String, Stack> load(Context c) {
        Map<String, Stack> out = new LinkedHashMap<>();
        try {
            JSONObject o = new JSONObject(Prefs.get(c).raw().getString("stacks", "{}"));
            JSONArray names = o.names();
            if (names == null) return out;
            for (int i = 0; i < names.length(); i++) {
                String id = names.getString(i);
                JSONObject s = o.getJSONObject(id);
                Stack st = new Stack(id, s.optString("name", "Stack"));
                JSONArray a = s.optJSONArray("apps");
                if (a != null) for (int j = 0; j < a.length(); j++) st.apps.add(a.getString(j));
                out.put(id, st);
            }
        } catch (Exception ignored) { }
        return out;
    }

    static void save(Context c, Map<String, Stack> stacks) {
        JSONObject o = new JSONObject();
        try {
            for (Stack s : stacks.values()) {
                JSONObject j = new JSONObject();
                j.put("name", s.name);
                JSONArray a = new JSONArray();
                for (String k : s.apps) a.put(k);
                j.put("apps", a);
                o.put(s.id, j);
            }
        } catch (Exception ignored) { }
        Prefs.get(c).raw().edit().putString("stacks", o.toString()).apply();
    }

    static Stack get(Context c, String slot) {
        if (!isStack(slot)) return null;
        return load(c).get(slot.substring(PREFIX.length()));
    }

    /** Make a new stack from two (or more) apps and return its home slot. */
    static String create(Context c, List<String> appKeys) {
        Map<String, Stack> all = load(c);
        String id = Long.toString(System.currentTimeMillis(), 36);
        Stack s = new Stack(id, suggestName(c, appKeys));
        s.apps.addAll(appKeys);
        all.put(id, s);
        save(c, all);
        return s.slot();
    }

    static void addApp(Context c, String slot, String appKey) {
        Map<String, Stack> all = load(c);
        Stack s = all.get(slot.substring(PREFIX.length()));
        if (s == null || s.apps.contains(appKey) || s.apps.size() >= 9) return;
        s.apps.add(appKey);
        save(c, all);
    }

    static void rename(Context c, String slot, String name) {
        Map<String, Stack> all = load(c);
        Stack s = all.get(slot.substring(PREFIX.length()));
        if (s == null) return;
        s.name = name.trim().isEmpty() ? s.name : name.trim();
        save(c, all);
    }

    /**
     * Take an app out of a stack. Returns what should now sit in the stack's home slot:
     * the stack itself, the one app left, or null if it's empty.
     */
    static String removeApp(Context c, String slot, String appKey) {
        Map<String, Stack> all = load(c);
        String id = slot.substring(PREFIX.length());
        Stack s = all.get(id);
        if (s == null) return null;
        s.apps.remove(appKey);
        if (s.apps.size() >= 2) {
            save(c, all);
            return slot;
        }
        all.remove(id);
        save(c, all);
        return s.apps.isEmpty() ? null : s.apps.get(0);
    }

    static void delete(Context c, String slot) {
        Map<String, Stack> all = load(c);
        all.remove(slot.substring(PREFIX.length()));
        save(c, all);
    }

    /** Name a stack from what's in it: Social, Games, Media, Photos, News, Travel, Work. */
    static String suggestName(Context c, List<String> appKeys) {
        PackageManager pm = c.getPackageManager();
        Map<String, Integer> votes = new HashMap<>();
        for (String k : appKeys) {
            String pkg = k.contains("/") ? k.substring(0, k.indexOf('/')) : k;
            try {
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                String n = categoryName(ai.category);
                if (n != null) votes.merge(n, 1, Integer::sum);
            } catch (Exception ignored) { }
        }
        String best = null;
        int bestN = 0;
        for (Map.Entry<String, Integer> e : votes.entrySet()) {
            if (e.getValue() > bestN) { best = e.getKey(); bestN = e.getValue(); }
        }
        return best != null && bestN * 2 >= appKeys.size() ? best : "Stack";
    }

    private static String categoryName(int cat) {
        switch (cat) {
            case ApplicationInfo.CATEGORY_SOCIAL: return "Social";
            case ApplicationInfo.CATEGORY_GAME: return "Games";
            case ApplicationInfo.CATEGORY_AUDIO:
            case ApplicationInfo.CATEGORY_VIDEO: return "Media";
            case ApplicationInfo.CATEGORY_IMAGE: return "Photos";
            case ApplicationInfo.CATEGORY_NEWS: return "News";
            case ApplicationInfo.CATEGORY_MAPS: return "Travel";
            case ApplicationInfo.CATEGORY_PRODUCTIVITY: return "Work";
            default: return null;
        }
    }
}
