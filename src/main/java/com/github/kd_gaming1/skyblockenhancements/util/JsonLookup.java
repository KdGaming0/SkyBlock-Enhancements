package com.github.kd_gaming1.skyblockenhancements.util;

import com.google.gson.Gson;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** An immutable, validated enchant data snapshot. Parsing is done by the background loader. */
public final class JsonLookup {
    private static final Gson GSON = new Gson();

    private final Map<String, List<String>> enchants;
    private final List<List<String>> pools;
    private final Map<String, Integer> maxLevels;

    private JsonLookup(EnchantData data) {
        Map<String, List<String>> types = new HashMap<>();
        data.enchants.forEach((type, names) -> types.put(type, List.copyOf(names)));
        enchants = Map.copyOf(types);
        pools = data.enchant_pools.stream().map(List::copyOf).toList();
        Map<String, Integer> levels = new HashMap<>();
        data.enchants_xp_cost.forEach((id, costs) -> {
            // Copying also rejects null entries before this snapshot can be published.
            levels.put(id, List.copyOf(costs).size());
        });
        maxLevels = Map.copyOf(levels);
    }

    public static JsonLookup parse(String json) {
        EnchantData data = GSON.fromJson(json, EnchantData.class);
        if (data == null || data.enchants == null || data.enchants.isEmpty()
                || data.enchant_pools == null || data.enchants_xp_cost == null
                || data.enchants_xp_cost.isEmpty()) {
            throw new IllegalArgumentException("Incomplete enchant data");
        }
        return new JsonLookup(data);
    }

    public List<String> getEnchants(String type) {
        return enchants.getOrDefault(type.toUpperCase(Locale.ROOT), List.of());
    }

    public List<List<String>> getEnchantPools() {
        return pools;
    }

    public int getMaxLevel(String enchantId) {
        return maxLevels.getOrDefault(enchantId, -1);
    }

    private static final class EnchantData {
        Map<String, List<String>> enchants;
        List<List<String>> enchant_pools;
        Map<String, List<Integer>> enchants_xp_cost;
    }
}
