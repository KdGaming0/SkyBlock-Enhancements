package com.github.kd_gaming1.skyblockenhancements.mixin.rrv;

import cc.cassian.rrv.api.ActionType;
import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.overlay.itemlist.view.ItemViewOverlay;
import cc.cassian.rrv.common.recipe.ClientRecipeCache;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
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
 * </ol>
 *
 * <p>The NPC check runs before the standard lookup because NPC items (like SkyMart's emerald)
 * may share a vanilla item type with other SkyBlock items, causing unrelated recipes to appear.
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
                ci.cancel();
                sbe$openWithPreferredTab(stack, inputRecipes, preferredTab);
            }
            return;
        }

        // ── Non-NPC fallback: RESULT → INPUT ────────────────────────────────────────
        if (openType != ActionType.RESULT) return;
        if (!ClientRecipeCache.INSTANCE.getRecipesForCraftingOutput(stack).isEmpty()) return;

        List<ReliableClientRecipe> inputRecipes =
                ClientRecipeCache.INSTANCE.getRecipesForCraftingInput(stack);
        if (inputRecipes.isEmpty()) return;

        ci.cancel();
        ItemViewOverlay.INSTANCE.openRecipeView(stack, ActionType.INPUT);
    }

    @SuppressWarnings("DuplicatedCode")
    @Unique
    private static void sbe$openWithPreferredTab(
            ItemStack stack,
            List<ReliableClientRecipe> recipes,
            ReliableClientRecipeType preferredTab) {

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        Screen current = Minecraft.getInstance().screen;
        Screen parent = current;
        ArrayList<RecipeViewScreen> viewHistory = new ArrayList<>();

        if (current instanceof RecipeViewScreen viewScreen) {
            parent = viewScreen.getMenu().getParentScreen();
            viewHistory = viewScreen.getMenu().getViewHistory();
        }

        int containerId = parent instanceof AbstractContainerScreen<?> cs
                ? cs.getMenu().containerId : 0;

        Minecraft.getInstance().setScreen(
                new RecipeViewScreen(
                        new RecipeViewMenu(
                                parent, containerId, player.getInventory(),
                                recipes, stack, ActionType.INPUT,
                                viewHistory, preferredTab),
                        player.getInventory(),
                        Component.empty()));
    }

    /**
     * Finds the NPC item matching the clicked stack's {@code CUSTOM_NAME} using the
     * pre-built Component-keyed index in {@link NeuItemRegistry}. O(1) via
     * {@code Component.equals()} / {@code hashCode()} instead of scanning all ~8 000 items.
     */
    @Unique
    private static NeuItem sbe$findNpcItem(ItemStack stack) {
        Component customName = stack.get(DataComponents.CUSTOM_NAME);
        if (customName == null || !NeuItemRegistry.isLoaded()) return null;

        return NeuItemRegistry.getNpcByDisplayName(customName);
    }
}