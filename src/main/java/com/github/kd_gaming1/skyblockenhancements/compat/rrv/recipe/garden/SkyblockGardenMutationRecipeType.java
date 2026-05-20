package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.garden;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * RRV recipe type for SkyBlock garden mutations.
 *
 * <p>The card is kept to 146 px tall so that it fits inside RRV's 214 px viewport
 * ({@code 146 + 16 + 24 + 24 = 210 ≤ 214}).  Only the central 6×6 area of the
 * 9×9 expanded layout is exposed as slots; the actual {@code gridSize×gridSize}
 * mutation is centred inside that area.
 */
public class SkyblockGardenMutationRecipeType implements ReliableClientRecipeType {

    public static final SkyblockGardenMutationRecipeType INSTANCE = new SkyblockGardenMutationRecipeType();

    public static final int DISPLAY_WIDTH  = 140;
    public static final int DISPLAY_HEIGHT = 150;

    private static final int METADATA_PANEL_HEIGHT = 12;
    private static final int GRID_OFFSET_Y = METADATA_PANEL_HEIGHT + 1;
    private static final int CELL_SIZE = 16;
    private static final int CELL_GAP  = 0;
    private static final int GRID_SIZE = 6;

    private final ItemStack icon = new ItemStack(Items.WHEAT_SEEDS);
    private final List<ItemStack> craftReferences = List.of(icon);

    @Override public Component getDisplayName()   { return Component.literal("Mutation"); }
    @Override public int getDisplayWidth()        { return DISPLAY_WIDTH; }
    @Override public int getDisplayHeight()       { return DISPLAY_HEIGHT; }
    @Override public Identifier getGuiTexture()   { return null; }
    @Override public int getSlotCount()           { return GRID_SIZE * GRID_SIZE; }
    @Override public ItemStack getIcon()          { return icon; }
    @Override public List<ItemStack> getCraftReferences() { return craftReferences; }

    @Override
    public void placeSlots(RecipeViewMenu.SlotDefinition def) {
        int gridPixelSize = GRID_SIZE * CELL_SIZE + (GRID_SIZE - 1) * CELL_GAP;
        int startX = (DISPLAY_WIDTH - gridPixelSize) / 2;
        int startY = GRID_OFFSET_Y;

        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                int slotIndex = row * GRID_SIZE + col;
                int x = startX + col * (CELL_SIZE + CELL_GAP);
                int y = startY + row * (CELL_SIZE + CELL_GAP);
                def.addItemSlot(slotIndex, x, y);
            }
        }
    }

    @Override
    public Identifier getId() {
        return Identifier.fromNamespaceAndPath("skyblock_enhancements", "garden_mutation");
    }

    public static int metadataPanelHeight() { return METADATA_PANEL_HEIGHT; }
    public static int gridOffsetY()         { return GRID_OFFSET_Y; }
    public static int cellSize()            { return CELL_SIZE; }
    public static int cellGap()             { return CELL_GAP; }
    public static int gridSize()            { return GRID_SIZE; }
}
