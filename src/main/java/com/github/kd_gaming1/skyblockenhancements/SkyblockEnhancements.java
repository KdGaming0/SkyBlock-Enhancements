package com.github.kd_gaming1.skyblockenhancements;

import com.github.kd_gaming1.skyblockenhancements.command.Commands;
import com.github.kd_gaming1.skyblockenhancements.command.ReminderCommand;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.RrvCompat;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.SkyblockRrvClientPlugin;
import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.feature.Fullbright;
import com.github.kd_gaming1.skyblockenhancements.feature.ItemGlowManager;
import com.github.kd_gaming1.skyblockenhancements.feature.chat.tabs.ChatTabState;
import com.github.kd_gaming1.skyblockenhancements.feature.katreminder.KatReminderFeature;
import com.github.kd_gaming1.skyblockenhancements.feature.missingenchants.MissingEnchants;
import com.github.kd_gaming1.skyblockenhancements.feature.reminder.ReminderManager;
import com.github.kd_gaming1.skyblockenhancements.feature.reminder.ReminderStorage;
import com.github.kd_gaming1.skyblockenhancements.feature.reminder.RemindersFileData;
import com.github.kd_gaming1.skyblockenhancements.repo.ItemStackBuilder;
import com.github.kd_gaming1.skyblockenhancements.repo.NeuRepoDownloader;
import com.github.kd_gaming1.skyblockenhancements.util.HypixelLocationState;
import com.github.kd_gaming1.skyblockenhancements.util.IrisCompat;
import com.github.kd_gaming1.skyblockenhancements.util.NeuRepoCache;
import eu.midnightdust.lib.config.MidnightConfig;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import net.azureaaron.hmapi.network.HypixelNetworking;
import net.azureaaron.hmapi.network.packet.v1.s2c.LocationUpdateS2CPacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
//? if >=1.21.11 {
import net.minecraft.util.Util;
        //?} else {
/*import net.minecraft.Util;
 *///?}
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SkyblockEnhancements implements ClientModInitializer {
    public static final String MOD_ID = "skyblock_enhancements";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private final NeuRepoCache cache = new NeuRepoCache();
    private final ReminderStorage reminderStorage =
            new ReminderStorage(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("reminders.json"));
    private final ReminderManager reminderManager = new ReminderManager();

    private final AtomicBoolean remindersSaved = new AtomicBoolean(false);

    private volatile CompletableFuture<Void> repoFuture = CompletableFuture.completedFuture(null);
    private final NeuRepoDownloader repoDownloader = new NeuRepoDownloader();
    private long lastRefreshCheckTick = 0;

    private static SkyblockEnhancements instance;

    @Override
    public void onInitializeClient() {
        instance = this;

        MidnightConfig.init(MOD_ID, SkyblockEnhancementsConfig.class);

        HypixelNetworking.registerToEvents(
                Util.make(new Object2IntOpenHashMap<>(), map -> map.put(LocationUpdateS2CPacket.ID, 1)));

        HypixelLocationState.register();
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            HypixelLocationState.reset();
            ChatTabState.reset();
        });

        MissingEnchants.init();
        ItemGlowManager.init();
        Fullbright.init();

        ClientTickEvents.END_CLIENT_TICK.register(Fullbright::onTick);
        ClientTickEvents.END_CLIENT_TICK.register(client -> IrisCompat.tick());

        ClientLifecycleEvents.CLIENT_STARTED.register(
                client ->
                        CompletableFuture.runAsync(
                                () -> {
                                    try {
                                        cache.downloadAndSave("constants/enchants.json");
                                    } catch (Exception e) {
                                        LOGGER.error("Failed to download enchants data", e);
                                    }
                                }));
        if (RrvCompat.isActive()) {
            ClientLifecycleEvents.CLIENT_STARTED.register(
                    client -> {
                        repoFuture = repoDownloader.downloadAsync();
                        repoFuture.thenRun(SkyblockRrvClientPlugin::spoofRrvCache);
                    });

            // Periodic auto-refresh check (every ~5 min of ticks)
            ClientTickEvents.END_CLIENT_TICK.register(
                    client -> {
                        if (++lastRefreshCheckTick < 6000) return; // ~5 min
                        lastRefreshCheckTick = 0;
                        if (repoDownloader.needsRefresh(
                                SkyblockEnhancementsConfig.repoRefreshIntervalHours)) {
                            ItemStackBuilder.clearCache();
                            repoFuture = repoDownloader.refresh();
                            repoFuture.thenRun(SkyblockRrvClientPlugin::spoofRrvCache);
                            LOGGER.info("Auto-refreshing NEU repo data...");
                            ItemStackBuilder.clearCache();
                            repoDownloader.refresh().thenRun(SkyblockRrvClientPlugin::spoofRrvCache);
                        }
                    });
        }

        Commands.register();

        reminderStorage.load();
        reminderManager.loadFromStorage(reminderStorage.getRemindersData());
        KatReminderFeature.init(MOD_ID);

        ReminderCommand.register(reminderManager, rm -> forceSaveReminders());

        ClientTickEvents.END_CLIENT_TICK.register(reminderManager::onClientTick);

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> saveRemindersOnce());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> saveRemindersOnce());
    }

    private void forceSaveReminders() {
        RemindersFileData data = reminderManager.saveToStorage();
        reminderStorage.setRemindersData(data);
        reminderStorage.save();
        remindersSaved.set(false);
    }

    private void saveRemindersOnce() {
        if (remindersSaved.compareAndSet(false, true)) {
            RemindersFileData data = reminderManager.saveToStorage();
            reminderStorage.setRemindersData(data);
            reminderStorage.save();
        }
    }

    public NeuRepoDownloader getRepoDownloader() {
        return repoDownloader;
    }

    public static SkyblockEnhancements getInstance() {
        return instance;
    }

    public CompletableFuture<Void> getRepoFuture() {
        return repoFuture;
    }
}
