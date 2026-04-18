package com.github.kd_gaming1.skyblockenhancements.compat.rrv.injection;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

import cc.cassian.rrv.api.recipe.ItemView;
import cc.cassian.rrv.api.recipe.ReliableServerRecipeType;
import cc.cassian.rrv.common.recipe.ClientRecipeManager;
import cc.cassian.rrv.common.recipe.ServerRecipeManager.ServerRecipeEntry;
import cc.cassian.rrv.common.recipe.cache.LowEndRecipeCache;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemStack;

/**
 * Isolates all direct calls to RRV internal cache classes behind a single facade.
 *
 * <p>This class is the only place that touches {@link LowEndRecipeCache} and
 * {@link ClientRecipeManager} — if RRV refactors those internals, only this file
 * needs to be updated.
 *
 * <p>Items are injected directly into {@link LowEndRecipeCache} (they are not
 * transactional and don't need to go through the task queue). Recipes are queued
 * as batched tasks per type, then {@code processRecipes} is queued last and
 * {@link ClientRecipeManager#runTasks()} triggers async execution.
 */
@SuppressWarnings("UnstableApiUsage")
public final class RrvCacheInjector {

    /**
     * The RRV version this facade was tested against. Update this constant whenever
     * RRV publishes a new release and you've verified compatibility.
     */
    private static final String EXPECTED_RRV_VERSION = "6.6.2";

    private static boolean versionChecked = false;

    private RrvCacheInjector() {}

    /**
     * Pushes pre-built items and recipes into RRV's low-level cache.
     *
     * <p>Items are registered directly (the start/receive/end protocol is not
     * transactional in RRV — each call independently modifies ClientRecipeCache).
     * Recipes are batched per type through the task queue to respect the per-type
     * caching protocol in {@link LowEndRecipeCache}.
     */
    @SuppressWarnings("UnstableApiUsage")
    public static void inject(
            List<ItemStack> items,
            Map<ReliableServerRecipeType<?>, List<ServerRecipeEntry>> grouped) {

        checkVersionOnce();

        // Items: direct injection, no task queue needed.
        injectItems(items);

        // Recipes: queue per-type batches then processRecipes at the end.
        queueRecipeInjection(grouped);

        // Trigger async execution of all queued recipe tasks.
        ClientRecipeManager.INSTANCE.runTasks();
    }

    // ── Item injection ──────────────────────────────────────────────────────────

    /**
     * Registers all items as stack-sensitives. These calls go directly to
     * LowEndRecipeCache → ClientRecipeCache and don't need the task queue.
     */
    private static void injectItems(List<ItemStack> items) {
        LowEndRecipeCache.INSTANCE.stackSensitiveStartRecieved(items.size());
        for (ItemStack stack : items) {
            LowEndRecipeCache.INSTANCE.stackSensitiveRecieved(
                    new ItemView.StackSensitive(stack));
        }
        LowEndRecipeCache.INSTANCE.stackSensitiveEndRecieved();
    }

    // ── Recipe injection ────────────────────────────────────────────────────────

    /**
     * Queues recipe injection as tasks. Each recipe type's start→recipes→end cycle
     * is a single task to satisfy LowEndRecipeCache's per-type protocol.
     * {@code processRecipes} is queued as the final task.
     */
    private static void queueRecipeInjection(
            Map<ReliableServerRecipeType<?>, List<ServerRecipeEntry>> grouped) {

        // cacheStartRecieved tells LowEndRecipeCache how many types to expect.
        ClientRecipeManager.INSTANCE.queueTask(
                () -> LowEndRecipeCache.INSTANCE.cacheStartRecieved(grouped.size()));

        // Each type's full cycle is one task — startCaching, all recipes, endCaching.
        for (var entry : grouped.entrySet()) {
            ReliableServerRecipeType<?> type = entry.getKey();
            List<ServerRecipeEntry> recipes = entry.getValue();

            ClientRecipeManager.INSTANCE.queueTask(() -> {
                LowEndRecipeCache.INSTANCE.startCaching(type, recipes.size());
                for (ServerRecipeEntry recipeEntry : recipes) {
                    LowEndRecipeCache.INSTANCE.cacheModRecipe(recipeEntry);
                }
                LowEndRecipeCache.INSTANCE.endCaching(type);
            });
        }

        // processRecipes wraps, indexes, and clears the cache — must run last.
        ClientRecipeManager.INSTANCE.queueTask(
                ClientRecipeManager.INSTANCE::processRecipes);
    }

    // ── Version check ───────────────────────────────────────────────────────────

    private static void checkVersionOnce() {
        if (versionChecked) return;
        versionChecked = true;

        FabricLoader.getInstance().getModContainer("rrv").ifPresent(mod -> {
            String installed = mod.getMetadata().getVersion().getFriendlyString();
            if (!installed.startsWith(EXPECTED_RRV_VERSION)) {
                LOGGER.warn(
                        "RRV version mismatch: expected {}, found {}. "
                                + "Cache injection may break if RRV changed internal APIs.",
                        EXPECTED_RRV_VERSION, installed);
            }
        });
    }
}