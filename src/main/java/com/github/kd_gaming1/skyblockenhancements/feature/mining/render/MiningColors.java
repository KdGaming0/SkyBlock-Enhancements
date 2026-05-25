package com.github.kd_gaming1.skyblockenhancements.feature.mining.render;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;

/**
 * Colour constants and discrete-band interpolation for the mining progress
 * visualiser.
 *
 * <p>When {@code pingOffsetColorUseMid} is {@code true} (default):
 * <pre>
 * Progress 0%  – 70%  : SOLID RED    ("keep mining")
 * Progress 70% – 73%  : RED → YELLOW blend
 * Progress 73% – 99%  : SOLID YELLOW ("getting close")
 * Progress 100%       : SOLID GREEN  ("switch NOW")
 * Progress 100%+      : FLASHING BRIGHT GREEN ("SWITCH NOW!")
 * </pre>
 *
 * <p>When {@code pingOffsetColorUseMid} is {@code false} — red straight to green:
 * <pre>
 * Progress 0%  – 99%  : SOLID RED    ("keep mining")
 * Progress 100%       : SOLID GREEN  ("switch NOW")
 * Progress 100%+      : FLASHING BRIGHT GREEN ("SWITCH NOW!")
 * </pre>
 */
public final class MiningColors {

    private MiningColors() {}

    /** Outline alpha — fully opaque for crisp edges. */
    public static final int ALPHA_OUTLINE = 0xFF;

    /**
     * Highlight fill alpha — tuned low so the block underneath remains clearly
     * visible. At ~30 % the fill reads as a tinted glow, not a solid paint wash.
     */
    public static final int ALPHA_HIGHLIGHT = 0x50;

    private static final int RED_R = 0xFF,    RED_G = 0x33,    RED_B = 0x33;
    private static final int YELLOW_R = 0xFF, YELLOW_G = 0xCC, YELLOW_B = 0x00;
    private static final int GREEN_R = 0x33,  GREEN_G = 0xDD,  GREEN_B = 0x55;
    private static final int BRIGHT_R = 0x77, BRIGHT_G = 0xFF, BRIGHT_B = 0x77;

    private static final double YELLOW_START = 0.70;
    private static final double FLASH_START = 1.00;
    private static final double BLEND_WIDTH = 0.03;
    private static final int FLASH_PERIOD_TICKS = 2;

    // ── Public API ────────────────────────────────────────────────────────────

    public static int getColor(double progress, int elapsedTick, int alpha) {
        int r, g, b;

        if (progress > FLASH_START) {
            boolean flashOn = (elapsedTick / FLASH_PERIOD_TICKS) % 2 == 0;
            if (flashOn) {
                r = BRIGHT_R; g = BRIGHT_G; b = BRIGHT_B;
            } else {
                r = GREEN_R; g = GREEN_G; b = GREEN_B;
            }
        } else if (progress >= FLASH_START) {
            r = GREEN_R; g = GREEN_G; b = GREEN_B;
        } else if (SkyblockEnhancementsConfig.pingOffsetColorUseMid) {
            // Three-color mode: red → yellow → green
            if (progress >= YELLOW_START + BLEND_WIDTH) {
                r = YELLOW_R; g = YELLOW_G; b = YELLOW_B;
            } else if (progress >= YELLOW_START) {
                double t = (progress - YELLOW_START) / BLEND_WIDTH;
                r = lerp(RED_R, YELLOW_R, t);
                g = lerp(RED_G, YELLOW_G, t);
                b = lerp(RED_B, YELLOW_B, t);
            } else {
                r = RED_R; g = RED_G; b = RED_B;
            }
        } else {
            // Two-color mode: red → green (no yellow)
            r = RED_R; g = RED_G; b = RED_B;
        }

        return (alpha << 24) | (r << 16) | (g << 8) | b;
    }

    public static int getOutlineColor(double progress, int elapsedTick) {
        return getColor(progress, elapsedTick, ALPHA_OUTLINE);
    }

    public static int getHighlightColor(double progress, int elapsedTick) {
        return getColor(progress, elapsedTick, ALPHA_HIGHLIGHT);
    }

    // ── Integer linear interpolation ──────────────────────────────────────────

    private static int lerp(int from, int to, double t) {
        return from + (int) ((to - from) * t);
    }
}
