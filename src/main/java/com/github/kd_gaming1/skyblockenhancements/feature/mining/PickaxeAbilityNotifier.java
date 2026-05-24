package com.github.kd_gaming1.skyblockenhancements.feature.mining;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.util.HypixelLocationState;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.regex.Pattern;

/**
 * Displays a large on-screen title when a pickaxe ability cooldown finishes.
 *
 * <p>Listens for Hypixel's "Mining Speed Boost is now available!" and
 * "Pickobulus is now available!" chat messages and shows a centered title
 * (and optional sound) so the player doesn't miss the cooldown reset.
 */
public final class PickaxeAbilityNotifier {

    private static final Pattern LEGACY_FORMAT_CODE_PATTERN = Pattern.compile("(?i)\u00A7[0-9A-FK-OR]");

    private static final Pattern COOLDOWN_READY_PATTERN =
            Pattern.compile("^[a-zA-Z0-9 ]+ is now available!$");

    /** Cooldown in milliseconds to prevent duplicate titles. */
    private static final long COOLDOWN_MS = 5000;

    private static long lastTriggerTime = 0;

    private PickaxeAbilityNotifier() {}

    public static void init() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) {
                onChatMessage(message);
            }
        });
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) ->
                onChatMessage(message)
        );
    }

    private static void onChatMessage(Component message) {
        if (!SkyblockEnhancementsConfig.notifyPickaxeAbilityReady) {
            return;
        }
        if (!HypixelLocationState.isOnSkyblock()) {
            return;
        }

        String raw = LEGACY_FORMAT_CODE_PATTERN.matcher(message.getString()).replaceAll("");
        if (!COOLDOWN_READY_PATTERN.matcher(raw).matches()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastTriggerTime < COOLDOWN_MS) {
            return;
        }
        lastTriggerTime = now;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }

        client.gui.setSubtitle(
                Component.literal("Your Pickaxe Ability is Ready")
                        .withStyle(ChatFormatting.GREEN)
        );

        if (SkyblockEnhancementsConfig.pickaxeAbilityReadySound && client.level != null) {
            client.level.playSound(
                    client.player,
                    client.player.blockPosition(),
                    SoundEvents.NOTE_BLOCK_PLING.value(),
                    SoundSource.PLAYERS,
                    1.0f,
                    1.5f
            );
        }
    }
}
