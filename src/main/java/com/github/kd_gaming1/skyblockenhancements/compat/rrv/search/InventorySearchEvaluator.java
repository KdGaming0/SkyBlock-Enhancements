package com.github.kd_gaming1.skyblockenhancements.compat.rrv.search;

import com.github.kd_gaming1.skyblockenhancements.compat.rrv.category.SkyblockCategoryFilter;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.injection.FullStackListCache;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.SkyblockItemCategory;
import java.util.List;
import java.util.Locale;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import org.jetbrains.annotations.Nullable;

/**
 * Boolean match evaluator for inventory slot highlighting.
 *
 * <p>Uses the pre-built {@link SkyblockSearchIndex} token dictionary for known SkyBlock
 * items, giving full name + lore + category search without invoking tooltip callbacks.
 * Unknown items fall back to display-name containment.
 *
 * <p>Stat threshold clauses are not evaluated for inventory because stat BitSets are
 * indexed by overlay position, not by item ID. This is an accepted limitation: stat
 * filtering works in the RRV item list; inventory highlight supports keywords + category.
 *
 * <p>Enchanted books are handled specially: the enchant name lives in NBT
 * ({@code ExtraAttributes.enchantments}) rather than NEU repo tokens, so it is
 * extracted and checked when the generic token lookup misses.
 *
 * <p>A lightweight lore-component fallback catches any other inventory items whose
 * searchable content is not fully represented in the pre-computed token set (e.g.
 * dynamically-applied reforge names, star counts, etc.). This reads the already-parsed
 * {@link ItemLore} — it does NOT build tooltips.
 */
public final class InventorySearchEvaluator {

    private InventorySearchEvaluator() {}

    /**
     * Returns {@code true} if {@code stack} passes the active category and text filters.
     */
    public static boolean matches(ItemStack stack, String rawQuery,
                                  @Nullable SkyblockItemCategory category,
                                  @Nullable String subCategory) {
        if (stack.isEmpty()) {
            return false;
        }

        // Category gate
        if (category != null && !SkyblockCategoryFilter.matches(stack, category, subCategory)) {
            return false;
        }

        // No text query → category-only mode, slot is visible
        if (rawQuery == null || rawQuery.isBlank()) {
            return true;
        }

        String id = FullStackListCache.getCachedId(stack);
        SkyblockSearchIndex index = FullStackListCache.getSearchIndex();

        // Fast path: known SkyBlock item with a built index
        if (id != null && !id.isEmpty() && index != null) {
            SearchQuery query = SearchQueryParser.parse(rawQuery.toLowerCase(Locale.ROOT));
            if (query.isEmpty()) {
                return true;
            }

            if (index.itemMatchesInventoryQuery(id, query)) {
                return true;
            }

            // Token lookup missed — enchanted books store the enchant name in NBT,
            // not in the NEU repo tokens. Extract and check directly.
            if (id.endsWith("ENCHANTED_BOOK") && enchantNameMatches(stack, query)) {
                return true;
            }

            // General fallback: search through the LORE component (pre-parsed, no
            // tooltip building). Catches dynamic content not captured by NEU tokens.
            return loreMatchesQuery(stack, query);
        }

        // Fallback: vanilla / non-repo items — name-only containment
        String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
        return name.contains(rawQuery.toLowerCase(Locale.ROOT));
    }

    // ── Enchanted book NBT check ───────────────────────────────────────────────

    /**
     * Checks whether any keyword in {@code query} matches the enchantment name stored
     * in the stack's NBT ({@code ExtraAttributes.enchantments}).
     *
     * <p>Only the first enchantment key is read — Hypixel enchanted books contain
     * exactly one enchantment. The comparison is a prefix match so typing
     * {@code "sharp"} still finds {@code "sharpness"}.
     */
    private static boolean enchantNameMatches(ItemStack stack, SearchQuery query) {
        String enchantName = extractEnchantName(stack);
        if (enchantName == null || enchantName.isEmpty()) {
            return false;
        }

        for (SearchQuery.KeywordClause kw : query.keywords()) {
            if (!enchantName.startsWith(kw.token())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Extracts the enchantment name from a Hypixel enchanted book's NBT.
     * Reads {@code ExtraAttributes.enchantments} which stores a single key
     * (the enchant name) mapped to its level.
     *
     * @return lowercased enchant name, or {@code null} if not found
     */
    @Nullable
    private static String extractEnchantName(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }

        CompoundTag tag = data.copyTag();
        CompoundTag extraAttributes = tag.getCompound("ExtraAttributes").orElse(null);
        if (extraAttributes == null) {
            return null;
        }

        CompoundTag enchants = extraAttributes.getCompound("enchantments").orElse(null);
        if (enchants == null || enchants.isEmpty()) {
            return null;
        }

        // Hypixel enchanted books have exactly one enchantment key
        for (String key : enchants.keySet()) {
            return key.toLowerCase(Locale.ROOT);
        }
        return null;
    }

    // ── Lore component fallback ────────────────────────────────────────────────

    /**
     * Searches through the stack's {@link ItemLore} component for query keywords.
     * This is a lightweight fallback that avoids building full tooltips — it reads
     * the already-parsed lore lines directly.
     *
     * <p>Each keyword must appear in at least one lore line (AND semantics).
     * Matching uses simple {@link String#contains} after lowercasing.
     */
    private static boolean loreMatchesQuery(ItemStack stack, SearchQuery query) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) {
            return false;
        }

        List<Component> lines = lore.lines();
        if (lines.isEmpty()) {
            return false;
        }

        for (SearchQuery.KeywordClause kw : query.keywords()) {
            String token = kw.token();
            if (!loreLineContains(lines, token)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns {@code true} if any lore line contains {@code token} as a substring.
     * Lines are converted to lower-case on the fly; short-circuits on first match.
     */
    private static boolean loreLineContains(List<Component> lines, String token) {
        for (Component line : lines) {
            String text = line.getString().toLowerCase(Locale.ROOT);
            if (text.contains(token)) {
                return true;
            }
        }
        return false;
    }
}
