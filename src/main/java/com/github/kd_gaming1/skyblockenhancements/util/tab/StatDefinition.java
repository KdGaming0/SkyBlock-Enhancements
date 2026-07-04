package com.github.kd_gaming1.skyblockenhancements.util.tab;

import com.github.kd_gaming1.skyblockenhancements.util.StatValueType;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Immutable descriptor for a single SkyBlock stat that may appear in the tab list.
 *
 * <p>Each definition carries a canonical key, one or more text aliases, the expected
 * value type, and optional custom parsing logic. Aliases cover every known textual
 * variation Hypixel uses (including colour-code-free forms and icon-prefixed forms).
 *
 * <p>Definitions are held in a static registry populated at class-load time via
 * {@link #registerDefaults()}. New stats can be registered at runtime with
 * {@link #register(StatDefinition)}.
 */
public final class StatDefinition {

    // ═══════════════════════════════════════════════════════════════════════════
    //  Aliases that appear in the tab list and must NEVER be treated as stats.
    // ═══════════════════════════════════════════════════════════════════════════

    private static final Set<String> BLOCKED_KEYWORDS = Set.of(
            "PLAYER", "PLAYERS", "SERVER", "AREA", "ISLAND", "PROFILE",
            "STATS", "INFO", "EVENT", "WAR", "PET", "MINION", "COOP",
            "VISITING", "GUILD", "PARTY", "MONEY", "COINS", "PURSE",
            "BANK", "BITS", "BOOSTER", "COOKIE", "TIME", "DATE",
            "FPS", "PING", "TPS", "MEMORY", "ENTITIES",
            "HYPIXEL", "SKYBLOCK", "EMPTY", "OPEN"
    );

    private static final Pattern LEADING_NOISE = Pattern.compile("^[^A-Za-z]*");

    private final String primaryKey;
    private final String[] aliases;
    private final StatValueType valueType;
    private final Function<String, String> customParser;

    private StatDefinition(Builder builder) {
        this.primaryKey = builder.primaryKey;
        this.aliases = builder.aliases;
        this.valueType = builder.valueType;
        this.customParser = builder.customParser;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String primaryKey() {
        return primaryKey;
    }

    public StatValueType valueType() {
        return valueType;
    }

    /**
     * Checks whether {@code normalizedLine} starts with any of this stat's aliases.
     * The check is case-insensitive and ignores leading non-letter characters.
     */
    boolean matches(String normalizedLine) {
        String stripped = LEADING_NOISE.matcher(normalizedLine).replaceFirst("");
        String upper = stripped.toUpperCase(Locale.ROOT);
        for (String alias : aliases) {
            if (upper.startsWith(alias) && isAliasBoundary(upper, alias.length())) return true;
        }
        return false;
    }

    /**
     * A matched alias is only a real match if what follows it is a word boundary. After skipping
     * spaces, a following <em>letter</em> means the line is a different, longer stat name — e.g.
     * "Mining Speed Boost" (a HOTM ability) must not match the "MINING SPEED" alias, and "MSB" must
     * not match "MS". A digit, sign, colon, percent, or end-of-line (a bare or multi-line stat) all
     * remain valid boundaries.
     */
    private static boolean isAliasBoundary(String upper, int aliasLen) {
        int i = aliasLen;
        while (i < upper.length() && upper.charAt(i) == ' ') i++;
        if (i >= upper.length()) return true;
        char c = upper.charAt(i);
        return c < 'A' || c > 'Z';
    }

    /**
     * Given a normalized line that {@link #matches} returned {@code true} for,
     * strips the matched alias and any trailing colon / whitespace and returns
     * the raw value string.
     */
    String extractValue(String normalizedLine) {
        String stripped = LEADING_NOISE.matcher(normalizedLine).replaceFirst("");
        String upper = stripped.toUpperCase(Locale.ROOT);

        int bestMatchLen = 0;
        for (String alias : aliases) {
            if (upper.startsWith(alias) && isAliasBoundary(upper, alias.length())
                    && alias.length() > bestMatchLen) {
                bestMatchLen = alias.length();
            }
        }
        if (bestMatchLen == 0) return "";

        // Find the original-cased position so we slice the original string.
        String remainder = stripped.substring(bestMatchLen).trim();
        if (remainder.startsWith(":")) {
            remainder = remainder.substring(1).trim();
        }
        return customParser != null ? customParser.apply(remainder) : remainder;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Builder
    // ═══════════════════════════════════════════════════════════════════════════

    public static Builder builder(String primaryKey) {
        return new Builder(primaryKey);
    }

    public static final class Builder {
        private final String primaryKey;
        private String[] aliases = new String[0];
        private StatValueType valueType = StatValueType.STRING;
        private Function<String, String> customParser;

        private Builder(String primaryKey) {
            this.primaryKey = primaryKey;
        }

        public Builder aliases(String... aliases) {
            this.aliases = aliases;
            return this;
        }

        public Builder valueType(StatValueType valueType) {
            this.valueType = valueType;
            return this;
        }

        public Builder customParser(Function<String, String> parser) {
            this.customParser = parser;
            return this;
        }

        public StatDefinition build() {
            return new StatDefinition(this);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Static Registry
    // ═══════════════════════════════════════════════════════════════════════════

    /** alias (uppercase) → definition */
    private static final Map<String, StatDefinition> REGISTRY = new HashMap<>();
    /** primaryKey → definition */
    private static final Map<String, StatDefinition> BY_PRIMARY_KEY = new HashMap<>();

    static {
        registerDefaults();
    }

    private static void registerDefaults() {
        register(builder("mining_speed")
                .aliases("MINING SPEED", "MS", "MINING SPD")
                .valueType(StatValueType.INT)
                .build());

        register(builder("mining_fortune")
                .aliases("MINING FORTUNE", "MF")
                .valueType(StatValueType.INT)
                .build());

        register(builder("breaking_power")
                .aliases("BREAKING POWER", "BP")
                .valueType(StatValueType.INT)
                .build());

        register(builder("health")
                .aliases("HEALTH", "HP", "MAX HEALTH")
                .valueType(StatValueType.INT)
                .build());

        register(builder("defense")
                .aliases("DEFENSE", "DEF")
                .valueType(StatValueType.INT)
                .build());

        register(builder("strength")
                .aliases("STRENGTH", "STR", "DAMAGE")
                .valueType(StatValueType.INT)
                .build());

        register(builder("speed")
                .aliases("SPEED", "WALK SPEED")
                .valueType(StatValueType.INT)
                .build());

        register(builder("crit_chance")
                .aliases("CRIT CHANCE", "CC")
                .valueType(StatValueType.FLOAT)
                .build());

        register(builder("crit_damage")
                .aliases("CRIT DAMAGE", "CD")
                .valueType(StatValueType.FLOAT)
                .build());

        register(builder("attack_speed")
                .aliases("ATTACK SPEED", "AS")
                .valueType(StatValueType.FLOAT)
                .build());

        register(builder("magic_find")
                .aliases("MAGIC FIND")
                .valueType(StatValueType.INT)
                .build());

        register(builder("pet_luck")
                .aliases("PET LUCK")
                .valueType(StatValueType.INT)
                .build());

        register(builder("sea_creature_chance")
                .aliases("SEA CREATURE CHANCE", "SCC")
                .valueType(StatValueType.FLOAT)
                .build());

        register(builder("ferocity")
                .aliases("FEROCITY", "FERO")
                .valueType(StatValueType.INT)
                .build());

        register(builder("ability_damage")
                .aliases("ABILITY DAMAGE", "ABLD")
                .valueType(StatValueType.FLOAT)
                .build());

        register(builder("true_defense")
                .aliases("TRUE DEFENSE", "TRUE DEF")
                .valueType(StatValueType.INT)
                .build());
    }

    // ── Public Registry API ───────────────────────────────────────────────────

    /**
     * Registers a stat definition. Aliases are stored uppercase for case-insensitive lookup.
     */
    public static void register(StatDefinition def) {
        BY_PRIMARY_KEY.put(def.primaryKey, def);
        for (String alias : def.aliases) {
            REGISTRY.put(alias, def);
        }
    }

    /**
     * Convenience method to register a stat with aliases without building manually.
     */
    public static void registerAlias(String primaryKey, String... aliases) {
        StatDefinition def = builder(primaryKey).aliases(aliases).build();
        register(def);
    }

    /**
     * Looks up a definition by any of its aliases (case-insensitive).
     */
    public static Optional<StatDefinition> get(String alias) {
        return Optional.ofNullable(REGISTRY.get(alias.toUpperCase(Locale.ROOT)));
    }

    /**
     * Looks up a definition by its canonical primary key.
     */
    public static Optional<StatDefinition> getByPrimaryKey(String key) {
        return Optional.ofNullable(BY_PRIMARY_KEY.get(key));
    }

    /**
     * Returns all registered definitions.
     */
    public static Collection<StatDefinition> getAll() {
        return Collections.unmodifiableCollection(BY_PRIMARY_KEY.values());
    }

    /**
     * Returns true if the given text looks like a section header or other non-stat line.
     * Used to quickly reject lines before attempting alias matching.
     */
    static boolean isBlockedKeyword(String normalizedLine) {
        String stripped = LEADING_NOISE.matcher(normalizedLine).replaceFirst("");
        return BLOCKED_KEYWORDS.contains(stripped.toUpperCase(Locale.ROOT));
    }
}
