package com.github.kd_gaming1.skyblockenhancements.feature.storage;

import com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements;
import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Lifecycle manager for the Storage Overlay feature.
 */
public final class StorageFeature {

    private static final AtomicReference<StorageSnapshotStorage> STORAGE_REF = new AtomicReference<>();
    private static volatile String cachedProfileId = "unknown";
    private static volatile boolean initialised = false;

    private StorageFeature() {}

    public static void init() {
        if (initialised) return;
        initialised = true;

        Path storageDir = FabricLoader.getInstance()
                .getConfigDir()
                .resolve(SkyblockEnhancements.MOD_ID)
                .resolve("storage");

        StorageSnapshotStorage storage = new StorageSnapshotStorage(storageDir);
        STORAGE_REF.set(storage);

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (!SkyblockEnhancementsConfig.enableStorageDashboard_Test) return;
            cachedProfileId = resolveProfileId();
            storage.load(cachedProfileId, StorageData.INSTANCE);
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> save());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> save());
    }

    public static void save() {
        if (!SkyblockEnhancementsConfig.enableStorageDashboard_Test) return;
        StorageSnapshotStorage storage = STORAGE_REF.get();
        if (storage == null) return;
        storage.save(cachedProfileId, StorageData.INSTANCE);
    }

    public static void clearCache() {
        StorageData.INSTANCE.clear();
        save();
    }

    public static String resolveProfileId() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return "unknown";
        return mc.player.getUUID().toString();
    }
}
