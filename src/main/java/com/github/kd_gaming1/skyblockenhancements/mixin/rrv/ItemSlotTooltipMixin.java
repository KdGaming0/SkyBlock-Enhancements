package com.github.kd_gaming1.skyblockenhancements.mixin.rrv;

import cc.cassian.rrv.common.overlay.ItemSlot;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.RrvCompat;
import java.util.List;

import com.github.kd_gaming1.skyblockenhancements.feature.missingenchants.MissingEnchants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Caches the tooltip generated for the hovered {@link ItemSlot} to avoid invoking all registered
 * {@code ItemTooltipCallback} listeners (SkyHanni regex, Skyblocker networth codec, etc.) on every
 * render frame for the same unchanged item.
 *
 * <p>Additionally sets {@link RrvCompat#enterOverlayTooltip()} for the duration of every tooltip
 * build so that features like {@link MissingEnchants} can skip
 * work that is only meaningful for actual container-slot hovers.
 */
@Mixin(value = ItemSlot.class, remap = false)
public class ItemSlotTooltipMixin {

    @Unique private static ItemStack sbe$lastHoveredStack = null;
    @Unique private static List<Component> sbe$cachedTooltip = List.of();

    /**
     * Redirects the per-frame {@code Screen.getTooltipFromItem()} call inside {@code
     * ItemSlot.render()} to a single-entry identity cache. On a cache miss the tooltip is rebuilt
     * with the overlay-context flag set so downstream callbacks can opt out cleanly.
     */
    @Redirect(
            method = "render",
            at =
            @At(
                    value = "INVOKE",
                    target =
                            "Lnet/minecraft/client/gui/screens/Screen;getTooltipFromItem"
                                    + "(Lnet/minecraft/client/Minecraft;Lnet/minecraft/world/item/ItemStack;)"
                                    + "Ljava/util/List;"),
            remap = true)
    private List<Component> sbe$cacheHoveredTooltip(Minecraft minecraft, ItemStack itemStack) {
        if (!RrvCompat.isActive()) {
            return Screen.getTooltipFromItem(minecraft, itemStack);
        }
        if (itemStack != sbe$lastHoveredStack) {
            sbe$lastHoveredStack = itemStack;
            RrvCompat.enterOverlayTooltip();
            try {
                sbe$cachedTooltip = Screen.getTooltipFromItem(minecraft, itemStack);
            } finally {
                RrvCompat.exitOverlayTooltip();
            }
        }
        return sbe$cachedTooltip;
    }
}