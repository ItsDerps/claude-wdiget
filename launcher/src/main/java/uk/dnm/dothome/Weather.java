package uk.dnm.dothome;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Optional weather from Open-Meteo (free, no account). Off until turned on in settings.
 * The only thing ever sent is a rough position (or the city you typed) to fetch the forecast.
 */
final class Weather {
    private Weather() { }

    static final class Data {
        double temp, hi, lo;
        int code;
        double[] hours = new double[0];   // the next hours' temperatures
        String place;
        long fetched;
    }

    static final class Place {
        final double lat, lon;
        final String name;
        Place(double lat, double lon, String name) { this.lat = lat; this.lon = lon; this.name = name; }
    }

    private static final long FRESH_MS = 30 * 60 * 1000L;
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static boolean busy;

    private static SharedPreferences p(Context c) { return Prefs.get(c).raw(); }

    static boolean enabled(Context c) { return p(c).getBoolean("weather_on", false); }
    static boolean fahrenheit(Context c) {
        return p(c).getBoolean("weather_f", Locale.getDefault().getCountry().equals("US"));
    }
    static boolean useLocation(Context c) { return p(c).getBoolean("weather_use_loc", false); }
    static String placeName(Context c) { return p(c).getString("weather_place", null); }

    static void setCity(Context c, Place pl) {
        p(c).edit().putBoolean("weather_on", true).putBoolean("weather_use_loc", false)
                .putFloat("weather_lat", (float) pl.lat).putFloat("weather_lon", (float) pl.lon)
                .putString("weather_place", pl.name).remove("weather_cache").apply();
    }

    static void setUseLocation(Context c) {
        p(c).edit().putBoolean("weather_on", true).putBoolean("weather_use_loc", true)
                .remove("weather_place").remove("weather_cache").apply();
    }

    static void turnOff(Context c) {
        p(c).edit().putBoolean("weather_on", false).remove("weather_cache").apply();
    }

    static Data cached(Context c) {
        try {
            String s = p(c).getString("weather_cache", null);
            if (s == null) return null;
            JSONObject o = new JSONObject(s);
            Data d = new Data();
            d.temp = o.getDouble("t");
            d.hi = o.getDouble("hi");
            d.lo = o.getDouble("lo");
            d.code = o.getInt("c");
            d.place = o.optString("p", null);
            d.fetched = o.getLong("f");
            JSONArray h = o.getJSONArray("h");
            d.hours = new double[h.length()];
            for (int i = 0; i < h.length(); i++) d.hours[i] = h.getDouble(i);
            return d;
        } catch (Exception e) {
            return null;
        }
    }

    /** Fetch a new forecast if the cached one is old (or {@code force}). Calls back on the main thread. */
    static void refresh(Context ctx, boolean force, Runnable done) {
        Context c = ctx.getApplicationContext();
        if (!enabled(c) || busy) return;
        Data old = cached(c);
        if (!force && old != null && System.currentTimeMillis() - old.fetched < FRESH_MS) return;
        busy = true;
        new Thread(() -> {
            try {
                Place pl = currentPlace(c);
                if (pl == null) return;
                String url = String.format(Locale.ROOT,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f"
                                + "&current=temperature_2m,weather_code&hourly=temperature_2m"
                                + "&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=2",
                        pl.lat, pl.lon);
                JSONObject o = new JSONObject(get(url));
                JSONObject cur = o.getJSONObject("current");
                Data d = new Data();
                d.temp = cur.getDouble("temperature_2m");
                d.code = cur.getInt("weather_code");
                JSONObject daily = o.getJSONObject("daily");
                d.hi = daily.getJSONArray("temperature_2m_max").getDouble(0);
                d.lo = daily.getJSONArray("temperature_2m_min").getDouble(0);
                // Hourly temperatures from now on
                JSONArray times = o.getJSONObject("hourly").getJSONArray("time");
                JSONArray temps = o.getJSONObject("hourly").getJSONArray("temperature_2m");
                String now = cur.getString("time").substring(0, 13);
                int start = 0;
                for (int i = 0; i < times.length(); i++) if (times.getString(i).startsWith(now)) { start = i; break; }
                int n = Math.min(10, temps.length() - start);
                d.hours = new double[Math.max(0, n)];
                for (int i = 0; i < n; i++) d.hours[i] = temps.getDouble(start + i);
                d.place = pl.name;
                d.fetched = System.currentTimeMillis();
                save(c, d);
            } catch (Exception ignored) {
            } finally {
                busy = false;
                if (done != null) main.post(done);
            }
        }, "weather").start();
    }

    private static Place currentPlace(Context c) {
        if (!useLocation(c)) {
            if (!p(c).contains("weather_lat")) return null;
            return new Place(p(c).getFloat("weather_lat", 0), p(c).getFloat("weather_lon", 0), placeName(c));
        }
        if (c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null;
        LocationManager lm = c.getSystemService(LocationManager.class);
        Location best = null;
        for (String prov : new String[]{"fused", LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER}) {
            try {
                Location l = lm.getLastKnownLocation(prov);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            } catch (Exception ignored) { }
        }
        if (best == null) return null;
        return new Place(best.getLatitude(), best.getLongitude(), null);
    }

    /** Look up a city by name. Blocking: call off the main thread. */
    static Place geocode(String city) {
        try {
            JSONObject o = new JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?count=1&name="
                    + URLEncoder.encode(city.trim(), "UTF-8")));
            JSONArray res = o.optJSONArray("results");
            if (res == null || res.length() == 0) return null;
            JSONObject r = res.getJSONObject(0);
            String name = r.optString("name", city);
            return new Place(r.getDouble("latitude"), r.getDouble("longitude"), name);
        } catch (Exception e) {
            return null;
        }
    }

    private static void save(Context c, Data d) throws Exception {
        JSONObject o = new JSONObject();
        o.put("t", d.temp).put("hi", d.hi).put("lo", d.lo).put("c", d.code).put("f", d.fetched);
        if (d.place != null) o.put("p", d.place);
        JSONArray h = new JSONArray();
        for (double v : d.hours) h.put(v);
        o.put("h", h);
        p(c).edit().putString("weather_cache", o.toString()).apply();
    }

    private static String get(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(8000);
        con.setRequestProperty("User-Agent", "DotHome");
        try (BufferedReader r = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return sb.toString();
        } finally {
            con.disconnect();
        }
    }

    static String temp(Context c, double celsius) {
        double v = fahrenheit(c) ? celsius * 9 / 5 + 32 : celsius;
        return Math.round(v) + "°";
    }

    /** WMO weather codes to a word or two. */
    static String describe(int code) {
        if (code == 0) return "Clear";
        if (code <= 2) return "Partly cloudy";
        if (code == 3) return "Cloudy";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Showers";
        if (code >= 85 && code <= 86) return "Snow showers";
        if (code >= 95) return "Thunderstorm";
        return "—";
    }
}
