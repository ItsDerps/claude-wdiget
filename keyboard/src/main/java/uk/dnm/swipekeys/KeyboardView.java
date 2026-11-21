package uk.dnm.swipekeys;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * A keyboard where every letter also carries a second character (shown small, above it).
 * Tap a key for the letter; pull it DOWN for the second character, like the iPad keyboard.
 */
public class KeyboardView extends View {

    public interface Listener {
        void onText(String text);
        void onDelete();
        void onEnter();
        void onCursor(int steps);
        void onSwitchIme();
    }

    static final int CHAR = 0, SHIFT = 1, DELETE = 2, ENTER = 3, SPACE = 4,
            TO_SYM = 5, TO_SYM2 = 6, TO_ALPHA = 7;
    static final int MODE_ALPHA = 0, MODE_SYM = 1, MODE_SYM2 = 2;
    static final int SHIFT_OFF = 0, SHIFT_ON = 1, SHIFT_LOCK = 2;

    static final class Key {
        final int type;
        final String main;
        final String alt;
        final float units;
        final RectF rect = new RectF();
        final RectF hit = new RectF();

        Key(int type, String main, String alt, float units) {
            this.type = type;
            this.main = main;
            this.alt = alt;
            this.units = units;
        }
    }

    static final class Touch {
        Key key;
        float downX, downY, lastX;
        float progress;          // 0 = resting, 1 = fully pulled down
        boolean flickTicked;
        boolean cursorMode;
        boolean consumed;
    }

    private static final float FLICK_COMMIT = 0.5f;

    private Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SparseArray<Touch> touches = new SparseArray<>();

    private final List<List<Key>> alphaRows = buildAlpha();
    private final List<List<Key>> symRows = buildSym();
    private final List<List<Key>> sym2Rows = buildSym2();
    private List<List<Key>> rows = alphaRows;

    private int mode = MODE_ALPHA;
    private int shift = SHIFT_OFF;
    private long lastShiftTap;
    private boolean symLocked;      // number/phone fields stay on the symbols page
    private boolean typedInSym;
    private String enterLabel;
    private boolean enterAccent;

    // Theme
    private int colBg, colKey, colSpecial, colPressed, colShadow, colText, colAlt, colAccent, colAccentPressed;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint icon = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF tmp = new RectF();
    private final Paint.FontMetrics fm = new Paint.FontMetrics();

    private Touch spaceTouch;
    private final Runnable spaceLongPress = () -> {
        Touch t = spaceTouch;
        if (t != null && !t.cursorMode && listener != null) {
            t.consumed = true;
            listener.onSwitchIme();
        }
    };
    private final Runnable deleteRepeat = new Runnable() {
        @Override public void run() {
            if (listener != null) listener.onDelete();
            handler.postDelayed(this, 50);
        }
    };

    public KeyboardView(Context context) {
        super(context);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        icon.setStrokeWidth(dp(1.7f));
        icon.setStrokeJoin(Paint.Join.ROUND);
        icon.setStrokeCap(Paint.Cap.ROUND);
        applyTheme();
    }

    public void setListener(Listener l) { listener = l; }

    // ---------------------------------------------------------------- state from the service

    public void setEditorInfo(EditorInfo ei) {
        cancelAll();
        int cls = ei == null ? InputType.TYPE_CLASS_TEXT : ei.inputType & InputType.TYPE_MASK_CLASS;
        symLocked = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE
                || cls == InputType.TYPE_CLASS_DATETIME;
        shift = SHIFT_OFF;
        setMode(symLocked ? MODE_SYM : MODE_ALPHA);

        enterLabel = null;
        enterAccent = false;
        if (ei != null && (ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0) {
            int action = ei.imeOptions & EditorInfo.IME_MASK_ACTION;
            switch (action) {
                case EditorInfo.IME_ACTION_GO: enterLabel = "go"; enterAccent = true; break;
                case EditorInfo.IME_ACTION_SEARCH: enterLabel = "search"; enterAccent = true; break;
                case EditorInfo.IME_ACTION_SEND: enterLabel = "send"; enterAccent = true; break;
                case EditorInfo.IME_ACTION_DONE: enterLabel = "done"; enterAccent = true; break;
                case EditorInfo.IME_ACTION_NEXT: enterLabel = "next"; break;
                case EditorInfo.IME_ACTION_PREVIOUS: enterLabel = "prev"; break;
                default: break;
            }
            if (enterLabel != null && ei.actionLabel != null && ei.actionLabel.length() <= 8) {
                enterLabel = ei.actionLabel.toString().toLowerCase();
            }
        }
        applyTheme();
        invalidate();
    }

    /** Called after every edit: capitalise at the start of sentences unless caps lock is on. */
    public void setAutoShift(boolean on) {
        if (shift == SHIFT_LOCK || mode != MODE_ALPHA) return;
        int s = on ? SHIFT_ON : SHIFT_OFF;
        if (s != shift) {
            shift = s;
            invalidate();
        }
    }

    private void setMode(int m) {
        mode = m;
        rows = m == MODE_ALPHA ? alphaRows : m == MODE_SYM ? symRows : sym2Rows;
        typedInSym = false;
        layoutKeys();
        invalidate();
    }

    // ---------------------------------------------------------------- layouts

    private static List<Key> chars(String mains, String alts) {
        List<Key> row = new ArrayList<>();
        for (int i = 0; i < mains.length(); i++) {
            row.add(new Key(CHAR, String.valueOf(mains.charAt(i)),
                    alts == null ? null : String.valueOf(alts.charAt(i)), 1f));
        }
        return row;
    }

    private static List<Key> bottomRow(int modeType, String modeLabel) {
        List<Key> r = new ArrayList<>();
        r.add(new Key(modeType, modeLabel, null, 1.5f));
        r.add(new Key(CHAR, ",", "!", 1f));
        r.add(new Key(SPACE, null, null, 5f));
        r.add(new Key(CHAR, ".", "?", 1f));
        r.add(new Key(ENTER, null, null, 1.5f));
        return r;
    }

    private static List<Key> symThirdRow(int modeType, String modeLabel) {
        List<Key> r = new ArrayList<>();
        r.add(new Key(modeType, modeLabel, null, 1.5f));
        for (char c : ".,?!'".toCharArray()) r.add(new Key(CHAR, String.valueOf(c), null, 1.4f));
        r.add(new Key(DELETE, null, null, 1.5f));
        return r;
    }

    private static List<List<Key>> buildAlpha() {
        List<List<Key>> rows = new ArrayList<>();
        rows.add(chars("qwertyuiop", "1234567890"));
        rows.add(chars("asdfghjkl", "@#£&*()'\""));
        List<Key> r3 = new ArrayList<>();
        r3.add(new Key(SHIFT, null, null, 1.5f));
        r3.addAll(chars("zxcvbnm", "%-+=/;:"));
        r3.add(new Key(DELETE, null, null, 1.5f));
        rows.add(r3);
        rows.add(bottomRow(TO_SYM, "123"));
        return rows;
    }

    private static List<List<Key>> buildSym() {
        List<List<Key>> rows = new ArrayList<>();
        rows.add(chars("1234567890", null));
        rows.add(chars("-/:;()£&@\"", null));
        rows.add(symThirdRow(TO_SYM2, "#+="));
        rows.add(bottomRow(TO_ALPHA, "ABC"));
        return rows;
    }

    private static List<List<Key>> buildSym2() {
        List<List<Key>> rows = new ArrayList<>();
        rows.add(chars("[]{}#%^*+=", null));
        rows.add(chars("_\\|~<>€$¥•", null));
        rows.add(symThirdRow(TO_SYM, "123"));
        rows.add(bottomRow(TO_ALPHA, "ABC"));
        return rows;
    }

    // ---------------------------------------------------------------- measuring

    private boolean isLandscape() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private float rowHeight() { return dp(isLandscape() ? 46 : 58); }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        int h = (int) (dp(6) + dp(10) + 4 * rowHeight());
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        layoutKeys();
    }

    private void layoutKeys() {
        float w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        float padX = dp(3), padTop = dp(6), padBottom = dp(10);
        float unit = (w - 2 * padX) / 10f;
        float rowH = (h - padTop - padBottom) / rows.size();
        float gx = dp(3), gy = dp(5.5f);
        for (int r = 0; r < rows.size(); r++) {
            List<Key> row = rows.get(r);
            float total = 0;
            for (Key k : row) total += k.units;
            float x = padX + (10f - total) * unit / 2f;
            float top = padTop + r * rowH;
            for (Key k : row) {
                float kw = k.units * unit;
                k.hit.set(x, top, x + kw, top + rowH);
                k.rect.set(x + gx, top + gy, x + kw - gx, top + rowH - gy);
                x += kw;
            }
            row.get(0).hit.left = 0;
            row.get(row.size() - 1).hit.right = w;
            if (r == 0) for (Key k : row) k.hit.top = 0;
            if (r == rows.size() - 1) for (Key k : row) k.hit.bottom = h;
        }
    }

    private Key findKey(float x, float y) {
        Key best = null;
        float bestD = Float.MAX_VALUE;
        for (List<Key> row : rows) {
            for (Key k : row) {
                if (k.hit.contains(x, y)) return k;
                float dx = k.hit.centerX() - x, dy = k.hit.centerY() - y;
                float d = dx * dx + dy * dy;
                if (d < bestD) { bestD = d; best = k; }
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- touch

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int idx = e.getActionIndex();
                flushPendingChars();
                Key k = findKey(e.getX(idx), e.getY(idx));
                if (k == null) break;
                Touch t = new Touch();
                t.key = k;
                t.downX = t.lastX = e.getX(idx);
                t.downY = e.getY(idx);
                touches.put(e.getPointerId(idx), t);
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                onKeyDown(t);
                invalidate();
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                for (int i = 0; i < e.getPointerCount(); i++) {
                    Touch t = touches.get(e.getPointerId(i));
                    if (t != null) onKeyMove(t, e.getX(i), e.getY(i));
                }
                invalidate();
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                int id = e.getPointerId(e.getActionIndex());
                Touch t = touches.get(id);
                if (t != null) {
                    touches.remove(id);
                    onKeyUp(t);
                }
                invalidate();
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                cancelAll();
                invalidate();
                break;
            default:
                break;
        }
        return true;
    }

    /** When a new finger lands, commit letters still held by other fingers so fast typing stays in order. */
    private void flushPendingChars() {
        List<Integer> done = new ArrayList<>();
        for (int i = 0; i < touches.size(); i++) {
            Touch t = touches.valueAt(i);
            if (t.key.type == CHAR && t.progress < 0.2f) {
                commitChar(t);
                done.add(touches.keyAt(i));
            }
        }
        for (int id : done) touches.remove(id);
    }

    private void cancelAll() {
        handler.removeCallbacks(deleteRepeat);
        handler.removeCallbacks(spaceLongPress);
        spaceTouch = null;
        touches.clear();
    }

    private void onKeyDown(Touch t) {
        switch (t.key.type) {
            case DELETE:
                if (listener != null) listener.onDelete();
                handler.removeCallbacks(deleteRepeat);
                handler.postDelayed(deleteRepeat, 400);
                break;
            case SHIFT: {
                long now = android.os.SystemClock.uptimeMillis();
                if (shift == SHIFT_LOCK) shift = SHIFT_OFF;
                else if (shift == SHIFT_ON) shift = (now - lastShiftTap < 350) ? SHIFT_LOCK : SHIFT_OFF;
                else shift = SHIFT_ON;
                lastShiftTap = now;
                break;
            }
            case SPACE:
                spaceTouch = t;
                handler.removeCallbacks(spaceLongPress);
                handler.postDelayed(spaceLongPress, 600);
                break;
            default:
                break;
        }
    }

    private void onKeyMove(Touch t, float x, float y) {
        Key k = t.key;
        if (k.type == CHAR && k.alt != null) {
            float p = (y - t.downY) / dp(26);
            t.progress = Math.max(0f, Math.min(1f, p));
            if (t.progress >= FLICK_COMMIT && !t.flickTicked) {
                t.flickTicked = true;
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            } else if (t.progress < FLICK_COMMIT && t.flickTicked) {
                t.flickTicked = false;
            }
        } else if (k.type == SPACE) {
            // Slide along the space bar to move the cursor
            if (!t.cursorMode && Math.abs(x - t.downX) > dp(14)) {
                t.cursorMode = true;
                t.lastX = x;
                handler.removeCallbacks(spaceLongPress);
            }
            if (t.cursorMode && listener != null) {
                float step = dp(10);
                while (x - t.lastX >= step) { listener.onCursor(1); t.lastX += step; }
                while (t.lastX - x >= step) { listener.onCursor(-1); t.lastX -= step; }
            }
        }
    }

    private void onKeyUp(Touch t) {
        switch (t.key.type) {
            case CHAR:
                commitChar(t);
                break;
            case SPACE:
                handler.removeCallbacks(spaceLongPress);
                spaceTouch = null;
                if (!t.cursorMode && !t.consumed && listener != null) {
                    boolean backToLetters = mode != MODE_ALPHA && !symLocked && typedInSym;
                    listener.onText(" ");
                    if (backToLetters) setMode(MODE_ALPHA);
                }
                break;
            case ENTER:
                if (listener != null) listener.onEnter();
                break;
            case DELETE:
                handler.removeCallbacks(deleteRepeat);
                break;
            case TO_SYM: setMode(MODE_SYM); break;
            case TO_SYM2: setMode(MODE_SYM2); break;
            case TO_ALPHA: setMode(MODE_ALPHA); break;
            default: break;
        }
    }

    private void commitChar(Touch t) {
        Key k = t.key;
        boolean flicked = k.alt != null && t.progress >= FLICK_COMMIT;
        String s = flicked ? k.alt : displayMain(k);
        if (mode == MODE_ALPHA && shift == SHIFT_ON) shift = SHIFT_OFF;
        if (mode != MODE_ALPHA) typedInSym = true;
        if (listener != null) listener.onText(s);
    }

    private String displayMain(Key k) {
        return (mode == MODE_ALPHA && shift != SHIFT_OFF) ? k.main.toUpperCase() : k.main;
    }

    // ---------------------------------------------------------------- drawing

    private void applyTheme() {
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        if (dark) {
            colBg = 0xFF1F1F21; colKey = 0xFF4B4B4F; colSpecial = 0xFF323235; colPressed = 0xFF6E6E73;
            colShadow = 0xFF0E0E0F; colText = Color.WHITE; colAlt = 0xFF9C9CA1;
        } else {
            colBg = 0xFFD1D4DA; colKey = Color.WHITE; colSpecial = 0xFFABB1BA; colPressed = 0xFFE3E4E8;
            colShadow = 0xFF898E96; colText = Color.BLACK; colAlt = 0xFF8A8A8E;
        }
        colAccent = 0xFF0A84FF;
        colAccentPressed = 0xFF3A9BFF;
    }

    @Override
    protected void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        applyTheme();
        requestLayout();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(colBg);
        for (List<Key> row : rows) {
            for (Key k : row) drawKey(c, k, touchFor(k));
        }
    }

    private Touch touchFor(Key k) {
        for (int i = 0; i < touches.size(); i++) {
            if (touches.valueAt(i).key == k) return touches.valueAt(i);
        }
        return null;
    }

    private void drawKey(Canvas c, Key k, Touch t) {
        boolean pressed = t != null;
        boolean special = k.type != CHAR && k.type != SPACE;
        int bg = pressed ? colPressed : special ? colSpecial : colKey;
        if (k.type == SHIFT && shift != SHIFT_OFF) bg = colKey;
        boolean accent = k.type == ENTER && enterAccent;
        if (accent) bg = pressed ? colAccentPressed : colAccent;

        float r = dp(6);
        tmp.set(k.rect);
        tmp.offset(0, dp(1));
        fill.setColor(colShadow);
        c.drawRoundRect(tmp, r, r, fill);
        fill.setColor(bg);
        c.drawRoundRect(k.rect, r, r, fill);

        float cx = k.rect.centerX(), cy = k.rect.centerY();
        int fg = accent ? Color.WHITE : colText;
        switch (k.type) {
            case CHAR: drawCharKey(c, k, t); break;
            case SPACE: drawLabel(c, "space", cx, cy, sp(15), colAlt, 255); break;
            case SHIFT: drawShift(c, cx, cy, fg); break;
            case DELETE: drawDelete(c, cx, cy, fg); break;
            case ENTER:
                if (enterLabel != null) drawLabel(c, enterLabel, cx, cy, sp(15), fg, 255);
                else drawReturn(c, cx, cy, fg);
                break;
            default: drawLabel(c, k.main, cx, cy, sp(16), fg, 255); break;
        }
    }

    private void drawCharKey(Canvas c, Key k, Touch t) {
        float cx = k.rect.centerX(), cy = k.rect.centerY(), h = k.rect.height();
        String main = displayMain(k);
        float mainSize = Character.isLetter(k.main.charAt(0)) ? sp(24) : sp(22);
        if (k.alt == null) {
            drawLabel(c, main, cx, cy, mainSize, colText, 255);
            return;
        }
        float p = t == null ? 0f : t.progress;
        // Resting: small alt character on top, letter just below centre.
        // Pulling down: letter slides out the bottom, alt grows into the centre.
        float mainY = cy + h * 0.10f + p * h * 0.5f;
        float altY = lerp(k.rect.top + h * 0.22f, cy, p);
        float altSize = lerp(sp(12), sp(24), p);
        c.save();
        c.clipRect(k.rect);
        drawLabel(c, main, cx, mainY, mainSize, colText, (int) (255 * (1f - p)));
        drawLabel(c, k.alt, cx, altY, altSize, blend(colAlt, colText, p), 255);
        c.restore();
    }

    private void drawLabel(Canvas c, String s, float cx, float cy, float size, int color, int alpha) {
        if (alpha <= 0) return;
        text.setTextSize(size);
        text.setColor(color);
        text.setAlpha(alpha);
        text.getFontMetrics(fm);
        c.drawText(s, cx, cy - (fm.ascent + fm.descent) / 2f, text);
    }

    private void drawShift(Canvas c, float cx, float cy, int color) {
        float s = dp(18), stem = s / 4.5f;
        cy -= dp(1);
        path.reset();
        path.moveTo(cx, cy - s / 2);
        path.lineTo(cx + s / 2, cy);
        path.lineTo(cx + stem, cy);
        path.lineTo(cx + stem, cy + s / 2);
        path.lineTo(cx - stem, cy + s / 2);
        path.lineTo(cx - stem, cy);
        path.lineTo(cx - s / 2, cy);
        path.close();
        icon.setColor(color);
        icon.setStyle(shift == SHIFT_OFF ? Paint.Style.STROKE : Paint.Style.FILL_AND_STROKE);
        c.drawPath(path, icon);
        if (shift == SHIFT_LOCK) {
            icon.setStyle(Paint.Style.STROKE);
            float y = cy + s / 2 + dp(3.5f);
            c.drawLine(cx - stem, y, cx + stem, y, icon);
        }
    }

    private void drawDelete(Canvas c, float cx, float cy, int color) {
        float w2 = dp(11), h2 = dp(7.5f);
        path.reset();
        path.moveTo(cx - w2, cy);
        path.lineTo(cx - w2 + dp(6), cy - h2);
        path.lineTo(cx + w2, cy - h2);
        path.lineTo(cx + w2, cy + h2);
        path.lineTo(cx - w2 + dp(6), cy + h2);
        path.close();
        icon.setColor(color);
        icon.setStyle(Paint.Style.STROKE);
        c.drawPath(path, icon);
        float xc = cx + dp(2.5f), d = dp(3.2f);
        c.drawLine(xc - d, cy - d, xc + d, cy + d, icon);
        c.drawLine(xc - d, cy + d, xc + d, cy - d, icon);
    }

    private void drawReturn(Canvas c, float cx, float cy, int color) {
        float r = dp(8), y = cy + dp(2), a = dp(4);
        icon.setColor(color);
        icon.setStyle(Paint.Style.STROKE);
        path.reset();
        path.moveTo(cx + r, cy - r);
        path.lineTo(cx + r, y);
        path.lineTo(cx - r, y);
        path.moveTo(cx - r + a, y - a);
        path.lineTo(cx - r, y);
        path.lineTo(cx - r + a, y + a);
        c.drawPath(path, icon);
    }

    // ---------------------------------------------------------------- helpers

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, getResources().getDisplayMetrics());
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    private static int blend(int a, int b, float t) {
        int r = (int) lerp(Color.red(a), Color.red(b), t);
        int g = (int) lerp(Color.green(a), Color.green(b), t);
        int bl = (int) lerp(Color.blue(a), Color.blue(b), t);
        return Color.rgb(r, g, bl);
    }
}
