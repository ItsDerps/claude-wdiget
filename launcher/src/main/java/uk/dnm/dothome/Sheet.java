package uk.dnm.dothome;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** A card that slides up from the bottom over a dimmed screen. Swipe it down or tap outside to close. */
final class Sheet {

    private final FrameLayout host;
    private final FrameLayout overlay;
    private final LinearLayout card;
    final LinearLayout body;
    private final Spring spring;
    private boolean closing;
    private Runnable onClosed;
    private float downY;

    Sheet(FrameLayout host, String title, int bottomInset) {
        this.host = host;
        Context c = host.getContext();
        overlay = new FrameLayout(c);
        View dim = new View(c);
        dim.setBackgroundColor(0x99000000);
        dim.setOnClickListener(v -> dismiss());
        overlay.addView(dim, new FrameLayout.LayoutParams(-1, -1));

        card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF111111);
        bg.setStroke(Ui.dpi(c, 1), Ui.LINE);
        float r = Ui.dp(c, 28);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        card.setBackground(bg);
        card.setPadding(0, Ui.dpi(c, 10), 0, bottomInset + Ui.dpi(c, 10));
        card.setClickable(true);

        View handle = new View(c);
        GradientDrawable hb = new GradientDrawable();
        hb.setColor(0xFF444444);
        hb.setCornerRadius(Ui.dp(c, 2));
        handle.setBackground(hb);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(Ui.dpi(c, 36), Ui.dpi(c, 4));
        hl.gravity = Gravity.CENTER_HORIZONTAL;
        card.addView(handle, hl);

        TextView t = new TextView(c);
        t.setText(title);
        t.setTypeface(Ui.dots(c));
        t.setTextSize(24);
        t.setTextColor(Ui.WHITE);
        t.setPadding(Ui.dpi(c, 20), Ui.dpi(c, 12), Ui.dpi(c, 20), Ui.dpi(c, 6));
        card.addView(t);

        ScrollView scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body);
        card.addView(scroll, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        cl.topMargin = Ui.dpi(c, 110); // never taller than most of the screen
        overlay.addView(card, cl);

        // Drag the card down to close
        card.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: downY = e.getRawY(); return true;
                case MotionEvent.ACTION_MOVE:
                    card.setTranslationY(Math.max(0, e.getRawY() - downY));
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (card.getTranslationY() > Ui.dp(c, 90)) dismiss();
                    else card.animate().translationY(0).setDuration(160).start();
                    return true;
                default: return true;
            }
        });

        spring = new Spring(450, 0.9f, 0f, v -> {
            card.setTranslationY((1 - v) * card.getHeight());
            dim.setAlpha(Ui.clamp(v, 0, 1));
        });
    }

    Sheet onClosed(Runnable r) { onClosed = r; return this; }

    void show() {
        host.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        if (Ui.motion(host.getContext()) == Ui.MOTION_FULL) {
            card.setTranslationY(Ui.dp(host.getContext(), 600));
            card.post(() -> spring.animateTo(1f, 0));
        } else {
            spring.set(1f);
        }
    }

    boolean isShowing() { return overlay.getParent() != null && !closing; }

    void dismiss() {
        if (closing) return;
        closing = true;
        Runnable done = () -> {
            host.removeView(overlay);
            if (onClosed != null) onClosed.run();
        };
        if (Ui.motion(host.getContext()) == Ui.MOTION_FULL) {
            card.animate().translationY(card.getHeight()).setDuration(180).start();
            overlay.animate().alpha(0).setDuration(200).withEndAction(done).start();
        } else {
            done.run();
        }
    }

    // ------------------------------------------------------------------ rows

    void section(String s) {
        Context c = host.getContext();
        TextView t = new TextView(c);
        t.setText(s);
        t.setTypeface(Ui.mono(c));
        t.setTextSize(9.5f);
        t.setLetterSpacing(0.18f);
        t.setTextColor(Ui.RED);
        t.setPadding(Ui.dpi(c, 20), Ui.dpi(c, 14), Ui.dpi(c, 20), Ui.dpi(c, 6));
        body.addView(t);
    }

    View row(Drawable icon, String title, String sub, Runnable onTap) {
        Context c = host.getContext();
        LinearLayout r = new LinearLayout(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(Ui.dpi(c, 20), Ui.dpi(c, 10), Ui.dpi(c, 20), Ui.dpi(c, 10));
        r.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x22FFFFFF), null, new android.graphics.drawable.ColorDrawable(Ui.WHITE)));
        if (icon != null) {
            ImageView iv = new ImageView(c);
            iv.setImageDrawable(icon);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            GradientDrawable ib = new GradientDrawable();
            ib.setColor(0xFF1A1A1A);
            ib.setCornerRadius(Ui.dp(c, 12));
            iv.setBackground(ib);
            iv.setPadding(Ui.dpi(c, 8), Ui.dpi(c, 8), Ui.dpi(c, 8), Ui.dpi(c, 8));
            iv.setClipToOutline(true);
            r.addView(iv, new LinearLayout.LayoutParams(Ui.dpi(c, 52), Ui.dpi(c, 52)));
        }
        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(icon != null ? Ui.dpi(c, 14) : 0, 0, 0, 0);
        TextView t = new TextView(c);
        t.setText(title);
        t.setTextColor(Ui.WHITE);
        t.setTextSize(15);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        text.addView(t);
        if (sub != null) {
            TextView s = new TextView(c);
            s.setText(sub);
            s.setTextColor(0xFF888888);
            s.setTextSize(12);
            text.addView(s);
        }
        r.addView(text, new LinearLayout.LayoutParams(0, -2, 1f));
        r.setOnClickListener(v -> {
            Ui.tap(v);
            dismiss();
            if (onTap != null) onTap.run();
        });
        body.addView(r);
        return r;
    }
}
