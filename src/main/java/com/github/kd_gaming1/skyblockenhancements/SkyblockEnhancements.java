package com.github.kd_gaming1.skyblockenhancements;

import com.github.kd_gaming1.skyblockenhancements.command.Commands;
import com.github.kd_gaming1.skyblockenhancements.command.ReminderCommand;
import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.feature.ItemGlowManager;
import com.github.kd_gaming1.skyblockenhancements.feature.missingenchants.MissingEnchants;
import com.github.kd_gaming1.skyblockenhancements.feature.katreminder.KatReminderFeature;
import com.github.kd_gaming1.skyblockenhancements.feature.reminder.ReminderManager;
import com.github.kd_gaming1.skyblockenhancements.feature.reminder.ReminderStorage;
import com.github.kd_gaming1.skyblockenhancements.feature.reminder.RemindersFileData;
import com.github.kd_gaming1.skyblockenhancements.util.HypixelLocationState;
import com.github.kd_gaming1.skyblockenhancements.util.NeuRepoCache;

import eu.midnightdust.lib.config.MidnightConfig;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.azureaaron.hmapi.network.HypixelNetworking;
import net.azureaaron.hmapi.network.packet.v1.s2c.LocationUpdateS2CPacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
//? if >=1.21.11 {
/*import net.minecraft.util.Util;
 *///?} else {
import net.minecraft.Util;
//?}

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public class SkyblockEnhancements implements ClientModInitializer {
    public static final String MOD_ID = "skyblock_enhancements";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private final NeuRepoCache cache = new NeuRepoCache();

    private final ReminderStorage reminderStorage = new ReminderStorage(FabricLoader.getInstance()
            .getConfigDir().resolve(MOD_ID).resolve("reminders.json"));
    private final ReminderManager reminderManager = new ReminderManager();
    private final Object saveLock = new Object();

    @Override public void onInitializeClient() {
        MidnightConfig.init(MOD_ID, SkyblockEnhancementsConfig.class);

        HypixelNetworking.registerToEvents(Util.make(new Object2IntOpenHashMap<>(), map -> map.put(LocationUpdateS2CPacket.ID, 1)));

        HypixelLocationState.register();

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> HypixelLocationState.reset());

        // Initializing features
        MissingEnchants.init();
        ItemGlowManager.init();

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> CompletableFuture.runAsync(() -> {
            try {
                cache.downloadAndSave("constants/enchants.json");
            } catch (Exception e) {
                LOGGER.error("Failed to download enchants data", e);
            }
        }));

        // Commands
        Commands.register();
        ReminderCommand.register(reminderManager);

        // Remind me features
        reminderStorage.load();
        reminderManager.loadFromStorage(reminderStorage.getRemindersData());
        KatReminderFeature.init(MOD_ID);

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
}
