package uk.dnm.swipekeys;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Emoji picker that takes the place of the keys: scrolling categories, tabs, ABC and delete. */
public class EmojiPanel extends LinearLayout {

    public interface Listener {
        void onEmoji(String emoji);
        void onDelete();
        void onAbc();
    }

    private static final String PREF_RECENT = "recent_emoji";
    private static final int MAX_RECENT = 40;

    private Listener listener;
    private final SharedPreferences prefs;
    private final ScrollView scroll;
    private final Grid grid;
    private final Tabs tabs;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final List<String> titles = new ArrayList<>();
    private final List<String[]> sections = new ArrayList<>();
    private String[][] categories; // filtered to what this phone can draw
    private final List<String> recent = new ArrayList<>();
    private boolean rememberRecents = true;

    private int colBg, colText, colDim, colPressed, colBar;

    public EmojiPanel(Context context) {
        super(context);
        setOrientation(VERTICAL);
        prefs = context.getSharedPreferences("swipekeys", Context.MODE_PRIVATE);
        applyTheme();

        scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(OVER_SCROLL_NEVER);
        grid = new Grid(context);
        scroll.addView(grid, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(scroll, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        tabs = new Tabs(context);
        scroll.setOnScrollChangeListener((v, x, y, ox, oy) -> tabs.setActive(grid.sectionAt(y)));
        addView(tabs, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) dp(46)));
        setBackgroundColor(colBg);
    }

    public void setListener(Listener l) { listener = l; }

    /** With keyboard memory off, picked emoji aren't saved as recents. */
    public void setRememberRecents(boolean on) { rememberRecents = on; }

    /** Call when the panel is shown, so recents are fresh. */
    public void refresh() {
        if (categories == null) {
            Paint probe = new Paint();
            categories = new String[EmojiData.CATEGORIES.length][];
            for (int i = 0; i < categories.length; i++) {
                List<String> ok = new ArrayList<>();
                for (String e : EmojiData.CATEGORIES[i].split(" ")) {
                    if (!e.isEmpty() && probe.hasGlyph(e)) ok.add(e);
                }
                categories[i] = ok.toArray(new String[0]);
            }
        }
        recent.clear();
        String saved = prefs.getString(PREF_RECENT, "");
        if (!saved.isEmpty()) recent.addAll(Arrays.asList(saved.split(" ")));

        titles.clear();
        sections.clear();
        titles.add("Recently used");
        sections.add(recent.toArray(new String[0]));
        for (int i = 0; i < categories.length; i++) {
            titles.add(EmojiData.NAMES[i]);
            sections.add(categories[i]);
        }
        grid.requestLayout();
        grid.invalidate();
        // Open on recents if there are any, otherwise on smileys
        handler.post(() -> {
            int target = recent.isEmpty() ? 1 : 0;
            scroll.scrollTo(0, grid.sectionTop(target));
            tabs.setActive(target);
        });
    }

    private void remember(String e) {
        if (!rememberRecents) return;
        recent.remove(e);
        recent.add(0, e);
        while (recent.size() > MAX_RECENT) recent.remove(recent.size() - 1);
        prefs.edit().putString(PREF_RECENT, String.join(" ", recent)).apply();
        // Recents section updates next time the panel opens, so the grid doesn't jump while picking
    }

    private void applyTheme() {
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        if (dark) {
            colBg = 0xFF1F1F21; colText = Color.WHITE; colDim = 0xFF9C9CA1; colPressed = 0xFF3A3A3D; colBar = 0xFF2A2A2D;
        } else {
            colBg = 0xFFD1D4DA; colText = Color.BLACK; colDim = 0xFF6C6C70; colPressed = 0xFFE9EAEE; colBar = 0xFFC3C7CE;
        }
    }

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, getResources().getDisplayMetrics());
    }

    // ------------------------------------------------------------------ grid

    private final class Grid extends View {
        private final Paint emoji = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint.FontMetrics fm = new Paint.FontMetrics();
        private final Rect clip = new Rect();
        private int[] tops = new int[0];
        private int pressedSection = -1, pressedIndex = -1;
        private float downX, downY;

        Grid(Context c) {
            super(c);
            emoji.setTextAlign(Paint.Align.CENTER);
            title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }

        int cols() { return Math.max(8, (int) (getWidth() / dp(46))); }
        float cell() { return getWidth() / (float) cols(); }
        float header() { return dp(30); }

        int sectionTop(int s) { return s < tops.length ? tops[s] : 0; }

        int sectionAt(int y) {
            int s = 0;
            for (int i = 0; i < tops.length; i++) if (tops[i] <= y + dp(4)) s = i;
            return s;
        }

        @Override
        protected void onMeasure(int ws, int hs) {
            int w = MeasureSpec.getSize(ws);
            int cols = Math.max(8, (int) (w / dp(46)));
            float cell = w / (float) cols;
            tops = new int[sections.size()];
            float y = 0;
            for (int s = 0; s < sections.size(); s++) {
                tops[s] = (int) y;
                int n = sections.get(s).length;
                int rows = n == 0 ? 1 : (n + cols - 1) / cols;
                y += header() + rows * cell;
            }
            setMeasuredDimension(w, (int) (y + dp(8)));
        }

        @Override
        protected void onDraw(Canvas c) {
            c.getClipBounds(clip);
            int cols = cols();
            float cell = cell();
            emoji.setTextSize(Math.min(sp(28), cell * 0.62f));
            emoji.getFontMetrics(fm);
            title.setTextSize(sp(13));
            for (int s = 0; s < sections.size(); s++) {
                float top = tops[s];
                String[] list = sections.get(s);
                int rows = list.length == 0 ? 1 : (list.length + cols - 1) / cols;
                float bottom = top + header() + rows * cell;
                if (bottom < clip.top || top > clip.bottom) continue;
                title.setColor(colDim);
                c.drawText(titles.get(s).toUpperCase(), dp(12), top + header() - dp(9), title);
                if (list.length == 0) {
                    title.setColor(colDim);
                    c.drawText("Emoji you use will show up here", dp(12), top + header() + cell / 2 + dp(4), title);
                    continue;
                }
                for (int i = 0; i < list.length; i++) {
                    float x = (i % cols) * cell, y = top + header() + (i / cols) * cell;
                    if (y + cell < clip.top || y > clip.bottom) continue;
                    if (s == pressedSection && i == pressedIndex) {
                        fill.setColor(colPressed);
                        c.drawRoundRect(x + dp(2), y + dp(2), x + cell - dp(2), y + cell - dp(2), dp(8), dp(8), fill);
                    }
                    c.drawText(list[i], x + cell / 2, y + cell / 2 - (fm.ascent + fm.descent) / 2, emoji);
                }
            }
        }

        private int[] hit(float x, float y) {
            int cols = cols();
            float cell = cell();
            for (int s = sections.size() - 1; s >= 0; s--) {
                if (y < tops[s]) continue;
                float gy = y - tops[s] - header();
                if (gy < 0) return null;
                int idx = (int) (gy / cell) * cols + (int) (x / cell);
                if (idx < 0 || idx >= sections.get(s).length) return null;
                return new int[]{s, idx};
            }
            return null;
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    downX = e.getX();
                    downY = e.getY();
                    int[] h = hit(e.getX(), e.getY());
                    pressedSection = h == null ? -1 : h[0];
                    pressedIndex = h == null ? -1 : h[1];
                    invalidate();
                    return true;
                }
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(e.getX() - downX) > dp(10) || Math.abs(e.getY() - downY) > dp(10)) {
                        pressedSection = pressedIndex = -1;
                        invalidate();
                    }
                    return true;
                case MotionEvent.ACTION_UP: {
                    if (pressedSection >= 0) {
                        String em = sections.get(pressedSection)[pressedIndex];
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        remember(em);
                        if (listener != null) listener.onEmoji(em);
                    }
                    pressedSection = pressedIndex = -1;
                    invalidate();
                    return true;
                }
                default:
                    pressedSection = pressedIndex = -1;
                    invalidate();
                    return true;
            }
        }
    }

    // ------------------------------------------------------------------ bottom tabs

    private final class Tabs extends View {
        private static final int HIT_ABC = -2, HIT_DEL = -3, HIT_NONE = -1;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint icon = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint.FontMetrics fm = new Paint.FontMetrics();
        private int active;
        private int pressed = HIT_NONE;
        private final Runnable repeat = new Runnable() {
            @Override public void run() {
                if (listener != null) listener.onDelete();
                handler.postDelayed(this, 50);
            }
        };

        Tabs(Context c) {
            super(c);
            paint.setTextAlign(Paint.Align.CENTER);
        }

        void setActive(int s) {
            if (s != active) {
                active = s;
                invalidate();
            }
        }

        private float sideW() { return dp(54); }
        private int tabCount() { return EmojiData.TAB_ICONS.length + 1; }
        private float tabW() { return (getWidth() - 2 * sideW()) / tabCount(); }

        @Override
        protected void onDraw(Canvas c) {
            c.drawColor(colBar);
            float h = getHeight(), cy = h / 2, side = sideW(), tw = tabW();
            if (pressed == HIT_ABC) { paint.setColor(colPressed); c.drawCircle(side / 2, cy, dp(19), paint); }
            if (pressed == HIT_DEL) { paint.setColor(colPressed); c.drawCircle(getWidth() - side / 2, cy, dp(19), paint); }

            paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            paint.setTextSize(sp(15));
            paint.setColor(colText);
            paint.setAlpha(255);
            paint.getFontMetrics(fm);
            c.drawText("ABC", side / 2, cy - (fm.ascent + fm.descent) / 2, paint);
            Icons.backspace(c, Icons.stroke(icon, colText, dp(1.7f)), getWidth() - side / 2, cy, dp(22));

            for (int i = 0; i < tabCount(); i++) {
                float cx = side + tw * i + tw / 2;
                boolean on = i == active;
                if (on) {
                    paint.setColor(colPressed);
                    c.drawCircle(cx, cy, Math.min(tw / 2 - dp(1), dp(17)), paint);
                }
                if (i == 0) {
                    Icons.clock(c, Icons.stroke(icon, on ? colText : colDim, dp(1.6f)), cx, cy, dp(17));
                } else {
                    paint.setTypeface(Typeface.DEFAULT);
                    paint.setTextSize(Math.min(sp(19), tw * 0.62f));
                    paint.setColor(Color.BLACK);
                    paint.setAlpha(on ? 255 : 120);
                    paint.getFontMetrics(fm);
                    c.drawText(EmojiData.TAB_ICONS[i - 1], cx, cy - (fm.ascent + fm.descent) / 2, paint);
                    paint.setAlpha(255);
                }
            }
        }

        private int hit(float x) {
            if (x < sideW()) return HIT_ABC;
            if (x > getWidth() - sideW()) return HIT_DEL;
            return Math.max(0, Math.min(tabCount() - 1, (int) ((x - sideW()) / tabW())));
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    pressed = hit(e.getX());
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    if (pressed == HIT_DEL) {
                        if (listener != null) listener.onDelete();
                        handler.postDelayed(repeat, 400);
                    }
                    invalidate();
                    return true;
                case MotionEvent.ACTION_UP: {
                    handler.removeCallbacks(repeat);
                    int p = pressed;
                    pressed = HIT_NONE;
                    if (p == HIT_ABC && hit(e.getX()) == HIT_ABC) {
                        if (listener != null) listener.onAbc();
                    } else if (p >= 0) {
                        int target = hit(e.getX());
                        if (target >= 0) {
                            setActive(target);
                            scroll.smoothScrollTo(0, grid.sectionTop(target));
                        }
                    }
                    invalidate();
                    return true;
                }
                case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(repeat);
                    pressed = HIT_NONE;
                    invalidate();
                    return true;
                default:
                    return true;
            }
        }
    }
}
