package com.github.kd_gaming1.skyblockenhancements.compat.rrv;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

import cc.cassian.rrv.api.recipe.ReliableServerRecipe;
import cc.cassian.rrv.api.recipe.ReliableServerRecipeType;
import cc.cassian.rrv.common.recipe.ServerRecipeManager.ServerRecipeEntry;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.npc.SkyblockNpcInfoRecipeType;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.npc.SkyblockNpcInfoRegistry;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.npc.SkyblockNpcShopRecipeType;
import com.github.kd_gaming1.skyblockenhancements.repo.*;

import java.util.*;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Owns the cached SkyBlock item/recipe data that gets injected into RRV.
 *
 * <p>This class manages cache state only — build logic is delegated to
 * {@link SkyblockItemListBuilder} and {@link SkyblockRecipeGrouper}.
 *
 * <p>Thread safety: {@link #buildCache()} is {@code synchronized} so concurrent calls
 * (e.g. repo reload racing with a lobby-switch callback) don't produce duplicate work.
 * All volatile fields are published atomically at the end of the build.
 */
public final class SkyblockInjectionCache {

    /** Pre-built item stacks from the NEU repo. {@code null} until first build. */
    @Nullable private static volatile List<ItemStack> cachedItems;

    /** Pre-grouped recipe entries ready for injection. {@code null} until first build. */
    @Nullable private static volatile java.util.concurrent.ConcurrentHashMap<ReliableServerRecipeType<?>, List<ServerRecipeEntry>> cachedGrouped;

    /**
     * {@code true} once data has been successfully injected into RRV's cache.
     * Prevents redundant re-injection on lobby switches where RRV fires the reload
     * callback but never actually clears its own cache.
     */
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

    /** Clears the injected flag without clearing cached data — used by reload callbacks. */
    public static void markNotInjected() {
        injected = false;
    }

    // ── Invalidation ─────────────────────────────────────────────────────────────

    /** Clears all cached data so the next pipeline run regenerates everything. */
    public static void invalidate() {
        cachedItems = null;
        cachedGrouped = null;
        injected = false;
        SkyblockNpcShopRecipeType.INSTANCE.clearCache();
        SkyblockNpcInfoRecipeType.INSTANCE.clearCache();
        FullStackListCache.invalidate();
    }

    // ── Cache building ───────────────────────────────────────────────────────────

    /**
     * Builds the item list, recipes, and populates {@link FullStackListCache} from the
     * built items. Intended to be called from a background thread after repo download.
     *
     * <p>This is the single build step — no defensive rebuilds elsewhere. If the cache
     * is already populated, this is a no-op.
     */
    public static synchronized void buildCache() {
        if (cachedItems != null && cachedGrouped != null) {
            return;
        }

        if (NeuItemRegistry.getAll().isEmpty()) {
            LOGGER.warn("buildCache called with empty registry — skipping.");
            return;
        }

        SkyblockNpcInfoRegistry.clear();

        List<ItemStack> items = SkyblockItemListBuilder.build();
        Map<ReliableServerRecipeType<?>, List<ServerRecipeEntry>> grouped =
                SkyblockRecipeGrouper.group(SkyblockRrvPlugin.generateAllRecipes());

        // Populate FullStackListCache directly from the built items — no redundant scan.
        FullStackListCache.populateFromInjected(items);

        // Publish atomically — readers check cachedItems as the "ready" signal.
        cachedGrouped = new java.util.concurrent.ConcurrentHashMap<>(grouped);
        cachedItems = items;

        LOGGER.info("Built injection cache: {} items, {} recipes.",
                items.size(),
                grouped.values().stream().mapToInt(List::size).sum());
    }

    /**
     * Rebuilds only essence upgrade recipes from the now-loaded {@link HypixelItemsRegistry}
     * and injects them into RRV. Used when Hypixel data arrives after the initial injection.
     *
     * <p>This is a no-op if the main cache hasn't been built yet (items are required first),
     * or if Hypixel data still isn't loaded.
     */
    public static synchronized void buildEssenceRecipesOnly() {
        if (cachedItems == null || cachedGrouped == null) {
            LOGGER.warn("buildEssenceRecipesOnly called before main cache is built — skipping.");
            return;
        }

        if (!HypixelItemsRegistry.isLoaded()) {
            LOGGER.warn("buildEssenceRecipesOnly: Hypixel registry still not loaded — skipping.");
            return;
        }

        // Generate only essence upgrade recipes.
        List<ReliableServerRecipe> essenceRecipes = SkyblockRrvPlugin.generateEssenceRecipesOnly();
        if (essenceRecipes.isEmpty()) {
            LOGGER.warn("buildEssenceRecipesOnly: no essence recipes generated.");
            return;
        }

        Map<ReliableServerRecipeType<?>, List<ServerRecipeEntry>> essenceGrouped =
                SkyblockRecipeGrouper.group(essenceRecipes);

        // Merge into the existing grouped map so the full map stays consistent.
        cachedGrouped.putAll(essenceGrouped);

        LOGGER.info("Delta-injecting {} essence upgrade recipes into RRV.",
                essenceRecipes.size());

        RrvCacheInjector.inject(cachedItems, essenceGrouped);
    }
}