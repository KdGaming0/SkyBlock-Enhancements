package com.github.kd_gaming1.skyblockenhancements.compat.rrv.crafting;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.SkyblockRecipePriority;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.SkyblockRecipeUtil;
import com.github.kd_gaming1.skyblockenhancements.util.HypixelLocationState;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Client-side display for a 3×3 SkyBlock crafting recipe with optional wiki and view-recipe buttons. */
public class SkyblockCraftingClientRecipe implements ReliableClientRecipe {

    private final SlotContent[] inputs;
    private final SlotContent output;
    private final String[] wikiUrls;

    private Button wikiButton;

    private final int tierOffset;

    public SkyblockCraftingClientRecipe(
            SlotContent[] inputs, SlotContent output, String[] wikiUrls) {
        this.inputs = inputs;
        this.output = output;
        this.wikiUrls = SkyblockRecipeUtil.sanitizeWikiUrls(wikiUrls);
        this.tierOffset = SkyblockRecipeUtil.extractTierFromResults(getResults());
    }

    @Override
    public ReliableClientRecipeType getViewType() {
        return SkyblockCraftingRecipeType.INSTANCE;
    }

    @Override
    public void bindSlots(RecipeViewMenu.SlotFillContext ctx) {
        for (int i = 0; i < 9; i++) {
            if (inputs[i] != null) ctx.bindSlot(i, inputs[i]);
        }
        if (output != null) ctx.bindSlot(9, output);
    }

    @Override
    public List<SlotContent> getIngredients() {
        List<SlotContent> list = new ArrayList<>();
        for (SlotContent sc : inputs) {
            if (sc != null) list.add(sc);
        }
        return list;
    }

    @Override
    public List<SlotContent> getResults() {
        return output != null ? List.of(output) : List.of();
    }

    @Override
    public boolean redirectsAsResult(ItemStack stack) {
        return SkyblockRecipeUtil.matchesAnyOrFamily(stack, getResults());
    }

    @Override
    public boolean redirectsAsIngredient(ItemStack stack) {
        return SkyblockRecipeUtil.matchesAnyOrFamily(stack, getIngredients());
    }

    @Override
    public void renderRecipe(
            RecipeViewScreen screen, RecipePosition pos, GuiGraphics gfx,
            int mouseX, int mouseY, float partialTicks) {

        gfx.drawString(
                Minecraft.getInstance().font, Component.literal("→"), 62, 22, 0xFF404040, false);

        if (wikiButton == null || !screen.children().contains(wikiButton)) {
            rebuildButtons(screen, pos);
        }
    }

    /**
     * Rebuilds both the wiki button and the view-recipe button.
     * Called when the screen clears its widget list (e.g. on resize or page navigation).
     */
    private void rebuildButtons(RecipeViewScreen screen, RecipePosition pos) {
        int btnY = pos.top() + 56;

        wikiButton = SkyblockRecipeUtil.addWikiButton(screen, wikiUrls, pos.left(), btnY);

        // View Recipe button — only relevant while on SkyBlock
        if (HypixelLocationState.isOnSkyblock()) {
            String itemId = resolveOutputId();
            if (itemId != null) {
                Button viewRecipeButton = Button.builder(
                                Component.literal("Craft"),
                                b -> sendViewRecipeCommand(itemId))
                        .pos(pos.left() + 62, btnY)
                        .size(56, 12)
                        .build();
                screen.addRecipeWidget(viewRecipeButton);
            }
        }
    }

    /**
     * Extracts the SkyBlock internal ID from the output slot's first valid stack.
     * Returns null if the output is empty or has no ID component.
     */
    private String resolveOutputId() {
        if (output == null || output.isEmpty()) return null;

        List<ItemStack> contents = output.getValidContents();
        if (contents.isEmpty()) return null;

        return SkyblockRecipeUtil.extractSkyblockId(contents.getFirst());
    }

    /** Sends {@code /viewrecipe <itemId>} to the server. */
    private static void sendViewRecipeCommand(String itemId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.getConnection() != null) {
            mc.getConnection().sendCommand("viewrecipe " + itemId);
        }
    }

    @Override
    public int getPriority() {
        return SkyblockRecipePriority.CRAFTING + tierOffset;
    }
}