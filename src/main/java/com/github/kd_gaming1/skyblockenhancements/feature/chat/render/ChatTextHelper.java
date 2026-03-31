package com.github.kd_gaming1.skyblockenhancements.feature.chat.render;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.ARGB;
import org.jetbrains.annotations.Nullable;

/**
 * Utilities for analysing Hypixel chat text to detect centered lines and separator patterns.
 *
 * <p>Inspired by BetterHypixelChat by viciscat (GPLv3). This is an original implementation — no
 * code was copied.
 */
public final class ChatTextHelper {

    /** Default chat width Hypixel formats for (in pixels at scale 1). */
    public static final int HYPIXEL_DEFAULT_WIDTH = 320;

    private static final int CENTER_TOLERANCE = 8;

    private ChatTextHelper() {}

    /**
     * Returns {@code true} if the line appears to be space-padded centered text. Hypixel pads lines
     * with leading/trailing spaces to visually center them at the default chat width. This breaks at
     * different widths or fonts.
     */
    public static boolean isCenteredText(Font font, String raw, String trimmed, int chatWidth) {
        if (!raw.startsWith(" ") || trimmed.isEmpty()) return false;

        int leadingSpaces = indexOfFirstNonSpace(raw);
        if (leadingSpaces < 2) return false;

        int trimmedWidth = font.width(trimmed);
        if (trimmedWidth >= chatWidth) return false;

        // Check if the leading whitespace roughly centers the text at the default width.
        int expectedLeadingPx = (HYPIXEL_DEFAULT_WIDTH - trimmedWidth) / 2;
        int actualLeadingPx = font.width(raw.substring(0, leadingSpaces));
        return Math.abs(expectedLeadingPx - actualLeadingPx) <= CENTER_TOLERANCE;
    }

    /**
     * Returns {@code true} if the line is a dash-separated title like {@code -----SkyBlock-----}.
     * The dashes must appear on both sides and the text in the middle must be narrow enough to be
     * decorative rather than content.
     */
    public static boolean isSeparatorLine(Font font, String raw, String trimmed) {
        if (!trimmed.startsWith("-") || !trimmed.endsWith("-")) return false;
        if (trimmed.length() < 5) return false;

        int firstNonDash = indexOfFirstNot(trimmed, '-');
        int lastNonDash = lastIndexOfFirstNot(trimmed, '-');
        // All dashes — still a separator (plain line).
        if (firstNonDash < 0) return true;
        // Dashes on both sides with text in the middle.
        return firstNonDash > 2 && (trimmed.length() - 1 - lastNonDash) > 2;
    }

    /** Extracts the middle text from a separator line, or {@code null} if it is all dashes. */
    @Nullable
    public static String extractSeparatorMiddle(String trimmed) {
        int first = indexOfFirstNot(trimmed, '-');
        int last = lastIndexOfFirstNot(trimmed, '-');
        if (first < 0 || last < first) return null;
        return trimmed.substring(first, last + 1).trim();
    }

    /** Extracts the first text color from a {@link Component}, defaulting to white. */
    public static int extractColor(Component text) {
        Style style = text.getStyle();
        TextColor color = style.getColor();
        if (color != null) return ARGB.opaque(color.getValue());

        for (Component sibling : text.getSiblings()) {
            TextColor sc = sibling.getStyle().getColor();
            if (sc != null) return ARGB.opaque(sc.getValue());
        }
        return 0xFFFFFFFF;
    }

    // --- simple string helpers ---

    private static int indexOfFirstNonSpace(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) != ' ') return i;
        }
        return -1;
    }

    private static int indexOfFirstNot(String s, char c) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) != c) return i;
        }
        return -1;
    }

    private static int lastIndexOfFirstNot(String s, char c) {
        for (int i = s.length() - 1; i >= 0; i--) {
            if (s.charAt(i) != c) return i;
        }
        return -1;
    }
}