package com.github.kd_gaming1.skyblockenhancements.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * General SkyBlock item utilities that do not depend on RRV.
 */
public final class SkyblockItemUtil {
    private SkyblockItemUtil() {}

    /**
     * Extracts the SkyBlock internal ID from a stack's {@code CUSTOM_DATA} component.
     *
     * @param stack the item stack to inspect
     * @return the {@code id} string from NBT, or {@code null} if absent or empty
     */
    @Nullable
    public static String extractSkyblockId(ItemStack stack) {
        if (stack.isEmpty()) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        String id = data.copyTag().getStringOr("id", "");
        return id.isEmpty() ? null : id;
    }

    /**
     * Returns the price-lookup ID for a stack.
     *
     * <p>For enchanted books this resolves the stored enchantment to the API key
     * (e.g. {@code ENCHANTMENT_ULTIMATE_FLOWSTATE_3}). For all other items it
     * falls back to {@link #extractSkyblockId}.
     */
    @Nullable
    public static String getPriceLookupId(ItemStack stack) {
        String baseId = extractSkyblockId(stack);
        if (!"ENCHANTED_BOOK".equals(baseId)) return baseId;

        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag tag = data.copyTag();

        // SkyBlock stores enchantments in tag.enchantments.{name} = level
        // getCompound() returns Optional<CompoundTag> in this MC version
        var enchantsOpt = tag.getCompound("enchantments");
        if (enchantsOpt.isEmpty()) return null;
        CompoundTag enchants = enchantsOpt.get();
        if (enchants.isEmpty()) return null;

        // Enchanted books have exactly one enchantment
        Set<String> keys = enchants.keySet();
        String enchantName = keys.iterator().next();
        int level = enchants.getIntOr(enchantName, 1);
        return "ENCHANTMENT_" + enchantName.toUpperCase() + "_" + level;
    }
}
