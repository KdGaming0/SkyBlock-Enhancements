package com.github.kd_gaming1.skyblockenhancements.compat.rrv.npc;

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

/**
 * Recipe type for NPC shop recipes. Up to 5 cost slots → 1 result slot.
 */
public class SkyblockNpcShopRecipeType implements ReliableClientRecipeType {

    public static final SkyblockNpcShopRecipeType INSTANCE = new SkyblockNpcShopRecipeType();

    private static final int SLOT = 18;

    /** Cached icon — allocated once, never mutated. */
    private final ItemStack icon = new ItemStack(Items.EMERALD);

    private List<ItemStack> cachedNpcReferences = null;

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
        return 66;
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
            def.addItemSlot(i, i * SLOT, 21);
        }
        def.addItemSlot(5, 102, 21);
    }

    @Override
    public Identifier getId() {
        return Identifier.fromNamespaceAndPath("skyblock_enhancements", "skyblock_npc_shop");
    }

    @Override
    public ItemStack getIcon() {
        return icon;
    }

    public void clearCache() {
        cachedNpcReferences = null;
    }

    /**
     * Only NPCs that actually have shop recipes serve as craft references — prevents
     * non-shop NPCs from appearing in the sidebar with zero matching recipes.
     */
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

    /**
     * Matches a craft-reference (NPC head) to shop recipes belonging to that specific NPC.
     * Uses {@code CUSTOM_NAME} comparison because all NPC heads share the same item type.
     */
    @Override
    public ReferenceCondition getCraftReferenceCondition() {
        return (npcStack, recipe) -> {
            if (!(recipe instanceof SkyblockNpcShopClientRecipe shopRecipe)) return false;
            return matchesNpcByName(npcStack, shopRecipe.getNpcId());
        };
    }

    /**
     * Shared NPC name-matching logic used by both shop and info recipe types.
     * Compares the clicked stack's {@code CUSTOM_NAME} against the registered NPC item's
     * display name.
     */
    static boolean matchesNpcByName(ItemStack clickedStack, String npcId) {
        if (npcId.isEmpty()) return false;

        NeuItem npcItem = NeuItemRegistry.get(npcId);
        if (npcItem == null) return false;

        Component clickedName = clickedStack.get(DataComponents.CUSTOM_NAME);
        Component npcName = ItemStackBuilder.build(npcItem).get(DataComponents.CUSTOM_NAME);
        return clickedName != null && clickedName.equals(npcName);
    }
}