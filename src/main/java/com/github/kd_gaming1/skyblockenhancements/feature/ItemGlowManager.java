package com.github.kd_gaming1.skyblockenhancements.feature;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.util.HypixelLocationState;
import com.github.kd_gaming1.skyblockenhancements.util.ItemRarity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
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

    /** Skyblock rarity → outline RGB color. Rarity extraction itself lives in {@link ItemRarity}. */
    private static final Map<ItemRarity, Integer> RARITY_COLORS = Map.ofEntries(
            Map.entry(ItemRarity.COMMON,       0xFFFFFF),
            Map.entry(ItemRarity.UNCOMMON,     0x55FF55),
            Map.entry(ItemRarity.RARE,         0x5555FF),
            Map.entry(ItemRarity.EPIC,         0x800080),
            Map.entry(ItemRarity.LEGENDARY,    0xFFD700),
            Map.entry(ItemRarity.MYTHIC,       0xFF55FF),
            Map.entry(ItemRarity.DIVINE,       0x00FFFF),
            Map.entry(ItemRarity.SPECIAL,      0xFF5555),
            Map.entry(ItemRarity.VERY_SPECIAL, 0xFF5555),
            Map.entry(ItemRarity.ULTIMATE,     0xAA0000),
            Map.entry(ItemRarity.ADMIN,        0xAA0000)
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

    private static int tickCounter = 0;

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

        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, world) -> clear());
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
        Integer color = glowColors.get(uuid);
        return color != null ? color : parseColor(SkyblockEnhancementsConfig.defaultGlowColor);
    }

    /**
     * Returns the glow color if the item is currently glowing, or -1 if it is not.
     */
    public static int getGlowColorIfActive(UUID uuid) {
        if (!onSkyblock) return -1;
        if (uuid == null || !SkyblockEnhancementsConfig.enableItemGlowOutline) return -1;
        if (!SkyblockEnhancementsConfig.showThroughWalls && !lineOfSightVisible.contains(uuid)) return -1;
        Integer color = glowColors.get(uuid);
        return color != null ? color : -1;
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
        int color = ItemRarity.fromItem(item.getItem())
                .map(RARITY_COLORS::get)
                .orElseGet(() -> parseColor(SkyblockEnhancementsConfig.defaultGlowColor));
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

        if (tickCounter++ % 10 == 0) {
            updateLineOfSight(client.player);
        }
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