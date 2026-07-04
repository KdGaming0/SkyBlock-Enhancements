package com.github.kd_gaming1.skyblockenhancements.mixin.dropguard;

import com.github.kd_gaming1.skyblockenhancements.feature.dropguard.DropGuard;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Blocks the in-world drop key ({@code Q}) from throwing a high-rarity item. This is the no-GUI path
 * ({@link LocalPlayer#drop(boolean)}), which bypasses {@code slotClicked}; the GUI drop paths are
 * handled by {@code ContainerDropGuardMixin}. Cancelling at HEAD suppresses both the local prediction
 * and the packet send, exactly like the slot-lock drop block.
 */
@Mixin(LocalPlayer.class)
public class PlayerItemDropGuardMixin {

    @Inject(method = "drop(Z)Z", at = @At("HEAD"), cancellable = true)
    private void sbe$guardRareDrop(boolean all, CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player) (Object) this;
        ItemStack held = self.getMainHandItem();
        if (DropGuard.shouldBlockDrop(held, "q")) {
            cir.setReturnValue(false);
        }
    }
}
