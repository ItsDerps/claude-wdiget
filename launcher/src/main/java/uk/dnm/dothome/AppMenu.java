package uk.dnm.dothome;

import android.content.Context;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Touch & hold: the icon lifts, the screen behind dims and blurs, and a card shows the app's own
 * shortcuts plus actions that work on any app.
 */
final class AppMenu {

    interface Actions {
        void info(Apps.App a, View from);
        boolean onHome(Apps.App a);
        boolean inDock(Apps.App a);
        boolean dockFull();
        void toggleHome(Apps.App a);
        void toggleDock(Apps.App a);
        void hide(Apps.App a);
        void uninstall(Apps.App a);
        void setBackdrop(boolean blurred);
        /** The stack currently open, if the held app is inside one. */
        String openStackSlot();
        void removeFromStack(Apps.App a);
    }

    private final FrameLayout overlay;
    private final FrameLayout host;
    private final Actions actions;
    private final Spring spring;
    private View card;
    private boolean closing;

    private AppMenu(FrameLayout host, FrameLayout overlay, Actions actions) {
        this.host = host;
        this.overlay = overlay;
        this.actions = actions;
        spring = new Spring(500, 0.8f, 0f, v -> apply(v));
    }

    boolean isShowing() { return overlay.getParent() != null && !closing; }

    static AppMenu show(FrameLayout host, AppIconView src, Actions actions) {
        Context c = host.getContext();
        Apps.App app = src.app;
        FrameLayout overlay = new FrameLayout(c);
        AppMenu m = new AppMenu(host, overlay, actions);

        View dim = new View(c);
        dim.setBackgroundColor(0x99000000);
        dim.setOnClickListener(v -> m.dismiss());
        overlay.addView(dim, new FrameLayout.LayoutParams(-1, -1));

        // Where the held icon is, relative to the host
        int[] hostPos = new int[2], srcPos = new int[2];
        host.getLocationOnScreen(hostPos);
        src.getLocationOnScreen(srcPos);
        RectF disc = src.discBounds();
        float discLeft = srcPos[0] - hostPos[0] + disc.left, discTop = srcPos[1] - hostPos[1] + disc.top;

        AppIconView lifted = new AppIconView(c, disc.width() / c.getResources().getDisplayMetrics().density, false);
        lifted.bind(app, null);
        lifted.setLifted(true);
        lifted.setClickable(false);
        int lw = (int) Math.max(disc.width() + Ui.dp(c, 18), Ui.dp(c, 64));
        FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(lw, (int) (disc.height() + Ui.dp(c, 12)));
        llp.leftMargin = (int) (discLeft + disc.width() / 2 - lw / 2f);
        llp.topMargin = (int) (discTop - Ui.dp(c, 6));
        overlay.addView(lifted, llp);

        // The card
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dpi(c, 6), Ui.dpi(c, 6), Ui.dpi(c, 6), Ui.dpi(c, 6));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF161616);
        bg.setStroke(Ui.dpi(c, 1), Ui.LINE2);
        bg.setCornerRadius(Ui.dp(c, 24));
        box.setBackground(bg);
        box.setElevation(Ui.dp(c, 12));

        TextView title = new TextView(c);
        title.setText(app.label);
        title.setTextColor(0xFF9A9A9A);
        title.setTypeface(Ui.mono(c));
        title.setTextSize(10);
        title.setLetterSpacing(0.14f);
        title.setAllCaps(true);
        title.setPadding(Ui.dpi(c, 12), Ui.dpi(c, 6), Ui.dpi(c, 12), Ui.dpi(c, 6));
        box.addView(title);

        for (ShortcutInfo s : shortcuts(c, app)) {
            box.addView(m.shortcutRow(c, app, s));
        }

        LinearLayout row = new LinearLayout(c);
        row.setPadding(0, Ui.dpi(c, 6), 0, 0);
        View rule = new View(c);
        rule.setBackgroundColor(0xFF262626);
        box.addView(rule, new LinearLayout.LayoutParams(-1, Ui.dpi(c, 1)));
        boolean onHome = actions.onHome(app), inDock = actions.inDock(app);
        boolean inStack = actions.openStackSlot() != null;
        row.addView(m.action(c, Glyph.INFO, "Info", Ui.WHITE, () -> actions.info(app, src)));
        if (inStack) {
            row.addView(m.action(c, Glyph.STACK, "Unstack", Ui.WHITE, () -> actions.removeFromStack(app)));
        } else {
            row.addView(m.action(c, onHome ? Glyph.HOME_REMOVE : Glyph.HOME_ADD, onHome ? "Unpin" : "Pin", Ui.WHITE, () -> actions.toggleHome(app)));
            if (inDock || !actions.dockFull()) {
                row.addView(m.action(c, Glyph.DOCK, inDock ? "Undock" : "Dock", Ui.WHITE, () -> actions.toggleDock(app)));
            }
        }
        row.addView(m.action(c, Glyph.HIDE, "Hide", Ui.WHITE, () -> actions.hide(app)));
        row.addView(m.action(c, Glyph.REMOVE, "Remove", Ui.RED, () -> actions.uninstall(app)));
        box.addView(row);

        int cardW = Ui.dpi(c, 248);
        box.measure(View.MeasureSpec.makeMeasureSpec(cardW, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
        int cardH = box.getMeasuredHeight();
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(cardW, cardH);
        float cx = discLeft + disc.width() / 2;
        clp.leftMargin = (int) Ui.clamp(cx - cardW / 2f, Ui.dp(c, 12), host.getWidth() - cardW - Ui.dp(c, 12));
        float above = discTop - Ui.dp(c, 14) - cardH;
        boolean placeAbove = above > Ui.dp(c, 40);
        clp.topMargin = (int) (placeAbove ? above : discTop + disc.height() + Ui.dp(c, 18));
        overlay.addView(box, clp);
        box.setPivotX(cx - clp.leftMargin);
        box.setPivotY(placeAbove ? cardH : 0);
        m.card = box;

        host.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        actions.setBackdrop(true);
        if (Ui.motion(c) == Ui.MOTION_FULL) {
            m.apply(0);
            m.spring.animateTo(1f, 0);
        } else {
            m.spring.set(1f);
        }
        return m;
    }

    private static List<ShortcutInfo> shortcuts(Context c, Apps.App app) {
        List<ShortcutInfo> out = new ArrayList<>();
        LauncherApps la = c.getSystemService(LauncherApps.class);
        try {
            if (!la.hasShortcutHostPermission()) return out;
            LauncherApps.ShortcutQuery q = new LauncherApps.ShortcutQuery();
            q.setPackage(app.pkg());
            q.setActivity(app.component);
            q.setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC
                    | LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST | LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED);
            List<ShortcutInfo> l = la.getShortcuts(q, app.user);
            if (l != null) {
                l.sort((a, b) -> {
                    if (a.isDeclaredInManifest() != b.isDeclaredInManifest()) return a.isDeclaredInManifest() ? -1 : 1;
                    return Integer.compare(a.getRank(), b.getRank());
                });
                for (ShortcutInfo s : l) {
                    if (!s.isEnabled()) continue;
                    out.add(s);
                    if (out.size() == 4) break;
                }
            }
        } catch (Exception ignored) { }
        return out;
    }

    private View shortcutRow(Context c, Apps.App app, ShortcutInfo s) {
        LinearLayout r = new LinearLayout(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(Ui.dpi(c, 12), Ui.dpi(c, 10), Ui.dpi(c, 12), Ui.dpi(c, 10));
        r.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x22FFFFFF), null, round(c, Ui.WHITE, 16)));
        ImageView ic = new ImageView(c);
        try {
            Drawable d = c.getSystemService(LauncherApps.class).getShortcutIconDrawable(s, c.getResources().getDisplayMetrics().densityDpi);
            if (d != null) {
                d = d.mutate();
                android.graphics.ColorMatrix cm = new android.graphics.ColorMatrix();
                cm.setSaturation(0);
                d.setColorFilter(new android.graphics.ColorMatrixColorFilter(cm));
                ic.setImageDrawable(d);
            }
        } catch (Exception ignored) { }
        r.addView(ic, new LinearLayout.LayoutParams(Ui.dpi(c, 20), Ui.dpi(c, 20)));
        TextView t = new TextView(c);
        CharSequence label = s.getShortLabel() != null ? s.getShortLabel() : s.getLongLabel();
        t.setText(label);
        t.setTextColor(Ui.WHITE);
        t.setTextSize(14);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setPadding(Ui.dpi(c, 12), 0, 0, 0);
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        r.setOnClickListener(v -> {
            Ui.tap(v);
            try {
                Rect b = new Rect();
                v.getGlobalVisibleRect(b);
                c.getSystemService(LauncherApps.class).startShortcut(s.getPackage(), s.getId(), b, null, s.getUserHandle());
            } catch (Exception ignored) { }
            dismiss();
        });
        return r;
    }

    private View action(Context c, int glyph, String label, int color, Runnable run) {
        LinearLayout b = new LinearLayout(c);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER_HORIZONTAL);
        b.setPadding(0, Ui.dpi(c, 6), 0, Ui.dpi(c, 4));
        b.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x22FFFFFF), null, round(c, Ui.WHITE, 14)));
        ImageView ic = new ImageView(c);
        ic.setImageDrawable(new Glyph(glyph, Ui.dpi(c, 18), color));
        b.addView(ic, new LinearLayout.LayoutParams(Ui.dpi(c, 18), Ui.dpi(c, 18)));
        TextView t = new TextView(c);
        t.setText(label);
        t.setTextColor(color == Ui.RED ? Ui.RED : 0xFFAAAAAA);
        t.setTextSize(9.5f);
        t.setPadding(0, Ui.dpi(c, 4), 0, 0);
        b.addView(t);
        b.setOnClickListener(v -> {
            Ui.tap(v);
            dismiss();
            run.run();
        });
        b.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        return b;
    }

    private static GradientDrawable round(Context c, int color, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(Ui.dp(c, r));
        return g;
    }

    private void apply(float v) {
        if (card == null) return;
        float s = 0.9f + 0.1f * v;
        card.setScaleX(s);
        card.setScaleY(s);
        card.setAlpha(Ui.clamp(v, 0, 1));
        overlay.getChildAt(0).setAlpha(Ui.clamp(v, 0, 1));
    }

    void dismiss() {
        if (closing) return;
        closing = true;
        actions.setBackdrop(false);
        if (Ui.motion(host.getContext()) == Ui.MOTION_FULL) {
            card.animate().alpha(0).scaleX(0.94f).scaleY(0.94f).setDuration(120).start();
            overlay.animate().alpha(0).setDuration(140).withEndAction(() -> host.removeView(overlay)).start();
        } else {
            host.removeView(overlay);
        }
    }
}
