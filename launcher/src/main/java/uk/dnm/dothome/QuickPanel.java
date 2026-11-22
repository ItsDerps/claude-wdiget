package uk.dnm.dothome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The quick panel, opened from the right edge. Letters run down the edge in a gentle curve and
 * your thumb's height picks one directly, so every letter is in reach. That letter's apps sit in
 * a short list beside it: slide left onto one and let go to open it. Let go on a letter and the
 * panel stays open, so you can tap instead; tap outside to close.
 */
public class QuickPanel extends View {

    interface Host {
        void onDialPick(Apps.App app, RectF from);
        void onDialOpenChanged(boolean open);
    }

    private static final int MAX_ROWS = 7;

    private Host host;
    private final Map<Character, List<Apps.App>> groups = new LinkedHashMap<>();
    private final List<Character> letters = new ArrayList<>();
    private int letterIdx = -1, rowIdx = -1, rowScroll;
    private boolean dragging, sticky, appsMode;
    private final Spring open, card;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final TextPaint letterP = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint nameP = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint hintP = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tmp = new RectF(), cardRect = new RectF();

    public QuickPanel(Context c) {
        super(c);
        letterP.setTextAlign(Paint.Align.CENTER);
        nameP.setTextSize(Ui.sp(c, 15.5f));
        hintP.setTypeface(Ui.mono(c));
        hintP.setTextSize(Ui.sp(c, 9));
        hintP.setLetterSpacing(0.16f);
        hintP.setTextAlign(Paint.Align.CENTER);
        open = new Spring(520, 0.85f, 0f, v -> invalidate());
        card = new Spring(700, 0.75f, 0f, v -> invalidate());
        setVisibility(INVISIBLE);
    }

    void setHost(Host h) { host = h; }

    boolean isActive() { return dragging; }
    boolean isOpen() { return getVisibility() == VISIBLE && (dragging || sticky); }

    // ------------------------------------------------------------------ geometry

    private float top() { return getHeight() * 0.16f; }
    private float bottom() { return getHeight() * 0.86f; }

    private float letterY(int i) {
        if (letters.size() <= 1) return (top() + bottom()) / 2;
        return top() + (bottom() - top()) * i / (letters.size() - 1);
    }

    /** Letters bulge gently away from the edge in the middle, like a soft arc. */
    private float letterX(int i) {
        float t = letters.size() <= 1 ? 0.5f : i / (float) (letters.size() - 1);
        return getWidth() - Ui.dp(getContext(), 26) - (float) Math.sin(t * Math.PI) * Ui.dp(getContext(), 22);
    }

    private int letterAt(float y) {
        int best = 0;
        float bd = Float.MAX_VALUE;
        for (int i = 0; i < letters.size(); i++) {
            float d = Math.abs(letterY(i) - y);
            if (d < bd) { bd = d; best = i; }
        }
        return best;
    }

    private List<Apps.App> currentApps() {
        if (letterIdx < 0 || letterIdx >= letters.size()) return new ArrayList<>();
        return groups.get(letters.get(letterIdx));
    }

    private float rowH() { return Ui.dp(getContext(), 50); }

    /** The app card sits beside the letter, kept on screen. */
    private void layoutCard() {
        List<Apps.App> l = currentApps();
        int rows = Math.min(MAX_ROWS, l.size()) + (l.size() > MAX_ROWS ? 1 : 0);
        float h = rows * rowH() + Ui.dp(getContext(), 16);
        float w = Math.min(getWidth() * 0.68f, Ui.dp(getContext(), 270));
        float right = letterX(letterIdx) - Ui.dp(getContext(), 30);
        float cy = letterY(letterIdx);
        float t = Ui.clamp(cy - h / 2, Ui.dp(getContext(), 60), getHeight() - h - Ui.dp(getContext(), 60));
        cardRect.set(right - w, t, right, t + h);
    }

    private int rowAt(float y) {
        List<Apps.App> l = currentApps();
        int shown = Math.min(MAX_ROWS, l.size());
        int r = (int) ((y - cardRect.top - Ui.dp(getContext(), 8)) / rowH());
        return Ui.clampInt(r, 0, shown - 1);
    }

    // ------------------------------------------------------------------ driving

    void begin(List<Apps.App> apps, float rawY) {
        groups.clear();
        letters.clear();
        for (Apps.App a : apps) {
            List<Apps.App> l = groups.get(a.section);
            if (l == null) { l = new ArrayList<>(); groups.put(a.section, l); letters.add(a.section); }
            l.add(a);
        }
        if (letters.isEmpty()) return;
        dragging = true;
        sticky = false;
        appsMode = false;
        rowIdx = -1;
        rowScroll = 0;
        setVisibility(VISIBLE);
        int[] p = new int[2];
        getLocationOnScreen(p);
        letterIdx = letterAt(rawY - p[1]);
        layoutCard();
        if (Ui.motion(getContext()) == Ui.MOTION_OFF) { open.set(1); card.set(1); } else { open.animateTo(1, 0); card.set(0); card.animateTo(1, 0); }
        Ui.detent(this);
        if (host != null) host.onDialOpenChanged(true);
        invalidate();
    }

    void move(float rawX, float rawY) {
        if (!dragging) return;
        int[] p = new int[2];
        getLocationOnScreen(p);
        float x = rawX - p[0], y = rawY - p[1];
        track(x, y);
    }

    private void track(float x, float y) {
        boolean nowApps = x < letterX(letterIdx) - Ui.dp(getContext(), 40) && !currentApps().isEmpty();
        if (nowApps != appsMode) {
            appsMode = nowApps;
            Ui.tick(this);
        }
        if (appsMode) {
            int r = rowAt(y);
            if (r != rowIdx) {
                rowIdx = r;
                Ui.letter(this);
            }
        } else {
            rowIdx = -1;
            int i = letterAt(y);
            if (i != letterIdx) {
                letterIdx = i;
                layoutCard();
                if (Ui.motion(getContext()) == Ui.MOTION_FULL) { card.set(0.6f); card.animateTo(1, 0); }
                Ui.letter(this);
            }
        }
        invalidate();
    }

    void end(boolean pick) {
        if (!dragging) return;
        dragging = false;
        if (pick && appsMode && rowIdx >= 0) {
            launch(rowIdx);
        } else if (pick) {
            // Let go on a letter: stay open so you can tap
            sticky = true;
            appsMode = false;
            invalidate();
        } else {
            close();
        }
    }

    private void launch(int row) {
        List<Apps.App> l = currentApps();
        if (row < 0 || row >= l.size()) { close(); return; }
        float y = cardRect.top + Ui.dp(getContext(), 8) + row * rowH();
        RectF from = new RectF(cardRect.left + Ui.dp(getContext(), 12), y + Ui.dp(getContext(), 7),
                cardRect.left + Ui.dp(getContext(), 48), y + Ui.dp(getContext(), 43));
        Apps.App a = l.get(row);
        close();
        if (host != null) host.onDialPick(a, from);
    }

    void close() {
        dragging = false;
        sticky = false;
        if (host != null) host.onDialOpenChanged(false);
        if (Ui.motion(getContext()) == Ui.MOTION_OFF) {
            open.set(0);
            setVisibility(INVISIBLE);
        } else {
            open.animateTo(0, 0);
            postDelayed(() -> { if (!dragging && !sticky) setVisibility(INVISIBLE); }, 260);
        }
    }

    /** Sticky mode: taps choose letters and apps; a tap anywhere else closes. */
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!sticky) return false;
        float x = e.getX(), y = e.getY();
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) return true;
        if (e.getActionMasked() == MotionEvent.ACTION_UP) {
            if (cardRect.contains(x, y)) {
                int r = rowAt(y);
                Ui.tap(this);
                launch(r);
            } else if (x > getWidth() - Ui.dp(getContext(), 70) && y > top() - Ui.dp(getContext(), 20) && y < bottom() + Ui.dp(getContext(), 20)) {
                letterIdx = letterAt(y);
                layoutCard();
                if (Ui.motion(getContext()) == Ui.MOTION_FULL) { card.set(0.6f); card.animateTo(1, 0); }
                Ui.letter(this);
                invalidate();
            } else {
                close();
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void onDraw(Canvas c) {
        float o = Ui.clamp(open.value, 0, 1);
        if (o <= 0.002f || letters.isEmpty()) return;
        Context ctx = getContext();
        Theme th = Theme.get(ctx);

        fill.setColor(th.light ? 0xFFEDEDEA : 0xFF000000);
        fill.setAlpha((int) (200 * o));
        c.drawRect(0, 0, getWidth(), getHeight(), fill);

        // Letters
        float slide = (1 - o) * Ui.dp(ctx, 40);
        for (int i = 0; i < letters.size(); i++) {
            boolean on = i == letterIdx;
            float x = letterX(i) + slide, y = letterY(i);
            if (on) {
                fill.setColor(Ui.RED);
                fill.setAlpha((int) (255 * o));
                c.drawCircle(x - Ui.dp(ctx, 26), y, Ui.dp(ctx, 3), fill);
                letterP.setTypeface(Ui.dots(ctx));
                letterP.setTextSize(Ui.sp(ctx, 32));
                letterP.setColor(th.text);
            } else {
                letterP.setTypeface(Ui.mono(ctx));
                letterP.setTextSize(Ui.sp(ctx, 13));
                letterP.setColor(th.textMute);
            }
            letterP.setAlpha((int) (255 * o));
            c.drawText(String.valueOf(letters.get(i)), x, y + letterP.getTextSize() * 0.35f, letterP);
        }

        // That letter's apps
        List<Apps.App> l = currentApps();
        if (!l.isEmpty()) {
            float k = Ui.clamp(card.value, 0, 1.2f) * o;
            c.save();
            c.scale(0.9f + 0.1f * Math.min(1, k), 0.9f + 0.1f * Math.min(1, k), cardRect.right, letterY(letterIdx));
            fill.setColor(th.surface);
            fill.setAlpha((int) (255 * Math.min(1, k)));
            float r = Ui.dp(ctx, 24);
            c.drawRoundRect(cardRect, r, r, fill);
            fill.setStyle(Paint.Style.STROKE);
            fill.setStrokeWidth(Ui.dp(ctx, 1));
            fill.setColor(th.surfaceLine);
            fill.setAlpha((int) (255 * Math.min(1, k)));
            c.drawRoundRect(cardRect, r, r, fill);
            fill.setStyle(Paint.Style.FILL);
            int shown = Math.min(MAX_ROWS, l.size());
            for (int i = 0; i < shown; i++) {
                Apps.App a = l.get(i);
                float y = cardRect.top + Ui.dp(ctx, 8) + i * rowH();
                if (i == rowIdx) {
                    fill.setColor(th.light ? 0xFFEDEDEA : 0xFF242424);
                    fill.setAlpha((int) (255 * Math.min(1, k)));
                    tmp.set(cardRect.left + Ui.dp(ctx, 6), y + Ui.dp(ctx, 2), cardRect.right - Ui.dp(ctx, 6), y + rowH() - Ui.dp(ctx, 2));
                    c.drawRoundRect(tmp, Ui.dp(ctx, 16), Ui.dp(ctx, 16), fill);
                }
                float icon = Ui.dp(ctx, 36), ix = cardRect.left + Ui.dp(ctx, 14), iy = y + (rowH() - icon) / 2;
                fill.setColor(th.iconBg);
                fill.setAlpha((int) (255 * Math.min(1, k)));
                c.drawCircle(ix + icon / 2, iy + icon / 2, icon / 2, fill);
                if (a.icon != null) {
                    tmp.set(ix, iy, ix + icon, iy + icon);
                    bmp.setAlpha((int) (255 * Math.min(1, k)));
                    bmp.setColorFilter(th.light ? new android.graphics.PorterDuffColorFilter(th.glyph, android.graphics.PorterDuff.Mode.SRC_IN) : null);
                    c.drawBitmap(a.icon, null, tmp, bmp);
                }
                nameP.setColor(th.text);
                nameP.setTypeface(i == rowIdx ? Typeface.create("sans-serif-medium", Typeface.NORMAL) : Typeface.DEFAULT);
                nameP.setAlpha((int) (255 * Math.min(1, k)));
                float tx = ix + icon + Ui.dp(ctx, 12);
                CharSequence name = TextUtils.ellipsize(a.label, nameP, cardRect.right - tx - Ui.dp(ctx, 12), TextUtils.TruncateAt.END);
                c.drawText(name, 0, name.length(), tx, y + rowH() / 2 + nameP.getTextSize() * 0.35f, nameP);
            }
            if (l.size() > MAX_ROWS) {
                hintP.setColor(th.textMute);
                hintP.setAlpha((int) (255 * Math.min(1, k)));
                c.drawText("+" + (l.size() - MAX_ROWS) + " MORE IN THE DRAWER", cardRect.centerX(),
                        cardRect.top + Ui.dp(ctx, 8) + shown * rowH() + rowH() / 2, hintP);
            }
            c.restore();
        }

        hintP.setColor(th.textMute);
        hintP.setAlpha((int) (255 * o));
        String h = sticky ? "TAP AN APP · TAP OUTSIDE TO CLOSE" : appsMode ? "LET GO TO OPEN" : "SLIDE LEFT ONTO AN APP";
        c.drawText(h, getWidth() / 2f, getHeight() - Ui.dp(ctx, 64), hintP);
        if (open.isRunning() || card.isRunning()) postInvalidateOnAnimation();
    }
}
