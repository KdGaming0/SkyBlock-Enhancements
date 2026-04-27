package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.drops;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Drop-recipe view: preview box + centered mob name caption on top, 4×3 drop grid below,
 * wiki button at the bottom. Coordinates are local to the recipe display.
 */
public class SkyblockDropsRecipeType implements ReliableClientRecipeType {

    public static final SkyblockDropsRecipeType INSTANCE = new SkyblockDropsRecipeType();

    static final int SLOT = 18;
    static final int COLS = 4;
    static final int ROWS = 3;

    static final int PREVIEW_BOX_SIZE = 36;
    static final int PREVIEW_BOX_TOP = 4;
    static final int NAME_CAPTION_TOP = PREVIEW_BOX_TOP + PREVIEW_BOX_SIZE + 3;

    static final int SLOT_GRID_TOP = NAME_CAPTION_TOP + 12;
    static final int SLOT_GRID_LEFT = 6;

    static final int WIKI_BUTTON_HEIGHT = 20;
    static final int WIKI_BUTTON_MARGIN_TOP = 4;
    static final int WIKI_BUTTON_TOP = SLOT_GRID_TOP + ROWS * SLOT + WIKI_BUTTON_MARGIN_TOP;

    private static final int DISPLAY_WIDTH = COLS * SLOT + SLOT_GRID_LEFT * 2;
    private static final int DISPLAY_HEIGHT = WIKI_BUTTON_TOP + WIKI_BUTTON_HEIGHT + 2;

    private final ItemStack icon = new ItemStack(Items.DIAMOND_SWORD);

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

    public static int displayWidth()  { return DISPLAY_WIDTH; }
}