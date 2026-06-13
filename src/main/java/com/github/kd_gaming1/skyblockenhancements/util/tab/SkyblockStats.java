package com.github.kd_gaming1.skyblockenhancements.util.tab;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Public facade for all SkyBlock stats extracted from the tab list.
 * Updated automatically by {@link TabListMonitor}.
 */
public final class SkyblockStats {

    private SkyblockStats() {}

    // ── Mutable state ───────────────────────────────────────────────────────

    private static final Map<String, String> stats = new HashMap<>(32);
    private static boolean ready = false;
    private static long lastUpdateTick = 0;
    private static int parsedStatCount = 0;

    // ── Events ──────────────────────────────────────────────────────────────

    public static final Event<StatChangeCallback> STAT_CHANGED = EventFactory.createArrayBacked(
            StatChangeCallback.class,
            callbacks -> (key, oldValue, newValue) -> {
                for (StatChangeCallback cb : callbacks) cb.onStatChanged(key, oldValue, newValue);
            }
    );

    @FunctionalInterface
    public interface StatChangeCallback {
        void onStatChanged(String key, String oldValue, String newValue);
    }

    public static final Event<TabRefreshedCallback> TAB_REFRESHED = EventFactory.createArrayBacked(
            TabRefreshedCallback.class,
            callbacks -> allStats -> {
                for (TabRefreshedCallback cb : callbacks) cb.onTabRefreshed(allStats);
            }
    );

    @FunctionalInterface
    public interface TabRefreshedCallback {
        void onTabRefreshed(Map<String, String> allStats);
    }

    // ── Mining Speed ────────────────────────────────────────────────────────

    public static OptionalInt getMiningSpeed() {
        return getInt("mining_speed");
    }

    public static int getMiningSpeedOrZero() {
        return getMiningSpeed().orElse(0);
    }

    public static boolean hasMiningSpeed() {
        return hasStat("mining_speed");
    }

    // ── Generic typed access ────────────────────────────────────────────────

    public static OptionalInt getInt(String key) {
        String raw = stats.get(key);
        return raw != null ? TabStatParser.parseInt(raw) : OptionalInt.empty();
    }

    public static OptionalLong getLong(String key) {
        String raw = stats.get(key);
        return raw != null ? TabStatParser.parseLong(raw) : OptionalLong.empty();
    }

    public static OptionalDouble getDouble(String key) {
        String raw = stats.get(key);
        return raw != null ? TabStatParser.parseDouble(raw) : OptionalDouble.empty();
    }

    public static Optional<String> getString(String key) {
        return Optional.ofNullable(stats.get(key));
    }

    public static boolean hasStat(String key) {
        return stats.containsKey(key);
    }

    public static Map<String, String> getAllStats() {
        return Collections.unmodifiableMap(new HashMap<>(stats));
    }

    public static Set<String> getAvailableStatKeys() {
        return Collections.unmodifiableSet(new HashSet<>(stats.keySet()));
    }

    // ── Meta ────────────────────────────────────────────────────────────────

    public static boolean isReady() {
        return ready;
    }

    public static long getLastUpdateTick() {
        return lastUpdateTick;
    }

    public static int getParsedStatCount() {
        return parsedStatCount;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Demand-based missing-stat warnings
    // ═════════════════════════════════════════════════════════════════════════

    private static final int WARN_COOLDOWN_TICKS = 5 * 20;

    private static final class Demand {
        final String statKey;
        final String displayName;
        @Nullable final String enableHint;
        @Nullable final String reason;
        int ticksSinceLastWarn = WARN_COOLDOWN_TICKS;

        Demand(String statKey, String displayName, @Nullable String enableHint, @Nullable String reason) {
            this.statKey = statKey;
            this.displayName = displayName;
            this.enableHint = enableHint;
            this.reason = reason;
        }
    }

    private static final Map<String, Demand> demands = new HashMap<>();
    private static final Set<String> ignoredDemands = new HashSet<>();

    /**
     * Registers that a feature requires a stat from the tab list.
     * If the stat is not available, a warning is sent every 5 seconds.
     * The demand is automatically cleared when the stat appears.
     */
    public static void demandStat(String statKey, String displayName) {
        demandStat(statKey, displayName, null, null);
    }

    public static void demandStat(String statKey, String displayName, @Nullable String enableHint) {
        demandStat(statKey, displayName, enableHint, null);
    }

    public static void demandStat(String statKey, String displayName, @Nullable String enableHint, @Nullable String reason) {
        if (hasStat(statKey)) return;
        if (ignoredDemands.contains(statKey)) return;
        demands.computeIfAbsent(statKey, k -> new Demand(statKey, displayName, enableHint, reason));
    }

    public static void ignoreDemand(String statKey) {
        ignoredDemands.add(statKey);
        demands.remove(statKey);
    }

    /** Checks all active demands and sends warnings for any still unmet. */
    public static void checkDemands() {
        if (demands.isEmpty()) return;
        demands.entrySet().removeIf(e -> hasStat(e.getKey()));
        if (demands.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        for (Demand d : demands.values()) {
            d.ticksSinceLastWarn++;
            if (d.ticksSinceLastWarn >= WARN_COOLDOWN_TICKS) {
                d.ticksSinceLastWarn = 0;
                sendMissingStatWarning(mc, d);
            }
        }
    }

    public static void clearDemands() {
        demands.clear();
    }

    private static void sendMissingStatWarning(Minecraft mc, Demand d) {
        assert mc.player != null;

        MutableComponent message = Component.literal(
                "§c[SkyblockEnhancements] " + d.displayName + " not found in tab list!");

        if (d.reason != null || d.enableHint != null) {
            StringBuilder detail = new StringBuilder("\n§7");
            if (d.reason != null) {
                detail.append(d.reason);
            }
            if (d.enableHint != null) {
                if (d.reason != null) {
                    detail.append(" ");
                }
                detail.append(d.enableHint);
            }
            message.append(Component.literal(detail.toString()));
        }

        MutableComponent ignore = Component.literal("§e[Ignore]")
                .withStyle(style -> style.withClickEvent(
                        new ClickEvent.RunCommand("/skyblockenhancements ignore_tab_stat " + d.statKey)));

        message.append(Component.literal("\n")).append(ignore);
        mc.player.sendSystemMessage(message);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Internal: called by TabListMonitor
    // ═════════════════════════════════════════════════════════════════════════

    static void updateFromParseResult(TabStatParser.ParseResult result) {
        Map<String, String> incoming = result.getAllStats();

        if (!ready) {
            stats.putAll(incoming);
            ready = true;
            parsedStatCount = incoming.size();
            lastUpdateTick = net.minecraft.client.Minecraft.getInstance().level != null
                    ? net.minecraft.client.Minecraft.getInstance().level.getGameTime()
                    : 0;
            TAB_REFRESHED.invoker().onTabRefreshed(getAllStats());
            return;
        }

        for (Map.Entry<String, String> e : incoming.entrySet()) {
            String key = e.getKey();
            String newVal = e.getValue();
            String oldVal = stats.put(key, newVal);
            if (oldVal == null || !oldVal.equals(newVal)) {
                STAT_CHANGED.invoker().onStatChanged(key, oldVal, newVal);
            }
        }

        Iterator<Map.Entry<String, String>> it = stats.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, String> e = it.next();
            if (!incoming.containsKey(e.getKey())) {
                STAT_CHANGED.invoker().onStatChanged(e.getKey(), e.getValue(), null);
                it.remove();
            }
        }

        parsedStatCount = stats.size();
        lastUpdateTick = net.minecraft.client.Minecraft.getInstance().level != null
                ? net.minecraft.client.Minecraft.getInstance().level.getGameTime()
                : 0;
        TAB_REFRESHED.invoker().onTabRefreshed(getAllStats());
    }

    static void markStale() {
        ready = false;
        stats.clear();
        parsedStatCount = 0;
        clearDemands();
    }
}
