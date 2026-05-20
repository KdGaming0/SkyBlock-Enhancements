package com.github.kd_gaming1.skyblockenhancements.compat.rrv.service;

import cc.cassian.rrv.api.ActionType;
import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.ClientRecipeCache;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.injection.SkyblockRecipeIndex;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.npc.SkyblockNpcInfoRecipeType;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.npc.SkyblockNpcShopRecipeType;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.reforge.SkyblockReforgeClientRecipe;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.util.SkyblockRecipeUtil;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItem;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItemRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
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
 *   <li><b>SkyBlock item recipe lookup</b> — uses {@link SkyblockRecipeIndex} for O(1)
 *       lookup by SkyBlock internal ID, bypassing RRV's slow item-type scan.</li>
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
                SkyblockRecipeIndex.getRecipesForResult(stack);
        List<ReliableClientRecipe> inputRecipes =
                SkyblockRecipeIndex.getRecipesForIngredient(stack);

        // Fall back to RRV's default lookup if our index is empty.
        if (resultRecipes.isEmpty() && inputRecipes.isEmpty()) {
            resultRecipes = ClientRecipeCache.INSTANCE.getRecipesForCraftingOutput(stack);
            inputRecipes = ClientRecipeCache.INSTANCE.getRecipesForCraftingInput(stack);
        }

        List<ReliableClientRecipe> merged = mergeDeduped(resultRecipes, inputRecipes);
        if (merged.isEmpty()) return false;

        String seekId = SkyblockRecipeUtil.extractSkyblockId(stack);
        ActionType actionType = resultRecipes.isEmpty() ? ActionType.INPUT : ActionType.RESULT;
        openWithTab(stack, merged, null, seekId, actionType);
        return true;
    }

    private static boolean tryOpenInput(ItemStack stack) {
        List<ReliableClientRecipe> inputRecipes =
                SkyblockRecipeIndex.getRecipesForIngredient(stack);

        // Fall back to RRV's default lookup if our index is empty.
        if (inputRecipes.isEmpty()) {
            inputRecipes = ClientRecipeCache.INSTANCE.getRecipesForCraftingInput(stack);
        }
        if (inputRecipes.isEmpty()) return false;

        String seekId = SkyblockRecipeUtil.extractSkyblockId(stack);
        openWithTab(stack, inputRecipes, null, seekId, ActionType.INPUT);
        return true;
    }

    /**
     * Merges two recipe lists into one, deduplicating by identity.
     * Uses an {@link IdentityHashMap} set for O(1) containment checks
     * instead of the previous O(n²) {@link List#contains} approach.
     */
    private static List<ReliableClientRecipe> mergeDeduped(
            List<ReliableClientRecipe> a,
            List<ReliableClientRecipe> b) {
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;

        Set<ReliableClientRecipe> seen =
                Collections.newSetFromMap(new IdentityHashMap<>(a.size() + b.size()));
        List<ReliableClientRecipe> merged = new ArrayList<>(a.size() + b.size());
        for (ReliableClientRecipe r : a) if (seen.add(r)) merged.add(r);
        for (ReliableClientRecipe r : b) if (seen.add(r)) merged.add(r);
        return merged;
    }

    private static NeuItem findNpcItem(ItemStack stack) {
        Component displayName = stack.get(DataComponents.CUSTOM_NAME);
        if (displayName == null) return null;
        return NeuItemRegistry.getNpcByDisplayName(displayName.getString());
    }

    public static final Comparator<ReliableClientRecipe> RECIPE_COMPARATOR = (a, b) -> {
        boolean aReforge = a instanceof SkyblockReforgeClientRecipe;
        boolean bReforge = b instanceof SkyblockReforgeClientRecipe;
        if (!aReforge && !bReforge) return 0;
        if (aReforge != bReforge) return aReforge ? 1 : -1;

        SkyblockReforgeClientRecipe ra = (SkyblockReforgeClientRecipe) a;
        SkyblockReforgeClientRecipe rb = (SkyblockReforgeClientRecipe) b;
        int nameCmp = ra.getReforgeName().compareTo(rb.getReforgeName());
        if (nameCmp != 0) return nameCmp;
        return Integer.compare(rarityOrdinal(ra.getRarity()), rarityOrdinal(rb.getRarity()));
    };

    public static int rarityOrdinal(String rarity) {
        return switch (rarity) {
            case "COMMON" -> 0;
            case "UNCOMMON" -> 1;
            case "RARE" -> 2;
            case "EPIC" -> 3;
            case "LEGENDARY" -> 4;
            case "MYTHIC" -> 5;
            case "DIVINE" -> 6;
            case "SPECIAL" -> 7;
            case "VERY_SPECIAL" -> 8;
            case "SUPREME" -> 9;
            default -> -1;
        };
    }

    private static void openWithTab(
            ItemStack stack,
            List<ReliableClientRecipe> recipes,
            ReliableClientRecipeType preferredTab,
            String seekId,
            ActionType actionType) {

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        if (recipes == null || recipes.isEmpty()) return;

        recipes.sort(RECIPE_COMPARATOR);

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
