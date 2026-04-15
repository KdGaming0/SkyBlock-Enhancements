package com.github.kd_gaming1.skyblockenhancements.compat.rrv;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

import cc.cassian.rrv.api.recipe.ReliableServerRecipe;
import cc.cassian.rrv.api.recipe.ReliableServerRecipeType;
import cc.cassian.rrv.common.recipe.ServerRecipeManager.ServerRecipeEntry;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.npc.SkyblockNpcInfoRecipeType;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.npc.SkyblockNpcInfoRegistry;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.npc.SkyblockNpcShopRecipeType;
import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.repo.*;

import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Owns the cached SkyBlock item/recipe data that gets injected into RRV.
 *
 * <p>Separating this from {@link SkyblockRrvClientPlugin} keeps the plugin class focused
 * on RRV lifecycle callbacks, while this class handles the heavy data preparation.
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

        List<ItemStack> items = buildItemList();
        Map<ReliableServerRecipeType<?>, List<ServerRecipeEntry>> grouped = buildRecipes();

        // Populate FullStackListCache directly from the built items — no redundant scan.
        FullStackListCache.populateFromInjected(items);

        // Publish atomically — readers check cachedItems as the "ready" signal.
        cachedGrouped = grouped;
        cachedItems = items;

        LOGGER.info("Built injection cache: {} items, {} recipes.",
                items.size(),
                grouped.values().stream().mapToInt(List::size).sum());
    }

    // ── Item list construction ────────────────────────────────────────────────────

    /**
     * Builds the sorted item list from the NEU registry. When compact mode is enabled,
     * child items from {@code parents.json} are excluded — their recipes remain accessible
     * through the parent item.
     */
    private static List<ItemStack> buildItemList() {
        boolean compact = SkyblockEnhancementsConfig.compactItemList;

        // Pair stacks with their NeuItem for sorting without re-resolving.
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
                candidates.add(new StackWithMeta(stack, neuItem));
            }
        }

        // Sort: primary = family prefix, secondary = rarity, tertiary = display name
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
                        s -> s.neuItem().rarity != null
                                ? s.neuItem().rarity.ordinal() : Integer.MAX_VALUE)
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
        return items;
    }

    // ── Recipe construction ──────────────────────────────────────────────────────

    /**
     * Generates all recipes and groups them by type with synthetic IDs.
     */
    private static Map<ReliableServerRecipeType<?>, List<ServerRecipeEntry>> buildRecipes() {
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
        return grouped;
    }
}