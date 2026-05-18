package com.github.kd_gaming1.skyblockenhancements.compat.rrv.search;

import com.github.kd_gaming1.skyblockenhancements.compat.rrv.category.SkyblockCategoryState;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.SkyblockItemCategory;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/**
 * Bridges the inverted {@link SkyblockSearchIndex} with RRV's item list overlay.
 *
 * <p>Converts a raw query string into a ranked {@link List<ItemStack>} that respects
 * RRV's three-tier priority ordering (name match → lore/metadata match → fallback).
 *
 * <p>Category filtering is applied at the index level when a category is active,
 * avoiding the post-search {@code removeIf} scan.
 */
public final class SkyblockSearchFilter {

    private SkyblockSearchFilter() {}

    /**
     * Filters the indexed item list by {@code rawQuery}.
     *
     * @param rawQuery the user's search text (already lowercased by the mixin)
     * @param index    the pre-built search index
     * @return ranked list of matching items, or the full list when {@code rawQuery} is empty
     */
    public static List<ItemStack> filter(String rawQuery, SkyblockSearchIndex index) {
        SearchQuery query = SearchQueryParser.parse(rawQuery);

        // Inject active category filter at the index level if one is selected
        SkyblockItemCategory activeCategory = SkyblockCategoryState.getActiveCategory();
        if (activeCategory != null) {
            query = new SearchQuery(query.keywords(), query.stats(), activeCategory);
        }

        // Empty text + no category → return everything
        if (query.isEmpty()) {
            return new ArrayList<>(index.getItems());
        }

        SearchResult result = index.search(query);

        List<ItemStack> items = index.getItems();
        List<ItemStack> output = new ArrayList<>(result.totalSize());

        appendMatches(output, result.firstPrio(), items);
        appendMatches(output, result.secondPrio(), items);
        appendMatches(output, result.thirdPrio(), items);

        return output;
    }

    private static void appendMatches(List<ItemStack> output, BitSet bits, List<ItemStack> items) {
        for (int i = bits.nextSetBit(0); i >= 0; i = bits.nextSetBit(i + 1)) {
            output.add(items.get(i));
        }
    }
}
