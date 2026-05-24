package com.github.kd_gaming1.skyblockenhancements.util.tab;

import com.github.kd_gaming1.skyblockenhancements.util.HypixelLocationState;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Observes the client tab list, detects changes via fast hashing, and triggers
 * re-parsing only when necessary.
 *
 * <p>Key design decisions for performance:
 * <ul>
 *   <li>Polls only every {@value #POLL_INTERVAL_TICKS} ticks — parsing is unnecessary
 *       every single tick because tab stats change at most a few times per second.</li>
 *   <li>Change detection is a rolling XOR hash (zero-allocation, O(n)).</li>
 *   <li>Parsing is skipped entirely when the player is not on SkyBlock.</li>
 *   <li>Raw lines are exposed as an unmodifiable view for debugging only.</li>
 * </ul>
 */
public final class TabListMonitor {

    private static final int POLL_INTERVAL_TICKS = 5;

    private static int tickCounter = 0;
    private static int lastContentHash = 0;
    private static boolean refreshedThisTick = false;
    private static boolean registered = false;

    /** Ordered snapshot of raw tab-line texts. Guarded by client-thread single-writer. */
    private static final List<String> rawLines = new ArrayList<>(80);

    private TabListMonitor() {}

    /** Registers the tick listener. Idempotent — safe to call multiple times. */
    public static void register() {
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(TabListMonitor::onClientTick);
    }

    /** Returns an unmodifiable view of the last captured raw tab lines. */
    public static List<String> getRawLines() {
        return Collections.unmodifiableList(rawLines);
    }

    /** {@code true} if the tab list content changed during the most recent poll. */
    public static boolean didRefreshThisTick() {
        return refreshedThisTick;
    }

    // ── Tick handler ──────────────────────────────────────────────────────────

    private static void onClientTick(Minecraft client) {
        refreshedThisTick = false;

        if (!HypixelLocationState.isOnSkyblock()) {
            if (!rawLines.isEmpty()) {
                rawLines.clear();
                lastContentHash = 0;
                SkyblockStats.markStale();
            }
            return;
        }

        if (client.player == null || client.getConnection() == null) {
            return;
        }

        if (++tickCounter < POLL_INTERVAL_TICKS) return;
        tickCounter = 0;

        // Gather display names from every listed tab entry.
        Collection<PlayerInfo> entries = client.getConnection().getListedOnlinePlayers();
        if (entries.isEmpty()) return;

        List<String> current = new ArrayList<>(entries.size());
        int hash = entries.size();
        for (PlayerInfo info : entries) {
            if (info.getTabListDisplayName() == null) continue;
            String text = info.getTabListDisplayName().getString();
            if (text.isEmpty()) continue;
            current.add(text);
            hash ^= text.hashCode() + 31 * hash;
        }

        if (hash != lastContentHash) {
            lastContentHash = hash;
            rawLines.clear();
            rawLines.addAll(current);
            refreshedThisTick = true;

            TabStatParser.ParseResult result = TabStatParser.parse(current);
            SkyblockStats.updateFromParseResult(result);
        }
    }
}
