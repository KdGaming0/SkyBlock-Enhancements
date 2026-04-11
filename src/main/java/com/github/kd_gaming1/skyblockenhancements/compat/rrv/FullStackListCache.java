package com.github.kd_gaming1.skyblockenhancements.compat.rrv;

import cc.cassian.rrv.api.recipe.ItemView;
import cc.cassian.rrv.common.recipe.ClientRecipeCache;
import com.github.kd_gaming1.skyblockenhancements.mixin.rrv.RrvCategoryFilterMixin;
import com.github.kd_gaming1.skyblockenhancements.repo.NeuItem;
import com.github.kd_gaming1.skyblockenhancements.repo.NeuItemRegistry;
import com.github.kd_gaming1.skyblockenhancements.repo.SkyblockItemCategory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

/**
 * Caches the full SkyBlock item list and several per-stack derived values used by the
 * RRV item overlay. All caches are keyed by {@link ItemStack} identity (not equality),
 * which is safe because every stack originates from {@code ItemStackBuilder.CACHE} and
 * is never replaced during a session.
 *
 * <p>Caches maintained:
 * <ul>
 *   <li>Full item list ({@link #cachedList})</li>
 *   <li>Lowercase display names ({@link #cachedNames}) — avoids {@code toHoverableText()} per keystroke</li>
 *   <li>SkyBlock internal IDs ({@link #cachedIds}) — avoids {@code NbtComponent.copyTag()} in the filter loop</li>
 *   <li>Resolved {@link NeuItem} references ({@link #cachedNeuItems}) — avoids HashMap lookup in the filter loop</li>
 *   <li>Per-category identity sets ({@link #cachedByCategory}) — O(1) category membership test
 *       with no NeuItem resolution in {@link RrvCategoryFilterMixin}</li>
 * </ul>
 *
 * <p>All caches are invalidated together on RRV client reload or NEU repo change.
 */
public final class FullStackListCache {

    private static volatile List<ItemStack> cachedList;
    private static volatile Map<ItemStack, String> cachedNames;

    /** SkyBlock internal ID per stack (e.g. {@code "ASPECT_OF_THE_END"}). */
    private static volatile Map<ItemStack, String> cachedIds;

    /** Resolved {@link NeuItem} per stack — eliminates registry lookup in the filter path. */
    private static volatile Map<ItemStack, NeuItem> cachedNeuItems;

    /**
     * Pre-built identity sets grouped by {@link SkyblockItemCategory}.
     */
    private static volatile Map<SkyblockItemCategory, Set<ItemStack>> cachedByCategory;

    private FullStackListCache() {}

    // ── Public accessors ─────────────────────────────────────────────────────────

    /** Returns the cached item list, building it if necessary. */
    public static List<ItemStack> getOrBuild() {
        List<ItemStack> snapshot = cachedList;
        if (snapshot != null) return snapshot;
        return buildCache();
    }

    /**
     * Returns the pre-computed lowercased display name for the given stack.
     * Falls back to a live computation if the cache has not been built yet.
     */
    public static String getLowercaseName(ItemStack stack) {
        Map<ItemStack, String> names = cachedNames;
        if (names != null) {
            String cached = names.get(stack);
            if (cached != null) return cached;
        }
        return stack.getHoverName().getString().toLowerCase();
    }

    /**
     * Returns the pre-extracted SkyBlock internal ID for the given stack without
     * allocating an NBT copy. Falls back to a live {@code copyTag()} call for stacks
     * that are not in the overlay cache (e.g. recipe ingredient/result stacks).
     *
     * <p>This is the single authoritative place to extract a SkyBlock ID — both
     * {@link SkyblockCategoryFilter} and {@link SkyblockRecipeUtil} should delegate here.
     */
    @Nullable
    public static String getCachedId(ItemStack stack) {
        if (stack.isEmpty()) return null;

        Map<ItemStack, String> ids = cachedIds;
        if (ids != null) {
            String id = ids.get(stack);
            if (id != null) return id;
        }

        // Fallback: live extraction for stacks outside the overlay list (recipe slots, etc.)
        return extractIdFromStack(stack);
    }

    /**
     * Returns the pre-resolved {@link NeuItem} for the given stack without a registry
     * lookup or NBT copy. Falls back to a live lookup for non-overlay stacks.
     */
    @Nullable
    public static NeuItem getCachedNeuItem(ItemStack stack) {
        if (stack.isEmpty()) return null;

        Map<ItemStack, NeuItem> items = cachedNeuItems;
        if (items != null) {
            NeuItem item = items.get(stack);
            if (item != null) return item;
        }

        // Fallback: live resolution for non-overlay stacks
        String id = extractIdFromStack(stack);
        if (id == null || id.isEmpty()) return null;
        return NeuItemRegistry.get(id);
    }

    /**
     * Returns the pre-built identity set of all overlay stacks belonging to the given
     * {@link SkyblockItemCategory}. The returned set uses reference equality (identity),
     * so {@link Set#contains} is an O(1) probe against the overlay list's cached stack
     * references — no NeuItem resolution or NBT access required.
     *
     * <p>Triggers a full cache build if the cache has not yet been populated. Returns an
     * empty set for unknown or null categories.
     */
    public static Set<ItemStack> getCategoryItems(@Nullable SkyblockItemCategory category) {
        if (category == null) return Collections.emptySet();

        Map<SkyblockItemCategory, Set<ItemStack>> map = cachedByCategory;
        if (map == null) {
            buildCache();
            map = cachedByCategory;
        }
        if (map == null) return Collections.emptySet();

        Set<ItemStack> result = map.get(category);
        return result != null ? result : Collections.emptySet();
    }

    /** Clears all caches so the next access rebuilds from the registry. */
    public static void invalidate() {
        cachedList       = null;
        cachedNames      = null;
        cachedIds        = null;
        cachedNeuItems   = null;
        cachedByCategory = null;
    }

    // ── Internal ────────────────────────────────────────────────────────────────

    @SuppressWarnings("UnstableApiUsage")
    private static List<ItemStack> buildCache() {
        List<ItemStack> results = new ArrayList<>();
        BuiltInRegistries.ITEM.forEach(item -> {
            for (ItemView.StackSensitive sensitive :
                    ClientRecipeCache.INSTANCE.getStackSensitives(item)) {
                results.add(sensitive.stack());
            }
        });

        int size = results.size();
        Map<ItemStack, String>   names    = new IdentityHashMap<>(size);
        Map<ItemStack, String>   ids      = new IdentityHashMap<>(size);
        Map<ItemStack, NeuItem>  neuItems = new IdentityHashMap<>(size);

        // Per-category buckets — IdentityHashMap-backed sets for O(1) membership tests
        Map<SkyblockItemCategory, Set<ItemStack>> byCategory = new EnumMap<>(SkyblockItemCategory.class);
        for (SkyblockItemCategory cat : SkyblockItemCategory.values()) {
            byCategory.put(cat, Collections.newSetFromMap(new IdentityHashMap<>()));
        }

        for (ItemStack stack : results) {
            names.put(stack, stack.getHoverName().getString().toLowerCase());

            String id = extractIdFromStack(stack);
            if (id != null && !id.isEmpty()) {
                ids.put(stack, id);

                NeuItem neuItem = NeuItemRegistry.get(id);
                if (neuItem != null) {
                    neuItems.put(stack, neuItem);

                    // Eagerly resolve and memoise the category so the filter path never
                    // needs to call fromNeuItem() during an overlay render or updateQuery().
                    if (neuItem.category == null) {
                        neuItem.category = SkyblockItemCategory.fromNeuItem(neuItem);
                    }
                    if (neuItem.category != null) {
                        byCategory.get(neuItem.category).add(stack);
                    }
                }
            }
        }

        // Wrap each set and the outer map as unmodifiable to prevent accidental mutation.
        EnumMap<SkyblockItemCategory, Set<ItemStack>> immutableByCategory =
                new EnumMap<>(SkyblockItemCategory.class);
        for (Map.Entry<SkyblockItemCategory, Set<ItemStack>> entry : byCategory.entrySet()) {
            immutableByCategory.put(entry.getKey(), Collections.unmodifiableSet(entry.getValue()));
        }

        List<ItemStack> immutable = Collections.unmodifiableList(results);
        cachedNames      = names;
        cachedIds        = ids;
        cachedNeuItems   = neuItems;
        cachedByCategory = Collections.unmodifiableMap(immutableByCategory);
        cachedList       = immutable; // publish last — readers use this as the "cache ready" signal
        return immutable;
    }

    /**
     * Extracts the SkyBlock internal ID directly from a stack's {@code CUSTOM_DATA}
     * component. This allocates an NBT copy and should only be called for stacks that
     * are not already in the identity caches (i.e. recipe slots, not overlay items).
     */
    @Nullable
    private static String extractIdFromStack(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        // copyTag() allocates — intentionally limited to the non-cached fallback path
        String id = data.copyTag().getStringOr("id", "");
        return id.isEmpty() ? null : id;
    }

    static {
        ItemView.addClientReloadCallback(FullStackListCache::invalidate);
    }
}