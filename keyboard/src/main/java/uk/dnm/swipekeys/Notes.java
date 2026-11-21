package uk.dnm.swipekeys;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/** Saved notes, kept on the device. Newest first. */
final class Notes {
    private Notes() { }

    private static final String KEY = "notes";

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("swipekeys", Context.MODE_PRIVATE);
    }

    static List<String> load(Context c) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString(KEY, "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (Exception ignored) { }
        return out;
    }

    static void save(Context c, List<String> notes) {
        JSONArray a = new JSONArray();
        for (String n : notes) a.put(n);
        prefs(c).edit().putString(KEY, a.toString()).apply();
    }

    static void add(Context c, String note) {
        if (note == null || note.trim().isEmpty()) return;
        List<String> notes = load(c);
        notes.remove(note);
        notes.add(0, note);
        save(c, notes);
    }

    static void remove(Context c, String note) {
        List<String> notes = load(c);
        notes.remove(note);
        save(c, notes);
    }
}
