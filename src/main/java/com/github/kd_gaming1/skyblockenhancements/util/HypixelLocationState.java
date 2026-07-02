package com.github.kd_gaming1.skyblockenhancements.util;

import net.azureaaron.hmapi.events.HypixelPacketEvents;
import net.azureaaron.hmapi.network.packet.v1.s2c.LocationUpdateS2CPacket;

import java.util.Set;

public final class HypixelLocationState {

    private static boolean onHypixel = false;
    private static boolean onSkyblock = false;
    private static boolean onMiningIsland = false;
    private static boolean inDungeon = false;

    private static final Set<String> MINING_ISLAND_MAPS = Set.of(
            "Dwarven Mines", "Crystal Hollows", "Mineshaft"
    );

    private static final String DUNGEON_MAP = "Dungeon";

    private HypixelLocationState() {}

    public static void register() {
        HypixelPacketEvents.HELLO.register(packet -> onHypixel = true);

        HypixelPacketEvents.LOCATION_UPDATE.register(packet -> {
            if (!(packet instanceof LocationUpdateS2CPacket location)) return;

            onSkyblock = location.serverType()
                    .map("SKYBLOCK"::equals)
                    .orElse(false);

            onMiningIsland = location.map()
                    .map(MINING_ISLAND_MAPS::contains)
                    .orElse(false);

            inDungeon = location.map()
                    .map(DUNGEON_MAP::equals)
                    .orElse(false);
        });
    }

    public static  boolean isOnHypixel() { return  onHypixel; }

    public static boolean isOnSkyblock() { return onSkyblock; }

    public static boolean isOnMiningIsland() {
        return onMiningIsland;
    }

    public static boolean isInDungeon() {
        return inDungeon;
    }

    public static void reset() {
        onHypixel = false;
        onSkyblock = false;
        onMiningIsland = false;
        inDungeon = false;
    }
}
