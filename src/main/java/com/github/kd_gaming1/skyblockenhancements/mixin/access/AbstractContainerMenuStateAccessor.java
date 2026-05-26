package com.github.kd_gaming1.skyblockenhancements.mixin.access;

import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@link AbstractContainerMenu#stateId} for cache invalidation.
 *
 * <p>The field increments whenever the server updates slot contents, making it
 * an ideal, allocation-free signal for recomputing derived slot state.
 */
@Mixin(AbstractContainerMenu.class)
public interface AbstractContainerMenuStateAccessor {

    @Accessor("stateId")
    int skyblockenhancements$getStateId();
}
