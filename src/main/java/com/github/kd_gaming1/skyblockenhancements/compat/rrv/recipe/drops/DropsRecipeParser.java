package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.drops;

import cc.cassian.rrv.api.recipe.ReliableServerRecipe;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.SlotRefParser;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItem;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Parses a modern {@code recipes[]} entry of {@code type == "drops"}. */
public final class DropsRecipeParser {

    private static final String DEFAULT_MOB_NAME = "Unknown Mob";

    private DropsRecipeParser() {}

    public static ReliableServerRecipe parse(JsonObject recipe, NeuItem item) {
        JsonArray dropsArr = recipe.has("drops") ? recipe.getAsJsonArray("drops") : null;
        if (dropsArr == null || dropsArr.isEmpty()) return null;

        String mobName = recipe.has("name") ? recipe.get("name").getAsString() : DEFAULT_MOB_NAME;

        int size = dropsArr.size();
        SlotContent[] drops = new SlotContent[size];
        String[] chances = new String[size];
        for (int i = 0; i < size; i++) {
            JsonObject drop = dropsArr.get(i).getAsJsonObject();
            drops[i] = SlotRefParser.parse(str(drop, "id"));
            chances[i] = str(drop, "chance");
        }

        int level = recipe.has("level") ? recipe.get("level").getAsInt() : 0;
        int combatXp = recipe.has("combat_xp") ? recipe.get("combat_xp").getAsInt() : 0;

        return new SkyblockDropsServerRecipe(
                mobName, drops, chances, level, combatXp, item.getWikiUrls());
    }

    private static String str(JsonObject obj, String key) {
        return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsString() : null;
    }
}