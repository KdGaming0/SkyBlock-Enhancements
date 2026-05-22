package com.github.kd_gaming1.skyblockenhancements.mixin.rrv.itemlist;

import cc.cassian.rrv.api.recipe.ItemView;
import cc.cassian.rrv.common.overlay.itemlist.view.ItemFilters;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.RrvCompat;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.injection.FullStackListCache;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.search.SkyblockSearchFilter;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.search.SkyblockSearchIndex;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

@Mixin(ItemFilters.class)
public class ItemFiltersMixin {

    // ── Tooltip cache ────────────────────────────────────────────────────────────

    @Unique
    private static final Map<ItemStack, List<Component>> sbe$tooltipCache =
            new IdentityHashMap<>(8192);

    /**
     * Caches tooltip lookups performed by {@code ItemFilters.getTooltipMatch()}.
     * Targeted explicitly at that method rather than a wildcard so the mixin stays
     * stable if RRV refactors other methods in this class.
     */
    @Redirect(
            method = "getTooltipMatch",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/Screen;getTooltipFromItem"
                            + "(Lnet/minecraft/client/Minecraft;Lnet/minecraft/world/item/ItemStack;)"
                            + "Ljava/util/List;")
    )
    private static List<Component> sbe$cachedTooltip(Minecraft mc, ItemStack stack) {
        List<Component> cached = sbe$tooltipCache.get(stack);
        if (cached != null) return cached;
        List<Component> built;
        try {
            built = Screen.getTooltipFromItem(mc, stack);
        } catch (Exception e) {
            SkyblockEnhancements.LOGGER.warn(
                    "Tooltip generation failed for '{}' during RRV search (another mod threw during ItemTooltipCallback).",
                    stack.getHoverName().getString(), e);
            built = List.of();
        }
        sbe$tooltipCache.put(stack, built);
        return built;
    }

    // ── Display name cache ───────────────────────────────────────────────────────

    @WrapOperation(
            method = "defaultFilter",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;getDisplayName()"
                            + "Lnet/minecraft/network/chat/Component;")
    )
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

    // ── SkyBlock search filter ───────────────────────────────────────────────────

    @Inject(method = "defaultFilter", at = @At("HEAD"), cancellable = true, remap = false)
    private static void sbe$skyblockSearchFilter(String query, CallbackInfoReturnable<List<ItemStack>> cir) {
        if (!RrvCompat.isActive()) return;

        SkyblockSearchIndex index = FullStackListCache.getSearchIndex();
        if (index == null) return;

        cir.setReturnValue(SkyblockSearchFilter.filter(query, index));
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────────

    static {
        // Clear on RRV reload — stacks are replaced, old identity keys become stale
        ItemView.addClientReloadCallback(sbe$tooltipCache::clear);
    }
}