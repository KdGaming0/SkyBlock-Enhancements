package com.github.kd_gaming1.skyblockenhancements.feature.dropguard;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.util.HypixelLocationState;
import com.github.kd_gaming1.skyblockenhancements.util.ItemRarity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Optional;

/**
 * Blocks dropping high-rarity SkyBlock items so a valuable item can't be thrown away by accident.
 */
public final class DropGuard {

    private DropGuard() {}

    private static final long PRESS_WINDOW_MS = 1000L;
    private static final int REQUIRED_PRESSES = 3;

    // Triple-press tracking for the current item. Reset whenever the item key changes or the window
    // lapses, so the count only ever accumulates across rapid presses on one specific item.
    private static String activeKey = null;
    private static int pressCount = 0;
    private static long lastPressMs = 0L;

    /**
     * Decides whether a drop attempt should be cancelled.
     *
     * @param stack       the item being dropped
     * @param slotContext a stable per-origin tag (e.g. {@code "q"}, {@code "cursor"}, {@code "slot12"})
     *                    used as the triple-press key when the item carries no Hypixel UUID
     * @return {@code true} if the drop should be blocked, {@code false} to allow it
     */
    public static boolean shouldBlockDrop(ItemStack stack, String slotContext) {
        if (!SkyblockEnhancementsConfig.enableRarityDropGuard) return false;
        if (!HypixelLocationState.isOnSkyblock()) return false;
        if (stack == null || stack.isEmpty()) return false;

        Optional<ItemRarity> rarity = ItemRarity.fromItem(stack);
        if (rarity.isEmpty()) return false; // unknown rarity → never block

        ItemRarity min = SkyblockEnhancementsConfig.rarityDropGuardMinRarity;
        if (rarity.get().ordinal() < min.ordinal()) return false; // below threshold → allow

        if (!SkyblockEnhancementsConfig.rarityDropGuardTripleDrop) {
            feedback(rarity.get(), 0);
            return true; // hard block, no escape hatch
        }

        int remaining = registerPress(keyFor(stack, slotContext));
        if (remaining <= 0) {
            resetPresses();
            return false; // third rapid press → let it through
        }
        feedback(rarity.get(), remaining);
        return true;
    }

    /** Advances the triple-press counter for {@code key}; returns presses still required to drop. */
    private static int registerPress(String key) {
        long now = System.currentTimeMillis();
        if (key.equals(activeKey) && now - lastPressMs <= PRESS_WINDOW_MS) {
            pressCount++;
        } else {
            activeKey = key;
            pressCount = 1;
        }
        lastPressMs = now;
        return REQUIRED_PRESSES - pressCount;
    }

    private static void resetPresses() {
        activeKey = null;
        pressCount = 0;
        lastPressMs = 0L;
    }

    /**
     * A stable identity for triple-press tracking: the Hypixel item UUID when present (unique per
     * item), otherwise the origin context plus the item's registry id.
     */
    private static String keyFor(ItemStack stack, String slotContext) {
        String uuid = hypixelUuid(stack);
        if (uuid != null) return "uuid:" + uuid;
        return slotContext + ":" + BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    /** Reads {@code ExtraAttributes.uuid} from the item's custom data, or {@code null} if absent. */
    private static String hypixelUuid(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag extra = data.copyTag().getCompoundOrEmpty("ExtraAttributes");
        return extra.getString("uuid").orElse(null);
    }

    private static void feedback(ItemRarity rarity, int remaining) {
        Minecraft mc = Minecraft.getInstance();
        Component message = remaining > 0
                ? Component.translatable("skyblock_enhancements.dropguard.blocked_countdown",
                        rarity.displayName(), remaining)
                : Component.translatable("skyblock_enhancements.dropguard.blocked", rarity.displayName());
        mc.gui.setOverlayMessage(message, false);
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.DISPENSER_FAIL, 1.0f));
    }
}
