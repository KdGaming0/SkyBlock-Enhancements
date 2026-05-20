package com.github.kd_gaming1.skyblockenhancements.compat.rrv;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

import cc.cassian.rrv.api.recipe.ItemView;
import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.api.recipe.ReliableServerRecipe;
import cc.cassian.rrv.api.recipe.ReliableServerRecipeType;
import com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.category.SkyblockCategoryFilter;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.injection.FullStackListCache;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.injection.RrvCacheInjector;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.injection.SkyblockInjectionCache;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.injection.SkyblockRecipeIndex;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.crafting.SkyblockCraftingClientRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.crafting.SkyblockCraftingServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.drops.MobPreviewRenderer;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.drops.SkyblockDropsClientRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.drops.SkyblockDropsServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.essence.SkyblockEssenceUpgradeClientRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.essence.SkyblockEssenceUpgradeServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.forge.SkyblockForgeClientRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.forge.SkyblockForgeServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.kat.SkyblockKatUpgradeClientRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.kat.SkyblockKatUpgradeServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.npc.SkyblockNpcInfoClientRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.npc.SkyblockNpcInfoRecipeType;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.npc.SkyblockNpcInfoRegistry;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.npc.SkyblockNpcInfoServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.npc.SkyblockNpcShopClientRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.npc.SkyblockNpcShopRecipeType;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.npc.SkyblockNpcShopServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.reforge.SkyblockReforgeClientRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.reforge.SkyblockReforgeServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.trade.SkyblockTradeClientRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.trade.SkyblockTradeServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.wiki.SkyblockWikiInfoClientRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.wiki.SkyblockWikiInfoServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItemRegistry;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.mob.MobRenderRegistry;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.mob.MobSkinRegistry;
import cc.cassian.rrv.api.ReliableRecipeViewerClientPlugin;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RRV client plugin entry point. Registers recipe wrappers and handles reload callbacks.
 *
 * <p>Cache management is delegated to {@link SkyblockInjectionCache}, and injection
 * mechanics to {@link RrvCacheInjector}. This class only handles RRV lifecycle events.
 */
public class SkyblockRrvClientPlugin implements ReliableRecipeViewerClientPlugin {

    /** Guards against concurrent injection into RRV's internal caches. */
    private static final AtomicBoolean INJECTING = new AtomicBoolean(false);

    @Override
    public void onIntegrationInitialize() {
        if (!RrvCompat.isActive()) return;

        // Wire invalidation: when the registry is cleared (repo reload), tear down RRV caches.
        NeuItemRegistry.addClearListener(SkyblockInjectionCache::invalidate);
        NeuItemRegistry.addClearListener(SkyblockCategoryFilter::invalidateIndex);
        NeuItemRegistry.addClearListener(SkyblockRrvClientPlugin::clearMobCaches);
        NeuItemRegistry.addClearListener(SkyblockNpcShopRecipeType.INSTANCE::clearCache);
        NeuItemRegistry.addClearListener(SkyblockNpcInfoRecipeType.INSTANCE::clearCache);
        NeuItemRegistry.addClearListener(SkyblockNpcInfoRegistry::clear);
        NeuItemRegistry.addClearListener(com.github.kd_gaming1.skyblockenhancements.compat.rrv.injection.SkyblockRecipeIndex::invalidate);

        registerRecipeWrappers();

        // Re-inject when RRV clears its cache (e.g. on server reconnect / lobby switch).
        ItemView.addClientReloadCallback(() -> {
            SkyblockRecipeIndex.invalidate();
            CompletableFuture<Void> future = SkyblockEnhancements.getInstance().getRepoFuture();
            if (future.isDone()) {
                injectIfReady();
            } else {
                SkyblockInjectionCache.markNotInjected();
            }
        });
    }

    /**
     * Releases all mob-preview data on repo reload. Also invalidates the cached PlayerModel
     * because resource packs can change between reloads, which rebakes the model layer.
     */
    private static void clearMobCaches() {
        MobRenderRegistry.clear();
        MobSkinRegistry.clear();
        MobPreviewRenderer.invalidatePlayerModel();
    }

    /**
     * Injects cached data into RRV if the cache is ready and not already injected.
     * Called from the reload callback and from the startup pipeline.
     *
     * <p>The method is idempotent and thread-safe: concurrent callers are coalesced
     * into a single injection attempt.
     */
    public static void injectIfReady() {
        if (!NeuItemRegistry.isLoaded()) return;
        if (SkyblockInjectionCache.isInjected()) {
            LOGGER.debug("RRV cache already populated — skipping re-injection.");
            return;
        }

        if (!INJECTING.compareAndSet(false, true)) {
            LOGGER.debug("Injection already in progress — skipping duplicate call.");
            return;
        }

        try {
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
        } finally {
            INJECTING.set(false);
        }
    }

    // ── Recipe wrappers (server → client) ────────────────────────────────────────

    @FunctionalInterface
    private interface ClientRecipeFactory<R extends ReliableServerRecipe, C extends ReliableClientRecipe> {
        C create(R recipe);
    }

    private static <R extends ReliableServerRecipe, C extends ReliableClientRecipe>
    void register(ReliableServerRecipeType<R> type, ClientRecipeFactory<R, C> factory) {
        ItemView.addClientRecipeWrapper(type, r -> List.of(factory.create(r)));
    }

    private void registerRecipeWrappers() {
        register(SkyblockCraftingServerRecipe.TYPE,
                r -> new SkyblockCraftingClientRecipe(r.getInputs(), r.getOutput(), r.getWikiUrls(), r.getCrafttext()));

        register(SkyblockForgeServerRecipe.TYPE,
                r -> new SkyblockForgeClientRecipe(
                        r.getInputs(), r.getOutput(), r.getDurationSeconds(), r.getWikiUrls(), r.getCrafttext()));

        register(SkyblockNpcShopServerRecipe.TYPE,
                r -> new SkyblockNpcShopClientRecipe(
                        r.getCosts(), r.getResult(), r.getNpcId(), r.getNpcDisplayName(),
                        r.getWikiUrls()));

        register(SkyblockNpcInfoServerRecipe.TYPE, SkyblockNpcInfoClientRecipe::createAndRegister);

        register(SkyblockTradeServerRecipe.TYPE,
                r -> new SkyblockTradeClientRecipe(r.getCost(), r.getResult(), r.getWikiUrls()));

        register(SkyblockDropsServerRecipe.TYPE, SkyblockDropsClientRecipe::new);

        register(SkyblockKatUpgradeServerRecipe.TYPE,
                r -> new SkyblockKatUpgradeClientRecipe(
                        r.getInput(), r.getOutput(), r.getMaterials(), r.getCoins(),
                        r.getTimeSeconds(), r.getWikiUrls()));

        register(SkyblockWikiInfoServerRecipe.TYPE,
                r -> new SkyblockWikiInfoClientRecipe(r.getDisplayItem(), r.getDisplayName(), r.getWikiUrls()));

        register(SkyblockEssenceUpgradeServerRecipe.TYPE,
                r -> new SkyblockEssenceUpgradeClientRecipe(
                        r.getInput(), r.getOutput(), r.getEssence(), r.getCompanions(),
                        r.getStarLevel(), r.getEssenceType(), r.getWikiUrls()));

        register(SkyblockReforgeServerRecipe.TYPE,
                r -> new SkyblockReforgeClientRecipe(r));
    }
}
