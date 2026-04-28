package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base;

import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import java.util.ArrayList;
import java.util.List;

/**
 * Intermediate base for recipes whose ingredients are a simple array of slots.
 * Provides a shared {@link #getIngredients()} implementation that filters out
 * {@code null} entries, eliminating the ~8-line loop duplicated in Crafting,
 * Forge, and NPC Shop client recipes.
 *
 * <p>Recipes with composite ingredient lists (e.g. Kat = input + materials,
 * Essence = input + essence + companions) should continue to override
 * {@code getIngredients()} directly.
 */
public abstract class ArraySlotRecipe extends AbstractSkyblockClientRecipe {

    protected ArraySlotRecipe(String[] wikiUrls) {
        super(wikiUrls);
    }

    protected abstract SlotContent[] getInputSlots();

    @Override
    public List<SlotContent> getIngredients() {
        SlotContent[] slots = getInputSlots();
        List<SlotContent> list = new ArrayList<>(slots.length);
        for (SlotContent sc : slots) {
            // Pad empty indices instead of dropping them so length/layout matches
            list.add(sc != null ? sc : SlotRefParser.empty());
        }
        return list;
    }
}