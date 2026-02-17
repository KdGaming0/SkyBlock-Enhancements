package com.github.kd_gaming1.skyblockenhancements.feature.reminder;

import com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Handles loading and saving reminder data to disk.
 * Provides persistence layer between ReminderManager and file system.
 */
public class ReminderStorage {
    private final Path remindersPath;

    private RemindersFileData remindersData;

    public ReminderStorage(Path remindersPath) {
        this.remindersPath = remindersPath;
    }

    public void load() {
        try {
            remindersData = JsonFileUtil.readOrCreate(
                    remindersPath,
                    RemindersFileData.class,
                    new RemindersFileData()
            );

            if (remindersData == null) {
                remindersData = new RemindersFileData();
            }
        } catch (Exception e) {
            remindersData = new RemindersFileData();
            SkyblockEnhancements.LOGGER.error("Failed to load reminders data, resetting file", e);
        }
    }

    public void save() {
        if (remindersData == null) return;

        try {
            JsonFileUtil.writeAtomic(remindersPath, remindersData);
        } catch (IOException e) {
            SkyblockEnhancements.LOGGER.error("Failed to save reminders data", e);
        }
    }

    public  RemindersFileData getRemindersData() {
        return remindersData;
    }


    public synchronized void setRemindersData(RemindersFileData data) {
        this.remindersData = (data != null) ? data : new RemindersFileData();
    }
}
