package com.github.kd_gaming1.skyblockenhancements.repo;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Immutable snapshot of an in-progress NEU repo download.
 *
 * <p>Both futures are guaranteed to be completed (successfully or with {@code false})
 * by {@link NeuRepoDownloader}. Callers must not reassign these futures;
 * instead, start a new download to obtain a fresh session.
 *
 * @param neuReady     completes {@code true} when NEU item data is loaded into {@link com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItemRegistry}
 * @param hypixelReady completes {@code true} when Hypixel item data is loaded into {@link com.github.kd_gaming1.skyblockenhancements.repo.hypixel.HypixelItemsRegistry}
 * @param startedAt    when the session began
 */
public record DownloadSession(
    CompletableFuture<Boolean> neuReady,
    CompletableFuture<Boolean> hypixelReady,
    Instant startedAt
) {}
