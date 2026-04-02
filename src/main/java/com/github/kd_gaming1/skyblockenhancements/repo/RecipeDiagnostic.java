package com.github.kd_gaming1.skyblockenhancements.repo;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.*;

/**
 * Scans the loaded {@link NeuItemRegistry} and logs a full breakdown of every recipe
 * type found in the NEU repo — including counts, field names, and one concrete example
 * per type so you know exactly what to parse for each category.
 *
 * <p>Call {@link #run()} once after the registry has been marked loaded, either from
 * {@link NeuRepoDownloader} after a fresh ZIP parse or after loading from cache.
 *
 * <p>Output goes to the game log at INFO level. Look for lines prefixed with
 * "[SBE Recipe Diagnostic]" to find it quickly.
 *
 * <p>Remove this class (or gate it behind a dev config flag) once you have implemented
 * all the recipe types you care about.
 */
public final class RecipeDiagnostic {

    private RecipeDiagnostic() {}

    /**
     * Runs the full diagnostic over every item currently in {@link NeuItemRegistry}.
     * Safe to call from any thread.
     */
    public static void run() {
        Collection<NeuItem> items = NeuItemRegistry.getAll().values();

        // ── Counters ────────────────────────────────────────────────────────────────

        int totalItems        = items.size();
        int withLegacyRecipe  = 0;   // top-level "recipe" object (A1-C3)
        int withRecipesArray  = 0;   // top-level "recipes" array (modern)
        int noRecipeAtAll     = 0;

        // type string → count of recipe entries with that type
        Map<String, Integer> typeCounts = new TreeMap<>();

        // type string → one representative NeuItem (for the example dump below)
        Map<String, NeuItem> typeExamples = new LinkedHashMap<>();

        // type string → set of all top-level field names seen across all recipes of that type
        Map<String, Set<String>> typeFields = new TreeMap<>();

        // Items that have a "recipes" entry missing the "type" field entirely
        int missingTypeField = 0;

        // ── Scan ────────────────────────────────────────────────────────────────────

        for (NeuItem item : items) {
            boolean hasLegacy  = item.recipe  != null && !item.recipe.isEmpty();
            boolean hasModern  = item.recipes != null && !item.recipes.isEmpty();

            if (hasLegacy)  withLegacyRecipe++;
            if (hasModern)  withRecipesArray++;
            if (!hasLegacy && !hasModern) noRecipeAtAll++;

            if (hasModern) {
                for (JsonObject recipe : item.recipes) {
                    if (!recipe.has("type")) {
                        missingTypeField++;
                        typeCounts.merge("(no type field)", 1, Integer::sum);
                        typeFields.computeIfAbsent("(no type field)", k -> new TreeSet<>())
                                .addAll(fieldNames(recipe));
                        typeExamples.putIfAbsent("(no type field)", item);
                        continue;
                    }

                    String type = recipe.get("type").getAsString();
                    typeCounts.merge(type, 1, Integer::sum);
                    typeFields.computeIfAbsent(type, k -> new TreeSet<>())
                            .addAll(fieldNames(recipe));
                    typeExamples.putIfAbsent(type, item);
                }
            }
        }

        // ── Report ──────────────────────────────────────────────────────────────────

        String sep = "=".repeat(64);

        LOGGER.info("[SBE Recipe Diagnostic] {}", sep);
        LOGGER.info("[SBE Recipe Diagnostic] NEU repo recipe breakdown");
        LOGGER.info("[SBE Recipe Diagnostic] {}", sep);
        LOGGER.info("[SBE Recipe Diagnostic]   Total items in registry : {}", totalItems);
        LOGGER.info("[SBE Recipe Diagnostic]   Items with legacy recipe : {} (top-level 'recipe' A1-C3 object)", withLegacyRecipe);
        LOGGER.info("[SBE Recipe Diagnostic]   Items with recipes array : {} (modern 'recipes' array)", withRecipesArray);
        LOGGER.info("[SBE Recipe Diagnostic]   Items with no recipe     : {}", noRecipeAtAll);
        LOGGER.info("[SBE Recipe Diagnostic]   Recipe entries missing 'type' field : {}", missingTypeField);
        LOGGER.info("[SBE Recipe Diagnostic] {}", sep);
        LOGGER.info("[SBE Recipe Diagnostic]   Recipe type counts (sorted alphabetically):");

        // Find the longest type name for alignment
        int maxLen = typeCounts.keySet().stream().mapToInt(String::length).max().orElse(10);

        for (Map.Entry<String, Integer> entry : typeCounts.entrySet()) {
            String line = String.format("%-" + maxLen + "s : %d", entry.getKey(), entry.getValue());
            LOGGER.info("[SBE Recipe Diagnostic]     {}", line);
        }

        LOGGER.info("[SBE Recipe Diagnostic] {}", sep);
        LOGGER.info("[SBE Recipe Diagnostic]   Fields seen per type (union across all recipes of that type):");

        for (Map.Entry<String, Set<String>> entry : typeFields.entrySet()) {
            LOGGER.info("[SBE Recipe Diagnostic]     [{}]  fields: {}", entry.getKey(), entry.getValue());
        }

        LOGGER.info("[SBE Recipe Diagnostic] {}", sep);
        LOGGER.info("[SBE Recipe Diagnostic]   One example item per type:");

        for (Map.Entry<String, NeuItem> entry : typeExamples.entrySet()) {
            String  type = entry.getKey();
            NeuItem item = entry.getValue();

            // Find the specific recipe entry for this type to show its raw JSON
            JsonObject exampleRecipe = findRecipeOfType(item, type);

            LOGGER.info("[SBE Recipe Diagnostic]     [{}]", type);
            LOGGER.info("[SBE Recipe Diagnostic]       item        : {}", item.internalName);
            LOGGER.info("[SBE Recipe Diagnostic]       displayName : {}", item.displayName);
            if (exampleRecipe != null) {
                LOGGER.info("[SBE Recipe Diagnostic]       recipe JSON : {}", exampleRecipe);
            }
        }

        LOGGER.info("[SBE Recipe Diagnostic] {}", sep);
        LOGGER.info("[SBE Recipe Diagnostic] Done. Distinct modern recipe types found: {}", typeCounts.size());
        LOGGER.info("[SBE Recipe Diagnostic] {}", sep);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    /** Returns all top-level field names in a JsonObject. */
    private static Set<String> fieldNames(JsonObject obj) {
        Set<String> names = new TreeSet<>();
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            names.add(e.getKey());
        }
        return names;
    }

    /**
     * Finds the first recipe entry in {@code item.recipes} whose "type" matches,
     * or {@code null} if not found (handles the "(no type field)" sentinel too).
     */
    private static JsonObject findRecipeOfType(NeuItem item, String type) {
        if (item.recipes == null) return null;
        for (JsonObject r : item.recipes) {
            if ("(no type field)".equals(type) && !r.has("type")) return r;
            if (r.has("type") && type.equals(r.get("type").getAsString()))  return r;
        }
        return null;
    }
}