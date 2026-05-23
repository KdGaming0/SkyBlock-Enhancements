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

    /** Subtle border for individual (non-multiblock) cells. */
    public static final int GRID_BORDER_SUBTLE  = 0xFF3d3d3d;

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

    // ── New visual-design tokens ──

    /** Water droplet fill — bright cyan. */
    public static final int WATER_DROPLET_FILL = 0xFF00BCD4;

    /** Water droplet highlight — white dot. */
    public static final int WATER_DROPLET_HIGHLIGHT = 0xFFFFFFFF;

    /** Surface indicator: Farmland — earthy brown. */
    public static final int SURFACE_FARMLAND = 0xFF8B6914;

    /** Surface indicator: Soul Sand — dark sand. */
    public static final int SURFACE_SOUL_SAND = 0xFF4A3728;

    /** Surface indicator: End Stone — pale yellow. */
    public static final int SURFACE_END_STONE = 0xFFDDD6A3;

    /** Surface indicator: Sand — warm beige. */
    public static final int SURFACE_SAND = 0xFFD6CFA6;

    /** Dashed border for target regions (60% opacity blue). */
    public static final int REGION_TARGET_DASHED = 0x996bb3ff;

    /** Dashed border for ingredient regions (60% opacity gold). */
    public static final int REGION_INGREDIENT_DASHED = 0x99c4a44a;

    /** Dimension label text (white at 70% opacity). */
    public static final int DIMENSION_LABEL = 0xB3FFFFFF;

    /** Checkerboard overlay (5% lighter variant for texture). */
    public static final int CHECKERBOARD_LIGHTEN = 0x0DFFFFFF;

    /** Middle-dot separator character for info lines. */
    public static final char MIDDLE_DOT = '\u00B7';
}
