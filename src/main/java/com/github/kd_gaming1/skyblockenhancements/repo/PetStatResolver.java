package com.github.kd_gaming1.skyblockenhancements.repo;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves dynamic stat placeholders in pet lore using data from {@code constants/petnums.json}.
 *
 * <p>NEU pet items store their lore with placeholders like {@code {STRENGTH}}, {@code {SPEED}},
 * and {@code {0}}, {@code {1}} for ability values. This class substitutes those placeholders
 * with the actual max-level (level 100) stat values from petnums, producing readable lore.
 *
 * <p>The {@code {LVL}} placeholder in display names is replaced with {@code 100} to match.
 *
 * <h3>Petnums structure</h3>
 * <pre>{@code
 * {
 *   "LION": {
 *     "LEGENDARY": {
 *       "1":   { "statNums": {"STRENGTH": 0.5, ...}, "otherNums": [0.2, 0.2, ...] },
 *       "100": { "statNums": {"STRENGTH": 50.0, ...}, "otherNums": [20, 20, ...] }
 *     }
 *   }
 * }
 * }</pre>
 */
public final class PetStatResolver {

    /** Matches stat-name placeholders like {@code {STRENGTH}} or {@code {SEA_CREATURE_CHANCE}}. */
    private static final Pattern STAT_PLACEHOLDER = Pattern.compile("\\{([A-Z][A-Z_]+)}");

    /** Matches numeric placeholders like {@code {0}}, {@code {1}}. */
    private static final Pattern INDEX_PLACEHOLDER = Pattern.compile("\\{(\\d+)}");

    /** Matches the level placeholder in display names. */
    private static final Pattern LEVEL_PLACEHOLDER = Pattern.compile("\\{LVL}");

    /**
     * The stat level to display. Using max level keeps things simple and matches what
     * most players want to see when browsing the item list.
     */
    private static final int DISPLAY_LEVEL = 100;

    /**
     * Maps pet rarity suffix (from internalName) to the rarity name used in petnums.json.
     * Pet internalNames use the format {@code "LION;4"} where the number after the semicolon
     * is the rarity index: 0=COMMON, 1=UNCOMMON, 2=RARE, 3=EPIC, 4=LEGENDARY, 5=MYTHIC.
     */
    private static final String[] RARITY_SUFFIX_TO_NAME = {
            "COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC"
    };

    // ── Parsed petnums data ─────────────────────────────────────────────────────

    /**
     * Pet name → rarity name → level stats. Only stores the level-100 values since
     * that's what we display. Stored as an immutable snapshot after loading.
     */
    private static volatile Map<String, Map<String, LevelStats>> petStats = Map.of();

    private PetStatResolver() {}

    // ── Data class ──────────────────────────────────────────────────────────────

    /**
     * Holds the resolved stat values for a single pet at a specific rarity and level.
     *
     * @param statNums  named stats (e.g. {@code "STRENGTH" → 50.0})
     * @param otherNums positional ability values (index 0, 1, 2, ...)
     */
    public record LevelStats(
            Map<String, Double> statNums,
            List<Double> otherNums) {}

    // ── Loading ─────────────────────────────────────────────────────────────────

    /**
     * Parses {@code constants/petnums.json} and stores the level-100 stats for each
     * pet+rarity combination. Called from {@link NeuConstantsRegistry#loadPetNums(JsonObject)}.
     *
     * @param json the root petnums JSON object
     */
    static void load(JsonObject json) {
        Map<String, Map<String, LevelStats>> result = new HashMap<>(json.size());

        for (var petEntry : json.entrySet()) {
            String petName = petEntry.getKey();
            JsonElement rarities = petEntry.getValue();
            if (!rarities.isJsonObject()) continue;

            Map<String, LevelStats> rarityMap = new HashMap<>();

            for (var rarityEntry : rarities.getAsJsonObject().entrySet()) {
                String rarityName = rarityEntry.getKey();
                JsonElement levels = rarityEntry.getValue();
                if (!levels.isJsonObject()) continue;

                JsonObject levelsObj = levels.getAsJsonObject();
                String levelKey = String.valueOf(DISPLAY_LEVEL);
                if (!levelsObj.has(levelKey)) continue;

                JsonElement levelData = levelsObj.get(levelKey);
                if (!levelData.isJsonObject()) continue;

                LevelStats stats = parseLevelStats(levelData.getAsJsonObject());
                if (stats != null) {
                    rarityMap.put(rarityName, stats);
                }
            }

            if (!rarityMap.isEmpty()) {
                result.put(petName, Collections.unmodifiableMap(rarityMap));
            }
        }

        petStats = Collections.unmodifiableMap(result);
        LOGGER.info("Loaded petnums for {} pets", result.size());
    }

    /** Clears all loaded petnums data. Called during registry clear. */
    static void clear() {
        petStats = Map.of();
    }

    // ── Resolution ──────────────────────────────────────────────────────────────

    /**
     * Resolves all stat placeholders in a pet item's lore and display name.
     * If petnums data is not available for this pet, the item is left unchanged.
     *
     * <p>This mutates the item's {@link NeuItem#lore} and {@link NeuItem#displayName} in place.
     * It should be called once during the post-parse phase, after constants are loaded.
     *
     * @param item the pet item to resolve (must be a pet — caller should check)
     */
    public static void resolve(NeuItem item) {
        if (item.internalName == null) return;

        PetIdentity identity = parsePetIdentity(item.internalName);
        if (identity == null) return;

        Map<String, LevelStats> rarityMap = petStats.get(identity.petName);
        if (rarityMap == null) return;

        LevelStats stats = rarityMap.get(identity.rarityName);
        if (stats == null) return;

        // Resolve display name: replace {LVL} with max level
        if (item.displayName != null) {
            item.displayName = LEVEL_PLACEHOLDER.matcher(item.displayName)
                    .replaceAll(String.valueOf(DISPLAY_LEVEL));
        }

        // Resolve lore placeholders
        if (item.lore == null || item.lore.isEmpty()) return;

        List<String> resolved = new ArrayList<>(item.lore.size());
        for (String line : item.lore) {
            resolved.add(resolveLine(line, stats));
        }
        item.lore = resolved;
    }

    /**
     * Returns whether petnums data has been loaded for any pets.
     */
    public static boolean isLoaded() {
        return !petStats.isEmpty();
    }

    // ── Internal ────────────────────────────────────────────────────────────────

    /**
     * Extracts the pet name and rarity from an internal name like {@code "LION;4"}.
     *
     * @return the identity, or {@code null} if the name doesn't match the pet pattern
     */
    private static PetIdentity parsePetIdentity(String internalName) {
        int semi = internalName.lastIndexOf(';');
        if (semi < 0 || semi == internalName.length() - 1) return null;

        String petName = internalName.substring(0, semi);
        String suffixStr = internalName.substring(semi + 1);

        int suffixIndex;
        try {
            suffixIndex = Integer.parseInt(suffixStr);
        } catch (NumberFormatException e) {
            return null;
        }

        if (suffixIndex < 0 || suffixIndex >= RARITY_SUFFIX_TO_NAME.length) return null;

        return new PetIdentity(petName, RARITY_SUFFIX_TO_NAME[suffixIndex]);
    }

    private record PetIdentity(String petName, String rarityName) {}

    /**
     * Replaces all stat and index placeholders in a single lore line.
     */
    private static String resolveLine(String line, LevelStats stats) {
        if (!line.contains("{")) return line;

        // Replace named stat placeholders: {STRENGTH}, {SPEED}, etc.
        Matcher statMatcher = STAT_PLACEHOLDER.matcher(line);
        StringBuilder sb = new StringBuilder();
        while (statMatcher.find()) {
            String statName = statMatcher.group(1);
            // Skip {LVL} — it only appears in displayName, not lore, but guard anyway
            if ("LVL".equals(statName)) continue;

            Double value = stats.statNums.get(statName);
            String replacement = value != null ? formatStat(value) : statMatcher.group(0);
            statMatcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        statMatcher.appendTail(sb);
        line = sb.toString();

        // Replace indexed placeholders: {0}, {1}, {2}, etc.
        Matcher indexMatcher = INDEX_PLACEHOLDER.matcher(line);
        sb = new StringBuilder();
        while (indexMatcher.find()) {
            int index = Integer.parseInt(indexMatcher.group(1));
            String replacement;
            if (index >= 0 && index < stats.otherNums.size()) {
                replacement = formatStat(stats.otherNums.get(index));
            } else {
                replacement = indexMatcher.group(0); // Leave unresolved
            }
            indexMatcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        indexMatcher.appendTail(sb);

        return sb.toString();
    }

    /**
     * Formats a stat value for display. Whole numbers are shown without decimals,
     * fractional values keep up to two decimal places with trailing zeros stripped.
     *
     * <p>Examples: {@code 50.0 → "50"}, {@code 0.5 → "0.5"}, {@code 12.50 → "12.5"}.
     */
    private static String formatStat(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        // Format with up to 2 decimal places, strip trailing zeros
        String formatted = String.format(Locale.ROOT, "%.2f", value);
        formatted = formatted.contains(".")
                ? formatted.replaceAll("0+$", "").replaceAll("\\.$", "")
                : formatted;
        return formatted;
    }

    /**
     * Parses a single level entry from petnums.json into a {@link LevelStats}.
     */
    private static LevelStats parseLevelStats(JsonObject levelObj) {
        Map<String, Double> statNums = Map.of();
        List<Double> otherNums = List.of();

        if (levelObj.has("statNums") && levelObj.get("statNums").isJsonObject()) {
            JsonObject statsObj = levelObj.getAsJsonObject("statNums");
            Map<String, Double> map = new HashMap<>(statsObj.size());
            for (var entry : statsObj.entrySet()) {
                try {
                    map.put(entry.getKey(), entry.getValue().getAsDouble());
                } catch (Exception e) {
                    LOGGER.debug("Skipping non-numeric stat: {}", entry.getKey());
                }
            }
            statNums = Collections.unmodifiableMap(map);
        }

        if (levelObj.has("otherNums") && levelObj.get("otherNums").isJsonArray()) {
            List<Double> list = new ArrayList<>();
            for (JsonElement e : levelObj.getAsJsonArray("otherNums")) {
                try {
                    list.add(e.getAsDouble());
                } catch (Exception ex) {
                    list.add(0.0);
                }
            }
            otherNums = Collections.unmodifiableList(list);
        }

        if (statNums.isEmpty() && otherNums.isEmpty()) return null;
        return new LevelStats(statNums, otherNums);
    }
}