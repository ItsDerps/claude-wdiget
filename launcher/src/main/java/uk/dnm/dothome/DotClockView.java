package uk.dnm.dothome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.text.format.DateFormat;
import android.view.View;

import java.util.Calendar;
import java.util.Random;

/**
 * The home clock: a real 5 × 7 dot matrix with faintly visible unlit dots.
 * On unlock, the lit dots fly in from scattered points and click into the time.
 */
public class DotClockView extends View {

    private static final String[] DIGITS = {
            "01110100011001110101110011000101110", // 0
            "00100011000010000100001000010001110", // 1
            "01110100010000100010001000100011111", // 2
            "11111000100010000010000011000101110", // 3
            "00010001100101010010111110001000010", // 4
            "11111100001111000001000011000101110", // 5
            "00110010001000011110100011000101110", // 6
            "11111000010001000100010000100001000", // 7
            "01110100011000101110100011000101110", // 8
            "01110100011000101111000010001001100", // 9
    };

    private final Paint lit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint unlit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String time = "00:00";
    private float maxPitch;
    private long assembleStart = -1;
    private float[] scatterX = new float[0], scatterY = new float[0];

    public DotClockView(Context c) {
        super(c);
        lit.setColor(Ui.WHITE);
        unlit.setColor(0xFF161616);
        maxPitch = Ui.dp(c, 9.4f);
        update();
    }

    void applyTheme() {
        Theme t = Theme.get(getContext());
        lit.setColor(t.text);
        unlit.setColor(t.light ? 0xFFDEDED9 : 0xFF161616);
        invalidate();
    }

    /** Re-read the time (call each minute). */
    void update() {
        Calendar cal = Calendar.getInstance();
        int h = cal.get(Calendar.HOUR_OF_DAY);
        if (!DateFormat.is24HourFormat(getContext())) {
            h = h % 12;
            if (h == 0) h = 12;
        }
        String t = String.format(java.util.Locale.ROOT, "%02d:%02d", h, cal.get(Calendar.MINUTE));
        if (!t.equals(time)) {
            time = t;
            invalidate();
        }
    }

    /** Dots fly together into the time. Skipped when motion is Calm or Off. */
    void assemble() {
        if (Ui.motion(getContext()) != Ui.MOTION_FULL) return;
        Random r = new Random(SystemClock.uptimeMillis());
        int n = 4 * 35 + 7;
        scatterX = new float[n];
        scatterY = new float[n];
        float spread = getWidth() * 0.45f;
        for (int i = 0; i < n; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            float d = spread * (0.35f + r.nextFloat() * 0.8f);
            scatterX[i] = (float) Math.cos(a) * d;
            scatterY[i] = (float) Math.sin(a) * d * 0.6f;
        }
        assembleStart = SystemClock.uptimeMillis();
        invalidate();
    }

    private float pitch(int width) {
        // 4 digits × 5 columns + colon + 5 gaps = 26 columns
        return Math.min(maxPitch, width / 26f);
    }

    @Override
    protected void onMeasure(int w, int h) {
        int width = MeasureSpec.getSize(w);
        float p = pitch(width);
        setMeasuredDimension(width, (int) Math.ceil(p * 7));
    }

    @Override
    protected void onDraw(Canvas c) {
        float p = pitch(getWidth()), r = p * 0.36f;
        long now = SystemClock.uptimeMillis();
        boolean animating = assembleStart >= 0;
        float x = 0;
        int dot = 0;
        for (int ci = 0; ci < time.length(); ci++) {
            char ch = time.charAt(ci);
            int cols = ch == ':' ? 1 : 5;
            for (int row = 0; row < 7; row++) {
                for (int col = 0; col < cols; col++) {
                    boolean on = ch == ':' ? (row == 2 || row == 4) : DIGITS[ch - '0'].charAt(row * 5 + col) == '1';
                    float cx = x + col * p + p / 2, cy = row * p + p / 2;
                    if (!on) {
                        c.drawCircle(cx, cy, r, unlit);
                        continue;
                    }
                    if (animating && dot < scatterX.length) {
                        float t = Ui.clamp((now - assembleStart - dot * 3f) / 480f, 0, 1);
                        float e = 1 - (1 - t) * (1 - t) * (1 - t);
                        lit.setAlpha((int) (255 * (0.25f + 0.75f * e)));
                        c.drawCircle(cx + scatterX[dot] * (1 - e), cy + scatterY[dot] * (1 - e), r, lit);
                    } else {
                        lit.setAlpha(255);
                        if (ch == ':') {
                            // The colon is red: a small Nothing signature
                            int keep = lit.getColor();
                            lit.setColor(Ui.RED);
                            c.drawCircle(cx, cy, r, lit);
                            lit.setColor(keep);
                        } else {
                            c.drawCircle(cx, cy, r, lit);
                        }
                    }
                    dot++;
                }
            }
            x += (cols + 1) * p;
        }
        lit.setAlpha(255);
        if (animating) {
            if (now - assembleStart > 480 + dot * 3 + 20) assembleStart = -1;
            postInvalidateOnAnimation();
        }
    }
}
