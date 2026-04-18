package com.github.kd_gaming1.skyblockenhancements.repo;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItem;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItemRegistry;

/**
 * Scans the loaded {@link NeuItemRegistry} and logs a full breakdown of every recipe type found
 * in the NEU repo — counts, field names, and one concrete example per type.
 *
 * <p>Gated behind {@link SkyblockEnhancementsConfig#enableRecipeDiagnostics} so it only runs
 * when explicitly enabled by the user (useful for development, not needed in production).
 */
public final class RecipeDiagnostic {

    private static final String TAG = "[SBE Recipe Diagnostic] ";

    private RecipeDiagnostic() {}

    /** Runs the diagnostic if enabled in config. Safe to call from any thread. */
    public static void run() {
        if (!SkyblockEnhancementsConfig.enableRecipeDiagnostics) return;

        Collection<NeuItem> items = NeuItemRegistry.getAll().values();

        int totalItems = items.size();
        int withLegacyRecipe = 0;
        int withRecipesArray = 0;
        int noRecipeAtAll = 0;
        int missingTypeField = 0;

        // type → count of recipe entries with that type
        Map<String, Integer> typeCounts = new TreeMap<>();
        // type → one representative NeuItem for the example dump
        Map<String, NeuItem> typeExamples = new LinkedHashMap<>();
        // type → union of all top-level field names seen across recipes of that type
        Map<String, Set<String>> typeFields = new TreeMap<>();

        for (NeuItem item : items) {
            boolean hasLegacy = item.recipe != null && !item.recipe.isEmpty();
            boolean hasModern = item.recipes != null && !item.recipes.isEmpty();

            if (hasLegacy) withLegacyRecipe++;
            if (hasModern) withRecipesArray++;
            if (!hasLegacy && !hasModern) noRecipeAtAll++;

            if (!hasModern) continue;

            for (JsonObject recipe : item.recipes) {
                String type;
                if (!recipe.has("type")) {
                    missingTypeField++;
                    type = "(no type field)";
                } else {
                    type = recipe.get("type").getAsString();
                }

                typeCounts.merge(type, 1, Integer::sum);
                typeFields.computeIfAbsent(type, k -> new TreeSet<>()).addAll(fieldNames(recipe));
                typeExamples.putIfAbsent(type, item);
            }
        }

        logReport(totalItems, withLegacyRecipe, withRecipesArray, noRecipeAtAll,
                missingTypeField, typeCounts, typeFields, typeExamples);
    }

    private static void logReport(
            int totalItems, int withLegacyRecipe, int withRecipesArray, int noRecipeAtAll,
            int missingTypeField, Map<String, Integer> typeCounts,
            Map<String, Set<String>> typeFields, Map<String, NeuItem> typeExamples) {

        String sep = "=".repeat(64);

        LOGGER.info("{}{}", TAG, sep);
        LOGGER.info("{}NEU repo recipe breakdown", TAG);
        LOGGER.info("{}{}", TAG, sep);
        LOGGER.info("{}  Total items in registry : {}", TAG, totalItems);
        LOGGER.info("{}  Items with legacy recipe : {}", TAG, withLegacyRecipe);
        LOGGER.info("{}  Items with recipes array : {}", TAG, withRecipesArray);
        LOGGER.info("{}  Items with no recipe     : {}", TAG, noRecipeAtAll);
        LOGGER.info("{}  Entries missing 'type'   : {}", TAG, missingTypeField);
        LOGGER.info("{}{}", TAG, sep);

        int maxLen = typeCounts.keySet().stream().mapToInt(String::length).max().orElse(10);
        LOGGER.info("{}  Recipe type counts:", TAG);
        for (Map.Entry<String, Integer> entry : typeCounts.entrySet()) {
            LOGGER.info("{}    {}", TAG, String.format("%-" + maxLen + "s : %d",
                    entry.getKey(), entry.getValue()));
        }

        LOGGER.info("{}{}", TAG, sep);
        LOGGER.info("{}  Fields seen per type:", TAG);
        for (Map.Entry<String, Set<String>> entry : typeFields.entrySet()) {
            LOGGER.info("{}    [{}]  {}", TAG, entry.getKey(), entry.getValue());
        }

        LOGGER.info("{}{}", TAG, sep);
        LOGGER.info("{}  Example item per type:", TAG);
        for (Map.Entry<String, NeuItem> entry : typeExamples.entrySet()) {
            String type = entry.getKey();
            NeuItem item = entry.getValue();
            JsonObject exampleRecipe = findRecipeOfType(item, type);

            LOGGER.info("{}    [{}]", TAG, type);
            LOGGER.info("{}      item        : {}", TAG, item.internalName);
            LOGGER.info("{}      displayName : {}", TAG, item.displayName);
            if (exampleRecipe != null) {
                LOGGER.info("{}      recipe JSON : {}", TAG, exampleRecipe);
            }
        }

        LOGGER.info("{}{}", TAG, sep);
        LOGGER.info("{}Done. Distinct modern recipe types: {}", TAG, typeCounts.size());
        LOGGER.info("{}{}", TAG, sep);
    }

    private static Set<String> fieldNames(JsonObject obj) {
        Set<String> names = new TreeSet<>();
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            names.add(e.getKey());
        }
        return names;
    }

    private static JsonObject findRecipeOfType(NeuItem item, String type) {
        if (item.recipes == null) return null;
        for (JsonObject r : item.recipes) {
            if ("(no type field)".equals(type) && !r.has("type")) return r;
            if (r.has("type") && type.equals(r.get("type").getAsString())) return r;
        }
        return null;
    }
}