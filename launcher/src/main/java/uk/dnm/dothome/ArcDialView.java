package uk.dnm.dothome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import java.util.List;

/**
 * The app dial: a half circle that slides out from the right edge with every app's name around
 * its rim. Rolling the thumb up or down turns the dial (faster thumb = bigger jumps); the app at
 * the centre is highlighted. Let go to open it, or slide back to the edge to cancel.
 */
public class ArcDialView extends View {

    interface Host {
        void onDialPick(Apps.App app, RectF from);
        void onDialOpenChanged(boolean open);
    }

    private static final float STEP_DEG = 11f;      // angle between neighbouring apps
    private static final float VISIBLE_DEG = 78f;

    private Host host;
    private List<Apps.App> apps = new java.util.ArrayList<>();
    private float pos;                // current (fractional) index at the centre
    private int lastTick = -1;
    private float lastY;
    private long lastT;
    private boolean active, cancelZone;
    private final Spring open, settle;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final TextPaint name = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint letter = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint hint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tmp = new RectF();

    public ArcDialView(Context c) {
        super(c);
        name.setTextAlign(Paint.Align.LEFT);
        letter.setTypeface(Ui.dots(c));
        letter.setTextAlign(Paint.Align.CENTER);
        hint.setTypeface(Ui.mono(c));
        hint.setTextSize(Ui.sp(c, 9));
        hint.setLetterSpacing(0.16f);
        hint.setTextAlign(Paint.Align.CENTER);
        hint.setColor(0xFF777777);
        open = new Spring(520, 0.78f, 0f, v -> invalidate());
        settle = new Spring(700, 0.9f, 0f, v -> { pos = v; invalidate(); });
        setVisibility(INVISIBLE);
    }

    void setHost(Host h) { host = h; }

    boolean isActive() { return active; }

    private float radius() { return Math.min(getWidth() * 0.66f, Ui.dp(getContext(), 290)); }
    private float cx() { return getWidth(); }
    private float cy() { return getHeight() * 0.55f; }

    /** Start the dial. {@code startFraction} (0..1) picks roughly where in A–Z to begin. */
    void begin(List<Apps.App> list, float startFraction, float rawY) {
        if (list.isEmpty()) return;
        apps = list;
        settle.cancel();
        pos = Math.round(Ui.clamp(startFraction, 0, 1) * (apps.size() - 1));
        lastTick = Math.round(pos);
        lastY = rawY;
        lastT = SystemClock.uptimeMillis();
        active = true;
        cancelZone = false;
        setVisibility(VISIBLE);
        if (Ui.motion(getContext()) == Ui.MOTION_OFF) open.set(1f); else open.animateTo(1f, 0);
        Ui.detent(this);
        if (host != null) host.onDialOpenChanged(true);
        invalidate();
    }

    void move(float rawX, float rawY) {
        if (!active) return;
        long now = SystemClock.uptimeMillis();
        float dy = rawY - lastY;
        float dt = Math.max(1, now - lastT);
        float speed = Math.abs(dy) / dt * 1000f / Ui.dp(getContext(), 1); // dp per second
        // Slow thumb = one app at a time; fast thumb = big jumps
        float accel = 1f + Ui.clamp((speed - 250f) / 900f, 0, 3.5f);
        pos = Ui.clamp(pos + dy / Ui.dp(getContext(), 26) * accel, 0, apps.size() - 1);
        lastY = rawY;
        lastT = now;
        int i = Math.round(pos);
        if (i != lastTick) {
            lastTick = i;
            Ui.letter(this);
        }
        boolean cz = rawX > getWidth() - Ui.dp(getContext(), 14);
        if (cz != cancelZone) {
            cancelZone = cz;
            Ui.tick(this);
        }
        invalidate();
    }

    void end(boolean pick) {
        if (!active) return;
        active = false;
        int i = Math.round(pos);
        boolean launch = pick && !cancelZone && i >= 0 && i < apps.size();
        RectF from = new RectF();
        if (launch) {
            float r = radius();
            float iconR = Ui.dp(getContext(), 26);
            float x = cx() - r;
            from.set(x - iconR, cy() - iconR, x + iconR, cy() + iconR);
        }
        if (Ui.motion(getContext()) == Ui.MOTION_OFF) {
            open.set(0f);
            setVisibility(INVISIBLE);
        } else {
            open.stiffness = 600;
            open.damping = 1f;
            open.animateTo(0f, 0);
            postDelayed(() -> { if (!active) setVisibility(INVISIBLE); open.stiffness = 520; open.damping = 0.78f; }, 320);
        }
        if (host != null) host.onDialOpenChanged(false);
        if (launch && host != null) host.onDialPick(apps.get(i), from);
    }

    @Override
    protected void onDraw(Canvas c) {
        float o = Ui.clamp(open.value, 0, 1.2f);
        if (o <= 0.002f || apps.isEmpty()) return;
        Context ctx = getContext();
        float R = radius() * Math.min(1f, o) * (o > 1 ? 1 + (o - 1) * 0.15f : 1);
        float cx = cx(), cy = cy();

        // Dim what's behind
        fill.setColor(0xFF000000);
        fill.setAlpha((int) (170 * Math.min(1, o)));
        c.drawRect(0, 0, getWidth(), getHeight(), fill);

        // The half disc, with a dotted rim
        fill.setColor(0xFF0C0C0C);
        fill.setAlpha(255);
        c.drawCircle(cx, cy, R, fill);
        for (float a = 90; a <= 270; a += 3.5f) {
            double rad = Math.toRadians(a);
            float px = cx + (float) Math.cos(rad) * R, py = cy + (float) Math.sin(rad) * R;
            boolean centre = Math.abs(a - 180) < 1.8f;
            dot.setColor(centre ? Ui.RED : 0xFF2C2C2C);
            c.drawCircle(px, py, Ui.dp(ctx, centre ? 2.4f : 1.3f), dot);
        }

        int centreIdx = Math.round(pos);
        // The highlighted app's letter, big and faint inside the dial
        Apps.App cur = apps.get(Ui.clampInt(centreIdx, 0, apps.size() - 1));
        letter.setTextSize(R * 0.42f);
        letter.setColor(0xFF1E1E1E);
        c.drawText(String.valueOf(cur.section), cx - R * 0.32f, cy + letter.getTextSize() * 0.35f, letter);

        float textAlpha = Ui.clamp((o - 0.35f) / 0.5f, 0, 1);
        int first = (int) Math.floor(pos - VISIBLE_DEG / STEP_DEG) - 1;
        int last = (int) Math.ceil(pos + VISIBLE_DEG / STEP_DEG) + 1;
        float iconMax = Ui.dp(ctx, 46), iconMin = Ui.dp(ctx, 26);
        for (int i = Math.max(0, first); i <= Math.min(apps.size() - 1, last); i++) {
            float deg = (i - pos) * STEP_DEG;
            if (Math.abs(deg) > VISIBLE_DEG + STEP_DEG) continue;
            float focus = 1f - Ui.clamp(Math.abs(deg) / (STEP_DEG * 1.2f), 0, 1);          // 1 at the centre
            float fade = 1f - Ui.clamp((Math.abs(deg) - VISIBLE_DEG * 0.55f) / (VISIBLE_DEG * 0.45f), 0, 1);
            double rad = Math.toRadians(180 + deg);
            float rimX = cx + (float) Math.cos(rad) * R, rimY = cy + (float) Math.sin(rad) * R;
            Apps.App a = apps.get(i);

            // Icon sits on the rim
            float s = Ui.lerp(iconMin, iconMax, focus);
            fill.setColor(focus > 0.5f ? 0xFF222222 : Ui.ICON_BG);
            fill.setAlpha((int) (255 * fade * textAlpha));
            c.drawCircle(rimX, rimY, s / 2, fill);
            if (focus > 0.5f) {
                dot.setColor(Ui.WHITE);
                dot.setAlpha((int) (255 * textAlpha));
                dot.setStyle(Paint.Style.STROKE);
                dot.setStrokeWidth(Ui.dp(ctx, 1.5f));
                c.drawCircle(rimX, rimY, s / 2, dot);
                dot.setStyle(Paint.Style.FILL);
            }
            if (a.icon != null) {
                tmp.set(rimX - s / 2, rimY - s / 2, rimX + s / 2, rimY + s / 2);
                bmp.setAlpha((int) (255 * fade * textAlpha));
                c.drawBitmap(a.icon, null, tmp, bmp);
            }

            // Name runs inward from the rim, along the dial's spoke
            c.save();
            c.translate(rimX, rimY);
            c.rotate(deg);
            name.setTextSize(Ui.lerp(Ui.sp(ctx, 13), Ui.sp(ctx, 19), focus));
            name.setTypeface(focus > 0.5f ? android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                    : android.graphics.Typeface.DEFAULT);
            int col = focus > 0.5f ? Ui.WHITE : 0xFF8A8A8A;
            name.setColor(col);
            name.setAlpha((int) (255 * fade * textAlpha));
            float maxW = R * 0.78f - s / 2;
            CharSequence label = TextUtils.ellipsize(a.label, name, maxW, TextUtils.TruncateAt.END);
            c.drawText(label, 0, label.length(), s / 2 + Ui.dp(ctx, 10), name.getTextSize() * 0.35f, name);
            c.restore();
        }

        hint.setAlpha((int) (255 * textAlpha));
        String h = cancelZone ? "LET GO TO CANCEL" : "ROLL TO TURN · LET GO TO OPEN";
        hint.setColor(cancelZone ? Ui.RED : 0xFF777777);
        c.drawText(h, getWidth() / 2f, getHeight() - Ui.dp(ctx, 70), hint);
        if (open.isRunning()) postInvalidateOnAnimation();
    }
}
