package com.github.kd_gaming1.skyblockenhancements.repo.network;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

import com.google.gson.Gson;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.jetbrains.annotations.Nullable;

/**
 * Thin wrapper around {@link HttpClient} for JSON endpoints.
 *
 * <p>Centralises timeout configuration, user-agent headers, and Gson deserialisation
 * so that {@link com.github.kd_gaming1.skyblockenhancements.repo.NeuRepoDownloader}
 * and {@link com.github.kd_gaming1.skyblockenhancements.repo.hypixel.HypixelItemsDownloader}
 * share the same HTTP behaviour.
 */
public final class JsonHttpClient {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final String USER_AGENT = "SkyblockEnhancements";

    private final HttpClient http;
    private final Gson gson;

    public JsonHttpClient(HttpClient http, Gson gson) {
        this.http = http;
        this.gson = gson;
    }

    /**
     * Performs a GET request and deserialises the response body as JSON.
     *
     * @param url  the endpoint
     * @param type the class to deserialise into
     * @return the parsed object, or {@code null} on non-2xx status or parse failure
     */
    @Nullable
    public <T> T getJson(String url, Class<T> type) {
        try {
            HttpResponse<String> response = getStringResponse(url);
            if (response == null) return null;
            return gson.fromJson(response.body(), type);
        } catch (Exception e) {
            LOGGER.warn("Failed to parse JSON from {}: {}", url, e.getMessage());
            return null;
        }
    }

    /**
     * Performs a GET request and returns the raw response body as a string.
     *
     * @param url the endpoint
     * @return the response body, or {@code null} on non-2xx status
     */
    @Nullable
    public String getString(String url) {
        HttpResponse<String> response = getStringResponse(url);
        return response != null ? response.body() : null;
    }

    /**
     * Performs a HEAD request.
     *
     * @param url the endpoint
     * @return the response, or {@code null} on failure
     */
    @Nullable
    public HttpResponse<Void> head(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofSeconds(5))
                    .build();

            return http.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            LOGGER.warn("HEAD request to {} failed: {}", url, e.getMessage());
            return null;
        }
    }

    @Nullable
    private HttpResponse<String> getStringResponse(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .timeout(DEFAULT_TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOGGER.warn("HTTP {} from {}", response.statusCode(), url);
                return null;
            }
            return response;
        } catch (Exception e) {
            LOGGER.warn("GET request to {} failed: {}", url, e.getMessage());
            return null;
        }
    }
}
