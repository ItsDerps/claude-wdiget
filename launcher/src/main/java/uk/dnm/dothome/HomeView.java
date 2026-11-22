package uk.dnm.dothome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** The home screen: clock and glance chips on top (for looking), apps and dock below (for touching). */
public class HomeView extends LinearLayout {

    interface Host extends AppIconView.Listener {
        void onClockTap();
        void onDateTap();
        void onBatteryTap();
        void onEventTap();
    }

    final DotClockView clock;
    private final TextView date;
    private final LinearLayout glance;
    private final TextView battery, event;
    private final LinearLayout pinnedBox, dock;
    private final View hint;
    private final View top;

    public HomeView(Context c, Host host) {
        super(c);
        setOrientation(VERTICAL);
        setClipChildren(false);
        setClipToPadding(false);

        LinearLayout topBox = new LinearLayout(c);
        topBox.setOrientation(VERTICAL);
        topBox.setPadding(Ui.dpi(c, 24), Ui.dpi(c, 28), Ui.dpi(c, 24), 0);
        top = topBox;
        clock = new DotClockView(c);
        clock.setOnClickListener(v -> host.onClockTap());
        topBox.addView(clock, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        date = new TextView(c);
        date.setTypeface(Ui.mono(c));
        date.setTextSize(11.5f);
        date.setLetterSpacing(0.2f);
        date.setTextColor(0xFF9A9A9A);
        date.setPadding(0, Ui.dpi(c, 14), 0, 0);
        date.setOnClickListener(v -> host.onDateTap());
        topBox.addView(date);
        glance = new LinearLayout(c);
        glance.setPadding(0, Ui.dpi(c, 12), 0, 0);
        battery = chip(c);
        battery.setOnClickListener(v -> host.onBatteryTap());
        event = chip(c);
        event.setOnClickListener(v -> host.onEventTap());
        event.setVisibility(GONE);
        glance.addView(event);
        glance.addView(battery);
        topBox.addView(glance);
        addView(topBox);

        addView(new View(c), new LayoutParams(1, 0, 1f));

        pinnedBox = new LinearLayout(c);
        pinnedBox.setOrientation(VERTICAL);
        pinnedBox.setClipChildren(false);
        pinnedBox.setPadding(Ui.dpi(c, 10), 0, Ui.dpi(c, 10), Ui.dpi(c, 14));
        addView(pinnedBox);

        dock = new LinearLayout(c);
        dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.setClipChildren(false);
        dock.setPadding(Ui.dpi(c, 4), Ui.dpi(c, 4), Ui.dpi(c, 4), Ui.dpi(c, 4));
        GradientDrawable tray = new GradientDrawable();
        tray.setColor(0x0AFFFFFF);
        tray.setStroke(Ui.dpi(c, 1), 0xFF1C1C1C);
        tray.setCornerRadius(Ui.dp(c, 34));
        dock.setBackground(tray);
        LayoutParams dlp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        dlp.setMargins(Ui.dpi(c, 14), 0, Ui.dpi(c, 14), 0);
        addView(dock, dlp);

        hint = new HintDots(c);
        addView(hint, new LayoutParams(LayoutParams.MATCH_PARENT, Ui.dpi(c, 22)));
        updateDate();
    }

    private TextView chip(Context c) {
        TextView t = new TextView(c);
        t.setTextSize(11);
        t.setTextColor(0xFFCFCFCF);
        t.setPadding(Ui.dpi(c, 9), Ui.dpi(c, 5), Ui.dpi(c, 10), Ui.dpi(c, 5));
        t.setCompoundDrawablePadding(Ui.dpi(c, 6));
        t.setSingleLine(true);
        t.setMaxWidth(Ui.dpi(c, 190));
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF121212);
        bg.setStroke(Ui.dpi(c, 1), Ui.LINE);
        bg.setCornerRadius(Ui.dp(c, 16));
        t.setBackground(bg);
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dpi(c, 7);
        t.setLayoutParams(lp);
        return t;
    }

    private GradientDrawable dot(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        int s = Ui.dpi(getContext(), 6);
        d.setSize(s, s);
        d.setBounds(0, 0, s, s);
        return d;
    }

    void setInsets(int topInset, int bottomInset) {
        setPadding(0, topInset, 0, bottomInset);
    }

    void updateDate() {
        date.setText(new SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(new Date()).toUpperCase(Locale.getDefault()).replace(".", ""));
        clock.update();
    }

    void setShowTop(boolean showClock, boolean showGlance) {
        clock.setVisibility(showClock ? VISIBLE : GONE);
        glance.setVisibility(showGlance ? VISIBLE : GONE);
    }

    void setBattery(int level, boolean charging) {
        battery.setText(level + "%");
        battery.setCompoundDrawables(dot(charging ? Ui.RED : level <= 15 ? Ui.RED : 0xFF777777), null, null, null);
    }

    void setEvent(String text) {
        if (text == null) {
            event.setVisibility(GONE);
        } else {
            event.setText(text);
            event.setCompoundDrawables(dot(Ui.WHITE), null, null, null);
            event.setVisibility(VISIBLE);
        }
    }

    void bind(List<Apps.App> pinned, List<Apps.App> dockApps, boolean labels, Set<String> badges, AppIconView.Listener l) {
        Context c = getContext();
        pinnedBox.removeAllViews();
        LinearLayout row = null;
        for (int i = 0; i < Math.min(12, pinned.size()); i++) {
            if (i % 4 == 0) {
                row = new LinearLayout(c);
                row.setClipChildren(false);
                LayoutParams rlp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
                rlp.topMargin = i == 0 ? 0 : Ui.dpi(c, 6);
                pinnedBox.addView(row, rlp);
            }
            row.addView(icon(pinned.get(i), labels, badges, l), new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        }
        if (row != null) for (int i = row.getChildCount(); i < 4; i++) row.addView(new View(c), new LayoutParams(0, 1, 1f));

        dock.removeAllViews();
        for (Apps.App a : dockApps) dock.addView(icon(a, false, badges, l), new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        for (int i = dockApps.size(); i < 4; i++) dock.addView(new View(c), new LayoutParams(0, 1, 1f));
        dock.setVisibility(dockApps.isEmpty() ? GONE : VISIBLE);
    }

    private AppIconView icon(Apps.App a, boolean labels, Set<String> badges, AppIconView.Listener l) {
        AppIconView v = new AppIconView(getContext(), 48, labels);
        v.bind(a, l);
        v.setBadge(badges.contains(a.pkg()));
        return v;
    }

    void updateBadges(Set<String> pkgs) {
        walk(this, pkgs);
    }

    private void walk(android.view.ViewGroup g, Set<String> pkgs) {
        for (int i = 0; i < g.getChildCount(); i++) {
            View v = g.getChildAt(i);
            if (v instanceof AppIconView) {
                AppIconView a = (AppIconView) v;
                if (a.app != null) a.setBadge(pkgs.contains(a.app.pkg()));
            } else if (v instanceof android.view.ViewGroup) {
                walk((android.view.ViewGroup) v, pkgs);
            }
        }
    }

    /** Three dots under the dock: swipe up for everything. */
    private static final class HintDots extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        HintDots(Context c) { super(c); }

        @Override
        protected void onDraw(Canvas c) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f + Ui.dp(getContext(), 2), gap = Ui.dp(getContext(), 9);
            for (int i = -1; i <= 1; i++) {
                p.setColor(i == 0 ? 0xFF888888 : 0xFF555555);
                c.drawCircle(cx + i * gap, cy, Ui.dp(getContext(), 2), p);
            }
        }
    }
}
