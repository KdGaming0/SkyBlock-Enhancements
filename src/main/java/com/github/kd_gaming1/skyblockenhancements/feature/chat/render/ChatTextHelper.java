package com.github.kd_gaming1.skyblockenhancements.feature.chat.render;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.ARGB;

/**
 * Utilities for analysing Hypixel chat text to detect centered lines and separators.
 */
public final class ChatTextHelper {

    /** Default chat width Hypixel formats for (in pixels at scale 1). */
    public static final int HYPIXEL_DEFAULT_WIDTH = 320;

    private static final int CENTER_TOLERANCE = 8;

    private ChatTextHelper() {}

    /**
     * Returns {@code true} if the line appears to be space-padded centered text.
     */
    public static boolean isCenteredText(Font font, String raw, String trimmed, int chatWidth) {
        if (!raw.startsWith(" ") || trimmed.isEmpty()) return false;

        int leadingSpaces = indexOfFirstNonSpace(raw);
        if (leadingSpaces < 2) return false;

        int trimmedWidth = font.width(trimmed);
        if (trimmedWidth >= chatWidth) return false;

        int expectedLeadingPx = (HYPIXEL_DEFAULT_WIDTH - trimmedWidth) / 2;
        int actualLeadingPx = font.width(raw.substring(0, leadingSpaces));
        return Math.abs(expectedLeadingPx - actualLeadingPx) <= CENTER_TOLERANCE;
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

    /** Checks if the string consists entirely of dash-like characters. */
    public static boolean isFullSeparator(String trimmed) {
        if (trimmed.length() < 5) return false;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c != '-' && c != '—' && c != '=') return false;
        }
        return true;
    }

    /** Checks if the string starts and ends with dashes but has text in the middle. */
    public static boolean isCenteredSeparator(String trimmed) {
        if (trimmed.length() < 10) return false;
        if (!trimmed.startsWith("-") || !trimmed.endsWith("-")) return false;

        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c != '-' && c != '—' && c != '=' && c != ' ') return true;
        }
        return false;
    }

    /** Extracts the actual text out of a centered separator (e.g. "--- SkyBlock ---" -> "SkyBlock"). */
    public static String extractMiddleText(String fullString) {
        String trimmed = fullString.trim();
        int start = 0;
        while (start < trimmed.length() && isDashOrSpace(trimmed.charAt(start))) {
            start++;
        }
        int end = trimmed.length() - 1;
        while (end >= 0 && isDashOrSpace(trimmed.charAt(end))) {
            end--;
        }
        if (start <= end) {
            return trimmed.substring(start, end + 1);
        }
        return "";
    }

    private static boolean isDashOrSpace(char c) {
        return c == '-' || c == '—' || c == '=' || c == ' ';
    }

    private static int indexOfFirstNonSpace(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) != ' ') return i;
        }
        return -1;
    }
}