package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.drops;

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

public class SkyblockDropsClientRecipe extends AbstractSkyblockClientRecipe
        implements ReliableClientRecipe {

    private static final int BUTTON_ROW_Y_OFFSET = 12 + 3 * 18;
    private static final int MOB_NAME_X = 6;
    private static final int MOB_NAME_Y = 2;

    private final String mobName;
    private final SlotContent[] drops;
    private final String[] chances;

    public SkyblockDropsClientRecipe(String mobName, SlotContent[] drops, String[] chances,
                                     int level, int combatXp, String[] wikiUrls) {
        super(wikiUrls);
        this.mobName = mobName;
        this.drops = drops;
        this.chances = chances;
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
    public void renderRecipe(RecipeViewScreen screen, RecipePosition pos, GuiGraphics gfx,
                             int mouseX, int mouseY, float partialTicks) {
        gfx.drawString(Minecraft.getInstance().font,
                Component.literal(mobName), MOB_NAME_X, MOB_NAME_Y, 0xFFFFFFFF, true);
        maintainButtons(screen, pos);
    }

    @Override
    @Nullable
    protected Button placeButtons(RecipeViewScreen screen, RecipePosition pos) {
        return placeWikiButton(screen, pos.left(), pos.top() + BUTTON_ROW_Y_OFFSET);
    }

    @Override
    public int getPriority() {
        return SkyblockRecipePriority.DROPS;
    }
}