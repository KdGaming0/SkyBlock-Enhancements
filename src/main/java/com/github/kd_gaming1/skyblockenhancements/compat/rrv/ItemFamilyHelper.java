package com.github.kd_gaming1.skyblockenhancements.compat.rrv;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.repo.NeuConstantsRegistry;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

/**
 * Provides item family resolution for compact mode. When compact mode is enabled, child items
 * are hidden from the item grid and only the parent is shown. This helper:
 *
 * <ul>
 *   <li>Checks whether a clicked item's family includes a given internal ID
 *       (so child recipes match the parent item in the grid)</li>
 *   <li>Builds a display name suffix like {@code "I–XII"} from the tier range of a family</li>
 * </ul>
 */
public final class ItemFamilyHelper {

    /** Matches a trailing Roman numeral or Arabic number in a display name (e.g. " I", " XII", " 11"). */
    private static final Pattern TIER_SUFFIX = Pattern.compile("\\s+([IVXLCDM]+|\\d+)$");

    private static final String[] ROMAN = {
            "", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X",
            "XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX",
            "XXI", "XXII", "XXIII", "XXIV", "XXV"
    };

    private ItemFamilyHelper() {}

    public static boolean shouldCompactFamily(String parentId) {
        if (parentId == null) return false;
        if (parentId.contains("_GENERATOR_")) return true;
        if (parentId.contains(";")) {
            return !parentId.startsWith("PET_SKIN_") && !parentId.startsWith("POTION_") && !parentId.matches("^[A-Z_]+;[0-5]$");
        }
        return false;
    }

    /**
     * Returns {@code true} if {@code candidateId} is in the same item family as
     * {@code parentId}. A family consists of the parent itself plus all its children
     * from {@code parents.json}.
     *
     * <p>Only returns {@code true} when compact mode is enabled — in expanded mode,
     * each item stands alone and should only match its own recipes.
     */
    public static boolean isFamilyMember(String parentId, String candidateId) {
        if (parentId == null || candidateId == null) return false;
        if (parentId.equals(candidateId)) return true;

        List<String> children = NeuConstantsRegistry.getChildren(parentId);
        if (!children.isEmpty()) {
            return children.contains(candidateId);
        }

        String realParent = NeuConstantsRegistry.getParent(parentId);
        if (realParent != null) {
            if (realParent.equals(candidateId)) return true;
            return NeuConstantsRegistry.getChildren(realParent).contains(candidateId);
        }

        return false;
    }

    /**
     * Builds a compact display name for a parent item by replacing its tier suffix with
     * a range. For example, if the parent is "Creeper Minion I" and it has 10 children
     * (tiers 2–11), returns "Creeper Minion I–XI".
     *
     * <p>Returns {@code null} if the item has no children or the name doesn't end with
     * a recognizable tier suffix.
     *
     * @param parentId    the internal name of the parent item
     * @param displayName the current display name (with {@code §} color codes)
     * @return the modified display name with tier range, or {@code null} if unchanged
     */
    @Nullable
    public static String buildCompactDisplayName(String parentId, String displayName) {
        if (!SkyblockEnhancementsConfig.compactItemList) return null;
        if (!shouldCompactFamily(parentId)) return null;
        if (displayName == null) return null;

        List<String> children = NeuConstantsRegistry.getChildren(parentId);
        if (children.isEmpty()) return null;

        // Total family size = 1 (parent) + children count
        int totalTiers = 1 + children.size();
        if (totalTiers <= 1) return null;

        // Strip color codes for tier detection, then re-apply
        String stripped = displayName.replaceAll("§.", "");
        Matcher matcher = TIER_SUFFIX.matcher(stripped);
        if (!matcher.find()) return null;

        String firstTier = matcher.group(1);
        String lastTier = resolveLastTier(firstTier, totalTiers);
        if (lastTier == null) return null;

        // Find the same suffix position in the original colored string
        // by finding the last occurrence of the tier text
        int suffixStart = findTierSuffixStart(displayName, firstTier);
        if (suffixStart < 0) return null;

        return displayName.substring(0, suffixStart) + firstTier + "–" + lastTier;
    }

    // ── Internal ────────────────────────────────────────────────────────────────

    /**
     * Given the first tier string and total tier count, returns the last tier string.
     * Handles both Roman numerals (I → XII) and Arabic numbers (1 → 12).
     */
    @Nullable
    private static String resolveLastTier(String firstTier, int totalTiers) {
        // Try Arabic number first
        try {
            int first = Integer.parseInt(firstTier);
            return String.valueOf(first + totalTiers - 1);
        } catch (NumberFormatException ignored) {
        }

        // Try Roman numeral
        int firstValue = romanToInt(firstTier);
        if (firstValue > 0) {
            int lastValue = firstValue + totalTiers - 1;
            return intToRoman(lastValue);
        }

        return null;
    }

    /** Finds the start index of the tier suffix in the colored display name. */
    private static int findTierSuffixStart(String displayName, String tierText) {
        // Search backwards for the tier text, skipping color codes
        int searchFrom = displayName.length();
        while (searchFrom > 0) {
            int idx = displayName.lastIndexOf(tierText, searchFrom - 1);
            if (idx < 0) return -1;

            // Check that the char before (ignoring color codes) is a space
            int beforeIdx = idx - 1;
            // Skip backwards past any color code at this position
            while (beforeIdx >= 1 && displayName.charAt(beforeIdx - 1) == '§') {
                beforeIdx -= 2;
            }
            if (beforeIdx >= 0 && displayName.charAt(beforeIdx) == ' ') {
                return idx;
            }
            searchFrom = idx;
        }
        return -1;
    }

    private static int romanToInt(String roman) {
        for (int i = 1; i < ROMAN.length; i++) {
            if (ROMAN[i].equals(roman)) return i;
        }
        return 0;
    }

    private static @NonNull String intToRoman(int value) {
        if (value > 0 && value < ROMAN.length) return ROMAN[value];
        // Fallback for values beyond our table
        return String.valueOf(value);
    }
}