package com.github.kd_gaming1.skyblockenhancements.feature.reminder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;

public class ReminderManager {

    public static final int REPEAT_FOREVER = -1;

    private final List<Reminder> activeReminders = new ArrayList<>();
    private final List<Runnable> changeListeners = new ArrayList<>();
    private final ReminderNotifier notifier;
    private int nextId = 1;
    private long lastTickMs = 0;
    private long accumulatedMs = 0;

    public ReminderManager(ReminderNotifier notifier) {
        this.notifier = notifier;
    }

    /** Registers a listener that is fired after any CRUD mutation. */
    public void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    private void notifyChanged() {
        for (Runnable listener : changeListeners) {
            try {
                listener.run();
            } catch (Exception ignored) {
                // Defensive: never let a listener crash the manager.
            }
        }
    }

    public Reminder createReminder(
            long durationMs,
            OutputType outputType,
            String name,
            String message,
            TriggerType triggerType,
            int totalRepeats) {
        int id = nextId++;
        long remainingMs = triggerType == TriggerType.WHILE_PLAYING ? durationMs : 0;
        long dueAtMs = triggerType == TriggerType.REAL_TIME ? System.currentTimeMillis() + durationMs : 0;

        Reminder reminder =
                new Reminder(
                        id,
                        name,
                        message,
                        triggerType,
                        outputType,
                        remainingMs,
                        dueAtMs,
                        durationMs,
                        totalRepeats);
        activeReminders.add(reminder);
        notifyChanged();
        return reminder;
    }

    public void loadFromStorage(RemindersFileData data) {
        nextId = Math.max(1, data.nextReminderId);
        activeReminders.clear();

        for (RemindersFileData.ReminderData rd : data.reminders) {
            TriggerType triggerType;
            OutputType outputType;
            try {
                triggerType = TriggerType.valueOf(rd.triggerType);
                outputType = OutputType.valueOf(rd.outputType);
            } catch (Exception e) {
                continue;
            }

            Reminder reminder =
                    new Reminder(
                            rd.id,
                            rd.name,
                            rd.message,
                            triggerType,
                            outputType,
                            rd.remainingMs != null ? rd.remainingMs : 0,
                            rd.dueAtMs != null ? rd.dueAtMs : 0,
                            rd.originalDuration,
                            rd.totalRepeats);
            reminder.repeatCount = rd.currentRepeatCount;
            reminder.paused = rd.paused || rd.fired;

            if (reminder.paused && triggerType == TriggerType.REAL_TIME && rd.remainingMs == null) {
                reminder.remainingMs = rd.originalDuration;
            }

            activeReminders.add(reminder);
            nextId = Math.max(nextId, rd.id + 1);
        }
        notifyChanged();
    }

    public RemindersFileData saveToStorage() {
        RemindersFileData data = new RemindersFileData();
        data.nextReminderId = nextId;
        long now = System.currentTimeMillis();

        for (Reminder reminder : activeReminders) {
            RemindersFileData.ReminderData rd = new RemindersFileData.ReminderData();
            rd.id = reminder.id;
            rd.createdAtMs = now;
            rd.name = reminder.name;
            rd.triggerType = reminder.triggerType.name();
            rd.outputType = reminder.outputType.name();
            rd.message = reminder.message;
            rd.originalDuration = reminder.originalDuration;
            rd.totalRepeats = reminder.totalRepeats;
            rd.currentRepeatCount = reminder.repeatCount;
            rd.paused = reminder.paused;
            rd.fired = false;

            if (reminder.triggerType == TriggerType.WHILE_PLAYING || reminder.paused) {
                rd.remainingMs = reminder.remainingMs;
            } else {
                rd.remainingMs = null;
            }
            rd.dueAtMs = reminder.dueAtMs;

            data.reminders.add(rd);
        }
        return data;
    }

    public void onClientTick(Minecraft client) {
        long now = System.currentTimeMillis();
        if (lastTickMs == 0) {
            lastTickMs = now;
            return;
        }

        long frameElapsed = Math.max(0, now - lastTickMs);
        lastTickMs = now;
        accumulatedMs += frameElapsed;

        if (accumulatedMs < 1000) {
            // Still process real-time reminders so they can fire on time.
            updateRealTimeReminders(client, now);
            return;
        }

        long wholeSecondsMs = (accumulatedMs / 1000) * 1000;
        accumulatedMs %= 1000;

        updateReminders(client, now, wholeSecondsMs);
    }

    private void updateRealTimeReminders(Minecraft client, long now) {
        for (Reminder reminder : activeReminders) {
            if (reminder.paused) {
                continue;
            }
            if (reminder.triggerType == TriggerType.REAL_TIME && now >= reminder.dueAtMs) {
                handleExpiredReminder(client, reminder, now);
            }
        }
    }

    void updateReminders(Minecraft client, long now, long elapsedMsStep) {
        boolean isPlaying = client.player != null && client.level != null;
        for (Reminder reminder : activeReminders) {
            if (reminder.paused) {
                continue;
            }

            if (reminder.triggerType == TriggerType.WHILE_PLAYING) {
                if (isPlaying) {
                    reminder.remainingMs -= elapsedMsStep;
                }
                if (reminder.remainingMs <= 0) {
                    handleExpiredReminder(client, reminder, now);
                }
                continue;
            }

            if (now >= reminder.dueAtMs) {
                handleExpiredReminder(client, reminder, now);
            }
        }
    }

    private boolean hasExpired(Reminder reminder, long now, long elapsed, boolean isPlaying) {
        return switch (reminder.triggerType) {
            case WHILE_PLAYING -> {
                if (isPlaying) {
                    reminder.remainingMs -= elapsed;
                }
                yield reminder.remainingMs <= 0;
            }
            case REAL_TIME -> now >= reminder.dueAtMs;
        };
    }

    private void handleExpiredReminder(Minecraft client, Reminder reminder, long now) {
        notifier.fire(client, reminder, now);
        reminder.repeatCount++;

        boolean hasMoreRepeats =
                reminder.totalRepeats == REPEAT_FOREVER || reminder.repeatCount < reminder.totalRepeats;

        resetTimer(reminder);

        if (!hasMoreRepeats) {
            reminder.paused = true;
            reminder.repeatCount = 0;
        }
    }

    public boolean removeReminder(int id) {
        boolean removed = activeReminders.removeIf(r -> r.id == id);
        if (removed) notifyChanged();
        return removed;
    }

    public void updateReminder(
            int id,
            long durationMs,
            OutputType outputType,
            String name,
            String message,
            TriggerType triggerType,
            int totalRepeats) {
        Reminder r = findById(id);
        if (r == null) {
            return;
        }

        boolean wasPaused = r.paused;

        r.originalDuration = durationMs;
        r.outputType = outputType;
        r.name = name;
        r.message = message;
        r.triggerType = triggerType;
        r.totalRepeats = totalRepeats;
        r.repeatCount = 0;

        if (wasPaused) {
            r.remainingMs = durationMs;
            if (triggerType == TriggerType.REAL_TIME) {
                r.dueAtMs = System.currentTimeMillis() + durationMs;
            }
        } else {
            resetTimer(r);
        }
        notifyChanged();
    }

    public boolean toggleReminder(int id) {
        Reminder r = findById(id);
        if (r == null) {
            return false;
        }

        if (r.paused) {
            r.paused = false;
            if (r.totalRepeats != REPEAT_FOREVER && r.repeatCount >= r.totalRepeats) {
                r.repeatCount = 0;
            }
            if (r.remainingMs <= 0) {
                r.remainingMs = r.originalDuration;
            }
            if (r.triggerType == TriggerType.REAL_TIME) {
                r.dueAtMs = System.currentTimeMillis() + r.remainingMs;
            }
            lastTickMs = System.currentTimeMillis();
        } else {
            if (r.triggerType == TriggerType.REAL_TIME) {
                r.remainingMs = Math.max(0, r.dueAtMs - System.currentTimeMillis());
            }
            r.paused = true;
        }
        notifyChanged();
        return true;
    }

    public int removeAllReminders() {
        int count = activeReminders.size();
        activeReminders.clear();
        if (count > 0) notifyChanged();
        return count;
    }

    public boolean renameReminder(int id, String newName) {
        Reminder r = findById(id);
        if (r == null) {
            return false;
        }
        r.name = newName.isBlank() ? null : newName;
        notifyChanged();
        return true;
    }

    public boolean snoozeReminder(int id, long extraMs) {
        Reminder r = findById(id);
        if (r == null) {
            return false;
        }

        if (r.triggerType == TriggerType.WHILE_PLAYING) {
            r.remainingMs += extraMs;
        } else {
            r.dueAtMs = Math.max(System.currentTimeMillis(), r.dueAtMs) + extraMs;
        }
        notifyChanged();
        return true;
    }

    public boolean resetReminderTime(int id) {
        Reminder r = findById(id);
        if (r == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        r.repeatCount = 0;
        r.remainingMs = r.originalDuration;
        if (r.triggerType == TriggerType.REAL_TIME) {
            r.dueAtMs = now + r.originalDuration;
        }

        lastTickMs = now;
        notifyChanged();
        return true;
    }

    public List<Reminder> getActiveReminders() {
        List<Reminder> copy = new ArrayList<>(activeReminders);
        copy.sort(
                Comparator.comparingInt((Reminder r) -> r.paused ? 1 : 0)
                        .thenComparingLong(Reminder::getRemainingMs));
        return copy;
    }

    private Reminder findById(int id) {
        for (Reminder r : activeReminders) {
            if (r.id == id) {
                return r;
            }
        }
        return null;
    }

    public static String formatMs(long ms) {
        long seconds = Math.max(0, ms) / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return pluralUnit(days, "day");
        }
        if (hours > 0) {
            return pluralUnit(hours, "hour");
        }
        if (minutes > 0) {
            return pluralUnit(minutes, "minute");
        }
        return pluralUnit(seconds, "second");
    }

    private static String pluralUnit(long n, String s) {
        return n + " " + (n == 1 ? s : s + "s");
    }

    private void resetTimer(Reminder r) {
        r.remainingMs = r.originalDuration;
        if (r.triggerType == TriggerType.REAL_TIME) {
            r.dueAtMs = System.currentTimeMillis() + r.originalDuration;
        }
    }
}
