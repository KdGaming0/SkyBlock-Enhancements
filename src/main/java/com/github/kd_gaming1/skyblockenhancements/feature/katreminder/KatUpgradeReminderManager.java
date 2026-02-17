package com.github.kd_gaming1.skyblockenhancements.feature.katreminder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements;
import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.feature.reminder.JsonFileUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Handles Kat dialogs and creates real-time reminders that
 * persist across sessions in a dedicated JSON file.
 */
public class KatUpgradeReminderManager {
    private static final Pattern GIVE_REGEX = Pattern.compile("^I(?:['\\u2019])ll get your (?<pet>.+) upgraded to (?<rarity>[A-Za-z]+) in no time[!.]?$");
    private static final Pattern REMIND_REGEX = Pattern.compile("^I(?:['\\u2019])ll remind you when your (?<pet>.+) is done[!.]?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DURATION_REGEX = Pattern.compile("^Come back in (?<duration>.+) to pick it up[!.]?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DURATION_REMIND_REGEX = Pattern.compile("^I(?:['\\u2019])ll remind you in (?<duration>.+)[!.]?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DURATION_PART_REGEX = Pattern.compile("(\\d+)\\s*(day|days|hour|hours|houre|houres|minute|minutes|second|seconds)", Pattern.CASE_INSENSITIVE);

    private static final String FLOWER_MESSAGE = "A flower? For me? How sweet!";
    private static final String BOUQUET_MESSAGE = "A bouquet? For me? How sweet!";
    private static final String RESET_MESSAGE = "If you have any other pets you'd like to upgrade, you know where to find me!";

    private static final long FLOWER_REDUCTION_MS = 86_400_000L;
    private static final long BOUQUET_REDUCTION_MS = 432_000_000L;

    private final Path storagePath;
    private final BooleanSupplier inHubSupplier;
    private final List<KatUpgradeReminder> activeReminders = new ArrayList<>();

    private PendingUpgrade pendingUpgrade;
    private long lastSecondUpdateMs;

    public KatUpgradeReminderManager(Path storagePath, BooleanSupplier inHubSupplier) {
        this.storagePath = storagePath;
        this.inHubSupplier = inHubSupplier;
    }

    public void load() {
        try {
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

    public List<KatReminderData> getActiveReminders() {
        // Return a detached snapshot for list rendering; callers must not mutate internal state.
        List<KatReminderData> reminders = new ArrayList<>();
        for (KatUpgradeReminder reminder : activeReminders) {
            KatReminderData reminderData = new KatReminderData();
            reminderData.pet = reminder.pet;
            reminderData.rarity = reminder.rarity;
            reminderData.readyAtMs = reminder.readyAtMs;
            reminders.add(reminderData);
        }
        return reminders;
    }

    public int removeAllReminders() {
        // Command-driven bulk delete for Kat reminders.
        int count = activeReminders.size();
        activeReminders.clear();
        reset();
        save();
        return count;
    }

    public void onNpcDialog(String npcName, String dialog) {
        if (!SkyblockEnhancementsConfig.setKatReminderForPetUpgrades) return;
        if (!"Kat".equals(npcName)) return;

        if (handleSpecialDialog(dialog)) return;
        if (tryReadUpgradeStart(dialog)) return;
        if (tryReadReminderStart(dialog)) return;
        if (tryCreateReminderFromDuration(dialog, DURATION_REGEX)) return;
        tryCreateReminderFromDuration(dialog, DURATION_REMIND_REGEX);
    }

    public void onClientTick(Minecraft client) {
        if (!SkyblockEnhancementsConfig.setKatReminderForPetUpgrades) return;
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
            if (!inHubSupplier.getAsBoolean()) continue;

            sendReadyMessage(client, reminder);
            iterator.remove();
            changed = true;
        }

        if (changed) {
            save();
        }
    }

    private boolean handleSpecialDialog(String dialog) {
        if (FLOWER_MESSAGE.equals(dialog)) {
            reduceMostRecentReminder(FLOWER_REDUCTION_MS);
            return true;
        }
        if (BOUQUET_MESSAGE.equals(dialog)) {
            reduceMostRecentReminder(BOUQUET_REDUCTION_MS);
            return true;
        }
        if (RESET_MESSAGE.equals(dialog)) {
            reset();
            return true;
        }
        return false;
    }

    private boolean tryReadUpgradeStart(String dialog) {
        Matcher giveMatcher = GIVE_REGEX.matcher(dialog);
        if (!giveMatcher.matches()) return false;

        String pet = giveMatcher.group("pet").trim();
        String rarity = normalizeRarity(giveMatcher.group("rarity"));
        if (rarity == null) return false;

        pendingUpgrade = new PendingUpgrade(pet, rarity);
        return true;
    }

    private boolean tryReadReminderStart(String dialog) {
        Matcher remindMatcher = REMIND_REGEX.matcher(dialog);
        if (!remindMatcher.matches()) return false;

        pendingUpgrade = new PendingUpgrade(remindMatcher.group("pet").trim(), "COMMON");
        return true;
    }

    private boolean tryCreateReminderFromDuration(String dialog, Pattern durationPattern) {
        if (pendingUpgrade == null) return false;

        Matcher durationMatcher = durationPattern.matcher(dialog);
        if (!durationMatcher.matches()) return false;

        long durationMs = parseLongDurationMilliseconds(durationMatcher.group("duration"));
        if (durationMs <= 0) return false;

        activeReminders.add(new KatUpgradeReminder(
                pendingUpgrade.pet,
                pendingUpgrade.rarity,
                System.currentTimeMillis() + durationMs
        ));
        reset();
        save();
        return true;
    }

    private long parseLongDurationMilliseconds(String rawDuration) {
        Matcher partMatcher = DURATION_PART_REGEX.matcher(rawDuration);

        long totalSeconds = 0L;
        boolean foundPart = false;

        while (partMatcher.find()) {
            long amount = Long.parseLong(partMatcher.group(1));
            if (amount <= 0) continue;

            String unit = partMatcher.group(2).toLowerCase(Locale.ROOT);
            totalSeconds += switch (unit) {
                case "day", "days" -> amount * 86_400L;
                case "hour", "hours", "houre", "houres" -> amount * 3_600L;
                case "minute", "minutes" -> amount * 60L;
                case "second", "seconds" -> amount;
                default -> 0L;
            };
            foundPart = true;
        }

        if (!foundPart) return 0L;
        return totalSeconds * 1000L;
    }

    private void reduceMostRecentReminder(long reductionMs) {
        if (activeReminders.isEmpty()) return;

        int index = activeReminders.size() - 1;
        KatUpgradeReminder reminder = activeReminders.get(index);
        long reducedReadyAt = Math.max(System.currentTimeMillis(), reminder.readyAtMs - reductionMs);

        activeReminders.set(index, new KatUpgradeReminder(reminder.pet, reminder.rarity, reducedReadyAt));
        save();
    }

    private void reset() {
        pendingUpgrade = null;
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
        MutableComponent message = Component.literal("Your ")
                .append(Component.literal(reminder.pet).withStyle(rarityColor))
                .append(Component.literal(" is Ready to pick up! "))
                .append(Component.literal("[call]").withStyle(style -> style
                        .withColor(ChatFormatting.AQUA)
                        .withClickEvent(new ClickEvent.RunCommand("/call kat"))));

        assert client.player != null;
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

    private record PendingUpgrade(String pet, String rarity) { }

    private record KatUpgradeReminder(String pet, String rarity, long readyAtMs) { }

    public static class KatRemindersFileData {
        public List<KatReminderData> reminders = new ArrayList<>();
    }

    public static class KatReminderData {
        public String pet;
        public String rarity;
        public long readyAtMs;
    }
}
