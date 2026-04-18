package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.essence;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Essence upgrade layout:
 * <ul>
 *   <li>Slot 0 — input item (left)</li>
 *   <li>Slot 1 — essence (center-top)</li>
 *   <li>Slots 2–5 — companion items (2×2 grid below essence)</li>
 *   <li>Slot 6 — output item (right)</li>
 * </ul>
 */
public class SkyblockEssenceUpgradeRecipeType implements ReliableClientRecipeType {

    public static final SkyblockEssenceUpgradeRecipeType INSTANCE = new SkyblockEssenceUpgradeRecipeType();

    private static final int SLOT = 18;

    private final ItemStack icon = new ItemStack(Items.EXPERIENCE_BOTTLE);
    private final List<ItemStack> craftReferences = List.of(icon);

    @Override public Component  getDisplayName()   { return Component.literal("Essence Upgrade"); }
    @Override public int        getDisplayWidth()  { return 130; }
    @Override public int        getDisplayHeight() { return 68; }
    @Override public Identifier getGuiTexture()    { return null; }
    @Override public int        getSlotCount()     { return 7; }
    @Override public ItemStack  getIcon()          { return icon; }
    @Override public List<ItemStack> getCraftReferences() { return craftReferences; }

    @Override
    public void placeSlots(RecipeViewMenu.SlotDefinition def) {
        def.addItemSlot(0, 0, 18);
        def.addItemSlot(1, 30, 0);
        def.addItemSlot(2, 24, SLOT + 4);
        def.addItemSlot(3, 24 + SLOT, SLOT + 4);
        def.addItemSlot(4, 24, SLOT * 2 + 4);
        def.addItemSlot(5, 24 + SLOT, SLOT * 2 + 4);
        def.addItemSlot(6, 106, 18);
    }

    @Override
    public Identifier getId() {
        return Identifier.fromNamespaceAndPath("skyblock_enhancements", "skyblock_essence_upgrade");
    }
}