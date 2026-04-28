package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.npc;

import com.github.kd_gaming1.skyblockenhancements.repo.item.ItemStackBuilder;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItem;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItemRegistry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Matches a clicked NPC head stack to recipes belonging to a specific NPC by comparing the
 * stack's {@code CUSTOM_NAME} against the registered NPC item's display name.
 *
 * <p>NPC heads all share the same Minecraft item type, so the display name is the only reliable
 * discriminator when looking up craft references in the recipe viewer sidebar.
 *
 * <p>To avoid rebuilding {@link ItemStack}s on every match call, the registered display name
 * {@link Component} is cached per NPC id and invalidated on repo reload.
 */
final class NpcNameMatcher {

    private NpcNameMatcher() {}

    /** npcId → registered display name Component. Cleared on repo reload. */
    private static final Map<String, Component> DISPLAY_NAME_CACHE = new ConcurrentHashMap<>();

    static boolean matches(ItemStack clickedStack, String npcId) {
        if (npcId.isEmpty()) return false;

        Component registeredName = DISPLAY_NAME_CACHE.get(npcId);
        if (registeredName == null) {
            NeuItem npcItem = NeuItemRegistry.get(npcId);
            if (npcItem == null) return false;

            registeredName = ItemStackBuilder.build(npcItem).get(DataComponents.CUSTOM_NAME);
            if (registeredName == null) return false;

            DISPLAY_NAME_CACHE.put(npcId, registeredName);
        }

        Component clickedName = clickedStack.get(DataComponents.CUSTOM_NAME);
        return clickedName != null && clickedName.equals(registeredName);
    }

    /** Clears the display-name cache. Must be called on every repo reload. */
    static void clearCache() {
        DISPLAY_NAME_CACHE.clear();
    }
}
