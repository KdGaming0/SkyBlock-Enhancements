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
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.npc.SkyblockNpcShopClientRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.npc.SkyblockNpcShopServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.trade.SkyblockTradeClientRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.trade.SkyblockTradeServerRecipe;
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

public class SkyblockRrvClientPlugin implements ReliableRecipeViewerClientPlugin {

    @Override
    public void onIntegrationInitialize() {
        if (!RrvCompat.isActive()) return;

        // ── Recipe wrappers ───────────────────────────────────────────────────────

        ItemView.addClientRecipeWrapper(
                SkyblockCraftingServerRecipe.TYPE,
                r -> List.of(new SkyblockCraftingClientRecipe(r.getInputs(), r.getOutput())));

        ItemView.addClientRecipeWrapper(
                SkyblockForgeServerRecipe.TYPE,
                r -> List.of(new SkyblockForgeClientRecipe(r.getInputs(), r.getOutput(), r.getDurationSeconds())));

        ItemView.addClientRecipeWrapper(
                SkyblockNpcShopServerRecipe.TYPE,
                r -> List.of(new SkyblockNpcShopClientRecipe(r.getCosts(), r.getResult(), r.getNpcId())));

        ItemView.addClientRecipeWrapper(
                SkyblockTradeServerRecipe.TYPE,
                r -> List.of(new SkyblockTradeClientRecipe(r.getCost(), r.getResult())));

        ItemView.addClientRecipeWrapper(
                SkyblockDropsServerRecipe.TYPE,
                r -> List.of(new SkyblockDropsClientRecipe(r.getMobName(), r.getDrops(), r.getChances(), r.getLevel(), r.getCombatXp())));

        ItemView.addClientRecipeWrapper(
                SkyblockKatgradeServerRecipe.TYPE,
                r -> List.of(new SkyblockKatgradeClientRecipe(r.getInput(), r.getOutput(), r.getMaterials(), r.getCoins(), r.getTimeSeconds())));

        // ── Item index population ─────────────────────────────────────────────────

        // When we connect to Hypixel, RRV will clear the cache. This ensures we inject our recipes right back in.
        ItemView.addClientReloadCallback(() -> {
            CompletableFuture<Void> future = SkyblockEnhancements.getInstance().getRepoFuture();
            if (future.isDone()) {
                spoofRrvCache();
            } else {
                future.thenRun(SkyblockRrvClientPlugin::spoofRrvCache);
            }
        });
    }

    /**
     * Hacks RRV's internal network cache queue to manually process the recipes
     * exactly like an incoming network packet from a compatible server.
     */
    public static void spoofRrvCache() {
        if (!NeuItemRegistry.isLoaded()) return;

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
                            Identifier.fromNamespaceAndPath("skyblock_enhancements", "recipe_" + (idCounter++)),
                            recipe
                    ));
        }

        ClientRecipeManager.INSTANCE.queueTask(() -> {
            // 1. Spoof Items (Stack Sensitives)
            LowEndRecipeCache.INSTANCE.stackSensitiveStartRecieved(items.size());
            for (ItemStack stack : items) {
                LowEndRecipeCache.INSTANCE.stackSensitiveRecieved(new ItemView.StackSensitive(stack));
            }
            LowEndRecipeCache.INSTANCE.stackSensitiveEndRecieved();

            // 2. Spoof Recipes
            LowEndRecipeCache.INSTANCE.cacheStartRecieved(grouped.size());
            for (Map.Entry<ReliableServerRecipeType<?>, List<ServerRecipeEntry>> entry : grouped.entrySet()) {
                LowEndRecipeCache.INSTANCE.startCaching(entry.getKey(), entry.getValue().size());
                for (ServerRecipeEntry recipeEntry : entry.getValue()) {
                    LowEndRecipeCache.INSTANCE.cacheModRecipe(recipeEntry);
                }
                LowEndRecipeCache.INSTANCE.endCaching(entry.getKey());
            }

            LowEndRecipeCache.INSTANCE.processRecipes();
        });

        ClientRecipeManager.INSTANCE.runTasks();
        LOGGER.info("Spoofed RRV Cache with {} SkyBlock items and {} recipes.", items.size(), allRecipes.size());
    }
}