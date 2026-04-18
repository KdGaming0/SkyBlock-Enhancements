package com.github.kd_gaming1.skyblockenhancements.compat.rrv.injection;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

import cc.cassian.rrv.api.recipe.ReliableServerRecipe;
import cc.cassian.rrv.api.recipe.ReliableServerRecipeType;
import cc.cassian.rrv.common.recipe.ServerRecipeManager.ServerRecipeEntry;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.npc.SkyblockNpcInfoRecipeType;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.npc.SkyblockNpcInfoRegistry;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.npc.SkyblockNpcShopRecipeType;
import com.github.kd_gaming1.skyblockenhancements.repo.hypixel.HypixelItemsRegistry;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItemRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.generator.SkyblockRecipeGenerator;

/**
 * Owns the cached SkyBlock item list and recipe map that gets injected into RRV.
 *
 * <p>This class manages state and orchestration only — item list construction lives in
 * {@link SkyblockItemListBuilder}, recipe grouping in {@link SkyblockRecipeGrouper}, and
 * RRV low-level cache writes in {@link RrvCacheInjector}.
 *
 * <p>{@link #buildCache()} is synchronised so concurrent callers (repo reload vs. RRV reload
 * callback) don't race to produce duplicate work. Published fields are volatile so unlocked
 * readers always see a fully-constructed cache.
 */
public final class SkyblockInjectionCache {

    @Nullable private static volatile List<ItemStack> cachedItems;
    @Nullable private static volatile Map<ReliableServerRecipeType<?>, List<ServerRecipeEntry>> cachedGrouped;

    /** {@code true} once RRV has received the current cache, preventing double-injection. */
    private static volatile boolean injected;

    private SkyblockInjectionCache() {}

    // ── Accessors ────────────────────────────────────────────────────────────────

    @Nullable
    public static List<ItemStack> getCachedItems() {
        return cachedItems;
    }

    @Nullable
    public static Map<ReliableServerRecipeType<?>, List<ServerRecipeEntry>> getCachedGrouped() {
        return cachedGrouped;
    }

    public static boolean isInjected() {
        return injected;
    }

    public static void markInjected() {
        injected = true;
    }

    /** Clears the injection flag without clearing data. Used by RRV reload callbacks. */
    public static void markNotInjected() {
        injected = false;
    }

    // ── Invalidation ─────────────────────────────────────────────────────────────

    public static void invalidate() {
        cachedItems = null;
        cachedGrouped = null;
        injected = false;
        SkyblockNpcShopRecipeType.INSTANCE.clearCache();
        SkyblockNpcInfoRecipeType.INSTANCE.clearCache();
        FullStackListCache.invalidate();
    }

    // ── Build ────────────────────────────────────────────────────────────────────

    /**
     * Builds the full item list and recipe map from the current registries. No-op if the cache
     * is already built, or if the NEU registry hasn't been populated yet.
     */
    public static synchronized void buildCache() {
        if (cachedItems != null && cachedGrouped != null) return;

        if (NeuItemRegistry.getAll().isEmpty()) {
            LOGGER.warn("buildCache called with empty registry — skipping.");
            return;
        }

        SkyblockNpcInfoRegistry.clear();

        List<ItemStack> items = SkyblockItemListBuilder.build();
        Map<ReliableServerRecipeType<?>, List<ServerRecipeEntry>> grouped =
                SkyblockRecipeGrouper.group(SkyblockRecipeGenerator.generateAll());

        FullStackListCache.populateFromInjected(items);

        cachedGrouped = new HashMap<>(grouped);
        cachedItems = items;

        LOGGER.info("Built injection cache: {} items, {} recipes.",
                items.size(), countRecipes(grouped));
    }

    /**
     * Regenerates only essence upgrade recipes and injects them into RRV. Used when Hypixel
     * API data arrives after the initial injection (delta path).
     */
    public static synchronized void buildEssenceRecipesOnly() {
        if (cachedItems == null || cachedGrouped == null) {
            LOGGER.warn("buildEssenceRecipesOnly called before main cache is built — skipping.");
            return;
        }
        if (!HypixelItemsRegistry.isLoaded()) {
            LOGGER.warn("buildEssenceRecipesOnly: Hypixel registry not loaded — skipping.");
            return;
        }

        List<ReliableServerRecipe> essenceRecipes = SkyblockRecipeGenerator.generateEssenceOnly();
        if (essenceRecipes.isEmpty()) {
            LOGGER.warn("buildEssenceRecipesOnly: no essence recipes generated.");
            return;
        }

        Map<ReliableServerRecipeType<?>, List<ServerRecipeEntry>> essenceGrouped =
                SkyblockRecipeGrouper.group(essenceRecipes);

        cachedGrouped.putAll(essenceGrouped);

        LOGGER.info("Delta-injecting {} essence upgrade recipes into RRV.", essenceRecipes.size());
        RrvCacheInjector.inject(cachedItems, essenceGrouped);
    }

    private static int countRecipes(Map<?, ? extends List<?>> grouped) {
        return grouped.values().stream().mapToInt(List::size).sum();
    }
}