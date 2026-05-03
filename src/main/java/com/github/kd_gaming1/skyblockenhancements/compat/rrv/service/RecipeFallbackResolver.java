package com.github.kd_gaming1.skyblockenhancements.compat.rrv.service;

import cc.cassian.rrv.api.ActionType;
import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.ClientRecipeCache;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.reforge.SkyblockReforgeClientRecipe;
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
 *   <li><b>NPC items</b> — detected by display name. Opens with the correct tab
 *       pre-selected (shop vs info).</li>
 *   <li><b>Reforge stones</b> — merges reforge recipes into the left-click (RESULT)
 *       view so stats appear alongside crafting and wiki tabs.</li>
 *   <li><b>Family item page-seek</b> — advances to the page whose recipe result
 *       matches the clicked item's exact SkyBlock ID.</li>
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

        if (tryOpenNpc(stack)) return true;
        if (openType == ActionType.RESULT && tryOpenMergedResult(stack)) return true;
        if (openType == ActionType.INPUT && tryOpenInput(stack)) return true;

        return false;
    }

    private static boolean tryOpenNpc(ItemStack stack) {
        NeuItem npcItem = findNpcItem(stack);
        if (npcItem == null) return false;

        List<ReliableClientRecipe> inputRecipes =
                ClientRecipeCache.INSTANCE.getRecipesForCraftingInput(stack);
        if (inputRecipes.isEmpty()) return false;

        ReliableClientRecipeType preferredTab = npcItem.hasNpcShopRecipes()
                ? SkyblockNpcShopRecipeType.INSTANCE
                : SkyblockNpcInfoRecipeType.INSTANCE;

        openWithTab(stack, inputRecipes, preferredTab, null, ActionType.ANY);
        return true;
    }

    private static boolean tryOpenMergedResult(ItemStack stack) {
        List<ReliableClientRecipe> resultRecipes =
                ClientRecipeCache.INSTANCE.getRecipesForCraftingOutput(stack);
        List<ReliableClientRecipe> inputRecipes =
                ClientRecipeCache.INSTANCE.getRecipesForCraftingInput(stack);

        List<ReliableClientRecipe> merged = appendReforgeRecipes(resultRecipes, inputRecipes);
        if (merged.isEmpty()) return false;

        String seekId = SkyblockRecipeUtil.extractSkyblockId(stack);
        ActionType actionType = resultRecipes.isEmpty() ? ActionType.INPUT : ActionType.RESULT;
        openWithTab(stack, merged, null, seekId, actionType);
        return true;
    }

    private static boolean tryOpenInput(ItemStack stack) {
        List<ReliableClientRecipe> inputRecipes =
                ClientRecipeCache.INSTANCE.getRecipesForCraftingInput(stack);
        if (inputRecipes.isEmpty()) return false;

        String seekId = SkyblockRecipeUtil.extractSkyblockId(stack);
        openWithTab(stack, inputRecipes, null, seekId, ActionType.INPUT);
        return true;
    }

    /**
     * Appends reforge recipes from {@code candidates} that are not already present
     * in {@code base}. Preserves original behavior for all other recipe types.
     */
    private static List<ReliableClientRecipe> appendReforgeRecipes(
            List<ReliableClientRecipe> base,
            List<ReliableClientRecipe> candidates) {
        if (candidates.isEmpty()) return base;

        List<ReliableClientRecipe> additions = candidates.stream()
                .filter(r -> r instanceof SkyblockReforgeClientRecipe)
                .filter(r -> !base.contains(r))
                .toList();

        if (additions.isEmpty()) return base;

        List<ReliableClientRecipe> merged = new ArrayList<>(base);
        merged.addAll(additions);
        return merged;
    }

    private static NeuItem findNpcItem(ItemStack stack) {
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
