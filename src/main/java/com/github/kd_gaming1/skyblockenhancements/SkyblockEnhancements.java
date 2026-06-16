package com.github.kd_gaming1.skyblockenhancements;

import com.github.kd_gaming1.skyblockenhancements.command.Commands;
import com.github.kd_gaming1.skyblockenhancements.command.DebugCommand;
import com.github.kd_gaming1.skyblockenhancements.command.ReminderCommand;
import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.feature.Fullbright;
import com.github.kd_gaming1.skyblockenhancements.feature.ItemGlowManager;
import com.github.kd_gaming1.skyblockenhancements.feature.filter.LogFilterRegistry;
import com.github.kd_gaming1.skyblockenhancements.feature.katreminder.KatReminderFeature;
import com.github.kd_gaming1.skyblockenhancements.feature.mining.PickaxeAbilityNotifier;
import com.github.kd_gaming1.skyblockenhancements.feature.mining.PingOffsetMiningFeature;
import com.github.kd_gaming1.skyblockenhancements.feature.missingenchants.MissingEnchants;
import com.github.kd_gaming1.skyblockenhancements.feature.pricing.PriceDataFetcher;
import com.github.kd_gaming1.skyblockenhancements.feature.pricing.PriceStore;
import com.github.kd_gaming1.skyblockenhancements.feature.pricing.PriceTooltipEnhancement;
import com.github.kd_gaming1.skyblockenhancements.feature.pricing.PriceTooltipKeybinds;
import com.github.kd_gaming1.skyblockenhancements.feature.slotlock.SlotLockManager;
import com.github.kd_gaming1.skyblockenhancements.feature.reminder.ReminderManager;
import com.github.kd_gaming1.skyblockenhancements.feature.reminder.ReminderNotifier;
import com.github.kd_gaming1.skyblockenhancements.feature.reminder.ReminderStorage;
import com.github.kd_gaming1.skyblockenhancements.feature.reminder.RemindersFileData;
import com.github.kd_gaming1.skyblockenhancements.util.HypixelLocationState;
import com.github.kd_gaming1.skyblockenhancements.util.IrisCompat;
import com.github.kd_gaming1.skyblockenhancements.util.ProfileIdTracker;
import com.github.kd_gaming1.skyblockenhancements.util.tab.TabListMonitor;
import com.github.kd_gaming1.skyblockenhancements.util.tool.HeldItemTracker;
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
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SkyblockEnhancements implements ClientModInitializer {

    public static final String MOD_ID = "skyblock_enhancements";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private final ReminderStorage reminderStorage =
            new ReminderStorage(
                    FabricLoader.getInstance()
                            .getConfigDir()
                            .resolve(MOD_ID)
                            .resolve("reminders.json"));
    private final PriceStore priceStore = new PriceStore();
    private final PriceDataFetcher priceFetcher = new PriceDataFetcher(new SkyblockEnhancementsConfig(), priceStore);
    private final PriceTooltipEnhancement priceTooltip = new PriceTooltipEnhancement(new SkyblockEnhancementsConfig(), priceStore);
    private final ReminderNotifier reminderNotifier = new ReminderNotifier(new SkyblockEnhancementsConfig());
    private final ReminderManager reminderManager = new ReminderManager(reminderNotifier);

    /** Guards against double-saving reminders on disconnect + shutdown. */
    private final AtomicBoolean remindersSaved = new AtomicBoolean(false);

    private static SkyblockEnhancements instance;

    @Override
    public void onInitializeClient() {
        instance = this;

        MidnightConfig.init(MOD_ID, SkyblockEnhancementsConfig.class);

        LogFilterRegistry.register();

        // Subscribe to Hypixel location packets so we can track which island the player is on.
        HypixelNetworking.registerToEvents(
                Util.make(new Object2IntOpenHashMap<>(), map -> map.put(LocationUpdateS2CPacket.ID, 1)));

        HypixelLocationState.register();
        ProfileIdTracker.register(
                FabricLoader.getInstance()
                        .getConfigDir()
                        .resolve(MOD_ID)
                        .resolve("profile_id_cache.json"));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            HypixelLocationState.reset();
        });

        DebugCommand.register();
        Commands.register();

        HeldItemTracker.register();
        PingOffsetMiningFeature.register();
        PickaxeAbilityNotifier.init();
        TabListMonitor.register();
        MissingEnchants.init();
        ItemGlowManager.init();
        Fullbright.init();
        PriceTooltipKeybinds.init();
        SlotLockManager.init(
                FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("slot_locks.json"));
        priceTooltip.register();
        priceFetcher.start();

        ClientTickEvents.END_CLIENT_TICK.register(Fullbright::onTick);
        ClientTickEvents.END_CLIENT_TICK.register(client -> IrisCompat.tick());
        ClientTickEvents.END_CLIENT_TICK.register(client -> priceFetcher.tick());

        // Enchant data is independent of RRV — always fetch on startup.
        ClientLifecycleEvents.CLIENT_STARTED.register(
                client -> CompletableFuture.runAsync(() -> {
                    try {
                        downloadEnchantsData();
                    } catch (Exception e) {
                        LOGGER.error("Failed to download enchants data", e);
                    }
                }));

        initReminders();
    }

    /** Loads persisted reminders, registers the /reminder command, and hooks save events. */
    private void initReminders() {
        reminderStorage.load();
        reminderManager.loadFromStorage(reminderStorage.getRemindersData());
        KatReminderFeature.init(MOD_ID);

        ReminderCommand.register(reminderManager, rm -> forceSaveReminders());

        ClientTickEvents.END_CLIENT_TICK.register(reminderManager::onClientTick);

        // Persist reminders on disconnect and shutdown — whichever fires first wins.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> saveRemindersOnce());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> saveRemindersOnce());
    }

    /** Immediately persists reminders and resets the "already saved" guard. */
    private void forceSaveReminders() {
        RemindersFileData data = reminderManager.saveToStorage();
        reminderStorage.setRemindersData(data);
        reminderStorage.save();
        remindersSaved.set(false);
    }

    /** Persists reminders exactly once per session via CAS guard. */
    private void saveRemindersOnce() {
        if (remindersSaved.compareAndSet(false, true)) {
            RemindersFileData data = reminderManager.saveToStorage();
            reminderStorage.setRemindersData(data);
            reminderStorage.save();
        }
    }

    private static void downloadEnchantsData() throws Exception {
        String url = "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/constants/enchants.json";
        java.net.http.HttpClient http = java.net.http.HttpClient.newHttpClient();
        com.google.gson.Gson gson = new com.google.gson.GsonBuilder().create();
        com.github.kd_gaming1.skyblockenhancements.repo.network.JsonHttpClient client =
                new com.github.kd_gaming1.skyblockenhancements.repo.network.JsonHttpClient(http, gson);
        String text = client.getString(url);
        if (text != null) {
            java.nio.file.Path target = FabricLoader.getInstance()
                    .getConfigDir()
                    .resolve(MOD_ID)
                    .resolve("data")
                    .resolve("constants")
                    .resolve("enchants.json");
            com.github.kd_gaming1.skyblockenhancements.repo.io.AtomicFileWriter.writeString(target, text);
        }
    }

    public static SkyblockEnhancements getInstance() {
        return instance;
    }
}
