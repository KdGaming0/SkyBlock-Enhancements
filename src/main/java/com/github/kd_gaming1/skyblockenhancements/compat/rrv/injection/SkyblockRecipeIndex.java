package com.github.kd_gaming1.skyblockenhancements.compat.rrv.injection;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.common.recipe.ClientRecipeCache;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.util.SkyblockRecipeUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Fast SkyBlock-ID-based index over RRV's client recipe cache.
 *
 * <p>RRV indexes recipes by base {@link net.minecraft.world.item.Item} type, which means pressing U
 * on a {@code PLAYER_HEAD} scans <em>every</em> SkyBlock recipe. This index maps SkyBlock internal
 * IDs directly to the recipes that use them as ingredients or results, giving O(1) lookup.
 *
 * <p>The index is rebuilt automatically after recipe injection ({@link RrvCacheInjector}) and
 * lazily on first access if RRV reloads its cache independently.
 */
public final class SkyblockRecipeIndex {

    /** Immutable map: SkyBlock ID → recipes where that ID appears as an ingredient. */
    private static volatile Map<String, List<ReliableClientRecipe>> byIngredientId = Map.of();
    /** Immutable map: SkyBlock ID → recipes where that ID appears as a result. */
    private static volatile Map<String, List<ReliableClientRecipe>> byResultId = Map.of();
    /** Snapshot of recipe count when the index was last built. Detects stale cache. */
    private static volatile int lastRecipeCount = -1;

    private SkyblockRecipeIndex() {}

    // ── Rebuild ─────────────────────────────────────────────────────────────────

    /**
     * Rebuilds the index from the current {@link ClientRecipeCache}. Called from the task queue
     * after RRV finishes processing injected recipes. Safe to call from any thread.
     */
    public static void rebuildIndex() {
        List<ReliableClientRecipe> all = ClientRecipeCache.INSTANCE.getRecipes();
        if (all.isEmpty()) {
            byIngredientId = Map.of();
            byResultId = Map.of();
            lastRecipeCount = 0;
            return;
        }

        // Avoid rebuilding when the cache hasn't changed.
        if (all.size() == lastRecipeCount) return;

        Map<String, List<ReliableClientRecipe>> byIngredient = new java.util.HashMap<>(4096);
        Map<String, List<ReliableClientRecipe>> byResult = new java.util.HashMap<>(4096);

        for (ReliableClientRecipe recipe : all) {
            indexSlots(recipe.getIngredients(), recipe, byIngredient);
            indexSlots(recipe.getResults(), recipe, byResult);
        }

        // Deduplicate and freeze.
        byIngredientId = freezeMap(byIngredient);
        byResultId = freezeMap(byResult);
        lastRecipeCount = all.size();

        LOGGER.info("SkyblockRecipeIndex rebuilt: {} ingredient IDs, {} result IDs.",
                byIngredientId.size(), byResultId.size());
    }

    // ── Public lookup ───────────────────────────────────────────────────────────

    /**
     * Returns all recipes that use {@code stack} as an ingredient, identified by its SkyBlock ID.
     * Falls back to an empty list for stacks without a SkyBlock ID or when the index is stale.
     */
    public static List<ReliableClientRecipe> getRecipesForIngredient(ItemStack stack) {
        if (stack.isEmpty()) return List.of();
        ensureFresh();
        String id = SkyblockRecipeUtil.extractSkyblockId(stack);
        if (id == null) return List.of();
        return byIngredientId.getOrDefault(id, List.of());
    }

    /**
     * Returns all recipes that produce {@code stack} as a result, identified by its SkyBlock ID.
     */
    public static List<ReliableClientRecipe> getRecipesForResult(ItemStack stack) {
        if (stack.isEmpty()) return List.of();
        ensureFresh();
        String id = SkyblockRecipeUtil.extractSkyblockId(stack);
        if (id == null) return List.of();
        return byResultId.getOrDefault(id, List.of());
    }

    /** Clears the index so the next access rebuilds. */
    public static void invalidate() {
        byIngredientId = Map.of();
        byResultId = Map.of();
        lastRecipeCount = -1;
    }

    // ── Internal ────────────────────────────────────────────────────────────────

    private static void ensureFresh() {
        if (ClientRecipeCache.INSTANCE.getRecipes().size() != lastRecipeCount) {
            rebuildIndex();
        }
    }

    private static void indexSlots(
            List<SlotContent> slots,
            ReliableClientRecipe recipe,
            Map<String, List<ReliableClientRecipe>> target) {

        // Use identity set to avoid adding the same recipe twice for duplicate IDs in one recipe.
        Set<String> seenThisRecipe = Collections.newSetFromMap(new IdentityHashMap<>());

        for (SlotContent slot : slots) {
            for (ItemStack stack : slot.getValidContents()) {
                String id = SkyblockRecipeUtil.extractSkyblockId(stack);
                if (id != null && !id.isEmpty() && seenThisRecipe.add(id)) {
                    target.computeIfAbsent(id, k -> new ArrayList<>(4)).add(recipe);
                }
            }
        }
    }

    private static Map<String, List<ReliableClientRecipe>> freezeMap(
            Map<String, List<ReliableClientRecipe>> mutable) {

        Map<String, List<ReliableClientRecipe>> frozen = new java.util.HashMap<>(mutable.size());
        for (var entry : mutable.entrySet()) {
            // Return mutable lists — RRV's RecipeViewMenu may reassign its internal
            // reference but never mutates the passed list, but we play it safe.
            frozen.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return Collections.unmodifiableMap(frozen);
    }
}
