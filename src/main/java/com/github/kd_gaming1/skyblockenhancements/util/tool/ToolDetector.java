package com.github.kd_gaming1.skyblockenhancements.util.tool;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Locale;
import java.util.Optional;

/**
 * Detects the {@link ToolType} of the item the player is currently holding.
 *
 * <p>Detection is two-tier:
 * <ol>
 *   <li><b>SkyBlock ID pattern matching</b> — reads {@code ExtraAttributes.id}
 *       from the item's {@link DataComponents#CUSTOM_DATA} and matches the
 *       ID against each {@link ToolType}'s ID fragments (e.g. "TITANIUM_PICKAXE"
 *       contains "PICKAXE" → {@link ToolType#PICKAXE}). No hardcoded lists —
 *       this works for any current or future SkyBlock item automatically.</li>
 *   <li><b>Display name heuristic</b> — if the ID is unavailable (non-SkyBlock
 *       item, custom item), falls back to matching the display name against
 *       {@link ToolType}'s name fragments.</li>
 * </ol>
 *
 * <p>All methods are safe to call from the client thread and return quickly.
 */
public final class ToolDetector {

    private ToolDetector() {}

    // ═══════════════════════════════════════════════════════════════════════════
    //  Public API
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Detects the tool type of the item currently held in the main hand.
     *
     * @return the detected tool type, or {@link ToolType#UNKNOWN} if the held
     *         item is not a tool (or the player has nothing equipped)
     */
    public static ToolType detectHeldTool() {
        ItemStack held = getHeldItem();
        if (held.isEmpty()) return ToolType.UNKNOWN;
        return detect(held);
    }

    /**
     * Detects the tool type of an arbitrary item stack.
     *
     * @return the detected tool type, or {@link ToolType#UNKNOWN}
     */
    public static ToolType detect(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return ToolType.UNKNOWN;

        // Tier 1: SkyBlock ExtraAttributes.id pattern matching
        String skyblockId = getSkyblockId(stack);
        if (skyblockId != null && !skyblockId.isEmpty()) {
            String upperId = skyblockId.toUpperCase(Locale.ROOT);
            for (ToolType type : ToolType.knownValues()) {
                if (type.matchesId(upperId)) return type;
            }
        }

        // Tier 2: display name heuristic fallback
        String displayName = stack.getHoverName().getString();
        for (ToolType type : ToolType.knownValues()) {
            if (type.matchesName(displayName)) return type;
        }

        return ToolType.UNKNOWN;
    }

    /** Returns {@code true} if the player is holding any known tool. */
    public static boolean isHoldingTool() {
        return detectHeldTool() != ToolType.UNKNOWN;
    }

    /** Returns {@code true} if the player is holding a tool of the given type. */
    public static boolean isHolding(ToolType expected) {
        return detectHeldTool() == expected;
    }

    /** Returns {@code true} if the player is holding a mining-related tool. */
    public static boolean isHoldingMiningTool() {
        ToolType t = detectHeldTool();
        return t == ToolType.PICKAXE
                || t == ToolType.DRILL
                || t == ToolType.GAUNTLET
                || t == ToolType.CHISEL;
    }

    /** Returns {@code true} if the player is holding a farming-related tool. */
    public static boolean isHoldingFarmingTool() {
        ToolType t = detectHeldTool();
        return t == ToolType.HOE
                || t == ToolType.FARMING_AXE
                || t == ToolType.WATERING_CAN;
    }

    /**
     * Returns the SkyBlock ID of the held item, or empty if unavailable.
     * This is the raw {@code ExtraAttributes.id} value.
     */
    public static Optional<String> getHeldSkyblockId() {
        ItemStack held = getHeldItem();
        if (held.isEmpty()) return Optional.empty();
        String id = getSkyblockId(held);
        return id != null && !id.isEmpty() ? Optional.of(id) : Optional.empty();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Internal helpers — 1.21+ Data Components API
    // ═══════════════════════════════════════════════════════════════════════════

    /** Returns the item in the player's main hand, or {@link ItemStack#EMPTY}. */
    private static ItemStack getHeldItem() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return ItemStack.EMPTY;
        return mc.player.getMainHandItem();
    }

    static String getSkyblockId(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return null;

        CompoundTag root = customData.copyTag();

        return root.getString("id").orElse(null);
    }
}
