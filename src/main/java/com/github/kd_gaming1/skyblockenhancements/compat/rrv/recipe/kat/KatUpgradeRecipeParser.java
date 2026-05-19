package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.kat;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

import cc.cassian.rrv.api.recipe.ReliableServerRecipe;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.SlotRefParser;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.JsonUtil;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItem;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Parses a modern {@code recipes[]} entry of {@code type == "katgrade"}. */
public final class KatUpgradeRecipeParser {

    private static final SlotContent[] EMPTY_MATERIALS = new SlotContent[0];

    private KatUpgradeRecipeParser() {}

    public static ReliableServerRecipe parse(JsonObject recipe, NeuItem item) {
        String inputRef = JsonUtil.getString(recipe, "input");
        String outputRef = JsonUtil.getString(recipe, "output");
        if (inputRef == null || outputRef == null) {
            LOGGER.warn("Kat-grade recipe for {} missing input/output — skipping.", item.internalName);
            return null;
        }

        SlotContent[] materials = parseMaterials(recipe);
        int coins = JsonUtil.getInt(recipe, "coins", 0);
        int time = JsonUtil.getInt(recipe, "time", 0);

        ReliableServerRecipe result = new SkyblockKatUpgradeServerRecipe(
                SlotRefParser.parse(inputRef),
                SlotRefParser.parse(outputRef),
                materials,
                coins,
                time,
                item.getWikiUrls());

        LOGGER.debug("Parsed kat-grade recipe for {}: {} -> {} ({} materials, {} coins, {}s)",
                item.internalName, inputRef, outputRef, materials.length, coins, time);

        return result;
    }

    private static SlotContent[] parseMaterials(JsonObject recipe) {
        JsonArray arr = JsonUtil.getArray(recipe, "items");
        if (arr == null || arr.isEmpty()) return EMPTY_MATERIALS;
        SlotContent[] out = new SlotContent[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            out[i] = SlotRefParser.parse(arr.get(i).getAsString());
        }
        return out;
    }
}
