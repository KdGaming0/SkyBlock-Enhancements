package com.github.kd_gaming1.skyblockenhancements.util;

import com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements;
import com.github.kd_gaming1.skyblockenhancements.repo.io.AtomicFileWriter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.azureaaron.hmapi.events.HypixelPacketEvents;
import net.azureaaron.hmapi.network.packet.v1.s2c.LocationUpdateS2CPacket;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
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
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tracks the current SkyBlock profile UUID by probing the server with {@code /profileid}.
 *
 * <p>This is faster and more reliable than parsing the tab list for the profile cute-name.
 * The UUID is exposed through a static API so any feature can query it.
 *
 * <p>Design notes:
 * <ul>
 *   <li>Probes are sent immediately when the player enters SkyBlock and then every
 *       {@value #RETRY_INTERVAL_TICKS} ticks, up to {@value #MAX_PROBE_ATTEMPTS} attempts, until a
 *       response is received. The cap stops the mod from issuing {@code /profileid} forever if a
 *       response never matches.</li>
 *   <li>A new server instance (a changed {@link LocationUpdateS2CPacket#serverName()}) can mean a
 *       profile switch, so a fresh probe is scheduled. The previously confirmed UUID is kept until a
 *       <em>different</em> one is confirmed, so ordinary warps within the same profile don't flicker
 *       the locks.</li>
 *   <li>The outgoing {@code /profileid} command is suppressed so the player never sees the mod
 *       probing. The {@code Profile ID: ...} response is parsed; responses to the mod's own probes
 *       are hidden (so re-probing on every server change doesn't spam chat), while a manually typed
 *       {@code /profileid} stays visible.</li>
 *   <li>The suggestion-spam lines that sometimes accompany the response are hidden within a
 *       short window after a probe.</li>
 *   <li>A per-account cache of the last known profile UUID is persisted to disk. It is used
 *       tentatively on entering SkyBlock so locks appear instantly when rejoining the same
 *       profile, while {@code /profileid} verifies/corrects the value in the background.</li>
 *   <li>State is reset on disconnect and when the player leaves SkyBlock.</li>
 * </ul>
 */
public final class ProfileIdTracker {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            SkyblockEnhancements.MOD_ID + "/ProfileIdTracker");

    private static final Pattern PROFILE_ID_LINE =
            Pattern.compile("Profile ID: ([a-z0-9\\-]+)", Pattern.CASE_INSENSITIVE);

    private static final String[] SUGGESTION_TEXTS = {
            "CLICK THIS TO SUGGEST IT IN CHAT [DASHES]",
            "CLICK THIS TO SUGGEST IT IN CHAT [NO DASHES]"
    };

    private static final int RETRY_INTERVAL_TICKS = 200; // 10 seconds between probes while awaiting a response
    private static final int MAX_PROBE_ATTEMPTS = 5;     // give up after this many to avoid endless commands
    private static final int REPROBE_DEBOUNCE_TICKS = 60; // 3 seconds: coalesce rapid server hops into one probe
    private static final int SUGGESTION_HIDE_WINDOW_TICKS = 100; // 5 seconds

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type CACHE_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    private static Path cachePath;
    private static Map<String, String> profileCache = new HashMap<>();

    private static UUID profileId;
    private static boolean profileIdConfirmed;
    private static boolean wasOnSkyblock = false;
    private static boolean awaitingConfirm = false; // actively probing to (re)confirm the profile UUID
    private static int attemptsLeft = 0;            // remaining probes before giving up this round
    private static int retryTimer = 0;
    private static int ticksSinceProbe = Integer.MAX_VALUE;
    private static boolean pendingSend = false;
    private static String lastServerName;           // last SkyBlock server instance seen (e.g. "mini88A")
    private static boolean serverChanged = false;   // a new server instance arrived; re-probe on the next tick

    private ProfileIdTracker() {}

    /** Registers the tick handler, location listener, and disconnect reset; loads the persisted UUID cache. */
    public static void register(Path cacheFilePath) {
        cachePath = cacheFilePath;
        loadCache();
        ClientTickEvents.END_CLIENT_TICK.register(ProfileIdTracker::onClientTick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
        HypixelPacketEvents.LOCATION_UPDATE.register(packet -> {
            if (packet instanceof LocationUpdateS2CPacket location) {
                onServerChanged(location.serverName());
            }
        });
    }

    /**
     * Flags a re-probe when the player moves to a different server instance, which can mean a
     * profile switch. The tick handler performs the actual probe (where the connection is known
     * to be live). Runs on the client thread (HM-API dispatches via {@code Minecraft.execute}).
     */
    private static void onServerChanged(String serverName) {
        if (!Objects.equals(serverName, lastServerName)) {
            lastServerName = serverName;
            serverChanged = true;
        }
    }

    /**
     * Returns the current SkyBlock profile UUID, if known.
     *
     * <p>The returned value is the most recently confirmed UUID. While on SkyBlock and no UUID has
     * been confirmed this session, the last cached UUID for the current account is returned
     * tentatively so features can display locks immediately on rejoin.
     *
     * <p>This is the canonical source for profile UUID; other features should call it
     * instead of parsing the tab list themselves.
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

    /** {@code true} if the current profile UUID has been confirmed by {@code /profileid} this session. */
    public static boolean isProfileIdConfirmed() {
        return profileIdConfirmed;
    }

    /**
     * Called by the outgoing-chat mixin. Returns {@code true} if the message should be
     * suppressed because it is this tracker’s internal {@code /profileid} probe.
     */
    public static boolean shouldSuppressOutgoingCommand(String message) {
        if (!pendingSend) return false;
        if (!"/profileid".equals(message)) return false;
        pendingSend = false;
        return true;
    }

    /**
     * Called by the incoming-chat mixin. Parses the profile UUID if present and returns
     * {@code true} if the message should be suppressed: a {@code Profile ID: ...} line answering
     * the mod's own probe, or the suggestion-spam follow-ups. A manually typed {@code /profileid}
     * (no recent probe) stays visible.
     */
    public static boolean handleIncomingChat(String rawText) {
        if (rawText == null) return false;
        String stripped = StringUtil.stripColorCodes(rawText).trim();
        if (stripped.isEmpty()) return false;

        Matcher matcher = PROFILE_ID_LINE.matcher(stripped);
        if (matcher.find()) {
            try {
                UUID parsed = UUID.fromString(matcher.group(1));
                if (!parsed.equals(profileId)) {
                    profileId = parsed;
                    cacheProfileUuid(parsed);
                    LOGGER.info("Detected SkyBlock profile UUID: {}", profileId);
                }
                profileIdConfirmed = true;
                awaitingConfirm = false; // got our answer — stop retrying this round
                retryTimer = 0;
            } catch (IllegalArgumentException e) {
                LOGGER.error("Failed to parse profile UUID from: {}", stripped, e);
            }
            // Hide responses to the mod's own probes so re-probing on every server change doesn't
            // spam chat; a manually typed /profileid (no recent probe) stays visible.
            return ticksSinceProbe <= SUGGESTION_HIDE_WINDOW_TICKS;
        }

        if (ticksSinceProbe <= SUGGESTION_HIDE_WINDOW_TICKS) {
            for (String text : SUGGESTION_TEXTS) {
                if (stripped.contains(text)) {
                    return true;
                }
            }
        }
        return false;
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

    // ── Tick handler ───────────────────────────────────────────────────────────

    private static void onClientTick(Minecraft client) {
        if (!HypixelLocationState.isOnSkyblock()) {
            if (wasOnSkyblock) {
                reset();
            }
            return;
        }

        ticksSinceProbe++;
        boolean justEntered = !wasOnSkyblock;
        wasOnSkyblock = true;

        // (Re)start probing on entry, or after a server change that may be a profile switch.
        if (justEntered || serverChanged) {
            boolean debounced = !justEntered && ticksSinceProbe < REPROBE_DEBOUNCE_TICKS;
            serverChanged = false;
            if (!debounced) {
                beginProbing(client);
                return;
            }
        }

        if (awaitingConfirm) {
            retryTimer++;
            if (retryTimer >= RETRY_INTERVAL_TICKS) {
                retryTimer = 0;
                probeOrGiveUp(client);
            }
        }
    }

    /** Starts a fresh round of probing: resets the attempt budget and sends the first probe. */
    private static void beginProbing(Minecraft client) {
        awaitingConfirm = true;
        attemptsLeft = MAX_PROBE_ATTEMPTS;
        retryTimer = 0;
        probeOrGiveUp(client);
    }

    /** Sends a probe if attempts remain, otherwise stops quietly and keeps the current UUID. */
    private static void probeOrGiveUp(Minecraft client) {
        if (attemptsLeft <= 0) {
            awaitingConfirm = false;
            return;
        }
        attemptsLeft--;
        sendProfileIdCommand(client);
    }

    private static void sendProfileIdCommand(Minecraft client) {
        if (client.player == null || client.getConnection() == null) return;
        pendingSend = true;
        ticksSinceProbe = 0;
        client.getConnection().sendCommand("profileid");
    }

    private static void reset() {
        profileId = null;
        profileIdConfirmed = false;
        wasOnSkyblock = false;
        awaitingConfirm = false;
        attemptsLeft = 0;
        retryTimer = 0;
        ticksSinceProbe = Integer.MAX_VALUE;
        pendingSend = false;
        lastServerName = null;
        serverChanged = false;
    }

    private static String accountUuid() {
        var user = Minecraft.getInstance().getUser();
        return (user != null) ? user.getProfileId().toString() : "unknown";
    }
}
