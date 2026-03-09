package com.github.kd_gaming1.skyblockenhancements.feature.reminder;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * Manages active reminders, handling countdown logic and firing events.
 * Updates timers based on the trigger type (real-time or while-playing only).
 */
public class ReminderManager {
    /** Pass as {@code repeatCount} to repeat a reminder until manually removed. */
    public static final int REPEAT_FOREVER = -1;

    private final List<Reminder> activeReminders = new ArrayList<>();
    private int nextId = 1;
    private long lastTickMs = 0;

    // ── Factory ─────────────────────────────────────────────────────────────

    /**
     * Creates and registers a new reminder.
     *
     * @param durationMs  how long until the reminder fires
     * @param outputType  how to display the reminder when it fires
     * @param name        optional player-facing label; pass {@code null} to use the message
     * @param message     the reminder text shown when it fires
     * @param triggerType whether the countdown runs in real-time or only while playing
     * @param repeatCount number of times to fire, or {@link #REPEAT_FOREVER}
     */
    public Reminder createReminder(long durationMs, OutputType outputType, String name,
                                   String message, TriggerType triggerType, int repeatCount) {
        int id = nextId++;
        long remainingMs = triggerType == TriggerType.WHILE_PLAYING ? durationMs : 0;
        long dueAtMs = triggerType == TriggerType.REAL_TIME ? System.currentTimeMillis() + durationMs : 0;

        Reminder reminder = new Reminder(id, name, message, triggerType, outputType,
                remainingMs, dueAtMs, durationMs, repeatCount);
        activeReminders.add(reminder);
        return reminder;
    }

    // ── Persistence ─────────────────────────────────────────────────────────

    public void loadFromStorage(RemindersFileData data) {
        nextId = data.nextReminderId;
        activeReminders.clear();

        for (RemindersFileData.ReminderData rd : data.reminders) {
            TriggerType triggerType;
            OutputType outputType;
            try {
                triggerType = TriggerType.valueOf(rd.triggerType);
                outputType = OutputType.valueOf(rd.outputType);
            } catch (IllegalArgumentException e) {
                continue; // Skip reminders with corrupt enum values
            }

            Reminder reminder = new Reminder(
                    rd.id, rd.name, rd.message, triggerType, outputType,
                    rd.remainingMs != null ? rd.remainingMs : 0,
                    rd.dueAtMs != null ? rd.dueAtMs : 0,
                    rd.originalDuration,
                    rd.totalRepeats
            );
            reminder.repeatCount = rd.currentRepeatCount;
            reminder.paused = rd.paused;
            activeReminders.add(reminder);
        }
    }

    public RemindersFileData saveToStorage() {
        RemindersFileData data = new RemindersFileData();
        data.nextReminderId = nextId;

        for (Reminder reminder : activeReminders) {
            RemindersFileData.ReminderData rd = new RemindersFileData.ReminderData();
            rd.id = reminder.id;
            rd.createdAtMs = System.currentTimeMillis();
            rd.name = reminder.name;
            rd.triggerType = reminder.triggerType.name();
            rd.outputType = reminder.outputType.name();
            rd.message = reminder.message;
            rd.originalDuration = reminder.originalDuration;
            rd.totalRepeats = reminder.totalRepeats;
            rd.currentRepeatCount = reminder.repeatCount;
            rd.paused = reminder.paused;

            if (reminder.triggerType == TriggerType.WHILE_PLAYING) {
                rd.remainingMs = reminder.remainingMs;
                rd.dueAtMs = null;
            } else {
                rd.dueAtMs = reminder.dueAtMs;
                rd.remainingMs = null;
            }

            data.reminders.add(rd);
        }
        return data;
    }

    // ── Tick update ─────────────────────────────────────────────────────────

    public void onClientTick(Minecraft client) {
        long now = System.currentTimeMillis();

        if (lastTickMs == 0) {
            lastTickMs = now;
            return;
        }

        long elapsed = now - lastTickMs;
        if (elapsed < 1000) return;

        lastTickMs = now;
        updateReminders(client, now, elapsed);
    }

    void updateReminders(Minecraft client, long now, long elapsed) {
        boolean isPlaying = client.player != null && client.level != null;
        Iterator<Reminder> iterator = activeReminders.iterator();

        while (iterator.hasNext()) {
            Reminder reminder = iterator.next();
            if (reminder.paused) continue;

            if (hasExpired(reminder, now, elapsed, isPlaying)) {
                boolean remove = handleExpiredReminder(client, reminder, now);
                if (remove) iterator.remove();
            }
        }
    }

    private boolean hasExpired(Reminder reminder, long now, long elapsed, boolean isPlaying) {
        return switch (reminder.triggerType) {
            case WHILE_PLAYING -> {
                if (isPlaying) reminder.remainingMs -= elapsed;
                yield reminder.remainingMs <= 0;
            }
            case REAL_TIME -> now >= reminder.dueAtMs;
        };
    }

    /**
     * Fires a reminder, then either resets it for the next repeat or marks it done.
     *
     * @return true if the reminder should be removed from the active list
     */
    private boolean handleExpiredReminder(Minecraft client, Reminder reminder, long now) {
        fireReminder(client, reminder, now);
        reminder.repeatCount++;

        boolean repeatForever = reminder.totalRepeats == REPEAT_FOREVER;
        boolean hasRepeatsLeft = reminder.repeatCount < reminder.totalRepeats;

        if (repeatForever || hasRepeatsLeft) {
            resetReminder(reminder, now);
            return false;
        }

        reminder.fired = true;
        return true;
    }

    private void resetReminder(Reminder reminder, long now) {
        if (reminder.triggerType == TriggerType.WHILE_PLAYING) {
            reminder.remainingMs = reminder.originalDuration;
        } else {
            reminder.dueAtMs = now + reminder.originalDuration;
        }
    }

    // ── Firing ──────────────────────────────────────────────────────────────

    private void fireReminder(Minecraft client, Reminder reminder, long now) {
        if (client.player == null) return;

        long lateMs = reminder.getLateMs(now);
        boolean showVisual = reminder.outputType != OutputType.SOUND_ONLY;

        if (showVisual && reminder.outputType != OutputType.TITLE_BOX) {
            client.player.displayClientMessage(buildChatMessage(reminder, lateMs), false);
        }

        if (showVisual && reminder.outputType != OutputType.CHAT) {
            client.gui.setTitle(Component.literal("⏰ " + reminder.getDisplayName()).withStyle(ChatFormatting.YELLOW));
            client.gui.setSubtitle(Component.literal(reminder.message));
            client.gui.setTimes(10, 70, 20);
        }

        if (SkyblockEnhancementsConfig.enableReminderSound && client.level != null) {
            playReminderSound(client);
        }
    }

    private MutableComponent buildChatMessage(Reminder reminder, long lateMs) {
        String label = "⏰ " + reminder.getDisplayName();

        MutableComponent msg = Component.literal(label + " ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(reminder.message).withStyle(ChatFormatting.WHITE));

        if (lateMs > 10_000) {
            // Only show "X late" if meaningfully late (>10 seconds), not just a tick drift.
            msg.append(Component.literal(" (fired " + formatMs(lateMs) + " late)").withStyle(ChatFormatting.DARK_GRAY));
        }

        // Snooze buttons
        msg.append(Component.literal("  "));
        msg.append(snoozeButton(reminder.id, 5 * 60_000L, "5m"));
        msg.append(Component.literal(" "));
        msg.append(snoozeButton(reminder.id, 60 * 60_000L, "1h"));

        return msg;
    }

    private MutableComponent snoozeButton(int id, long durationMs, String label) {
        long minutes = durationMs / 60_000;
        String command = "/remindme snooze " + id + " " + minutes + " minutes";
        return Component.literal("[+" + label + "]").withStyle(style -> style
                .withColor(ChatFormatting.AQUA)
                .withClickEvent(new ClickEvent.RunCommand(command)));
    }

    private void playReminderSound(Minecraft client) {
        if (client.level == null || client.player == null) return;

        Object candidate = switch (SkyblockEnhancementsConfig.reminderSound) {
            case BELL -> SoundEvents.NOTE_BLOCK_BELL;
            case PLING -> SoundEvents.NOTE_BLOCK_PLING;
            case CHIME -> SoundEvents.NOTE_BLOCK_CHIME;
            case LEVEL_UP -> SoundEvents.PLAYER_LEVELUP;
            case EXPERIENCE -> SoundEvents.EXPERIENCE_ORB_PICKUP;
            case HARP -> SoundEvents.NOTE_BLOCK_HARP;
            case SUCCESS -> SoundEvents.UI_TOAST_CHALLENGE_COMPLETE;
            case UI -> SoundEvents.UI_TOAST_IN;
        };

        SoundEvent soundEvent = candidate instanceof Holder<?> holder
                ? (SoundEvent) holder.value()
                : (SoundEvent) candidate;

        float volume = (float) SkyblockEnhancementsConfig.reminderSoundVolume;
        float pitch = (float) SkyblockEnhancementsConfig.reminderSoundPitch;

        client.level.playSound(
                client.player,
                client.player.blockPosition(),
                soundEvent,
                SoundSource.PLAYERS,
                volume,
                pitch
        );
    }

    // ── Management ──────────────────────────────────────────────────────────

    public boolean removeReminder(int id) {
        return activeReminders.removeIf(r -> r.id == id);
    }

    public int removeAllReminders() {
        int count = activeReminders.size();
        activeReminders.clear();
        return count;
    }

    /** Renames a reminder. Returns false if no reminder with that id exists. */
    public boolean renameReminder(int id, String newName) {
        for (Reminder reminder : activeReminders) {
            if (reminder.id == id) {
                reminder.name = newName.isBlank() ? null : newName;
                return true;
            }
        }
        return false;
    }

    /**
     * Pauses a WHILE_PLAYING reminder so its countdown stops ticking.
     * Has no effect on REAL_TIME reminders (which always count against wall time).
     *
     * @return false if no reminder with that id exists, or it's REAL_TIME
     */
    public boolean pauseReminder(int id) {
        for (Reminder reminder : activeReminders) {
            if (reminder.id == id) {
                if (reminder.triggerType == TriggerType.REAL_TIME) return false;
                reminder.paused = true;
                return true;
            }
        }
        return false;
    }

    /** Resumes a paused WHILE_PLAYING reminder. Returns false if not found or not paused. */
    public boolean resumeReminder(int id) {
        for (Reminder reminder : activeReminders) {
            if (reminder.id == id) {
                if (!reminder.paused) return false;
                reminder.paused = false;
                return true;
            }
        }
        return false;
    }

    /**
     * Snoozes a reminder by adding {@code extraMs} to its next fire time.
     * For WHILE_PLAYING reminders this extends remainingMs; for REAL_TIME it pushes dueAtMs forward.
     *
     * @return false if no reminder with that id exists
     */
    public boolean snoozeReminder(int id, long extraMs) {
        for (Reminder reminder : activeReminders) {
            if (reminder.id == id) {
                if (reminder.triggerType == TriggerType.WHILE_PLAYING) {
                    reminder.remainingMs += extraMs;
                } else {
                    long base = Math.max(System.currentTimeMillis(), reminder.dueAtMs);
                    reminder.dueAtMs = base + extraMs;
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Returns active reminders sorted by time remaining (soonest first).
     * Paused WHILE_PLAYING reminders sort after active ones.
     */
    public List<Reminder> getActiveReminders() {
        List<Reminder> copy = new ArrayList<>(activeReminders);
        copy.sort(Comparator
                .comparingInt((Reminder r) -> r.paused ? 1 : 0)
                .thenComparingLong(Reminder::getRemainingMs));
        return copy;
    }

    // ── Formatting (shared with command layer) ───────────────────────────────

    public static String formatMs(long ms) {
        long seconds = Math.max(0, ms) / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) return pluralUnit(days, "day");
        if (hours > 0) return pluralUnit(hours, "hour");
        if (minutes > 0) return pluralUnit(minutes, "minute");
        return pluralUnit(seconds, "second");
    }

    private static String pluralUnit(long amount, String singular) {
        return amount + " " + (amount == 1 ? singular : singular + "s");
    }
}