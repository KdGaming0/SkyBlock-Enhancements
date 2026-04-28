package com.github.kd_gaming1.skyblockenhancements.compat.rrv.service;

import cc.cassian.rrv.api.ActionType;
import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.ClientRecipeCache;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.npc.SkyblockNpcInfoRecipeType;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.npc.SkyblockNpcShopRecipeType;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.util.SkyblockRecipeUtil;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItem;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItemRegistry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Resolves recipe-view fallback logic when RRV's default lookup doesn't find a match.
 *
 * <p>Handles three scenarios:
 * <ol>
 *   <li><b>NPC items</b> — detected by {@code CUSTOM_NAME}. Opens with the correct tab
 *       pre-selected (shop vs info).</li>
 *   <li><b>RESULT miss fallback</b> — when no output recipes exist, retries with
 *       input recipes so craft-reference items still work.</li>
 *   <li><b>Family item page-seek</b> — when a family item is clicked, advances to the
 *       page whose recipe result matches the clicked item's exact Skyblock ID.</li>
 * </ol>
 */
public final class RecipeFallbackResolver {

    private RecipeFallbackResolver() {}

    /**
     * Attempts to resolve and open a recipe view for the given stack.
     *
     * @return {@code true} if a fallback recipe view was opened and the caller should
     *         cancel the original RRV action
     */
    public static boolean tryOpen(ItemStack stack, ActionType openType) {
        if (stack.isEmpty()) return false;

        // ── NPC handling (runs for any ActionType) ───────────────────────────────
        NeuItem npcItem = findNpcItem(stack);
        if (npcItem != null) {
            List<ReliableClientRecipe> inputRecipes =
                    ClientRecipeCache.INSTANCE.getRecipesForCraftingInput(stack);
            if (!inputRecipes.isEmpty()) {
                ReliableClientRecipeType preferredTab = npcItem.hasNpcShopRecipes()
                        ? SkyblockNpcShopRecipeType.INSTANCE
                        : SkyblockNpcInfoRecipeType.INSTANCE;
                // Use ActionType.ANY so that the NPC item doesn't get filtered as an output
                openWithTab(stack, inputRecipes, preferredTab, null, ActionType.ANY);
                return true;
            }
        }

        // ── RESULT-miss fallback ─────────────────────────────────────────────────
        if (openType == ActionType.RESULT) {
            List<ReliableClientRecipe> resultRecipes =
                    ClientRecipeCache.INSTANCE.getRecipesForCraftingOutput(stack);
            if (resultRecipes.isEmpty()) {
                List<ReliableClientRecipe> inputRecipes =
                        ClientRecipeCache.INSTANCE.getRecipesForCraftingInput(stack);
                if (!inputRecipes.isEmpty()) {
                    openWithTab(stack, inputRecipes, null,
                            SkyblockRecipeUtil.extractSkyblockId(stack), ActionType.INPUT);
                    return true;
                }
            } else {
                openWithTab(stack, resultRecipes, null,
                        SkyblockRecipeUtil.extractSkyblockId(stack), ActionType.RESULT);
                return true;
            }
        }

        // ── INPUT path ───────────────────────────────────────────────────────────
        if (openType == ActionType.INPUT) {
            List<ReliableClientRecipe> inputRecipes =
                    ClientRecipeCache.INSTANCE.getRecipesForCraftingInput(stack);
            if (!inputRecipes.isEmpty()) {
                openWithTab(stack, inputRecipes, null,
                        SkyblockRecipeUtil.extractSkyblockId(stack), openType);
                return true;
            }
        }

        return false;
    }

    // ---------------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------------

    private static NeuItem findNpcItem(ItemStack stack) {
        if (!stack.has(DataComponents.CUSTOM_NAME)) return null;
        Component displayName = stack.get(DataComponents.CUSTOM_NAME);
        if (displayName == null) return null;
        return NeuItemRegistry.getNpcByDisplayName(displayName);
    }

    private static void openWithTab(
            ItemStack stack,
            List<ReliableClientRecipe> recipes,
            ReliableClientRecipeType preferredTab,
            String seekId,
            ActionType actionType) {

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

        // Instead of hardcoding ActionType.RESULT, use the actionType parameter
        RecipeViewMenu menu = preferredTab != null
                ? new RecipeViewMenu(parent, containerId, player.getInventory(),
                recipes, stack, actionType, viewHistory, preferredTab)
                : new RecipeViewMenu(parent, containerId, player.getInventory(),
                recipes, stack, actionType, viewHistory);

        if (seekId != null) {
            SkyblockRecipeUtil.seekToMatchingPage(menu, seekId);
        }

        Minecraft.getInstance().setScreen(
                new RecipeViewScreen(menu, player.getInventory(), Component.empty()));
    }
}
