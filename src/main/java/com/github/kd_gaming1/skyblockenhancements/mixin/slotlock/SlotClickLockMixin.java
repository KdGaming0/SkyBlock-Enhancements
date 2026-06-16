package com.github.kd_gaming1.skyblockenhancements.mixin.slotlock;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.feature.slotlock.SlotLockManager;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Anti-cheat-safe slot locking: cancels {@code slotClicked} at HEAD, before
 * {@code gameMode.handleContainerInput(...)} runs, so no container packet is ever sent. The client
 * simply does not perform the move — it never sends an illegal or out-of-sync action.
 *
 * <p>Two move vectors are blocked:
 * <ul>
 *   <li>Any interaction whose target {@code slot} is a locked player-inventory slot
 *       (pickup, place, shift-click, drop, clone, swap-into).</li>
 *   <li>A number-key / offhand-swap ({@link ContainerInput#SWAP}) whose {@code buttonNum} points at
 *       a locked hotbar (0–8) or offhand (40) slot while hovering somewhere else — otherwise a
 *       hotkey would empty a locked slot the cursor isn't on.</li>
 * </ul>
 */
@Mixin(AbstractContainerScreen.class)
public class SlotClickLockMixin {

    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void sbe$blockLockedSlot(Slot slot, int slotId, int buttonNum, ContainerInput containerInput, CallbackInfo ci) {
        if (!SkyblockEnhancementsConfig.enableSlotLocking) {
            return;
        }

        // Direct interaction with a locked inventory slot.
        if (slot != null
                && slot.container instanceof Inventory
                && SlotLockManager.isLocked(slot.getContainerSlot())) {
            ci.cancel();
            return;
        }

        // Number-key / offhand swap that targets a locked hotbar or offhand slot.
        if (containerInput == ContainerInput.SWAP && SlotLockManager.isLocked(buttonNum)) {
            ci.cancel();
        }
    }
}
