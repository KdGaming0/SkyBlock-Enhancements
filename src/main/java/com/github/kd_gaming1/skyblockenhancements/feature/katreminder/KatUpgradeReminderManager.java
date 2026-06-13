package com.github.kd_gaming1.skyblockenhancements.feature.katreminder;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BooleanSupplier;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Coordinates Kat pet-upgrade reminders: parses NPC dialog, persists reminders,
 * and fires chat notifications when upgrades are ready.
 *
 * <p>Delegation:
 * <ul>
 *   <li>Dialog parsing → {@link KatDialogParser}</li>
 *   <li>File persistence → {@link KatReminderStore}</li>
 * </ul>
 */
public class KatUpgradeReminderManager {

    private final BooleanSupplier inHubSupplier;
    private final KatReminderStore store;

    private final List<KatUpgradeReminder> activeReminders = new ArrayList<>();

    private KatDialogParser.UpgradeStart pendingUpgrade;
    private long lastSecondUpdateMs;
    private boolean pendingBrokenFileNotice;

    public KatUpgradeReminderManager(java.nio.file.Path storagePath, BooleanSupplier inHubSupplier) {
        this.inHubSupplier = inHubSupplier;
        this.store = new KatReminderStore(storagePath);
    }

    // ── Persistence ──────────────────────────────────────────────────────────────

    public void load() {
        KatReminderStore.LoadResult result = store.load();
        activeReminders.clear();
        activeReminders.addAll(result.reminders());
        pendingBrokenFileNotice = result.wasRecoveredFromBrokenFile();
    }

    public void save() {
        store.save(activeReminders);
    }

    public List<KatReminderStore.KatReminderData> getActiveReminders() {
        List<KatReminderStore.KatReminderData> reminders = new ArrayList<>();
        for (KatUpgradeReminder r : activeReminders) {
            KatReminderStore.KatReminderData d = new KatReminderStore.KatReminderData();
            d.pet = r.pet();
            d.rarity = r.rarity();
            d.readyAtMs = r.readyAtMs();
            reminders.add(d);
        }
        return reminders;
    }

    public int removeAllReminders() {
        int count = activeReminders.size();
        activeReminders.clear();
        reset();
        save();
        return count;
    }

    // ── Dialog handling ──────────────────────────────────────────────────────────

    public void onNpcDialog(String npcName, String dialog) {
        if (!SkyblockEnhancementsConfig.setKatReminderForPetUpgrades) return;
        if (!"Kat".equals(npcName)) return;

        KatDialogParser.SpecialDialog special = KatDialogParser.detectSpecialDialog(dialog);
        if (special != KatDialogParser.SpecialDialog.NONE) {
            handleSpecialDialog(special);
            return;
        }

        KatDialogParser.UpgradeStart upgrade = KatDialogParser.tryParseUpgradeStart(dialog);
        if (upgrade != null) {
            pendingUpgrade = upgrade;
            return;
        }

        KatDialogParser.ReminderStart reminder = KatDialogParser.tryParseReminderStart(dialog);
        if (reminder != null) {
            pendingUpgrade = new KatDialogParser.UpgradeStart(reminder.pet(), "COMMON");
            return;
        }

        tryCreateReminderFromDuration(dialog);
    }

    private void handleSpecialDialog(KatDialogParser.SpecialDialog special) {
        switch (special) {
            case FLOWER -> reduceMostRecentReminder(KatDialogParser.FLOWER_REDUCTION_MS);
            case BOUQUET -> reduceMostRecentReminder(KatDialogParser.BOUQUET_REDUCTION_MS);
            case RESET -> reset();
            default -> { /* no-op */ }
        }
    }

    private void tryCreateReminderFromDuration(String dialog) {
        if (pendingUpgrade == null) return;

        long durationMs = KatDialogParser.parseDuration(dialog);
        if (durationMs <= 0) return;

        activeReminders.add(new KatUpgradeReminder(
                pendingUpgrade.pet(), pendingUpgrade.rarity(),
                System.currentTimeMillis() + durationMs));
        reset();
        save();
    }

    private void reduceMostRecentReminder(long reductionMs) {
        if (activeReminders.isEmpty()) return;

        int index = activeReminders.size() - 1;
        KatUpgradeReminder r = activeReminders.get(index);
        long reducedReadyAt = Math.max(System.currentTimeMillis(), r.readyAtMs() - reductionMs);

        activeReminders.set(index, new KatUpgradeReminder(r.pet(), r.rarity(), reducedReadyAt));
        save();
    }

    private void reset() {
        pendingUpgrade = null;
    }

    // ── Tick / firing ────────────────────────────────────────────────────────────

    public void onClientTick(Minecraft client) {
        if (pendingBrokenFileNotice && client.player != null) {
            sendBrokenFileMessage(client);
            pendingBrokenFileNotice = false;
        }

        if (!SkyblockEnhancementsConfig.setKatReminderForPetUpgrades) return;
        if (client.player == null) return;

        long now = System.currentTimeMillis();
        if (lastSecondUpdateMs == 0) {
            lastSecondUpdateMs = now;
            return;
        }
        if (now - lastSecondUpdateMs < 1000) return;
        lastSecondUpdateMs = now;

        boolean changed = false;
        Iterator<KatUpgradeReminder> it = activeReminders.iterator();
        while (it.hasNext()) {
            KatUpgradeReminder r = it.next();
            if (now < r.readyAtMs()) continue;
            if (!inHubSupplier.getAsBoolean()) continue;

            sendReadyMessage(client, r);
            it.remove();
            changed = true;
        }

        if (changed) save();
    }

    // ── Chat messages ────────────────────────────────────────────────────────────

    private void sendBrokenFileMessage(Minecraft client) {
        MutableComponent message = Component.literal("[KatReminder]").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("Your ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("KatReminder File ").withStyle(ChatFormatting.AQUA))
                .append(Component.literal("was ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("broken").withStyle(ChatFormatting.RED))
                .append(Component.literal(". We've created a fresh one for you and backed up your old file.").withStyle(ChatFormatting.YELLOW));

        assert client.player != null;
        client.player.sendSystemMessage(message);
    }

    private void sendReadyMessage(Minecraft client, KatUpgradeReminder reminder) {
        ChatFormatting rarityColor = mapRarityColor(reminder.rarity());
        MutableComponent message = Component.literal("Your ")
                .append(Component.literal(reminder.pet()).withStyle(rarityColor))
                .append(Component.literal(" is Ready to pick up! "))
                .append(Component.literal("[call]").withStyle(style -> style
                        .withColor(ChatFormatting.AQUA)
                        .withClickEvent(new ClickEvent.RunCommand("/call kat"))));

        assert client.player != null;
        client.player.sendSystemMessage(message);
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
}
