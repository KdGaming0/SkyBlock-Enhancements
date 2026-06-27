/*
 * Progress-to-colour banding adapted from Revvilon/PingOffsetMiner (PomRendering),
 * CC0-1.0: https://github.com/Revvilon/PingOffsetMiner
 * See THIRD_PARTY_LICENSES.md for the full attribution.
 */

package com.github.kd_gaming1.skyblockenhancements.feature.mining.render;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;

/**
 * Turns mining progress into the ARGB colour the overlay draws.
 *
 * <p>Colours come from the config pickers ({@code pingOffsetColorStart/Mid/End});
 * the highlight fill alpha comes from {@code pingOffsetHighlightAlpha}. The
 * outline is always fully opaque. Bands (three-colour mode, the default):
 * <pre>
 *   0%   – 70%  : start colour        ("keep mining")
 *   70%  – 73%  : start → mid blend
 *   73%  – &lt;100% : mid colour          ("getting close")
 *   &ge;100%      : end colour          ("switch NOW")
 * </pre>
 * Two-colour mode ({@code pingOffsetColorUseMid = false}) drops the mid band:
 * start until 100%, then end.
 */
public final class MiningColors {

    private MiningColors() {}

    /** Fallbacks used when a config colour string fails to parse. */
    private static final int FALLBACK_START = 0xFF3333; // red
    private static final int FALLBACK_MID   = 0xFFCC00; // yellow
    private static final int FALLBACK_END   = 0x33DD55; // green

    private static final double MID_START = 0.70;
    private static final double MID_BLEND_WIDTH = 0.03;

    public static int getOutlineColor(double progress) {
        return packColor(rgbForProgress(progress), 0xFF);
    }

    public static int getHighlightColor(double progress) {
        int alpha = percentToAlpha(SkyblockEnhancementsConfig.pingOffsetHighlightAlpha);
        return packColor(rgbForProgress(progress), alpha);
    }

    private static int rgbForProgress(double progress) {
        int start = parseColor(SkyblockEnhancementsConfig.pingOffsetColorStart, FALLBACK_START);
        int end = parseColor(SkyblockEnhancementsConfig.pingOffsetColorEnd, FALLBACK_END);

        if (progress >= 1.0) return end;
        if (!SkyblockEnhancementsConfig.pingOffsetColorUseMid) return start;

        int mid = parseColor(SkyblockEnhancementsConfig.pingOffsetColorMid, FALLBACK_MID);
        if (progress >= MID_START + MID_BLEND_WIDTH) return mid;
        if (progress >= MID_START) {
            double t = (progress - MID_START) / MID_BLEND_WIDTH;
            return lerpColor(start, mid, t);
        }
        return start;
    }

    // ── Colour helpers ──────────────────────────────────────────────────────

    private static int packColor(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    private static int percentToAlpha(int percent) {
        int clamped = Math.max(0, Math.min(100, percent));
        return Math.round(clamped * 255f / 100f);
    }

    private static int lerpColor(int from, int to, double t) {
        int r = lerp((from >> 16) & 0xFF, (to >> 16) & 0xFF, t);
        int g = lerp((from >> 8) & 0xFF, (to >> 8) & 0xFF, t);
        int b = lerp(from & 0xFF, to & 0xFF, t);
        return (r << 16) | (g << 8) | b;
    }

    private static int lerp(int from, int to, double t) {
        return from + (int) ((to - from) * t);
    }

    /** Parses {@code "#RRGGBB"} (or {@code "RRGGBB"}) to a 24-bit RGB int, defaulting on failure. */
    private static int parseColor(String hex, int fallback) {
        if (hex == null) return fallback;
        try {
            return Integer.parseInt(hex.replace("#", "").trim(), 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
