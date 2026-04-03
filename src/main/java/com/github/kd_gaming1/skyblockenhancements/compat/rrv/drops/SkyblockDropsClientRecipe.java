package com.github.kd_gaming1.skyblockenhancements.compat.rrv.drops;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.SkyblockRecipeUtil;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Client display for mob drop recipes. Shows mob name and drop items with chance tooltips. */
public class SkyblockDropsClientRecipe implements ReliableClientRecipe {

    private final String mobName;
    private final SlotContent[] drops;
    private final String[] chances;
    private final int level;
    private final int combatXp;

    public SkyblockDropsClientRecipe(
            String mobName, SlotContent[] drops, String[] chances, int level, int combatXp) {
        this.mobName = mobName;
        this.drops = drops;
        this.chances = chances;
        this.level = level;
        this.combatXp = combatXp;
    }

    @Override
    public ReliableClientRecipeType getViewType() {
        return SkyblockDropsRecipeType.INSTANCE;
    }

    @Override
    public void bindSlots(RecipeViewMenu.SlotFillContext ctx) {
        for (int i = 0; i < drops.length && i < 12; i++) {
            if (drops[i] == null) continue;
            ctx.bindOptionalSlot(i, drops[i], RecipeViewMenu.OptionalSlotRenderer.DEFAULT);
            final int idx = i;
            ctx.addAdditionalStackModifier(
                    i,
                    (stack, tooltip) -> {
                        if (idx < chances.length && chances[idx] != null && !chances[idx].isEmpty()) {
                            tooltip.add(Component.literal("§7Drop chance: §e" + chances[idx]));
                        }
                    });
        }
    }

    @Override
    public List<SlotContent> getIngredients() {
        return List.of();
    }

    @Override
    public List<SlotContent> getResults() {
        List<SlotContent> list = new ArrayList<>();
        for (SlotContent sc : drops) {
            if (sc != null) list.add(sc);
        }
        return list;
    }

    @Override
    public boolean redirectsAsResult(ItemStack stack) {
        return SkyblockRecipeUtil.matchesAny(stack, getResults());
    }

    @Override
    public void renderRecipe(
            RecipeViewScreen screen,
            RecipePosition pos,
            GuiGraphics gfx,
            int mouseX,
            int mouseY,
            float partialTicks) {
        gfx.drawString(Minecraft.getInstance().font, Component.literal(mobName), 6, 2, 0xFFFFFFFF, true);
    }
}