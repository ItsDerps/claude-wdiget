package uk.dnm.dothome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** Small line icons drawn on a 24-unit grid, in the same style as the design preview. */
final class Glyph extends Drawable {

    static final int SEARCH = 0, CLEAR = 1, CHEVRON = 2, INFO = 3, HOME_ADD = 4, HOME_REMOVE = 5,
            DOCK = 6, HIDE = 7, REMOVE = 8, TIMER = 9, ALARM = 10, SETTINGS = 11, WEB = 12, CALL = 13, COPY = 14;

    private final int kind;
    private final int size;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    Glyph(int kind, int sizePx, int color) {
        this.kind = kind;
        this.size = sizePx;
        p.setColor(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeWidth(sizePx / 24f * 1.7f);
        setBounds(0, 0, sizePx, sizePx);
    }

    @Override public int getIntrinsicWidth() { return size; }
    @Override public int getIntrinsicHeight() { return size; }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        c.save();
        c.translate(b.left, b.top);
        float u = b.width() / 24f;
        c.scale(u, u);
        p.setStrokeWidth(1.7f);
        path.reset();
        switch (kind) {
            case SEARCH:
                c.drawCircle(11, 11, 6.5f, p);
                c.drawLine(16, 16, 20.5f, 20.5f, p);
                break;
            case CLEAR:
                c.drawLine(7, 7, 17, 17, p);
                c.drawLine(17, 7, 7, 17, p);
                break;
            case CHEVRON:
                path.moveTo(9, 5); path.lineTo(16, 12); path.lineTo(9, 19);
                c.drawPath(path, p);
                break;
            case INFO:
                c.drawCircle(12, 12, 8.5f, p);
                c.drawLine(12, 11, 12, 16, p);
                c.drawPoint(12, 8, p);
                break;
            case HOME_ADD:
            case HOME_REMOVE:
                path.moveTo(4, 11); path.lineTo(12, 4); path.lineTo(20, 11);
                path.moveTo(6, 9.5f); path.lineTo(6, 20); path.lineTo(18, 20); path.lineTo(18, 9.5f);
                c.drawPath(path, p);
                c.drawLine(9, 14.5f, 15, 14.5f, p);
                if (kind == HOME_ADD) c.drawLine(12, 11.5f, 12, 17.5f, p);
                break;
            case DOCK:
                c.drawRoundRect(3, 14, 21, 20, 3, 3, p);
                c.drawCircle(8, 17, 0.6f, p);
                c.drawCircle(12, 17, 0.6f, p);
                c.drawCircle(16, 17, 0.6f, p);
                c.drawLine(12, 4, 12, 10.5f, p);
                c.drawLine(9, 7.5f, 12, 10.5f, p);
                c.drawLine(15, 7.5f, 12, 10.5f, p);
                break;
            case HIDE:
                path.moveTo(3, 12); path.quadTo(12, 3, 21, 12); path.quadTo(12, 21, 3, 12);
                c.drawPath(path, p);
                c.drawCircle(12, 12, 2.5f, p);
                c.drawLine(4, 20, 20, 4, p);
                break;
            case REMOVE:
                c.drawLine(5, 7, 19, 7, p);
                path.moveTo(10, 7); path.lineTo(10, 4.5f); path.lineTo(14, 4.5f); path.lineTo(14, 7);
                path.moveTo(7, 7); path.lineTo(8, 20); path.lineTo(16, 20); path.lineTo(17, 7);
                c.drawPath(path, p);
                break;
            case TIMER:
                c.drawCircle(12, 13, 7.5f, p);
                c.drawLine(12, 13, 12, 9, p);
                c.drawLine(10, 2.5f, 14, 2.5f, p);
                break;
            case ALARM:
                c.drawCircle(12, 13, 7.5f, p);
                c.drawLine(12, 13, 12, 9.5f, p);
                c.drawLine(12, 13, 14.5f, 14.5f, p);
                c.drawLine(4, 6, 7, 3.5f, p);
                c.drawLine(20, 6, 17, 3.5f, p);
                break;
            case SETTINGS:
                c.drawCircle(12, 12, 3, p);
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    c.drawLine(12 + (float) Math.cos(a) * 6.5f, 12 + (float) Math.sin(a) * 6.5f,
                            12 + (float) Math.cos(a) * 9f, 12 + (float) Math.sin(a) * 9f, p);
                }
                break;
            case WEB:
                c.drawCircle(12, 12, 8.5f, p);
                c.drawLine(3.5f, 12, 20.5f, 12, p);
                c.drawOval(8.5f, 3.5f, 15.5f, 20.5f, p);
                break;
            case CALL:
                path.moveTo(6.5f, 3.5f); path.lineTo(9.5f, 3.5f); path.lineTo(11, 7.5f); path.lineTo(9, 8.8f);
                path.cubicTo(10.2f, 11.6f, 12.4f, 13.8f, 15.2f, 15f);
                path.lineTo(16.5f, 13); path.lineTo(20.5f, 14.5f); path.lineTo(20.5f, 17.5f);
                path.cubicTo(20.5f, 18.6f, 19.6f, 19.5f, 18.5f, 19.5f);
                path.cubicTo(10.7f, 19, 5, 13.3f, 4.5f, 5.5f);
                path.cubicTo(4.5f, 4.4f, 5.4f, 3.5f, 6.5f, 3.5f);
                c.drawPath(path, p);
                break;
            case COPY:
                c.drawRoundRect(8, 8, 20, 20, 2.5f, 2.5f, p);
                path.moveTo(16, 8); path.lineTo(16, 5.5f); path.quadTo(16, 4, 14.5f, 4);
                path.lineTo(5.5f, 4); path.quadTo(4, 4, 4, 5.5f); path.lineTo(4, 14.5f);
                path.quadTo(4, 16, 5.5f, 16); path.lineTo(8, 16);
                c.drawPath(path, p);
                break;
            default:
                break;
        }
        c.restore();
    }

    @Override public void setAlpha(int a) { p.setAlpha(a); }
    @Override public void setColorFilter(ColorFilter cf) { p.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
