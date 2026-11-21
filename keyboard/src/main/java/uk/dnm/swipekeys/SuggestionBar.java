package uk.dnm.swipekeys;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.List;

/** Three-slot suggestion strip above the keys. The best suggestion sits in the middle. */
public class SuggestionBar extends View {

    public interface Listener { void onPick(String word); }

    private Listener listener;
    private final String[] slots = new String[3]; // left, middle, right
    private int pressed = -1;

    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tmp = new RectF();
    private final Paint.FontMetrics fm = new Paint.FontMetrics();
    private final Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);
    private final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private int colBg, colText, colDivider, colPressed;

    public SuggestionBar(Context context) {
        super(context);
        text.setTextAlign(Paint.Align.CENTER);
        applyTheme();
    }

    public void setListener(Listener l) { listener = l; }

    public void setSuggestions(List<String> s) {
        String m = s.size() > 0 ? s.get(0) : null;
        String l = s.size() > 1 ? s.get(1) : null;
        String r = s.size() > 2 ? s.get(2) : null;
        if (eq(m, slots[1]) && eq(l, slots[0]) && eq(r, slots[2])) return;
        slots[0] = l; slots[1] = m; slots[2] = r;
        pressed = -1;
        invalidate();
    }

    private static boolean eq(String a, String b) { return a == null ? b == null : a.equals(b); }

    private void applyTheme() {
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        if (dark) {
            colBg = 0xFF1F1F21; colText = Color.WHITE; colDivider = 0xFF48484C; colPressed = 0xFF3A3A3D;
        } else {
            colBg = 0xFFD1D4DA; colText = Color.BLACK; colDivider = 0xFFA4A9B1; colPressed = 0xFFE9EAEE;
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        applyTheme();
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        boolean land = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), (int) dp(land ? 36 : 44));
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(colBg);
        float w = getWidth(), h = getHeight(), cell = w / 3f;
        for (int i = 0; i < 3; i++) {
            float left = i * cell;
            if (i == pressed && slots[i] != null) {
                tmp.set(left + dp(4), dp(5), left + cell - dp(4), h - dp(3));
                fill.setColor(colPressed);
                c.drawRoundRect(tmp, dp(6), dp(6), fill);
            }
            if (slots[i] == null) continue;
            text.setTypeface(i == 1 ? medium : regular);
            text.setTextSize(sp(17));
            text.setColor(colText);
            float maxW = cell - dp(14);
            // Shrink long words a bit before cutting them off
            while (text.measureText(slots[i]) > maxW && text.getTextSize() > sp(12)) {
                text.setTextSize(text.getTextSize() - sp(1));
            }
            CharSequence s = TextUtils.ellipsize(slots[i], text, maxW, TextUtils.TruncateAt.END);
            text.getFontMetrics(fm);
            float cy = (h + dp(2)) / 2f;
            c.drawText(s, 0, s.length(), left + cell / 2f, cy - (fm.ascent + fm.descent) / 2f, text);
        }
        fill.setColor(colDivider);
        for (int i = 1; i < 3; i++) {
            boolean show = slots[i - 1] != null || slots[i] != null;
            if (show && pressed != i && pressed != i - 1) {
                float x = i * cell;
                c.drawRect(x - dp(0.5f), h * 0.3f, x + dp(0.5f), h * 0.78f, fill);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int cellIdx = (int) Math.max(0, Math.min(2, e.getX() / (getWidth() / 3f)));
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (slots[cellIdx] != null) {
                    pressed = cellIdx;
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (pressed != -1 && (cellIdx != pressed || e.getY() < -dp(20) || e.getY() > getHeight() + dp(20))) {
                    pressed = -1;
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP: {
                int p = pressed;
                pressed = -1;
                invalidate();
                if (p != -1 && slots[p] != null && listener != null) listener.onPick(slots[p]);
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                pressed = -1;
                invalidate();
                return true;
            default:
                return true;
        }
    }

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, getResources().getDisplayMetrics());
    }
}
