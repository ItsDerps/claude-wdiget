package uk.dnm.dothome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;

import java.util.Set;

/**
 * The A–Z strip on the right of the drawer. Slide a thumb down it: a bubble shows the letter,
 * the list jumps there, and each new letter gives a tiny tick.
 * The view is wide (it also draws the bubble) but only the right edge reacts to touch.
 */
public class ScrubberView extends View {

    interface Listener { void onLetter(char letter); }

    static final String LETTERS = "#ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private Listener listener;
    private Set<Character> present = new java.util.HashSet<>();
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubble = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubbleText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private int active = -1;
    private float touchY;
    private final Spring pop;

    public ScrubberView(Context c) {
        super(c);
        text.setTypeface(Ui.mono(c));
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(Ui.sp(c, 8));
        bubble.setColor(Ui.WHITE);
        bubbleText.setTypeface(Ui.dots(c));
        bubbleText.setTextAlign(Paint.Align.CENTER);
        bubbleText.setColor(Ui.BLACK);
        bubbleText.setTextSize(Ui.sp(c, 34));
        pop = new Spring(800, 0.7f, 0f, v -> invalidate());
    }

    void setListener(Listener l) { listener = l; }

    void setPresent(Set<Character> p) {
        present = p;
        invalidate();
    }

    private float stripW() { return Ui.dp(getContext(), 22); }
    private float top() { return Ui.dp(getContext(), 8); }
    private float bottom() { return getHeight() - Ui.dp(getContext(), 8); }
    private float step() { return (bottom() - top()) / LETTERS.length(); }

    @Override
    protected void onDraw(Canvas c) {
        float x = getWidth() - stripW() / 2;
        for (int i = 0; i < LETTERS.length(); i++) {
            char ch = LETTERS.charAt(i);
            boolean has = present.contains(ch);
            float y = top() + step() * (i + 0.5f);
            text.setColor(i == active ? Ui.WHITE : has ? 0xFF777777 : 0xFF333333);
            float size = Ui.sp(getContext(), 8) * (i == active ? 1.6f : 1f);
            text.setTextSize(size);
            c.drawText(String.valueOf(ch), x, y + size * 0.35f, text);
        }
        float s = pop.value;
        if (s > 0.01f && active >= 0) {
            float r = Ui.dp(getContext(), 32);
            float bx = getWidth() - stripW() - Ui.dp(getContext(), 18) - r;
            float by = Ui.clamp(touchY, r + top(), bottom() - r);
            c.save();
            c.scale(s, s, bx + r, by);
            // A teardrop pointing at the thumb
            path.reset();
            path.addCircle(bx, by, r, Path.Direction.CW);
            path.moveTo(bx + r * 0.7f, by - r * 0.7f);
            path.lineTo(bx + r * 1.45f, by);
            path.lineTo(bx + r * 0.7f, by + r * 0.7f);
            path.close();
            c.drawPath(path, bubble);
            c.drawText(String.valueOf(LETTERS.charAt(active)), bx, by + bubbleText.getTextSize() * 0.35f, bubbleText);
            c.restore();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (e.getX() < getWidth() - stripW() - Ui.dp(getContext(), 8)) return false;
                getParent().requestDisallowInterceptTouchEvent(true);
                pop.animateTo(1f);
                track(e.getY());
                return true;
            case MotionEvent.ACTION_MOVE:
                track(e.getY());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                pop.animateTo(0f);
                active = -1;
                invalidate();
                return true;
            default:
                return true;
        }
    }

    private void track(float y) {
        touchY = y;
        int i = (int) Ui.clamp((y - top()) / step(), 0, LETTERS.length() - 1);
        // Snap to the nearest letter that has apps
        int best = -1;
        for (int d = 0; d < LETTERS.length(); d++) {
            if (i - d >= 0 && present.contains(LETTERS.charAt(i - d))) { best = i - d; break; }
            if (i + d < LETTERS.length() && present.contains(LETTERS.charAt(i + d))) { best = i + d; break; }
        }
        if (best >= 0 && best != active) {
            active = best;
            Ui.letter(this);
            if (listener != null) listener.onLetter(LETTERS.charAt(best));
        }
        invalidate();
    }
}
