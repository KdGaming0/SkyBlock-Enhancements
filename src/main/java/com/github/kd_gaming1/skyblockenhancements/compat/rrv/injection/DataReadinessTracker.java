package com.github.kd_gaming1.skyblockenhancements.compat.rrv.injection;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.repo.DownloadSession;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.SkyblockRrvClientPlugin;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Orchestrates the wait-for-readiness → build → inject pipeline after downloads finish.
 *
 * <p>Both NEU and Hypixel data expose a {@link CompletableFuture<Boolean>} signal from
 * {@link com.github.kd_gaming1.skyblockenhancements.repo.NeuRepoDownloader} via an immutable
 * {@link DownloadSession}. This class composes those futures asynchronously on a dedicated
 * single-thread executor to avoid starving the shared {@code ForkJoinPool.commonPool()}.
 */
public final class DataReadinessTracker {

    /**
     * Hard upper bound for waiting on a single future.
     * The downloader always completes its futures (success or failure), so this is
     * only a safety net against an unforeseen bug leaving a future dangling.
     */
    private static final long HARD_TIMEOUT_SECONDS = 120L;

    /** Dedicated executor for cache-build and injection work. */
    private static final Executor BACKGROUND = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SkyblockEnhancements-DataReadiness");
        t.setDaemon(true);
        return t;
    });

    private DataReadinessTracker() {}

    // ── Entry points ─────────────────────────────────────────────────────────────

    /**
     * Starts the readiness-check chain on a background thread. Called from
     * {@link com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements} after
     * {@link com.github.kd_gaming1.skyblockenhancements.repo.NeuRepoDownloader#startDownload} is fired.
     */
    public static CompletableFuture<Void> waitAndInject(DownloadSession session) {
        return session.neuReady()
                .thenAcceptBothAsync(session.hypixelReady(), (neuOk, hypixelOk) -> {
                    resolveReadinessAndInject(session, neuOk, hypixelOk);
                }, BACKGROUND)
                .orTimeout(HARD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .whenComplete((unused, throwable) -> {
                    if (throwable != null) {
                        LOGGER.error("Data readiness pipeline failed", throwable);
                    }
                });
    }

    /**
     * Retry variant: re-checks Hypixel readiness and delta-injects essence recipes if ready.
     */
    public static CompletableFuture<Void> retryHypixelAndDeltaInject(DownloadSession session) {
        return session.hypixelReady()
                .thenAcceptAsync(ready -> {
                    if (Boolean.TRUE.equals(ready)) {
                        SkyblockInjectionCache.buildEssenceRecipesOnly();
                        sendChatMessage("§a[Skyblock Enhancements] Essence upgrade recipes loaded successfully.");
                    } else {
                        LOGGER.warn("Hypixel retry failed — essence upgrades still unavailable.");
                    }
                }, BACKGROUND)
                .orTimeout(HARD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .whenComplete((unused, throwable) -> {
                    if (throwable != null) {
                        LOGGER.error("Hypixel retry pipeline failed", throwable);
                    }
                });
    }

    /**
     * Retry variant: re-checks NEU readiness and performs a full inject if ready.
     */
    public static CompletableFuture<Void> retryNeuAndInject(DownloadSession session) {
        return session.neuReady()
                .thenAcceptAsync(ready -> {
                    if (Boolean.TRUE.equals(ready)) {
                        SkyblockInjectionCache.buildCache();
                        SkyblockRrvClientPlugin.injectIfReady();
                        sendChatMessage("§a[Skyblock Enhancements] SkyBlock item data loaded successfully.");
                    } else {
                        LOGGER.warn("NEU repo retry failed — item list still unavailable.");
                    }
                }, BACKGROUND)
                .orTimeout(HARD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .whenComplete((unused, throwable) -> {
                    if (throwable != null) {
                        LOGGER.error("NEU retry pipeline failed", throwable);
                    }
                });
    }

    // ── Core wait + inject ───────────────────────────────────────────────────────

    private static void resolveReadinessAndInject(DownloadSession session, boolean neuReady, boolean hypixelReady) {
        if (!neuReady && !hypixelReady) {
            LOGGER.error("Both NEU repo and Hypixel data failed to load — item list will be empty.");
            sendChatMessage(
                    "§c[Skyblock Enhancements] Failed to load SkyBlock item data from both sources. " +
                            "Run §e/skyblockenhancements refresh repoData §cto retry.");
            return;
        }

        SkyblockInjectionCache.buildCache();
        SkyblockRrvClientPlugin.injectIfReady();

        if (!neuReady) {
            LOGGER.warn("NEU repo data unavailable — injected Hypixel data only (no item list).");
            sendChatMessage(
                    "§e[Skyblock Enhancements] SkyBlock item list unavailable (NEU repo failed). " +
                            "Will retry in " + SkyblockEnhancementsConfig.repoRefreshCheckMinutes + " minutes.");
        }

        if (!hypixelReady) {
            LOGGER.warn("Hypixel data unavailable — essence upgrades not shown. Will retry in {} min.",
                    SkyblockEnhancementsConfig.repoRefreshCheckMinutes);
            sendChatMessage(
                    "§e[Skyblock Enhancements] Essence upgrade recipes unavailable (Hypixel API failed). " +
                            "Will retry in " + SkyblockEnhancementsConfig.repoRefreshCheckMinutes + " minutes.");
        }
    }

    // ── Chat message helper ──────────────────────────────────────────────────────

    private static void sendChatMessage(String message) {
        Minecraft.getInstance().execute(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.gui.getChat().addMessage(Component.literal(message));
            }
        });
    }
}
