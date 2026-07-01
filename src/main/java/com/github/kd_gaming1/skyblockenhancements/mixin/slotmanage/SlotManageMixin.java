package com.github.kd_gaming1.skyblockenhancements.mixin.slotmanage;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.feature.slotmanage.SlotManager;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Owns the click-time behaviour of slot management on {@link AbstractContainerScreen}:
 *
 * <ul>
 *   <li><b>{@code mouseClicked} @HEAD</b> — while the edit key is held, swallows clicks and routes
 *       right-click to a lock toggle and left-click to the bind state machine (binding only in the
 *       player inventory). Outside edit mode, a shift+left-click on a bound inventory slot performs
 *       exactly one vanilla {@link ContainerInput#SWAP} — byte-for-byte what pressing the number key
 *       would send.</li>
 *   <li><b>{@code slotClicked} @HEAD</b> — cancels before {@code gameMode.handleContainerInput(...)}
 *       runs, so locked slots never emit a container packet. Also blocks a SWAP whose {@code buttonNum}
 *       targets a locked hotbar/offhand slot.</li>
 * </ul>
 */
@Mixin(AbstractContainerScreen.class)
public abstract class SlotManageMixin {

    @Shadow @Final protected AbstractContainerMenu menu;

    @Shadow
    private Slot getHoveredSlot(double x, double y) {
        return null; // replaced by the shadowed target method
    }

    @Shadow
    protected abstract void slotClicked(Slot slot, int slotId, int buttonNum, ContainerInput containerInput);

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void sbe$onMouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        boolean lockEnabled = SkyblockEnhancementsConfig.enableSlotLocking;
        boolean bindEnabled = SkyblockEnhancementsConfig.enableSlotBinding;
        if (!lockEnabled && !bindEnabled) {
            return;
        }

        boolean inInventory = (Object) this instanceof InventoryScreen;

        // Bind mode: the held key swallows clicks. Left-click drives the bind state machine;
        // right-click unlinks a bound slot. (Locking is a quick tap of the key, handled in the tick.)
        if (SlotManager.isEditKeyDown()) {
            SlotManager.notifyEditModeClick(); // any click here means the release isn't a lock tap
            if (inInventory && bindEnabled) {
                Slot slot = getHoveredSlot(event.x(), event.y());
                if (slot != null) {
                    if (event.button() == 0) {
                        SlotManager.handleBindClick(slot);
                        if (slot.container instanceof Inventory) {
                            SlotManager.beginDrag(slot.getContainerSlot());
                        }
                    } else if (event.button() == 1) {
                        SlotManager.unbindSlot(slot);
                    }
                }
            }
            cir.setReturnValue(true);
            return;
        }

        // Normal mode: shift+click on a bound inventory slot. Left = swap, right = unlink.
        if (!inInventory || !bindEnabled || !event.hasShiftDown()) {
            return;
        }
        Slot slot = getHoveredSlot(event.x(), event.y());
        if (slot == null || !(slot.container instanceof Inventory)) {
            return;
        }
        if (event.button() == 0) {
            if (!menu.getCarried().isEmpty()) {
                return;
            }
            int containerSlot = slot.getContainerSlot();
            Integer hotbarButton = SlotManager.getBinding(containerSlot);
            Slot source = slot;
            if (hotbarButton == null) {
                Integer sourceSlot = SlotManager.getSourceBoundToHotbar(containerSlot);
                if (sourceSlot != null) {
                    source = sbe$findInventorySlot(sourceSlot);
                    hotbarButton = containerSlot;
                }
            }
            if (hotbarButton == null || source == null) {
                return;
            }
            slotClicked(source, source.index, hotbarButton, ContainerInput.SWAP);
            cir.setReturnValue(true);
        } else if (event.button() == 1) {
            if (SlotManager.unbindSlot(slot)) {
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void sbe$onMouseReleased(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        boolean lockEnabled = SkyblockEnhancementsConfig.enableSlotLocking;
        boolean bindEnabled = SkyblockEnhancementsConfig.enableSlotBinding;
        if (!lockEnabled && !bindEnabled) {
            return;
        }
        if (!SlotManager.isEditKeyDown()) {
            return;
        }
        // A left-release over the opposite side of where the press started completes a drag-to-bind.
        if (bindEnabled && (Object) this instanceof InventoryScreen && event.button() == 0) {
            SlotManager.handleBindDragRelease(getHoveredSlot(event.x(), event.y()));
        }
        cir.setReturnValue(true); // press was swallowed in edit mode; swallow the matching release too
    }

    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void sbe$blockLockedSlot(Slot slot, int slotId, int buttonNum, ContainerInput containerInput, CallbackInfo ci) {
        if (!SkyblockEnhancementsConfig.enableSlotLocking) {
            return;
        }
        if (slot != null && slot.container instanceof Inventory && SlotManager.isLocked(slot.getContainerSlot())) {
            ci.cancel();
            return;
        }
        if (containerInput == ContainerInput.SWAP && SlotManager.isLocked(buttonNum)) {
            ci.cancel();
        }
    }

    /** Resolves the live {@link Slot} for a player-inventory {@code getContainerSlot()} index (0–40). */
    @Unique
    private Slot sbe$findInventorySlot(int containerSlot) {
        for (Slot s : menu.slots) {
            if (s.container instanceof Inventory && s.getContainerSlot() == containerSlot) {
                return s;
            }
        }
        return null;
    }
}
