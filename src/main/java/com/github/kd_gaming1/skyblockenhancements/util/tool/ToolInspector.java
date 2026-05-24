package com.github.kd_gaming1.skyblockenhancements.util.tool;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Optional;

/**
 * High-level façade that combines tool detection and stat extraction in one call.
 */
public final class ToolInspector {

    private ToolInspector() {}

    public static ToolInfo inspectHeld() {
        return HeldItemTracker.getToolInfo();
    }

    public static ToolInfo inspect(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ToolInfo.NONE;
        }

        ToolType type = ToolDetector.detect(stack);
        String displayName = stack.getHoverName().getString();

        // Extract NBT Metadata
        String skyblockId = "";
        String uuid = "";
        String reforge = "";
        int toolLevel = 0;

        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag root = customData.copyTag();

            skyblockId = root.getString("id").orElse("");
            uuid = root.getString("uuid").orElse("");
            reforge = root.getString("modifier").orElse("");
            toolLevel = root.getInt("levelable_lvl").orElse(0);
        }

        ToolStatExtractor.ParsedStats stats = ToolStatExtractor.extractAll(stack);

        return new ToolInfo(type, skyblockId, displayName, uuid, reforge, toolLevel, stats.values(), stats.mask());
    }

    public static int getHeldMiningSpeed() {
        return inspectHeld().getInt(ToolStat.MINING_SPEED, 0);
    }

    public static int getHeldBreakingPower() {
        return inspectHeld().getInt(ToolStat.BREAKING_POWER, 0);
    }

    public static int getHeldMiningFortune() {
        return inspectHeld().getInt(ToolStat.MINING_FORTUNE, 0);
    }

    private static String getSkyblockId(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return "";

        CompoundTag root = customData.copyTag();
        return root.getCompound("ExtraAttributes")
                .flatMap(extra -> extra.getString("id"))
                .orElse("");
    }
}