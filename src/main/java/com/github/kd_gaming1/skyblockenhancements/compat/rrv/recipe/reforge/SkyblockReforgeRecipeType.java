package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.reforge;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * RRV recipe type for SkyBlock reforges (blacksmith + reforge stones).
 * Compact layout: one item slot (stone or empty for blacksmith) + text stats.
 */
public class SkyblockReforgeRecipeType implements ReliableClientRecipeType {

    public static final SkyblockReforgeRecipeType INSTANCE = new SkyblockReforgeRecipeType();

    public static final int DISPLAY_WIDTH = 140;
    public static final int DISPLAY_HEIGHT = 90;

    private static final int SLOT_X = 4;
    private static final int SLOT_Y = 4;

    private final ItemStack icon = new ItemStack(Items.ANVIL);
    private final List<ItemStack> craftReferences = List.of(icon);

    @Override public Component getDisplayName()   { return Component.literal("SkyBlock Reforge"); }
    @Override public int getDisplayWidth()        { return DISPLAY_WIDTH; }
    @Override public int getDisplayHeight()       { return DISPLAY_HEIGHT; }
    @Override public Identifier getGuiTexture()   { return null; }
    @Override public int getSlotCount()           { return 1; }
    @Override public ItemStack getIcon()          { return icon; }
    @Override public List<ItemStack> getCraftReferences() { return craftReferences; }

    @Override
    public void placeSlots(RecipeViewMenu.SlotDefinition def) {
        def.addItemSlot(0, SLOT_X, SLOT_Y);
    }

    @Override
    public Identifier getId() {
        return Identifier.fromNamespaceAndPath("skyblock_enhancements", "skyblock_reforge");
    }
}
