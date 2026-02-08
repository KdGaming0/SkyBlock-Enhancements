package com.github.kd_gaming1.skyblockenhancements.feature.reminder;

import java.util.ArrayList;
import java.util.List;

/**
 * Data structure for serializing reminders to/from JSON storage.
 * Contains all reminder state needed for persistence across sessions.
 */
public class RemindersFileData {
    public int nextReminderId = 1;
    public List<ReminderData> reminders = new ArrayList<>();

    public static class ReminderData {
        public int id;
        public long createdAtMs;
        public String status;
        public String triggerType;
        public String outputType;
        public String message;

        public long originalDuration;
        public Long remainingMs;      // WHILE_PLAYING
        public Long dueAtMs;          // REAL_TIME

        public int totalRepeats;
        public int currentRepeatCount;
    }
}