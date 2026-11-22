package uk.dnm.dothome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.View;

/** Shared look and feel: colours, fonts, sizes, haptics and how much motion to use. */
final class Ui {
    private Ui() { }

    static final int BLACK = 0xFF000000;
    static final int SURFACE = 0xFF111111;
    static final int ICON_BG = 0xFF1A1A1A;
    static final int LINE = 0xFF222222;
    static final int LINE2 = 0xFF2A2A2A;
    static final int MUTE = 0xFF8A8A8A;
    static final int DIM = 0xFF555555;
    static final int WHITE = 0xFFFFFFFF;
    static final int RED = 0xFFD71921;

    static final int MOTION_FULL = 0, MOTION_CALM = 1, MOTION_OFF = 2;
    static final int HAPTIC_OFF = 0, HAPTIC_SOFT = 1, HAPTIC_CRISP = 2;

    private static Typeface dots, mono;

    static Typeface dots(Context c) {
        if (dots == null) {
            try { dots = c.getResources().getFont(R.font.doto_black); } catch (Exception e) { dots = Typeface.MONOSPACE; }
        }
        return dots;
    }

    static Typeface mono(Context c) {
        if (mono == null) {
            try { mono = c.getResources().getFont(R.font.space_mono); } catch (Exception e) { mono = Typeface.MONOSPACE; }
        }
        return mono;
    }

    static float dp(Context c, float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    static int dpi(Context c, float v) { return Math.round(dp(c, v)); }

    static float sp(Context c, float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, c.getResources().getDisplayMetrics());
    }

    /** Full, Calm (short fades instead of springs) or Off. Android's "remove animations" forces Off. */
    static int motion(Context c) {
        if (!ValueAnimator.areAnimatorsEnabled()) return MOTION_OFF;
        return Prefs.get(c).motion();
    }

    // ---- haptics: a small palette of short, crisp clicks

    static void tick(View v) { haptic(v, HapticFeedbackConstants.CLOCK_TICK, HapticFeedbackConstants.CLOCK_TICK); }

    static void tap(View v) { haptic(v, HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.KEYBOARD_TAP); }

    static void detent(View v) {
        haptic(v, HapticFeedbackConstants.CLOCK_TICK,
                android.os.Build.VERSION.SDK_INT >= 30 ? HapticFeedbackConstants.GESTURE_END : HapticFeedbackConstants.CONTEXT_CLICK);
    }

    static void firm(View v) { haptic(v, HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.LONG_PRESS); }

    static void letter(View v) {
        haptic(v, HapticFeedbackConstants.CLOCK_TICK, HapticFeedbackConstants.TEXT_HANDLE_MOVE);
    }

    private static void haptic(View v, int soft, int crisp) {
        int level = Prefs.get(v.getContext()).haptics();
        if (level == HAPTIC_OFF) return;
        v.performHapticFeedback(level == HAPTIC_SOFT ? soft : crisp);
    }

    static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    static int clampInt(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
