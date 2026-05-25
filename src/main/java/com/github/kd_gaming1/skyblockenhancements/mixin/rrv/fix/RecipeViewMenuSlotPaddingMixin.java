package com.github.kd_gaming1.skyblockenhancements.mixin.rrv.fix;

import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.safety.SbeSafeDummySlot;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pads {@link RecipeViewMenu}'s {@code slots} list with inactive dummy slots after
 * every {@code updateByPage()} so that third-party mods doing hardcoded direct access
 * (e.g. {@code menu.slots.get(29)}) do not crash with {@code IndexOutOfBoundsException}.
 *
 * <p>RRV calls {@code slots.clear()} inside {@code updateByPage()} and rebuilds the
 * list from scratch. During that window — and for recipes that simply don't need many
 * slots — the list can shrink to as few as 1 entry. Many SkyBlock mods probe fixed
 * indices assuming a chest-like layout. This mixin adds harmless off-screen dummy slots
 * until the list reaches a safe minimum size.
 *
 * <p>The dummy slots are skipped by vanilla rendering ({@code isActive() == false}),
 * ignored by RRV's recipe binding (which uses per-type slot counts), and reject all
 * item placement, so they have no functional impact on gameplay.
 */
@Mixin(RecipeViewMenu.class)
public class RecipeViewMenuSlotPaddingMixin {

    @Unique
    private static final int SBE$MINIMUM_SLOT_COUNT = 54;

    @Inject(method = "updateByPage", at = @At("RETURN"))
    private void sbe$padSlots(CallbackInfo ci) {
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        int currentSize = menu.slots.size();

        for (int i = currentSize; i < SBE$MINIMUM_SLOT_COUNT; i++) {
            SbeSafeDummySlot dummy = new SbeSafeDummySlot();
            dummy.index = i;
            menu.slots.add(dummy);
        }
    }
}
