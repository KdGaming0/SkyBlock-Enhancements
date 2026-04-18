package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.crafting;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.util.SkyblockRecipePriority;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.util.SkyblockRecipeUtil;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.base.AbstractSkyblockClientRecipe;
import com.github.kd_gaming1.skyblockenhancements.util.HypixelLocationState;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class SkyblockCraftingClientRecipe extends AbstractSkyblockClientRecipe
        implements ReliableClientRecipe {

    private static final int ARROW_X   = 62;
    private static final int ARROW_Y   = 22;
    private static final int BUTTON_ROW_Y_OFFSET = 56;
    private static final int WIKI_BUTTON_W = 56;

    private final SlotContent[] inputs;
    private final SlotContent output;
    private final int tierOffset;

    public SkyblockCraftingClientRecipe(SlotContent[] inputs, SlotContent output, String[] wikiUrls) {
        super(wikiUrls);
        this.inputs = inputs;
        this.output = output;
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
    public void renderRecipe(RecipeViewScreen screen, RecipePosition pos, GuiGraphics gfx,
                             int mouseX, int mouseY, float partialTicks) {
        gfx.drawString(Minecraft.getInstance().font,
                Component.literal("→"), ARROW_X, ARROW_Y, 0xFF404040, false);
        maintainButtons(screen, pos);
    }

    @Override
    @Nullable
    protected Button placeButtons(RecipeViewScreen screen, RecipePosition pos) {
        int btnY = pos.top() + BUTTON_ROW_Y_OFFSET;
        Button sentinel = placeWikiButton(screen, pos.left(), btnY);

        if (HypixelLocationState.isOnSkyblock()) {
            String itemId = resolveOutputId();
            if (itemId != null) {
                int craftX = (sentinel != null) ? pos.left() + WIKI_BUTTON_W + 6 : pos.left();
                Button craftBtn = Button.builder(
                                Component.literal("Craft"),
                                b -> sendViewRecipeCommand(itemId))
                        .pos(craftX, btnY)
                        .size(WIKI_BUTTON_W, 12)
                        .build();
                screen.addRecipeWidget(craftBtn);
                if (sentinel == null) sentinel = craftBtn;
            }
        }
        return sentinel;
    }

    @Nullable
    private String resolveOutputId() {
        if (output == null || output.isEmpty()) return null;
        List<ItemStack> contents = output.getValidContents();
        if (contents.isEmpty()) return null;
        return SkyblockRecipeUtil.extractSkyblockId(contents.getFirst());
    }

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