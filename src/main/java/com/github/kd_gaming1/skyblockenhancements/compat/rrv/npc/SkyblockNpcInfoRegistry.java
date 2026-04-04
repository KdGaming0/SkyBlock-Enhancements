package com.github.kd_gaming1.skyblockenhancements.compat.rrv.npc;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live reference to every {@link SkyblockNpcInfoClientRecipe} so the NPC-shop page can
 * navigate directly to the matching info card without re-querying the RRV cache.
 *
 * <p>Populated during cache spoofing via the {@code SkyblockNpcInfoServerRecipe.TYPE} wrapper.
 * Cleared before each spoof so stale entries from previous refreshes don't linger.
 */
public final class SkyblockNpcInfoRegistry {

    private static final Map<String, SkyblockNpcInfoClientRecipe> REGISTRY =
            new ConcurrentHashMap<>();

    private SkyblockNpcInfoRegistry() {}

    public static void register(String npcId, SkyblockNpcInfoClientRecipe recipe) {
        REGISTRY.put(npcId, recipe);
    }

    public static SkyblockNpcInfoClientRecipe get(String npcId) {
        return REGISTRY.get(npcId);
    }

    public static void clear() {
        REGISTRY.clear();
    }
}