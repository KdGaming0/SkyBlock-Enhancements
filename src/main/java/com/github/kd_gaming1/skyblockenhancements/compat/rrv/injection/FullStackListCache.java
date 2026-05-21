package com.github.kd_gaming1.skyblockenhancements.compat.rrv.injection;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

import cc.cassian.rrv.api.recipe.ItemView;
import cc.cassian.rrv.common.recipe.ClientRecipeCache;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.search.SkyblockSearchIndex;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.util.PetIdResolver;
import com.github.kd_gaming1.skyblockenhancements.mixin.access.CustomDataAccessor;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItem;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItemRegistry;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.SkyblockItemCategory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

/**
 * Caches the full SkyBlock item list and several per-stack derived values used by the
 * RRV item overlay. All caches are keyed by {@link ItemStack} identity (not equality),
 * which is safe because every stack originates from {@code ItemStackBuilder.CACHE} and
 * is never replaced during a session.
 *
 * <p>The primary population path is {@link #populateFromInjected(List)}, called from
 * {@link SkyblockInjectionCache#buildCache()} on the background thread, using
 * the exact same stack references that will be injected into RRV. This eliminates
 * the redundant registry scan that the old {@link #buildCacheFromRegistry()} performed.
 *
 * <p>All caches are invalidated together on RRV client reload or NEU repo change.
 */
public final class FullStackListCache {

    @Nullable private static volatile List<ItemStack> cachedList;
    @Nullable private static volatile Map<ItemStack, String> cachedNames;
    @Nullable private static volatile Map<ItemStack, String> cachedIds;
    @Nullable private static volatile Map<ItemStack, NeuItem> cachedNeuItems;
    @Nullable private static volatile Map<SkyblockItemCategory, Set<ItemStack>> cachedByCategory;
    @Nullable private static volatile SkyblockSearchIndex cachedSearchIndex;

    private FullStackListCache() {}

    // ── RRV callback registration ───────────────────────────────────────────────

    /** Registers the RRV client-reload invalidation callback.
     *  Call only after confirming RRV is active. */
    public static void registerRrvReloadCallback() {
        ItemView.addClientReloadCallback(FullStackListCache::invalidate);
    }

    // ── Primary population path ──────────────────────────────────────────────────

    /**
     * Populates all caches directly from the given item list. Called from
     * {@link SkyblockInjectionCache#buildCache()} on the background thread, using
     * the exact same stack references that will be injected into RRV.
     */
    public static void populateFromInjected(List<ItemStack> items) {
        buildDerivedCaches(items);
    }

    // ── Public accessors ─────────────────────────────────────────────────────────

    /**
     * Returns the cached item list, building from registry as a fallback if
     * {@link #populateFromInjected} hasn't been called yet.
     */
    public static List<ItemStack> getOrBuild() {
        List<ItemStack> snapshot = cachedList;
        if (snapshot != null) return snapshot;
        return buildCacheFromRegistry();
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
     * Returns the pre-extracted SkyBlock internal ID for the given stack.
     * Uses the identity cache for overlay stacks and falls back to a live
     * extraction for arbitrary stacks (inventory items, recipe copies, etc.).
     *
     * <p>The extraction path supports both mod-generated stacks ({@code CustomData.id})
     * and server-sent Hypixel stacks ({@code ExtraAttributes.id}).
     */
    @Nullable
    public static String getCachedId(ItemStack stack) {
        if (stack.isEmpty()) return null;

        // Fast path: overlay stacks are identity-stable
        Map<ItemStack, String> ids = cachedIds;
        if (ids != null) {
            String id = ids.get(stack);
            if (id != null) return id;
        }

        // Fallback: extract without copying NBT.
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

        String id = extractIdFromStack(stack);
        if (id == null || id.isEmpty()) return null;
        return NeuItemRegistry.get(id);
    }

    /**
     * Returns the pre-built identity set of all overlay stacks belonging to the given
     * {@link SkyblockItemCategory}. Returns an empty set for unknown or null categories.
     */
    public static Set<ItemStack> getCategoryItems(@Nullable SkyblockItemCategory category) {
        if (category == null) return Collections.emptySet();

        Map<SkyblockItemCategory, Set<ItemStack>> map = cachedByCategory;
        if (map == null) {
            buildCacheFromRegistry();
            map = cachedByCategory;
        }
        if (map == null) return Collections.emptySet();

        Set<ItemStack> result = map.get(category);
        return result != null ? result : Collections.emptySet();
    }

    /**
     * Returns the pre-built {@link SkyblockSearchIndex} for fast inverted search,
     * or {@code null} if the cache has not been populated yet.
     */
    @Nullable
    public static SkyblockSearchIndex getSearchIndex() {
        return cachedSearchIndex;
    }

    /** Clears all caches so the next access rebuilds. */
    public static void invalidate() {
        cachedList = null;
        cachedNames = null;
        cachedIds = null;
        cachedNeuItems = null;
        cachedByCategory = null;
        cachedSearchIndex = null;
    }

    // ── Shared cache construction ───────────────────────────────────────────────

    /**
     * Builds all derived caches (names, IDs, NeuItems, category sets) from a list
     * of stacks. Synchronised to prevent races when the fallback path and the primary
     * path run concurrently.
     */
    private static synchronized void buildDerivedCaches(List<ItemStack> items) {
        // Double-checked: another thread may have built the cache while we waited.
        if (cachedList != null) return;

        int size = items.size();
        Map<ItemStack, String> names = new IdentityHashMap<>(size);
        Map<ItemStack, String> ids = new IdentityHashMap<>(size);
        Map<ItemStack, NeuItem> neuItems = new IdentityHashMap<>(size);

        Map<SkyblockItemCategory, Set<ItemStack>> byCategory = new EnumMap<>(SkyblockItemCategory.class);
        for (SkyblockItemCategory cat : SkyblockItemCategory.values()) {
            byCategory.put(cat, Collections.newSetFromMap(new IdentityHashMap<>()));
        }

        for (ItemStack stack : items) {
            names.put(stack, stack.getHoverName().getString().toLowerCase(java.util.Locale.ROOT));

            String id = extractIdFromStack(stack);
            if (id != null && !id.isEmpty()) {
                ids.put(stack, id);

                NeuItem neuItem = NeuItemRegistry.get(id);
                if (neuItem != null) {
                    neuItems.put(stack, neuItem);

                    if (neuItem.category != null) {
                        byCategory.get(neuItem.category).add(stack);
                    }
                }
            }
        }

        EnumMap<SkyblockItemCategory, Set<ItemStack>> immutableByCategory =
                new EnumMap<>(SkyblockItemCategory.class);
        for (var entry : byCategory.entrySet()) {
            immutableByCategory.put(entry.getKey(), Collections.unmodifiableSet(entry.getValue()));
        }

        SkyblockSearchIndex searchIndex = new SkyblockSearchIndex(items, neuItems);

        cachedNames = Collections.unmodifiableMap(names);
        cachedIds = Collections.unmodifiableMap(ids);
        cachedNeuItems = Collections.unmodifiableMap(neuItems);
        cachedByCategory = Collections.unmodifiableMap(immutableByCategory);
        cachedSearchIndex = searchIndex;
        cachedList = Collections.unmodifiableList(items); // publish last — readers use this as the ready signal
    }

    // ── Fallback: registry scan ─────────────────────────────────────────────────

    /**
     * Fallback path that scans {@code BuiltInRegistries.ITEM} → {@code ClientRecipeCache}
     * to reconstruct the item list. Only used if {@link #populateFromInjected} was never
     * called (shouldn't happen in normal flow).
     */
    @SuppressWarnings("UnstableApiUsage")
    private static synchronized List<ItemStack> buildCacheFromRegistry() {
        LOGGER.warn("FullStackListCache.buildCacheFromRegistry() fallback triggered — "
                + "populateFromInjected() was expected to run first. "
                + "Please report this if it happens consistently.");

        List<ItemStack> results = new ArrayList<>();
        BuiltInRegistries.ITEM.forEach(item -> {
            for (ItemView.StackSensitive sensitive :
                    ClientRecipeCache.INSTANCE.getStackSensitives(item)) {
                results.add(sensitive.stack());
            }
        });

        buildDerivedCaches(results);
        return cachedList != null ? cachedList : Collections.emptyList();
    }

    // ── ID extraction ───────────────────────────────────────────────────────────

    /**
     * Extracts the SkyBlock internal ID from a stack. Checks three sources:
     * <ol>
     *   <li>{@code petInfo} — for pets the actual type/tier live here (NEU & Hypixel formats).</li>
     *   <li>{@code CustomData.id} — set by {@code ItemStackBuilder} on mod-generated stacks.</li>
     *   <li>{@code ExtraAttributes.id} — present on server-sent Hypixel items.</li>
     * </ol>
     *
     * <p>Reads directly from the underlying NBT without copying the compound,
     * so this is safe to call from hot paths.
     */
    @Nullable
    private static String extractIdFromStack(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            CompoundTag rawTag = ((CustomDataAccessor) (Object) data).getRawTag();

            // 1. Pets: petInfo JSON contains the real type and tier.
            String petId = PetIdResolver.resolveFromTag(rawTag);
            if (petId != null) return petId;

            // 2. Mod-generated stacks (and most server items).
            String id = rawTag.getStringOr("id", "");
            if (!id.isEmpty() && !"PET".equals(id)) return id;
        }

        // 3. Fallback: Hypixel server items store the ID in ExtraAttributes.
        CompoundTag extraAttributes = getExtraAttributes(stack);
        if (extraAttributes != null) {
            String id = extraAttributes.getStringOr("id", "");
            if (!id.isEmpty() && !"PET".equals(id)) return id;
        }

        return null;
    }

    /**
     * Returns the {@code ExtraAttributes} compound from a stack, or {@code null} if absent.
     * Hypixel stores item metadata (including {@code id}, {@code uuid}, {@code timestamp})
     * under this key.
     */
    @Nullable
    private static CompoundTag getExtraAttributes(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        return ((CustomDataAccessor) (Object) data).getRawTag().getCompound("ExtraAttributes").orElse(null);
    }
}
