package uk.dnm.dothome;

import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;

/**
 * Turns any app icon into Dot Home's style: a white glyph that sits on a dark disc.
 * Uses the app's own one-colour (themed) icon when it has one (Android 13+); otherwise a
 * greyscale version of its foreground.
 */
final class IconFactory {
    private IconFactory() { }

    /** A size × size bitmap holding just the glyph, already clipped to the disc. */
    static Bitmap make(Context c, LauncherActivityInfo info, int size) {
        int density = c.getResources().getDisplayMetrics().densityDpi;
        Drawable d = info.getIcon(density);
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Path circle = new Path();
        circle.addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW);
        cv.clipPath(circle);

        if (d instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable a = (AdaptiveIconDrawable) d;
            Drawable mono = Build.VERSION.SDK_INT >= 33 ? a.getMonochrome() : null;
            // The visible circle of an adaptive icon is 72 of its 108 units, so the layer is 1.5× the disc
            int full = Math.round(size * 1.5f);
            int off = (size - full) / 2;
            if (mono != null) {
                mono = mono.mutate();
                mono.setColorFilter(new PorterDuffColorFilter(Ui.WHITE, PorterDuff.Mode.SRC_IN));
                mono.setBounds(off, off, off + full, off + full);
                mono.draw(cv);
                return b;
            }
            Drawable fg = a.getForeground();
            if (fg != null) {
                fg = fg.mutate();
                fg.setColorFilter(greyscale());
                fg.setBounds(off, off, off + full, off + full);
                fg.draw(cv);
                return b;
            }
        }
        // Old-style icon: greyscale and smaller, so it reads as a glyph rather than a sticker
        if (d != null) {
            d = d.mutate();
            d.setColorFilter(greyscale());
            int inner = Math.round(size * 0.66f), off = (size - inner) / 2;
            d.setBounds(off, off, off + inner, off + inner);
            d.draw(cv);
        }
        return b;
    }

    private static ColorMatrixColorFilter greyscale() {
        ColorMatrix m = new ColorMatrix();
        m.setSaturation(0);
        // Lift and stretch contrast a little so dark logos still show on the dark disc
        ColorMatrix contrast = new ColorMatrix(new float[]{
                1.25f, 0, 0, 0, 30,
                0, 1.25f, 0, 0, 30,
                0, 0, 1.25f, 0, 30,
                0, 0, 0, 1, 0});
        m.postConcat(contrast);
        return new ColorMatrixColorFilter(m);
    }
}
