package com.github.kd_gaming1.skyblockenhancements.repo;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

import com.github.kd_gaming1.skyblockenhancements.repo.neu.mob.MobRenderDefinition;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.mob.MobRenderRegistry;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItemParser;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipInputStream;

/**
 * Processes {@code /mobs/*.json} entries during the repo ZIP walk.
 *
 * <p>Extracted to a separate class so {@link RepoZipParser} stays a thin dispatcher; the mob
 * registry is an independent subsystem (drops-recipe rendering) and parsing details should not
 * leak into the main repo parser.
 */
public final class MobEntryProcessor {

    private static int parsedCount;
    private static int registeredCount;
    private static int failedCount;

    private MobEntryProcessor() {}

    /** Resets ingestion counters. Call before starting a new ZIP parse. */
    public static void resetCounters() {
        parsedCount = 0;
        registeredCount = 0;
        failedCount = 0;
    }

    /** Logs a summary after all entries have been processed. */
    public static void logSummary() {
        LOGGER.info("Mob JSON ingestion: {} parsed, {} registered, {} failed.",
                parsedCount, registeredCount, failedCount);
    }

    /**
     * Returns {@code true} when the entry is a mob JSON and was consumed (successfully or not).
     * Registration under the exact NEU ref string ({@code "@neurepo:mobs/<name>.json"}) matches
     * how drops recipes reference it, so no further normalisation is needed at lookup time.
     */
    public static boolean process(String name, ZipInputStream zis) throws IOException {
        int mobsIdx = name.indexOf("/mobs/");
        if (mobsIdx < 0 || !name.endsWith(".json")) return false;

        String fileName = name.substring(mobsIdx + 6);
        if (fileName.contains("/")) return false;

        parsedCount++;
        String ref = "@neurepo:mobs/" + fileName;
        String content = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
        registerMob(ref, content);
        return true;
    }

    private static void registerMob(String ref, String content) {
        try {
            JsonObject obj = NeuItemParser.GSON.fromJson(content, JsonObject.class);
            MobRenderDefinition def = MobRenderDefinition.parse(obj);
            if (def != null) {
                MobRenderRegistry.register(ref, def);
                registeredCount++;
            } else {
                LOGGER.debug("Mob JSON produced null definition (no entity field): {}", ref);
                failedCount++;
            }
        } catch (JsonSyntaxException e) {
            LOGGER.debug("Skipping malformed mob JSON: {}", ref);
            failedCount++;
        }
    }
}