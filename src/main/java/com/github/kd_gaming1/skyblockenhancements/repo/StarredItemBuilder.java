package com.github.kd_gaming1.skyblockenhancements.repo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.jetbrains.annotations.Nullable;

/**
 * Builds the output {@link ItemStack} for an essence upgrade recipe.
 *
 * <p>Two things are modified on the copy relative to the base item:
 * <ol>
 *   <li><b>Name</b> — {@code §e★{star} } is prepended to the original display name.</li>
 *   <li><b>Lore</b> — each lore line is scanned with a pre-compiled {@link Pattern} per API stat
 *       name; if a line contains a matching stat label, the numeric value is replaced with the
 *       value from {@code tieredStats[statName][star - 1]}.</li>
 * </ol>
 *
 * <p>This approach is deliberately simple: the output stack carries the correct data as plain
 * lore text, so RRV's standard tooltip rendering shows it with zero extra code.
 */
public final class StarredItemBuilder {

    /**
     * Maps API stat names (as returned by {@code tiered_stats} keys) to the display text
     * as it appears in NEU repo lore lines.
     * Pattern per entry: {@code (?i)(displayName:\s*(?:§[0-9a-fk-or])+\+?)(\d+)}
     */
    private static final Map<String, Pattern> STAT_PATTERNS;

    static {
        // Key = API stat name, value = the exact substring that precedes the number in lore.
        // All are matched case-insensitively to handle any future lore capitalisation variance.
        Map<String, String> displayNames = Map.of(
                "DAMAGE",          "damage",
                "STRENGTH",        "strength",
                "DEFENSE",         "defense",
                "HEALTH",          "health",
                "INTELLIGENCE",    "intelligence",
                "CRITICAL_DAMAGE", "crit damage",
                "CRITICAL_CHANCE", "crit chance",
                "WALK_SPEED",      "speed",
                "ATTACK_SPEED",    "bonus attack speed"
        );

        Map<String, Pattern> patterns = new HashMap<>(displayNames.size());
        for (var entry : displayNames.entrySet()) {
            // Matches "StatLabel: §X+digits" or "StatLabel: §X§Xdigits"
            // Group 1 = everything up to and including the last formatting code + optional '+'
            // Group 2 = the numeric value to replace
            patterns.put(entry.getKey(), Pattern.compile(
                    "(?i)(" + Pattern.quote(entry.getValue())
                            + ":\\s*(?:§[0-9a-fk-or])+\\+?)(\\d+)"));
        }
        STAT_PATTERNS = Collections.unmodifiableMap(patterns);
    }

    private StarredItemBuilder() {}

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Returns a copy of the base item's stack with:
     * <ul>
     *   <li>Name: {@code §e★{star} } + original display name (retains original rarity colour)</li>
     *   <li>Lore: stat values updated to {@code tieredStats[stat][star - 1]} where available</li>
     * </ul>
     *
     * <p>If {@code tieredStats} is {@code null} or empty, the lore is left unchanged and only
     * the star name is applied.
     *
     * @param item        source item from the NEU registry
     * @param star        star level (1-indexed, e.g. 1 = ★1)
     * @param tieredStats per-star stat values from {@link HypixelItemsRegistry#getTieredStats},
     *                    or {@code null} if the API has no stat data for this item
     */
    public static ItemStack buildStarredOutput(
            NeuItem item, int star, @Nullable Map<String, int[]> tieredStats) {

        ItemStack stack = ItemStackBuilder.build(item).copy();

        applyStarName(stack, item, star);

        if (tieredStats != null && !tieredStats.isEmpty()) {
            applyTieredStats(stack, star, tieredStats);
        }

        return stack;
    }

    // ── Name ───────────────────────────────────────────────────────────────────

    private static void applyStarName(ItemStack stack, NeuItem item, int star) {
        String base = item.displayName != null ? item.displayName : "";
        String stars = " §6" + "✪".repeat(star);
        // Append gold star glyphs after the original display name
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(base + stars));
    }
    // ── Lore stat replacement ──────────────────────────────────────────────────

    private static void applyTieredStats(ItemStack stack, int star,
                                         Map<String, int[]> tieredStats) {

        ItemLore existing = stack.get(DataComponents.LORE);
        if (existing == null || existing.lines().isEmpty()) return;

        List<Component> updated = new ArrayList<>(existing.lines().size());
        for (Component line : existing.lines()) {
            updated.add(replaceStat(line, star, tieredStats));
        }
        stack.set(DataComponents.LORE, new ItemLore(updated));
    }

    /**
     * Scans a single lore line against every stat pattern and replaces the numeric value
     * for any stat that has data at the requested star level. At most one stat pattern will
     * match per line (SkyBlock lore has one stat per line).
     */
    private static Component replaceStat(Component line, int star,
                                         Map<String, int[]> tieredStats) {

        // Component.literal stores the raw string (with § codes) as its content.
        String raw = line.getString();

        for (var entry : tieredStats.entrySet()) {
            int[] values = entry.getValue();
            // star is 1-indexed; values array is 0-indexed
            if (star > values.length) continue;

            Pattern pattern = STAT_PATTERNS.get(entry.getKey());
            if (pattern == null) continue;

            Matcher matcher = pattern.matcher(raw);
            if (!matcher.find()) continue;

            // Replace only the digit group (group 2), preserving formatting prefix (group 1)
            String replaced = matcher.replaceFirst("$1" + values[star - 1]);
            return Component.literal(replaced);
        }

        return line;
    }
}