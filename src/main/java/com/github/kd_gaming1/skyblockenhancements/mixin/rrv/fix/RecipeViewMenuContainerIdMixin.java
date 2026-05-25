package com.github.kd_gaming1.skyblockenhancements.mixin.rrv.fix;

import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Isolates RRV's {@link RecipeViewMenu} from server container packets by assigning
 * a unique negative {@code containerId} during construction.
 *
 * <p>RRV normally reuses the parent screen's {@code containerId} (or {@code 0} for
 * the inventory). This causes server {@code ClientboundContainerSetContentPacket}
 * and {@code ClientboundContainerSetSlotPacket} packets to be routed to
 * {@code RecipeViewMenu}, triggering {@code initializeContents} → {@code Slot.set()}
 * → third-party mod event cascades that crash when they assume a stable slot layout.
 *
 * <p>Servers never emit negative container IDs, so no server packet will ever target
 * a {@code RecipeViewMenu}. The parent's real menu continues to receive its own
 * packets normally.
 */
@Mixin(RecipeViewMenu.class)
public class RecipeViewMenuContainerIdMixin {

    @Unique
    private static final AtomicInteger SBE$ISOLATED_ID = new AtomicInteger(-1);

    @ModifyArg(
            method = "<init>(Lnet/minecraft/client/gui/screens/Screen;ILnet/minecraft/world/entity/player/Inventory;Ljava/util/List;Lnet/minecraft/world/item/ItemStack;Lcc/cassian/rrv/api/ActionType;Ljava/util/ArrayList;Lcc/cassian/rrv/api/recipe/ReliableClientRecipeType;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;<init>(Lnet/minecraft/world/inventory/MenuType;I)V"
            ),
            index = 1
    )
    private static int sbe$isolateContainerId(int containerId) {
        return SBE$ISOLATED_ID.getAndDecrement();
    }
}
