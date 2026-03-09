package com.github.kd_gaming1.skyblockenhancements.feature.reminder;

/**
 * Represents a single reminder with timing information and display settings.
 * Supports both real-time and while-playing trigger types with optional repeating,
 * an optional display name, pause/resume, and late-fire detection.
 *
 * <p>Fields are package-private so only {@link ReminderManager} can mutate them.</p>
 */
public class Reminder {
    final int id;
    /** Optional player-facing label. Falls back to the message when null. */
    String name;
    final String message;
    final TriggerType triggerType;
    final OutputType outputType;
    final long originalDuration;
    final int totalRepeats;

    /** Remaining milliseconds until fire. Used by {@link TriggerType#WHILE_PLAYING} only. */
    long remainingMs;
    /** Absolute epoch-ms at which to fire. Used by {@link TriggerType#REAL_TIME} only. */
    long dueAtMs;

    int repeatCount;
    boolean fired;
    boolean paused;

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
        this.fired = false;
        this.paused = false;
    }

    // ── Public read-only accessors ──────────────────────────────────────────

    public int getId() { return id; }
    /** The player-facing label, or the message if no name was set. */
    public String getDisplayName() { return name != null ? name : message; }
    public String getName() { return name; }
    public String getMessage() { return message; }
    public TriggerType getTriggerType() { return triggerType; }
    public OutputType getOutputType() { return outputType; }
    public long getOriginalDuration() { return originalDuration; }
    public int getTotalRepeats() { return totalRepeats; }
    public int getRepeatCount() { return repeatCount; }
    public boolean isFired() { return fired; }
    public boolean isPaused() { return paused; }

    /** Remaining ms (WHILE_PLAYING), or ms until dueAtMs (REAL_TIME). Never negative. */
    public long getRemainingMs() {
        if (triggerType == TriggerType.REAL_TIME) {
            return Math.max(0, dueAtMs - System.currentTimeMillis());
        }
        return Math.max(0, remainingMs);
    }

    /**
     * How many milliseconds late this reminder fired relative to {@code firedAtMs}.
     * Returns 0 if it fired on time or early.
     */
    public long getLateMs(long firedAtMs) {
        if (triggerType == TriggerType.REAL_TIME) {
            return Math.max(0, firedAtMs - dueAtMs);
        }
        return 0;
    }
}