package com.github.kd_gaming1.skyblockenhancements.feature.katreminder;

import com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements;
import com.github.kd_gaming1.skyblockenhancements.feature.reminder.JsonFileUtil;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles persistence (load / save / backup) for Kat pet-upgrade reminders.
 * Isolated from the orchestration logic in {@link KatUpgradeReminderManager}.
 */
public class KatReminderStore {

    private final Path storagePath;

    public KatReminderStore(Path storagePath) {
        this.storagePath = storagePath;
    }

    // ── Load ─────────────────────────────────────────────────────────────────────

    public record LoadResult(List<KatUpgradeReminder> reminders, boolean wasRecoveredFromBrokenFile) {}

    /**
     * Loads reminders from disk. Returns an empty list if the file is missing,
     * malformed, or corrupt. In the corrupt case a backup is created automatically.
     */
    public LoadResult load() {
        List<KatUpgradeReminder> out = new ArrayList<>();
        boolean recovered = false;
        try {
            KatRemindersFileData data = JsonFileUtil.readOrCreate(
                    storagePath, KatRemindersFileData.class, new KatRemindersFileData());

            if (data != null && data.reminders != null) {
                for (KatReminderData rd : data.reminders) {
                    if (rd == null || rd.pet == null || rd.rarity == null) continue;
                    out.add(new KatUpgradeReminder(rd.pet, rd.rarity, rd.readyAtMs));
                }
            }
        } catch (JsonParseException e) {
            out.clear();
            recoverFromBrokenFile(e);
            recovered = true;
        } catch (IOException e) {
            out.clear();
            SkyblockEnhancements.LOGGER.error("Failed to load Kat reminders", e);
        }
        return new LoadResult(out, recovered);
    }

    // ── Save ─────────────────────────────────────────────────────────────────────

    /** Persists the given reminders atomically. */
    public void save(List<KatUpgradeReminder> reminders) {
        KatRemindersFileData data = new KatRemindersFileData();
        for (KatUpgradeReminder r : reminders) {
            KatReminderData rd = new KatReminderData();
            rd.pet = r.pet();
            rd.rarity = r.rarity();
            rd.readyAtMs = r.readyAtMs();
            data.reminders.add(rd);
        }

        try {
            JsonFileUtil.writeAtomic(storagePath, data);
        } catch (IOException e) {
            SkyblockEnhancements.LOGGER.error("Failed to save Kat reminders", e);
        }
    }

    // ── Broken-file recovery ─────────────────────────────────────────────────────

    private void recoverFromBrokenFile(Exception parseException) {
        backupBrokenFile();
        save(List.of());
        SkyblockEnhancements.LOGGER.error(
                "Kat reminders file is invalid JSON and was reset", parseException);
    }

    private void backupBrokenFile() {
        Path backupPath = storagePath.resolveSibling(storagePath.getFileName() + ".broken");
        try {
            Files.copy(storagePath, backupPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            SkyblockEnhancements.LOGGER.error("Failed to back up broken Kat reminders file", e);
        }
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────────

    public static class KatRemindersFileData {
        public List<KatReminderData> reminders = new ArrayList<>();
    }

    public static class KatReminderData {
        public String pet;
        public String rarity;
        public long readyAtMs;
    }
}
