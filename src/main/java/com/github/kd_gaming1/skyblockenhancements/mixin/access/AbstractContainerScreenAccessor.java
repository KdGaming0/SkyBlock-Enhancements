package com.github.kd_gaming1.skyblockenhancements.mixin.access;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@link AbstractContainerScreen#hoveredSlot} so the slot-locking tick handler can read
 * which slot the cursor is over when the lock key is pressed.
 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {

    @Accessor("hoveredSlot")
    Slot skyblockenhancements$getHoveredSlot();
}
