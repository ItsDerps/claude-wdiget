package uk.dnm.dothome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * The home background: pure black with a faint dot grid that lights up under your finger, like
 * the Glyph. Every touch anywhere leaves a short-lived glow; drags leave a fading trail.
 */
public class DotGridView extends View {

    private static final long LIFE_MS = 650;

    private final float step, baseR;
    private final Paint grid = new Paint();
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<float[]> sources = new ArrayList<>(); // x, y, startTime, isTap
    private float lastX = -1, lastY = -1;
    private boolean enabledGlow = true;

    public DotGridView(Context c) {
        super(c);
        int s = Math.round(Ui.dp(c, 13));
        step = s; // whole pixels, so the glow lines up with the tiled dots
        baseR = Ui.dp(c, 1.1f);
        Bitmap tile = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(tile);
        cv.drawColor(Ui.BLACK);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFF121212);
        cv.drawCircle(s / 2f, s / 2f, baseR, p);
        grid.setShader(new BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
    }

    void setGlowEnabled(boolean on) { enabledGlow = on; }

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

    /** A burst of light at a point (e.g. where an app icon lands when you come back home). */
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

    @Override
    protected void onDraw(Canvas c) {
        c.drawRect(0, 0, getWidth(), getHeight(), grid);
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
                    // A tap spreads out as a ring; a drag just glows where it passed
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
                glow.setColor(red ? Ui.RED : Ui.WHITE);
                glow.setAlpha((int) (Math.min(1, best) * (red ? 255 : 200)));
                c.drawCircle(dx0, dy0, baseR * (1 + best * 0.9f), glow);
            }
        }
        postInvalidateOnAnimation();
    }
}
