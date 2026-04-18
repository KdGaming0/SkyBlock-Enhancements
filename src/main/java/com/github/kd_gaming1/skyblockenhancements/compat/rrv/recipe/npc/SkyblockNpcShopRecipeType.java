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

/** 5 cost slots + 1 result; craft-references are NPCs with at least one shop recipe. */
public class SkyblockNpcShopRecipeType implements ReliableClientRecipeType {

    public static final SkyblockNpcShopRecipeType INSTANCE = new SkyblockNpcShopRecipeType();

    private static final int SLOT = 18;

    private final ItemStack icon = new ItemStack(Items.EMERALD);

    /** Lazily populated on first access — rebuilt when {@link #clearCache()} fires on repo reload. */
    private List<ItemStack> cachedNpcReferences = null;

    @Override public Component  getDisplayName()   { return Component.literal("SkyBlock NPC Shop"); }
    @Override public int        getDisplayWidth()  { return 120; }
    @Override public int        getDisplayHeight() { return 66; }
    @Override public Identifier getGuiTexture()    { return null; }
    @Override public int        getSlotCount()     { return 6; }
    @Override public ItemStack  getIcon()          { return icon; }

    @Override
    public void placeSlots(RecipeViewMenu.SlotDefinition def) {
        for (int i = 0; i < 5; i++) {
            def.addItemSlot(i, i * SLOT, 21);
        }
        def.addItemSlot(5, 102, 21);
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