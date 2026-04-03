package com.github.kd_gaming1.skyblockenhancements.compat.rrv.npc;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import com.github.kd_gaming1.skyblockenhancements.repo.ItemStackBuilder;
import com.github.kd_gaming1.skyblockenhancements.repo.NeuItem;
import com.github.kd_gaming1.skyblockenhancements.repo.NeuItemRegistry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Up to 5 cost slots → 1 result slot. Slots 0–4 = costs, slot 5 = result. */
public class SkyblockNpcShopRecipeType implements ReliableClientRecipeType {

    public static final SkyblockNpcShopRecipeType INSTANCE = new SkyblockNpcShopRecipeType();

    private static final int SLOT = 18;

    @Override
    public Component getDisplayName() {
        return Component.literal("SkyBlock NPC Shop");
    }

    @Override
    public int getDisplayWidth() {
        return 120;
    }

    @Override
    public int getDisplayHeight() {
        return 40;
    }

    @Override
    public Identifier getGuiTexture() {
        return null;
    }

    @Override
    public int getSlotCount() {
        return 6;
    }

    @Override
    public void placeSlots(RecipeViewMenu.SlotDefinition def) {
        for (int i = 0; i < 5; i++) {
            def.addItemSlot(i, i * SLOT, 11);
        }
        def.addItemSlot(5, 102, 11);
    }

    @Override
    public Identifier getId() {
        return Identifier.fromNamespaceAndPath("skyblock_enhancements", "skyblock_npc_shop");
    }

    @Override
    public ItemStack getIcon() {
        return new ItemStack(Items.EMERALD);
    }

    /**
     * Returns one ItemStack per known NPC. RRV indexes these into {@code byItemIngredient} so that
     * clicking any NPC item in the index opens this recipe type. The
     * {@link #getCraftReferenceCondition()} then filters down to only that specific NPC's recipes.
     */
    @Override
    public List<ItemStack> getCraftReferences() {
        if (!NeuItemRegistry.isLoaded()) return List.of();

        List<ItemStack> npcs = new ArrayList<>();
        for (NeuItem item : NeuItemRegistry.getAll().values()) {
            if (item.internalName.endsWith("_NPC")) {
                ItemStack stack = ItemStackBuilder.build(item);
                if (!stack.isEmpty()) npcs.add(stack);
            }
        }
        return npcs;
    }

    /**
     * Matches a craft-reference (NPC item) to a recipe only if the recipe belongs to that NPC.
     * Comparison is by the NPC item's {@code CUSTOM_NAME} component against the recipe's npcId
     * display name, since NPC items are player heads sharing the same vanilla item type.
     */
    @Override
    public ReferenceCondition getCraftReferenceCondition() {
        return (npcStack, recipe) -> {
            if (!(recipe instanceof SkyblockNpcShopClientRecipe shopRecipe)) return false;
            String npcId = shopRecipe.getNpcId();
            if (npcId.isEmpty()) return false;

            // Look up the NPC item by its internalName and compare stacks
            NeuItem npcItem = NeuItemRegistry.get(npcId);
            if (npcItem == null) return false;

            ItemStack npcItemStack = ItemStackBuilder.build(npcItem);
            // Use CUSTOM_NAME as the discriminator — NPC items are heads with unique names
            Component clickedName = npcStack.get(DataComponents.CUSTOM_NAME);
            Component npcName = npcItemStack.get(DataComponents.CUSTOM_NAME);
            return clickedName != null && clickedName.equals(npcName);
        };
    }
}