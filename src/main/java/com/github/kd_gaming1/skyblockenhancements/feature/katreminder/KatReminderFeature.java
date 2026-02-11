package com.github.kd_gaming1.skyblockenhancements.feature.katreminder;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/**
 * Minimal bootstrap for the Kat reminder feature to keep the main mod init clean.
 */
public final class KatReminderFeature {
    private static final Object SAVE_LOCK = new Object();

    private static KatUpgradeReminderManager katUpgradeReminderManager;

    private KatReminderFeature() {}

    /**
     * Initializes Kat reminder listeners and loads persisted state.
     *
     * @param modId mod id used to resolve the config directory
     */
    public static void init(String modId) {
        if (katUpgradeReminderManager != null) return;

        Path storagePath = FabricLoader.getInstance().getConfigDir().resolve(modId).resolve("kat_reminders.json");
        katUpgradeReminderManager = new KatUpgradeReminderManager(storagePath);
        NpcDialogWatcher npcDialogWatcher = new NpcDialogWatcher(katUpgradeReminderManager::onNpcDialog, "Kat");

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

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> save());
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
}
