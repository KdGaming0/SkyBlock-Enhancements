package com.github.kd_gaming1.skyblockenhancements.feature.reminder;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Manages active reminders, handling countdown logic and firing events.
 * Updates timers based on the trigger type (real-time or while-playing only).
 */
public class ReminderManager {
    // Active reminders that cna fire
    private final List<Reminder> activeReminders = new ArrayList<>();

    // Used to create unique ids
    private int nextReminderIndex = 1;

    //  Used to run updates once per second
    private long lastSecondUpdateMs = 0;

    public Reminder createWhilePlayingReminder(long durationMs, String output, String message) {
        int id = nextReminderIndex++;
        Reminder reminder = new Reminder(id, message, "WHILE_PLAYING", output,
                durationMs, 0, durationMs, 1);
        activeReminders.add(reminder);
        return reminder;
    }

    public Reminder createRealTimeReminder(long durationMs, String output, String message) {
        int id = nextReminderIndex++;
        long dueAtMs = System.currentTimeMillis() + durationMs;
        Reminder reminder = new Reminder(id, message, "REAL_TIME", output,
                0, dueAtMs, durationMs, 1);
        activeReminders.add(reminder);
        return reminder;
    }

    public Reminder createRepeatingWhilePlayingReminder(long durationMs, String output, String message, int repeatCount) {
        int id = nextReminderIndex++;
        Reminder reminder = new Reminder(id, message, "WHILE_PLAYING", output, durationMs, 0, durationMs, repeatCount);
        activeReminders.add(reminder);
        return reminder;
    }

    public Reminder createRepeatingRealTimeReminder(long durationMs, String output, String message, int repeatCount) {
        int id = nextReminderIndex++;
        long dueAtMs = System.currentTimeMillis() + durationMs;
        Reminder reminder = new Reminder(id, message, "REAL_TIME", output, 0, dueAtMs, durationMs, repeatCount);
        activeReminders.add(reminder);
        return reminder;
    }

    public void loadFromStorage(RemindersFileData remindersData) {
        this.nextReminderIndex = remindersData.nextReminderId;
        this.activeReminders.clear();

        for (RemindersFileData.ReminderData rd : remindersData.reminders) {
            if (!"ACTIVE".equals(rd.status)) continue;

            Reminder reminder = new Reminder(
                    rd.id, rd.message, rd.triggerType, rd.outputType,
                    rd.remainingMs != null ? rd.remainingMs : 0,
                    rd.dueAtMs != null ? rd.dueAtMs : 0,
                    rd.originalDuration,
                    rd.totalRepeats
            );
            reminder.currentRepeatCount = rd.currentRepeatCount;
            activeReminders.add(reminder);
        }
    }

    public void onClientTick(Minecraft client) {
        long currentTimeMillis = System.currentTimeMillis();

        if (lastSecondUpdateMs == 0) {
            lastSecondUpdateMs = currentTimeMillis;
            return;
        }

        if (currentTimeMillis - lastSecondUpdateMs < 1000) {
            return;
        }

        long elapsedMilliseconds = currentTimeMillis - lastSecondUpdateMs;
        lastSecondUpdateMs = currentTimeMillis;

        updateReminders(client, currentTimeMillis, elapsedMilliseconds);
    }

    public void updateReminders(Minecraft client, long currentTimeMillis, long elapsedMilliseconds) {
        Iterator<Reminder> iterator = activeReminders.iterator();

        while (iterator.hasNext()) {
            Reminder reminder = iterator.next();

            if (reminder.isFired) {
                iterator.remove();
                continue;
            }

            boolean shouldFire = false;

            if ("WHILE_PLAYING".equals(reminder.triggerType)) {
                // Only countdown when a player is in-game
                boolean isPlaying = client.player != null && client.level != null;
                if (isPlaying) {
                    reminder.remainingMs -= elapsedMilliseconds;
                    shouldFire = reminder.remainingMs <= 0;
                }
            } else if ("REAL_TIME".equals(reminder.triggerType)) {
                // Always check against absolute time
                shouldFire = currentTimeMillis >= reminder.dueAtMs;
            }

            if (shouldFire) {
                fireReminder(client, reminder);
                reminder.currentRepeatCount++;

                if (reminder.totalRepeats == -1 || reminder.currentRepeatCount < reminder.totalRepeats) {
                    // Reset the reminder
                    if ("WHILE_PLAYING".equals(reminder.triggerType)) {
                        reminder.remainingMs = reminder.originalDuration;
                    } else {
                        reminder.dueAtMs = currentTimeMillis + reminder.originalDuration;
                    }
                } else {
                    reminder.isFired = true;
                    iterator.remove();
                }
            }
        }
    }

    private void fireReminder(Minecraft client, Reminder reminder) {
        if (client.player == null) return;

        String output = reminder.output.toLowerCase();

        if (output.equals("chat") || output.equals("chat_and_title")) {
            String text = "[Reminder " + reminder.id + "] " + reminder.message;
            client.player.displayClientMessage(Component.literal(text), false);
        }

        if (output.equals("title_box") || output.equals("chat_and_title")) {
            client.gui.setTitle(Component.literal("[Reminder " + reminder.id + "]"));
            client.gui.setSubtitle(Component.literal(reminder.message));
            client.gui.setTimes(10, 70, 20);
        }

        if (SkyblockEnhancementsConfig.enableReminderSound && client.level != null) {
            playReminderSound(client);
        }
    }

    private void playReminderSound(Minecraft client) {
        final Object soundCandidate = switch (SkyblockEnhancementsConfig.reminderSound) {
            case BELL -> SoundEvents.NOTE_BLOCK_BELL;
            case PLING -> SoundEvents.NOTE_BLOCK_PLING;
            case CHIME -> SoundEvents.NOTE_BLOCK_CHIME;
            case LEVEL_UP -> SoundEvents.PLAYER_LEVELUP;
            case EXPERIENCE -> SoundEvents.EXPERIENCE_ORB_PICKUP;
            case HARP -> SoundEvents.NOTE_BLOCK_HARP;
            case SUCCESS -> SoundEvents.UI_TOAST_CHALLENGE_COMPLETE;
            case UI -> SoundEvents.UI_TOAST_IN;
        };

        float volume = (float) SkyblockEnhancementsConfig.reminderSoundVolume;
        float pitch = (float) SkyblockEnhancementsConfig.reminderSoundPitch;

        final SoundEvent soundEvent;
        if (soundCandidate instanceof Holder<?> holder) {
            Object value = holder.value();
            soundEvent = (SoundEvent) value;
        } else {
            soundEvent = (SoundEvent) soundCandidate;
        }

        if (client.level != null && client.player != null)
            client.level.playSound(
                    client.player,
                    client.player.blockPosition(),
                    soundEvent,
                    SoundSource.PLAYERS,
                    volume,
                    pitch
            );
    }

    public boolean removeReminder(int id) {
        return activeReminders.removeIf(reminder -> reminder.id == id);
    }

    public int removeAllReminders() {
        int count = activeReminders.size();
        activeReminders.clear();
        return count;
    }

    public List<Reminder> getActiveReminders() {
        return new ArrayList<>(activeReminders);
    }

    public RemindersFileData saveToStorage() {
        RemindersFileData data = new RemindersFileData();
        data.nextReminderId = this.nextReminderIndex;

        for (Reminder reminder : activeReminders) {
            RemindersFileData.ReminderData rd = new RemindersFileData.ReminderData();
            rd.id = reminder.id;
            rd.createdAtMs = System.currentTimeMillis();
            rd.status = reminder.isFired ? "FIRED" : "ACTIVE";
            rd.triggerType = reminder.triggerType;
            rd.outputType = reminder.output;
            rd.message = reminder.message;
            rd.originalDuration = reminder.originalDuration;
            rd.totalRepeats = reminder.totalRepeats;
            rd.currentRepeatCount = reminder.currentRepeatCount;

            if ("WHILE_PLAYING".equals(reminder.triggerType)) {
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
}