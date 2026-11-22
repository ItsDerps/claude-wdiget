package uk.dnm.dothome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * The home background: a halftone dot field. Dots swell into the Phone (2)'s Glyph ring and a
 * soft "horizon" rising from the bottom, and every touch lights the dots under your finger.
 */
public class DotGridView extends View {

    private static final long LIFE_MS = 650;

    private final float step, baseR;
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final List<float[]> sources = new ArrayList<>(); // x, y, startTime, isTap
    private float lastX = -1, lastY = -1;
    private boolean enabledGlow = true;
    private Bitmap field;
    private int fieldTheme = -1;

    public DotGridView(Context c) {
        super(c);
        step = Math.round(Ui.dp(c, 13));
        baseR = Ui.dp(c, 1.05f);
    }

    void setGlowEnabled(boolean on) { enabledGlow = on; }

    /** Redraw the halftone field (theme changed). */
    void refreshTheme() {
        fieldTheme = -1;
        invalidate();
    }

    /** Feed every touch on the screen (in this view's coordinates). */
    void touch(MotionEvent e) {
        if (!enabledGlow) return;
        int a = e.getActionMasked();
        long now = SystemClock.uptimeMillis();
        if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_POINTER_DOWN) {
            int i = e.getActionIndex();
            add(e.getX(i), e.getY(i), now, true);
        } else if (a == MotionEvent.ACTION_MOVE) {
            float x = e.getX(), y = e.getY();
            if (Math.hypot(x - lastX, y - lastY) > step * 0.9f) add(x, y, now, false);
        }
    }

    void burst(float x, float y) {
        if (enabledGlow) add(x, y, SystemClock.uptimeMillis(), true);
    }

    private void add(float x, float y, long t, boolean tap) {
        lastX = x;
        lastY = y;
        sources.add(new float[]{x, y, t, tap ? 1 : 0});
        while (sources.size() > 28) sources.remove(0);
        postInvalidateOnAnimation();
    }

    /** 0..1: how much a dot at (x, y) swells. The Glyph ring plus a rising horizon. */
    private float fieldAt(float x, float y, int w, int h) {
        float cx = w * 0.80f, cy = h * 0.36f, R = w * 0.34f, band = w * 0.028f;
        float d = (float) Math.hypot(x - cx, y - cy);
        float ring = (float) Math.exp(-Math.pow((d - R) / band, 2));
        // A gap in the ring at the lower left, like the Phone (2)'s Glyph
        double ang = Math.atan2(y - cy, x - cx);
        if (ang > 1.9 && ang < 2.5) ring *= 0.15f;
        float horizon = Ui.clamp((y / h - 0.58f) / 0.42f, 0, 1);
        horizon = horizon * horizon * 0.55f;
        return Math.max(ring * 0.9f, horizon);
    }

    private void buildField(Theme t) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        if (field != null && (field.getWidth() != w || field.getHeight() != h)) field = null;
        if (field == null) field = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(field);
        c.drawColor(t.bg);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (float x = step / 2; x < w; x += step) {
            for (float y = step / 2; y < h; y += step) {
                float f = fieldAt(x, y, w, h);
                p.setColor(blend(t.dot, t.dotStrong, f));
                c.drawCircle(x, y, baseR * (0.85f + 1.25f * f), p);
            }
        }
        fieldTheme = t.id;
    }

    private static int blend(int a, int b, float f) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000 | ((int) (ar + (br - ar) * f) << 16) | ((int) (ag + (bg - ag) * f) << 8) | (int) (ab + (bb - ab) * f);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        fieldTheme = -1;
    }

    @Override
    protected void onDraw(Canvas c) {
        Theme t = Theme.get(getContext());
        if (fieldTheme != t.id || field == null) buildField(t);
        if (field != null) c.drawBitmap(field, 0, 0, bmp);
        if (sources.isEmpty()) return;
        long now = SystemClock.uptimeMillis();
        for (int k = sources.size() - 1; k >= 0; k--) if (now - sources.get(k)[2] > LIFE_MS) sources.remove(k);
        if (sources.isEmpty()) return;

        float reach = Ui.dp(getContext(), 64);
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -1, maxY = -1;
        for (float[] s : sources) {
            minX = Math.min(minX, s[0] - reach);
            minY = Math.min(minY, s[1] - reach);
            maxX = Math.max(maxX, s[0] + reach);
            maxY = Math.max(maxY, s[1] + reach);
        }
        int i0 = Math.max(0, (int) (minX / step)), i1 = (int) (maxX / step) + 1;
        int j0 = Math.max(0, (int) (minY / step)), j1 = (int) (maxY / step) + 1;
        for (int i = i0; i <= i1; i++) {
            float dx0 = i * step + step / 2;
            for (int j = j0; j <= j1; j++) {
                float dy0 = j * step + step / 2;
                float best = 0;
                boolean red = false;
                for (float[] s : sources) {
                    float age = (now - s[2]) / (float) LIFE_MS;
                    float d = (float) Math.hypot(dx0 - s[0], dy0 - s[1]);
                    float v;
                    if (s[3] == 1) {
                        float ring = reach * (0.25f + 0.75f * age);
                        float off = Math.abs(d - ring) / (reach * 0.35f);
                        v = Math.max(0, 1 - off) * (1 - age);
                        if (d < step * 0.75f && age < 0.35f) { v = 1; red = true; }
                    } else {
                        v = Math.max(0, 1 - d / (reach * 0.7f));
                        v = v * v * (1 - age);
                    }
                    if (v > best) best = v;
                }
                if (best < 0.04f) continue;
                glow.setColor(red ? Ui.RED : t.glow);
                glow.setAlpha((int) (Math.min(1, best) * (red ? 255 : t.light ? 150 : 200)));
                c.drawCircle(dx0, dy0, baseR * (1 + best * 0.9f), glow);
            }
        }
        postInvalidateOnAnimation();
    }
}
