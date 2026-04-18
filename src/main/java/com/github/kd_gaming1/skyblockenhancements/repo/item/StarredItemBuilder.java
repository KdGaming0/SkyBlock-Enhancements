package com.github.kd_gaming1.skyblockenhancements.repo.item;

import java.util.ArrayList;
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
import com.github.kd_gaming1.skyblockenhancements.repo.hypixel.EssenceStat;
import com.github.kd_gaming1.skyblockenhancements.repo.hypixel.EssenceStatResolver;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItem;

/**
 * Builds the input and output {@link ItemStack}s for an essence upgrade recipe.
 *
 * <p>Input = unstarred base for ★1, or the {@code star-1} state otherwise.<br>
 * Output = the item at {@code star}, with stat lines rewritten in place.
 *
 * <p><b>Lore handling:</b> the original NEU repo lore is preserved verbatim. Each line is
 * scanned against a per-stat {@link Pattern}; when a line matches a known stat, the numeric
 * value is replaced with the new value and a green {@code (+Δ)} delta is appended. Lines
 * that don't match any stat pattern (ability text, set bonuses, reforge notes, dungeon tier,
 * etc.) pass through unchanged.
 *
 * <p>Stat values come from {@link EssenceStatResolver} — tiered_stats when present, otherwise
 * base × (1 + 0.02 × star). Items with no stat data keep their lore entirely unchanged.
 */
public final class StarredItemBuilder {

    /** One regex per stat; matches "Label: §color[+]digits". Group 1 = prefix, group 2 = digits. */
    private static final Map<EssenceStat, Pattern> STAT_PATTERNS = buildStatPatterns();

    private static final String DELTA_COLOR    = "§a";
    private static final String DELTA_BRACKETS = "§8";

    private StarredItemBuilder() {}

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Builds the input side of a star recipe — the item as it looks *before* applying this star.
     * For ★1 this is the unstarred base. For ★N (N≥2) this is the ★(N-1) form.
     */
    public static ItemStack buildInput(NeuItem item, int star) {
        if (star <= 1) {
            return ItemStackBuilder.build(item).copy();
        }
        int previousStar = star - 1;
        Map<EssenceStat, Integer> snapshot = EssenceStatResolver.resolve(item.internalName, previousStar);
        return buildStack(item, previousStar, snapshot, null);
    }

    /**
     * Builds the output side of a star recipe — the item at {@code star}, with stat lines
     * showing the new value and the delta from the previous tier.
     */
    public static ItemStack buildOutput(NeuItem item, int star) {
        Map<EssenceStat, Integer> after  = EssenceStatResolver.resolve(item.internalName, star);
        Map<EssenceStat, Integer> before = star > 0
                ? EssenceStatResolver.resolve(item.internalName, star - 1)
                : null;
        return buildStack(item, star, after, before);
    }

    // ── Core builder ───────────────────────────────────────────────────────────

    private static ItemStack buildStack(NeuItem item, int star,
                                        @Nullable Map<EssenceStat, Integer> after,
                                        @Nullable Map<EssenceStat, Integer> before) {

        ItemStack stack = ItemStackBuilder.build(item).copy();
        applyStarName(stack, item, star);

        // No stat data — leave NEU lore untouched.
        if (after == null || after.isEmpty()) return stack;

        ItemLore existing = stack.get(DataComponents.LORE);
        if (existing == null || existing.lines().isEmpty()) return stack;

        List<Component> updated = new ArrayList<>(existing.lines().size());
        for (Component line : existing.lines()) {
            updated.add(rewriteStatLine(line, after, before));
        }
        stack.set(DataComponents.LORE, new ItemLore(updated));
        return stack;
    }

    private static void applyStarName(ItemStack stack, NeuItem item, int star) {
        String base  = item.displayName != null ? item.displayName : item.internalName;
        String stars = star > 0 ? " §6" + "✪".repeat(star) : "";
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(base + stars));
    }

    // ── Line rewriting ────────────────────────────────────────────────────────

    /**
     * If the line matches a known stat pattern, rebuild it with the new value + delta.
     * Otherwise return it unchanged. At most one stat matches per line.
     */
    private static Component rewriteStatLine(Component line,
                                             Map<EssenceStat, Integer> after,
                                             @Nullable Map<EssenceStat, Integer> before) {

        String raw = line.getString();

        for (var entry : after.entrySet()) {
            EssenceStat stat = entry.getKey();
            Pattern pattern  = STAT_PATTERNS.get(stat);
            if (pattern == null) continue;

            Matcher m = pattern.matcher(raw);
            if (!m.find()) continue;

            int afterValue = entry.getValue();
            Integer beforeBoxed = before != null ? before.get(stat) : null;
            return Component.literal(rebuildWithDelta(m, raw, afterValue, beforeBoxed));
        }
        return line;
    }

    /**
     * Returns the rewritten line: {@code prefix + newValue + optionalPercent + (+Δ) + tail}.
     * The optional {@code '%'} is captured separately so the delta sits <em>after</em> the
     * percent sign ({@code "+31% (+1)"}), matching SkyBlock's usual formatting.
     */
    private static String rebuildWithDelta(Matcher m, String raw, int afterValue,
                                           @Nullable Integer before) {
        int prefixEnd  = m.end(1); // end of "Label: §color[+]"
        int numberEnd  = m.end(2); // end of the digits
        int percentEnd = m.end(3); // == numberEnd when no '%' is present

        String prefix  = raw.substring(0, prefixEnd);
        String percent = raw.substring(numberEnd, percentEnd); // "" or "%"
        String tail    = raw.substring(percentEnd);

        StringBuilder sb = new StringBuilder(raw.length() + 16);
        sb.append(prefix).append(afterValue).append(percent);

        if (before != null && before != afterValue) {
            int delta = afterValue - before;
            String sign = delta > 0 ? "+" : "";
            sb.append(' ').append(DELTA_BRACKETS).append('(')
                    .append(DELTA_COLOR).append(sign).append(delta)
                    .append(DELTA_BRACKETS).append(')');
        }
        sb.append(tail);
        return sb.toString();
    }

    // ── Pattern catalog ───────────────────────────────────────────────────────

    /**
     * One pattern per modelled stat. Matches the label, optional formatting codes, an optional
     * {@code '+'}, the digits, and an optional {@code '%'} suffix. Example match against
     * {@code "§7Crit Damage: §c+30%"}:
     * <ul>
     *   <li>group 1 = {@code "§7Crit Damage: §c+"}</li>
     *   <li>group 2 = {@code "30"}</li>
     *   <li>group 3 = {@code "%"}</li>
     * </ul>
     */
    private static Map<EssenceStat, Pattern> buildStatPatterns() {
        Map<EssenceStat, Pattern> out = new HashMap<>();
        for (EssenceStat stat : EssenceStat.values()) {
            String label = stat.displayLabel();
            String regex = "(?i)(" + Pattern.quote(label) + ":\\s*(?:§[0-9a-fk-or])*\\+?)(\\d+)(%?)";
            out.put(stat, Pattern.compile(regex));
        }
        return Map.copyOf(out);
    }
}