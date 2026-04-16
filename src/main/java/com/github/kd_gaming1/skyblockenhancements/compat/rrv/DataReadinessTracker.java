package com.github.kd_gaming1.skyblockenhancements.compat.rrv;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.repo.NeuRepoDownloader;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Orchestrates the wait-for-readiness → build → inject pipeline after downloads finish.
 *
 * <p>Both NEU and Hypixel data expose a {@link CompletableFuture<Boolean>} signal from
 * {@link NeuRepoDownloader}. This class waits on <em>both</em> futures to reach a
 * definitive outcome before injecting:
 *
 * <ul>
 *   <li>{@link FutureResult#READY} — data loaded successfully.</li>
 *   <li>{@link FutureResult#FAILED} — data load failed (no network, no cache).</li>
 *   <li>{@link FutureResult#TIMED_OUT} — future did not complete within the safety
 *       deadline. Treated as a hard failure; the downloader guarantees futures are always
 *       completed, so this should never occur in practice.</li>
 * </ul>
 *
 * <p>Injection is performed once both futures are settled. A {@link FutureResult#FAILED}
 * outcome for Hypixel means essence upgrades are skipped and a tick-based retry is
 * scheduled. A {@link FutureResult#TIMED_OUT} is logged as an error and treated the same
 * as {@link FutureResult#FAILED} (defensive — should not happen).
 */
public final class DataReadinessTracker {

    /**
     * Hard upper bound for waiting on a single future.
     * The downloader always completes its futures (success or failure), so this is
     * only a safety net against an unforeseen bug leaving a future dangling.
     */
    private static final long HARD_TIMEOUT_SECONDS = 120L;

    private DataReadinessTracker() {}

    // ── Three-state result ───────────────────────────────────────────────────────

    private enum FutureResult {
        /** Future completed with {@code true} — data is available. */
        READY,
        /** Future completed with {@code false} — data load confirmed failed. */
        FAILED,
        /**
         * Future did not complete within {@link #HARD_TIMEOUT_SECONDS}.
         * Treated as failure; should not occur given the downloader's guarantee.
         */
        TIMED_OUT
    }

    // ── Entry points ─────────────────────────────────────────────────────────────

    /**
     * Starts the readiness-check chain on a background thread. Called from
     * {@link com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements} after
     * {@link NeuRepoDownloader#downloadAsync} is fired.
     */
    public static CompletableFuture<Void> waitAndInject(NeuRepoDownloader downloader) {
        return CompletableFuture.runAsync(() -> resolveReadinessAndInject(downloader));
    }

    /**
     * Retry variant: re-checks Hypixel readiness and delta-injects essence recipes if ready.
     */
    public static CompletableFuture<Void> retryHypixelAndDeltaInject(NeuRepoDownloader downloader) {
        return CompletableFuture.runAsync(() -> {
            FutureResult result = awaitFuture(downloader.getHypixelReadyFuture(), "Hypixel");
            if (result == FutureResult.READY) {
                SkyblockInjectionCache.buildEssenceRecipesOnly();
                sendChatMessage("§a[Skyblock Enhancements] Essence upgrade recipes loaded successfully.");
            } else {
                LOGGER.warn("Hypixel retry {} — essence upgrades still unavailable.", result);
            }
        });
    }

    /**
     * Retry variant: re-checks NEU readiness and performs a full inject if ready.
     */
    public static CompletableFuture<Void> retryNeuAndInject(NeuRepoDownloader downloader) {
        return CompletableFuture.runAsync(() -> {
            FutureResult result = awaitFuture(downloader.getNeuReadyFuture(), "NEU repo");
            if (result == FutureResult.READY) {
                SkyblockInjectionCache.buildCache();
                SkyblockRrvClientPlugin.injectIfReady();
                sendChatMessage("§a[Skyblock Enhancements] SkyBlock item data loaded successfully.");
            } else {
                LOGGER.warn("NEU repo retry {} — item list still unavailable.", result);
            }
        });
    }

    // ── Core wait + inject ───────────────────────────────────────────────────────

    private static void resolveReadinessAndInject(NeuRepoDownloader downloader) {
        // Wait for both futures to reach a definitive outcome before making any decision.
        // TIMED_OUT only occurs if the downloader has a bug — treated as FAILED defensively.
        FutureResult neuResult     = awaitFuture(downloader.getNeuReadyFuture(),     "NEU repo");
        FutureResult hypixelResult = awaitFuture(downloader.getHypixelReadyFuture(), "Hypixel");

        boolean neuReady     = neuResult     == FutureResult.READY;
        boolean hypixelReady = hypixelResult == FutureResult.READY;

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
            LOGGER.warn("NEU repo data unavailable ({}) — injected Hypixel data only (no item list).", neuResult);
            sendChatMessage(
                    "§e[Skyblock Enhancements] SkyBlock item list unavailable (NEU repo failed). " +
                            "Will retry in " + SkyblockEnhancementsConfig.repoRefreshCheckMinutes + " minutes.");
        }

        if (!hypixelReady) {
            LOGGER.warn("Hypixel data unavailable ({}) — essence upgrades not shown. Will retry in {} min.",
                    hypixelResult, SkyblockEnhancementsConfig.repoRefreshCheckMinutes);
            sendChatMessage(
                    "§e[Skyblock Enhancements] Essence upgrade recipes unavailable (Hypixel API failed). " +
                            "Will retry in " + SkyblockEnhancementsConfig.repoRefreshCheckMinutes + " minutes.");
        }
    }

    // ── Future resolution ────────────────────────────────────────────────────────

    /**
     * Blocks until the future completes or {@link #HARD_TIMEOUT_SECONDS} elapses.
     *
     * <p>Distinguishes three outcomes:
     * <ul>
     *   <li>Completed {@code true}  → {@link FutureResult#READY}</li>
     *   <li>Completed {@code false} → {@link FutureResult#FAILED}</li>
     *   <li>Timeout / exception    → {@link FutureResult#TIMED_OUT}</li>
     * </ul>
     *
     * <p>No polling loop — we block for the full deadline in one call. The downloader
     * guarantees the future is always completed (its try/catch calls {@code complete(false)}),
     * so in the normal case this returns long before the deadline.
     */
    private static FutureResult awaitFuture(CompletableFuture<Boolean> future, String sourceName) {
        try {
            Boolean result = future.get(HARD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(result)) {
                LOGGER.debug("{} ready.", sourceName);
                return FutureResult.READY;
            }
            LOGGER.warn("{} future completed with failure.", sourceName);
            return FutureResult.FAILED;
        } catch (java.util.concurrent.TimeoutException e) {
            LOGGER.error("{} future did not complete within {}s — treating as failure. " +
                            "This is a bug; the downloader should always complete its futures.",
                    sourceName, HARD_TIMEOUT_SECONDS);
            return FutureResult.TIMED_OUT;
        } catch (Exception e) {
            LOGGER.warn("{} readiness future threw: {}", sourceName, e.getMessage());
            return FutureResult.TIMED_OUT;
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