package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.generator;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

import cc.cassian.rrv.api.recipe.ReliableServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.crafting.CraftingRecipeParser;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.drops.DropsRecipeParser;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.forge.ForgeRecipeParser;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.kat.KatUpgradeRecipeParser;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.npc.NpcInfoRecipeBuilder;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.npc.NpcShopRecipeParser;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.trade.TradeRecipeParser;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.wiki.WikiInfoRecipeBuilder;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItem;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItemRegistry;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts every {@link NeuItem} in the registry into the full set of RRV server recipes.
 *
 * <p>Dispatches each item through a fixed pipeline:
 * <ol>
 *   <li>Legacy crafting (from {@code item.recipe})</li>
 *   <li>Every modern {@code recipes[]} entry, switched on its {@code type} field</li>
 *   <li>NPC info card when {@code internalName} ends in {@code _NPC}</li>
 *   <li>Wiki-only fallback for items with wiki URLs but no other recipes</li>
 * </ol>
 *
 * <p>After the per-item pass, essence upgrade recipes are appended from Hypixel data.
 */
public final class SkyblockRecipeGenerator {

    private SkyblockRecipeGenerator() {}

    /** Generates every recipe for the current registry snapshot. */
    public static List<ReliableServerRecipe> generateAll() {
        List<ReliableServerRecipe> out = new ArrayList<>();
        if (!NeuItemRegistry.isLoaded()) {
            LOGGER.debug("NEU repo not yet loaded — skipping recipe generation");
            return out;
        }

        for (NeuItem item : NeuItemRegistry.getAll().values()) {
            int before = out.size();

            addIfNotNull(out, CraftingRecipeParser.parseLegacy(item));
            parseModernRecipes(out, item);
            addIfNotNull(out, NpcInfoRecipeBuilder.build(item));

            // Wiki-only fallback only when nothing else was generated for this item.
            if (out.size() == before) {
                addIfNotNull(out, WikiInfoRecipeBuilder.build(item));
            }
        }

        EssenceUpgradeGenerator.generate(out);
        return out;
    }

    /**
     * Generates only essence upgrade recipes. Used by the delta-inject path when Hypixel data
     * becomes available after the initial injection.
     */
    public static List<ReliableServerRecipe> generateEssenceOnly() {
        List<ReliableServerRecipe> out = new ArrayList<>();
        EssenceUpgradeGenerator.generate(out);
        return out;
    }

    /**
     * Walks {@code item.recipes} once, dispatching each entry by its {@code type} field.
     * Unknown types are ignored silently so new NEU types don't crash generation.
     */
    private static void parseModernRecipes(List<ReliableServerRecipe> out, NeuItem item) {
        if (item.recipes == null) return;
        for (JsonObject recipe : item.recipes) {
            if (!recipe.has("type") || !recipe.get("type").isJsonPrimitive()) continue;
            String type = recipe.get("type").getAsString();

            ReliableServerRecipe parsed = switch (type) {
                case "crafting" -> CraftingRecipeParser.parseModern(recipe, item);
                case "forge"    -> ForgeRecipeParser.parse(recipe, item);
                case "npc_shop" -> NpcShopRecipeParser.parse(recipe, item);
                case "trade"    -> TradeRecipeParser.parse(recipe, item);
                case "drops"    -> DropsRecipeParser.parse(recipe, item);
                case "katgrade" -> KatUpgradeRecipeParser.parse(recipe, item);
                default         -> null;
            };
            addIfNotNull(out, parsed);
        }
    }

    private static void addIfNotNull(List<ReliableServerRecipe> out, ReliableServerRecipe r) {
        if (r != null) out.add(r);
    }
}