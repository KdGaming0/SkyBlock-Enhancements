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

/**
 * NPC info layout: 100px tall — location header, up to ~6 lore lines, then a button row.
 *
 * <p>Craft references are only the NPCs without shop recipes; shop NPCs are reached via the
 * shop page's "NPC Info" button, so surfacing them here would create duplicates in the sidebar.
 */
public class SkyblockNpcInfoRecipeType implements ReliableClientRecipeType {

    public static final SkyblockNpcInfoRecipeType INSTANCE = new SkyblockNpcInfoRecipeType();

    private final ItemStack icon = new ItemStack(Items.PLAYER_HEAD);

    private List<ItemStack> cachedReferences = null;

    @Override public Component  getDisplayName()   { return Component.literal("SkyBlock NPC"); }
    @Override public int        getDisplayWidth()  { return 130; }
    @Override public int        getDisplayHeight() { return 100; }
    @Override public Identifier getGuiTexture()    { return null; }
    @Override public int        getSlotCount()     { return 1; }
    @Override public ItemStack  getIcon()          { return icon; }

    @Override
    public void placeSlots(RecipeViewMenu.SlotDefinition def) {
        def.addItemSlot(0, 2, 2);
    }

    @Override
    public Identifier getId() {
        return Identifier.fromNamespaceAndPath("skyblock_enhancements", "skyblock_npc_info");
    }

    public void clearCache() {
        cachedReferences = null;
    }

    @Override
    public List<ItemStack> getCraftReferences() {
        if (!NeuItemRegistry.isLoaded()) return List.of();
        if (cachedReferences != null) return cachedReferences;

        List<ItemStack> npcs = new ArrayList<>();
        for (NeuItem item : NeuItemRegistry.getAll().values()) {
            if (item.internalName.endsWith("_NPC") && !item.hasNpcShopRecipes()) {
                ItemStack stack = ItemStackBuilder.build(item);
                if (!stack.isEmpty()) npcs.add(stack);
            }
        }
        cachedReferences = npcs;
        return npcs;
    }

    @Override
    public ReferenceCondition getCraftReferenceCondition() {
        return (npcStack, recipe) -> {
            if (!(recipe instanceof SkyblockNpcInfoClientRecipe infoRecipe)) return false;
            return NpcNameMatcher.matches(npcStack, infoRecipe.getNpcId());
        };
    }
}