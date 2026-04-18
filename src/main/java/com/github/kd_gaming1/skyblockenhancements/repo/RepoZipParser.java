package com.github.kd_gaming1.skyblockenhancements.repo;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItem;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItemParser;

/**
 * Downloads and stream-parses the NEU repo ZIP archive. Dispatches individual entries
 * to {@link NeuItemParser} for item/SNBT parsing.
 *
 * <p>Never extracts to disk — all parsing happens in-memory during the ZIP stream read.
 */
public final class RepoZipParser {

    private static final String REPO_ZIP_URL =
            "https://codeload.github.com/NotEnoughUpdates/NotEnoughUpdates-REPO/zip/refs/heads/master";

    private static final Set<String> CONSTANTS_TO_EXTRACT = Set.of(
            "parents.json",
            "essencecosts.json",
            "museum.json",
            "pets.json",
            "petnums.json"
    );

    private RepoZipParser() {}

    /**
     * Result of a full ZIP download + parse. Contains all items, constants, and the
     * ETag for cache validation on subsequent launches.
     */
    public record ParseResult(
            Map<String, NeuItem> items,
            Map<String, JsonObject> constants,
            String etag) {}

    // ── Public entry point ───────────────────────────────────────────────────────

    /**
     * Downloads the NEU repo ZIP and stream-parses all entries in a single pass.
     * SNBT overrides are applied after the stream completes.
     * Category and rarity are resolved once at the end.
     *
     * @param http the shared HttpClient
     * @return parsed items, constants, and ETag
     * @throws Exception on HTTP or parse failure
     */
    public static ParseResult downloadAndParse(HttpClient http) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(REPO_ZIP_URL))
                .header("User-Agent", "SkyblockEnhancements")
                .header("Accept", "application/zip")
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();

        HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        int status = resp.statusCode();
        if (status < 200 || status >= 300) {
            throw new IOException("HTTP " + status + " downloading repo ZIP");
        }

        String etag = resp.headers().firstValue("ETag").orElse(null);

        Map<String, NeuItem> items = new HashMap<>(5000);
        Map<String, String> snbtIds = new HashMap<>();
        Map<String, Boolean> snbtGlints = new HashMap<>();
        Map<String, JsonObject> constants = new HashMap<>();

        parseZipStream(resp.body(), items, snbtIds, snbtGlints, constants);
        applySnbtOverrides(items, snbtIds, snbtGlints);
        NeuItemParser.resolveAllCategoryAndRarity(items);

        return new ParseResult(items, constants, etag);
    }

    // ── ZIP stream processing ───────────────────────────────────────────────────

    private static void parseZipStream(
            InputStream body,
            Map<String, NeuItem> items,
            Map<String, String> snbtIds,
            Map<String, Boolean> snbtGlints,
            Map<String, JsonObject> constants) throws IOException {

        try (ZipInputStream zis = new ZipInputStream(
                new BufferedInputStream(body, 1 << 16), StandardCharsets.UTF_8)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (name.contains("..")) continue;

                if (processItemEntry(name, zis, items)
                        || processSnbtEntry(name, zis, snbtIds, snbtGlints)
                        || processConstantsEntry(name, zis, constants)) {
                    zis.closeEntry();
                    continue;
                }

                zis.closeEntry();
            }
        }
    }

    // ── Entry dispatchers ───────────────────────────────────────────────────────

    /**
     * Processes a {@code /items/*.json} entry. Returns {@code true} if the entry matched.
     */
    private static boolean processItemEntry(String name, ZipInputStream zis,
                                            Map<String, NeuItem> items) throws IOException {
        int itemsIdx = name.indexOf("/items/");
        if (itemsIdx < 0 || !name.endsWith(".json")) return false;

        String fileName = name.substring(itemsIdx + 7);
        if (fileName.contains("/")) return false;

        String internalName = fileName.substring(0, fileName.length() - 5);
        String content = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
        try {
            NeuItem item = NeuItemParser.parseItemJson(internalName, content);
            items.put(internalName, item);
        } catch (JsonSyntaxException e) {
            LOGGER.debug("Skipping malformed item JSON: {}", internalName);
        }
        return true;
    }

    /**
     * Processes a {@code /itemsOverlay/*&#47;*.snbt} entry. Returns {@code true} if matched.
     */
    private static boolean processSnbtEntry(String name, ZipInputStream zis,
                                            Map<String, String> snbtIds,
                                            Map<String, Boolean> snbtGlints) throws IOException {
        int overlayIdx = name.indexOf("/itemsOverlay/");
        if (overlayIdx < 0 || !name.endsWith(".snbt")) return false;

        String afterOverlay = name.substring(overlayIdx + 14);
        int slash = afterOverlay.indexOf('/');
        if (slash < 0 || afterOverlay.indexOf('/', slash + 1) >= 0) return false;

        String fileName = afterOverlay.substring(slash + 1);
        String internalName = fileName.substring(0, fileName.length() - 5);
        String content = new String(zis.readAllBytes(), StandardCharsets.UTF_8);

        String snbtId = NeuItemParser.parseSnbtId(content);
        if (snbtId != null) snbtIds.put(internalName, snbtId);

        if (NeuItemParser.hasGlintOverride(content)) {
            snbtGlints.put(internalName, true);
        }
        return true;
    }

    /**
     * Processes a {@code /constants/*.json} entry. Returns {@code true} if matched.
     */
    private static boolean processConstantsEntry(String name, ZipInputStream zis,
                                                 Map<String, JsonObject> constants) throws IOException {
        int constantsIdx = name.indexOf("/constants/");
        if (constantsIdx < 0 || !name.endsWith(".json")) return false;

        String fileName = name.substring(constantsIdx + 11);
        if (!CONSTANTS_TO_EXTRACT.contains(fileName)) return false;

        String content = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
        try {
            constants.put(fileName, NeuItemParser.GSON.fromJson(content, JsonObject.class));
        } catch (JsonSyntaxException e) {
            LOGGER.warn("Skipping malformed constants file: {}", fileName);
        }
        return true;
    }

    // ── SNBT override application ───────────────────────────────────────────────

    private static void applySnbtOverrides(Map<String, NeuItem> items,
                                           Map<String, String> snbtIds,
                                           Map<String, Boolean> snbtGlints) {
        snbtIds.forEach((id, modernId) -> {
            NeuItem item = items.get(id);
            if (item != null) item.snbtItemId = modernId;
        });

        snbtGlints.forEach((id, glint) -> {
            NeuItem item = items.get(id);
            if (item != null) item.enchantmentGlint = glint;
        });
    }
}