package com.github.kd_gaming1.skyblockenhancements.mixin.rrv;

import cc.cassian.rrv.api.ActionType;
import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.overlay.itemlist.view.ItemViewOverlay;
import cc.cassian.rrv.common.recipe.ClientRecipeCache;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.SkyblockRecipeUtil;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.npc.SkyblockNpcInfoRecipeType;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.npc.SkyblockNpcShopRecipeType;
import com.github.kd_gaming1.skyblockenhancements.repo.NeuItem;
import com.github.kd_gaming1.skyblockenhancements.repo.NeuItemRegistry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts recipe-view lookups to handle two scenarios:
 *
 * <ol>
 *   <li><b>NPC items</b> — detected by {@code CUSTOM_NAME} via the NPC display-name index.
 *       Opens with the correct tab pre-selected: shop for shop NPCs, info otherwise.</li>
 *   <li><b>Non-NPC RESULT misses</b> — when RESULT finds nothing, retries with INPUT so
 *       craft-reference items still work.</li>
 *   <li><b>Family item page-seek</b> — when a family item is clicked, advances to the
 *       page whose recipe result matches the clicked item's exact Skyblock ID.</li>
 * </ol>
 */
@Mixin(ItemViewOverlay.class)
public class RecipeViewFallbackMixin {

    @Inject(
            method = "openRecipeView(Lnet/minecraft/world/item/ItemStack;Lcc/cassian/rrv/api/ActionType;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    @SuppressWarnings("UnstableApiUsage")
    private void sbe$interceptForNpcAndFallback(
            ItemStack stack, ActionType openType, CallbackInfo ci) {
        if (stack.isEmpty()) return;

        // ── NPC handling (runs for any ActionType) ──────────────────────────────────
        NeuItem npcItem = sbe$findNpcItem(stack);
        if (npcItem != null) {
            List<ReliableClientRecipe> inputRecipes =
                    ClientRecipeCache.INSTANCE.getRecipesForCraftingInput(stack);
            if (!inputRecipes.isEmpty()) {
                ReliableClientRecipeType preferredTab = npcItem.hasNpcShopRecipes()
                        ? SkyblockNpcShopRecipeType.INSTANCE
                        : SkyblockNpcInfoRecipeType.INSTANCE;
                sbe$openWithTab(stack, inputRecipes, preferredTab, null);
                ci.cancel();
                return;
            }
        }

        // ── RESULT-miss fallback ────────────────────────────────────────────────────
        if (openType == ActionType.RESULT) {
            List<ReliableClientRecipe> resultRecipes =
                    ClientRecipeCache.INSTANCE.getRecipesForCraftingOutput(stack);
            if (resultRecipes.isEmpty()) {
                List<ReliableClientRecipe> inputRecipes =
                        ClientRecipeCache.INSTANCE.getRecipesForCraftingInput(stack);
                if (!inputRecipes.isEmpty()) {
                    sbe$openWithTab(stack, inputRecipes, null, SkyblockRecipeUtil.extractSkyblockId(stack));
                    ci.cancel();
                    return;
                }
            } else {
                // Normal RESULT path — seek to matching page for family items
                sbe$openWithTab(stack, resultRecipes, null, SkyblockRecipeUtil.extractSkyblockId(stack));
                ci.cancel();
                return;
            }
        }

        // ── INPUT path — seek to matching page for family items ────────────────────
        if (openType == ActionType.INPUT) {
            List<ReliableClientRecipe> inputRecipes =
                    ClientRecipeCache.INSTANCE.getRecipesForCraftingInput(stack);
            if (!inputRecipes.isEmpty()) {
                sbe$openWithTab(stack, inputRecipes, null, SkyblockRecipeUtil.extractSkyblockId(stack));
                ci.cancel();
            }
        }
    }

    /**
     * Opens a {@link RecipeViewScreen} with optional preferred tab and page-seek.
     *
     * @param stack       the clicked item (used as the recipe origin)
     * @param recipes     recipes to display
     * @param preferredTab tab to show first, or {@code null} to use RRV's default ordering
     * @param seekId      Skyblock ID to seek to on the correct page, or {@code null} to skip
     */
    @Unique
    private void sbe$openWithTab(
            ItemStack stack,
            List<ReliableClientRecipe> recipes,
            ReliableClientRecipeType preferredTab,
            String seekId) {

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        Screen parent = Minecraft.getInstance().screen;
        ArrayList<RecipeViewScreen> viewHistory = new ArrayList<>();

        if (parent instanceof RecipeViewScreen viewScreen) {
            parent = viewScreen.getMenu().getParentScreen();
            viewHistory = viewScreen.getMenu().getViewHistory();
        }

        int containerId = parent instanceof AbstractContainerScreen<?> cs
                ? cs.getMenu().containerId : 0;

        RecipeViewMenu menu = preferredTab != null
                ? new RecipeViewMenu(parent, containerId, player.getInventory(),
                recipes, stack, ActionType.RESULT, viewHistory, preferredTab)
                : new RecipeViewMenu(parent, containerId, player.getInventory(),
                recipes, stack, ActionType.RESULT, viewHistory);

        // Seek to the page matching the clicked item's exact Skyblock ID
        if (seekId != null) {
            SkyblockRecipeUtil.seekToMatchingPage(menu, seekId);
        }

        Minecraft.getInstance().setScreen(
                new RecipeViewScreen(menu, player.getInventory(), Component.empty()));
    }

    @Unique
    private NeuItem sbe$findNpcItem(ItemStack stack) {
        if (!stack.has(DataComponents.CUSTOM_NAME)) return null;
        Component displayName = stack.get(DataComponents.CUSTOM_NAME);
        if (displayName == null) return null;
        return NeuItemRegistry.getNpcByDisplayName(displayName);
    }

}