package com.github.kd_gaming1.skyblockenhancements.compat.rrv;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

import cc.cassian.rrv.api.ReliableRecipeViewerPlugin;
import cc.cassian.rrv.api.recipe.ItemView;
import cc.cassian.rrv.api.recipe.ReliableServerRecipe;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.crafting.SkyblockCraftingServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.drops.SkyblockDropsServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.forge.SkyblockForgeServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.kat.SkyblockKatgradeServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.npc.SkyblockNpcShopServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.trade.SkyblockTradeServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.repo.ItemStackBuilder;
import com.github.kd_gaming1.skyblockenhancements.repo.NeuItem;
import com.github.kd_gaming1.skyblockenhancements.repo.NeuItemRegistry;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;

public class SkyblockRrvPlugin implements ReliableRecipeViewerPlugin {

    private static final String[] GRID_KEYS = {"A1", "A2", "A3", "B1", "B2", "B3", "C1", "C2", "C3"};

    @Override
    public void onIntegrationInitialize() {
        if (!RrvCompat.isActive()) return;

        ItemView.addServerRecipeProvider(recipeList -> {
            recipeList.addAll(generateAllRecipes());
            LOGGER.info("Provided {} total SkyBlock recipes to RRV Server", recipeList.size());
        });
    }

    public static List<ReliableServerRecipe> generateAllRecipes() {
        List<ReliableServerRecipe> list = new ArrayList<>();
        if (!NeuItemRegistry.isLoaded()) {
            LOGGER.debug("NEU repo not yet loaded — skipping recipe generation");
            return list;
        }

        SkyblockRrvPlugin instance = new SkyblockRrvPlugin();
        for (NeuItem item : NeuItemRegistry.getAll().values()) {
            instance.addLegacyCraftingRecipe(list, item);
            instance.addModernRecipes(list, item, "crafting");
            instance.addModernRecipes(list, item, "forge");
            instance.addModernRecipes(list, item, "npc_shop");
            instance.addModernRecipes(list, item, "trade");
            instance.addModernRecipes(list, item, "drops");
            instance.addModernRecipes(list, item, "katgrade");
        }
        return list;
    }

    // ── Legacy crafting ─────────────────────────────────────────────────────────
    private boolean addLegacyCraftingRecipe(List<ReliableServerRecipe> list, NeuItem item) {
        if (item.recipe == null || item.recipe.isEmpty()) return false;

        SlotContent[] inputs = new SlotContent[9];
        for (int i = 0; i < 9; i++) {
            inputs[i] = parseSlotRef(item.recipe.get(GRID_KEYS[i]));
        }

        list.add(
                new SkyblockCraftingServerRecipe(inputs, SlotContent.of(ItemStackBuilder.build(item).copy())));
        return true;
    }

    // ── Modern recipes dispatcher ───────────────────────────────────────────────
    private int addModernRecipes(List<ReliableServerRecipe> list, NeuItem item, String type) {
        if (item.recipes == null) return 0;

        int count = 0;
        for (JsonObject recipe : item.recipes) {
            if (!type.equals(jsonStr(recipe, "type"))) continue;

            ReliableServerRecipe parsed =
                    switch (type) {
                        case "crafting" -> parseCrafting(recipe, item);
                        case "forge" -> parseForge(recipe, item);
                        case "npc_shop" -> parseNpcShop(recipe);
                        case "trade" -> parseTrade(recipe);
                        case "drops" -> parseDrops(recipe);
                        case "katgrade" -> parseKatgrade(recipe);
                        default -> null;
                    };

            if (parsed != null) {
                list.add(parsed);
                count++;
            }
        }
        return count;
    }

    // ── Crafting ────────────────────────────────────────────────────────────────
    private ReliableServerRecipe parseCrafting(JsonObject recipe, NeuItem item) {
        SlotContent[] inputs = new SlotContent[9];
        for (int i = 0; i < 9; i++) {
            inputs[i] = parseSlotRef(jsonStr(recipe, GRID_KEYS[i]));
        }

        ItemStack output = resolveOutput(recipe, item);
        return new SkyblockCraftingServerRecipe(inputs, SlotContent.of(output));
    }

    // ── Forge ───────────────────────────────────────────────────────────────────
    private ReliableServerRecipe parseForge(JsonObject recipe, NeuItem item) {
        JsonArray inputsArr = recipe.has("inputs") ? recipe.getAsJsonArray("inputs") : null;
        if (inputsArr == null || inputsArr.isEmpty()) return null;

        SlotContent[] inputs = new SlotContent[inputsArr.size()];
        for (int i = 0; i < inputsArr.size(); i++) {
            inputs[i] = parseSlotRef(inputsArr.get(i).getAsString());
        }

        ItemStack output = resolveOutput(recipe, item);
        int duration = recipe.has("duration") ? recipe.get("duration").getAsInt() : 0;

        return new SkyblockForgeServerRecipe(inputs, SlotContent.of(output), duration);
    }

    // ── NPC Shop ────────────────────────────────────────────────────────────────
    private ReliableServerRecipe parseNpcShop(JsonObject recipe) {
        JsonArray costArr = recipe.has("cost") ? recipe.getAsJsonArray("cost") : null;
        String resultRef = jsonStr(recipe, "result");
        if (costArr == null || resultRef == null) return null;

        SlotContent[] costs = new SlotContent[costArr.size()];
        for (int i = 0; i < costArr.size(); i++) {
            costs[i] = parseSlotRef(costArr.get(i).getAsString());
        }

        SlotContent result = parseSlotRef(resultRef);
        return new SkyblockNpcShopServerRecipe(costs, result);
    }

    // ── Trade ───────────────────────────────────────────────────────────────────
    private ReliableServerRecipe parseTrade(JsonObject recipe) {
        String costRef = jsonStr(recipe, "cost");
        String resultRef = jsonStr(recipe, "result");
        if (costRef == null || resultRef == null) return null;

        return new SkyblockTradeServerRecipe(parseSlotRef(costRef), parseSlotRef(resultRef));
    }

    // ── Drops ───────────────────────────────────────────────────────────────────
    private ReliableServerRecipe parseDrops(JsonObject recipe) {
        String mobName = jsonStr(recipe, "name");
        if (mobName == null) mobName = "Unknown Mob";

        JsonArray dropsArr = recipe.has("drops") ? recipe.getAsJsonArray("drops") : null;
        if (dropsArr == null || dropsArr.isEmpty()) return null;

        int size = dropsArr.size();
        SlotContent[] drops = new SlotContent[size];
        String[] chances = new String[size];

        for (int i = 0; i < size; i++) {
            JsonObject drop = dropsArr.get(i).getAsJsonObject();
            drops[i] = parseSlotRef(jsonStr(drop, "id"));
            chances[i] = jsonStr(drop, "chance");
        }

        int level = recipe.has("level") ? recipe.get("level").getAsInt() : 0;
        int xp = recipe.has("combat_xp") ? recipe.get("combat_xp").getAsInt() : 0;

        return new SkyblockDropsServerRecipe(mobName, drops, chances, level, xp);
    }

    // ── Katgrade ────────────────────────────────────────────────────────────────
    private ReliableServerRecipe parseKatgrade(JsonObject recipe) {
        String inputRef = jsonStr(recipe, "input");
        String outputRef = jsonStr(recipe, "output");
        if (inputRef == null || outputRef == null) return null;

        SlotContent input = parseSlotRef(inputRef);
        SlotContent output = parseSlotRef(outputRef);

        // Material items
        JsonArray itemsArr = recipe.has("items") ? recipe.getAsJsonArray("items") : null;
        SlotContent[] materials;
        if (itemsArr != null) {
            materials = new SlotContent[itemsArr.size()];
            for (int i = 0; i < itemsArr.size(); i++) {
                materials[i] = parseSlotRef(itemsArr.get(i).getAsString());
            }
        } else {
            materials = new SlotContent[0];
        }

        long coins = recipe.has("coins") ? recipe.get("coins").getAsLong() : 0;
        int time = recipe.has("time") ? recipe.get("time").getAsInt() : 0;

        return new SkyblockKatgradeServerRecipe(input, output, materials, coins, time);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────
    private ItemStack resolveOutput(JsonObject recipe, NeuItem item) {
        String overrideId = jsonStr(recipe, "overrideOutputId");
        ItemStack output;

        if (overrideId != null) {
            NeuItem overrideItem = NeuItemRegistry.get(overrideId);
            output =
                    overrideItem != null
                            ? ItemStackBuilder.build(overrideItem).copy()
                            : ItemStackBuilder.build(item).copy();
        } else {
            output = ItemStackBuilder.build(item).copy();
        }

        int count = recipe.has("count") ? recipe.get("count").getAsInt() : 1;
        if (count > 1) output.setCount(count);
        return output;
    }

    private SlotContent parseSlotRef(String ref) {
        if (ref == null || ref.isEmpty()) return SlotContent.of(ItemStack.EMPTY);

        String id = ref;
        int count = 1;

        int colon = ref.indexOf(':');
        if (colon >= 0) {
            id = ref.substring(0, colon);
            try {
                count = Integer.parseInt(ref.substring(colon + 1));
            } catch (NumberFormatException ignored) {
            }
        }

        return SlotContent.of(ItemStackBuilder.buildIngredient(id, count));
    }

    private static String jsonStr(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }
}