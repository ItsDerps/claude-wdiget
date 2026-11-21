package uk.dnm.swipekeys;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Word suggestions from a bundled frequency list (assets/words.txt), plus words and
 * word pairs learned from what the user types (saved on the device, never sent anywhere).
 */
final class Predictor {

    private static final String USER_FILE = "learned.txt";
    private static final int MAX_NEXT_PER_WORD = 24;

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());

    // Bundled dictionary (filled in on a background thread, then swapped in on the main thread)
    String[] words = new String[0];   // display form, e.g. "don't", "I'm"
    String[] keys = new String[0];    // lowercase, apostrophes removed: "dont", "im"
    int[] freq = new int[0];          // Zipf frequency x100 (~250..770)
    private Integer[] byKeyOrder = new Integer[0];
    HashMap<String, Integer> exact = new HashMap<>();
    private HashMap<String, String> byLower = new HashMap<>(); // "london" -> "London", "i'm" -> "I'm"

    // Learned from the user
    HashMap<String, Integer> userWords = new HashMap<>();
    HashMap<String, HashMap<String, Integer>> next = new HashMap<>();
    private boolean dirty;

    Predictor(Context context) {
        this.context = context.getApplicationContext();
        new Thread(this::load, "predictor-load").start();
    }

    // ------------------------------------------------------------------ loading / saving

    private void load() {
        List<String> w = new ArrayList<>(52000);
        List<Integer> f = new ArrayList<>(52000);
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                context.getAssets().open("words.txt"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                int tab = line.indexOf('\t');
                if (tab <= 0) continue;
                w.add(line.substring(0, tab));
                f.add(Integer.parseInt(line.substring(tab + 1).trim()));
            }
        } catch (Exception ignored) { }

        int n = w.size();
        String[] ws = w.toArray(new String[0]);
        String[] ks = new String[n];
        int[] fs = new int[n];
        HashMap<String, Integer> ex = new HashMap<>(n * 2);
        HashMap<String, String> bl = new HashMap<>(n * 2);
        for (int i = 0; i < n; i++) {
            ks[i] = key(ws[i]);
            fs[i] = f.get(i);
            ex.putIfAbsent(ks[i], i); // list is sorted by frequency, so the first is the most common
            bl.putIfAbsent(ws[i].toLowerCase(), ws[i]);
        }
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        Arrays.sort(order, Comparator.comparing(i -> ks[i]));

        HashMap<String, Integer> uw = new HashMap<>();
        HashMap<String, HashMap<String, Integer>> nx = new HashMap<>();
        File file = new File(context.getFilesDir(), USER_FILE);
        if (file.exists()) {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(
                    new FileInputStream(file), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    String[] p = line.split("\t");
                    try {
                        if (p.length == 3 && p[0].equals("w")) {
                            uw.put(p[1], Integer.parseInt(p[2]));
                        } else if (p.length == 4 && p[0].equals("n")) {
                            nx.computeIfAbsent(p[1], k -> new HashMap<>()).put(p[2], Integer.parseInt(p[3]));
                        }
                    } catch (NumberFormatException ignored) { }
                }
            } catch (Exception ignored) { }
        }

        main.post(() -> {
            words = ws; keys = ks; freq = fs; exact = ex; byKeyOrder = order; byLower = bl;
            // Keep anything learned while we were loading
            for (Map.Entry<String, Integer> e : userWords.entrySet()) uw.merge(e.getKey(), e.getValue(), Integer::sum);
            for (Map.Entry<String, HashMap<String, Integer>> e : next.entrySet()) {
                HashMap<String, Integer> m = nx.computeIfAbsent(e.getKey(), k -> new HashMap<>());
                for (Map.Entry<String, Integer> e2 : e.getValue().entrySet()) m.merge(e2.getKey(), e2.getValue(), Integer::sum);
            }
            userWords = uw; next = nx;
        });
    }

    void saveIfDirty() {
        if (!dirty) return;
        dirty = false;
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : userWords.entrySet()) {
            sb.append("w\t").append(e.getKey()).append('\t').append(e.getValue()).append('\n');
        }
        for (Map.Entry<String, HashMap<String, Integer>> e : next.entrySet()) {
            for (Map.Entry<String, Integer> e2 : e.getValue().entrySet()) {
                sb.append("n\t").append(e.getKey()).append('\t').append(e2.getKey())
                        .append('\t').append(e2.getValue()).append('\n');
            }
        }
        String data = sb.toString();
        File dir = context.getFilesDir();
        new Thread(() -> {
            File tmp = new File(dir, USER_FILE + ".tmp");
            try (Writer out = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
                out.write(data);
            } catch (Exception e) {
                return;
            }
            //noinspection ResultOfMethodCallIgnored
            tmp.renameTo(new File(dir, USER_FILE));
        }, "predictor-save").start();
    }

    // ------------------------------------------------------------------ learning

    /** Remember a finished word, and that it followed {@code prev} (may be null). */
    void learn(String prev, String word) {
        String w = storeForm(word);
        if (w == null) return;
        userWords.merge(w, 1, Integer::sum);
        if (prev != null) {
            String p = prev.toLowerCase();
            HashMap<String, Integer> m = next.computeIfAbsent(p, k -> new HashMap<>());
            m.merge(w, 1, Integer::sum);
            if (m.size() > MAX_NEXT_PER_WORD) {
                String weakest = null;
                int min = Integer.MAX_VALUE;
                for (Map.Entry<String, Integer> e : m.entrySet()) {
                    if (!e.getKey().equals(w) && e.getValue() < min) { min = e.getValue(); weakest = e.getKey(); }
                }
                if (weakest != null) m.remove(weakest);
            }
        }
        dirty = true;
    }

    /** The user insisted on this word (kept it instead of the correction): never correct it again. */
    void markKnown(String prev, String word) {
        learn(prev, word);
        learn(null, word);
    }

    /** Forget everything learned from the user, on disk too. */
    void clearLearned() {
        userWords = new HashMap<>();
        next = new HashMap<>();
        dirty = false;
        File dir = context.getFilesDir();
        new Thread(() -> {
            //noinspection ResultOfMethodCallIgnored
            new File(dir, USER_FILE).delete();
        }, "predictor-clear").start();
    }

    // ------------------------------------------------------------------ autocorrect

    /**
     * What a just-finished word should become, or null to leave it alone.
     * Only corrects words that aren't real words (or ones the user has taught it), and only
     * when one fix is clearly the most likely.
     */
    String autocorrect(String prev, String typed) {
        if (typed.isEmpty() || words.length == 0 || typed.length() > 24) return null;
        String norm = typed.replace('’', '\'');
        String lower = norm.toLowerCase();
        if (typed.length() > 1 && typed.equals(typed.toUpperCase())) return null; // ABBREVIATIONS
        if (known(norm)) {
            // Real word: only fix "i" -> "I" (and "i'm" -> "I'm" etc.)
            if (lower.equals("i") || lower.startsWith("i'")) {
                String d = byLower.get(lower);
                if (d != null && !d.equals(norm) && d.charAt(0) == 'I') return d;
            }
            return null;
        }
        String k = key(norm);
        if (k.isEmpty()) return null;

        // Same letters, missing apostrophe: "dont" -> "don't", "im" -> "I'm"
        Integer same = exact.get(k);
        if (same != null) return matchCase(words[same], typed);

        if (k.length() < 3) return null; // too short to guess safely
        HashMap<String, Integer> nextCounts = prev == null ? null : next.get(prev.toLowerCase());
        String best = null, second = null;
        double bestScore = -1e9, secondScore = -1e9;
        for (Map.Entry<String, Integer> e : edits(k).entrySet()) {
            Integer i = exact.get(e.getKey());
            if (i == null) continue;
            String w = words[i];
            if (isAbbrev(w)) continue; // don't turn words into abbreviations
            double score = freq[i] - e.getValue();
            Integer u = userWords.get(w);
            if (u != null) score += 70 * Math.log(1 + u);
            if (nextCounts != null) {
                Integer c = nextCounts.get(w);
                if (c != null) score += 90 * Math.log(1 + c);
            }
            if (score > bestScore) {
                second = best; secondScore = bestScore;
                best = w; bestScore = score;
            } else if (score > secondScore) {
                second = w; secondScore = score;
            }
        }
        // Two words run together: "alot" -> "a lot", "inthe" -> "in the"
        for (int cut = 1; cut < k.length(); cut++) {
            Integer a = exact.get(k.substring(0, cut)), b = exact.get(k.substring(cut));
            if (a == null || b == null) continue;
            if (cut == 1 && !words[a].equals("a") && !words[a].equals("I")) continue;
            if (!words[a].equals("I") && (isAbbrev(words[a]) || isAbbrev(words[b]))) continue;
            double score = Math.min(freq[a], freq[b]) - 100;
            String w = words[a] + " " + words[b];
            if (score > bestScore) {
                second = best; secondScore = bestScore;
                best = w; bestScore = score;
            } else if (score > secondScore) {
                second = w; secondScore = score;
            }
        }
        if (best == null || bestScore < fixScore) return null;
        if (second != null && bestScore - secondScore < fixMargin) return null; // too close to call
        return matchCase(best, typed);
    }

    private static boolean isAbbrev(String w) {
        return w.length() > 1 && w.equals(w.toUpperCase());
    }

    // How sure autocorrect must be. Raised when the user keeps undoing corrections.
    int fixScore = 300;
    int fixMargin = 40;

    /** Make autocorrect more careful the more often its fixes get undone. */
    void tuneAutocorrect(int fixesKept, int fixesUndone) {
        double undoRate = fixesUndone / (double) (fixesKept + fixesUndone + 8);
        fixScore = 300 + (int) (200 * undoRate);
        fixMargin = 40 + (int) (160 * undoRate);
    }

    /** A dictionary word, or one the user has typed at least twice. */
    boolean known(String word) {
        String lower = word.replace('’', '\'').toLowerCase();
        if (byLower.containsKey(lower)) return true;
        Integer u = userWords.get(word);
        if (u == null) u = userWords.get(lower);
        return u != null && u >= 2;
    }

    /** "Hello" at the start of a sentence is stored as "hello"; "iPhone" or "London" keep their case. */
    private String storeForm(String word) {
        if (word == null) return null;
        String w = word.replace('’', '\'');
        if (w.isEmpty() || w.length() > 32) return null;
        Integer i = exact.get(key(w));
        if (i != null && words[i].equalsIgnoreCase(w)) return words[i];
        if (w.length() > 1 && Character.isUpperCase(w.charAt(0)) && w.substring(1).equals(w.substring(1).toLowerCase())) {
            // Could be a name, or just a capitalised first word. Keep the case only if seen that way before.
            String lower = w.toLowerCase();
            if (userWords.containsKey(lower)) return lower;
        }
        return w;
    }

    // ------------------------------------------------------------------ suggesting

    /** Up to three suggestions, best first. {@code typed} is the partial word before the cursor (may be empty). */
    List<String> suggest(String prev, String typed) {
        HashMap<String, Integer> nextCounts = prev == null ? null : next.get(prev.toLowerCase());
        LinkedHashMap<String, Double> scores = new LinkedHashMap<>();

        if (typed.isEmpty()) {
            // Next-word prediction, from pairs the user has typed before
            if (nextCounts != null) {
                for (Map.Entry<String, Integer> e : nextCounts.entrySet()) {
                    if (e.getValue() >= 2) scores.put(e.getKey(), (double) e.getValue());
                }
            }
            return top(scores, typed);
        }

        String k = key(typed);
        if (k.isEmpty()) return new ArrayList<>();

        // 1. Dictionary words that start with what's typed (keep only the best few: short prefixes match thousands)
        final int keep = 12;
        int[] topI = new int[keep];
        double[] topS = new double[keep];
        int topN = 0;
        int lo = lowerBound(k);
        for (int j = lo; j < byKeyOrder.length; j++) {
            int i = byKeyOrder[j];
            if (!keys[i].startsWith(k)) break;
            double s = freq[i];
            if (keys[i].length() == k.length()) s += 120;          // exactly what was typed
            else s -= 8 * Math.min(6, keys[i].length() - k.length()); // slight preference for shorter
            if (nextCounts != null) {
                Integer c = nextCounts.get(words[i]);
                if (c != null) s += 90 * Math.log(1 + c);
            }
            if (topN == keep && s <= topS[keep - 1]) continue;
            int pos = topN < keep ? topN++ : keep - 1;
            while (pos > 0 && topS[pos - 1] < s) {
                topS[pos] = topS[pos - 1];
                topI[pos] = topI[pos - 1];
                pos--;
            }
            topS[pos] = s;
            topI[pos] = i;
        }
        for (int t = 0; t < topN; t++) add(scores, words[topI[t]], topS[t]);

        // 2. Words the user has taught it
        for (Map.Entry<String, Integer> e : userWords.entrySet()) {
            String w = e.getKey();
            String wk = key(w);
            if (!wk.startsWith(k)) continue;
            boolean known = exact.containsKey(wk);
            if (!known && e.getValue() < 2) continue; // a one-off could be a typo
            double base = known ? 0 : 480 + (wk.length() == k.length() ? 120 : 0);
            Double cur = scores.get(w);
            add(scores, w, (cur != null ? cur : base) + 70 * Math.log(1 + e.getValue()));
        }

        // 3. Likely typos: one letter wrong, missing, extra, or swapped
        if (k.length() >= 2) {
            int shortWord = k.length() <= 3 ? 60 : 0;
            for (Map.Entry<String, Integer> e : edits(k).entrySet()) {
                Integer i = exact.get(e.getKey());
                if (i != null) add(scores, words[i], freq[i] - e.getValue() - shortWord);
            }
        }

        // 4. Boost words that usually follow the previous one (dictionary prefix matches were boosted above)
        if (nextCounts != null) {
            for (Map.Entry<String, Double> e : scores.entrySet()) {
                if (!key(e.getKey()).startsWith(k) || !exact.containsKey(key(e.getKey()))) {
                    Integer c = nextCounts.get(e.getKey());
                    if (c != null) e.setValue(e.getValue() + 90 * Math.log(1 + c));
                }
            }
        }
        return top(scores, typed);
    }

    private static void add(Map<String, Double> scores, String w, double s) {
        Double cur = scores.get(w);
        if (cur == null || s > cur) scores.put(w, s);
    }

    private static List<String> top(Map<String, Double> scores, String typed) {
        List<Map.Entry<String, Double>> list = new ArrayList<>(scores.entrySet());
        list.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        List<String> out = new ArrayList<>(3);
        HashSet<String> seen = new HashSet<>();
        for (Map.Entry<String, Double> e : list) {
            String w = matchCase(e.getKey(), typed);
            if (seen.add(w)) out.add(w);
            if (out.size() == 3) break;
        }
        return out;
    }

    /** Follow the user's capitalisation: "Hel" → "Hello", "HEL" → "HELLO". */
    private static String matchCase(String w, String typed) {
        if (typed.isEmpty() || !Character.isUpperCase(typed.charAt(0))) return w;
        boolean allCaps = typed.length() > 1 && typed.equals(typed.toUpperCase());
        if (allCaps) return w.toUpperCase();
        return Character.toUpperCase(w.charAt(0)) + w.substring(1);
    }

    private int lowerBound(String k) {
        int lo = 0, hi = byKeyOrder.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (keys[byKeyOrder[mid]].compareTo(k) < 0) lo = mid + 1; else hi = mid;
        }
        return lo;
    }

    /** Words one edit away, mapped to a penalty: swaps and wrong letters are likelier typos than missing ones. */
    private static HashMap<String, Integer> edits(String w) {
        HashMap<String, Integer> out = new HashMap<>();
        int n = w.length();
        for (int i = 0; i < n; i++) out.merge(w.substring(0, i) + w.substring(i + 1), 180, Math::min);     // extra letter typed
        for (int i = 0; i < n - 1; i++) {
            out.merge(w.substring(0, i) + w.charAt(i + 1) + w.charAt(i) + w.substring(i + 2), 90, Math::min); // swapped
        }
        for (int i = 0; i <= n; i++) {
            for (char c = 'a'; c <= 'z'; c++) {
                if (i < n && c != w.charAt(i)) out.merge(w.substring(0, i) + c + w.substring(i + 1), 120, Math::min); // wrong letter
                out.merge(w.substring(0, i) + c + w.substring(i), 130, Math::min);                                   // letter missed
            }
        }
        out.remove(w);
        return out;
    }

    static String key(String w) {
        StringBuilder sb = new StringBuilder(w.length());
        for (int i = 0; i < w.length(); i++) {
            char c = w.charAt(i);
            if (c != '\'' && c != '’') sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }
}
