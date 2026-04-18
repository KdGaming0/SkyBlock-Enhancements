package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.kat;

import cc.cassian.rrv.api.recipe.ReliableServerRecipe;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.SlotRefParser;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItem;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Parses a modern {@code recipes[]} entry of {@code type == "katgrade"}. */
public final class KatUpgradeRecipeParser {

    private static final SlotContent[] EMPTY_MATERIALS = new SlotContent[0];

    private KatUpgradeRecipeParser() {}

    public static ReliableServerRecipe parse(JsonObject recipe, NeuItem item) {
        String inputRef = str(recipe, "input");
        String outputRef = str(recipe, "output");
        if (inputRef == null || outputRef == null) return null;

        SlotContent[] materials = parseMaterials(recipe);
        int coins = recipe.has("coins") ? recipe.get("coins").getAsInt() : 0;
        int time = recipe.has("time") ? recipe.get("time").getAsInt() : 0;

        return new SkyblockKatUpgradeServerRecipe(
                SlotRefParser.parse(inputRef),
                SlotRefParser.parse(outputRef),
                materials,
                coins,
                time,
                item.getWikiUrls());
    }

    private static SlotContent[] parseMaterials(JsonObject recipe) {
        JsonArray arr = recipe.has("items") ? recipe.getAsJsonArray("items") : null;
        if (arr == null || arr.isEmpty()) return EMPTY_MATERIALS;
        SlotContent[] out = new SlotContent[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            out[i] = SlotRefParser.parse(arr.get(i).getAsString());
        }
        return out;
    }

    private static String str(JsonObject obj, String key) {
        return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsString() : null;
    }
}