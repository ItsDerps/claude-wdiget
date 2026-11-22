package uk.dnm.dothome;

import android.content.Context;

/**
 * Dot Home's looks. Dot: dark, with a halftone dot field and the Glyph ring behind the clock.
 * Paper: Nothing's off-white, light grey dots, white icons with black glyphs, red accents.
 * Wallpaper: your own wallpaper behind Dot's colours.
 */
final class Theme {

    static final int DOT = 0, PAPER = 1, WALLPAPER = 2;

    final int id;
    final boolean light;
    final int bg, dot, dotStrong, glow;        // background, grid dots, halftone peak, touch glow
    final int surface, surfaceLine;            // cards, widgets, dock tray
    final int text, textMute, textFaint;
    final int iconBg, iconRing, glyph;         // app discs and their glyphs
    final int chipBg;
    final int accent = Ui.RED;

    private Theme(int id) {
        this.id = id;
        light = id == PAPER;
        if (light) {
            bg = 0xFFEDEDEA; dot = 0xFFD9D9D4; dotStrong = 0xFFB9B9B3; glow = 0xFF000000;
            surface = 0xFFFFFFFF; surfaceLine = 0xFFDADAD5;
            text = 0xFF0A0A0A; textMute = 0xFF6A6A66; textFaint = 0xFFA5A5A0;
            iconBg = 0xFFFFFFFF; iconRing = 0xFFDCDCD7; glyph = 0xFF0A0A0A;
            chipBg = 0xFFFFFFFF;
        } else {
            bg = 0xFF000000; dot = 0xFF141414; dotStrong = 0xFF2E2E2E; glow = 0xFFFFFFFF;
            surface = 0xFF111111; surfaceLine = 0xFF1F1F1F;
            text = 0xFFFFFFFF; textMute = 0xFF9A9A9A; textFaint = 0xFF555555;
            iconBg = 0xFF1A1A1A; iconRing = 0xFF262626; glyph = 0xFFFFFFFF;
            chipBg = 0xFF121212;
        }
    }

    private static Theme cached;

    static Theme get(Context c) {
        int id = Prefs.get(c).raw().getInt("theme", DOT);
        if (cached == null || cached.id != id) cached = new Theme(id);
        return cached;
    }

    static void set(Context c, int id) {
        Prefs.get(c).raw().edit().putInt("theme", id).apply();
    }
}
