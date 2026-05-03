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
 *
 * <p>Tall card layout that displays the full stat list for one (reforge, rarity) pair.
 * One card per page — necessary to fit all stats and the ability text without truncation.
 *
 * <p>Clicking a reforgable item matches via {@code redirectsAsResult} (rarity + item type).
 * Clicking a reforge stone matches via {@code redirectsAsIngredient} (stone internal name).
 */
public class SkyblockReforgeRecipeType implements ReliableClientRecipeType {

    public static final SkyblockReforgeRecipeType INSTANCE = new SkyblockReforgeRecipeType();

    /**
     * Width expanded from 132 to 156 so longer stat names
     * (e.g. "Bonus Attack Speed") fit without ellipsis.
     */
    public static final int DISPLAY_WIDTH = 156;

    /**
     * Height expanded from 66 to 146 to fit all stat lines:
     * header (30px) + ability line (9px) + up to 8 stat lines (72px) + padding + button.
     * <p>RRV viewport: 214px. One card: 146 + 16 + 24 + 24 = 210 ≤ 214.
     */
    public static final int DISPLAY_HEIGHT = 146;

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
