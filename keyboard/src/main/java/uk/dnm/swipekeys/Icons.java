package uk.dnm.swipekeys;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/** Small line icons drawn straight onto a canvas, sized by {@code s} (roughly the icon's width). */
final class Icons {
    private Icons() { }

    private static final Path path = new Path();
    private static final RectF r = new RectF();

    static Paint stroke(Paint p, int color, float width) {
        p.setColor(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(width);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        return p;
    }

    static void smiley(Canvas c, Paint p, float cx, float cy, float s) {
        float rad = s / 2;
        c.drawCircle(cx, cy, rad, p);
        Paint.Style st = p.getStyle();
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(cx - rad * 0.36f, cy - rad * 0.22f, rad * 0.11f, p);
        c.drawCircle(cx + rad * 0.36f, cy - rad * 0.22f, rad * 0.11f, p);
        p.setStyle(st);
        r.set(cx - rad * 0.5f, cy - rad * 0.35f, cx + rad * 0.5f, cy + rad * 0.55f);
        c.drawArc(r, 25, 130, false, p);
    }

    static void mic(Canvas c, Paint p, float cx, float cy, float s) {
        float w = s * 0.36f, top = cy - s * 0.55f, bottom = cy + s * 0.12f;
        r.set(cx - w / 2, top, cx + w / 2, bottom);
        c.drawRoundRect(r, w / 2, w / 2, p);
        r.set(cx - s * 0.36f, cy - s * 0.32f, cx + s * 0.36f, cy + s * 0.3f);
        c.drawArc(r, 0, 180, false, p);
        c.drawLine(cx, cy + s * 0.3f, cx, cy + s * 0.5f, p);
        c.drawLine(cx - s * 0.18f, cy + s * 0.5f, cx + s * 0.18f, cy + s * 0.5f, p);
    }

    /** A sheet with a folded corner and lines: notes / clipboard. */
    static void notes(Canvas c, Paint p, float cx, float cy, float s) {
        float w = s * 0.4f, h = s * 0.5f, f = s * 0.2f;
        path.reset();
        path.moveTo(cx - w, cy - h);
        path.lineTo(cx + w - f, cy - h);
        path.lineTo(cx + w, cy - h + f);
        path.lineTo(cx + w, cy + h);
        path.lineTo(cx - w, cy + h);
        path.close();
        c.drawPath(path, p);
        for (int i = 0; i < 3; i++) {
            float y = cy - h * 0.25f + i * h * 0.38f;
            c.drawLine(cx - w * 0.55f, y, cx + w * (i == 2 ? 0.1f : 0.55f), y, p);
        }
    }

    /** A wide keyboard shrinking into a narrow one: one-handed mode. */
    static void oneHand(Canvas c, Paint p, float cx, float cy, float s) {
        float w = s * 0.5f, h = s * 0.36f;
        r.set(cx - w, cy - h, cx + w, cy + h);
        c.drawRoundRect(r, s * 0.08f, s * 0.08f, p);
        Paint.Style st = p.getStyle();
        p.setStyle(Paint.Style.FILL);
        r.set(cx - w + s * 0.12f, cy - h + s * 0.12f, cx + s * 0.1f, cy + h - s * 0.12f);
        c.drawRoundRect(r, s * 0.04f, s * 0.04f, p);
        p.setStyle(st);
    }

    /** Four squares: open the tools. */
    static void grid(Canvas c, Paint p, float cx, float cy, float s) {
        float g = s * 0.12f, b = s * 0.3f;
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                float x = cx + (i == 0 ? -g / 2 - b : g / 2);
                float y = cy + (j == 0 ? -g / 2 - b : g / 2);
                r.set(x, y, x + b, y + b);
                c.drawRoundRect(r, s * 0.06f, s * 0.06f, p);
            }
        }
    }

    /** Three slider lines: settings. */
    static void sliders(Canvas c, Paint p, float cx, float cy, float s) {
        float w = s * 0.5f;
        float[] knobs = {0.3f, -0.35f, 0.1f};
        for (int i = 0; i < 3; i++) {
            float y = cy + (i - 1) * s * 0.32f;
            c.drawLine(cx - w, y, cx + w, y, p);
            Paint.Style st = p.getStyle();
            p.setStyle(Paint.Style.FILL);
            c.drawCircle(cx + knobs[i] * w * 2 * 0.5f, y, s * 0.09f, p);
            p.setStyle(st);
        }
    }

    static void chevronLeft(Canvas c, Paint p, float cx, float cy, float s) {
        float h = s * 0.4f;
        path.reset();
        path.moveTo(cx + h * 0.5f, cy - h);
        path.lineTo(cx - h * 0.5f, cy);
        path.lineTo(cx + h * 0.5f, cy + h);
        c.drawPath(path, p);
    }

    static void chevronRight(Canvas c, Paint p, float cx, float cy, float s) {
        float h = s * 0.4f;
        path.reset();
        path.moveTo(cx - h * 0.5f, cy - h);
        path.lineTo(cx + h * 0.5f, cy);
        path.lineTo(cx - h * 0.5f, cy + h);
        c.drawPath(path, p);
    }

    /** Arrows pointing out to both sides: back to the full-width keyboard. */
    static void expand(Canvas c, Paint p, float cx, float cy, float s) {
        float w = s * 0.5f, a = s * 0.2f;
        c.drawLine(cx - w, cy, cx + w, cy, p);
        path.reset();
        path.moveTo(cx - w + a, cy - a);
        path.lineTo(cx - w, cy);
        path.lineTo(cx - w + a, cy + a);
        path.moveTo(cx + w - a, cy - a);
        path.lineTo(cx + w, cy);
        path.lineTo(cx + w - a, cy + a);
        c.drawPath(path, p);
    }

    static void backspace(Canvas c, Paint p, float cx, float cy, float s) {
        float w2 = s * 0.5f, h2 = s * 0.34f, tip = s * 0.27f;
        path.reset();
        path.moveTo(cx - w2, cy);
        path.lineTo(cx - w2 + tip, cy - h2);
        path.lineTo(cx + w2, cy - h2);
        path.lineTo(cx + w2, cy + h2);
        path.lineTo(cx - w2 + tip, cy + h2);
        path.close();
        c.drawPath(path, p);
        float xc = cx + s * 0.11f, d = s * 0.14f;
        c.drawLine(xc - d, cy - d, xc + d, cy + d, p);
        c.drawLine(xc - d, cy + d, xc + d, cy - d, p);
    }

    static void clock(Canvas c, Paint p, float cx, float cy, float s) {
        c.drawCircle(cx, cy, s / 2, p);
        c.drawLine(cx, cy, cx, cy - s * 0.3f, p);
        c.drawLine(cx, cy, cx + s * 0.22f, cy + s * 0.1f, p);
    }

    /** An eye; crossed out when {@code off}. Keyboard memory on/off. */
    static void eye(Canvas c, Paint p, float cx, float cy, float s, boolean off) {
        float w = s / 2, h = s * 0.32f;
        path.reset();
        path.moveTo(cx - w, cy);
        path.quadTo(cx, cy - h * 2, cx + w, cy);
        path.quadTo(cx, cy + h * 2, cx - w, cy);
        path.close();
        c.drawPath(path, p);
        c.drawCircle(cx, cy, s * 0.15f, p);
        if (off) c.drawLine(cx - w * 0.85f, cy + w * 0.85f, cx + w * 0.85f, cy - w * 0.85f, p);
    }
}
