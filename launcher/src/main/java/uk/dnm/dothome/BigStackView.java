package uk.dnm.dothome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import java.util.List;

/**
 * A large folder (2 × 2), as on the Nothing launcher: a rounded square showing four apps big
 * enough to open directly. Tap anywhere else on it to open the whole stack.
 */
public class BigStackView extends FrameLayout implements Cell {

    interface Listener {
        void onBigStackOpen(BigStackView v);
        void onBigStackHold(BigStackView v);
    }

    final String slot;
    final String name;
    final List<Apps.App> apps;
    private final Listener listener;
    private final boolean showLabel;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final Spring scale;
    private boolean ghost, dropTarget, dropStack, pressed, held;
    private float downX, downY;
    private final int slop;
    private final Runnable hold = this::onHold;

    private void onHold() {
        if (!pressed) return;
        held = true;
        Ui.firm(this);
        scale.animateTo(1f);
        if (listener != null) listener.onBigStackHold(this);
    }

    public BigStackView(Context c, String slot, String name, List<Apps.App> apps, boolean labels,
                        Listener listener, AppIconView.Listener appListener) {
        super(c);
        this.slot = slot;
        this.name = name;
        this.apps = apps;
        this.listener = listener;
        this.showLabel = labels;
        setWillNotDraw(false);
        setClipChildren(false);
        stroke.setStyle(Paint.Style.STROKE);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(Ui.sp(c, 10.5f));
        text.setColor(0xFFD0D0D0);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        scale = new Spring(600, 0.55f, 1f, v -> {
            setScaleX(v);
            setScaleY(v);
        });
        for (int i = 0; i < Math.min(4, apps.size()); i++) {
            AppIconView a = new AppIconView(c, 50, false);
            a.bind(apps.get(i), appListener);
            a.slot = apps.get(i).key;
            addView(a);
        }
        setContentDescription(name + ", large folder with " + apps.size() + " apps");
    }

    private float labelH() { return showLabel ? Ui.dp(getContext(), 20) : 0; }

    @Override
    protected void onMeasure(int w, int h) {
        int width = MeasureSpec.getSize(w), height = MeasureSpec.getSize(h);
        setMeasuredDimension(width, height);
        int cellW = (int) ((width - Ui.dp(getContext(), 24)) / 2), cellH = (int) ((height - labelH() - Ui.dp(getContext(), 22)) / 2);
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).measure(MeasureSpec.makeMeasureSpec(cellW, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(cellH, MeasureSpec.EXACTLY));
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        float inset = Ui.dp(getContext(), 6);
        box.set(inset, inset, getWidth() - inset, getHeight() - labelH() - inset);
        int cellW = getChildCount() > 0 ? getChildAt(0).getMeasuredWidth() : 0;
        int cellH = getChildCount() > 0 ? getChildAt(0).getMeasuredHeight() : 0;
        float x0 = box.centerX() - cellW, y0 = box.centerY() - cellH;
        for (int i = 0; i < getChildCount(); i++) {
            int cx = (int) (x0 + (i % 2) * cellW), cy = (int) (y0 + (i / 2) * cellH);
            getChildAt(i).layout(cx, cy, cx + cellW, cy + cellH);
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        float r = Ui.dp(getContext(), 30);
        if (ghost) {
            stroke.setPathEffect(new DashPathEffect(new float[]{Ui.dp(getContext(), 4), Ui.dp(getContext(), 4)}, 0));
            stroke.setColor(dropTarget ? Ui.RED : 0xFF333333);
            stroke.setStrokeWidth(Ui.dp(getContext(), 1.2f));
            c.drawRoundRect(box, r, r, stroke);
            stroke.setPathEffect(null);
            return;
        }
        fill.setColor(0xFF151515);
        c.drawRoundRect(box, r, r, fill);
        stroke.setColor(dropTarget ? Ui.RED : 0xFF262626);
        stroke.setStrokeWidth(Ui.dp(getContext(), dropTarget ? 2 : 1));
        c.drawRoundRect(box, r, r, stroke);
        if (apps.size() > 4) {
            text.setColor(0xFF777777);
            text.setTypeface(Ui.mono(getContext()));
            text.setTextSize(Ui.sp(getContext(), 9));
            c.drawText("+" + (apps.size() - 4), box.right - Ui.dp(getContext(), 18), box.bottom - Ui.dp(getContext(), 10), text);
            text.setTypeface(android.graphics.Typeface.DEFAULT);
            text.setTextSize(Ui.sp(getContext(), 10.5f));
            text.setColor(0xFFD0D0D0);
        }
        if (showLabel) {
            CharSequence l = TextUtils.ellipsize(name, text, getWidth() - Ui.dp(getContext(), 8), TextUtils.TruncateAt.END);
            c.drawText(l, 0, l.length(), getWidth() / 2f, getHeight() - Ui.dp(getContext(), 6), text);
        }
    }

    @Override
    protected void dispatchDraw(Canvas c) {
        if (!ghost) super.dispatchDraw(c);
    }

    // Taps on the four apps go to them; taps on the tile itself open the stack.
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressed = true;
                held = false;
                downX = e.getX();
                downY = e.getY();
                if (Ui.motion(getContext()) == Ui.MOTION_FULL) {
                    scale.stiffness = 1400;
                    scale.damping = 1f;
                    scale.animateTo(0.95f, 0);
                }
                postDelayed(hold, ViewConfiguration.getLongPressTimeout());
                return true;
            case MotionEvent.ACTION_MOVE:
                if (pressed && Math.hypot(e.getX() - downX, e.getY() - downY) > slop) release(false);
                return true;
            case MotionEvent.ACTION_UP: {
                boolean tap = pressed && !held;
                release(tap);
                if (tap && listener != null) {
                    Ui.tap(this);
                    listener.onBigStackOpen(this);
                }
                return true;
            }
            default:
                release(false);
                return true;
        }
    }

    private void release(boolean overshoot) {
        pressed = false;
        removeCallbacks(hold);
        scale.stiffness = 600;
        scale.damping = overshoot ? 0.55f : 0.9f;
        if (Ui.motion(getContext()) == Ui.MOTION_FULL) scale.animateTo(1f); else scale.set(1f);
    }

    // ---- Cell
    @Override public boolean hasContent() { return true; }
    @Override public void setGhost(boolean on) { ghost = on; invalidate(); }
    @Override public void setDropTarget(boolean on, boolean makesStack) { dropTarget = on; dropStack = makesStack; invalidate(); }
    @Override public RectF discBounds() { return new RectF(box); }
}
