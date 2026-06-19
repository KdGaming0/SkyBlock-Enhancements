/*
 * Part of the ping-offset mining feature inspired by PingOffsetMiner:
 * https://github.com/Revvilon/PingOffsetMiner
 */

package com.github.kd_gaming1.skyblockenhancements.feature.mining;

import com.github.kd_gaming1.skyblockenhancements.util.HypixelLocationState;
import com.github.kd_gaming1.skyblockenhancements.util.StringUtil;
import com.github.kd_gaming1.skyblockenhancements.util.tool.ToolAbilityExtractor;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.regex.Pattern;

/**
 * Tracks when the held tool's Mining Speed Boost ability is active.
 * Detects activation via chat message and counts down using a tick timer.
 * The ability duration is read from the tool's lore when equipped.
 */
public final class MiningAbilityTracker {

    private MiningAbilityTracker() {}

    private static final Pattern ACTIVATION_PATTERN = Pattern.compile(
            "(?i)^you used your mining speed boost pickaxe ability!$"
    );

    private static final int DEFAULT_BOOST_TICKS = 15 * 20; // 15 seconds fallback
    private static final int BOOST_SPEED_BONUS = 300; // +300 mining speed from tab list

    private static boolean boostActive = false;
    private static int remainingTicks = 0;
    private static int cachedDurationTicks = DEFAULT_BOOST_TICKS;
    private static boolean registered = false;

    /** Registers the chat listener. Idempotent. */
    public static void register() {
        if (registered) return;
        registered = true;

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) onChatMessage(message);
        });
    }

    /** Call once per tick to count down the boost timer. */
    public static void tick() {
        if (!boostActive) return;
        if (--remainingTicks <= 0) {
            boostActive = false;
            remainingTicks = 0;
        }
    }

    /** Returns true if the Mining Speed Boost ability is currently active. */
    public static boolean isBoostActive() {
        return boostActive;
    }

    /** Returns the flat mining speed bonus granted by the boost. */
    public static int getBoostSpeedBonus() {
        return boostActive ? BOOST_SPEED_BONUS : 0;
    }

    /**
     * Re-scans the held tool to cache the ability duration.
     * Call this when the held item changes.
     */
    public static void refreshTool() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ItemStack held = mc.player.getMainHandItem();
        if (held.isEmpty()) return;

        var info = ToolAbilityExtractor.extract(held);
        if (info == null) return;

        if (info.isMiningSpeedBoost() && info.durationSeconds() > 0) {
            cachedDurationTicks = info.durationSeconds() * 20;
        } else {
            cachedDurationTicks = DEFAULT_BOOST_TICKS;
        }
    }

    private static void onChatMessage(Component message) {
        if (!HypixelLocationState.isOnSkyblock()) return;

        String raw = StringUtil.stripColorCodes(message.getString());
        if (ACTIVATION_PATTERN.matcher(raw.trim()).matches()) {
            boostActive = true;
            remainingTicks = cachedDurationTicks;
        }
    }
}
