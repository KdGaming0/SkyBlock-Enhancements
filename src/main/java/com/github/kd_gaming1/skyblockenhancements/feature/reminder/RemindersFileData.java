package com.github.kd_gaming1.skyblockenhancements.feature.reminder;

import java.util.ArrayList;
import java.util.List;

/**
 * Data structure for serializing reminders to/from JSON storage.
 * Only active (not-yet-fired) reminders are persisted.
 */
public class RemindersFileData {
    public int nextReminderId = 1;
    public List<ReminderData> reminders = new ArrayList<>();

    public static class ReminderData {
        public int id;
        public long createdAtMs;
        public String name;         // nullable — player-set label
        public String triggerType;
        public String outputType;
        public String message;

        public long originalDuration;
        public Long remainingMs;    // WHILE_PLAYING only
        public Long dueAtMs;        // REAL_TIME only

        public int totalRepeats;
        public int currentRepeatCount;
        public boolean paused;
    }
}