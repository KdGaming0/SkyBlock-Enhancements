package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.trade;

import cc.cassian.rrv.api.recipe.ReliableServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.SlotRefParser;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItem;
import com.google.gson.JsonObject;

/** Parses a modern {@code recipes[]} entry of {@code type == "trade"}. */
public final class TradeRecipeParser {

    private TradeRecipeParser() {}

    public static ReliableServerRecipe parse(JsonObject recipe, NeuItem item) {
        String costRef = str(recipe, "cost");
        String resultRef = str(recipe, "result");
        if (costRef == null || resultRef == null) return null;

        return new SkyblockTradeServerRecipe(
                SlotRefParser.parse(costRef),
                SlotRefParser.parse(resultRef),
                item.getWikiUrls());
    }

    private static String str(JsonObject obj, String key) {
        return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsString() : null;
    }
}