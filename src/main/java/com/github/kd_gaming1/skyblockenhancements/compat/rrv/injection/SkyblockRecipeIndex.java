package com.github.kd_gaming1.skyblockenhancements.compat.rrv.injection;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.common.recipe.ClientRecipeCache;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.reforge.SkyblockReforgeClientRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.util.SkyblockRecipeUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.item.ItemStack;

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

    /**
     * Cache for filtered ingredient lookups. {@link #redirectsAsIngredient} is deterministic
     * for a given SkyBlock ID, so we avoid recomputing it for duplicate inventory items.
     */
    private static final Map<String, List<ReliableClientRecipe>> ingredientFilterCache = new java.util.HashMap<>();
    /** Same as above for result lookups. */
    private static final Map<String, List<ReliableClientRecipe>> resultFilterCache = new java.util.HashMap<>();

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

        ingredientFilterCache.clear();
        resultFilterCache.clear();

        Map<String, List<ReliableClientRecipe>> byIngredient = new java.util.HashMap<>(4096);
        Map<String, List<ReliableClientRecipe>> byResult = new java.util.HashMap<>(4096);

        for (ReliableClientRecipe recipe : all) {
            if (recipe instanceof SkyblockReforgeClientRecipe reforge) {
                indexReforgeRecipe(reforge, byIngredient, byResult);
            } else {
                indexSlots(recipe.getIngredients(), recipe, byIngredient);
                indexSlots(recipe.getResults(), recipe, byResult);
            }
        }

        // Sort reforge recipes so lookups return deterministically ordered lists.
        Comparator<ReliableClientRecipe> reforgeComparator = reforgeComparator();
        for (List<ReliableClientRecipe> list : byIngredient.values()) {
            if (list.size() > 1) list.sort(reforgeComparator);
        }
        for (List<ReliableClientRecipe> list : byResult.values()) {
            if (list.size() > 1) list.sort(reforgeComparator);
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
     *
     * <p>Results are filtered through {@link ReliableClientRecipe#redirectsAsIngredient} so that
     * reforge recipes only match when the item's rarity matches the recipe's rarity.
     */
    public static List<ReliableClientRecipe> getRecipesForIngredient(ItemStack stack) {
        if (stack.isEmpty()) return List.of();
        ensureFresh();
        String id = SkyblockRecipeUtil.extractSkyblockId(stack);
        if (id == null) return List.of();

        List<ReliableClientRecipe> cached = ingredientFilterCache.get(id);
        if (cached != null) {
            return new ArrayList<>(cached);
        }

        List<ReliableClientRecipe> filtered = filterByRedirect(
                byIngredientId.getOrDefault(id, List.of()), stack, false);
        ingredientFilterCache.put(id, filtered);
        return filtered;
    }

    /**
     * Returns all recipes that produce {@code stack} as a result, identified by its SkyBlock ID.
     *
     * <p>Results are filtered through {@link ReliableClientRecipe#redirectsAsResult} so that
     * reforge recipes only match when the item's rarity matches the recipe's rarity.
     */
    public static List<ReliableClientRecipe> getRecipesForResult(ItemStack stack) {
        if (stack.isEmpty()) return List.of();
        ensureFresh();
        String id = SkyblockRecipeUtil.extractSkyblockId(stack);
        if (id == null) return List.of();

        List<ReliableClientRecipe> cached = resultFilterCache.get(id);
        if (cached != null) {
            return new ArrayList<>(cached);
        }

        List<ReliableClientRecipe> filtered = filterByRedirect(
                byResultId.getOrDefault(id, List.of()), stack, true);
        resultFilterCache.put(id, filtered);
        return filtered;
    }

    /** Clears the index so the next access rebuilds. */
    public static void invalidate() {
        byIngredientId = Map.of();
        byResultId = Map.of();
        lastRecipeCount = -1;
        ingredientFilterCache.clear();
        resultFilterCache.clear();
    }

    // ── Internal ────────────────────────────────────────────────────────────────

    private static void ensureFresh() {
        List<ReliableClientRecipe> all = ClientRecipeCache.INSTANCE.getRecipes();
        if (all.size() != lastRecipeCount) {
            rebuildIndex();
            return;
        }
        // Same-size rebuild detection: RRV may rebuild ClientRecipeCache with the same
        // number of recipes but different objects (e.g. after a reload). Verify that a
        // sample recipe from our index is still present in the live cache.
        if (!byIngredientId.isEmpty()) {
            List<ReliableClientRecipe> sampleList = byIngredientId.values().iterator().next();
            if (!sampleList.isEmpty()) {
                ReliableClientRecipe sample = sampleList.get(0);
                boolean stillPresent = false;
                for (ReliableClientRecipe r : all) {
                    if (r == sample) {
                        stillPresent = true;
                        break;
                    }
                }
                if (!stillPresent) {
                    rebuildIndex();
                }
            }
        }
    }

    /**
     * Filters a list of candidate recipes by calling the appropriate redirect method.
     * For non-reforge recipes this is effectively a no-op; for reforge recipes it ensures
     * only variants whose rarity matches the clicked item survive.
     */
    private static List<ReliableClientRecipe> filterByRedirect(
            List<ReliableClientRecipe> recipes, ItemStack stack, boolean resultRedirect) {
        if (recipes.isEmpty()) return List.of();
        int size = recipes.size();
        List<ReliableClientRecipe> filtered = new ArrayList<>(size);
        for (ReliableClientRecipe recipe : recipes) {
            boolean matches = resultRedirect
                    ? recipe.redirectsAsResult(stack)
                    : recipe.redirectsAsIngredient(stack);
            if (matches) filtered.add(recipe);
        }
        return filtered;
    }

    private static void indexReforgeRecipe(
            SkyblockReforgeClientRecipe reforge,
            Map<String, List<ReliableClientRecipe>> byIngredient,
            Map<String, List<ReliableClientRecipe>> byResult) {

        Set<String> seenResult = Collections.newSetFromMap(new IdentityHashMap<>());
        for (String id : reforge.getResultInternalNames()) {
            if (id != null && !id.isEmpty() && seenResult.add(id)) {
                byResult.computeIfAbsent(id, k -> new ArrayList<>(4)).add(reforge);
                byIngredient.computeIfAbsent(id, k -> new ArrayList<>(4)).add(reforge);
            }
        }

        if (!reforge.isBlacksmith()) {
            String stone = reforge.getStoneInternalName();
            if (stone != null && !stone.isEmpty()) {
                byIngredient.computeIfAbsent(stone, k -> new ArrayList<>(4)).add(reforge);
            }
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

    /**
     * Returns a comparator that sorts reforge recipes by name then rarity.
     * Non-reforge recipes are treated as equal (stable sort preserves their order).
     */
    private static Comparator<ReliableClientRecipe> reforgeComparator() {
        return (a, b) -> {
            boolean aReforge = a instanceof SkyblockReforgeClientRecipe;
            boolean bReforge = b instanceof SkyblockReforgeClientRecipe;
            if (!aReforge && !bReforge) return 0;
            if (aReforge != bReforge) return aReforge ? 1 : -1;

            SkyblockReforgeClientRecipe ra = (SkyblockReforgeClientRecipe) a;
            SkyblockReforgeClientRecipe rb = (SkyblockReforgeClientRecipe) b;
            int nameCmp = ra.getReforgeName().compareTo(rb.getReforgeName());
            if (nameCmp != 0) return nameCmp;
            return Integer.compare(rarityOrdinal(ra.getRarity()), rarityOrdinal(rb.getRarity()));
        };
    }

    private static int rarityOrdinal(String rarity) {
        return switch (rarity) {
            case "COMMON" -> 0;
            case "UNCOMMON" -> 1;
            case "RARE" -> 2;
            case "EPIC" -> 3;
            case "LEGENDARY" -> 4;
            case "MYTHIC" -> 5;
            case "DIVINE" -> 6;
            case "SPECIAL" -> 7;
            case "VERY_SPECIAL" -> 8;
            case "SUPREME" -> 9;
            default -> -1;
        };
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
