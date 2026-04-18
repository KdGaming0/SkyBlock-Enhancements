package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.npc;

import cc.cassian.rrv.api.ActionType;
import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Opens a new {@link RecipeViewScreen} while preserving parent-screen and view-history state.
 *
 * <p>Shared by NPC shop/info navigation so the "back" navigation works regardless of which recipe
 * opened which. If we're already inside a recipe view, reuse its parent + history; otherwise the
 * current screen becomes the parent.
 */
final class RecipeViewOpener {

    private RecipeViewOpener() {}

    static void open(ReliableClientRecipe recipe) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        Screen current = Minecraft.getInstance().screen;
        Screen parent = current;
        ArrayList<RecipeViewScreen> viewHistory = new ArrayList<>();

        if (current instanceof RecipeViewScreen existing) {
            parent = existing.getMenu().getParentScreen();
            viewHistory = existing.getMenu().getViewHistory();
        }

        int containerId = parent instanceof AbstractContainerScreen<?> cs
                ? cs.getMenu().containerId : 0;

        Minecraft.getInstance().setScreen(new RecipeViewScreen(
                new RecipeViewMenu(
                        parent, containerId, player.getInventory(),
                        List.<ReliableClientRecipe>of(recipe),
                        ItemStack.EMPTY, ActionType.ANY, viewHistory),
                player.getInventory(),
                Component.empty()));
    }
}