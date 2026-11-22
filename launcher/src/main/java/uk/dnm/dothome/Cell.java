package uk.dnm.dothome;

import android.graphics.RectF;

/** Anything that occupies a spot on home: an app icon, a stack, a large icon or a large folder. */
interface Cell {
    /** Holds an app or stack (false for an empty edit-mode slot). */
    boolean hasContent();
    /** Edit mode: dashed outline while its content is being dragged away. */
    void setGhost(boolean on);
    /** Edit mode: highlighted as the drop target; {@code makesStack} when dropping will stack. */
    void setDropTarget(boolean on, boolean makesStack);
    /** The round (or square) target area, in the view's own coordinates. */
    RectF discBounds();
}
