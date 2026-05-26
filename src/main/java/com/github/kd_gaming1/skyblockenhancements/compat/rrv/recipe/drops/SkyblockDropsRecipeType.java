package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.drops;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.render.RecipeLayoutConstants;
import net.minecraft.world.item.Items;

/**
 * Drop-recipe view: preview box + centered mob name caption on top, 7×4 drop grid below,
 * wiki button at the bottom. One size fits all mobs (up to 28 drops).
 *
 * <p>Display height is 150 px so that exactly one recipe fits per page inside RRV's
 * 214 px viewport ({@code 150 + 16 + 24 + 24 = 214}).
 */
public final class SkyblockDropsRecipeType implements ReliableClientRecipeType {

    public static final SkyblockDropsRecipeType INSTANCE = new SkyblockDropsRecipeType();

    private static final int SLOT = RecipeLayoutConstants.SLOT_SIZE;
    private static final int COLS = 7;
    private static final int ROWS = 4;

    static final int PREVIEW_BOX_SIZE = 36;
    static final int PREVIEW_BOX_TOP = 4;
    private static final int NAME_CAPTION_TOP = PREVIEW_BOX_TOP + PREVIEW_BOX_SIZE + 3;

    private static final int DISPLAY_WIDTH = 140;
    private static final int SLOT_GRID_TOP = NAME_CAPTION_TOP + 12;
    private static final int SLOT_GRID_LEFT = (DISPLAY_WIDTH - COLS * SLOT) / 2;

    private static final int WIKI_BUTTON_HEIGHT = 20;
    private static final int WIKI_BUTTON_MARGIN_TOP = 1;
    private static final int WIKI_BUTTON_TOP = SLOT_GRID_TOP + ROWS * SLOT + WIKI_BUTTON_MARGIN_TOP;

    private static final int DISPLAY_HEIGHT = WIKI_BUTTON_TOP + WIKI_BUTTON_HEIGHT + 2;

    private final ItemStack icon = new ItemStack(Items.DIAMOND_SWORD);

    private SkyblockDropsRecipeType() {}

    @Override public Component  getDisplayName()   { return Component.literal("SkyBlock Mob Drops"); }
    @Override public int        getDisplayWidth()  { return DISPLAY_WIDTH; }
    @Override public int        getDisplayHeight() { return DISPLAY_HEIGHT; }
    @Override public Identifier getGuiTexture()    { return null; }
    @Override public int        getSlotCount()     { return COLS * ROWS; }
    @Override public ItemStack  getIcon()          { return icon; }

    @Override
    public void placeSlots(RecipeViewMenu.SlotDefinition def) {
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                def.addItemSlot(
                        row * COLS + col,
                        col * SLOT + SLOT_GRID_LEFT,
                        row * SLOT + SLOT_GRID_TOP);
            }
        }
    }

    @Override
    public Identifier getId() {
        return Identifier.fromNamespaceAndPath("skyblock_enhancements", "skyblock_drops");
    }

    static int wikiButtonTop() {
        return WIKI_BUTTON_TOP;
    }

    static int nameCaptionTop() {
        return NAME_CAPTION_TOP;
    }

    static int displayWidth() {
        return DISPLAY_WIDTH;
    }
}
