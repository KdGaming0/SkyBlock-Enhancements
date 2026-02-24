package com.github.kd_gaming1.skyblockenhancements.feature;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.util.HypixelLocationState;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks dropped items and assigns glow outline colors based on Skyblock rarity.
 * Mixins query {@link #isGlowing} and {@link #getGlowColor} each frame.
 */
public class ItemGlowManager {

    private ItemGlowManager() {}

    private static final double SHOWCASE_DETECTION_RADIUS = 1.5;
    private static final double LOOK_DOT_THRESHOLD = 0.15;
    private static volatile boolean onSkyblock = false;

    /** Skyblock rarity name → outline RGB color. */
    private static final Map<String, Integer> RARITY_COLORS = Map.ofEntries(
            Map.entry("COMMON",       0xFFFFFF),
            Map.entry("UNCOMMON",     0x55FF55),
            Map.entry("RARE",         0x5555FF),
            Map.entry("EPIC",         0x800080),
            Map.entry("LEGENDARY",    0xFFD700),
            Map.entry("MYTHIC",       0xFF55FF),
            Map.entry("DIVINE",       0x00FFFF),
            Map.entry("SPECIAL",      0xFF5555),
            Map.entry("VERY SPECIAL", 0xFF5555),
            Map.entry("ULTIMATE",     0xAA0000),
            Map.entry("ADMIN",        0xAA0000)
    );

    /** Checked bottom-up; first match wins. Order matters for substrings (e.g. "VERY SPECIAL" before "SPECIAL"). */
    private static final List<String> RARITY_ORDER = List.of(
            "VERY SPECIAL", "ULTIMATE", "DIVINE", "MYTHIC", "LEGENDARY",
            "EPIC", "RARE", "UNCOMMON", "SPECIAL", "ADMIN", "COMMON"
    );

    /** UUID → glow color for every actively tracked item. */
    private static final Map<UUID, Integer> glowColors = new ConcurrentHashMap<>();

    /** UUIDs of items that passed the line-of-sight check this tick (unused when showThroughWalls is on). */
    private static final Set<UUID> lineOfSightVisible = ConcurrentHashMap.newKeySet();

    /** UUID → ItemEntity for all tracked items. */
    private static final Map<UUID, ItemEntity> trackedEntities = new ConcurrentHashMap<>();

    /**
     * Items waiting to be processed on the next tick.
     * Entities are enqueued on load and processed one tick later so that
     * the server has time to send component data (lore, custom name, etc.) before we try to read it.
     */
    private static final Queue<ItemEntity> spawnQueue = new ArrayDeque<>();

    /** Registers entity load/unload listeners and the per-tick update loop. */
    public static void init() {
        ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof ItemEntity item) {
                spawnQueue.add(item);
            }
        });

        ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof ItemEntity) {
                onItemRemoved(entity.getUUID());
            }
        });

        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) -> clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());

        ClientTickEvents.END_CLIENT_TICK.register(ItemGlowManager::tick);
    }

    // --- Public API (called by mixins) ---

    /** Returns true if this item should render with a glow outline this frame. */
    public static boolean isGlowing(UUID uuid) {
        if (!onSkyblock) return false;
        if (!SkyblockEnhancementsConfig.enableItemGlowOutline) return false;
        if (SkyblockEnhancementsConfig.showThroughWalls) return glowColors.containsKey(uuid);
        return lineOfSightVisible.contains(uuid);
    }

    /** Returns the RGB glow color for a tracked item, or white if not found. */
    public static int getGlowColor(UUID uuid) {
        return glowColors.getOrDefault(uuid, parseColor(SkyblockEnhancementsConfig.defaultGlowColor));
    }

    /**
     * Returns the glow color if the item is currently glowing, or -1 if it is not.
     */
    public static int getGlowColorIfActive(UUID uuid) {
        if (!onSkyblock) return -1;
        if (uuid == null || !SkyblockEnhancementsConfig.enableItemGlowOutline) return -1;
        if (SkyblockEnhancementsConfig.showThroughWalls) return glowColors.getOrDefault(uuid, -1);
        return lineOfSightVisible.contains(uuid) ? glowColors.getOrDefault(uuid, -1) : -1;
    }

    /** Clears all tracking state (called on world change or disconnect). */
    public static void clear() {
        glowColors.clear();
        lineOfSightVisible.clear();
        trackedEntities.clear();
        spawnQueue.clear();
    }

    // --- Tracking lifecycle ---

    /** Registers an item for glow tracking and resolves its rarity color from lore. */
    private static void onItemSpawned(ItemEntity item) {
        if (HypixelLocationState.isOnHypixel() && isShowcaseItem(item)) return;
        String rarity = extractRarity(item);
        int color = RARITY_COLORS.getOrDefault(rarity, parseColor(SkyblockEnhancementsConfig.defaultGlowColor));
        UUID uuid = item.getUUID();
        glowColors.put(uuid, color);
        trackedEntities.put(uuid, item);
    }

    /** Removes a despawned or unloaded item from all tracking structures. */
    private static void onItemRemoved(UUID uuid) {
        glowColors.remove(uuid);
        lineOfSightVisible.remove(uuid);
        trackedEntities.remove(uuid);
    }

    // --- Per-tick update ---

    /** Runs every tick: processes the spawn queue, then updates line-of-sight visibility. */
    private static void tick(Minecraft client) {
        if (!SkyblockEnhancementsConfig.enableItemGlowOutline) return;
        onSkyblock = HypixelLocationState.isOnHypixel() && HypixelLocationState.isOnSkyblock();
        if (client.player == null || client.level == null) return;

        // Drain the spawn queue — items were held for one tick, so component data has arrived.
        ItemEntity queued;
        while ((queued = spawnQueue.poll()) != null) {
            if (!queued.isRemoved()) onItemSpawned(queued);
        }

        if (SkyblockEnhancementsConfig.showThroughWalls) return;

        updateLineOfSight(client.player);
    }

    /** Rebuilds the set of items visible to the player this tick. */
    private static void updateLineOfSight(Player player) {
        lineOfSightVisible.clear();

        for (Map.Entry<UUID, ItemEntity> entry : trackedEntities.entrySet()) {
            ItemEntity item = entry.getValue();
            if (item == null || item.isRemoved()) continue;
            if (isInLookCone(player, item) && player.hasLineOfSight(item)) {
                lineOfSightVisible.add(entry.getKey());
            }
        }
    }

    /** Returns true if the item is roughly in front of the player (within the look cone). */
    private static boolean isInLookCone(Player player, ItemEntity item) {
        Vec3 eye = player.getEyePosition();
        Vec3 toItem = item.position().add(0, item.getBbHeight() * 0.5, 0).subtract(eye);
        return player.getLookAngle().dot(toItem.normalize()) > LOOK_DOT_THRESHOLD;
    }

    // --- Helpers ---

    /**
     * Scans the item's lore bottom-up and returns the first matching Skyblock rarity string.
     * Returns "UNKNOWN" if the item has no lore or no recognizable rarity line.
     */
    private static String extractRarity(ItemEntity item) {
        long start = System.nanoTime();
        ItemLore lore = item.getItem().get(DataComponents.LORE);

        if (lore == null) return "UNKNOWN";

        List<Component> lines = lore.lines();
        for (int i = lines.size() - 1; i >= 0; i--) {
            String line = lines.get(i).getString().toUpperCase(Locale.ROOT);
            for (String rarity : RARITY_ORDER) {
                if (line.contains(rarity)) {
                    return rarity;
                }
            }
        }

        return "UNKNOWN";
    }

    /**
     * Returns true if the item appears to be sitting on a showcase stand.
     */
    private static boolean isShowcaseItem(ItemEntity item) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;

        List<ArmorStand> nearbyStands = mc.level.getEntitiesOfClass(
                ArmorStand.class,
                item.getBoundingBox().inflate(SHOWCASE_DETECTION_RADIUS)
        );

        for (ArmorStand stand : nearbyStands) {
            ItemStack head = stand.getItemBySlot(EquipmentSlot.HEAD);
            if (!head.isEmpty()
                    && head.getItem() instanceof BlockItem blockItem
                    && blockItem.getBlock() instanceof TransparentBlock) {
                return true;
            }
        }
        return false;
    }

    /** Parses a hex color string into an RGB int. */
    private static int parseColor(String hex) {
        return Integer.parseInt(hex.replace("#", ""), 16);
    }
}