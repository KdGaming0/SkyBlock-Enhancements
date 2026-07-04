package com.github.kd_gaming1.skyblockenhancements.mixin.dropguard;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.feature.dropguard.DropGuard;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blocks the GUI drop paths for a high-rarity item, all of which reach {@code slotClicked}:
 * <ul>
 *   <li>the drop key ({@code Q}) pressed while hovering a slot — {@link ContainerInput#THROW}, the
 *       item is in {@code slot}. Gated by the main guard.</li>
 *   <li>dropping the carried item outside the window ({@code slotId == -999}) — clicking outside sends
 *       {@code THROW}, but dragging the item out and releasing sends {@link ContainerInput#PICKUP};
 *       both drop the cursor stack. Gated by the separate "block outside drop" toggle.</li>
 * </ul>
 * A {@code -999} action that is neither {@code PICKUP} nor {@code THROW} (e.g. {@code QUICK_CRAFT} drag
 * distribution) is not a drop and is left alone. Cancelling at HEAD is packet-safe: it returns before
 * {@code gameMode.handleContainerInput(...)}, so no container packet is sent.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerDropGuardMixin {

    @Shadow @Final protected AbstractContainerMenu menu;

    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void sbe$guardRareDrop(Slot slot, int slotId, int buttonNum, ContainerInput containerInput, CallbackInfo ci) {
        ItemStack stack;
        String context;
        if (slotId == -999) {
            if (containerInput != ContainerInput.PICKUP && containerInput != ContainerInput.THROW) {
                return; // not a drop (e.g. quick-craft distribution)
            }
            if (!SkyblockEnhancementsConfig.rarityDropGuardBlockOutsideDrop) {
                return;
            }
            stack = menu.getCarried();
            context = "cursor";
        } else if (containerInput == ContainerInput.THROW && slot != null) {
            stack = slot.getItem();
            context = "slot" + slotId;
        } else {
            return;
        }

        if (DropGuard.shouldBlockDrop(stack, context)) {
            ci.cancel();
        }
    }
}
