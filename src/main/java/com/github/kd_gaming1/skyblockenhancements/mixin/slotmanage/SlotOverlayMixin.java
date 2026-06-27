package com.github.kd_gaming1.skyblockenhancements.mixin.slotmanage;

import com.github.kd_gaming1.skyblockenhancements.feature.slotmanage.SlotManager;
import com.github.kd_gaming1.skyblockenhancements.feature.slotmanage.SlotOverlayRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the slot-management overlay across three injection points:
 *
 * <ul>
 *   <li>{@code extractSlot} just <b>before</b> {@code itemDecorations} — the bind outlines and the
 *       pending-source highlight, so they sit above the item but <b>below</b> the stack-count text.</li>
 *   <li>{@code extractSlot} TAIL — the padlock sprite (every container).</li>
 *   <li>{@code extractContents} TAIL — the connecting lines (player inventory only), and a per-frame
 *       stash of the hovered slot used by the tap-to-lock handler.</li>
 * </ul>
 */
@Mixin(AbstractContainerScreen.class)
public abstract class SlotOverlayMixin {

    @Shadow @Final protected AbstractContainerMenu menu;
    @Shadow protected int leftPos;
    @Shadow protected int topPos;
    @Shadow protected Slot hoveredSlot;

    @Inject(
            method = "extractSlot",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
                    shift = At.Shift.BEFORE))
    private void sbe$renderBindOutline(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if ((Object) this instanceof InventoryScreen) {
            SlotOverlayRenderer.renderBindOutline(graphics, slot);
        }
    }

    @Inject(method = "extractSlot", at = @At("TAIL"))
    private void sbe$renderLock(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        SlotOverlayRenderer.renderLock(graphics, slot);
    }

    @Inject(method = "extractContents", at = @At("TAIL"))
    private void sbe$renderLines(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        SlotManager.setHoveredSlot(this.hoveredSlot);
        if ((Object) this instanceof InventoryScreen) {
            SlotOverlayRenderer.renderLines(graphics, leftPos, topPos, menu.slots);
        }
    }
}
