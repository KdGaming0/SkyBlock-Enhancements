package com.github.kd_gaming1.skyblockenhancements.feature.chat.render;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;

/** Utilities for analysing Hypixel chat text to detect centered lines and separators. */
public final class ChatTextHelper {

    public static final int HYPIXEL_DEFAULT_WIDTH = 320;
    private static final int CENTER_TOLERANCE = 12;

    private ChatTextHelper() {}

    /** Converts a rendered sequence back into a plain string. */
    public static String getString(FormattedCharSequence sequence) {
        StringBuilder sb = new StringBuilder();
        sequence.accept((index, style, cp) -> {
            sb.appendCodePoint(cp);
            return true;
        });
        return sb.toString();
    }

    /** Returns {@code true} if the line appears to be space-padded centered text. */
    public static boolean isCenteredText(Font font, String raw, String trimmed) {
        String cleanRaw = stripCompactSuffix(raw);
        String cleanTrimmed = stripCompactSuffix(trimmed);

        if (!cleanRaw.startsWith(" ") || cleanTrimmed.isEmpty()) return false;

        int leadingSpaces = indexOfFirstNonSpace(cleanRaw);
        if (leadingSpaces < 2) return false;

        int expectedPx = (HYPIXEL_DEFAULT_WIDTH - font.width(cleanTrimmed)) / 2;
        int actualPx = font.width(cleanRaw.substring(0, leadingSpaces));
        return Math.abs(expectedPx - actualPx) <= CENTER_TOLERANCE;
    }

    /**
     * Returns a new {@link FormattedCharSequence} with leading and trailing spaces stripped,
     * preserving all inner formatting. The result is computed eagerly so that repeated calls to
     * {@code accept()} (e.g. on every render frame) don't redo the work.
     */
    public static FormattedCharSequence trim(FormattedCharSequence sequence) {
        // Collect all (index, codepoint, style) triples in one pass.
        List<int[]> cps = new ArrayList<>();
        List<Style> styles = new ArrayList<>();
        sequence.accept((index, style, cp) -> {
            cps.add(new int[]{index, cp});
            styles.add(style);
            return true;
        });

        // Determine the trimmed range.
        int start = 0;
        while (start < cps.size() && cps.get(start)[1] == ' ') start++;
        int end = cps.size() - 1;
        while (end >= 0 && cps.get(end)[1] == ' ') end--;

        // Capture final bounds; the lambda below only iterates the pre-computed slice.
        final int from = start;
        final int to = end;
        return sink -> {
            for (int i = from; i <= to; i++) {
                int[] d = cps.get(i);
                if (!sink.accept(d[0], styles.get(i), d[1])) return false;
            }
            return true;
        };
    }

    /** Rebuilds a Component from a sequence for word-wrapping by Minecraft. */
    public static Component toComponent(FormattedCharSequence sequence) {
        MutableComponent result = Component.empty();
        StringBuilder sb = new StringBuilder();
        Style[] current = {Style.EMPTY};

        sequence.accept((index, style, cp) -> {
            if (!style.equals(current[0])) {
                if (!sb.isEmpty()) {
                    result.append(Component.literal(sb.toString()).withStyle(current[0]));
                    sb.setLength(0);
                }
                current[0] = style;
            }
            sb.appendCodePoint(cp);
            return true;
        });

        if (!sb.isEmpty()) {
            result.append(Component.literal(sb.toString()).withStyle(current[0]));
        }
        return result;
    }

    /** Extracts the first text color from a sequence, defaulting to opaque white. */
    public static int extractColor(FormattedCharSequence sequence) {
        int[] color = {0xFFFFFFFF};
        sequence.accept((index, style, cp) -> {
            if (style.getColor() != null) {
                color[0] = ARGB.opaque(style.getColor().getValue());
                return false;
            }
            return true;
        });
        return color[0];
    }

    /** Returns {@code true} if the string is composed entirely of separator characters. */
    public static boolean isFullSeparator(String trimmed) {
        String clean = stripCompactSuffix(trimmed);
        if (clean.length() < 5) return false;
        for (int i = 0; i < clean.length(); i++) {
            if (!isSeparatorChar(clean.charAt(i))) return false;
        }
        return true;
    }

    /** Returns {@code true} if dashes flank non-dash text (e.g. {@code ---- Text ----}). */
    public static boolean isCenteredSeparator(String trimmed) {
        String clean = stripCompactSuffix(trimmed);
        if (clean.length() < 10 || !clean.startsWith("-") || !clean.endsWith("-")) return false;

        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            if (c != '-' && c != '—' && c != '=' && c != ' ') return true;
        }
        return false;
    }

    /** Extracts the non-dash text from a centered separator like {@code ---- Text ----}. */
    public static String extractMiddleText(String fullString) {
        String clean = stripCompactSuffix(fullString.trim());
        int start = 0;
        while (start < clean.length() && isDashOrSpace(clean.charAt(start))) start++;
        int end = clean.length() - 1;
        while (end >= 0 && isDashOrSpace(clean.charAt(end))) end--;
        return start <= end ? clean.substring(start, end + 1) : "";
    }

    /**
     * Returns the compact suffix like {@code " (×3)"}, or {@code null} if none.
     *
     * @see #stripCompactSuffix(String)
     */
    public static String extractCompactSuffix(String s) {
        int idx = compactSuffixStart(s);
        return idx < 0 ? null : s.substring(idx);
    }

    /**
     * Strips the {@code (×N)} compact suffix from {@code s}, returning {@code s} unchanged if no
     * valid suffix is found.
     *
     * <p>Inlined to avoid the intermediate substring that the old
     * {@code extractCompactSuffix → suffix.length()} path created.
     */
    public static String stripCompactSuffix(String s) {
        int idx = compactSuffixStart(s);
        return idx < 0 ? s : s.substring(0, idx);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the start index of the {@code " (×N)"} suffix in {@code s}, or {@code -1} if the
     * string has no such suffix. Both {@link #extractCompactSuffix} and {@link #stripCompactSuffix}
     * delegate here so the validation runs exactly once per call.
     */
    private static int compactSuffixStart(String s) {
        int idx = s.lastIndexOf(" (×");
        if (idx <= 0 || !s.endsWith(")")) return -1;
        for (int i = idx + 3; i < s.length() - 1; i++) {
            if (!Character.isDigit(s.charAt(i))) return -1;
        }
        return idx;
    }

    private static boolean isSeparatorChar(char c) {
        return c == '-' || c == '—' || c == '=' || c == '▬';
    }

    private static boolean isDashOrSpace(char c) {
        return c == '-' || c == '—' || c == '=' || c == '▬' || c == ' ';
    }

    private static int indexOfFirstNonSpace(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) != ' ') return i;
        }
        return -1;
    }
}