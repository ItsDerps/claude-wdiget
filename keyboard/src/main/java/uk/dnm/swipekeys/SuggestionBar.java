package uk.dnm.swipekeys;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.List;

/**
 * The strip above the keys: a tools button on the left, the mic on the right, and in between
 * either three word suggestions (best in the middle) or the tools (emoji, notes, one-handed, settings).
 */
public class SuggestionBar extends View {

    public interface Listener {
        void onPick(String word);
        void onTool(int tool);
        void onMic();
    }

    static final int TOOL_CLOSE = -1, TOOL_EMOJI = 0, TOOL_NOTES = 1, TOOL_ONE_HAND = 2, TOOL_MEMORY = 3, TOOL_SETTINGS = 4;
    private static final int TOOL_COUNT = 5;

    // Touch targets
    private static final int HIT_NONE = -1, HIT_TOOLS = 100, HIT_MIC = 101, HIT_TOOL0 = 200;

    private Listener listener;
    private final String[] slots = new String[3]; // left, middle, right
    private boolean toolsOpen;
    private boolean highlightBest;
    private boolean memoryOn = true;
    private boolean listening;
    private String listeningText = "";
    private int highlightTool = -1;               // a tool whose panel is open
    private int pressed = HIT_NONE;

    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint icon = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tmp = new RectF();
    private final Paint.FontMetrics fm = new Paint.FontMetrics();
    private final Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);
    private final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private int colBg, colText, colDim, colDivider, colPressed;
    private static final int ACCENT = 0xFF0A84FF;

    public SuggestionBar(Context context) {
        super(context);
        text.setTextAlign(Paint.Align.CENTER);
        applyTheme();
    }

    public void setListener(Listener l) { listener = l; }

    public void setSuggestions(List<String> s) { setSuggestions(s, false); }

    /** {@code highlight}: the best suggestion is an autocorrection that space will apply. */
    public void setSuggestions(List<String> s, boolean highlight) {
        if (highlight != highlightBest) { highlightBest = highlight; invalidate(); }
        String m = s.size() > 0 ? s.get(0) : null;
        String l = s.size() > 1 ? s.get(1) : null;
        String r = s.size() > 2 ? s.get(2) : null;
        if (eq(m, slots[1]) && eq(l, slots[0]) && eq(r, slots[2])) return;
        slots[0] = l; slots[1] = m; slots[2] = r;
        if (m != null) toolsOpen = false; // new suggestions take over the strip
        invalidate();
    }

    public void setListening(boolean on, String partial) {
        listening = on;
        listeningText = partial == null ? "" : partial;
        invalidate();
    }

    /** Highlight the tool whose panel is showing (or -1). */
    public void setActiveTool(int tool) {
        highlightTool = tool;
        if (tool >= 0) toolsOpen = true;
        invalidate();
    }

    public void setMemory(boolean on) {
        memoryOn = on;
        invalidate();
    }

    public void closeTools() {
        toolsOpen = false;
        invalidate();
    }

    private boolean hasSuggestions() { return slots[0] != null || slots[1] != null || slots[2] != null; }

    /** Tools show when opened, or whenever there is nothing to suggest. */
    private boolean showingTools() { return !listening && (toolsOpen || !hasSuggestions()); }

    private static boolean eq(String a, String b) { return a == null ? b == null : a.equals(b); }

    private void applyTheme() {
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        if (dark) {
            colBg = 0xFF1F1F21; colText = Color.WHITE; colDim = 0xFFB0B0B5; colDivider = 0xFF48484C; colPressed = 0xFF3A3A3D;
        } else {
            colBg = 0xFFD1D4DA; colText = Color.BLACK; colDim = 0xFF55585E; colDivider = 0xFFA4A9B1; colPressed = 0xFFE9EAEE;
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
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), (int) dp(land ? 38 : 46));
    }

    private float side() { return dp(48); }

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(colBg);
        float w = getWidth(), h = getHeight(), side = side();
        float cy = (h + dp(2)) / 2f;

        // Tools button (left)
        if (pressed == HIT_TOOLS) pill(c, 0, side, h);
        Icons.stroke(icon, colDim, dp(1.7f));
        if (highlightTool >= 0 || (showingTools() && hasSuggestions())) Icons.chevronLeft(c, icon, side / 2, cy, dp(20));
        else Icons.grid(c, icon, side / 2, cy, dp(18));

        // Mic (right)
        float micX = w - side / 2;
        if (listening) {
            fill.setColor(ACCENT);
            float pulse = 1f + 0.08f * (float) Math.sin(SystemClock.uptimeMillis() / 140.0);
            c.drawCircle(micX, cy, dp(16) * pulse, fill);
            Icons.mic(c, Icons.stroke(icon, Color.WHITE, dp(1.7f)), micX, cy, dp(18));
            postInvalidateOnAnimation();
        } else {
            if (pressed == HIT_MIC) pill(c, w - side, w, h);
            Icons.mic(c, Icons.stroke(icon, colDim, dp(1.7f)), micX, cy, dp(18));
        }

        float left = side, right = w - side, mid = right - left;
        if (listening) {
            String s = listeningText.isEmpty() ? "Listening…" : listeningText;
            text.setTypeface(regular);
            text.setTextSize(sp(16));
            text.setColor(listeningText.isEmpty() ? colDim : colText);
            // Show the end of long dictation
            CharSequence shown = TextUtils.ellipsize(s, text, mid - dp(16), TextUtils.TruncateAt.START);
            text.getFontMetrics(fm);
            c.drawText(shown, 0, shown.length(), left + mid / 2, cy - (fm.ascent + fm.descent) / 2f, text);
            return;
        }

        if (showingTools()) {
            float cell = mid / TOOL_COUNT;
            for (int i = 0; i < TOOL_COUNT; i++) {
                float x = left + cell * i, ccx = x + cell / 2;
                boolean on = i == highlightTool;
                if (pressed == HIT_TOOL0 + i || on) {
                    fill.setColor(on ? (ACCENT & 0x40FFFFFF) : colPressed);
                    c.drawCircle(ccx, cy, dp(18), fill);
                }
                Icons.stroke(icon, on ? ACCENT : colText, dp(1.7f));
                switch (i) {
                    case TOOL_EMOJI: Icons.smiley(c, icon, ccx, cy, dp(20)); break;
                    case TOOL_NOTES: Icons.notes(c, icon, ccx, cy, dp(20)); break;
                    case TOOL_ONE_HAND: Icons.oneHand(c, icon, ccx, cy, dp(22)); break;
                    case TOOL_MEMORY:
                        if (!memoryOn) Icons.stroke(icon, 0xFFE5484D, dp(1.7f));
                        Icons.eye(c, icon, ccx, cy, dp(22), !memoryOn);
                        break;
                    default: Icons.sliders(c, icon, ccx, cy, dp(20)); break;
                }
            }
            return;
        }

        float cell = mid / 3f;
        for (int i = 0; i < 3; i++) {
            float x = left + i * cell;
            if (pressed == i && slots[i] != null) pill(c, x, x + cell, h);
            if (slots[i] == null) continue;
            text.setTypeface(i == 1 ? medium : regular);
            text.setTextSize(sp(17));
            text.setColor(i == 1 && highlightBest ? ACCENT : colText);
            float maxW = cell - dp(12);
            while (text.measureText(slots[i]) > maxW && text.getTextSize() > sp(12)) {
                text.setTextSize(text.getTextSize() - sp(1));
            }
            CharSequence s = TextUtils.ellipsize(slots[i], text, maxW, TextUtils.TruncateAt.END);
            text.getFontMetrics(fm);
            c.drawText(s, 0, s.length(), x + cell / 2f, cy - (fm.ascent + fm.descent) / 2f, text);
        }
        fill.setColor(colDivider);
        for (int i = 1; i < 3; i++) {
            if ((slots[i - 1] != null || slots[i] != null) && pressed != i && pressed != i - 1) {
                float x = left + i * cell;
                c.drawRect(x - dp(0.5f), h * 0.3f, x + dp(0.5f), h * 0.78f, fill);
            }
        }
    }

    private void pill(Canvas c, float l, float r, float h) {
        tmp.set(l + dp(4), dp(5), r - dp(4), h - dp(3));
        fill.setColor(colPressed);
        c.drawRoundRect(tmp, dp(8), dp(8), fill);
    }

    private int hitTest(float x) {
        float w = getWidth(), side = side();
        if (x < side) return HIT_TOOLS;
        if (x > w - side) return HIT_MIC;
        if (listening) return HIT_NONE;
        float mid = w - 2 * side;
        if (showingTools()) {
            int i = (int) ((x - side) / (mid / TOOL_COUNT));
            return HIT_TOOL0 + Math.max(0, Math.min(TOOL_COUNT - 1, i));
        }
        int i = Math.max(0, Math.min(2, (int) ((x - side) / (mid / 3f))));
        return slots[i] != null ? i : HIT_NONE;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressed = hitTest(e.getX());
                if (pressed != HIT_NONE) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (pressed != HIT_NONE && (hitTest(e.getX()) != pressed
                        || e.getY() < -dp(20) || e.getY() > getHeight() + dp(20))) {
                    pressed = HIT_NONE;
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP: {
                int p = pressed;
                pressed = HIT_NONE;
                invalidate();
                if (p == HIT_NONE || listener == null) return true;
                if (p == HIT_TOOLS) {
                    if (highlightTool >= 0) {
                        // A panel is open: this button closes it
                        highlightTool = -1;
                        toolsOpen = false;
                        listener.onTool(TOOL_CLOSE);
                    } else {
                        toolsOpen = !showingTools() || !hasSuggestions();
                    }
                    invalidate();
                } else if (p == HIT_MIC) {
                    listener.onMic();
                } else if (p >= HIT_TOOL0) {
                    listener.onTool(p - HIT_TOOL0);
                } else if (slots[p] != null) {
                    listener.onPick(slots[p]);
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                pressed = HIT_NONE;
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
