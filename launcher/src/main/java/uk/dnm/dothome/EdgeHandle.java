package uk.dnm.dothome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * A thin strip on the right edge, marked by a column of faint dots. Touch it and the app dial
 * slides out; roll your thumb to turn it. It's excluded from the back gesture so the swipe
 * reaches the dial instead.
 */
public class EdgeHandle extends View {

    private final ArcDialView dial;
    private final Supplier<List<Apps.App>> apps;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean touching;
    private final Rect exclusion = new Rect();

    public EdgeHandle(Context c, ArcDialView dial, Supplier<List<Apps.App>> apps) {
        super(c);
        this.dial = dial;
        this.apps = apps;
        setContentDescription("App dial: slide your thumb here");
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        exclusion.set(0, 0, r - l, b - t);
        setSystemGestureExclusionRects(Collections.singletonList(exclusion));
    }

    @Override
    protected void onDraw(Canvas c) {
        float x = getWidth() - Ui.dp(getContext(), 7), mid = getHeight() / 2f, gap = Ui.dp(getContext(), 9);
        for (int i = -3; i <= 3; i++) {
            float fade = 1f - Math.abs(i) / 4f;
            int base = touching ? 0xFFFFFF : 0x555555;
            p.setColor(0xFF000000 | base);
            p.setAlpha((int) (255 * fade * (touching ? 1f : 0.7f)));
            c.drawCircle(x, mid + i * gap, Ui.dp(getContext(), i == 0 ? 2f : 1.5f), p);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                getParent().requestDisallowInterceptTouchEvent(true);
                touching = true;
                invalidate();
                float frac = e.getY() / Math.max(1, getHeight());
                dial.begin(apps.get(), frac, e.getRawY());
                return true;
            }
            case MotionEvent.ACTION_MOVE:
                dial.move(e.getRawX(), e.getRawY());
                return true;
            case MotionEvent.ACTION_UP:
                touching = false;
                invalidate();
                dial.end(true);
                return true;
            case MotionEvent.ACTION_CANCEL:
                touching = false;
                invalidate();
                dial.end(false);
                return true;
            default:
                return true;
        }
    }
}
