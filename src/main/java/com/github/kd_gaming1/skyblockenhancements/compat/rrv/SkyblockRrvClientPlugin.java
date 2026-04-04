package com.github.kd_gaming1.skyblockenhancements.compat.rrv;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

import cc.cassian.rrv.api.ReliableRecipeViewerClientPlugin;
import cc.cassian.rrv.api.recipe.ItemView;
import cc.cassian.rrv.api.recipe.ReliableServerRecipe;
import cc.cassian.rrv.api.recipe.ReliableServerRecipeType;
import cc.cassian.rrv.common.recipe.ClientRecipeManager;
import cc.cassian.rrv.common.recipe.ServerRecipeManager.ServerRecipeEntry;
import cc.cassian.rrv.common.recipe.cache.LowEndRecipeCache;
import com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.crafting.SkyblockCraftingClientRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.crafting.SkyblockCraftingServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.drops.SkyblockDropsClientRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.drops.SkyblockDropsServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.forge.SkyblockForgeClientRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.forge.SkyblockForgeServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.kat.SkyblockKatgradeClientRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.kat.SkyblockKatgradeServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.npc.SkyblockNpcInfoClientRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.npc.SkyblockNpcInfoRegistry;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.npc.SkyblockNpcInfoServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.npc.SkyblockNpcShopClientRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.npc.SkyblockNpcShopServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.trade.SkyblockTradeClientRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.trade.SkyblockTradeServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.wiki.SkyblockWikiInfoClientRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.wiki.SkyblockWikiInfoServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.repo.ItemStackBuilder;
import com.github.kd_gaming1.skyblockenhancements.repo.NeuItem;
import com.github.kd_gaming1.skyblockenhancements.repo.NeuItemRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Client-side RRV plugin. Registers server→client recipe wrappers and handles cache spoofing
 * to inject SkyBlock items and recipes into RRV as if they came from a compatible server.
 */
public class SkyblockRrvClientPlugin implements ReliableRecipeViewerClientPlugin {

    @Override
    public void onIntegrationInitialize() {
        if (!RrvCompat.isActive()) return;

        // ── Recipe wrappers (server → client) ────────────────────────────────────────

        ItemView.addClientRecipeWrapper(
                SkyblockCraftingServerRecipe.TYPE,
                r -> List.of(new SkyblockCraftingClientRecipe(
                        r.getInputs(), r.getOutput(), r.getWikiUrls())));

        ItemView.addClientRecipeWrapper(
                SkyblockForgeServerRecipe.TYPE,
                r -> List.of(new SkyblockForgeClientRecipe(
                        r.getInputs(), r.getOutput(), r.getDurationSeconds(), r.getWikiUrls())));

        ItemView.addClientRecipeWrapper(
                SkyblockNpcShopServerRecipe.TYPE,
                r -> List.of(new SkyblockNpcShopClientRecipe(
                        r.getCosts(), r.getResult(), r.getNpcId(), r.getNpcDisplayName(),
                        r.getWikiUrls())));

        ItemView.addClientRecipeWrapper(
                SkyblockNpcInfoServerRecipe.TYPE,
                r -> {
                    SkyblockNpcInfoClientRecipe recipe = new SkyblockNpcInfoClientRecipe(
                            r.getNpcHead(), r.getNpcId(), r.getNpcDisplayName(),
                            r.getIsland(), r.getX(), r.getY(), r.getZ(),
                            r.getLoreLines(), r.getWikiUrls());
                    // Register so shop pages can navigate to this NPC's info card.
                    SkyblockNpcInfoRegistry.register(r.getNpcId(), recipe);
                    return List.of(recipe);
                });

        ItemView.addClientRecipeWrapper(
                SkyblockTradeServerRecipe.TYPE,
                r -> List.of(new SkyblockTradeClientRecipe(
                        r.getCost(), r.getResult(), r.getWikiUrls())));

        ItemView.addClientRecipeWrapper(
                SkyblockDropsServerRecipe.TYPE,
                r -> List.of(new SkyblockDropsClientRecipe(
                        r.getMobName(), r.getDrops(), r.getChances(), r.getLevel(),
                        r.getCombatXp(), r.getWikiUrls())));

        ItemView.addClientRecipeWrapper(
                SkyblockKatgradeServerRecipe.TYPE,
                r -> List.of(new SkyblockKatgradeClientRecipe(
                        r.getInput(), r.getOutput(), r.getMaterials(), r.getCoins(),
                        r.getTimeSeconds(), r.getWikiUrls())));

        ItemView.addClientRecipeWrapper(
                SkyblockWikiInfoServerRecipe.TYPE,
                r -> List.of(new SkyblockWikiInfoClientRecipe(r.getDisplayItem(), r.getWikiUrls())));

        // ── Cache reload hook ────────────────────────────────────────────────────────

        // Re-inject recipes when RRV clears its cache (e.g. on server reconnect).
        ItemView.addClientReloadCallback(() -> {
            // Clear stale NPC info entries so the wrong card isn't shown after a repo refresh.
            SkyblockNpcInfoRegistry.clear();

            CompletableFuture<Void> future = SkyblockEnhancements.getInstance().getRepoFuture();
            if (future.isDone()) {
                spoofRrvCache();
            } else {
                future.thenRun(SkyblockRrvClientPlugin::spoofRrvCache);
            }
        });
    }

    /**
     * Injects SkyBlock items and recipes into RRV's internal cache, mimicking what would
     * happen if a compatible server sent them over the network.
     */
    @SuppressWarnings("UnstableApiUsage")
    public static void spoofRrvCache() {
        if (!NeuItemRegistry.isLoaded()) return;

        // Clear before re-populating — the wrapper registers fresh entries for each recipe.
        SkyblockNpcInfoRegistry.clear();

        List<ItemStack> items = new ArrayList<>();
        for (NeuItem item : NeuItemRegistry.getAll().values()) {
            ItemStack stack = ItemStackBuilder.build(item);
            if (!stack.isEmpty()) {
                items.add(stack);
            }
        }

        List<ReliableServerRecipe> allRecipes = SkyblockRrvPlugin.generateAllRecipes();
        Map<ReliableServerRecipeType<?>, List<ServerRecipeEntry>> grouped = new HashMap<>();

        int idCounter = 0;
        for (ReliableServerRecipe recipe : allRecipes) {
            grouped.computeIfAbsent(recipe.getRecipeType(), k -> new ArrayList<>())
                    .add(new ServerRecipeEntry(
                            Identifier.fromNamespaceAndPath(
                                    "skyblock_enhancements", "recipe_" + (idCounter++)),
                            recipe));
        }

        ClientRecipeManager.INSTANCE.queueTask(() -> {
            // 1. Inject items into the stack-sensitive index.
            LowEndRecipeCache.INSTANCE.stackSensitiveStartRecieved(items.size());
            for (ItemStack stack : items) {
                LowEndRecipeCache.INSTANCE.stackSensitiveRecieved(
                        new ItemView.StackSensitive(stack));
            }
            LowEndRecipeCache.INSTANCE.stackSensitiveEndRecieved();

            // 2. Inject recipes grouped by type.
            LowEndRecipeCache.INSTANCE.cacheStartRecieved(grouped.size());
            for (var entry : grouped.entrySet()) {
                LowEndRecipeCache.INSTANCE.startCaching(entry.getKey(), entry.getValue().size());
                for (ServerRecipeEntry recipeEntry : entry.getValue()) {
                    LowEndRecipeCache.INSTANCE.cacheModRecipe(recipeEntry);
                }
                LowEndRecipeCache.INSTANCE.endCaching(entry.getKey());
            }

            LowEndRecipeCache.INSTANCE.processRecipes();
        });

        ClientRecipeManager.INSTANCE.runTasks();
        LOGGER.info("Spoofed RRV cache with {} items and {} recipes.", items.size(), allRecipes.size());
    }
}