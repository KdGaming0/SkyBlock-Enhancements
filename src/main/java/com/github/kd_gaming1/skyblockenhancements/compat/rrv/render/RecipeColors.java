package com.github.kd_gaming1.skyblockenhancements.compat.rrv.render;

/**
 * Centralised ARGB colour constants for RRV recipe rendering.
 * Eliminates magic-number duplication across the nine client recipe views.
 */
public final class RecipeColors {

    private RecipeColors() {}

    // ── General text colours ──

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

    // ── Garden mutation grid ──

    /** Empty cell background — subtle dark grey. */
    public static final int GRID_EMPTY_BG       = 0xFF333333;

    /** Target (center) cell background — saturated blue. */
    public static final int GRID_TARGET_BG      = 0xFF4a90d9;

    /** Ingredient cell background — muted amber. */
    public static final int GRID_INGREDIENT_BG  = 0xFF8B6914;

    /** Cell border — dark with enough contrast for definition. */
    public static final int GRID_BORDER         = 0xFF222222;

    /** Optional highlight border for target cells. */
    public static final int GRID_TARGET_BORDER     = 0xFF6bb3ff;

    /** Optional highlight border for ingredient cells. */
    public static final int GRID_INGREDIENT_BORDER = 0xFFc4a44a;

    // ── Legend / key ──

    /** Legend text colour for the target indicator. */
    public static final int LEGEND_TARGET     = 0xFF4a90d9;

    /** Legend text colour for the ingredient indicator. */
    public static final int LEGEND_INGREDIENT = 0xFFc4a44a;

    /** Legend label text colour (neutral grey). */
    public static final int LEGEND_TEXT       = 0xFF888888;

    // ── Region overlay ──

    /** Semi-transparent white for highlighting multi-cell regions. */
    public static final int REGION_HIGHLIGHT  = 0x40FFFFFF;
}
