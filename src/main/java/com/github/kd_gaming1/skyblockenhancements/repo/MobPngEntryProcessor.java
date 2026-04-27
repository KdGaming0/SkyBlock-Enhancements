package com.github.kd_gaming1.skyblockenhancements.repo;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

import com.github.kd_gaming1.skyblockenhancements.repo.neu.mob.MobSkinRegistry;
import java.io.IOException;
import java.util.zip.ZipInputStream;

/**
 * Processes {@code /mobs/*.png} entries during the repo ZIP walk. The raw file path
 * is handed to {@link MobSkinRegistry}, which normalises it to its canonical key form.
 */
public final class MobPngEntryProcessor {

    private static final String PATH_MARKER = "/mobs/";
    private static final String EXTENSION = ".png";

    private static int storedCount;

    private MobPngEntryProcessor() {}

    /** Resets the ingestion counter. Call before starting a new ZIP parse. */
    public static void resetCounters() {
        storedCount = 0;
    }

    /** Logs a summary after all entries have been processed. */
    public static void logSummary() {
        LOGGER.info("Mob PNG ingestion: {} skin files stored in memory ({} total in registry).",
                storedCount, MobSkinRegistry.storedCount());
    }

    public static boolean process(String name, ZipInputStream zis) throws IOException {
        int mobsIdx = name.indexOf(PATH_MARKER);
        if (mobsIdx < 0 || !name.endsWith(EXTENSION)) return false;

        String fileName = name.substring(mobsIdx + PATH_MARKER.length());
        if (fileName.contains("/")) return false;

        byte[] data = zis.readAllBytes();
        MobSkinRegistry.store(fileName, data);
        storedCount++;
        return true;
    }
}