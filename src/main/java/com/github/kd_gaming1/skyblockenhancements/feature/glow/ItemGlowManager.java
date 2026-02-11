package com.github.kd_gaming1.skyblockenhancements.feature.glow;

import com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements;
import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages glowing outlines for dropped items based on rarity.
 *
 * <p>This system performs incremental scanning of nearby items to avoid
 * performance spikes. Items are added to scoreboard teams to apply colored
 * glow effects based on their Hypixel Skyblock rarity tier.</p>
 */
public final class ItemGlowManager {
    private ItemGlowManager() {}

    private static final List<String> RARITIES = Arrays.asList(
            "COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE", "SPECIAL", "VERY SPECIAL", "ULTIMATE", "ADMIN"
    );

    private static final List<String> RARITIES_SORTED;
    static {
        RARITIES_SORTED = new ArrayList<>(RARITIES);
        // check longer names first to avoid substring false positives (e.g., UNCOMMON vs COMMON)
        RARITIES_SORTED.sort((a, b) -> Integer.compare(b.length(), a.length()));
    }

    // Items we want to force-glow right now
    private static final Set<UUID> SHOULD_GLOW = ConcurrentHashMap.newKeySet();

    // Items we have already added to the scoreboard team
    private static final Set<UUID> IN_TEAM = ConcurrentHashMap.newKeySet();

    // How often to refresh visibility (in ticks).
    private static final int REFRESH_INTERVAL_TICKS = 3;

    // Limit LOS checks to prevent lag spikes in item-dense areas
    private static final int MAX_LOS_CHECKS_PER_REFRESH = 200;

    // Only consider items within this distance
    private static double getMaxRange() { return SkyblockEnhancementsConfig.setItemGlowOutlineDistance; }

    // Only check LOS if the item is roughly in front of the camera.
    private static final double LOOK_DOT_THRESHOLD = 0.15;
    private static double maxRangeSqr() { double r = getMaxRange(); return r * r; }
    private static final String TEAM_PREFIX = "sbe_glow_";

    private static int tickCounter = 0;
    @SuppressWarnings("unchecked")
    private static final Iterator<ItemEntity>[] scanIterator = new Iterator[]{null};

    private static final Object2BooleanOpenHashMap<UUID> SHOWCASE_CACHE = new Object2BooleanOpenHashMap<>();
    private static final Object2IntOpenHashMap<UUID> SHOWCASE_CACHE_EXPIRES = new Object2IntOpenHashMap<>();

    private static final int SHOWCASE_CACHE_TTL_TICKS = 40;
    private static final double SHOWCASE_CHECK_RADIUS = 1.5;

    public static boolean shouldForceGlow(Entity entity) {
        return SHOULD_GLOW.contains(entity.getUUID());
    }

    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        boolean showThroughWalls = SkyblockEnhancementsConfig.showThoughWalls;

        if (mc.level == null || mc.player == null) {
            clearAllAndRemoveTeamEntries(mc);
            return;
        }

        tickCounter++;
        if (tickCounter % REFRESH_INTERVAL_TICKS != 0) {
            pruneDeadEntries(mc);
            return;
        }

        if (scanIterator[0] == null || !scanIterator[0].hasNext()) {
            scanIterator[0] = mc.level.getEntitiesOfClass(
                    ItemEntity.class,
                    mc.player.getBoundingBox().inflate(getMaxRange())
            ).iterator();
        }

        int checks = 0;
        while (scanIterator[0].hasNext() && checks < MAX_LOS_CHECKS_PER_REFRESH) {
            ItemEntity item = scanIterator[0].next();

            if (!item.isAlive()) {
                setGlow(item.getUUID(), false, mc);
                continue;
            }

            boolean visible = false;
            if (passesLookCone(mc.player, item)) {
                if (isOnHypixel() && isShopShowcaseItemCached(mc, item)) {
                    setGlow(item.getUUID(), false, mc);
                    continue;
                }

                if (showThroughWalls) {
                    visible = true;
                } else {
                    visible = mc.player.hasLineOfSight(item);
                }
            }

            setGlow(item.getUUID(), visible, mc);
            checks++;
        }

        if (!scanIterator[0].hasNext()) {
            pruneDeadEntries(mc);
        }
    }

    private static void setGlow(UUID id, boolean shouldGlowNow, Minecraft mc) {
        boolean wasGlow = SHOULD_GLOW.contains(id);

        if (shouldGlowNow) {
            if (!wasGlow) SHOULD_GLOW.add(id);
            if (!IN_TEAM.contains(id)) {
                addToTeam(id, mc);
            }
        } else {
            if (wasGlow) SHOULD_GLOW.remove(id);
            if (IN_TEAM.contains(id)) {
                removeFromTeam(id, mc);
            }
        }
    }

    private static void addToTeam(UUID id, Minecraft mc) {
        if (mc.level == null) return;

        Entity entity = mc.level.getEntity(id);
        if (!(entity instanceof ItemEntity item)) return;

        String rarity = extractRarityFromItemEntity(item);
        if (rarity == null) rarity = "COMMON";

        Scoreboard scoreboard = mc.level.getScoreboard();
        PlayerTeam team = GlowTeams.ensureTeam(mc.level, rarity);
        String key = id.toString();

        // If already in one of YOUR glow teams, but the wrong one, remove first
        PlayerTeam current = scoreboard.getPlayersTeam(key);
        if (current != null
                && current.getName().startsWith(TEAM_PREFIX)
                && !current.getName().equals(team.getName())) {
            scoreboard.removePlayerFromTeam(key);
        }

        scoreboard.addPlayerToTeam(key, team);
        IN_TEAM.add(id);
    }

    private static void removeFromTeam(UUID id, Minecraft mc) {
        if (mc.level == null) return;

        Scoreboard scoreboard = mc.level.getScoreboard();
        String key = id.toString();

        PlayerTeam current = scoreboard.getPlayersTeam(key);
        if (current != null && current.getName().startsWith(TEAM_PREFIX)) {
            scoreboard.removePlayerFromTeam(key);
        }

        IN_TEAM.remove(id);
    }

    private static void pruneDeadEntries(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;

        // Remove entries that no longer exist or are far away
        SHOULD_GLOW.removeIf(uuid -> {
            Entity e = mc.level.getEntity(uuid);
            if (e == null || !e.isAlive()) return true;
            if (!(e instanceof ItemEntity)) return true;
            return e.distanceToSqr(mc.player) > (getMaxRange() * getMaxRange());
        });

        IN_TEAM.removeIf(uuid -> {
            Entity e = mc.level.getEntity(uuid);
            if (e == null || !e.isAlive()) return true;
            if (!(e instanceof ItemEntity)) return true;
            return e.distanceToSqr(mc.player) > (getMaxRange() * getMaxRange());
        });

        SHOWCASE_CACHE.keySet().removeIf(uuid -> !IN_TEAM.contains(uuid) && !SHOULD_GLOW.contains(uuid));
        SHOWCASE_CACHE_EXPIRES.keySet().removeIf(uuid -> !SHOWCASE_CACHE.containsKey(uuid));
    }

    private static boolean passesLookCone(Player player, Entity entity) {
        Vec3 eye = player.getEyePosition();
        Vec3 toEntity = entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0).subtract(eye);

        double distSqr = toEntity.lengthSqr();
        if (distSqr > maxRangeSqr()) return false;

        Vec3 dirToEntity = toEntity.normalize();
        Vec3 look = player.getLookAngle();

        return look.dot(dirToEntity) > LOOK_DOT_THRESHOLD;
    }

    public static String extractRarityFromItemEntity(ItemEntity itemEntity) {
        ItemStack itemStack = itemEntity.getItem();

        ItemLore itemLore = itemStack.get(net.minecraft.core.component.DataComponents.LORE);

        if (itemLore == null) {
            return null;
        }

        return extractRarity(itemLore.lines());
    }

    private static String extractRarity(List<Component> loreLines) {
        // Iterate backwards through the lore lines, as Rarity is typically found at the bottom.
        for (int i = loreLines.size() - 1; i >= 0; i--) {
            String lineContent = loreLines.get(i).getString().toUpperCase(java.util.Locale.ROOT);

            for (String rarity : RARITIES_SORTED) {
                if (lineContent.contains(rarity)) {
                    return rarity;
                }
            }
        }
        return null;
    }


    private static boolean isShopShowcaseItemCached(Minecraft mc, ItemEntity item) {
        UUID id = item.getUUID();

        int expires = SHOWCASE_CACHE_EXPIRES.getOrDefault(id, -1);
        if (expires >= tickCounter && SHOWCASE_CACHE.containsKey(id)) {
            return SHOWCASE_CACHE.getBoolean(id);
        }

        boolean blocked = isShopShowcaseItem(mc, item);

        SHOWCASE_CACHE.put(id, blocked);
        SHOWCASE_CACHE_EXPIRES.put(id, tickCounter + SHOWCASE_CACHE_TTL_TICKS);
        return blocked;
    }

    private static boolean isShopShowcaseItem(Minecraft mc, ItemEntity item) {
        if (mc.level == null) return false;

        AABB box = item.getBoundingBox().inflate(SHOWCASE_CHECK_RADIUS);

        // Search only nearby armor stands
        List<ArmorStand> stands = mc.level.getEntitiesOfClass(ArmorStand.class, box);
        if (stands.isEmpty()) return false;

        for (ArmorStand as : stands) {
            ItemStack head = as.getItemBySlot(EquipmentSlot.HEAD);
            if (head.isEmpty()) continue;

            if (isGlass(head)) return true;
        }

        return false;
    }

    private static boolean isGlass(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) return false;
        var block = blockItem.getBlock();
        return (block instanceof TransparentBlock);
    }

    public static void clearAllAndRemoveTeamEntries(Minecraft mc) {
        if (mc != null && mc.level != null) {
            Scoreboard scoreboard = mc.level.getScoreboard();

            for (UUID id : IN_TEAM) {
                String key = id.toString();
                PlayerTeam current = scoreboard.getPlayersTeam(key);
                if (current != null && current.getName().startsWith(TEAM_PREFIX)) {
                    scoreboard.removePlayerFromTeam(key);
                }
            }
        }

        SHOWCASE_CACHE.clear();
        SHOWCASE_CACHE_EXPIRES.clear();
        SHOULD_GLOW.clear();
        IN_TEAM.clear();
        scanIterator[0] = null;
        tickCounter = 0;
    }

    private static boolean isOnHypixel() {
        return SkyblockEnhancements.helloPacketReceived.get();
    }
}
