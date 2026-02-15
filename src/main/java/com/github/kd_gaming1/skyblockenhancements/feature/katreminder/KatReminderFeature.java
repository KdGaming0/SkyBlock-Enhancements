package com.github.kd_gaming1.skyblockenhancements.feature.katreminder;

import net.azureaaron.hmapi.events.HypixelPacketEvents;
import net.azureaaron.hmapi.network.packet.v1.s2c.LocationUpdateS2CPacket;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.util.List;

/**
 * Minimal bootstrap for the Kat reminder feature to keep the main mod init clean.
 */
public final class KatReminderFeature {
    private static final Object SAVE_LOCK = new Object();

    private static KatUpgradeReminderManager katUpgradeReminderManager;
    // Updated by Hypixel location packets and used as a lightweight Hub gate for Kat parsing.
    private static volatile boolean inSkyBlockHub;

    private KatReminderFeature() {}

    /**
     * Initializes Kat reminder listeners and loads persisted state.
     *
     * @param modId mod id used to resolve the config directory
     */
    public static void init(String modId) {
        if (katUpgradeReminderManager != null) return;

        Path storagePath = FabricLoader.getInstance().getConfigDir().resolve(modId).resolve("kat_reminders.json");
        katUpgradeReminderManager = new KatUpgradeReminderManager(storagePath, KatReminderFeature::isInSkyBlockHub);
        NpcDialogWatcher npcDialogWatcher = new NpcDialogWatcher(katUpgradeReminderManager::onNpcDialog, "Kat");

        // Track current location to avoid processing Kat dialog outside SkyBlock Hub.
        HypixelPacketEvents.LOCATION_UPDATE.register(packet -> {
            if (packet instanceof LocationUpdateS2CPacket locationPacket) {
                inSkyBlockHub = isHubLocation(locationPacket);
            }
        });

        katUpgradeReminderManager.load();

        ClientTickEvents.END_CLIENT_TICK.register(katUpgradeReminderManager::onClientTick);
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) {
                npcDialogWatcher.onGameMessage(message);
            }
        });
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) ->
                npcDialogWatcher.onGameMessage(message)
        );

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            inSkyBlockHub = false;
            save();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> save());
    }

    /**
     * Persists Kat reminder state if the feature is initialized.
     */
    public static void save() {
        if (katUpgradeReminderManager == null) return;
        synchronized (SAVE_LOCK) {
            katUpgradeReminderManager.save();
        }
    }

    public static int removeAllReminders() {
        if (katUpgradeReminderManager == null) return 0;
        synchronized (SAVE_LOCK) {
            // Used by "/remindme remove all" so Kat timers are cleared with normal reminders.
            return katUpgradeReminderManager.removeAllReminders();
        }
    }

    public static List<KatUpgradeReminderManager.KatReminderData> getActiveReminders() {
        if (katUpgradeReminderManager == null) return List.of();
        return katUpgradeReminderManager.getActiveReminders();
    }

    private static boolean isInSkyBlockHub() {
        return inSkyBlockHub;
    }

    private static boolean isHubLocation(LocationUpdateS2CPacket locationPacket) {
        String serverType = locationPacket.serverType().orElse("");
        String mode = locationPacket.mode().orElse("");
        String map = locationPacket.map().orElse("");

        if (!"SKYBLOCK".equalsIgnoreCase(serverType)) {
            return false;
        }

        return "hub".equalsIgnoreCase(mode) || "hub".equalsIgnoreCase(map);
    }
}
