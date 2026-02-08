package com.github.kd_gaming1.skyblockenhancements;

import com.github.kd_gaming1.skyblockenhancements.command.Commands;
import com.github.kd_gaming1.skyblockenhancements.command.ReminderCommand;
import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.feature.MissingEnchants;
import com.github.kd_gaming1.skyblockenhancements.feature.glow.ItemGlowManager;
import com.github.kd_gaming1.skyblockenhancements.feature.reminder.ReminderManager;
import com.github.kd_gaming1.skyblockenhancements.feature.reminder.ReminderStorage;
import com.github.kd_gaming1.skyblockenhancements.feature.reminder.RemindersFileData;
import com.github.kd_gaming1.skyblockenhancements.util.NeuRepoCache;
import eu.midnightdust.lib.config.MidnightConfig;
import net.azureaaron.hmapi.events.HypixelPacketEvents;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class SkyblockEnhancements implements ClientModInitializer {
    public static final String MOD_ID = "skyblock_enhancements";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private final NeuRepoCache cache = new NeuRepoCache();

    private final ReminderStorage reminderStorage = new ReminderStorage(FabricLoader.getInstance()
            .getConfigDir().resolve(MOD_ID).resolve("reminders.json"));
    private final ReminderManager reminderManager = new ReminderManager();
    private final Object saveLock = new Object();

    public static final AtomicBoolean helloPacketReceived = new AtomicBoolean(false);

    @Override public void onInitializeClient() {
        MidnightConfig.init(MOD_ID, SkyblockEnhancementsConfig.class);

        HypixelPacketEvents.HELLO.register((packet) -> helloPacketReceived.set(true));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());

        // Initializing features
        MissingEnchants.init();

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> CompletableFuture.runAsync(() -> {
            try {
                cache.downloadAndSave("constants/enchants.json");
            } catch (Exception e) {
                LOGGER.error("Failed to download enchants data", e);
            }
        }));

        ClientTickEvents.END_CLIENT_TICK.register(client -> ItemGlowManager.onClientTick());

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ItemGlowManager.clearAllAndRemoveTeamEntries(client));

        // Commands
        Commands.register();
        ReminderCommand.register(reminderManager);

        // Remind me features
        reminderStorage.load();
        reminderManager.loadFromStorage(reminderStorage.getRemindersData());

        ClientTickEvents.END_CLIENT_TICK.register(reminderManager::onClientTick);

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> saveReminders());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> saveReminders());
    }

    private void saveReminders() {
        synchronized (saveLock) {
            RemindersFileData data = reminderManager.saveToStorage();
            reminderStorage.setRemindersData(data);
            reminderStorage.save();
        }
    }

    private void reset() {
        helloPacketReceived.set(false);
    }
}