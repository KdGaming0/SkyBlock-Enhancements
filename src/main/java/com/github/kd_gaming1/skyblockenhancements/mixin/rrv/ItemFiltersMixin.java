package com.github.kd_gaming1.skyblockenhancements.mixin.rrv;

import cc.cassian.rrv.api.recipe.ItemView;
import cc.cassian.rrv.common.overlay.itemlist.view.ItemFilters;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.FullStackListCache;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Performance optimisations for {@code ItemFilters}:
 *
 * <ol>
 *   <li><b>Tooltip cache</b> — redirects {@link Screen#getTooltipFromItem} to a
 *       {@link ConcurrentHashMap} so tooltip callbacks (SkyHanni, Skyblocker, etc.)
 *       aren't re-fired for every enchanted book on every keystroke.</li>
 *   <li><b>Display name cache</b> — redirects {@code stack.getDisplayName()} (which is
 *       {@code toHoverableText()} — bracket-wrapping, hover events, rarity styling,
 *       {@code ItemStack.copy()}) to return a lightweight component whose
 *       {@code getString()} returns the pre-cached lowercased name directly.</li>
 *   <li><b>Query pre-lowercasing</b> — lowercases the query once before the loop instead
 *       of three times per item.</li>
 * </ol>
 */
@Mixin(ItemFilters.class)
public class ItemFiltersMixin {

    // ── Tooltip cache ────────────────────────────────────────────────────────────

    @Unique
    private static final Map<ItemStack, List<Component>> sbe$tooltipCache =
            new ConcurrentHashMap<>(8192, 0.75f, 1);

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
        return sbe$tooltipCache.computeIfAbsent(
                stack, s -> Screen.getTooltipFromItem(mc, s));
    }

    // ── Display name cache ───────────────────────────────────────────────────────

    /**
     * Redirects the {@code stack.getDisplayName()} call inside {@code defaultFilter} to
     * return a pre-cached component that avoids the expensive {@code toHoverableText()} path.
     */
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
        ItemView.addClientReloadCallback(sbe$tooltipCache::clear);
    }
}