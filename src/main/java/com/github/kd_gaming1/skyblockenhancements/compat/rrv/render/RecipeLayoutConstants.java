package com.github.kd_gaming1.skyblockenhancements.compat.rrv.render;

/**
 * Shared layout metrics for RRV recipe rendering.
 */
public final class RecipeLayoutConstants {

    private RecipeLayoutConstants() {}

    /** Vanilla item-slot edge length in pixels. */
    public static final int SLOT_SIZE = 18;

    /** Width of the standard Wiki button. */
    public static final int WIKI_BUTTON_WIDTH  = 56;

    /** Height of the standard Wiki button. */
    public static final int WIKI_BUTTON_HEIGHT = 12;

    /**
     * Standard Y offset from the recipe card top to the button row.
     * Used by the majority of 68 px-tall recipe cards.
     */
    public static final int STANDARD_BUTTON_ROW_Y = 56;

    /** Horizontal gap between adjacent buttons. */
    public static final int BUTTON_GAP = 4;
}
