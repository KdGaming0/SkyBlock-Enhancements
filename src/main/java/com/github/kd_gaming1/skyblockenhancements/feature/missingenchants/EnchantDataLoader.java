package com.github.kd_gaming1.skyblockenhancements.feature.missingenchants;

import com.github.kd_gaming1.skyblockenhancements.repo.io.AtomicFileWriter;
import com.github.kd_gaming1.skyblockenhancements.repo.network.JsonHttpClient;
import com.github.kd_gaming1.skyblockenhancements.util.JsonLookup;
import com.google.gson.Gson;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.MOD_ID;

/** Loads disk data before refreshing it online. Never runs on the tooltip/client thread. */
final class EnchantDataLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnchantDataLoader.class);
    private static final String URL =
            "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/constants/enchants.json";

    private final Path path;
    private final Supplier<String> download;
    private final Consumer<JsonLookup> publish;

    EnchantDataLoader(Path path, Supplier<String> download, Consumer<JsonLookup> publish) {
        this.path = path;
        this.download = download;
        this.publish = publish;
    }

    static void register(Consumer<JsonLookup> publish) {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("data/constants/enchants.json");
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> CompletableFuture.runAsync(() -> {
            try (HttpClient http = HttpClient.newHttpClient()) {
                JsonHttpClient network = new JsonHttpClient(http, new Gson());
                new EnchantDataLoader(path, () -> network.getString(URL),
                        data -> client.execute(() -> publish.accept(data))).load();
            }
        }));
    }

    void load() {
        try {
            if (Files.exists(path)) publish.accept(JsonLookup.parse(Files.readString(path)));
        } catch (Exception e) {
            LOGGER.warn("Failed to load saved enchant data", e);
        }

        try {
            String json = download.get();
            if (json == null) return;
            JsonLookup data = JsonLookup.parse(json);
            // Validate first: a bad response must not replace the last usable disk file.
            try {
                AtomicFileWriter.writeString(path, json);
            } catch (Exception e) {
                LOGGER.warn("Failed to save enchant data; using the downloaded snapshot for this session", e);
            }
            publish.accept(data);
        } catch (Exception e) {
            LOGGER.warn("Failed to refresh enchant data; retaining the previous snapshot", e);
        }
    }
}
