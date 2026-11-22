package uk.dnm.dothome;

import android.app.SearchManager;
import android.content.Intent;
import android.net.Uri;
import android.provider.AlarmClock;
import android.provider.Settings;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Everything the search box can find: apps, answers, quick actions, settings and the web. */
final class SearchEngine {
    private SearchEngine() { }

    static final class Action {
        final String title, subtitle;
        final Intent intent;
        Action(String t, String s, Intent i) { title = t; subtitle = s; intent = i; }
    }

    static final class Results {
        String query;
        List<Apps.App> apps = new ArrayList<>();
        String answer;          // e.g. "51"
        String answerLabel;     // e.g. "12 × 4 + 3 ="
        List<Action> actions = new ArrayList<>();
        List<Action> settings = new ArrayList<>();
        Action web;
        /** What pressing go/enter does: an app, an action, or the web search. */
        Object topHit;
    }

    static Results search(String raw, List<Apps.App> apps, Apps repo) {
        Results r = new Results();
        String q = raw.trim();
        r.query = q;
        if (q.isEmpty()) return r;
        String lq = q.toLowerCase(Locale.ROOT);

        // ---- apps
        List<Object[]> scored = new ArrayList<>();
        for (Apps.App a : apps) {
            double s = appScore(a.lower, lq);
            if (s <= 0) continue;
            s += 4 * repo.usageBoost(a);
            scored.add(new Object[]{a, s});
        }
        scored.sort((x, y) -> Double.compare((double) y[1], (double) x[1]));
        double best = scored.isEmpty() ? 0 : (double) scored.get(0)[1];
        for (int i = 0; i < Math.min(8, scored.size()); i++) r.apps.add((Apps.App) scored.get(i)[0]);

        // ---- answers
        String units = Units.convert(lq);
        if (units != null) {
            r.answer = units;
            r.answerLabel = q.toUpperCase(Locale.ROOT) + " =";
        } else if (Calc.looksLikeMath(lq)) {
            Double v = Calc.eval(lq);
            if (v != null && !v.isNaN() && !v.isInfinite()) {
                r.answer = Calc.format(v);
                r.answerLabel = Calc.pretty(q) + " =";
            }
        }

        // ---- quick actions
        Matcher m = Pattern.compile("^timer\\s+(\\d+(?:\\.\\d+)?)\\s*(s|sec|secs|seconds?|m|min|mins|minutes?|h|hr|hours?)?$").matcher(lq);
        if (m.find()) {
            double n = Double.parseDouble(m.group(1));
            String unit = m.group(2) == null ? "m" : m.group(2);
            int secs = (int) Math.round(unit.startsWith("s") ? n : unit.startsWith("h") ? n * 3600 : n * 60);
            Intent i = new Intent(AlarmClock.ACTION_SET_TIMER)
                    .putExtra(AlarmClock.EXTRA_LENGTH, secs)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, false);
            r.actions.add(new Action("Start a " + human(secs) + " timer", "Press go to start", i));
        }
        m = Pattern.compile("^alarm\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?$").matcher(lq);
        if (m.find()) {
            int h = Integer.parseInt(m.group(1));
            int min = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
            if ("pm".equals(m.group(3)) && h < 12) h += 12;
            if ("am".equals(m.group(3)) && h == 12) h = 0;
            if (h < 24 && min < 60) {
                Intent i = new Intent(AlarmClock.ACTION_SET_ALARM)
                        .putExtra(AlarmClock.EXTRA_HOUR, h)
                        .putExtra(AlarmClock.EXTRA_MINUTES, min);
                r.actions.add(new Action(String.format(Locale.ROOT, "Set an alarm for %02d:%02d", h, min), "Press go to set it", i));
            }
        }
        if (lq.matches("^[+0-9 ()-]{6,}$")) {
            r.actions.add(new Action("Call " + q, "Opens the dialer", new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(q)))));
        }

        // ---- settings pages
        for (String[] s : SETTINGS) {
            if (s[0].toLowerCase(Locale.ROOT).contains(lq) || (lq.length() >= 3 && s[2].contains(lq))) {
                r.settings.add(new Action(s[0], "Settings", new Intent(s[1])));
                if (r.settings.size() == 3) break;
            }
        }

        // ---- web
        Intent web = new Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, q);
        r.web = new Action("Search the web for “" + q + "”", null, web);

        // Top hit: a confident app match first, then a quick action, then the web
        if (!r.apps.isEmpty() && best >= 60) r.topHit = r.apps.get(0);
        else if (!r.actions.isEmpty()) r.topHit = r.actions.get(0);
        else if (!r.apps.isEmpty()) r.topHit = r.apps.get(0);
        else r.topHit = r.web;
        return r;
    }

    /** How well an app name matches: start of name > start of a word > initials > contains > letters in order. */
    static double appScore(String name, String q) {
        if (name.startsWith(q)) return 100 - Math.min(20, name.length() - q.length());
        String[] words = name.split("[\\s\\-_.:]+");
        for (int i = 1; i < words.length; i++) if (words[i].startsWith(q)) return 80 - i;
        if (q.length() >= 2) {
            StringBuilder initials = new StringBuilder();
            for (String w : words) if (!w.isEmpty()) initials.append(w.charAt(0));
            if (initials.toString().startsWith(q)) return 70;
        }
        if (q.length() >= 2 && name.contains(q)) return 50;
        if (q.length() >= 3) {
            int j = 0;
            for (int i = 0; i < name.length() && j < q.length(); i++) if (name.charAt(i) == q.charAt(j)) j++;
            if (j == q.length()) return 20;
        }
        return 0;
    }

    private static String human(int secs) {
        if (secs % 3600 == 0) return (secs / 3600) + " hour";
        if (secs % 60 == 0) return (secs / 60) + " minute";
        return secs + " second";
    }

    /** Settings pages: title, intent action, extra search words. */
    private static final String[][] SETTINGS = {
            {"Wi-Fi", Settings.ACTION_WIFI_SETTINGS, "wifi internet network"},
            {"Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS, "headphones earbuds pair"},
            {"Battery", Intent.ACTION_POWER_USAGE_SUMMARY, "power charge saver"},
            {"Display", Settings.ACTION_DISPLAY_SETTINGS, "brightness screen dark mode"},
            {"Sound & vibration", Settings.ACTION_SOUND_SETTINGS, "volume ringtone vibrate"},
            {"Notifications", Settings.ACTION_NOTIFICATION_SETTINGS, "alerts"},
            {"Apps", Settings.ACTION_APPLICATION_SETTINGS, "applications"},
            {"Default apps", Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, "home launcher browser"},
            {"Location", Settings.ACTION_LOCATION_SOURCE_SETTINGS, "gps"},
            {"Security", Settings.ACTION_SECURITY_SETTINGS, "fingerprint lock password"},
            {"Privacy", Settings.ACTION_PRIVACY_SETTINGS, "permissions"},
            {"Storage", Settings.ACTION_INTERNAL_STORAGE_SETTINGS, "space files"},
            {"Date & time", Settings.ACTION_DATE_SETTINGS, "clock timezone"},
            {"Accessibility", Settings.ACTION_ACCESSIBILITY_SETTINGS, "talkback"},
            {"Mobile network", Settings.ACTION_NETWORK_OPERATOR_SETTINGS, "data sim roaming"},
            {"Hotspot & tethering", "android.settings.TETHER_SETTINGS", "share"},
            {"Do Not Disturb", Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS, "quiet dnd silent"},
            {"Keyboard", Settings.ACTION_INPUT_METHOD_SETTINGS, "language input swipe keys"},
            {"Developer options", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, "debug"},
            {"About phone", Settings.ACTION_DEVICE_INFO_SETTINGS, "version model"},
    };

    // ------------------------------------------------------------------ calculator

    static final class Calc {
        private final String s;
        private int pos;

        private Calc(String s) { this.s = s; }

        static boolean looksLikeMath(String q) {
            return q.matches("^[0-9.\\s+\\-*/x×÷^%()]+$") && q.matches(".*\\d.*") && q.matches(".*[+\\-*/x×÷^%].*");
        }

        static Double eval(String q) {
            try {
                Calc c = new Calc(q.replace('x', '*').replace('×', '*').replace('÷', '/').replace(" ", ""));
                double v = c.expr();
                return c.pos == c.s.length() ? v : null;
            } catch (Exception e) {
                return null;
            }
        }

        static String format(double v) {
            if (Math.abs(v) >= 1e12 || (Math.abs(v) < 1e-6 && v != 0)) return String.format(Locale.ROOT, "%.4g", v);
            return new DecimalFormat("#,##0.######").format(v);
        }

        static String pretty(String q) {
            return q.replaceAll("(?<=\\d)\\s*[xX]\\s*(?=\\d)", "*").replace("*", " × ").replace("/", " ÷ ").replace("+", " + ").replaceAll("(?<=\\d)-", " − ")
                    .replaceAll("\\s+", " ").trim().toUpperCase(Locale.ROOT);
        }

        private double expr() {
            double v = term();
            while (pos < s.length()) {
                char ch = s.charAt(pos);
                if (ch == '+') { pos++; v += term(); }
                else if (ch == '-') { pos++; v -= term(); }
                else break;
            }
            return v;
        }

        private double term() {
            double v = power();
            while (pos < s.length()) {
                char ch = s.charAt(pos);
                if (ch == '*') { pos++; v *= power(); }
                else if (ch == '/') { pos++; v /= power(); }
                else break;
            }
            return v;
        }

        private double power() {
            double v = unary();
            if (pos < s.length() && s.charAt(pos) == '^') { pos++; v = Math.pow(v, power()); }
            return v;
        }

        private double unary() {
            if (pos < s.length() && s.charAt(pos) == '-') { pos++; return -unary(); }
            if (pos < s.length() && s.charAt(pos) == '+') { pos++; return unary(); }
            double v = atom();
            if (pos < s.length() && s.charAt(pos) == '%') { pos++; v /= 100; }
            return v;
        }

        private double atom() {
            if (s.charAt(pos) == '(') {
                pos++;
                double v = expr();
                if (pos < s.length() && s.charAt(pos) == ')') pos++;
                return v;
            }
            int start = pos;
            while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) pos++;
            return Double.parseDouble(s.substring(start, pos));
        }
    }

    // ------------------------------------------------------------------ unit conversion

    static final class Units {
        private static final Pattern P = Pattern.compile(
                "^(-?\\d+(?:\\.\\d+)?)\\s*([a-z°]+)\\s+(?:in|to|as)\\s+([a-z°]+)$");

        /** "5 km in miles" -> "3.11 miles". Null if it isn't a conversion. */
        static String convert(String q) {
            Matcher m = P.matcher(q);
            if (!m.find()) return null;
            double v = Double.parseDouble(m.group(1));
            String from = unit(m.group(2)), to = unit(m.group(3));
            if (from == null || to == null) return null;
            if (from.equals("c") && to.equals("f")) return Calc.format(round(v * 9 / 5 + 32)) + " °F";
            if (from.equals("f") && to.equals("c")) return Calc.format(round((v - 32) * 5 / 9)) + " °C";
            Double a = metres(from), b = metres(to);
            if (a != null && b != null) return Calc.format(round(v * a / b)) + " " + name(to);
            a = grams(from);
            b = grams(to);
            if (a != null && b != null) return Calc.format(round(v * a / b)) + " " + name(to);
            return null;
        }

        private static double round(double v) { return Math.round(v * 100) / 100.0; }

        private static String unit(String u) {
            switch (u) {
                case "km": case "kilometre": case "kilometres": case "kilometer": case "kilometers": return "km";
                case "m": case "metre": case "metres": case "meter": case "meters": return "m";
                case "cm": case "centimetres": case "centimeters": return "cm";
                case "mm": return "mm";
                case "mi": case "mile": case "miles": return "mi";
                case "ft": case "foot": case "feet": return "ft";
                case "in": case "inch": case "inches": return "in";
                case "yd": case "yard": case "yards": return "yd";
                case "kg": case "kilo": case "kilos": case "kilograms": return "kg";
                case "g": case "gram": case "grams": return "g";
                case "lb": case "lbs": case "pound": case "pounds": return "lb";
                case "oz": case "ounce": case "ounces": return "oz";
                case "st": case "stone": return "st";
                case "c": case "°c": case "celsius": return "c";
                case "f": case "°f": case "fahrenheit": return "f";
                default: return null;
            }
        }

        private static Double metres(String u) {
            switch (u) {
                case "km": return 1000.0; case "m": return 1.0; case "cm": return 0.01; case "mm": return 0.001;
                case "mi": return 1609.344; case "ft": return 0.3048; case "in": return 0.0254; case "yd": return 0.9144;
                default: return null;
            }
        }

        private static Double grams(String u) {
            switch (u) {
                case "kg": return 1000.0; case "g": return 1.0; case "lb": return 453.59237; case "oz": return 28.349523;
                case "st": return 6350.29;
                default: return null;
            }
        }

        private static String name(String u) {
            switch (u) {
                case "mi": return "miles"; case "ft": return "ft"; case "in": return "in"; case "lb": return "lb";
                default: return u;
            }
        }
    }
}
