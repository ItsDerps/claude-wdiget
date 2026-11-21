package uk.dnm.swipekeys;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Swipe-to-write: turns a finger trail across the letters into likely words.
 * Candidates must start near where the finger went down, end near where it lifted, and pass
 * near every letter in order; they are then ranked by how closely the word's ideal path
 * matches the trail, how common the word is, and what the user usually types next.
 */
final class GlideDecoder {

    private static final int N = 48;

    private final Predictor pred;
    private String[] builtFor;
    @SuppressWarnings("unchecked")
    private final ArrayList<Integer>[] byFirst = new ArrayList[26];

    GlideDecoder(Predictor pred) { this.pred = pred; }

    private void index() {
        if (builtFor == pred.words) return;
        for (int i = 0; i < 26; i++) byFirst[i] = new ArrayList<>();
        for (int i = 0; i < pred.keys.length; i++) {
            String k = pred.keys[i];
            if (k.length() < 2 || !letters(k)) continue;
            byFirst[k.charAt(0) - 'a'].add(i);
        }
        builtFor = pred.words;
    }

    private static boolean letters(String k) {
        for (int i = 0; i < k.length(); i++) {
            char c = k.charAt(i);
            if (c < 'a' || c > 'z') return false;
        }
        return true;
    }

    /**
     * @param px,py   the trail, in view pixels ({@code n} points)
     * @param kx,ky   centre of each letter key a..z, in view pixels
     * @param keyW,keyH size of one key cell, used to normalise distances
     */
    List<String> decode(float[] px, float[] py, int n, float[] kx, float[] ky, float keyW, float keyH,
                        String prev, int max) {
        index();
        List<String> out = new ArrayList<>();
        if (n < 2 || keyW <= 0 || keyH <= 0) return out;

        // Work in "key units" so a step between neighbouring keys is about 1 in both directions
        float[] ux = new float[n], uy = new float[n];
        for (int i = 0; i < n; i++) { ux[i] = px[i] / keyW; uy[i] = py[i] / keyH; }
        float[] lx = new float[26], ly = new float[26];
        for (int i = 0; i < 26; i++) { lx[i] = kx[i] / keyW; ly[i] = ky[i] / keyH; }

        float[][] user = resample(ux, uy, n, N);
        float[][] fine = resample(ux, uy, n, 96);
        float userLen = length(ux, uy, n);

        int[] starts = nearest(lx, ly, ux[0], uy[0], 3, 1.1f);
        int[] ends = nearest(lx, ly, ux[n - 1], uy[n - 1], 3, 1.1f);
        boolean[] endOk = new boolean[26];
        for (int e : ends) endOk[e] = true;

        HashMap<String, Integer> nextCounts = prev == null ? null : pred.next.get(prev.toLowerCase());
        HashMap<String, Double> scores = new HashMap<>();

        for (int s : starts) {
            for (int idx : byFirst[s]) {
                String k = pred.keys[idx];
                if (!endOk[k.charAt(k.length() - 1) - 'a']) continue;
                Double d = match(k, lx, ly, user, fine, userLen);
                if (d == null) continue;
                double score = pred.freq[idx] / 100.0 - 6.0 * d;
                add(scores, pred.words[idx], score);
            }
        }
        // Words the user has taught it that aren't in the dictionary
        for (Map.Entry<String, Integer> e : pred.userWords.entrySet()) {
            String k = Predictor.key(e.getKey());
            if (k.length() < 2 || !letters(k) || pred.exact.containsKey(k) || e.getValue() < 2) continue;
            boolean startOk = false;
            for (int s : starts) if (k.charAt(0) - 'a' == s) startOk = true;
            if (!startOk || !endOk[k.charAt(k.length() - 1) - 'a']) continue;
            Double d = match(k, lx, ly, user, fine, userLen);
            if (d != null) add(scores, e.getKey(), 4.6 - 6.0 * d);
        }
        // Personal boosts
        for (Map.Entry<String, Double> e : scores.entrySet()) {
            Integer u = pred.userWords.get(e.getKey());
            if (u != null) e.setValue(e.getValue() + 0.35 * Math.log(1 + u));
            if (nextCounts != null) {
                Integer c = nextCounts.get(e.getKey());
                if (c != null) e.setValue(e.getValue() + 0.9 * Math.log(1 + c));
            }
        }

        List<Map.Entry<String, Double>> list = new ArrayList<>(scores.entrySet());
        list.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        HashSet<String> seen = new HashSet<>();
        for (Map.Entry<String, Double> e : list) {
            if (seen.add(e.getKey().toLowerCase())) out.add(e.getKey());
            if (out.size() >= max) break;
        }
        return out;
    }

    /** Average distance (in key units) between the word's ideal path and the trail, or null if it can't be this word. */
    private static Double match(String k, float[] lx, float[] ly, float[][] user, float[][] fine, float userLen) {
        // Ideal path: key centres, with double letters ("ll") collapsed
        float[] ix = new float[k.length()], iy = new float[k.length()];
        int m = 0;
        char last = 0;
        for (int i = 0; i < k.length(); i++) {
            char c = k.charAt(i);
            if (c == last) continue;
            last = c;
            ix[m] = lx[c - 'a'];
            iy[m] = ly[c - 'a'];
            m++;
        }
        if (m < 2) return null;
        float idealLen = length(ix, iy, m);
        if (idealLen < userLen * 0.45f - 0.6f || idealLen > userLen * 2.0f + 0.8f) return null;

        // Every middle letter must be passed close by, in order
        int j = 0;
        for (int i = 1; i < m - 1; i++) {
            while (j < fine[0].length && dist(fine[0][j], fine[1][j], ix[i], iy[i]) > 1.0f) j++;
            if (j >= fine[0].length) return null;
        }

        float[][] ideal = resample(ix, iy, m, N);
        double sum = 0;
        for (int i = 0; i < N; i++) sum += dist(ideal[0][i], ideal[1][i], user[0][i], user[1][i]);
        return sum / N;
    }

    private static void add(Map<String, Double> scores, String w, double s) {
        Double cur = scores.get(w);
        if (cur == null || s > cur) scores.put(w, s);
    }

    private static int[] nearest(float[] lx, float[] ly, float x, float y, int max, float within) {
        Integer[] order = new Integer[26];
        for (int i = 0; i < 26; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> Float.compare(dist(lx[a], ly[a], x, y), dist(lx[b], ly[b], x, y)));
        int count = 1;
        while (count < max && dist(lx[order[count]], ly[order[count]], x, y) <= within) count++;
        int[] out = new int[count];
        for (int i = 0; i < count; i++) out[i] = order[i];
        return out;
    }

    private static float dist(float ax, float ay, float bx, float by) {
        float dx = ax - bx, dy = ay - by;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static float length(float[] x, float[] y, int n) {
        float len = 0;
        for (int i = 1; i < n; i++) len += dist(x[i - 1], y[i - 1], x[i], y[i]);
        return len;
    }

    /** {@code count} points evenly spaced along the polyline. */
    static float[][] resample(float[] x, float[] y, int n, int count) {
        float[][] out = new float[2][count];
        float total = length(x, y, n);
        if (total <= 0) {
            for (int i = 0; i < count; i++) { out[0][i] = x[0]; out[1][i] = y[0]; }
            return out;
        }
        float step = total / (count - 1);
        int seg = 1;
        float segStart = 0; // distance along the path where segment seg begins
        float segLen = dist(x[0], y[0], x[1], y[1]);
        for (int i = 0; i < count; i++) {
            float target = Math.min(total, i * step);
            while (seg < n - 1 && segStart + segLen < target) {
                segStart += segLen;
                seg++;
                segLen = dist(x[seg - 1], y[seg - 1], x[seg], y[seg]);
            }
            float t = segLen > 0 ? (target - segStart) / segLen : 0;
            t = Math.max(0, Math.min(1, t));
            out[0][i] = x[seg - 1] + (x[seg] - x[seg - 1]) * t;
            out[1][i] = y[seg - 1] + (y[seg] - y[seg - 1]) * t;
        }
        return out;
    }
}
