package com.github.kd_gaming1.skyblockenhancements.feature.reminder;

import com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Handles loading and saving reminder data to disk.
 * Thread-safe via {@link AtomicReference}.
 */
public class ReminderStorage {
    private final Path filePath;
    private final AtomicReference<RemindersFileData> dataRef = new AtomicReference<>(new RemindersFileData());

    public ReminderStorage(Path filePath) {
        this.filePath = filePath;
    }

    public void load() {
        try {
            RemindersFileData loaded = JsonFileUtil.readOrCreate(filePath, RemindersFileData.class, new RemindersFileData());
            dataRef.set(loaded != null ? loaded : new RemindersFileData());
        } catch (JsonParseException e) {
            dataRef.set(new RemindersFileData());
            backupBrokenFile();
            SkyblockEnhancements.LOGGER.error("Reminders file is invalid JSON and was reset. A backup was created.", e);
        } catch (IOException e) {
            dataRef.set(new RemindersFileData());
            SkyblockEnhancements.LOGGER.error("Failed to load reminders, starting fresh", e);
        }
    }

    private void backupBrokenFile() {
        Path backupPath = filePath.resolveSibling(filePath.getFileName() + ".broken");
        try {
            Files.copy(filePath, backupPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            SkyblockEnhancements.LOGGER.error("Failed to back up broken reminders file", e);
        }
    }

    public void save() {
        try {
            JsonFileUtil.writeAtomic(filePath, dataRef.get());
        } catch (IOException e) {
            SkyblockEnhancements.LOGGER.error("Failed to save reminders", e);
        }
    }

    public RemindersFileData getRemindersData() {
        return dataRef.get();
    }

    public void setRemindersData(RemindersFileData data) {
        dataRef.set(data != null ? data : new RemindersFileData());
    }
}