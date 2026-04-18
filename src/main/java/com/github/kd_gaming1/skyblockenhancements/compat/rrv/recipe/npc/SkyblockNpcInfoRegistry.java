package com.github.kd_gaming1.skyblockenhancements.compat.rrv.recipe.npc;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live NPC-ID → info-recipe lookup so the shop page can open its sibling info card without
 * walking RRV's cache.
 *
 * <p>Populated during recipe wrapper instantiation (see {@code SkyblockRrvClientPlugin}) and
 * cleared before every injection pass so stale entries from an earlier repo version don't linger.
 */
public final class SkyblockNpcInfoRegistry {

    private static final Map<String, SkyblockNpcInfoClientRecipe> REGISTRY = new ConcurrentHashMap<>();

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