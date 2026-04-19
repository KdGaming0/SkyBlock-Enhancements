package com.github.kd_gaming1.skyblockenhancements.compat.rrv;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

import cc.cassian.rrv.api.recipe.ItemView;
import com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.crafting.SkyblockCraftingClientRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.crafting.SkyblockCraftingServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.drops.SkyblockDropsClientRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.drops.SkyblockDropsServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.essence.SkyblockEssenceUpgradeClientRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.essence.SkyblockEssenceUpgradeServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.forge.SkyblockForgeClientRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.forge.SkyblockForgeServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.kat.SkyblockKatUpgradeClientRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.kat.SkyblockKatUpgradeServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.npc.*;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.trade.SkyblockTradeClientRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.trade.SkyblockTradeServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.wiki.SkyblockWikiInfoClientRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.wiki.SkyblockWikiInfoServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItemRegistry;

import cc.cassian.rrv.api.ReliableRecipeViewerClientPlugin;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.injection.RrvCacheInjector;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.category.SkyblockCategoryFilter;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.injection.SkyblockInjectionCache;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.npc.SkyblockNpcInfoClientRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.npc.SkyblockNpcInfoRegistry;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.npc.SkyblockNpcInfoServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.npc.SkyblockNpcShopClientRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.npc.SkyblockNpcShopServerRecipe;

/**
 * RRV client plugin entry point. Registers recipe wrappers and handles reload callbacks.
 *
 * <p>Cache management is delegated to {@link SkyblockInjectionCache}, and injection
 * mechanics to {@link RrvCacheInjector}. This class only handles RRV lifecycle events.
 */
public class SkyblockRrvClientPlugin implements ReliableRecipeViewerClientPlugin {

    @Override
    public void onIntegrationInitialize() {
        if (!RrvCompat.isActive()) return;

        // Wire invalidation: when the registry is cleared (repo reload), tear down RRV caches.
        NeuItemRegistry.addClearListener(SkyblockInjectionCache::invalidate);
        NeuItemRegistry.addClearListener(SkyblockCategoryFilter::invalidateIndex);

        registerRecipeWrappers();

        // Re-inject when RRV clears its cache (e.g. on server reconnect / lobby switch).
        ItemView.addClientReloadCallback(() -> {
            CompletableFuture<Void> future = SkyblockEnhancements.getInstance().getRepoFuture();
            if (future.isDone()) {
                injectIfReady();
            } else {
                // Repo is still downloading — injection will happen when it completes.
                SkyblockInjectionCache.markNotInjected();
            }
        });
    }

    /**
     * Injects cached data into RRV if the cache is ready and not already injected.
     * Called from the reload callback and from the startup pipeline.
     */
    public static void injectIfReady() {
        if (!NeuItemRegistry.isLoaded()) return;
        if (SkyblockInjectionCache.isInjected()) {
            LOGGER.debug("RRV cache already populated — skipping re-injection.");
            return;
        }

        var items = SkyblockInjectionCache.getCachedItems();
        var grouped = SkyblockInjectionCache.getCachedGrouped();

        if (items == null || items.isEmpty() || grouped == null) {
            LOGGER.warn("Injection skipped — cache not yet built.");
            return;
        }

        RrvCacheInjector.inject(items, grouped);
        SkyblockInjectionCache.markInjected();

        LOGGER.info("Injected {} items and {} recipes into RRV.",
                items.size(),
                grouped.values().stream().mapToInt(List::size).sum());
    }

    // ── Recipe wrappers (server → client) ────────────────────────────────────────

    private void registerRecipeWrappers() {
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
                        r.getMobName(), r.getDrops(), r.getChances(),
                        r.getLevel(),
                        r.getCombatXp(), r.getWikiUrls())));

        ItemView.addClientRecipeWrapper(
                SkyblockKatUpgradeServerRecipe.TYPE,
                r -> List.of(new SkyblockKatUpgradeClientRecipe(
                        r.getInput(), r.getOutput(), r.getMaterials(), r.getCoins(),
                        r.getTimeSeconds(), r.getWikiUrls())));

        ItemView.addClientRecipeWrapper(
                SkyblockWikiInfoServerRecipe.TYPE,
                r -> List.of(new SkyblockWikiInfoClientRecipe(
                        r.getDisplayItem(), r.getDisplayName(), r.getWikiUrls())));

        ItemView.addClientRecipeWrapper(
                SkyblockEssenceUpgradeServerRecipe.TYPE,
                r -> List.of(new SkyblockEssenceUpgradeClientRecipe(
                        r.getInput(), r.getOutput(), r.getEssence(), r.getCompanions(),
                        r.getStarLevel(), r.getEssenceType(), r.getWikiUrls())));
    }
}