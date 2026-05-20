package com.github.kd_gaming1.skyblockenhancements.compat.rrv.render;

/**
 * Centralised ARGB colour constants for RRV recipe rendering.
 * Eliminates magic-number duplication across the nine client recipe views.
 */
public final class RecipeColors {

    private RecipeColors() {}

    /** Arrow glyph ({@code "→"}) — dark grey. */
    public static final int ARROW       = 0xFF404040;

    /** Duration text — medium grey. */
    public static final int DURATION    = 0xFF808080;

    /** Coin-cost text — warm gold. */
    public static final int COINS       = 0xFFAA8800;

    /** Placeholder / subtle text — light grey. */
    public static final int PLACEHOLDER = 0xFFAAAAAA;

    /** NPC coordinate text — slightly darker grey. */
    public static final int NPC_COORDS  = 0xFF888888;

    /** Default white for headers / lore that needs a shadow. */
    public static final int WHITE       = 0xFFFFFFFF;

    /** Dark body text (item names, etc.). */
    public static final int DARK_TEXT   = 0xFF404040;

    /** Garden mutation grid — empty cell background. */
    public static final int GRID_EMPTY_BG = 0xFF2a2a2a;

    /** Garden mutation grid — target (center) cell background. */
    public static final int GRID_TARGET_BG = 0xFF3b6ea5;

    /** Garden mutation grid — ingredient cell background. */
    public static final int GRID_INGREDIENT_BG = 0xFFa56e3b;

    /** Garden mutation grid — cell border. */
    public static final int GRID_BORDER = 0xFF1a1a1a;
}
