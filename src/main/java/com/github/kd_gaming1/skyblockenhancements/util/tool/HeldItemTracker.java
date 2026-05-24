package com.github.kd_gaming1.skyblockenhancements.util.tool;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/**
 * Tracks the player's held item and caches the result of tool detection + stat
 * extraction so that repeated queries don't re-parse lore.
 *
 * <p>Invalidation happens when:
 * <ul>
 *   <li>The player's selected hotbar slot changes</li>
 *   <li>The held item's components change (e.g. drill fuel consumed)</li>
 * </ul>
 *
 * <p>Call {@link #register()} once during mod initialisation.
 */
public final class HeldItemTracker {

    private HeldItemTracker() {}

    // ── Cached state ──────────────────────────────────────────────────────────

    private static volatile ToolInfo cachedInfo = ToolInfo.NONE;
    private static int lastSlot = -1;
    private static int lastItemHash = 0;
    private static boolean registered = false;

    // ── Public API ────────────────────────────────────────────────────────────

    /** Registers the tick listener. Idempotent. */
    public static void register() {
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(HeldItemTracker::onClientTick);
    }

    /**
     * Returns the cached {@link ToolInfo} for the currently held item.
     * Updated automatically each tick if the held item changed.
     *
     * <p>Never returns {@code null} — returns {@link ToolInfo#NONE} when no
     * tool is held.
     */
    public static ToolInfo getToolInfo() {
        return cachedInfo;
    }

    /**
     * Forces a refresh of the cached tool info on the next tick.
     * Call this if you know the held item's data changed (e.g. after applying
     * an enchant or drill upgrade).
     */
    public static void invalidate() {
        lastItemHash = 0;
    }

    // ── Tick handler ──────────────────────────────────────────────────────────

    private static void onClientTick(Minecraft client) {
        if (client.player == null) {
            cachedInfo = ToolInfo.NONE;
            lastSlot = -1;
            lastItemHash = 0;
            return;
        }

        int currentSlot = client.player.getInventory().getSelectedSlot();
        ItemStack held = client.player.getMainHandItem();
        int currentHash = computeHash(held);

        if (currentSlot != lastSlot || currentHash != lastItemHash) {
            lastSlot = currentSlot;
            lastItemHash = currentHash;
            cachedInfo = ToolInspector.inspect(held);
        }
    }

    // ── Item hashing — 1.21+ components-based ─────────────────────────────────

    /**
     * Computes a fast hash of an item stack that changes when the item type
     * or component data changes. Used for cache invalidation.
     *
     * <p>In 1.21+ we use {@link ItemStack#hashCode()} which accounts for
     * the item type, count, and all data components.
     */
    private static int computeHash(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        return stack.hashCode();
    }
}
