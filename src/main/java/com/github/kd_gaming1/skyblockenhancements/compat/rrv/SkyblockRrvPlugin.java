package com.github.kd_gaming1.skyblockenhancements.compat.rrv;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

import cc.cassian.rrv.api.ReliableRecipeViewerPlugin;
import cc.cassian.rrv.api.recipe.ReliableServerRecipe;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.crafting.SkyblockCraftingServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.drops.SkyblockDropsServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.essence.SkyblockEssenceUpgradeServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.forge.SkyblockForgeServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.kat.SkyblockKatUpgradeServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.npc.SkyblockNpcInfoServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.npc.SkyblockNpcShopServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.trade.SkyblockTradeServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.wiki.SkyblockWikiInfoServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.repo.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.world.item.ItemStack;

/**
 * Converts NEU repo items into RRV server recipes. All parse methods are stateless and static —
 * the class is only non-static because RRV discovers plugins via an instance entrypoint.
 */
public class SkyblockRrvPlugin implements ReliableRecipeViewerPlugin {

    private static final String[] GRID_KEYS = {"A1", "A2", "A3", "B1", "B2", "B3", "C1", "C2", "C3"};

    /** Reusable empty slot — avoids allocating a new SlotContent per empty crafting grid cell. */
    private static final SlotContent EMPTY_SLOT = SlotContent.of(ItemStack.EMPTY);

    @Override
    public void onIntegrationInitialize() {
        // Do nothing - Item injection handled by SkyblockInjectionCache.buildCache()
    }

    /**
     * Iterates every NEU item and emits all applicable recipe types. Items that produce no
     * recipes but have wiki URLs get a wiki-only fallback card instead.
     *
     * <p>Modern recipes are dispatched in a single pass per item (one loop over {@code item.recipes}
     * with a switch on type), avoiding redundant list scans.
     */
    public static List<ReliableServerRecipe> generateAllRecipes() {
        List<ReliableServerRecipe> list = new ArrayList<>();
        if (!NeuItemRegistry.isLoaded()) {
            LOGGER.debug("NEU repo not yet loaded — skipping recipe generation");
            return list;
        }

        for (NeuItem item : NeuItemRegistry.getAll().values()) {
            int beforeSize = list.size();

            addLegacyCraftingRecipe(list, item);
            addModernRecipes(list, item);
            addNpcInfoRecipe(list, item);

            // Wiki-only fallback for items with wiki URLs but no other recipe types.
            // NPC items are excluded — they already have their own info card.
            if (list.size() == beforeSize) {
                addWikiInfoRecipe(list, item);
            }
        }

        // Essence upgrades come from constants data, not individual item entries
        addAllEssenceUpgradeRecipes(list);

        return list;
    }

    /**
     * Generates only essence upgrade recipes. Called by the delta-inject path when
     * Hypixel data becomes available after the initial injection.
     */
    public static List<ReliableServerRecipe> generateEssenceRecipesOnly() {
        List<ReliableServerRecipe> list = new ArrayList<>();
        addAllEssenceUpgradeRecipes(list);
        return list;
    }

    // ── Legacy crafting ──────────────────────────────────────────────────────────

    private static void addLegacyCraftingRecipe(List<ReliableServerRecipe> list, NeuItem item) {
        if (item.recipe == null || item.recipe.isEmpty()) return;

        SlotContent[] inputs = new SlotContent[9];
        for (int i = 0; i < 9; i++) {
            inputs[i] = parseSlotRef(item.recipe.get(GRID_KEYS[i]));
        }

        list.add(new SkyblockCraftingServerRecipe(
                inputs, SlotContent.of(ItemStackBuilder.build(item).copy()), extractWikiUrls(item)));
    }

    // ── Modern recipe dispatcher (single-pass) ──────────────────────────────────

    /**
     * Iterates the item's modern {@code recipes} array once, dispatching each entry by its
     * {@code type} field. This replaces the previous approach of calling
     * {@code addModernRecipes(list, item, "type")} separately for each type string.
     */
    private static void addModernRecipes(List<ReliableServerRecipe> list, NeuItem item) {
        if (item.recipes == null) return;

        for (JsonObject recipe : item.recipes) {
            String type = jsonStr(recipe, "type");
            if (type == null) continue;

            ReliableServerRecipe parsed = switch (type) {
                case "crafting" -> parseCrafting(recipe, item);
                case "forge"    -> parseForge(recipe, item);
                case "npc_shop" -> parseNpcShop(recipe, item);
                case "trade"    -> parseTrade(recipe, item);
                case "drops"    -> parseDrops(recipe, item);
                case "katgrade" -> parseKatgrade(recipe, item);
                default         -> null;
            };

            if (parsed != null) list.add(parsed);
        }
    }

    // ── NPC info ─────────────────────────────────────────────────────────────────

    private static void addNpcInfoRecipe(List<ReliableServerRecipe> list, NeuItem item) {
        if (!item.internalName.endsWith("_NPC")) return;

        ItemStack head = ItemStackBuilder.build(item).copy();
        String[] lore = item.lore != null ? item.lore.toArray(new String[0]) : new String[0];

        list.add(new SkyblockNpcInfoServerRecipe(
                head,
                item.internalName,
                item.displayName != null ? item.displayName : "",
                item.island != null ? item.island : "",
                item.x, item.y, item.z,
                lore,
                extractWikiUrls(item)));
    }

    // ── Wiki info (fallback for recipe-less items with wiki URLs) ────────────────

    private static void addWikiInfoRecipe(List<ReliableServerRecipe> list, NeuItem item) {
        if (item.internalName.endsWith("_NPC")) return;
        if (!item.hasWikiUrls()) return;

        ItemStack displayStack = ItemStackBuilder.build(item).copy();
        if (displayStack.isEmpty()) return;

        list.add(new SkyblockWikiInfoServerRecipe(displayStack, extractWikiUrls(item)));
    }

    // ── Essence upgrades (from Hypixel API) ─────────────────────────────────────

    /**
     * Generates one essence upgrade recipe per star level for every item that has
     * {@code upgrade_costs} in the Hypixel API. The output slot carries the starred item
     * name and updated stat values baked into its lore, so the standard tooltip already
     * shows the correct per-star stats without any extra rendering code.
     */
    private static void addAllEssenceUpgradeRecipes(List<ReliableServerRecipe> list) {
        if (!HypixelItemsRegistry.isLoaded()) {
            LOGGER.warn("Hypixel items registry not loaded — skipping essence upgrade recipes");
            return;
        }

        int count = 0;
        for (String itemId : HypixelItemsRegistry.getAllUpgradeItemIds()) {
            NeuItem item = NeuItemRegistry.get(itemId);
            if (item == null) continue;

            ItemStack baseStack = ItemStackBuilder.build(item);
            if (baseStack.isEmpty()) continue;

            List<List<HypixelItemsRegistry.HypixelUpgradeCost>> apiCosts = HypixelItemsRegistry.getUpgradeCosts(itemId);
            Map<String, int[]> tieredStats = HypixelItemsRegistry.getTieredStats(itemId); // nullable
            String[] wikiUrls = extractWikiUrls(item);

            if (apiCosts == null) continue;

            for (int i = 0; i < apiCosts.size(); i++) {
                int star = i + 1;
                List<HypixelItemsRegistry.HypixelUpgradeCost> starCosts = apiCosts.get(i);

                String essenceType = null;
                int essenceAmount = 0;
                List<String> companionRefs = new ArrayList<>();

                for (HypixelItemsRegistry.HypixelUpgradeCost cost : starCosts) {
                    if (cost.isEssence()) {
                        essenceType = cost.essenceType();
                        essenceAmount = cost.amount();
                    } else {
                        companionRefs.add(cost.toSlotRef());
                    }
                }

                // Every essence upgrade must have an ESSENCE cost entry; skip stars that don't
                if (essenceType == null || essenceAmount <= 0) continue;

                SlotContent essenceSlot = parseSlotRef(
                        "ESSENCE_" + essenceType.toUpperCase() + ":" + essenceAmount);

                SlotContent[] companions = new SlotContent[companionRefs.size()];
                for (int j = 0; j < companionRefs.size(); j++) {
                    companions[j] = parseSlotRef(companionRefs.get(j));
                }

                // Output item: starred name + per-star stats in lore (baked at build time)
                ItemStack output = StarredItemBuilder.buildStarredOutput(item, star, tieredStats);

                list.add(new SkyblockEssenceUpgradeServerRecipe(
                        SlotContent.of(baseStack.copy()), SlotContent.of(output),
                        essenceSlot, companions, star, essenceType, wikiUrls));
                count++;
            }
        }

        LOGGER.info("Generated {} essence upgrade recipes from Hypixel API", count);
    }

    // ── Individual recipe parsers ────────────────────────────────────────────────

    private static ReliableServerRecipe parseCrafting(JsonObject recipe, NeuItem item) {
        SlotContent[] inputs = new SlotContent[9];
        for (int i = 0; i < 9; i++) {
            inputs[i] = parseSlotRef(jsonStr(recipe, GRID_KEYS[i]));
        }
        return new SkyblockCraftingServerRecipe(
                inputs, SlotContent.of(resolveOutput(recipe, item)), extractWikiUrls(item));
    }

    private static ReliableServerRecipe parseForge(JsonObject recipe, NeuItem item) {
        JsonArray inputsArr = recipe.has("inputs") ?
                recipe.getAsJsonArray("inputs") : null;
        if (inputsArr == null || inputsArr.isEmpty()) return null;

        SlotContent[] inputs = new SlotContent[inputsArr.size()];
        for (int i = 0; i < inputsArr.size(); i++) {
            inputs[i] = parseSlotRef(inputsArr.get(i).getAsString());
        }

        int duration = recipe.has("duration") ? recipe.get("duration").getAsInt() : 0;
        return new SkyblockForgeServerRecipe(
                inputs, SlotContent.of(resolveOutput(recipe, item)), duration, extractWikiUrls(item));
    }

    private static ReliableServerRecipe parseNpcShop(JsonObject recipe, NeuItem item) {
        JsonArray costArr = recipe.has("cost") ? recipe.getAsJsonArray("cost") : null;
        String resultRef = jsonStr(recipe, "result");
        if (costArr == null || resultRef == null) return null;

        SlotContent[] costs = new SlotContent[costArr.size()];
        for (int i = 0; i < costArr.size(); i++) {
            costs[i] = parseSlotRef(costArr.get(i).getAsString());
        }

        SlotContent result = parseSlotRef(resultRef);

        // Strip count suffix (e.g. "SHINY_ORB:2" → "SHINY_ORB") for the wiki URL lookup.
        String resultId = resultRef.contains(":") ? resultRef.substring(0, resultRef.indexOf(':')) : resultRef;
        NeuItem resultItem = NeuItemRegistry.get(resultId);
        String[] resultWikiUrls = resultItem != null ? extractWikiUrls(resultItem) : new String[0];

        return new SkyblockNpcShopServerRecipe(
                costs, result, item.internalName,
                item.displayName != null ? item.displayName : "",
                resultWikiUrls);
    }

    private static ReliableServerRecipe parseTrade(JsonObject recipe, NeuItem item) {
        String costRef = jsonStr(recipe, "cost");
        String resultRef = jsonStr(recipe, "result");
        if (costRef == null || resultRef == null) return null;

        return new SkyblockTradeServerRecipe(
                parseSlotRef(costRef), parseSlotRef(resultRef), extractWikiUrls(item));
    }

    private static ReliableServerRecipe parseDrops(JsonObject recipe, NeuItem item) {
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

        return new SkyblockDropsServerRecipe(
                mobName, drops, chances, level, xp, extractWikiUrls(item));
    }

    private static ReliableServerRecipe parseKatgrade(JsonObject recipe, NeuItem item) {
        String inputRef = jsonStr(recipe, "input");
        String outputRef = jsonStr(recipe, "output");
        if (inputRef == null || outputRef == null) return null;

        JsonArray materialsArr = recipe.has("items") ? recipe.getAsJsonArray("items") : null;
        SlotContent[] materials;
        if (materialsArr != null && !materialsArr.isEmpty()) {
            materials = new SlotContent[materialsArr.size()];
            for (int i = 0; i < materialsArr.size(); i++) {
                materials[i] = parseSlotRef(materialsArr.get(i).getAsString());
            }
        } else {
            materials = new SlotContent[0];
        }

        int coins = recipe.has("coins") ? recipe.get("coins").getAsInt() : 0;
        int time = recipe.has("time") ? recipe.get("time").getAsInt() : 0;

        return new SkyblockKatUpgradeServerRecipe(
                parseSlotRef(inputRef), parseSlotRef(outputRef), materials, coins, time,
                extractWikiUrls(item));
    }

    // ── Output resolution ────────────────────────────────────────────────────────

    private static ItemStack resolveOutput(JsonObject recipe, NeuItem item) {
        String outputRef = jsonStr(recipe, "overrideOutputId");
        ItemStack output;

        if (outputRef != null) {
            NeuItem overrideItem = NeuItemRegistry.get(outputRef);
            output = (overrideItem != null ?
                    ItemStackBuilder.build(overrideItem) : ItemStackBuilder.build(item)).copy();
        } else {
            output = ItemStackBuilder.build(item).copy();
        }

        int count = recipe.has("count") ? recipe.get("count").getAsInt() : 1;
        if (count > 1) output.setCount(count);
        return output;
    }

    /**
     * Parses a slot reference like {@code "ENCHANTED_DIAMOND:64"} into a SlotContent.
     * Returns a cached empty slot for null/blank refs.
     */
    static SlotContent parseSlotRef(String ref) {
        if (ref == null || ref.isEmpty()) return EMPTY_SLOT;

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

    /** Extracts wiki URLs from a NeuItem when {@code infoType} is {@code "WIKI_URL"}. */
    static String[] extractWikiUrls(NeuItem item) {
        if (item.info != null && "WIKI_URL".equals(item.infoType)) {
            return item.info.toArray(new String[0]);
        }
        return new String[0];
    }

    private static String jsonStr(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }
}