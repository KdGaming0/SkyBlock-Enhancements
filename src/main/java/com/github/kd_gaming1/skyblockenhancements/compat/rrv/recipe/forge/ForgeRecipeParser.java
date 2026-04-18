package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.forge;

import cc.cassian.rrv.api.recipe.ReliableServerRecipe;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.RecipeOutputResolver;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.SlotRefParser;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItem;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Parses a modern {@code recipes[]} entry of {@code type == "forge"}. */
public final class ForgeRecipeParser {

    private ForgeRecipeParser() {}

    public static ReliableServerRecipe parse(JsonObject recipe, NeuItem item) {
        JsonArray inputsArr = recipe.has("inputs") ? recipe.getAsJsonArray("inputs") : null;
        if (inputsArr == null || inputsArr.isEmpty()) return null;

        SlotContent[] inputs = new SlotContent[inputsArr.size()];
        for (int i = 0; i < inputsArr.size(); i++) {
            inputs[i] = SlotRefParser.parse(inputsArr.get(i).getAsString());
        }

        int duration = recipe.has("duration") ? recipe.get("duration").getAsInt() : 0;

        return new SkyblockForgeServerRecipe(
                inputs,
                SlotContent.of(RecipeOutputResolver.resolve(recipe, item)),
                duration,
                item.getWikiUrls());
    }
}