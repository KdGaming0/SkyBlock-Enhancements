package com.github.kd_gaming1.skyblockenhancements.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

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
}