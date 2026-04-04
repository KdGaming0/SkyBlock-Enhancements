package com.github.kd_gaming1.skyblockenhancements.compat.rrv;

import com.github.kd_gaming1.skyblockenhancements.repo.NeuItem;
import com.github.kd_gaming1.skyblockenhancements.repo.NeuItemRegistry;
import com.github.kd_gaming1.skyblockenhancements.repo.SkyblockItemCategory;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Tests whether an {@link ItemStack} in the RRV item list belongs to a given
 * {@link SkyblockItemCategory}. Resolves the stack back to its {@link NeuItem} via a
 * display-name index built lazily from the registry.
 *
 * <p>Category resolution is cached on each {@link NeuItem} as a transient field, so repeated
 * lookups for the same item are constant-time after the first pass.
 */
public final class SkyblockCategoryFilter {

    /**
     * Display-name → NeuItem index for O(1) lookups. Rebuilt lazily when the registry changes.
     * Guarded by the class monitor for thread safety during rebuilds.
     */
    private static volatile Map<String, NeuItem> displayNameIndex;

    /** Tracks the registry size at the time the index was built, to detect reloads. */
    private static int indexedSize;

    private SkyblockCategoryFilter() {}

    /**
     * Returns {@code true} if the given stack's underlying {@link NeuItem} belongs to the
     * specified category. Returns {@code false} for stacks that can't be resolved to a NeuItem
     * or whose NeuItem has no assigned category.
     */
    public static boolean matches(ItemStack stack, SkyblockItemCategory target) {
        if (stack.isEmpty() || target == null) return false;

        NeuItem item = resolveNeuItem(stack);
        if (item == null) return false;

        if (item.category == null) {
            item.category = SkyblockItemCategory.fromNeuItem(item);
        }
        return item.category == target;
    }

    /** Drops the display-name index so it's rebuilt on the next filter pass. */
    public static void invalidateIndex() {
        displayNameIndex = null;
    }

    /**
     * Resolves a display-ready {@link ItemStack} back to its {@link NeuItem} source via the
     * display-name index. Returns {@code null} if the stack has no custom name or no match.
     */
    private static NeuItem resolveNeuItem(ItemStack stack) {
        Component customName = stack.get(DataComponents.CUSTOM_NAME);
        if (customName == null) return null;

        return ensureIndex().get(customName.getString());
    }

    private static Map<String, NeuItem> ensureIndex() {
        Map<String, NeuItem> idx = displayNameIndex;
        int registrySize = NeuItemRegistry.getAll().size();

        if (idx != null && registrySize == indexedSize) return idx;

        synchronized (SkyblockCategoryFilter.class) {
            // Double-check after acquiring lock
            if (displayNameIndex != null && registrySize == indexedSize) {
                return displayNameIndex;
            }

            Map<String, NeuItem> newIndex = new HashMap<>(registrySize);
            for (NeuItem item : NeuItemRegistry.getAll().values()) {
                if (item.displayName != null) {
                    newIndex.put(item.displayName, item);
                }
            }
            indexedSize = registrySize;
            displayNameIndex = newIndex;
            return newIndex;
        }
    }
}