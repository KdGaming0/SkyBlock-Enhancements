package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.drops;

import cc.cassian.rrv.api.recipe.ReliableServerRecipe;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.SlotRefParser;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.JsonUtil;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItem;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Parses a modern {@code recipes[]} entry of {@code type == "drops"}. */
public final class DropsRecipeParser {

    private static final String DEFAULT_MOB_NAME = "Unknown Mob";

    private DropsRecipeParser() {}

    public static ReliableServerRecipe parse(JsonObject recipe, NeuItem item) {
        JsonArray dropsArr = JsonUtil.getArray(recipe, "drops");
        if (dropsArr == null || dropsArr.isEmpty()) return null;

        String mobName = JsonUtil.getString(recipe, "name", DEFAULT_MOB_NAME);
        String renderRef = JsonUtil.getString(recipe, "render");

        int size = dropsArr.size();
        SlotContent[] drops = new SlotContent[size];
        String[] chances = new String[size];
        for (int i = 0; i < size; i++) {
            JsonObject drop = dropsArr.get(i).getAsJsonObject();
            drops[i] = SlotRefParser.parse(JsonUtil.getString(drop, "id"));
            chances[i] = JsonUtil.getString(drop, "chance");
        }

        return new SkyblockDropsServerRecipe(
                mobName, renderRef, drops, chances, item.getWikiUrls());
    }
}
