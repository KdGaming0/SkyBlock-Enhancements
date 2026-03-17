package com.github.kd_gaming1.skyblockenhancements.feature.reminder;

import java.util.ArrayList;
import java.util.List;

/**
 * Data structure for serializing reminders to/from JSON storage.
 */
public class RemindersFileData {
    public int nextReminderId = 1;
    public List<ReminderData> reminders = new ArrayList<>();

    public static class ReminderData {
        public int id;
        public long createdAtMs;
        public String name;
        public String triggerType;
        public String outputType;
        public String message;

        public long originalDuration;
        public Long remainingMs;
        public Long dueAtMs;

        public int totalRepeats;
        public int currentRepeatCount;
        public boolean paused;
        public boolean fired;
    }
}