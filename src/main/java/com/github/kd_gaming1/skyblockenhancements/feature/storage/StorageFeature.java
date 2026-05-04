package com.github.kd_gaming1.skyblockenhancements.feature.storage;

import com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements;
import java.util.concurrent.atomic.AtomicReference;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Lifecycle manager for the Storage Dashboard feature.
 *
 * <p>Initialises the overlay manager, wires save-on-disconnect, and provides
 * a singleton accessor so the mixin can reach the manager without tight coupling.
 */
public final class StorageFeature {

    private static final AtomicReference<StorageOverlayManager> MANAGER_REF = new AtomicReference<>();
    private static volatile String cachedProfileId = "unknown";
    private static volatile boolean initialised = false;

    private StorageFeature() {}

    /** Creates the storage layer and hooks lifecycle events. */
    public static void init() {
        if (initialised) return;
        initialised = true;

        Path storageDir = FabricLoader.getInstance()
                .getConfigDir()
                .resolve(SkyblockEnhancements.MOD_ID)
                .resolve("storage");

        StorageSnapshotStorage storage = new StorageSnapshotStorage(storageDir);
        StorageOverlayManager manager = new StorageOverlayManager(storage);
        MANAGER_REF.set(manager);

        // Load persisted data when joining a server / world.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            cachedProfileId = StorageOverlayManager.resolveProfileId();
            manager.loadProfile(cachedProfileId);
            if (client.level != null) {
                manager.resolveAllStacks(client.level.registryAccess());
            }
        });

        // Persist on disconnect and shutdown.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> save());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> save());
    }

    /** Returns the overlay manager, or {@code null} if the feature is not initialised. */
    public static StorageOverlayManager getManager() {
        return MANAGER_REF.get();
    }

    /** Persists the current cache to disk using the current player's profile ID. */
    public static void save() {
        StorageOverlayManager manager = MANAGER_REF.get();
        if (manager == null) return;
        manager.saveToStorage(cachedProfileId);
    }

    /** Clears both the in-memory cache and persisted data for the current profile. */
    public static void clearCache() {
        StorageOverlayManager manager = MANAGER_REF.get();
        if (manager == null) return;
        manager.clearCache();
        save();
    }
}
