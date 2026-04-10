package com.github.kd_gaming1.skyblockenhancements.compat.rrv.drops;

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
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Client display for mob drop recipes. Shows mob name header and drop slots with chance tooltips. */
public class SkyblockDropsClientRecipe implements ReliableClientRecipe {

    /** Vertical space used by the mob name header + drop grid, before the wiki row. */
    private static final int CONTENT_HEIGHT = 12 + 3 * 18;

    private final String mobName;
    private final SlotContent[] drops;
    private final String[] chances;
    private final String[] wikiUrls;
    private Button wikiButton;

    public SkyblockDropsClientRecipe(
            String mobName, SlotContent[] drops, String[] chances, int level, int combatXp,
            String[] wikiUrls) {
        this.mobName = mobName;
        this.drops = drops;
        this.chances = chances;
        this.wikiUrls = SkyblockRecipeUtil.sanitizeWikiUrls(wikiUrls);
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

            // Capture index for the lambda — shows drop chance in the tooltip.
            final int idx = i;
            ctx.addAdditionalStackModifier(i, (stack, tooltip) -> {
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
                Minecraft.getInstance().font, Component.literal(mobName), 6, 2, 0xFFFFFFFF, true);

        if (wikiButton == null || !screen.children().contains(wikiButton)) {
            wikiButton = SkyblockRecipeUtil.addWikiButton(
                    screen, wikiUrls, pos.left(), pos.top() + CONTENT_HEIGHT);
        }
    }

    @Override
    public int getPriority() {
        return SkyblockRecipePriority.DROPS;
    }
}