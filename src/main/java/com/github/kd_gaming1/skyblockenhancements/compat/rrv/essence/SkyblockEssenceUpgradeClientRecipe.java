package com.github.kd_gaming1.skyblockenhancements.compat.rrv.essence;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.SkyblockRecipePriority;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.SkyblockRecipeUtil;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Client display for essence upgrade recipes. Renders the star level, essence type label,
 * an arrow between input and output, and a wiki button.
 */
public class SkyblockEssenceUpgradeClientRecipe implements ReliableClientRecipe {

    private static final int DISPLAY_HEIGHT = 68;
    private static final int ARROW_U = 0;
    private static final int ARROW_V = 0;
    private static final int ARROW_W = 22;
    private static final int ARROW_H = 15;

    private final SlotContent input;
    private final SlotContent output;
    private final SlotContent essence;
    private final SlotContent[] companions;
    private final int starLevel;
    private final String essenceType;
    private final String[] wikiUrls;

    private final List<GuiEventListener> addedButtons = new ArrayList<>(1);

    public SkyblockEssenceUpgradeClientRecipe(
            SlotContent input, SlotContent output, SlotContent essence,
            SlotContent[] companions, int starLevel, String essenceType, String[] wikiUrls) {
        this.input = input;
        this.output = output;
        this.essence = essence;
        this.companions = companions;
        this.starLevel = starLevel;
        this.essenceType = essenceType;
        this.wikiUrls = SkyblockRecipeUtil.sanitizeWikiUrls(wikiUrls);
    }

    @Override
    public ReliableClientRecipeType getViewType() {
        return SkyblockEssenceUpgradeRecipeType.INSTANCE;
    }

    @Override
    public void bindSlots(RecipeViewMenu.SlotFillContext ctx) {
        if (input != null) ctx.bindSlot(0, input);
        if (essence != null) ctx.bindSlot(1, essence);
        for (int i = 0; i < companions.length && i < 4; i++) {
            if (companions[i] != null) {
                ctx.bindOptionalSlot(2 + i, companions[i],
                        RecipeViewMenu.OptionalSlotRenderer.DEFAULT);
            }
        }
        if (output != null) ctx.bindSlot(6, output);
    }

    @Override
    public List<SlotContent> getIngredients() {
        List<SlotContent> list = new ArrayList<>();
        if (input != null) list.add(input);
        if (essence != null) list.add(essence);
        for (SlotContent comp : companions) {
            if (comp != null) list.add(comp);
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

        var font = Minecraft.getInstance().font;

        // Star level and essence type label (e.g. "★5 Wither")
        String starLabel = "★" + starLevel + " " + essenceType;
        gfx.drawString(font, Component.literal("§e" + starLabel),
                62, 2, 0xFFFFFF, true);

        // Arrow from input area to output
        gfx.drawString(font, Component.literal("→"),
                82, 22, 0xFF404040, false);

        // Wiki button
        if (!buttonsStillInScreen(screen)) {
            addedButtons.clear();
            addButtons(screen, pos);
        }
    }

    private boolean buttonsStillInScreen(RecipeViewScreen screen) {
        if (addedButtons.isEmpty()) return false;
        return SkyblockRecipeUtil.containsAllByIdentity(screen.children(), addedButtons);
    }

    private void addButtons(RecipeViewScreen screen, RecipePosition pos) {
        int btnX = pos.left() + 68;
        int btnY = pos.top() + DISPLAY_HEIGHT - 14;

        Button wikiBtn = SkyblockRecipeUtil.addWikiButton(screen, wikiUrls, btnX, btnY);
        if (wikiBtn != null) {
            addedButtons.add(wikiBtn);
        }
    }

    @Override
    public int getPriority() {
        return SkyblockRecipePriority.ESSENCE_UPGRADE;
    }
}