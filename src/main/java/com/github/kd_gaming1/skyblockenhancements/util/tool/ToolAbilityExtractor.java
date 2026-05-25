package com.github.kd_gaming1.skyblockenhancements.util.tool;

import com.github.kd_gaming1.skyblockenhancements.util.StringUtil;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts pickaxe ability information from a tool's lore lines.
 * Specifically looks for "Mining Speed Boost" ability name and cooldown.
 */
public final class ToolAbilityExtractor {

    private ToolAbilityExtractor() {}

    private static final Pattern ABILITY_NAME_PATTERN = Pattern.compile(
            "Ability:\s*(.+?)\s+(?:RIGHT CLICK|LEFT CLICK|SNEAK)?\s*$"
    );
    private static final Pattern COOLDOWN_PATTERN = Pattern.compile(
            "Cooldown:\s*([0-9.]+)\s*s"
    );
    private static final Pattern DURATION_PATTERN = Pattern.compile(
            "([0-9.]+)\s*s"
    );

    /** Information about a mining tool's ability. */
    public record AbilityInfo(
            String name,
            int cooldownSeconds,
            int durationSeconds,
            boolean isMiningSpeedBoost
    ) {}

    /**
     * Scans the held tool's lore for a Mining Speed Boost ability.
     * Returns AbilityInfo if found, empty AbilityInfo otherwise.
     */
    public static AbilityInfo extract(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return empty();
        List<String> lore = ToolStatExtractor.getLoreLines(stack);
        return extractFromLore(lore);
    }

    static AbilityInfo extractFromLore(List<String> loreLines) {
        if (loreLines == null || loreLines.isEmpty()) return empty();

        String abilityName = null;
        int abilityLineIdx = -1;

        // Pass 1: find the "Ability:" line
        for (int i = 0; i < loreLines.size(); i++) {
            String cleaned = StringUtil.stripColorCodes(loreLines.get(i));
            Matcher m = ABILITY_NAME_PATTERN.matcher(cleaned);
            if (m.find()) {
                abilityName = m.group(1).trim();
                abilityLineIdx = i;
                break;
            }
        }

        if (abilityName == null) return empty();

        boolean isMiningSpeedBoost = isMiningSpeedAbility(abilityName);

        // Pass 2: scan lines after the ability for cooldown and duration
        int cooldownSeconds = -1;
        int durationSeconds = -1;

        for (int i = abilityLineIdx + 1; i < loreLines.size(); i++) {
            String cleaned = StringUtil.stripColorCodes(loreLines.get(i));
            if (cleaned.isEmpty()) break; // stop at blank line

            // Cooldown line: "Cooldown: 120s"
            Matcher cd = COOLDOWN_PATTERN.matcher(cleaned);
            if (cd.find()) {
                try {
                    cooldownSeconds = (int) Double.parseDouble(cd.group(1));
                } catch (NumberFormatException ignored) {}
                continue;
            }

            // Duration in description: "Grants +300% Mining Speed for 15s"
            if (isMiningSpeedBoost && durationSeconds < 0) {
                Matcher dur = DURATION_PATTERN.matcher(cleaned);
                if (dur.find()) {
                    try {
                        durationSeconds = (int) Double.parseDouble(dur.group(1));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        return new AbilityInfo(abilityName, cooldownSeconds, durationSeconds, isMiningSpeedBoost);
    }

    private static boolean isMiningSpeedAbility(String name) {
        String lower = name.toLowerCase();
        return lower.contains("mining speed") || lower.contains("mining_speed");
    }

    private static AbilityInfo empty() {
        return new AbilityInfo("", -1, -1, false);
    }
}
