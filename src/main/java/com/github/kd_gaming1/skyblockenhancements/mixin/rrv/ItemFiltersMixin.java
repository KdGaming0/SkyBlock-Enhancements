package com.github.kd_gaming1.skyblockenhancements.mixin.rrv;

import cc.cassian.rrv.api.recipe.ItemView;
import cc.cassian.rrv.common.overlay.itemlist.view.ItemFilters;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.FullStackListCache;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

@Mixin(ItemFilters.class)
public class ItemFiltersMixin {

    // ── Tooltip cache ────────────────────────────────────────────────────────────

    @Unique
    private static final Map<ItemStack, List<Component>> sbe$tooltipCache =
            new IdentityHashMap<>(8192);

    @SuppressWarnings("MixinAnnotationTarget")
    @Redirect(
            method = "*",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/Screen;getTooltipFromItem"
                            + "(Lnet/minecraft/client/Minecraft;Lnet/minecraft/world/item/ItemStack;)"
                            + "Ljava/util/List;"),
            remap = true)
    private static List<Component> sbe$cachedTooltip(Minecraft mc, ItemStack stack) {
        // computeIfAbsent uses identity equality via IdentityHashMap
        return sbe$tooltipCache.computeIfAbsent(
                stack, s -> Screen.getTooltipFromItem(mc, s));
    }

    // ── Display name cache ───────────────────────────────────────────────────────

    @WrapOperation(
            method = "defaultFilter",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;getDisplayName()"
                            + "Lnet/minecraft/network/chat/Component;"),
            remap = true)
    private static Component sbe$cachedDisplayName(
            ItemStack stack, Operation<Component> original) {
        return Component.literal(FullStackListCache.getLowercaseName(stack));
    }

    // ── Query pre-lowercasing ────────────────────────────────────────────────────

    @SuppressWarnings("ModifyVariableMayUseName")
    @ModifyVariable(
            method = "defaultFilter",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            remap = false)
    private static String sbe$preLowercaseQuery(String query) {
        return query.toLowerCase();
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────────

    static {
        // Clear on RRV reload — stacks are replaced, old identity keys become stale
        ItemView.addClientReloadCallback(sbe$tooltipCache::clear);
    }
}