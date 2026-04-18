package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.crafting;

import cc.cassian.rrv.api.recipe.ReliableServerRecipe;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.RecipeOutputResolver;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.SlotRefParser;
import com.github.kd_gaming1.skyblockenhancements.repo.item.ItemStackBuilder;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItem;
import com.google.gson.JsonObject;

/**
 * Produces SkyBlock crafting recipes from both the legacy NEU {@code recipe} grid map and
 * modern {@code recipes[]} entries of {@code type == "crafting"}.
 */
public final class CraftingRecipeParser {

    private static final String[] GRID_KEYS = {
            "A1", "A2", "A3", "B1", "B2", "B3", "C1", "C2", "C3"
    };

    private CraftingRecipeParser() {}

    /**
     * Builds a crafting recipe from the legacy {@code item.recipe} map. Returns {@code null}
     * when the item has no legacy recipe data.
     */
    public static ReliableServerRecipe parseLegacy(NeuItem item) {
        if (item.recipe == null || item.recipe.isEmpty()) return null;

        SlotContent[] inputs = new SlotContent[9];
        for (int i = 0; i < 9; i++) {
            inputs[i] = SlotRefParser.parse(item.recipe.get(GRID_KEYS[i]));
        }
        return new SkyblockCraftingServerRecipe(
                inputs,
                SlotContent.of(ItemStackBuilder.build(item).copy()),
                item.getWikiUrls());
    }

    /** Builds a crafting recipe from a single modern {@code recipes[]} entry. */
    public static ReliableServerRecipe parseModern(JsonObject recipe, NeuItem item) {
        SlotContent[] inputs = new SlotContent[9];
        for (int i = 0; i < 9; i++) {
            inputs[i] = SlotRefParser.parse(jsonStr(recipe, GRID_KEYS[i]));
        }
        return new SkyblockCraftingServerRecipe(
                inputs,
                SlotContent.of(RecipeOutputResolver.resolve(recipe, item)),
                item.getWikiUrls());
    }

    private static String jsonStr(JsonObject obj, String key) {
        return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsString() : null;
    }
}