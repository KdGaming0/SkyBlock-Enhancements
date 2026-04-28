package com.github.kd_gaming1.skyblockenhancements.feature.katreminder;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

/**
 * Parses Kat NPC dialog strings to extract upgrade starts, reminders, durations,
 * and special-event messages (flowers, bouquets, reset).
 *
 * <p>All regex patterns and normalization logic live here so that
 * {@link KatUpgradeReminderManager} only orchestrates state transitions.
 */
public final class KatDialogParser {

    private KatDialogParser() {}

    // ── Patterns ─────────────────────────────────────────────────────────────────

    private static final Pattern GIVE_REGEX = Pattern.compile(
            "^I(?:['\\u2019])ll get your (?<pet>.+) upgraded to (?<rarity>[A-Za-z]+) in no time[!.]?$");
    private static final Pattern REMIND_REGEX = Pattern.compile(
            "^I(?:['\\u2019])ll remind you when your (?<pet>.+) is done[!.]?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DURATION_REGEX = Pattern.compile(
            "^Come back in (?<duration>.+) to pick it up[!.]?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DURATION_REMIND_REGEX = Pattern.compile(
            "^I(?:['\\u2019])ll remind you in (?<duration>.+)[!.]?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DURATION_PART_REGEX = Pattern.compile(
            "(\\d+)\\s*(day|days|hour|hours|houre|houres|minute|minutes|second|seconds)", Pattern.CASE_INSENSITIVE);

    // ── Special dialog messages ──────────────────────────────────────────────────

    private static final String FLOWER_MESSAGE = "A flower? For me? How sweet!";
    private static final String BOUQUET_MESSAGE = "A bouquet? For me? How sweet!";
    private static final String RESET_MESSAGE = "If you have any other pets you'd like to upgrade, you know where to find me!";

    public static final long FLOWER_REDUCTION_MS = 86_400_000L;
    public static final long BOUQUET_REDUCTION_MS = 432_000_000L;

    // ── Result records ───────────────────────────────────────────────────────────

    public record UpgradeStart(String pet, String rarity) {}
    public record ReminderStart(String pet) {}
    public record DurationResult(long durationMs) {}

    public enum SpecialDialog { FLOWER, BOUQUET, RESET, NONE }

    // ── Public API ───────────────────────────────────────────────────────────────

    /** Attempts to parse an upgrade-start dialog ("I'll get your X upgraded to Y..."). */
    @Nullable
    public static UpgradeStart tryParseUpgradeStart(String dialog) {
        Matcher m = GIVE_REGEX.matcher(dialog);
        if (!m.matches()) return null;

        String pet = m.group("pet").trim();
        String rarity = normalizeRarity(m.group("rarity"));
        if (rarity == null) return null;

        return new UpgradeStart(pet, rarity);
    }

    /** Attempts to parse a reminder-start dialog ("I'll remind you when your X is done"). */
    @Nullable
    public static ReminderStart tryParseReminderStart(String dialog) {
        Matcher m = REMIND_REGEX.matcher(dialog);
        if (!m.matches()) return null;
        return new ReminderStart(m.group("pet").trim());
    }

    /**
     * Parses a duration string like "2 days 5 hours" into milliseconds.
     * Returns {@code 0} if no parsable parts are found.
     */
    public static long parseDuration(String rawDuration) {
        Matcher m = DURATION_PART_REGEX.matcher(rawDuration);

        long totalSeconds = 0L;
        boolean found = false;

        while (m.find()) {
            long amount = Long.parseLong(m.group(1));
            if (amount <= 0) continue;

            String unit = m.group(2).toLowerCase(Locale.ROOT);
            totalSeconds += switch (unit) {
                case "day", "days" -> amount * 86_400L;
                case "hour", "hours", "houre", "houres" -> amount * 3_600L;
                case "minute", "minutes" -> amount * 60L;
                case "second", "seconds" -> amount;
                default -> 0L;
            };
            found = true;
        }

        return found ? totalSeconds * 1000L : 0L;
    }

    /** Detects flower / bouquet / reset messages. */
    public static SpecialDialog detectSpecialDialog(String dialog) {
        if (FLOWER_MESSAGE.equals(dialog))  return SpecialDialog.FLOWER;
        if (BOUQUET_MESSAGE.equals(dialog)) return SpecialDialog.BOUQUET;
        if (RESET_MESSAGE.equals(dialog))   return SpecialDialog.RESET;
        return SpecialDialog.NONE;
    }

    /** Normalises a raw rarity string to the internal enum name, or {@code null} if invalid. */
    @Nullable
    public static String normalizeRarity(String rawRarity) {
        String rarity = rawRarity.toUpperCase(Locale.ROOT);
        return switch (rarity) {
            case "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC" -> rarity;
            default -> null;
        };
    }
}
