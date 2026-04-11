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
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class SkyblockEssenceUpgradeClientRecipe implements ReliableClientRecipe {

    private static final int DISPLAY_HEIGHT = 68;

    private final SlotContent input;
    private final SlotContent output;
    private final SlotContent essence;
    private final SlotContent[] companions;
    private final int starLevel;
    private final String essenceType;
    private final String[] wikiUrls;

    // True when buttons need to be (re)added to the screen.
    private boolean buttonsDirty = true;
    private Button sentinelButton = null;

    public SkyblockEssenceUpgradeClientRecipe(
            SlotContent input, SlotContent output, SlotContent essence,
            SlotContent[] companions, int starLevel, String essenceType, String[] wikiUrls) {
        this.input       = input;
        this.output      = output;
        this.essence     = essence;
        this.companions  = companions;
        this.starLevel   = starLevel;
        this.essenceType = essenceType;
        this.wikiUrls    = SkyblockRecipeUtil.sanitizeWikiUrls(wikiUrls);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────────

    @Override
    public void initRecipe() {
        buttonsDirty = true;
    }

    @Override
    public void fadeRecipe() {
        buttonsDirty = true;
        sentinelButton = null;
    }

    // ── ReliableClientRecipe ──────────────────────────────────────────────────────

    @Override
    public ReliableClientRecipeType getViewType() {
        return SkyblockEssenceUpgradeRecipeType.INSTANCE;
    }

    @Override
    public void bindSlots(RecipeViewMenu.SlotFillContext ctx) {
        if (input != null)   ctx.bindSlot(0, input);
        if (essence != null) ctx.bindSlot(1, essence);
        for (int i = 0; i < companions.length && i < 4; i++) {
            if (companions[i] != null)
                ctx.bindOptionalSlot(2 + i, companions[i], RecipeViewMenu.OptionalSlotRenderer.DEFAULT);
        }
        if (output != null) ctx.bindSlot(6, output);
    }

    @Override
    public List<SlotContent> getIngredients() {
        List<SlotContent> list = new ArrayList<>();
        if (input != null)   list.add(input);
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

        gfx.drawString(font, Component.literal("§e★" + starLevel + " " + essenceType),
                62, 2, 0xFFFFFF, true);
        gfx.drawString(font, Component.literal("→"), 82, 22, 0xFF404040, false);

        if (buttonsDirty || (sentinelButton != null && !screen.children().contains(sentinelButton))) {
            addButtons(screen, pos);
            buttonsDirty = false;
        }
    }

    private void addButtons(RecipeViewScreen screen, RecipePosition pos) {
        sentinelButton = SkyblockRecipeUtil.addWikiButton(screen, wikiUrls, pos.left(), pos.top() + 56);
    }

    @Override
    public int getPriority() { return SkyblockRecipePriority.ESSENCE_UPGRADE; }
}