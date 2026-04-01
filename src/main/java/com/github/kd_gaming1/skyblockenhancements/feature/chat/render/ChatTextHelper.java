package com.github.kd_gaming1.skyblockenhancements.feature.chat.render;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Utilities for analysing Hypixel chat text to detect centered lines and separators.
 */
public final class ChatTextHelper {

    public static final int HYPIXEL_DEFAULT_WIDTH = 320;
    private static final int CENTER_TOLERANCE = 12;

    private ChatTextHelper() {}

    /** Converts a rendered sequence back into a plain string so we can analyze it line-by-line. */
    public static String getString(FormattedCharSequence sequence) {
        StringBuilder sb = new StringBuilder();
        sequence.accept((index, style, cp) -> {
            sb.appendCodePoint(cp);
            return true;
        });
        return sb.toString();
    }

    /**
     * Returns {@code true} if the line appears to be space-padded centered text.
     */
    public static boolean isCenteredText(Font font, String raw, String trimmed) {
        String cleanRaw = stripCompactSuffix(raw);
        String cleanTrimmed = stripCompactSuffix(trimmed);

        if (!cleanRaw.startsWith(" ") || cleanTrimmed.isEmpty()) {
            return false;
        }

        int leadingSpaces = indexOfFirstNonSpace(cleanRaw);
        if (leadingSpaces < 2) {
            return false;
        }

        int expectedLeadingPx = (HYPIXEL_DEFAULT_WIDTH - font.width(cleanTrimmed)) / 2;
        int actualLeadingPx = font.width(cleanRaw.substring(0, leadingSpaces));

        return Math.abs(expectedLeadingPx - actualLeadingPx) <= CENTER_TOLERANCE;
    }

    /**
     * Creates a new FormattedCharSequence with the leading and trailing spaces stripped out,
     * preserving all inner text formatting and colors.
     */
    public static FormattedCharSequence trim(FormattedCharSequence sequence) {
        return sink -> {
            List<CharData> chars = new ArrayList<>();
            sequence.accept((index, style, cp) -> {
                chars.add(new CharData(index, style, cp));
                return true;
            });

            int start = 0;
            while (start < chars.size() && chars.get(start).cp() == ' ') start++;

            int end = chars.size() - 1;
            while (end >= 0 && chars.get(end).cp() == ' ') end--;

            for (int i = start; i <= end; i++) {
                CharData d = chars.get(i);
                if (!sink.accept(d.index(), d.style(), d.cp())) return false;
            }
            return true;
        };
    }

    private record CharData(int index, Style style, int cp) {}

    /** Accurately rebuilds a Component from a sequence so it can be word-wrapped by Minecraft. */
    public static Component toComponent(FormattedCharSequence sequence) {
        final Style[] styleTracker = { Style.EMPTY };
        final StringBuilder sb = new StringBuilder();
        final net.minecraft.network.chat.MutableComponent finalComp = net.minecraft.network.chat.Component.empty();

        sequence.accept((index, style, cp) -> {
            if (!style.equals(styleTracker[0])) {
                if (sb.length() > 0) {
                    finalComp.append(net.minecraft.network.chat.Component.literal(sb.toString()).withStyle(styleTracker[0]));
                    sb.setLength(0);
                }
                styleTracker[0] = style;
            }
            sb.appendCodePoint(cp);
            return true;
        });

        if (sb.length() > 0) {
            finalComp.append(net.minecraft.network.chat.Component.literal(sb.toString()).withStyle(styleTracker[0]));
        }
        return finalComp;
    }

    /** Extracts the first text color from a specific visual line, defaulting to white. */
    public static int extractColor(FormattedCharSequence sequence) {
        final int[] color = { 0xFFFFFFFF };
        sequence.accept((index, style, cp) -> {
            if (style.getColor() != null) {
                color[0] = ARGB.opaque(style.getColor().getValue());
                return false; // Found color, stop iterating
            }
            return true;
        });
        return color[0];
    }

    public static boolean isFullSeparator(String trimmed) {
        String clean = stripCompactSuffix(trimmed);
        if (clean.length() < 5) return false;
        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            if (c != '-' && c != '—' && c != '=') return false;
        }
        return true;
    }

    public static boolean isCenteredSeparator(String trimmed) {
        String clean = stripCompactSuffix(trimmed);
        if (clean.length() < 10) return false;
        if (!clean.startsWith("-") || !clean.endsWith("-")) return false;

        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            if (c != '-' && c != '—' && c != '=' && c != ' ') return true;
        }
        return false;
    }

    public static String extractMiddleText(String fullString) {
        String clean = stripCompactSuffix(fullString.trim());
        int start = 0;
        while (start < clean.length() && isDashOrSpace(clean.charAt(start))) {
            start++;
        }
        int end = clean.length() - 1;
        while (end >= 0 && isDashOrSpace(clean.charAt(end))) {
            end--;
        }
        if (start <= end) {
            return clean.substring(start, end + 1);
        }
        return "";
    }

    public static String extractCompactSuffix(String s) {
        int idx = s.lastIndexOf(" (×");
        if (idx > 0 && s.endsWith(")")) {
            String countStr = s.substring(idx + 3, s.length() - 1);
            boolean allDigits = true;
            for (int i = 0; i < countStr.length(); i++) {
                if (!Character.isDigit(countStr.charAt(i))) {
                    allDigits = false;
                    break;
                }
            }
            if (allDigits && !countStr.isEmpty()) {
                return s.substring(idx);
            }
        }
        return null;
    }

    public static String stripCompactSuffix(String s) {
        String suffix = extractCompactSuffix(s);
        if (suffix != null) {
            return s.substring(0, s.length() - suffix.length());
        }
        return s;
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