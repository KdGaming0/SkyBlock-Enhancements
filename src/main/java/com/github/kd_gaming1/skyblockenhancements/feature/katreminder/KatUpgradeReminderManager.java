package com.github.kd_gaming1.skyblockenhancements.feature.katreminder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements;
import com.github.kd_gaming1.skyblockenhancements.feature.reminder.JsonFileUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Handles Kat's two-line upgrade dialog and creates real-time reminders that
 * persist across sessions in a dedicated JSON file.
 */
public class KatUpgradeReminderManager {
    // First Kat line: stores pet + target rarity context.
    private static final Pattern START_PATTERN = Pattern.compile("^I[’']ll get your (.+) upgraded to ([A-Za-z]+) in no time[!.]?$");
    // Second Kat line: stores countdown until pickup.
    private static final Pattern TIME_PATTERN = Pattern.compile("^Come back in (\\d+) (minute|minutes|hour|hours|houre|houres|day|days) to pick it up[!.]?$", Pattern.CASE_INSENSITIVE);

    private final Path storagePath;
    private final List<KatUpgradeReminder> activeReminders = new ArrayList<>();

    private PendingUpgrade pendingUpgrade;
    private long lastSecondUpdateMs;

    public KatUpgradeReminderManager(Path storagePath) {
        this.storagePath = storagePath;
    }

    public void load() {
        try {
            // Load existing reminders from disk (or create file on first run).
            KatRemindersFileData data = JsonFileUtil.readOrCreate(storagePath, KatRemindersFileData.class, new KatRemindersFileData());
            activeReminders.clear();

            if (data == null || data.reminders == null) {
                return;
            }

            for (KatReminderData reminderData : data.reminders) {
                if (reminderData == null || reminderData.pet == null || reminderData.rarity == null) continue;
                activeReminders.add(new KatUpgradeReminder(reminderData.pet, reminderData.rarity, reminderData.readyAtMs));
            }
        } catch (IOException e) {
            activeReminders.clear();
            SkyblockEnhancements.LOGGER.error("Failed to load Kat reminders", e);
        }
    }

    public void save() {
        // Persist only data required to rebuild runtime reminders.
        KatRemindersFileData data = new KatRemindersFileData();

        for (KatUpgradeReminder reminder : activeReminders) {
            KatReminderData reminderData = new KatReminderData();
            reminderData.pet = reminder.pet;
            reminderData.rarity = reminder.rarity;
            reminderData.readyAtMs = reminder.readyAtMs;
            data.reminders.add(reminderData);
        }

        try {
            JsonFileUtil.writeAtomic(storagePath, data);
        } catch (IOException e) {
            SkyblockEnhancements.LOGGER.error("Failed to save Kat reminders", e);
        }
    }

    public void onNpcDialog(String npcName, String dialog) {
        // This manager currently only handles Kat dialog flow.
        if (!"Kat".equals(npcName)) return;

        if (tryReadUpgradeStart(dialog)) {
            return;
        }

        tryCreateReminderFromTime(dialog);
    }

    public void onClientTick(Minecraft client) {
        if (client.player == null) return;

        long currentTimeMillis = System.currentTimeMillis();

        if (lastSecondUpdateMs == 0) {
            lastSecondUpdateMs = currentTimeMillis;
            return;
        }

        if (currentTimeMillis - lastSecondUpdateMs < 1000) {
            return;
        }

        lastSecondUpdateMs = currentTimeMillis;

        boolean changed = false;
        Iterator<KatUpgradeReminder> iterator = activeReminders.iterator();
        while (iterator.hasNext()) {
            KatUpgradeReminder reminder = iterator.next();
            if (currentTimeMillis < reminder.readyAtMs) continue;

            // Reminder expired: notify once, then remove from active list.
            sendReadyMessage(client, reminder);
            iterator.remove();
            changed = true;
        }

        if (changed) {
            save();
        }
    }

    private boolean tryReadUpgradeStart(String dialog) {
        Matcher startMatcher = START_PATTERN.matcher(dialog);
        if (!startMatcher.matches()) return false;

        String pet = startMatcher.group(1).trim();
        String rarity = normalizeRarity(startMatcher.group(2));
        if (rarity == null) return false;

        pendingUpgrade = new PendingUpgrade(pet, rarity);
        return true;
    }

    private void tryCreateReminderFromTime(String dialog) {
        // Second line is only valid after we parsed a matching first line.
        if (pendingUpgrade == null) return;

        Matcher timeMatcher = TIME_PATTERN.matcher(dialog);
        if (!timeMatcher.matches()) return;

        long amount = Long.parseLong(timeMatcher.group(1));
        if (amount <= 0) return;

        String unit = timeMatcher.group(2).toLowerCase(Locale.ROOT);
        long durationMs = toDurationMilliseconds(amount, unit);
        if (durationMs <= 0) return;

        activeReminders.add(new KatUpgradeReminder(
                pendingUpgrade.pet,
                pendingUpgrade.rarity,
                System.currentTimeMillis() + durationMs
        ));
        // Clear dialog state so unrelated lines do not create accidental reminders.
        pendingUpgrade = null;
        save();
    }

    private long toDurationMilliseconds(long amount, String unit) {
        if (unit.startsWith("minute")) {
            return amount * 60_000L;
        }
        if (unit.startsWith("hour") || unit.startsWith("houre")) {
            return amount * 3_600_000L;
        }
        if (unit.startsWith("day")) {
            return amount * 86_400_000L;
        }
        return 0L;
    }

    private String normalizeRarity(String rawRarity) {
        String rarity = rawRarity.toUpperCase(Locale.ROOT);
        return switch (rarity) {
            case "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC" -> rarity;
            default -> null;
        };
    }

    private void sendReadyMessage(Minecraft client, KatUpgradeReminder reminder) {
        ChatFormatting rarityColor = mapRarityColor(reminder.rarity);
        // Keep "[call]" clickable to execute "/call kat" directly from chat.
        MutableComponent message = Component.literal("Your ")
                .append(Component.literal(reminder.pet).withStyle(rarityColor))
                .append(Component.literal(" is Ready to pick up! "))
                .append(Component.literal("[call]").withStyle(style -> style
                        .withColor(ChatFormatting.AQUA)
                        .withClickEvent(new ClickEvent.RunCommand("/call kat"))));

        client.player.displayClientMessage(message, false);
    }

    private ChatFormatting mapRarityColor(String rarity) {
        return switch (rarity) {
            case "UNCOMMON" -> ChatFormatting.GREEN;
            case "RARE" -> ChatFormatting.BLUE;
            case "EPIC" -> ChatFormatting.DARK_PURPLE;
            case "LEGENDARY" -> ChatFormatting.GOLD;
            case "MYTHIC" -> ChatFormatting.LIGHT_PURPLE;
            default -> ChatFormatting.WHITE;
        };
    }

    private static class PendingUpgrade {
        private final String pet;
        private final String rarity;

        private PendingUpgrade(String pet, String rarity) {
            this.pet = pet;
            this.rarity = rarity;
        }
    }

    private static class KatUpgradeReminder {
        private final String pet;
        private final String rarity;
        private final long readyAtMs;

        private KatUpgradeReminder(String pet, String rarity, long readyAtMs) {
            this.pet = pet;
            this.rarity = rarity;
            this.readyAtMs = readyAtMs;
        }
    }

    public static class KatRemindersFileData {
        public List<KatReminderData> reminders = new ArrayList<>();
    }

    // Serialized representation for one reminder entry in kat_reminders.json.
    public static class KatReminderData {
        public String pet;
        public String rarity;
        public long readyAtMs;
    }
}
