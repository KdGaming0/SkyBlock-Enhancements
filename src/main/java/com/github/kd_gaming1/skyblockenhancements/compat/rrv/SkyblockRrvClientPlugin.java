package com.github.kd_gaming1.skyblockenhancements.compat.rrv;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

import cc.cassian.rrv.api.ReliableRecipeViewerClientPlugin;
import cc.cassian.rrv.api.recipe.ItemView;
import cc.cassian.rrv.api.recipe.ReliableServerRecipe;
import cc.cassian.rrv.api.recipe.ReliableServerRecipeType;
import cc.cassian.rrv.common.recipe.ServerRecipeManager.ServerRecipeEntry;
import com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.crafting.SkyblockCraftingClientRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.crafting.SkyblockCraftingServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.drops.SkyblockDropsClientRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.drops.SkyblockDropsServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.essence.SkyblockEssenceUpgradeClientRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.essence.SkyblockEssenceUpgradeServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.forge.SkyblockForgeClientRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.forge.SkyblockForgeServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.kat.SkyblockKatUpgradeClientRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.kat.SkyblockKatUpgradeServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.npc.*;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.trade.SkyblockTradeClientRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.trade.SkyblockTradeServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.wiki.SkyblockWikiInfoClientRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.wiki.SkyblockWikiInfoServerRecipe;
import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.repo.ItemStackBuilder;
import com.github.kd_gaming1.skyblockenhancements.repo.NeuConstantsRegistry;
import com.github.kd_gaming1.skyblockenhancements.repo.NeuItem;
import com.github.kd_gaming1.skyblockenhancements.repo.NeuItemRegistry;
import com.github.kd_gaming1.skyblockenhancements.repo.SkyblockItemCategory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Registers server→client recipe wrappers and handles cache spoofing
 * to inject SkyBlock items and recipes into RRV as if they came from a compatible server.
 *
 * <p>Generated data (items + recipes) is cached after the first build so that subsequent RRV
 * reload callbacks (triggered by every Hypixel lobby switch) can re-inject the same data
 * without the expensive repo re-parse. Call {@link #invalidateCache()} when the NEU repo is
 * actually re-downloaded.
 *
 * <p>Cache injection is delegated to {@link RrvCacheInjector} which isolates all internal
 * RRV API calls behind a version-checked facade.
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
                        r.getDisplayItem(), r.getWikiUrls())));

        ItemView.addClientRecipeWrapper(
                SkyblockEssenceUpgradeServerRecipe.TYPE,
                r -> List.of(new SkyblockEssenceUpgradeClientRecipe(
                        r.getInput(), r.getOutput(), r.getEssence(), r.getCompanions(),
                        r.getStarLevel(), r.getEssenceType(), r.getWikiUrls())));
    }

    // ── Cache spoofing ───────────────────────────────────────────────────────────

    /**
     * Pre-builds the item/recipe cache on a background thread without injecting
     * into RRV. Call this from an async context after the repo download completes.
     * The actual injection will happen when {@link #spoofRrvCache()} is called.
     */
    public static void prepareDataAsync() {
        if (!NeuItemRegistry.isLoaded()) return;
        if (injected) return;

        SkyblockNpcInfoRegistry.clear();
        ensureCachePopulated();

        LOGGER.info("RRV injection data prepared ({} items, {} recipes). "
                        + "Injection will happen on next spoofRrvCache() call.",
                cachedItems != null ? cachedItems.size() : 0,
                cachedGrouped != null
                        ? cachedGrouped.values().stream().mapToInt(List::size).sum() : 0);
    }

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

            if (cachedItems == null || cachedItems.isEmpty()) {
                LOGGER.warn("RRV injection aborted — item cache empty. Will retry on next trigger.");
                return;
            }

            RrvCacheInjector.inject(cachedItems, cachedGrouped);
            injected = true;

            assert cachedGrouped != null;
            LOGGER.info("Injected {} items and {} recipes into RRV.",
                    cachedItems.size(),
                    cachedGrouped.values().stream().mapToInt(List::size).sum());
        });
    }

    /**
     * Builds the cached item/recipe data if it doesn't already exist. When compact mode is
     * enabled, child items (from {@code parents.json}) are excluded from the item list — their
     * recipes remain and are viewable when clicking the parent item.
     *
     * <p>Skips caching entirely if the registry is empty — this can happen transiently during a
     * cache-version-bump re-download, where {@link NeuItemRegistry#clear()} has been called but
     * {@link NeuItemRegistry#markLoaded()} has not yet been reached.
     */
    private static void ensureCachePopulated() {
        if (cachedItems != null && cachedGrouped != null) {
            return;
        }

        if (NeuItemRegistry.getAll().isEmpty()) {
            LOGGER.warn("ensureCachePopulated called with empty registry — skipping cache build.");
            return;
        }

        boolean compact = SkyblockEnhancementsConfig.compactItemList;

        // Build items alongside their NeuItem source so we can sort without re-resolving.
        record StackWithMeta(ItemStack stack, NeuItem neuItem) {}

        List<StackWithMeta> candidates = new ArrayList<>();

        for (Map.Entry<String, NeuItem> entry : NeuItemRegistry.getAll().entrySet()) {
            String itemId = entry.getKey();
            NeuItem neuItem = entry.getValue();

            if (compact && NeuConstantsRegistry.isChild(itemId)) {
                String parentId = NeuConstantsRegistry.getParent(itemId);
                if (ItemFamilyHelper.shouldCompactFamily(parentId)) {
                    continue;
                }
            }

            ItemStack stack = ItemStackBuilder.build(neuItem);
            if (compact && !stack.isEmpty() && ItemFamilyHelper.shouldCompactFamily(itemId)) {
                String compactName = ItemFamilyHelper.buildCompactDisplayName(
                        itemId, neuItem.displayName);
                if (compactName != null) {
                    stack = stack.copy();
                    stack.set(DataComponents.CUSTOM_NAME, Component.literal(compactName));
                }
            }

            if (!stack.isEmpty()) {
                if (neuItem.rarity == null) {
                    neuItem.rarity = SkyblockItemCategory.extractRarity(neuItem);
                }
                candidates.add(new StackWithMeta(stack, neuItem));
            }
        }

        // Sort: primary = rarity ascending (COMMON → SPECIAL), secondary = display name
        candidates.sort(Comparator
                .<StackWithMeta, String>comparing(s -> {
                    String id = s.neuItem().internalName;
                    if (id != null) {
                        int semi = id.indexOf(';');
                        if (semi >= 0) return id.substring(0, semi);
                    }
                    return "";
                })
                .thenComparing(
                        s -> s.neuItem().rarity != null ?
                                s.neuItem().rarity.ordinal() : Integer.MAX_VALUE)
                .thenComparing(
                        s -> s.neuItem().displayName != null
                                ? s.neuItem().displayName.replaceAll("§.", "")
                                : "")
                .thenComparing(
                        s -> s.neuItem().internalName != null
                                ? s.neuItem().internalName
                                : ""));

        List<ItemStack> items = new ArrayList<>(candidates.size());
        for (StackWithMeta s : candidates) {
            items.add(s.stack());
        }

        // Recipes generated for all items (including children) so tier recipes remain accessible.
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
}