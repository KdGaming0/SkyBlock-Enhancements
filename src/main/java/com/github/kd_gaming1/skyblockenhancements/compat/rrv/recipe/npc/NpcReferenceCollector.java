package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.npc;

import com.github.kd_gaming1.skyblockenhancements.repo.item.ItemStackBuilder;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItem;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItemRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;

/**
 * Shared logic for collecting NPC {@link ItemStack} references used as craft-reference
 * sidebars in RRV recipe views.
 *
 * <p>Both {@link SkyblockNpcInfoRecipeType} and {@link SkyblockNpcShopRecipeType} iterate
 * the full NEU registry looking for {@code _NPC} items; only the filter predicate differs.
 */
final class NpcReferenceCollector {

    private NpcReferenceCollector() {}

    /**
     * Collects all NPC items matching {@code filter} into {@link ItemStack} prototypes.
     *
     * @param filter decides whether a given NPC item should be included
     * @return a new list on every call; callers are responsible for caching
     */
    static List<ItemStack> collect(Predicate<NeuItem> filter) {
        if (!NeuItemRegistry.isLoaded()) return List.of();

        List<ItemStack> npcs = new ArrayList<>();
        // Iterate directly over the backing collection to avoid the List.copyOf
        // snapshot that getAllValues() performs. This is safe because collect()
        // is only called during startup when no concurrent mutations occur.
        NeuItemRegistry.forEachValue(item -> {
            if (item.internalName != null && item.internalName.endsWith("_NPC") && filter.test(item)) {
                ItemStack stack = ItemStackBuilder.build(item);
                if (!stack.isEmpty()) npcs.add(stack);
            }
        });
        return npcs;
    }
}
