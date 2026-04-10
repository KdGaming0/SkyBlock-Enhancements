package com.github.kd_gaming1.skyblockenhancements.compat.rrv;

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
 * needs to be updated. A version check on startup logs a warning when the installed
 * RRV version differs from the one this facade was built against.
 *
 * <p>All methods must be called on the render/main thread (via {@code Minecraft.getInstance().execute()}).
 */
public final class RrvCacheInjector {

    /**
     * The RRV version this facade was tested against. Update this constant whenever
     * RRV publishes a new release and you've verified compatibility.
     */
    private static final String EXPECTED_RRV_VERSION = "6.6.2";

    private static boolean versionChecked = false;

    private RrvCacheInjector() {}

    /**
     * Pushes pre-built items and recipes into RRV's low-level cache. Queues
     * the work through {@link ClientRecipeManager} and runs it synchronously.
     *
     * @param items   item stacks to register as stack-sensitive entries
     * @param grouped recipes grouped by their server recipe type
     */
    @SuppressWarnings("UnstableApiUsage")
    public static void inject(
            List<ItemStack> items,
            Map<ReliableServerRecipeType<?>, List<ServerRecipeEntry>> grouped) {

        checkVersionOnce();

        ClientRecipeManager.INSTANCE.queueTask(() -> {
            injectItems(items);
            injectRecipes(grouped);
            LowEndRecipeCache.INSTANCE.processRecipes();
        });

        ClientRecipeManager.INSTANCE.runTasks();
    }

    // ── Item injection ──────────────────────────────────────────────────────────

    private static void injectItems(List<ItemStack> items) {
        LowEndRecipeCache.INSTANCE.stackSensitiveStartRecieved(items.size());
        for (ItemStack stack : items) {
            LowEndRecipeCache.INSTANCE.stackSensitiveRecieved(
                    new ItemView.StackSensitive(stack));
        }
        LowEndRecipeCache.INSTANCE.stackSensitiveEndRecieved();
    }

    // ── Recipe injection ────────────────────────────────────────────────────────

    private static void injectRecipes(
            Map<ReliableServerRecipeType<?>, List<ServerRecipeEntry>> grouped) {
        LowEndRecipeCache.INSTANCE.cacheStartRecieved(grouped.size());
        for (var entry : grouped.entrySet()) {
            LowEndRecipeCache.INSTANCE.startCaching(entry.getKey(), entry.getValue().size());
            for (ServerRecipeEntry recipeEntry : entry.getValue()) {
                LowEndRecipeCache.INSTANCE.cacheModRecipe(recipeEntry);
            }
            LowEndRecipeCache.INSTANCE.endCaching(entry.getKey());
        }
    }

    // ── Version check ───────────────────────────────────────────────────────────

    /**
     * Logs a warning if the installed RRV version doesn't match the expected version.
     * Only runs once per session — subsequent calls are no-ops.
     */
    private static void checkVersionOnce() {
        if (versionChecked) return;
        versionChecked = true;

        FabricLoader.getInstance().getModContainer("rrv").ifPresent(mod -> {
            String installed = mod.getMetadata().getVersion().getFriendlyString();
            if (!installed.startsWith(EXPECTED_RRV_VERSION)) {
                LOGGER.warn(
                        "RRV version mismatch: expected {}, found {}. "
                                + "Cache injection may break if RRV changed internal APIs. "
                                + "Please report issues at the Skyblock Enhancements issue tracker.",
                        EXPECTED_RRV_VERSION, installed);
            }
        });
    }
}