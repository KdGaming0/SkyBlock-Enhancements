package com.github.kd_gaming1.skyblockenhancements.feature.reminder;

/**
 * Represents a single reminder with timing information and display settings.
 * Supports both real-time and while-playing trigger types with optional repeating.
 */
public class Reminder {
    public final int id;
    public final String message;
    public final String triggerType;
    public final String output;
    public long remainingMs;      // For WHILE_PLAYING only
    public long dueAtMs;          // For REAL_TIME only
    public final long originalDuration;
    public final int totalRepeats;
    public int currentRepeatCount;
    public boolean isFired;

    public Reminder(int id, String message, String triggerType, String output,
                    long remainingMs, long dueAtMs, long originalDuration, int totalRepeats) {
        this.id = id;
        this.message = message;
        this.triggerType = triggerType;
        this.output = output;
        this.remainingMs = remainingMs;
        this.dueAtMs = dueAtMs;
        this.originalDuration = originalDuration;
        this.totalRepeats = totalRepeats;
        this.currentRepeatCount = 0;
        this.isFired = false;
    }
}