package com.github.kd_gaming1.skyblockenhancements.feature.missingenchants;

import com.github.kd_gaming1.skyblockenhancements.util.JsonLookup;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

// import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

/**
 * Determines which enchants are missing from an item given its type and current enchants.
 *
 * <p>Skyblock enchants can be mutually exclusive — only one enchant from a given pool
 * (e.g. Sharpness vs. Smite) may be applied at a time. The resolver loads these
 * pools from {@code enchants.json} once and maps each enchant ID to its pool index.
 * A {@link BitSet} of satisfied pools is then used to efficiently skip enchants whose
 * pool is already covered by something the item already has.
 *
 * <p>Ultimate enchants are treated separately: if the item type supports any ultimate
 * enchant and the item has none, a single generic "Ultimate enchant" entry is added
 * rather than listing every ultimate by name.
 *
 * <p>Pretty-name formatting (e.g. {@code turbo_wheat} → {@code Turbo-Wheat}) is cached
 * per enchant ID so it is only computed once across all tooltip calls.
 */
final class MissingEnchantResolver {

    private final JsonLookup lookup;
    private final Path enchantsJsonPath;

    // Flat map from enchant ID to its pool index, populated once from enchants.json.
    private final Map<String, List<Integer>> poolIdsByEnchant = new HashMap<>();
    private int poolCount = 0;
    private boolean poolsLoaded = false;

    // Pretty-name cache: enchant ID -> display name (e.g. "turbo_wheat" -> "Turbo-Wheat")
    private final Map<String, String> prettyNameCache = new HashMap<>(512);

    MissingEnchantResolver(JsonLookup lookup, Path enchantsJsonPath) {
        this.lookup = lookup;
        this.enchantsJsonPath = enchantsJsonPath;
    }

    List<String> findMissingEnchantNames(String itemType, Set<String> currentEnchants) {
        // LOGGER.info("Resolving missing enchants for {}", itemType);
        if (!loadPoolsIfNeeded()) return List.of();

        List<String> possibleEnchants = lookup.getEnchants(itemType, enchantsJsonPath);
        if (possibleEnchants.isEmpty()) return List.of();

        // Pre-compute which pools are already satisfied so isMissingEnchant can do a fast BitSet lookup.
        BitSet satisfiedPools = buildSatisfiedPools(currentEnchants);

        boolean itemSupportsUltimate = possibleEnchants.stream().anyMatch(this::isUltimateEnchant);
        boolean hasUltimateOnItem = hasUltimateEnchant(currentEnchants);

        ArrayList<String> missing = new ArrayList<>();

        for (String enchantId : possibleEnchants) {
            // Ultimates are evaluated separately below; skip them in the main loop.
            if (isUltimateEnchant(enchantId)) continue;

            if (isMissingEnchant(enchantId, currentEnchants, satisfiedPools)) {
                missing.add(toPrettyName(enchantId));
            }
        }

        // If any ultimate is possible but none is present, show a single generic prompt
        if (itemSupportsUltimate && !hasUltimateOnItem) {
            missing.add("Ultimate enchant");
        }

        missing.sort(String.CASE_INSENSITIVE_ORDER);
        return missing;
    }

    private boolean isUltimateEnchant(String enchantId) {
        return enchantId.startsWith("ultimate_");
    }

    void clearCaches() {
        poolIdsByEnchant.clear();
        poolCount = 0;
        poolsLoaded = false;
        prettyNameCache.clear();
    }

    private boolean loadPoolsIfNeeded() {
        if (poolsLoaded) return true;

        // File missing means the repo data hasn't been downloaded yet — try again next call.
        if (!Files.exists(enchantsJsonPath)) return false;

        List<List<String>> pools = lookup.getEnchantPools(enchantsJsonPath);
        if (pools == null || pools.isEmpty()) return false;

        poolIdsByEnchant.clear();
        poolCount = pools.size();

        for (int poolId = 0; poolId < pools.size(); poolId++) {
            for (String enchantId : pools.get(poolId)) {
                poolIdsByEnchant.computeIfAbsent(enchantId, k -> new ArrayList<>()).add(poolId);
            }
        }

        poolsLoaded = true;
        return true;
    }

    private BitSet buildSatisfiedPools(Set<String> currentEnchants) {
        BitSet satisfiedPools = new BitSet(poolCount);
        for (String enchantId : currentEnchants) {
            List<Integer> poolIds = poolIdsByEnchant.get(enchantId);
            if (poolIds != null) {
                for (int poolId : poolIds) satisfiedPools.set(poolId);
            }
        }
        return satisfiedPools;
    }

    private boolean isMissingEnchant(String enchantId, Set<String> currentEnchants, BitSet satisfiedPools) {
        if (currentEnchants.contains(enchantId)) return false;
        if (currentEnchants.contains("ultimate_one_for_all")) return false;

        List<Integer> poolIds = poolIdsByEnchant.get(enchantId);
        if (poolIds == null) return true;

        for (int poolId : poolIds) {
            // If this non-OFA pool is satisfied, the enchant isn't missing
            if (satisfiedPools.get(poolId)) return false;
        }

        return true;
    }

    private boolean hasUltimateEnchant(Set<String> currentEnchants) {
        for (String id : currentEnchants) {
            if (id.startsWith("ultimate_")) return true;
        }
        return false;
    }

    private String toPrettyName(String enchantId) {
        String cached = prettyNameCache.get(enchantId);
        if (cached != null) return cached;

        String name = formatEnchantName(enchantId);
        prettyNameCache.put(enchantId, name);
        return name;
    }

    private static String formatEnchantName(String enchantId) {
        String s = enchantId.toLowerCase(Locale.ROOT)
                .replace("ultimate_", "")
                .replace("turbo_", "turbo-")
                .replace('_', ' ')
                .trim();

        s = titleCase(s);
        return s.equalsIgnoreCase("pristine") ? "Prismatic" : s;
    }

    private static String titleCase(String input) {
        if (input.isEmpty()) return input;

        String[] parts = input.split("\\s+");
        StringBuilder out = new StringBuilder(input.length());

        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (p.isEmpty()) continue;

            if (i > 0) out.append(' ');
            out.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }

        return out.toString();
    }
}