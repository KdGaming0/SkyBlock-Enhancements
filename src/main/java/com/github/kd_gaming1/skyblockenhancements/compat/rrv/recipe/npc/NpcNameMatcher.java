package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.npc;

import com.github.kd_gaming1.skyblockenhancements.repo.item.ItemStackBuilder;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItem;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItemRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Matches a clicked NPC head stack to recipes belonging to a specific NPC by comparing the
 * stack's {@code CUSTOM_NAME} against the registered NPC item's display name.
 *
 * <p>NPC heads all share the same Minecraft item type, so the display name is the only reliable
 * discriminator when looking up craft references in the recipe viewer sidebar.
 */
final class NpcNameMatcher {

    private NpcNameMatcher() {}

    static boolean matches(ItemStack clickedStack, String npcId) {
        if (npcId.isEmpty()) return false;

        NeuItem npcItem = NeuItemRegistry.get(npcId);
        if (npcItem == null) return false;

        Component clickedName  = clickedStack.get(DataComponents.CUSTOM_NAME);
        Component registeredName = ItemStackBuilder.build(npcItem).get(DataComponents.CUSTOM_NAME);
        return clickedName != null && clickedName.equals(registeredName);
    }
}