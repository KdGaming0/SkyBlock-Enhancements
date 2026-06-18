package com.github.kd_gaming1.skyblockenhancements.util;

import com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements;
import com.github.kd_gaming1.skyblockenhancements.repo.io.AtomicFileWriter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tracks the current SkyBlock profile UUID by listening for Hypixel's automatic
 * {@code Profile ID: ...} system-chat messages, which are sent on every server change.
 *
 * <p>The UUID is exposed through a static API so any feature can query it without issuing
 * redundant {@code /profileid} commands.
 */
public final class ProfileIdTracker {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            SkyblockEnhancements.MOD_ID + "/ProfileIdTracker");

    private static final Pattern PROFILE_ID_LINE =
            Pattern.compile("Profile ID: ([a-z0-9\\-]+)", Pattern.CASE_INSENSITIVE);

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type CACHE_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    private static Path cachePath;
    private static Map<String, String> profileCache = new HashMap<>();

    private static UUID profileId;
    private static boolean profileIdConfirmed;

    private ProfileIdTracker() {}

    /** Loads the persisted UUID cache and registers the disconnect reset. */
    public static void register(Path cacheFilePath) {
        cachePath = cacheFilePath;
        loadCache();
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    /**
     * Returns the current SkyBlock profile UUID, if known.
     *
     * <p>The returned value is the most recently confirmed UUID. While on SkyBlock and no UUID has
     * been confirmed this session, the last cached UUID for the current account is returned
     * tentatively so features can display locks immediately on rejoin.
     */
    public static Optional<UUID> getProfileId() {
        if (profileId != null) {
            return Optional.of(profileId);
        }
        if (HypixelLocationState.isOnSkyblock()) {
            String cached = profileCache.get(accountUuid());
            if (cached != null) {
                try {
                    return Optional.of(UUID.fromString(cached));
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("Ignoring malformed cached profile UUID for {}: {}", accountUuid(), cached);
                }
            }
        }
        return Optional.empty();
    }

    /** {@code true} if the current profile UUID has been confirmed by a chat message this session. */
    public static boolean isProfileIdConfirmed() {
        return profileIdConfirmed;
    }

    /**
     * Called by the incoming-chat mixin. Parses the profile UUID if present.
     *
     * <p>Hypixel sends {@code Profile ID: ...} automatically on every SkyBlock server change, so
     * no command needs to be sent by the mod.
     */
    public static void handleIncomingChat(String rawText) {
        if (rawText == null) return;
        String stripped = StringUtil.stripColorCodes(rawText).trim();
        if (stripped.isEmpty()) return;

        Matcher matcher = PROFILE_ID_LINE.matcher(stripped);
        if (matcher.find()) {
            try {
                UUID parsed = UUID.fromString(matcher.group(1));
                if (!parsed.equals(profileId)) {
                    profileId = parsed;
                    profileIdConfirmed = true;
                    cacheProfileUuid(parsed);
                    LOGGER.debug("Detected SkyBlock profile UUID: {}", profileId);
                }
            } catch (IllegalArgumentException e) {
                LOGGER.error("Failed to parse profile UUID from: {}", stripped, e);
            }
        }
    }

    // ── Cache ──────────────────────────────────────────────────────────────────

    private static void loadCache() {
        if (cachePath == null) return;
        try {
            if (!Files.exists(cachePath)) {
                profileCache = new HashMap<>();
                return;
            }
            try (BufferedReader reader = Files.newBufferedReader(cachePath, StandardCharsets.UTF_8)) {
                Map<String, String> loaded = GSON.fromJson(reader, CACHE_TYPE);
                profileCache = (loaded != null) ? new HashMap<>(loaded) : new HashMap<>();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load profile UUID cache, starting fresh", e);
            profileCache = new HashMap<>();
        }
    }

    private static void cacheProfileUuid(UUID uuid) {
        if (cachePath == null) return;
        String account = accountUuid();
        String previous = profileCache.put(account, uuid.toString());
        if (uuid.toString().equals(previous)) return;
        try {
            AtomicFileWriter.writeJson(cachePath, profileCache, GSON);
        } catch (IOException e) {
            LOGGER.error("Failed to save profile UUID cache", e);
        }
    }

    private static void reset() {
        profileId = null;
        profileIdConfirmed = false;
    }

    private static String accountUuid() {
        var user = Minecraft.getInstance().getUser();
        return (user != null) ? user.getProfileId().toString() : "unknown";
    }
}
