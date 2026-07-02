package com.github.kd_gaming1.skyblockenhancements.mixin.slotmanage;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.feature.slotmanage.LockedDropMode;
import com.github.kd_gaming1.skyblockenhancements.feature.slotmanage.SlotManager;
import com.github.kd_gaming1.skyblockenhancements.util.HypixelLocationState;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Extends slot locking to the in-world drop key ({@code Q}). Pressing it with no GUI open drops the
 * held hotbar item through {@link LocalPlayer#drop(boolean)} — a path that bypasses
 * {@code slotClicked}, so it is not covered by {@code SlotManageMixin}.
 */
@Mixin(LocalPlayer.class)
public class PlayerDropLockMixin {

    @Inject(method = "drop(Z)Z", at = @At("HEAD"), cancellable = true)
    private void sbe$blockLockedDrop(boolean all, CallbackInfoReturnable<Boolean> cir) {
        if (!SkyblockEnhancementsConfig.enableSlotLocking) {
            return;
        }
        Player self = (Player) (Object) this;
        if (SlotManager.isLocked(self.getInventory().getSelectedSlot()) && !sbe$dropAllowed()) {
            cir.setReturnValue(false);
        }
    }

    /** Whether the current drop mode permits dropping a locked item with the drop key. */
    private static boolean sbe$dropAllowed() {
        LockedDropMode mode = SkyblockEnhancementsConfig.dropLockedItemsWithQ;
        return switch (mode) {
            case ALWAYS -> true;
            case IN_DUNGEONS -> HypixelLocationState.isInDungeon();
            case NEVER -> false;
        };
    }
}
