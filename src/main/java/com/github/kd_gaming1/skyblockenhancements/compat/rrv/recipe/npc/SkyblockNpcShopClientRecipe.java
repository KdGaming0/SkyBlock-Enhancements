package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.npc;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.util.SkyblockRecipePriority;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.AbstractSkyblockClientRecipe;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public class SkyblockNpcShopClientRecipe extends AbstractSkyblockClientRecipe
        implements ReliableClientRecipe {

    private static final int ARROW_X = 91;
    private static final int ARROW_Y = 25;
    private static final int BUTTON_ROW_Y_OFFSET = 52;
    private static final int BUTTON_W = 56;
    private static final int BUTTON_H = 12;

    private final SlotContent[] costs;
    private final SlotContent result;
    private final String npcId;
    private final String npcDisplayName;

    public SkyblockNpcShopClientRecipe(SlotContent[] costs, SlotContent result,
                                       String npcId, String npcDisplayName, String[] wikiUrls) {
        super(wikiUrls);
        this.costs = costs;
        this.result = result;
        this.npcId = npcId != null ? npcId : "";
        this.npcDisplayName = npcDisplayName != null ? npcDisplayName : "";
    }

    public String getNpcId() {
        return npcId;
    }

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
    public void renderRecipe(RecipeViewScreen screen, RecipePosition pos, GuiGraphics gfx,
                             int mouseX, int mouseY, float partialTicks) {
        if (!npcDisplayName.isEmpty()) {
            gfx.drawString(Minecraft.getInstance().font,
                    Component.literal(npcDisplayName), 0, 0, 0xFFFFFFFF, true);
        }
        gfx.drawString(Minecraft.getInstance().font,
                Component.literal("→"), ARROW_X, ARROW_Y, 0xFF404040, false);
        maintainButtons(screen, pos);
    }

    @Override
    @Nullable
    protected Button placeButtons(RecipeViewScreen screen, RecipePosition pos) {
        int btnY = pos.top() + BUTTON_ROW_Y_OFFSET;
        int leftX = pos.left();
        Button sentinel = null;

        SkyblockNpcInfoClientRecipe infoRecipe = SkyblockNpcInfoRegistry.get(npcId);
        if (infoRecipe != null) {
            Button infoBtn = Button.builder(
                            Component.literal("NPC Info"),
                            b -> RecipeViewOpener.open(infoRecipe))
                    .pos(leftX, btnY)
                    .size(BUTTON_W, BUTTON_H)
                    .build();
            screen.addRecipeWidget(infoBtn);
            sentinel = infoBtn;
        }

        Button wiki = placeWikiButton(screen, leftX + BUTTON_W + 6, btnY);
        if (sentinel == null) sentinel = wiki;

        return sentinel;
    }

    @Override
    public int getPriority() {
        return SkyblockRecipePriority.NPC_SHOP;
    }
}