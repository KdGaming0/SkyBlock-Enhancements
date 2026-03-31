package com.github.kd_gaming1.skyblockenhancements.mixin;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.util.HypixelLocationState;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents Skyblock swords (e.g. Spirit Sceptre, which is a Flower) from being placed as
 * blocks. Returning {@link InteractionResult#PASS} skips the block placement while still allowing
 * the game to fall through to {@code Item.use}, which sends the right-click packet to the server
 * and triggers the weapon's ability.
 */
@Mixin(BlockItem.class)
public class PreventWeaponPlacementMixin {

    @Unique
    private static final Pattern SWORD_TYPE_LINE =
            Pattern.compile(
                    "(?:COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE|VERY SPECIAL|SPECIAL)"
                            + "\\s+(?:DUNGEON\\s+)?SWORD\\b");

    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void sbe$preventWeaponPlacement(
            UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (!SkyblockEnhancementsConfig.preventWeaponPlacement) return;
        if (!HypixelLocationState.isOnSkyblock()) return;

        if (sbe$isSkyblockSword(context.getItemInHand())) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }

    @Unique
    private static boolean sbe$isSkyblockSword(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return false;

        List<Component> lines = lore.lines();
        for (int i = lines.size() - 1; i >= 0; i--) {
            String lineText = lines.get(i).getString();
            if (lineText.contains("SWORD") && SWORD_TYPE_LINE.matcher(lineText).find()) {
                return true;
            }
        }
        return false;
    }
}