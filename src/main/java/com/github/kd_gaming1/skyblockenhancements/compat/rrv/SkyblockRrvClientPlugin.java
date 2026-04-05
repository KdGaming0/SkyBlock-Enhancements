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
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.kat.SkyblockKatUpgradeClientRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.kat.SkyblockKatUpgradeServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.npc.SkyblockNpcInfoClientRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.npc.SkyblockNpcInfoRecipeType;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.npc.SkyblockNpcInfoRegistry;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.npc.SkyblockNpcInfoServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.npc.SkyblockNpcShopClientRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.npc.SkyblockNpcShopRecipeType;
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
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Client-side RRV plugin. Registers server→client recipe wrappers and handles cache spoofing
 * to inject SkyBlock items and recipes into RRV as if they came from a compatible server.
 *
 * <p>Generated data (items + recipes) is cached after the first build so that subsequent RRV
 * reload callbacks (triggered by every Hypixel lobby switch) can re-inject the same data
 * without the expensive repo re-parse. Call {@link #invalidateCache()} when the NEU repo is
 * actually re-downloaded.
 */
public class SkyblockRrvClientPlugin implements ReliableRecipeViewerClientPlugin {

    // ── Cached spoof data ────────────────────────────────────────────────────────

    /** Pre-built item stacks from the NEU repo. {@code null} until first generation. */
    @Nullable private static List<ItemStack> cachedItems;

    /** Pre-grouped recipe entries ready for injection. {@code null} until first generation. */
    @Nullable private static Map<ReliableServerRecipeType<?>, List<ServerRecipeEntry>> cachedGrouped;

    /**
     * {@code true} once data has been successfully injected into RRV's cache. Cleared by
     * {@link #invalidateCache()} when the NEU repo changes, forcing a full re-injection.
     * Prevents redundant re-injection on Hypixel lobby switches where RRV fires the reload
     * callback but never actually clears its own cache.
     */
    private static boolean injected;

    /** Clears the cached data so the next spoof call regenerates and re-injects everything. */
    public static void invalidateCache() {
        cachedItems = null;
        cachedGrouped = null;
        injected = false;
        SkyblockNpcShopRecipeType.INSTANCE.clearCache();
        SkyblockNpcInfoRecipeType.INSTANCE.clearCache();
        FullStackListCache.invalidate();
    }

    @Override
    public void onIntegrationInitialize() {
        if (!RrvCompat.isActive()) return;

        registerRecipeWrappers();

        // Re-inject recipes when RRV clears its cache (e.g. on server reconnect / lobby switch).
        ItemView.addClientReloadCallback(() -> {
            CompletableFuture<Void> future = SkyblockEnhancements.getInstance().getRepoFuture();
            if (future.isDone()) {
                spoofRrvCache();
            } else {
                future.thenRun(SkyblockRrvClientPlugin::spoofRrvCache);
            }
        });
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
                        r.getMobName(), r.getDrops(), r.getChances(), r.getLevel(),
                        r.getCombatXp(), r.getWikiUrls())));

        ItemView.addClientRecipeWrapper(
                SkyblockKatUpgradeServerRecipe.TYPE,
                r -> List.of(new SkyblockKatUpgradeClientRecipe(
                        r.getInput(), r.getOutput(), r.getMaterials(), r.getCoins(),
                        r.getTimeSeconds(), r.getWikiUrls())));

        ItemView.addClientRecipeWrapper(
                SkyblockWikiInfoServerRecipe.TYPE,
                r -> List.of(new SkyblockWikiInfoClientRecipe(
                        r.getDisplayItem(), r.getWikiUrls())));
    }

    // ── Cache spoofing ───────────────────────────────────────────────────────────

    /**
     * Injects SkyBlock items and recipes into RRV's internal cache. On the first call (or after
     * {@link #invalidateCache()}), generates data from the NEU repo and injects it. Subsequent
     * calls are no-ops since RRV doesn't clear its cache on lobby switches.
     */
    public static void spoofRrvCache() {
        if (!NeuItemRegistry.isLoaded()) return;
        if (injected) {
            LOGGER.debug("RRV cache already populated — skipping re-injection.");
            return;
        }

        SkyblockNpcInfoRegistry.clear();
        ensureCachePopulated();

        Minecraft.getInstance().execute(() -> {
            if (injected) return;

            injectIntoRrv(cachedItems, cachedGrouped);
            injected = true;

            assert cachedItems != null;
            assert cachedGrouped != null;
            LOGGER.info(
                    "Spoofed RRV cache with {} items and {} recipes.",
                    cachedItems.size(),
                    cachedGrouped.values().stream().mapToInt(List::size).sum());
        });
    }

    /**
     * Builds the cached item/recipe data if it doesn't already exist.
     */
    private static void ensureCachePopulated() {
        if (cachedItems != null && cachedGrouped != null) {
            return;
        }

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

        cachedItems = items;
        cachedGrouped = grouped;
    }

    /** Pushes pre-built items and recipes into RRV's low-level cache. */
    @SuppressWarnings("UnstableApiUsage")
    private static void injectIntoRrv(
            List<ItemStack> items,
            Map<ReliableServerRecipeType<?>, List<ServerRecipeEntry>> grouped) {

        ClientRecipeManager.INSTANCE.queueTask(() -> {
            LowEndRecipeCache.INSTANCE.stackSensitiveStartRecieved(items.size());
            for (ItemStack stack : items) {
                LowEndRecipeCache.INSTANCE.stackSensitiveRecieved(
                        new ItemView.StackSensitive(stack));
            }
            LowEndRecipeCache.INSTANCE.stackSensitiveEndRecieved();

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
    }
}