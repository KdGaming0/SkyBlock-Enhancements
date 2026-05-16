package com.github.kd_gaming1.skyblockenhancements.util;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Determines the "logical" stack size of an item by inspecting tooltip lore.
 *
 * <p>Hypixel stores the actual quantity in tooltip text for many UI items
 * (Bazaar orders, sacks, compost, etc.) where {@link ItemStack#getCount()}
 * is always 1. This class parses those tooltip lines and falls back to
 * the physical stack count when no special format is detected.
 */
public final class LogicalStackSize {

    private static final String SHORT_NUMBER_FORMAT = "[0-9]+(?:,[0-9]+)*(?:\\.[0-9]+)?[kKmMbB]?";

    private static final Pattern AMOUNT_REGEX = Pattern.compile(
            ".*(?:Offer amount|Amount|Order amount): (" + SHORT_NUMBER_FORMAT + ")x?");

    private static final Pattern STORED_REGEX = Pattern.compile(
            "Stored: (" + SHORT_NUMBER_FORMAT + ")/.+");

    private static final Pattern COMPOST_REGEX = Pattern.compile(
            "Compost Available: (" + SHORT_NUMBER_FORMAT + ")");

    private static final Pattern GEMSTONE_SACK_REGEX = Pattern.compile(
            " Amount: (" + SHORT_NUMBER_FORMAT + ")");

    private static final java.util.Map<Character, Double> SI_SCALARS = java.util.Map.of(
            'k', 1_000.0,
            'K', 1_000.0,
            'm', 1_000_000.0,
            'M', 1_000_000.0,
            'b', 1_000_000_000.0,
            'B', 1_000_000_000.0);

    private LogicalStackSize() {}

    /**
     * Returns the logical stack size for the given item.
     *
     * <p>Uses already-parsed tooltip lines (from the ItemTooltipCallback) to
     * search for known quantity patterns. If no known pattern matches, falls
     * back to {@link ItemStack#getCount()}.
     *
     * @param stack       the item stack to inspect
     * @param tooltipLines the parsed tooltip lines from ItemTooltipCallback
     * @return the logical quantity, always >= 1
     */
    public static long getLogicalStackSize(ItemStack stack, List<Component> tooltipLines) {
        if (tooltipLines != null && !tooltipLines.isEmpty()) {
            for (Component line : tooltipLines) {
                Long parsed = tryParse(line.getString());
                if (parsed != null && parsed > 0) {
                    return parsed;
                }
            }
        }
        return Math.max(1, stack.getCount());
    }

    /** Convenience overload when tooltip lines aren't available yet. */
    public static long getLogicalStackSize(ItemStack stack) {
        return Math.max(1, stack.getCount());
    }

    /** Tries all known patterns against a single tooltip line. */
    private static Long tryParse(String text) {
        Long result = tryPattern(AMOUNT_REGEX, text);
        if (result != null) return result;

        result = tryPattern(GEMSTONE_SACK_REGEX, text);
        if (result != null) return result;

        result = tryPattern(STORED_REGEX, text);
        if (result != null) return result;

        result = tryPattern(COMPOST_REGEX, text);
        return result;
    }

    /** Applies a single regex and parses the captured group as a short number. */
    private static Long tryPattern(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        if (!m.matches()) return null;
        try {
            return parseShortNumber(m.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Parses a short-form number string into a long value.
     *
     * <p>Supports optional commas, decimal points, and SI suffixes:
     * {@code 1,500}, {@code 2.5k}, {@code 3M}, {@code 1.2B}.
     */
    public static long parseShortNumber(String raw) {
        String s = raw.trim();
        if (s.isEmpty()) throw new NumberFormatException("Empty number");

        boolean negative = false;
        if (s.startsWith("-")) {
            negative = true;
            s = s.substring(1);
        } else if (s.startsWith("+")) {
            s = s.substring(1);
        }

        s = s.replace(",", "");
        char last = s.charAt(s.length() - 1);
        Double scalar = SI_SCALARS.get(last);
        double multiplier = 1.0;
        if (scalar != null) {
            s = s.substring(0, s.length() - 1);
            multiplier = scalar;
        }

        double value = Double.parseDouble(s) * multiplier;
        long result = (long) value;
        return negative ? -result : result;
    }
}