package com.github.kd_gaming1.skyblockenhancements.feature.reminder;

import com.github.kd_gaming1.skyblockenhancements.config.ModSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Handles all user-facing notification output for reminders:
 * chat messages, screen titles, and configurable sounds.
 *
 * <p>Extracted from {@link ReminderManager} so the manager can focus on
 * scheduling and state, while this class deals with Minecraft-side effects.
 */
public final class ReminderNotifier {

    private final ModSettings settings;

    public ReminderNotifier(ModSettings settings) {
        this.settings = settings;
    }

    public void fire(Minecraft client, Reminder reminder, long now) {
        if (client.player == null) {
            return;
        }

        long lateMs = reminder.getLateMs(now);

        switch (reminder.outputType) {
            case CHAT, CHAT_AND_TITLE, CHAT_AND_SOUND, ALL -> sendChatMessage(client, reminder, lateMs);
            default -> {}
        }
        switch (reminder.outputType) {
            case TITLE_BOX, CHAT_AND_TITLE, TITLE_AND_SOUND, ALL -> sendTitle(client, reminder);
            default -> {}
        }
        switch (reminder.outputType) {
            case SOUND_ONLY, CHAT_AND_SOUND, TITLE_AND_SOUND, ALL -> playReminderSound(client);
            default -> {}
        }
    }

    private void sendChatMessage(Minecraft client, Reminder reminder, long lateMs) {
        MutableComponent msg =
                Component.literal("⏰ ")
                        .withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(reminder.message).withStyle(ChatFormatting.WHITE));

        if (lateMs > 5000) {
            msg.append(
                    Component.literal(" (fired " + ReminderManager.formatMs(lateMs) + " late)")
                            .withStyle(ChatFormatting.DARK_GRAY));
        }

        client.gui.getChat().addClientSystemMessage(msg);
    }

    private void sendTitle(Minecraft client, Reminder reminder) {
        client.gui.setTitle(Component.literal(reminder.message).withStyle(ChatFormatting.YELLOW));
        client.gui.setSubtitle(Component.literal("Reminder").withStyle(ChatFormatting.GOLD));
    }

    private void playReminderSound(Minecraft client) {
        if (client.level == null || client.player == null || !settings.enableReminderSound()) {
            return;
        }

        Object candidate = switch (settings.reminderSound()) {
            case BELL -> SoundEvents.NOTE_BLOCK_BELL;
            case PLING -> SoundEvents.NOTE_BLOCK_PLING;
            case CHIME -> SoundEvents.NOTE_BLOCK_CHIME;
            case LEVEL_UP -> SoundEvents.PLAYER_LEVELUP;
            case EXPERIENCE -> SoundEvents.EXPERIENCE_ORB_PICKUP;
            case HARP -> SoundEvents.NOTE_BLOCK_HARP;
            case SUCCESS -> SoundEvents.UI_TOAST_CHALLENGE_COMPLETE;
            case UI -> SoundEvents.UI_TOAST_IN;
        };

        SoundEvent soundEvent =
                candidate instanceof Holder<?> h ? (SoundEvent) h.value() : (SoundEvent) candidate;

        client.level.playSound(
                client.player,
                client.player.blockPosition(),
                soundEvent,
                SoundSource.PLAYERS,
                (float) settings.reminderSoundVolume(),
                (float) settings.reminderSoundPitch());
    }
}
