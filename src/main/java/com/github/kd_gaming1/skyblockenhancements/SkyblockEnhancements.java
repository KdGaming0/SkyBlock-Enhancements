package com.github.kd_gaming1.skyblockenhancements;

import com.github.kd_gaming1.skyblockenhancements.command.Commands;
import com.github.kd_gaming1.skyblockenhancements.command.ReminderCommand;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.RrvCompat;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.SkyblockInjectionCache;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.SkyblockRrvClientPlugin;
import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.feature.Fullbright;
import com.github.kd_gaming1.skyblockenhancements.feature.ItemGlowManager;
import com.github.kd_gaming1.skyblockenhancements.feature.katreminder.KatReminderFeature;
import com.github.kd_gaming1.skyblockenhancements.feature.missingenchants.MissingEnchants;
import com.github.kd_gaming1.skyblockenhancements.feature.pricing.PriceDataFetcher;
import com.github.kd_gaming1.skyblockenhancements.feature.pricing.PriceTooltipEnhancement;
import com.github.kd_gaming1.skyblockenhancements.feature.reminder.ReminderManager;
import com.github.kd_gaming1.skyblockenhancements.feature.reminder.ReminderStorage;
import com.github.kd_gaming1.skyblockenhancements.feature.reminder.RemindersFileData;
import com.github.kd_gaming1.skyblockenhancements.feature.filter.LogFilterRegistry;
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
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SkyblockEnhancements implements ClientModInitializer {

    public static final String MOD_ID = "skyblock_enhancements";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Ticks between repo staleness checks (~5 minutes at 20 TPS). */
    private static final int REFRESH_CHECK_INTERVAL_TICKS = 12000;

    private final NeuRepoCache cache = new NeuRepoCache();
    private final ReminderStorage reminderStorage =
            new ReminderStorage(
                    FabricLoader.getInstance()
                            .getConfigDir()
                            .resolve(MOD_ID)
                            .resolve("reminders.json"));
    private final ReminderManager reminderManager = new ReminderManager();

    /** Guards against double-saving reminders on disconnect + shutdown. */
    private final AtomicBoolean remindersSaved = new AtomicBoolean(false);

    private volatile CompletableFuture<Void> repoFuture = CompletableFuture.completedFuture(null);
    private final NeuRepoDownloader repoDownloader = new NeuRepoDownloader();
    private long lastRefreshCheckTick = 0;

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
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            HypixelLocationState.reset();
        });

        MissingEnchants.init();
        ItemGlowManager.init();
        Fullbright.init();
        PriceTooltipEnhancement.init();
        PriceDataFetcher.init();

        ClientTickEvents.END_CLIENT_TICK.register(Fullbright::onTick);
        ClientTickEvents.END_CLIENT_TICK.register(client -> IrisCompat.tick());
        ClientTickEvents.END_CLIENT_TICK.register(client -> PriceDataFetcher.tick());

        // Enchant data is independent of RRV — always fetch on startup.
        ClientLifecycleEvents.CLIENT_STARTED.register(
                client -> CompletableFuture.runAsync(() -> {
                    try {
                        cache.downloadAndSave("constants/enchants.json");
                    } catch (Exception e) {
                        LOGGER.error("Failed to download enchants data", e);
                    }
                }));

        initRecipeViewer();
        initReminders();
    }

    /**
     * Sets up the NEU repo download → build → inject pipeline as a single async chain.
     *
     * <p>Pipeline:
     * <pre>
     * downloadAsync()                         // background: download + parse repo
     *   .thenRunAsync(buildCache)             // background: build stacks + recipes + FullStackListCache
     *   .thenRun(injectIfReady)              // inject into RRV (runTasks is already async)
     * </pre>
     *
     * <p>No defensive rebuilds anywhere — each step trusts the previous step completed.
     */
    private void initRecipeViewer() {
        Commands.register();
        if (!RrvCompat.isActive()) return;

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            LOGGER.info("Starting NEU repo download...");
            repoFuture = repoDownloader.downloadAsync()
                    .thenRunAsync(SkyblockInjectionCache::buildCache)
                    .thenRun(SkyblockRrvClientPlugin::injectIfReady)
                    .exceptionally(ex -> {
                        LOGGER.error("Failed to sync NEU repo with RRV", ex);
                        Minecraft.getInstance().execute(() -> {
                            Minecraft mc = Minecraft.getInstance();
                            if (mc.player != null) {
                                mc.gui.getChat().addMessage(Component.literal(
                                        "§c[Skyblock Enhancements] Failed to load SkyBlock item data. " +
                                                "The recipe viewer will be empty. Run §e/skyblockenhancements refresh repoData §cto retry."));
                            }
                        });
                        return null;
                    });
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (++lastRefreshCheckTick < REFRESH_CHECK_INTERVAL_TICKS) return;
            lastRefreshCheckTick = 0;

            if (!repoDownloader.needsRefresh(SkyblockEnhancementsConfig.repoRefreshIntervalHours)) return;

            LOGGER.info("Auto-refreshing NEU repo data...");
            ItemStackBuilder.clearCache();
            repoFuture = repoDownloader.refresh()
                    .thenRunAsync(SkyblockInjectionCache::buildCache)
                    .thenRun(SkyblockRrvClientPlugin::injectIfReady)
                    .exceptionally(ex -> {
                        LOGGER.error("Failed to refresh NEU repo", ex);
                        return null;
                    });
        });
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