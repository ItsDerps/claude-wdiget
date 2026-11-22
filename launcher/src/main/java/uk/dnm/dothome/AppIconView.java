package uk.dnm.dothome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.List;

/**
 * One app: a white glyph on a dark disc, with an optional label.
 * Touch: squashes to 0.9, dots ripple out from the finger, a ring of dots fills while held,
 * and on release it springs back slightly past full size.
 */
public class AppIconView extends View implements Cell {

    interface Listener {
        void onAppClick(AppIconView v);
        void onAppLongClick(AppIconView v);
    }

    Apps.App app;
    /** What this icon stands for on home: an app key or a "stack:" slot (null in the drawer). */
    String slot;
    /** Set when this icon is a stack. */
    List<Apps.App> stackApps;
    String stackName;
    /** An empty, dashed slot (edit mode). */
    boolean placeholder;
    private boolean dropTarget, dropStack, ghost;
    private final android.graphics.DashPathEffect dash;
    private Listener listener;
    private boolean showLabel;
    private final float disc;
    private boolean badge;
    private boolean lifted;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF dst = new RectF();

    private final Spring scale;
    private long rippleStart = -1;
    private float rippleX, rippleY;
    private long downTime = -1;
    private boolean longPressed, pressed;
    private float downX, downY;
    private final int slop;
    private final long longPressMs;

    private final Runnable longPress = this::onLongPressFired;

    private void onLongPressFired() {
        if (!pressed) return;
        longPressed = true;
        downTime = -1;
        Ui.firm(this);
        scale.animateTo(1f);
        if (listener != null) listener.onAppLongClick(this);
        invalidate();
    }

    public AppIconView(Context c, float discDp, boolean showLabel) {
        super(c);
        this.disc = Ui.dp(c, discDp);
        this.showLabel = showLabel;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Ui.dp(c, 1));
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(Ui.sp(c, 10.5f));
        text.setColor(0xFFD0D0D0);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        longPressMs = ViewConfiguration.getLongPressTimeout();
        scale = new Spring(600, 0.55f, 1f, v -> invalidate());
        dash = new android.graphics.DashPathEffect(new float[]{Ui.dp(c, 3), Ui.dp(c, 3)}, 0);
        setClickable(true);
        setHapticFeedbackEnabled(true);
    }

    void bind(Apps.App a, Listener l) {
        app = a;
        listener = l;
        setContentDescription(a == null ? null : a.label);
        invalidate();
    }

    void bindStack(String slot, String name, List<Apps.App> apps, Listener l) {
        this.slot = slot;
        this.stackName = name;
        this.stackApps = apps;
        this.app = null;
        listener = l;
        setContentDescription(name + ", stack of " + apps.size());
        invalidate();
    }

    void makePlaceholder() {
        placeholder = true;
        app = null;
        stackApps = null;
        setClickable(false);
        invalidate();
    }

    boolean isStack() { return stackApps != null; }
    @Override public boolean hasContent() { return app != null || stackApps != null; }

    /** Coming back from this app: the icon lands with a bounce and a ripple. */
    void pop() {
        if (Ui.motion(getContext()) != Ui.MOTION_FULL) return;
        scale.set(1.22f);
        scale.stiffness = 420;
        scale.damping = 0.45f;
        scale.animateTo(1f, 0);
        RectF d = discBounds();
        rippleStart = SystemClock.uptimeMillis();
        rippleX = d.centerX();
        rippleY = d.centerY();
        invalidate();
    }

    /** Large (2 × 2) icons centre themselves in their space; normal ones sit at the top. */
    private float top() {
        float content = disc + (showLabel ? Ui.dp(getContext(), 18) : 0);
        float t = Ui.dp(getContext(), 6);
        if (getHeight() > content + Ui.dp(getContext(), 30)) t = (getHeight() - content) / 2f;
        return t;
    }

    /** Edit mode: highlight as the cell a dragged app will land in (red ring; filled when it'll make a stack). */
    @Override
    public void setDropTarget(boolean on, boolean makesStack) {
        if (on != dropTarget || makesStack != dropStack) {
            dropTarget = on;
            dropStack = makesStack;
            invalidate();
        }
    }

    /** Edit mode: the source cell while its app is being dragged. */
    @Override
    public void setGhost(boolean on) {
        if (on != ghost) { ghost = on; invalidate(); }
    }

    void setBadge(boolean on) {
        if (on != badge) { badge = on; invalidate(); }
    }

    void setShowLabel(boolean on) {
        if (on != showLabel) { showLabel = on; requestLayout(); }
    }

    /** Drawn raised with a white ring, used for the copy shown above a touch-and-hold menu. */
    void setLifted(boolean on) {
        lifted = on;
        scale.set(on ? 1.12f : 1f);
        invalidate();
    }

    /** Where the disc is, in this view's coordinates (for the app-opening animation). */
    @Override
    public RectF discBounds() {
        float cx = getWidth() / 2f, top = top();
        return new RectF(cx - disc / 2, top, cx + disc / 2, top + disc);
    }

    @Override
    protected void onMeasure(int w, int h) {
        int width = (int) Math.max(disc + Ui.dp(getContext(), 18), Ui.dp(getContext(), 64));
        int height = (int) (disc + Ui.dp(getContext(), 12) + (showLabel ? Ui.dp(getContext(), 18) : 0));
        setMeasuredDimension(resolveSize(width, w), height);
    }

    @Override
    protected void onDraw(Canvas c) {
        Theme th = Theme.get(getContext());
        text.setColor(th.light ? 0xFF2A2A2A : 0xFFD0D0D0);
        // Glyphs are drawn white; on Paper they're tinted black
        bmp.setColorFilter(th.light ? new android.graphics.PorterDuffColorFilter(th.glyph, android.graphics.PorterDuff.Mode.SRC_IN) : null);
        float cx = getWidth() / 2f, top = top(), cy = top + disc / 2;
        long now = SystemClock.uptimeMillis();
        boolean anim = false;

        // Hold build-up: a ring of dots fills around the disc
        if (downTime > 0 && !longPressed) {
            float t = (now - downTime - 120) / (float) (longPressMs - 120);
            if (t > 0) {
                int n = 20;
                float r = disc / 2 + Ui.dp(getContext(), 7);
                for (int i = 0; i < n; i++) {
                    double a = -Math.PI / 2 + i * Math.PI * 2 / n;
                    fill.setColor(i / (float) n < t ? th.glow : th.iconRing);
                    c.drawCircle(cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r, Ui.dp(getContext(), 1.4f), fill);
                }
            }
            anim = true;
        }

        // Dot ripple from the touch point
        if (rippleStart >= 0) {
            float p = (now - rippleStart) / 420f;
            if (p >= 1) {
                rippleStart = -1;
            } else {
                float e = 1 - (1 - p) * (1 - p) * (1 - p);
                float maxR = Ui.dp(getContext(), 30);
                for (int ring = 0; ring < 2; ring++) {
                    float r = Math.max(0, Ui.dp(getContext(), 6) + e * maxR - ring * Ui.dp(getContext(), 9));
                    int alpha = (int) (255 * Math.max(0, (1 - p) - ring * 0.25f));
                    fill.setColor(th.glow);
                    fill.setAlpha(alpha);
                    int n = 16;
                    for (int i = 0; i < n; i++) {
                        double a = i * Math.PI * 2 / n;
                        c.drawCircle(rippleX + (float) Math.cos(a) * r, rippleY + (float) Math.sin(a) * r,
                                Ui.dp(getContext(), ring == 0 ? 1.3f : 0.9f), fill);
                    }
                }
                fill.setAlpha(255);
                anim = true;
            }
        }

        if (placeholder || ghost) {
            stroke.setPathEffect(dash);
            stroke.setStrokeWidth(Ui.dp(getContext(), 1.2f));
            stroke.setColor(dropTarget ? Ui.RED : th.light ? 0xFFBDBDB8 : 0xFF333333);
            if (dropTarget) {
                fill.setColor(0x26D71921);
                c.drawCircle(cx, cy, disc / 2, fill);
            }
            c.drawCircle(cx, cy, disc / 2 - Ui.dp(getContext(), 1), stroke);
            stroke.setPathEffect(null);
            if (anim) postInvalidateOnAnimation();
            return;
        }

        float s = scale.value * (dropTarget ? 1.08f : 1f);
        c.save();
        c.scale(s, s, cx, cy);
        if (lifted) {
            fill.setColor(0x99000000);
            c.drawCircle(cx, cy + Ui.dp(getContext(), 6), disc / 2 + Ui.dp(getContext(), 4), fill);
        }
        fill.setColor(lifted ? (th.light ? 0xFFFFFFFF : 0xFF222222) : th.iconBg);
        c.drawCircle(cx, cy, disc / 2, fill);
        stroke.setColor(lifted ? th.text : th.iconRing);
        stroke.setStrokeWidth(Ui.dp(getContext(), lifted ? 2 : 1));
        c.drawCircle(cx, cy, disc / 2 - stroke.getStrokeWidth() / 2, stroke);
        if (app != null && app.icon != null) {
            dst.set(cx - disc / 2, cy - disc / 2, cx + disc / 2, cy + disc / 2);
            c.drawBitmap(app.icon, null, dst, bmp);
        }
        if (stackApps != null) {
            // Four of the apps in miniature, 2 × 2
            float mini = disc * 0.42f, gap = disc * 0.04f;
            for (int i = 0; i < Math.min(4, stackApps.size()); i++) {
                Apps.App a = stackApps.get(i);
                float mx = cx + (i % 2 == 0 ? -gap / 2 - mini : gap / 2);
                float my = cy + (i < 2 ? -gap / 2 - mini : gap / 2);
                if (a.icon != null) {
                    dst.set(mx, my, mx + mini, my + mini);
                    c.drawBitmap(a.icon, null, dst, bmp);
                }
            }
        }
        if (dropTarget && dropStack) {
            stroke.setColor(Ui.RED);
            stroke.setStrokeWidth(Ui.dp(getContext(), 2));
            c.drawCircle(cx, cy, disc / 2 + Ui.dp(getContext(), 3), stroke);
        }
        if (app != null && app.work) {
            fill.setColor(th.text);
            c.drawCircle(cx + disc * 0.36f, cy + disc * 0.36f, Ui.dp(getContext(), 5), fill);
            fill.setColor(th.bg);
            c.drawCircle(cx + disc * 0.36f, cy + disc * 0.36f, Ui.dp(getContext(), 2.2f), fill);
        }
        if (badge) {
            float bx = cx + disc * 0.36f, by = cy - disc * 0.36f;
            fill.setColor(th.bg);
            c.drawCircle(bx, by, Ui.dp(getContext(), 6.5f), fill);
            fill.setColor(Ui.RED);
            c.drawCircle(bx, by, Ui.dp(getContext(), 4.5f), fill);
        }
        c.restore();

        String label = app != null ? app.label : stackName;
        if (showLabel && label != null) {
            float maxW = getWidth() - Ui.dp(getContext(), 4);
            CharSequence l = TextUtils.ellipsize(label, text, maxW, TextUtils.TruncateAt.END);
            c.drawText(l, 0, l.length(), cx, top + disc + Ui.dp(getContext(), 16), text);
        }
        if (anim) postInvalidateOnAnimation();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int motion = Ui.motion(getContext());
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressed = true;
                longPressed = false;
                downX = e.getX();
                downY = e.getY();
                downTime = SystemClock.uptimeMillis();
                if (motion == Ui.MOTION_FULL) {
                    rippleStart = downTime;
                    rippleX = e.getX();
                    rippleY = e.getY();
                    scale.stiffness = 1400;
                    scale.damping = 1f;
                    scale.animateTo(0.9f, 0);
                }
                postDelayed(longPress, longPressMs);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (pressed && (Math.abs(e.getX() - downX) > slop || Math.abs(e.getY() - downY) > slop)) {
                    release(false);
                }
                return true;
            case MotionEvent.ACTION_UP: {
                boolean click = pressed && !longPressed;
                release(click);
                if (click) {
                    Ui.tap(this);
                    if (listener != null) listener.onAppClick(this);
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                release(false);
                return true;
            default:
                return true;
        }
    }

    private void release(boolean overshoot) {
        pressed = false;
        downTime = -1;
        removeCallbacks(longPress);
        if (Ui.motion(getContext()) == Ui.MOTION_FULL) {
            scale.stiffness = 600;
            scale.damping = overshoot ? 0.55f : 0.9f;
            scale.animateTo(1f);
        } else {
            scale.set(1f);
        }
        invalidate();
    }
}
