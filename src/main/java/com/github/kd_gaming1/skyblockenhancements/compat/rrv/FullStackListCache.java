package com.github.kd_gaming1.skyblockenhancements.compat.rrv;

import cc.cassian.rrv.api.recipe.ItemView;
import cc.cassian.rrv.common.recipe.ClientRecipeCache;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/**
 * Caches the full SkyBlock item list and pre-computed lowercased display names used by the
 * RRV item overlay, eliminating two per-keystroke costs:
 *
 * <ol>
 *   <li>{@code fullStackList()} no longer re-scans the entire item registry.</li>
 *   <li>{@code stack.getDisplayName().getString().toLowerCase()} no longer creates
 *       bracketed/hover-styled {@code MutableText} objects, visits the component tree,
 *       and lowercases the result — all for 8 000+ items on every keystroke.</li>
 * </ol>
 *
 * <p>Both caches are invalidated together on RRV client reload or when the NEU repo changes.
 */
public final class FullStackListCache {

    /** Immutable cached item list. {@code null} until first build. */
    private static volatile List<ItemStack> cachedList;

    /**
     * Pre-computed lowercased display names keyed by {@link ItemStack} identity.
     */
    private static volatile Map<ItemStack, String> cachedNames;

    private FullStackListCache() {}

    /**
     * Returns the cached item list, building it if necessary.
     */
    public static List<ItemStack> getOrBuild() {
        List<ItemStack> snapshot = cachedList;
        if (snapshot != null) {
            return snapshot;
        }
        return buildCache();
    }

    /**
     * Returns the pre-computed lowercased display name for the given stack, or computes
     * it on the fly if the cache hasn't been built yet (shouldn't happen in practice since
     * {@code fullStackList()} is always called first).
     */
    public static String getLowercaseName(ItemStack stack) {
        Map<ItemStack, String> names = cachedNames;
        if (names != null) {
            String cached = names.get(stack);
            if (cached != null) {
                return cached;
            }
        }
        // Fallback: compute without caching (stack not in our list, or cache not yet built)
        return stack.getHoverName().getString().toLowerCase();
    }

    /** Clears both caches so the next access rebuilds from the registry. */
    public static void invalidate() {
        cachedList = null;
        cachedNames = null;
    }

    @SuppressWarnings("UnstableApiUsage")
    private static List<ItemStack> buildCache() {
        List<ItemStack> results = new ArrayList<>();
        BuiltInRegistries.ITEM.forEach(item -> {
            for (ItemView.StackSensitive sensitive :
                    ClientRecipeCache.INSTANCE.getStackSensitives(item)) {
                results.add(sensitive.stack());
            }
        });

        Map<ItemStack, String> names = new IdentityHashMap<>(results.size());
        for (ItemStack stack : results) {
            names.put(stack, stack.getHoverName().getString().toLowerCase());
        }

        List<ItemStack> immutable = Collections.unmodifiableList(results);
        cachedNames = names;
        cachedList = immutable;
        return immutable;
    }

    static {
        ItemView.addClientReloadCallback(FullStackListCache::invalidate);
    }
}