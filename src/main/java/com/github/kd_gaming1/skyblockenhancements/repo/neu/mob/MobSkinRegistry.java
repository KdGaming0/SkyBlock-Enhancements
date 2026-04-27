package com.github.kd_gaming1.skyblockenhancements.repo.neu.mob;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.LOGGER;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Stores NEU mob skin PNGs in memory and lazily registers them as GPU textures on first render.
 *
 * <p><b>Storage model:</b> skins are never written to disk. Raw PNG bytes are held in
 * {@link #RAW} after ZIP parsing. On first render request, {@link #getOrLoad(String)} decodes
 * the PNG into a {@link DynamicTexture} and registers it with Minecraft's {@link TextureManager}.
 * The texture then lives as a GPU resource until {@link #clear()} is called.
 *
 * <p>Keys are normalised to a bare, identifier-safe name (e.g. {@code "alligator"}) regardless
 * of input format — callers can pass {@code "alligator"}, {@code "alligator.png"},
 * {@code "mobs/alligator.png"}, or {@code "neurepo:mobs/alligator.png"}.
 */
public final class MobSkinRegistry {

    private static final String NAMESPACE = "skyblock_enhancements";
    private static final String TEXTURE_PATH_PREFIX = "neumobskin/";

    /** Sentinel value stored in {@link #REGISTERED} when a PNG fails to decode. */
    private static final Identifier FAILED_SENTINEL =
            Identifier.fromNamespaceAndPath(NAMESPACE, TEXTURE_PATH_PREFIX + "__failed__");

    private static final Map<String, byte[]> RAW = new ConcurrentHashMap<>();
    private static final Map<String, Identifier> REGISTERED = new ConcurrentHashMap<>();

    private MobSkinRegistry() {}

    /**
     * Stores raw PNG bytes under the normalised key. Called during ZIP parsing by
     * {@link com.github.kd_gaming1.skyblockenhancements.repo.MobPngEntryProcessor}.
     */
    public static void store(String skinRef, byte[] png) {
        if (skinRef == null || png == null || png.length == 0) return;
        String key = normalise(skinRef);
        if (key.isEmpty()) {
            LOGGER.warn("Mob skin ref normalised to empty string: '{}'", skinRef);
            return;
        }
        RAW.put(key, png);
    }

    /**
     * Returns the registered texture {@link Identifier} for the given skin ref, lazily
     * decoding and uploading the PNG on first access. Returns {@code null} if the ref
     * has no stored PNG or the PNG fails to decode.
     *
     * <p>Must be called from the render thread (GPU texture creation requires it).
     */
    @Nullable
    public static Identifier getOrLoad(String skinRef) {
        if (skinRef == null) return null;
        String key = normalise(skinRef);
        if (key.isEmpty()) return null;

        Identifier existing = REGISTERED.get(key);
        if (existing != null) {
            return existing == FAILED_SENTINEL ? null : existing;
        }

        byte[] png = RAW.get(key);
        if (png == null) {
            LOGGER.warn("No stored PNG for mob skin key '{}' (raw ref: '{}'). "
                    + "RAW contains {} entries.", key, skinRef, RAW.size());
            REGISTERED.put(key, FAILED_SENTINEL);
            return null;
        }

        return registerTexture(key, skinRef, png);
    }

    /**
     * Decodes the PNG bytes and registers the resulting texture. On failure, marks the
     * key as failed so subsequent frames don't retry.
     */
    @Nullable
    private static Identifier registerTexture(String key, String skinRef, byte[] png) {
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(png));
            Identifier id = Identifier.fromNamespaceAndPath(NAMESPACE, TEXTURE_PATH_PREFIX + key);
            TextureManager tm = Minecraft.getInstance().getTextureManager();
            tm.register(id, new DynamicTexture(id::toString, image));
            REGISTERED.put(key, id);
            LOGGER.debug("Registered mob skin texture: {} ({}x{}, {} bytes)",
                    key, image.getWidth(), image.getHeight(), png.length);
            return id;
        } catch (Exception e) {
            LOGGER.error("Failed to decode mob skin '{}' (key: '{}', {} bytes): {}",
                    skinRef, key, png.length, e.toString());
            REGISTERED.put(key, FAILED_SENTINEL);
            return null;
        }
    }

    /** Releases all GPU textures and discards raw bytes. Called on repo reload. */
    public static void clear() {
        TextureManager tm = Minecraft.getInstance().getTextureManager();
        for (Identifier id : REGISTERED.values()) {
            if (id != FAILED_SENTINEL) {
                tm.release(id);
            }
        }
        int textureCount = REGISTERED.size();
        int rawCount = RAW.size();
        REGISTERED.clear();
        RAW.clear();
        LOGGER.debug("Cleared mob skin registry ({} textures, {} raw entries)", textureCount, rawCount);
    }

    /** Returns {@code true} if a raw PNG is stored for the given ref. */
    public static boolean has(String skinRef) {
        if (skinRef == null) return false;
        String key = normalise(skinRef);
        return !key.isEmpty() && RAW.containsKey(key);
    }

    public static int storedCount() {
        return RAW.size();
    }

    /**
     * Reduces any NEU skin reference to a canonical, identifier-safe bare name.
     * <pre>
     *   "neurepo:mobs/alligator.png" → "alligator"
     *   "mobs/alligator.png"         → "alligator"
     *   "alligator.png"              → "alligator"
     *   "alligator"                  → "alligator"
     * </pre>
     */
    static String normalise(String raw) {
        String s = raw.trim();
        int colon = s.indexOf(':');
        if (colon >= 0) s = s.substring(colon + 1);

        int lastSlash = s.lastIndexOf('/');
        if (lastSlash >= 0) s = s.substring(lastSlash + 1);

        int dot = s.lastIndexOf('.');
        if (dot > 0) s = s.substring(0, dot);

        return sanitise(s.toLowerCase(Locale.ROOT));
    }

    private static String sanitise(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        return sb.toString();
    }
}