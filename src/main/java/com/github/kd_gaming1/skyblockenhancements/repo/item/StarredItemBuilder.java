package com.github.kd_gaming1.skyblockenhancements.repo.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 * scanned against known stat labels; when a line matches a known stat, the numeric
 * value is replaced with the new value and a green {@code (+Δ)} delta is appended. Lines
 * that don't match any stat (ability text, set bonuses, reforge notes, dungeon tier,
 * etc.) pass through unchanged.
 *
 * <p>Stat values come from {@link EssenceStatResolver} — tiered_stats when present, otherwise
 * base × (1 + 0.02 × star). Items with no stat data keep their lore entirely unchanged.
 */
public final class StarredItemBuilder {

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
     * Overload that accepts a pre-built base stack to avoid redundant
     * {@link ItemStackBuilder#build} calls when generating many star levels
     * for the same item.
     */
    public static ItemStack buildInput(NeuItem item, int star, ItemStack base) {
        if (star <= 1) {
            return base.copy();
        }
        int previousStar = star - 1;
        Map<EssenceStat, Integer> snapshot = EssenceStatResolver.resolve(item.internalName, previousStar);
        return buildStack(item, previousStar, snapshot, null, base);
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

    /**
     * Overload that accepts a pre-built base stack to avoid redundant
     * {@link ItemStackBuilder#build} calls when generating many star levels
     * for the same item.
     */
    public static ItemStack buildOutput(NeuItem item, int star, ItemStack base) {
        Map<EssenceStat, Integer> after  = EssenceStatResolver.resolve(item.internalName, star);
        Map<EssenceStat, Integer> before = star > 0
                ? EssenceStatResolver.resolve(item.internalName, star - 1)
                : null;
        return buildStack(item, star, after, before, base);
    }

    // ── Core builder ───────────────────────────────────────────────────────────

    private static ItemStack buildStack(NeuItem item, int star,
                                        @Nullable Map<EssenceStat, Integer> after,
                                        @Nullable Map<EssenceStat, Integer> before) {
        return buildStack(item, star, after, before, ItemStackBuilder.build(item));
    }

    private static ItemStack buildStack(NeuItem item, int star,
                                        @Nullable Map<EssenceStat, Integer> after,
                                        @Nullable Map<EssenceStat, Integer> before,
                                        ItemStack base) {
        ItemStack stack = base.copy();
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
     * If the line matches a known stat label, rebuild it with the new value + delta.
     * Otherwise return it unchanged. At most one stat matches per line.
     *
     * <p>Uses direct {@code indexOf} scanning instead of regex to avoid {@code Matcher}
     * allocation on every lore line.
     */
    private static Component rewriteStatLine(Component line,
                                             Map<EssenceStat, Integer> after,
                                             @Nullable Map<EssenceStat, Integer> before) {

        String raw = line.getString();

        for (var entry : after.entrySet()) {
            EssenceStat stat = entry.getKey();
            String label = stat.displayLabel();
            int labelPos = raw.indexOf(label);
            if (labelPos < 0) continue;

            // Ensure it's actually a stat line: must be followed by ':'
            int afterLabel = labelPos + label.length();
            if (afterLabel >= raw.length() || raw.charAt(afterLabel) != ':') continue;

            // Position right after "Label:"
            int pos = afterLabel + 1;

            // Skip whitespace, colour codes, and optional '+'
            while (pos < raw.length()) {
                char c = raw.charAt(pos);
                if (c == ' ' || c == '+') {
                    pos++;
                } else if (c == '§' && pos + 1 < raw.length()) {
                    pos += 2; // skip formatting code
                } else {
                    break;
                }
            }

            // Parse digits
            int numStart = pos;
            while (pos < raw.length() && raw.charAt(pos) >= '0' && raw.charAt(pos) <= '9') {
                pos++;
            }
            if (numStart == pos) continue; // no digits — not a stat line

            int numberEnd = pos;
            int percentEnd = pos;
            if (pos < raw.length() && raw.charAt(pos) == '%') {
                percentEnd = pos + 1;
            }

            int afterValue = entry.getValue();
            Integer beforeBoxed = before != null ? before.get(stat) : null;
            return Component.literal(rebuildWithDelta(raw, numStart, numberEnd, percentEnd, afterValue, beforeBoxed));
        }
        return line;
    }

    /**
     * Returns the rewritten line: {@code prefix + newValue + optionalPercent + (+Δ) + tail}.
     * The optional {@code '%'} is captured separately so the delta sits <em>after</em> the
     * percent sign ({@code "+31% (+1)"}), matching SkyBlock's usual formatting.
     */
    private static String rebuildWithDelta(String raw, int numStart, int numberEnd,
                                           int percentEnd, int afterValue,
                                           @Nullable Integer before) {
        String percent = raw.substring(numberEnd, percentEnd);
        String tail    = raw.substring(percentEnd);

        StringBuilder sb = new StringBuilder(raw.length() + 16);
        sb.append(raw, 0, numStart).append(afterValue).append(percent);

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
}
