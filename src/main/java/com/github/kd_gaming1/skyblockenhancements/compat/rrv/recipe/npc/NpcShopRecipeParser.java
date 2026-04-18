package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.npc;

import cc.cassian.rrv.api.recipe.ReliableServerRecipe;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.SlotRefParser;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItem;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItemRegistry;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Parses a modern {@code recipes[]} entry of {@code type == "npc_shop"}. */
public final class NpcShopRecipeParser {

    private NpcShopRecipeParser() {}

    public static ReliableServerRecipe parse(JsonObject recipe, NeuItem item) {
        JsonArray costArr = recipe.has("cost") ? recipe.getAsJsonArray("cost") : null;
        String resultRef = str(recipe);
        if (costArr == null || resultRef == null) return null;

        SlotContent[] costs = new SlotContent[costArr.size()];
        for (int i = 0; i < costArr.size(); i++) {
            costs[i] = SlotRefParser.parse(costArr.get(i).getAsString());
        }

        SlotContent result = SlotRefParser.parse(resultRef);
        String[] resultWikiUrls = resolveResultWikiUrls(resultRef);

        return new SkyblockNpcShopServerRecipe(
                costs,
                result,
                item.internalName,
                item.displayName != null ? item.displayName : "",
                resultWikiUrls);
    }

    /**
     * The result slot carries its own wiki URLs (e.g. opening the shop page for {@code SHINY_ORB}
     * should link to the orb's wiki, not the NPC's). Strip any {@code ":count"} suffix before
     * looking the item up.
     */
    private static String[] resolveResultWikiUrls(String resultRef) {
        int colon = resultRef.indexOf(':');
        String resultId = colon >= 0 ? resultRef.substring(0, colon) : resultRef;
        NeuItem resultItem = NeuItemRegistry.get(resultId);
        return resultItem != null ? resultItem.getWikiUrls() : new String[0];
    }

    private static String str(JsonObject obj) {
        return obj.has("result") && obj.get("result").isJsonPrimitive() ? obj.get("result").getAsString() : null;
    }
}