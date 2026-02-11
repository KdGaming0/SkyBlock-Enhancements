package com.github.kd_gaming1.skyblockenhancements.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

/**
 * Utility for loading and caching enchant configuration from a JSON file.
 *
 * <p>Reads a JSON file that matches the {@code EnchantData} structure using Gson,
 * caches the parsed result in-memory and exposes convenience accessors.</p>
 *
 * Errors while reading/parsing are logged and an empty {@code EnchantData} is returned.</p>
 */
public class JsonLookup {
    private static final Gson GSON = new GsonBuilder().create();

    private static volatile EnchantData cached;

    public EnchantData getData(Path location) {
        try {
            EnchantData local = cached;
            if (local != null) {
                return local;
            }

            try (Reader reader = Files.newBufferedReader(location)) {
                EnchantData loaded = GSON.fromJson(reader, EnchantData.class);
                if (loaded == null) loaded = new EnchantData();

                cached = loaded;
                return loaded;
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load enchants json from {}", location, e);
            return new EnchantData();
        }
    }

    public static void clearCache() {
        cached = null;
    }

    public List<String> getEnchants(String type, Path location) {
        EnchantData data = getData(location);
        if (data.enchants == null) return Collections.emptyList();

        List<String> result = data.enchants.get(type.toUpperCase());
        return result != null ? result : Collections.emptyList();
    }

    public List<List<String>> getEnchantPools(Path location) {
        EnchantData data = getData(location);
        if (data.enchant_pools == null) return Collections.emptyList();
        return data.enchant_pools;
    }

    public static class EnchantData {
        public Map<String, List<String>> enchants;
        public List<List<String>> enchant_pools;
    }
}