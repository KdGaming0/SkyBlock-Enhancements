package com.github.kd_gaming1.skyblockenhancements.compat.rrv.injection;

import com.github.kd_gaming1.skyblockenhancements.compat.rrv.util.ItemFamilyHelper;
import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.repo.item.ItemStackBuilder;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuConstantsRegistry;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItem;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItemRegistry;
import com.github.kd_gaming1.skyblockenhancements.util.StringUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Builds the sorted SkyBlock item list from the {@link NeuItemRegistry} for injection
 * into RRV. Handles compact mode filtering and multi-key sorting.
 *
 * <p>Extracted from {@link SkyblockInjectionCache} to keep the cache class focused
 * on state management only.
 */
public final class SkyblockItemListBuilder {

    private SkyblockItemListBuilder() {}

    /**
     * Builds the sorted item list from the NEU registry. When compact mode is enabled,
     * child items from {@code parents.json} are excluded — their recipes remain accessible
     * through the parent item.
     *
     * @return sorted, non-empty item list ready for RRV injection
     */
    public static List<ItemStack> build() {
        boolean compact = SkyblockEnhancementsConfig.compactItemList;

        // Pair stacks with their NeuItem and pre-computed sort key for zero-allocation sorting.
        record StackWithMeta(ItemStack stack, NeuItem neuItem, SortKey sortKey) {}

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
                SortKey key = new SortKey(neuItem);
                candidates.add(new StackWithMeta(stack, neuItem, key));
            }
        }

        // Sort using pre-computed keys — zero allocations during comparison.
        candidates.sort(Comparator.comparing(StackWithMeta::sortKey));

        List<ItemStack> items = new ArrayList<>(candidates.size());
        for (StackWithMeta s : candidates) {
            items.add(s.stack());
        }
        return items;
    }

    // ── Sort key ────────────────────────────────────────────────────────────────

    /**
     * Immutable, pre-computed sort key for a {@link NeuItem}. All expensive string
     * operations (substring, color-code stripping) happen once at construction time,
     * so the comparator performs only zero-allocation field comparisons.
     */
    private record SortKey(
            String familyPrefix,
            int rarityOrdinal,
            String cleanDisplayName,
            String internalName
    ) implements Comparable<SortKey> {

        SortKey(NeuItem item) {
            this(
                    familyPrefixOf(item.internalName),
                    item.rarity != null ? item.rarity.ordinal() : Integer.MAX_VALUE,
                    StringUtil.stripColorCodes(item.displayName),
                    item.internalName != null ? item.internalName : ""
            );
        }

        @Override
        public int compareTo(SortKey other) {
            int c = this.familyPrefix.compareTo(other.familyPrefix);
            if (c != 0) return c;

            c = Integer.compare(this.rarityOrdinal, other.rarityOrdinal);
            if (c != 0) return c;

            c = this.cleanDisplayName.compareTo(other.cleanDisplayName);
            if (c != 0) return c;

            return this.internalName.compareTo(other.internalName);
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    /** Returns the substring before the first {@code ';'}, or {@code ""} if absent. */
    private static String familyPrefixOf(String internalName) {
        if (internalName == null) return "";
        int semi = internalName.indexOf(';');
        return semi >= 0 ? internalName.substring(0, semi) : "";
    }


}
