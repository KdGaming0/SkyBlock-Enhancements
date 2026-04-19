package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.npc;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import com.github.kd_gaming1.skyblockenhancements.repo.item.ItemStackBuilder;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItem;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItemRegistry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** 2×5 cost grid + 1 result; craft-references are NPCs with at least one shop recipe. */
public class SkyblockNpcShopRecipeType implements ReliableClientRecipeType {

    public static final SkyblockNpcShopRecipeType INSTANCE = new SkyblockNpcShopRecipeType();

    private static final int SLOT = 18;
    private static final int COST_COLS = 5;
    private static final int COST_ROWS = 2;
    /** Top of cost grid — leaves 12px header row for the NPC display name. */
    private static final int GRID_TOP = 12;
    /** Result slot index (costs fill 0..9). */
    private static final int RESULT_INDEX = COST_COLS * COST_ROWS;
    /** Result sits to the right of the cost grid, vertically centered across the two rows. */
    private static final int RESULT_X = COST_COLS * SLOT + 6;
    private static final int RESULT_Y = GRID_TOP + SLOT / 2;

    private final ItemStack icon = new ItemStack(Items.EMERALD);

    /** Lazily populated on first access — rebuilt when {@link #clearCache()} fires on repo reload. */
    private List<ItemStack> cachedNpcReferences = null;

    @Override public Component  getDisplayName()   { return Component.literal("SkyBlock NPC Shop"); }
    @Override public int        getDisplayWidth()  { return RESULT_X + SLOT; }
    @Override public int        getDisplayHeight() { return GRID_TOP + COST_ROWS * SLOT + 18; }
    @Override public Identifier getGuiTexture()    { return null; }
    @Override public int        getSlotCount()     { return RESULT_INDEX + 1; }
    @Override public ItemStack  getIcon()          { return icon; }

    @Override
    public void placeSlots(RecipeViewMenu.SlotDefinition def) {
        for (int row = 0; row < COST_ROWS; row++) {
            for (int col = 0; col < COST_COLS; col++) {
                def.addItemSlot(row * COST_COLS + col, col * SLOT, GRID_TOP + row * SLOT);
            }
        }
        def.addItemSlot(RESULT_INDEX, RESULT_X, RESULT_Y);
    }

    @Override
    public Identifier getId() {
        return Identifier.fromNamespaceAndPath("skyblock_enhancements", "skyblock_npc_shop");
    }

    public void clearCache() {
        cachedNpcReferences = null;
    }

    @Override
    public List<ItemStack> getCraftReferences() {
        if (!NeuItemRegistry.isLoaded()) return List.of();
        if (cachedNpcReferences != null) return cachedNpcReferences;

        List<ItemStack> npcs = new ArrayList<>();
        for (NeuItem item : NeuItemRegistry.getAll().values()) {
            if (item.internalName.endsWith("_NPC") && item.hasNpcShopRecipes()) {
                ItemStack stack = ItemStackBuilder.build(item);
                if (!stack.isEmpty()) npcs.add(stack);
            }
        }
        cachedNpcReferences = npcs;
        return npcs;
    }

    @Override
    public ReferenceCondition getCraftReferenceCondition() {
        return (npcStack, recipe) -> {
            if (!(recipe instanceof SkyblockNpcShopClientRecipe shopRecipe)) return false;
            return NpcNameMatcher.matches(npcStack, shopRecipe.getNpcId());
        };
    }
}