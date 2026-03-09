package com.github.kd_gaming1.skyblockenhancements.feature.reminder;

import com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements;

import java.io.IOException;
import java.nio.file.Path;
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
        } catch (IOException e) {
            dataRef.set(new RemindersFileData());
            SkyblockEnhancements.LOGGER.error("Failed to load reminders, starting fresh", e);
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