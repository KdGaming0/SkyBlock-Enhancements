package com.github.kd_gaming1.skyblockenhancements.feature.slotlock;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Draws a small padlock sprite in the corner of locked player-inventory slots.
 *
 * <p>Called from {@code SlotLockOverlayMixin} at the tail of {@code extractSlot}, so the icon
 * renders on top of the item without disturbing vanilla slot rendering.
 */
public final class SlotLockRenderer {

    private SlotLockRenderer() {}

    /** Loaded from {@code assets/skyblock_enhancements/textures/gui/sprites/slot/locked.png}. */
    private static final Identifier LOCK_SPRITE =
            Identifier.fromNamespaceAndPath("skyblock_enhancements", "slot/locked");

    public static void render(GuiGraphicsExtractor graphics, Slot slot) {
        if (!SkyblockEnhancementsConfig.enableSlotLocking) {
            return;
        }
        if (!(slot.container instanceof Inventory)) {
            return;
        }
        if (!SlotLockManager.isLocked(slot.getContainerSlot())) {
            return;
        }
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, LOCK_SPRITE, slot.x, slot.y, 16, 16);
    }
}
