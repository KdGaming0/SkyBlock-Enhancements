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

            // Numeric threshold filters (slayer>3, skill:combat>20) need structured
            // data from the NeuItem. Evaluate them directly when possible.
            if (!query.filters().isEmpty() && !neuItemMatchesFilters(stack, query)) {
                return false;
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
     * Evaluates numeric-threshold filter clauses against the item's {@link NeuItem} data.
     * String-only filters are skipped (they are already handled by
     * {@link SkyblockSearchIndex#itemMatchesInventoryQuery}).
     *
     * @return {@code true} if all numeric filters pass or no NeuItem is available
     */
    private static boolean neuItemMatchesFilters(ItemStack stack, SearchQuery query) {
        var neuItem = FullStackListCache.getCachedNeuItem(stack);
        if (neuItem == null) {
            return true; // Can't evaluate — be permissive
        }

        for (SearchQuery.FilterClause f : query.filters()) {
            if (f.stringValue() != null && (f.op() == null || f.op() == SearchQuery.FilterClause.Operator.EQ)) {
                continue; // String filters handled by token set
            }

            boolean pass = switch (f.key()) {
                case "slayer" -> neuItemMatchesSlayerFilter(neuItem, f);
                case "skill" -> neuItemMatchesSkillFilter(neuItem, f);
                case "catacombs" -> neuItemMatchesCatacombsFilter(neuItem, f);
                case "rarity", "type", "soulbound", "dungeon", "rift" -> true; // handled by tokens
                default -> true;
            };
            if (!pass) {
                return false;
            }
        }
        return true;
    }

    private static boolean neuItemMatchesSlayerFilter(com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItem neuItem,
                                                      SearchQuery.FilterClause f) {
        if (neuItem.slayerReq == null || neuItem.slayerReq.isEmpty()) {
            return false;
        }
        String type = SkyblockSearchIndex.extractSlayerTypeStatic(neuItem.slayerReq);
        int level = SkyblockSearchIndex.extractSlayerLevelStatic(neuItem.slayerReq);
        if (type == null || level <= 0) {
            return false;
        }
        if (f.stringValue() != null && !type.equals(f.stringValue())) {
            return false;
        }
        return compareInt(level, f.op(), f.intValue());
    }

    private static boolean neuItemMatchesSkillFilter(com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItem neuItem,
                                                     SearchQuery.FilterClause f) {
        if (neuItem.lore == null) {
            return false;
        }
        String targetSkill = f.stringValue();
        for (String line : neuItem.lore) {
            String clean = com.github.kd_gaming1.skyblockenhancements.util.StringUtil.stripColorCodes(line).toLowerCase(java.util.Locale.ROOT);
            int skillIdx = clean.indexOf(" skill ");
            if (skillIdx < 0 || !clean.contains("requires") || clean.contains("catacombs")) {
                continue;
            }
            String afterSkill = clean.substring(skillIdx + 7).trim();
            int end = 0;
            for (int i = 0; i < afterSkill.length(); i++) {
                char c = afterSkill.charAt(i);
                if (c >= '0' && c <= '9') {
                    end = i + 1;
                }
            }
            if (end == 0) {
                continue;
            }
            try {
                int level = Integer.parseInt(afterSkill.substring(0, end));
                String beforeSkill = clean.substring(0, skillIdx).trim();
                int lastSpace = beforeSkill.lastIndexOf(' ');
                String skillName = lastSpace >= 0 ? beforeSkill.substring(lastSpace + 1) : beforeSkill;
                skillName = SkyblockSearchIndex.normalizeFilterTokenStatic(skillName);
                if (targetSkill != null && !targetSkill.equals(skillName)) {
                    continue;
                }
                if (compareInt(level, f.op(), f.intValue())) {
                    return true;
                }
            } catch (NumberFormatException ignored) {}
        }
        return false;
    }

    private static boolean neuItemMatchesCatacombsFilter(com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItem neuItem,
                                                         SearchQuery.FilterClause f) {
        if (neuItem.lore == null) {
            return false;
        }
        for (String line : neuItem.lore) {
            String clean = com.github.kd_gaming1.skyblockenhancements.util.StringUtil.stripColorCodes(line).toLowerCase(java.util.Locale.ROOT);
            if (!clean.contains("catacombs")) {
                continue;
            }
            int skillIdx = clean.indexOf(" skill ");
            if (skillIdx >= 0 && clean.contains("requires")) {
                String afterSkill = clean.substring(skillIdx + 7).trim();
                int end = 0;
                for (int i = 0; i < afterSkill.length(); i++) {
                    char c = afterSkill.charAt(i);
                    if (c >= '0' && c <= '9') {
                        end = i + 1;
                    }
                }
                if (end > 0) {
                    try {
                        int level = Integer.parseInt(afterSkill.substring(0, end));
                        if (compareInt(level, f.op(), f.intValue())) {
                            return true;
                        }
                    } catch (NumberFormatException ignored) {}
                }
                continue;
            }
            int floorIdx = clean.indexOf("floor ");
            if (floorIdx >= 0) {
                String afterFloor = clean.substring(floorIdx + 6).trim();
                int space = afterFloor.indexOf(' ');
                if (space > 0) {
                    afterFloor = afterFloor.substring(0, space);
                }
                afterFloor = afterFloor.replace(".", "").trim();
                int level = SkyblockSearchIndex.romanToIntStatic(afterFloor);
                if (level > 0 && compareInt(level, f.op(), f.intValue())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean compareInt(int actual, SearchQuery.FilterClause.Operator op, int expected) {
        if (op == null) {
            return actual == expected;
        }
        return switch (op) {
            case EQ -> actual == expected;
            case GT -> actual > expected;
            case LT -> actual < expected;
            case GTE -> actual >= expected;
            case LTE -> actual <= expected;
        };
    }

    /**
     * Searches through the stack's {@link ItemLore} component for query keywords and
     * string filter values. This is a lightweight fallback that avoids building full
     * tooltips — it reads the already-parsed lore lines directly.
     *
     * <p>Each keyword and each string filter value must appear in at least one lore line
     * (AND semantics). Matching uses simple {@link String#contains} after lowercasing.
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

        for (SearchQuery.FilterClause f : query.filters()) {
            String value = f.stringValue();
            if (value != null && (f.op() == null || f.op() == SearchQuery.FilterClause.Operator.EQ)) {
                if (!loreLineContains(lines, value)) {
                    return false;
                }
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
