package com.github.kd_gaming1.skyblockenhancements.compat.rrv.npc;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import com.github.kd_gaming1.skyblockenhancements.repo.ItemStackBuilder;
import com.github.kd_gaming1.skyblockenhancements.repo.NeuItem;
import com.github.kd_gaming1.skyblockenhancements.repo.NeuItemRegistry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Recipe type for NPC info cards. Shown when clicking an NPC item that has no shop recipes.
 * Shop NPCs are reached via the shop page's "NPC Info" button instead.
 *
 * <p>100 px tall: location header (~22 px), up to 6 lore lines (~60 px), and a
 * 18 px button row at the bottom.
 */
public class SkyblockNpcInfoRecipeType implements ReliableClientRecipeType {

    public static final SkyblockNpcInfoRecipeType INSTANCE = new SkyblockNpcInfoRecipeType();

    @Override
    public Component getDisplayName() {
        return Component.literal("SkyBlock NPC");
    }

    @Override
    public int getDisplayWidth() {
        return 130;
    }

    @Override
    public int getDisplayHeight() {
        return 100;
    }

    @Override
    public Identifier getGuiTexture() {
        return null;
    }

    @Override
    public int getSlotCount() {
        return 1;
    }

    @Override
    public void placeSlots(RecipeViewMenu.SlotDefinition def) {
        def.addItemSlot(0, 2, 2);
    }

    @Override
    public Identifier getId() {
        return Identifier.fromNamespaceAndPath("skyblock_enhancements", "skyblock_npc_info");
    }

    @Override
    public ItemStack getIcon() {
        return new ItemStack(Items.PLAYER_HEAD);
    }

    /**
     * Only non-shop NPCs appear as craft references — shop NPCs are reachable
     * via the shop page's "NPC Info" button instead of the sidebar.
     */
    @Override
    public List<ItemStack> getCraftReferences() {
        if (!NeuItemRegistry.isLoaded()) return List.of();

        List<ItemStack> npcs = new ArrayList<>();
        for (NeuItem item : NeuItemRegistry.getAll().values()) {
            if (item.internalName.endsWith("_NPC") && !item.hasNpcShopRecipes()) {
                ItemStack stack = ItemStackBuilder.build(item);
                if (!stack.isEmpty()) npcs.add(stack);
            }
        }
        return npcs;
    }

    /**
     * Delegates to the shared NPC name matcher — same logic as the shop type, just
     * comparing against a different client recipe class.
     */
    @Override
    public ReferenceCondition getCraftReferenceCondition() {
        return (npcStack, recipe) -> {
            if (!(recipe instanceof SkyblockNpcInfoClientRecipe infoRecipe)) return false;
            return SkyblockNpcShopRecipeType.matchesNpcByName(npcStack, infoRecipe.getNpcId());
        };
    }
}