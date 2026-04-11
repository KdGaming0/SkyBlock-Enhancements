package com.github.kd_gaming1.skyblockenhancements.compat.rrv.npc;

import cc.cassian.rrv.api.ActionType;
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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class SkyblockNpcShopClientRecipe implements ReliableClientRecipe {

    private final SlotContent[] costs;
    private final SlotContent result;
    private final String npcId;
    private final String npcDisplayName;
    private final String[] wikiUrls;

    // True when buttons need to be (re)added to the screen.
    // Set on init and fade so we never scan screen.children() per frame.
    private boolean buttonsDirty = true;

    public SkyblockNpcShopClientRecipe(
            SlotContent[] costs, SlotContent result, String npcId, String npcDisplayName,
            String[] wikiUrls) {
        this.costs = costs;
        this.result = result;
        this.npcId = npcId != null ? npcId : "";
        this.npcDisplayName = npcDisplayName != null ? npcDisplayName : "";
        this.wikiUrls = SkyblockRecipeUtil.sanitizeWikiUrls(wikiUrls);
    }

    public String getNpcId() {
        return npcId;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────────

    @Override
    public void initRecipe() {
        buttonsDirty = true;
    }

    @Override
    public void fadeRecipe() {
        buttonsDirty = true;
    }

    // ── ReliableClientRecipe ──────────────────────────────────────────────────────

    @Override
    public ReliableClientRecipeType getViewType() {
        return SkyblockNpcShopRecipeType.INSTANCE;
    }

    @Override
    public void bindSlots(RecipeViewMenu.SlotFillContext ctx) {
        for (int i = 0; i < costs.length && i < 5; i++) {
            if (costs[i] != null) {
                ctx.bindOptionalSlot(i, costs[i], RecipeViewMenu.OptionalSlotRenderer.DEFAULT);
            }
        }
        if (result != null) ctx.bindSlot(5, result);
    }

    @Override
    public List<SlotContent> getIngredients() {
        List<SlotContent> list = new ArrayList<>();
        for (SlotContent sc : costs) {
            if (sc != null) list.add(sc);
        }
        return list;
    }

    @Override
    public List<SlotContent> getResults() {
        return result != null ? List.of(result) : List.of();
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

        if (!npcDisplayName.isEmpty()) {
            gfx.drawString(font, Component.literal(npcDisplayName), 0, 0, 0xFFFFFFFF, true);
        }

        gfx.drawString(font, Component.literal("→"), 91, 25, 0xFF404040, false);

        if (buttonsDirty) {
            addButtons(screen, pos);
            buttonsDirty = false;
        }
    }

    private void addButtons(RecipeViewScreen screen, RecipePosition pos) {
        int btnY = pos.top() + 52;
        int leftX = pos.left();

        SkyblockNpcInfoClientRecipe infoRecipe = SkyblockNpcInfoRegistry.get(npcId);
        if (infoRecipe != null) {
            screen.addRecipeWidget(Button.builder(
                            Component.literal("NPC Info"),
                            b -> openNpcInfo(infoRecipe))
                    .pos(leftX, btnY)
                    .size(56, 12)
                    .build());
        }

        SkyblockRecipeUtil.addWikiButton(screen, wikiUrls, leftX + 62, btnY);
    }

    @SuppressWarnings("DuplicatedCode")
    private static void openNpcInfo(SkyblockNpcInfoClientRecipe infoRecipe) {
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
                                List.of(infoRecipe), ItemStack.EMPTY,
                                ActionType.ANY, viewHistory),
                        player.getInventory(),
                        Component.empty()));
    }

    @Override
    public int getPriority() {
        return SkyblockRecipePriority.NPC_SHOP;
    }
}