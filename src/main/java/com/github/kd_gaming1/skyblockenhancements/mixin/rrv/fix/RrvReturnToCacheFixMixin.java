package com.github.kd_gaming1.skyblockenhancements.mixin.rrv.fix;

import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;

/**
 * Prevents an ArrayIndexOutOfBoundsException when scrolling between pages of different sizes.
 * RRV shrinks the slot allocations for a smaller page but sometimes tries to process old,
 * larger coordinates in returnToCache(), causing an out-of-bounds crash.
 */
@Mixin(value = RecipeViewMenu.class, remap = false)
public class RrvReturnToCacheFixMixin {

    @Inject(method = "returnToCache", at = @At("HEAD"), cancellable = true)
    private void sbe$safelyReturnToCache(HashMap<Integer, ItemStack> usedPlayerSlots, NonNullList<ItemStack> stackSupply, CallbackInfo ci) {
        usedPlayerSlots.forEach((playerSlot, stack) -> {
            if (playerSlot >= 0 && playerSlot < stackSupply.size()) {
                if (stackSupply.get(playerSlot).isEmpty()) {
                    stackSupply.set(playerSlot, stack);
                } else {
                    stackSupply.get(playerSlot).setCount(stackSupply.get(playerSlot).getCount() + stack.getCount());
                }
            }
        });
        ci.cancel();
    }
}
