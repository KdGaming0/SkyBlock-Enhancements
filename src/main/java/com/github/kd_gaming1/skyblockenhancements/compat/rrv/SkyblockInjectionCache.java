package com.github.kd_gaming1.skyblockenhancements.compat.rrv;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

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
    @Nullable private static volatile Map<ReliableServerRecipeType<?>, List<ServerRecipeEntry>> cachedGrouped;

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
        HypixelItemsRegistry.clear();
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
        cachedGrouped = grouped;
        cachedItems = items;

        LOGGER.info("Built injection cache: {} items, {} recipes.",
                items.size(),
                grouped.values().stream().mapToInt(List::size).sum());
    }
}