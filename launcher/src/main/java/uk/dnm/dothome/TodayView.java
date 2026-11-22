package uk.dnm.dothome;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.BatteryManager;
import android.provider.CalendarContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Swipe right from home: today's agenda, weather, battery and a quick note. */
public class TodayView extends FrameLayout {

    private final LinearLayout col;
    private final ScrollView scroll;
    private EditText note;

    public TodayView(Context c) {
        super(c);
        setBackgroundColor(0xFF050505);
        setClickable(true);
        scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(OVER_SCROLL_NEVER);
        scroll.setClipToPadding(false);
        col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dpi(c, 16), Ui.dpi(c, 12), Ui.dpi(c, 16), Ui.dpi(c, 24));
        scroll.addView(col);
        addView(scroll, new LayoutParams(-1, -1));
    }

    void setInsets(int top, int bottom) {
        setPadding(0, top, 0, 0);
        scroll.setPadding(0, 0, 0, bottom);
    }

    boolean atTop() { return scroll.getScrollY() <= 0; }

    void hideKeyboard() {
        if (note != null) {
            getContext().getSystemService(android.view.inputmethod.InputMethodManager.class)
                    .hideSoftInputFromWindow(note.getWindowToken(), 0);
            note.clearFocus();
        }
    }

    /** Rebuild with fresh data (called each time the page opens). */
    void refresh() {
        Context c = getContext();
        col.removeAllViews();
        TextView title = new TextView(c);
        title.setText("TODAY");
        title.setTypeface(Ui.dots(c));
        title.setTextSize(32);
        title.setTextColor(Ui.WHITE);
        col.addView(title);
        TextView date = new TextView(c);
        date.setText(new SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(new Date()).toUpperCase(Locale.getDefault()));
        date.setTypeface(Ui.mono(c));
        date.setTextSize(10.5f);
        date.setLetterSpacing(0.18f);
        date.setTextColor(0xFF8A8A8A);
        date.setPadding(0, Ui.dpi(c, 2), 0, Ui.dpi(c, 10));
        col.addView(date);

        agenda(c);
        if (Weather.enabled(c)) {
            LinearLayout card = card(c, null);
            card.setPadding(0, 0, 0, 0);
            card.addView(new Widgets.WeatherW(c), new LinearLayout.LayoutParams(-1, Ui.dpi(c, 124)));
        }
        battery(c);
        noteCard(c);
    }

    private void agenda(Context c) {
        LinearLayout card = card(c, "AGENDA");
        if (c.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            card.addView(line(c, "Allow calendar access in Dot Home settings to see your day", 0xFF999999, 13));
            card.setOnClickListener(v -> Widgets.start(c, new Intent(c, SettingsActivity.class)));
            return;
        }
        long now = System.currentTimeMillis();
        java.util.Calendar end = java.util.Calendar.getInstance();
        end.set(java.util.Calendar.HOUR_OF_DAY, 23);
        end.set(java.util.Calendar.MINUTE, 59);
        Uri.Builder b = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(b, now - 12 * 3600_000L);
        ContentUris.appendId(b, end.getTimeInMillis());
        int shown = 0;
        try (Cursor cur = c.getContentResolver().query(b.build(),
                new String[]{CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
                        CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.EVENT_ID},
                CalendarContract.Instances.END + ">?", new String[]{String.valueOf(now)}, CalendarContract.Instances.BEGIN + " ASC")) {
            java.text.DateFormat tf = android.text.format.DateFormat.getTimeFormat(c);
            while (cur != null && cur.moveToNext() && shown < 5) {
                String t = cur.getString(0);
                boolean allDay = cur.getInt(3) == 1;
                long begin = cur.getLong(1), id = cur.getLong(4);
                LinearLayout row = new LinearLayout(c);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, Ui.dpi(c, 7), 0, Ui.dpi(c, 7));
                TextView time = new TextView(c);
                time.setText(allDay ? "ALL DAY" : begin <= now ? "NOW" : tf.format(new Date(begin)));
                time.setTypeface(Ui.mono(c));
                time.setTextSize(10.5f);
                time.setTextColor(begin <= now && !allDay ? Ui.RED : 0xFF999999);
                row.addView(time, new LinearLayout.LayoutParams(Ui.dpi(c, 64), -2));
                TextView name = new TextView(c);
                name.setText(t == null || t.isEmpty() ? "Event" : t);
                name.setTextColor(Ui.WHITE);
                name.setTextSize(14);
                name.setSingleLine(true);
                name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
                row.setOnClickListener(v -> Widgets.start(c, new Intent(Intent.ACTION_VIEW,
                        ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id))));
                card.addView(row);
                shown++;
            }
        } catch (Exception ignored) { }
        if (shown == 0) card.addView(line(c, "Nothing else today", 0xFFCCCCCC, 15));
    }

    private void battery(Context c) {
        Intent b = c.registerReceiver(null, new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (b == null) return;
        int level = Math.round(b.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100f / b.getIntExtra(BatteryManager.EXTRA_SCALE, 100));
        int st = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean charging = st == BatteryManager.BATTERY_STATUS_CHARGING;
        LinearLayout card = card(c, "BATTERY");
        TextView big = new TextView(c);
        big.setText(level + "%");
        big.setTypeface(Ui.dots(c));
        big.setTextSize(30);
        big.setTextColor(Ui.WHITE);
        card.addView(big);
        String sub = charging ? "Charging" : level <= 20 ? "Low — consider charging" : "On battery";
        if (charging) {
            long left = c.getSystemService(BatteryManager.class).computeChargeTimeRemaining();
            if (left > 0) sub += " · full in " + (left / 3600000 > 0 ? (left / 3600000) + " h " : "") + ((left / 60000) % 60) + " min";
        }
        card.addView(line(c, sub, 0xFF999999, 12));
        Widgets.DotBar bar = new Widgets.DotBar(c);
        bar.set(level / 100f);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, Ui.dpi(c, 10));
        bl.topMargin = Ui.dpi(c, 8);
        card.addView(bar, bl);
        card.setOnClickListener(v -> Widgets.start(c, new Intent(Intent.ACTION_POWER_USAGE_SUMMARY)));
    }

    private void noteCard(Context c) {
        LinearLayout card = card(c, "NOTE");
        note = new EditText(c);
        note.setBackground(null);
        note.setHint("Write a note…");
        note.setHintTextColor(0xFF555555);
        note.setTextColor(Ui.WHITE);
        note.setTextSize(15);
        note.setPadding(0, Ui.dpi(c, 4), 0, 0);
        note.setMinLines(3);
        note.setGravity(Gravity.TOP);
        note.setText(Prefs.get(c).raw().getString("today_note", ""));
        note.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) { }
            @Override public void afterTextChanged(Editable s) {
                Prefs.get(c).raw().edit().putString("today_note", s.toString()).apply();
            }
        });
        card.addView(note, new LinearLayout.LayoutParams(-1, -2));
    }

    private LinearLayout card(Context c, String label) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dpi(c, 16), Ui.dpi(c, 14), Ui.dpi(c, 16), Ui.dpi(c, 14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Ui.SURFACE);
        bg.setStroke(Ui.dpi(c, 1), 0xFF1F1F1F);
        bg.setCornerRadius(Ui.dp(c, 22));
        card.setBackground(bg);
        card.setClipToOutline(true);
        if (label != null) {
            TextView k = new TextView(c);
            k.setText(label);
            k.setTypeface(Ui.mono(c));
            k.setTextSize(9);
            k.setLetterSpacing(0.16f);
            k.setTextColor(0xFF888888);
            k.setPadding(0, 0, 0, Ui.dpi(c, 6));
            card.addView(k);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dpi(c, 10);
        col.addView(card, lp);
        return card;
    }

    private TextView line(Context c, String s, int color, float size) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(size);
        t.setTypeface(Typeface.DEFAULT);
        return t;
    }
}
