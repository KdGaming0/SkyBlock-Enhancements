package com.github.kd_gaming1.skyblockenhancements.util;

import com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static java.time.temporal.ChronoUnit.SECONDS;

// Downloads files from the NEU repo into the mod data folder
public class NeuRepoCache {
    private final URI rawBase;
    private final Path storageRoot;
    private final HttpClient client;

    public NeuRepoCache() {
        this.rawBase = URI.create("https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/");
        this.storageRoot = FabricLoader.getInstance().getConfigDir().resolve("Skyblock Enhancements").resolve("data");
        this.client = HttpClient.newHttpClient();
    }

    public URI resolve(String relativePath) {
        return rawBase.resolve(relativePath);
    }

    public Path getCachedPath(String relativePath) {
        // Keep the same folder structure as the repo
        return storageRoot.resolve(relativePath.replace("/", java.io.File.separator));
    }

    private Path fetchAndWrite(String relativePath, String action) {
        Path target = getCachedPath(relativePath);
        try {
            String body = fetchText(relativePath);

            Path parent = target.getParent();
            if (parent != null) Files.createDirectories(parent);

            Files.writeString(target, body, StandardCharsets.UTF_8);
            SkyblockEnhancements.LOGGER.info("{} {} -> {}", action, resolve(relativePath), target);
            return target;

        } catch (Exception e) {
            SkyblockEnhancements.LOGGER.error(
                    "{} failed for {}. Reason: {}",
                    action, resolve(relativePath), e.toString()
            );
            throw new RuntimeException(action + " failed: " + relativePath, e);
        }
    }

    public void downloadAndSave(String relativePath) {
        fetchAndWrite(relativePath, "Downloaded");
    }

    public Path refresh(String relativePath) {
        return fetchAndWrite(relativePath, "Refreshed");
    }

    private String fetchText(String relativePath) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(resolve(relativePath))
                .header("User-Agent", "SkyblockEnhancements/1.0")
                .header("Accept", "application/json")
                .timeout(Duration.of(10, SECONDS))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int status = response.statusCode();

        if (status < 200 || status >= 300) {
            throw new IOException("HTTP " + status + " for " + resolve(relativePath));
        }

        return response.body();
    }
}