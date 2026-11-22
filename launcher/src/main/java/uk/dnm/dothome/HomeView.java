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
        void onWeatherTap();
    }

    /** One home slot: an app or a stack. */
    static final class Slot {
        final String key;              // app key or "stack:<id>"
        final Apps.App app;
        final String stackName;
        final List<Apps.App> stackApps;
        boolean big;                   // large icon / large folder (2 × 2)
        Slot(String key, Apps.App app) { this.key = key; this.app = app; stackName = null; stackApps = null; }
        Slot(String key, String name, List<Apps.App> apps) { this.key = key; app = null; stackName = name; stackApps = apps; }
    }

    static final int ZONE_HOME = 0, ZONE_DOCK = 1;
    static final int HOME_SLOTS = 12, DOCK_SLOTS = 4;

    final DotClockView clock;
    private final TextView date;
    private final LinearLayout glance;
    private final TextView battery, event, weather;
    final LinearLayout widgetBox;
    /** Every cell on home and in the dock, in order (edit mode includes the empty ones). */
    final List<View> homeCells = new java.util.ArrayList<>();
    final List<AppIconView> dockCells = new java.util.ArrayList<>();
    private final GridBox pinnedBox;
    private final LinearLayout dock;
    private BigStackView.Listener bigListener;
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
        weather = chip(c);
        weather.setVisibility(GONE);
        weather.setOnClickListener(v -> host.onWeatherTap());
        glance.addView(weather);
        glance.addView(event);
        glance.addView(battery);
        topBox.addView(glance);
        addView(topBox);

        widgetBox = new LinearLayout(c);
        widgetBox.setOrientation(VERTICAL);
        widgetBox.setPadding(Ui.dpi(c, 14), Ui.dpi(c, 16), Ui.dpi(c, 14), 0);
        widgetBox.setClipChildren(false);
        addView(widgetBox, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        addView(new View(c), new LayoutParams(1, 0, 1f));

        pinnedBox = new GridBox(c);
        pinnedBox.setClipChildren(false);
        pinnedBox.setPadding(Ui.dpi(c, 10), 0, Ui.dpi(c, 10), Ui.dpi(c, 14));
        addView(pinnedBox, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

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

    void setWeather(String text) {
        if (text == null) {
            weather.setVisibility(GONE);
        } else {
            weather.setText(text);
            weather.setCompoundDrawables(null, null, null, null);
            weather.setVisibility(VISIBLE);
        }
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

    void setBigListener(BigStackView.Listener l) { bigListener = l; }

    void bind(List<Slot> pinned, List<Apps.App> dockApps, boolean labels, Set<String> badges,
              AppIconView.Listener l, boolean edit) {
        Context c = getContext();
        pinnedBox.removeAllViews();
        homeCells.clear();
        dockCells.clear();
        // Place items the way the grid will, so edit mode can fill every free spot with a dashed slot
        boolean[][] used = new boolean[12][4];
        int rowsUsed = 0;
        for (Slot sl : pinned) {
            View v = slotView(sl, labels, badges, l);
            int[] at = GridBox.place(used, sl.big ? 2 : 1);
            if (at == null) continue;
            rowsUsed = Math.max(rowsUsed, at[0] + (sl.big ? 2 : 1));
            pinnedBox.addView(v, new GridBox.Params(sl.big ? 2 : 1));
            homeCells.add(v);
        }
        if (edit) {
            int rows = Math.max(3, Math.min(6, rowsUsed + 1));
            for (int r = 0; r < rows; r++) {
                for (int col = 0; col < 4; col++) {
                    if (used[r][col]) continue;
                    used[r][col] = true;
                    AppIconView ph = new AppIconView(c, 48, labels);
                    ph.makePlaceholder();
                    pinnedBox.addView(ph, new GridBox.Params(1));
                    homeCells.add(ph);
                }
            }
        }

        dock.removeAllViews();
        int dockCount = edit ? DOCK_SLOTS : dockApps.size();
        for (int i = 0; i < Math.max(dockCount, edit ? DOCK_SLOTS : 0); i++) {
            AppIconView v;
            if (i < dockApps.size()) {
                v = icon(dockApps.get(i), false, badges, l);
                v.slot = dockApps.get(i).key;
            } else {
                v = new AppIconView(c, 48, false);
                v.makePlaceholder();
            }
            dockCells.add(v);
            dock.addView(v, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        }
        for (int i = dock.getChildCount(); i < 4; i++) dock.addView(new View(c), new LayoutParams(0, 1, 1f));
        dock.setVisibility(dockApps.isEmpty() && !edit ? GONE : VISIBLE);
        hint.setVisibility(edit ? INVISIBLE : VISIBLE);
    }

    private View slotView(Slot s, boolean labels, Set<String> badges, AppIconView.Listener l) {
        if (s.app != null) {
            AppIconView v = new AppIconView(getContext(), s.big ? 104 : 48, labels);
            v.bind(s.app, l);
            v.setBadge(badges.contains(s.app.pkg()));
            v.slot = s.key;
            return v;
        }
        boolean any = false;
        for (Apps.App a : s.stackApps) if (badges.contains(a.pkg())) any = true;
        if (s.big) {
            return new BigStackView(getContext(), s.key, s.stackName, s.stackApps, labels, bigListener, l);
        }
        AppIconView v = new AppIconView(getContext(), 48, labels);
        v.bindStack(s.key, s.stackName, s.stackApps, l);
        v.setBadge(any);
        return v;
    }

    /** Homescreen app icons, for the landing / cascade animations. */
    List<AppIconView> allIcons() {
        List<AppIconView> out = new java.util.ArrayList<>();
        collect(this, out);
        return out;
    }

    private void collect(android.view.ViewGroup g, List<AppIconView> out) {
        for (int i = 0; i < g.getChildCount(); i++) {
            View v = g.getChildAt(i);
            if (v instanceof AppIconView) out.add((AppIconView) v);
            else if (v instanceof android.view.ViewGroup && !(v instanceof android.appwidget.AppWidgetHostView)) collect((android.view.ViewGroup) v, out);
        }
    }

    /** After unlock: icons rise into place one after another, from the bottom up. */
    void cascade() {
        if (Ui.motion(getContext()) != Ui.MOTION_FULL) return;
        List<AppIconView> icons = allIcons();
        int h = getHeight();
        for (AppIconView v : icons) {
            if (!v.hasContent()) continue;
            int[] p = new int[2];
            v.getLocationInWindow(p);
            float fromBottom = h - p[1];
            long delay = (long) (Ui.clamp(fromBottom / Math.max(1f, h), 0, 1) * 220);
            v.setAlpha(0f);
            v.setScaleX(0.7f);
            v.setScaleY(0.7f);
            v.setTranslationY(Ui.dp(getContext(), 18));
            v.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0).setStartDelay(delay).setDuration(380)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(1.6f)).start();
        }
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
                else if (a.stackApps != null) {
                    boolean any = false;
                    for (Apps.App x : a.stackApps) if (pkgs.contains(x.pkg())) any = true;
                    a.setBadge(any);
                }
            } else if (v instanceof android.view.ViewGroup && !(v instanceof android.appwidget.AppWidgetHostView)) {
                walk((android.view.ViewGroup) v, pkgs);
            }
        }
    }

    /**
     * Four columns. Normal items take one cell, large icons and large folders take 2 × 2.
     * Items are placed in order into the first free spot that fits.
     */
    static final class GridBox extends android.view.ViewGroup {
        static final class Params extends android.view.ViewGroup.LayoutParams {
            final int span;
            int row, col;
            Params(int span) { super(MATCH_PARENT, WRAP_CONTENT); this.span = span; }
        }

        private int cellW, cellH, rowGap;

        GridBox(Context c) {
            super(c);
            rowGap = Ui.dpi(c, 6);
        }

        /** Find and mark the first free spot for a span (1 or 2). Returns {row, col} or null. */
        static int[] place(boolean[][] used, int span) {
            for (int r = 0; r <= used.length - span; r++) {
                for (int c = 0; c <= 4 - span; c++) {
                    boolean free = true;
                    for (int dr = 0; dr < span && free; dr++) for (int dc = 0; dc < span; dc++) if (used[r + dr][c + dc]) { free = false; break; }
                    if (span == 2 && c % 2 != 0) free = false; // large items sit on the left or right half
                    if (!free) continue;
                    for (int dr = 0; dr < span; dr++) for (int dc = 0; dc < span; dc++) used[r + dr][c + dc] = true;
                    return new int[]{r, c};
                }
            }
            return null;
        }

        @Override
        protected void onMeasure(int w, int h) {
            int width = MeasureSpec.getSize(w);
            cellW = (width - getPaddingLeft() - getPaddingRight()) / 4;
            // Row height comes from a normal icon
            cellH = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View v = getChildAt(i);
                if (((Params) v.getLayoutParams()).span == 1) {
                    v.measure(MeasureSpec.makeMeasureSpec(cellW, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                    cellH = Math.max(cellH, v.getMeasuredHeight());
                }
            }
            if (cellH == 0) cellH = Ui.dpi(getContext(), 78);
            boolean[][] used = new boolean[12][4];
            int rows = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View v = getChildAt(i);
                Params p = (Params) v.getLayoutParams();
                int[] at = place(used, p.span);
                if (at == null) { p.row = -1; continue; }
                p.row = at[0];
                p.col = at[1];
                rows = Math.max(rows, at[0] + p.span);
                if (p.span == 2) {
                    v.measure(MeasureSpec.makeMeasureSpec(cellW * 2, MeasureSpec.EXACTLY),
                            MeasureSpec.makeMeasureSpec(cellH * 2 + rowGap, MeasureSpec.EXACTLY));
                }
            }
            int height = rows == 0 ? 0 : rows * cellH + (rows - 1) * rowGap;
            setMeasuredDimension(width, height + getPaddingTop() + getPaddingBottom());
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            for (int i = 0; i < getChildCount(); i++) {
                View v = getChildAt(i);
                Params p = (Params) v.getLayoutParams();
                if (p.row < 0) { v.layout(0, 0, 0, 0); continue; }
                int x = getPaddingLeft() + p.col * cellW, y = getPaddingTop() + p.row * (cellH + rowGap);
                int w = cellW * p.span, h = p.span == 1 ? cellH : cellH * 2 + rowGap;
                v.layout(x, y, x + w, y + h);
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
