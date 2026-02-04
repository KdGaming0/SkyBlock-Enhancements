package com.github.kd_gaming1.skyblockenhancements;

import com.github.kd_gaming1.skyblockenhancements.util.NeuRepoCache;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SkyblockEnhancements implements ClientModInitializer {
    public static final String MOD_ID = "skyblock-enhancements";
    public static final String VERSION = /*$ mod_version*/ "0.1.0";
    public static final String MINECRAFT = /*$ minecraft*/ "1.21.5";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private final NeuRepoCache cache = new NeuRepoCache();

    @Override public void onInitializeClient() {
        LOGGER.info("Skyblock Enhancements is loaded up!");

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
                cache.downloadAndSave("constants/enchants.json");
        });
    }
}
