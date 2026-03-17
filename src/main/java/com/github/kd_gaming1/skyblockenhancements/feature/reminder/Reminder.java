package com.github.kd_gaming1.skyblockenhancements.feature.reminder;

/**
 * Represents a single reminder with timing information and display settings.
 * Functions similarly to a phone alarm (can be toggled ON/OFF).
 */
public class Reminder {
    final int id;
    String name;
    String message;
    TriggerType triggerType;
    OutputType outputType;
    long originalDuration;
    int totalRepeats;

    long remainingMs;
    long dueAtMs;

    int repeatCount;
    boolean paused; // Acts as the OFF/Disabled state

    Reminder(int id, String name, String message, TriggerType triggerType, OutputType outputType,
             long remainingMs, long dueAtMs, long originalDuration, int totalRepeats) {
        this.id = id;
        this.name = name;
        this.message = message;
        this.triggerType = triggerType;
        this.outputType = outputType;
        this.remainingMs = remainingMs;
        this.dueAtMs = dueAtMs;
        this.originalDuration = originalDuration;
        this.totalRepeats = totalRepeats;
        this.repeatCount = 0;
        this.paused = false;
    }

    public int getId() { return id; }
    public String getDisplayName() { return name != null ? name : message; }
    public String getName() { return name; }
    public String getMessage() { return message; }
    public TriggerType getTriggerType() { return triggerType; }
    public OutputType getOutputType() { return outputType; }
    public long getOriginalDuration() { return originalDuration; }
    public int getTotalRepeats() { return totalRepeats; }
    public int getRepeatCount() { return repeatCount; }
    public boolean isPaused() { return paused; }

    /** Remaining ms. If paused, returns the stored remaining time. */
    public long getRemainingMs() {
        if (paused) return remainingMs;
        if (triggerType == TriggerType.REAL_TIME) {
            return Math.max(0, dueAtMs - System.currentTimeMillis());
        }
        return Math.max(0, remainingMs);
    }

    public long getLateMs(long firedAtMs) {
        if (triggerType == TriggerType.REAL_TIME) {
            return Math.max(0, firedAtMs - dueAtMs);
        }
        return 0;
    }
}