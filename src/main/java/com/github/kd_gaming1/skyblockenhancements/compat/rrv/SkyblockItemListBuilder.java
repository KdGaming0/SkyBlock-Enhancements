package com.github.kd_gaming1.skyblockenhancements.compat.rrv;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.repo.*;
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
}