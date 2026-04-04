package com.github.kd_gaming1.skyblockenhancements.feature.filter;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.message.Message;

/**
 * Suppresses the noisy texture-signature errors that Mojang's auth library logs when Hypixel
 * sends NPC entities with unsigned or malformed skin texture properties.
 *
 * <p>Two loggers produce these messages:
 * <ul>
 *   <li>{@code YggdrasilServicesKeyInfo} — "Failed to verify signature on property"</li>
 *   <li>{@code YggdrasilMinecraftSessionService} — "Could not decode textures payload"</li>
 * </ul>
 *
 * <p>The filter also catches the downstream warnings from Minecraft's {@code SkinManager}:
 * "Profile contained invalid signature for textures property".
 *
 * <p>Gated behind {@link SkyblockEnhancementsConfig#hideTextureErrors} (default: {@code false}).
 */
public final class TextureSignatureFilter extends AbstractFilter {

    private static final String YGGDRASIL_KEY_INFO =
            "com.mojang.authlib.yggdrasil.YggdrasilServicesKeyInfo";
    private static final String YGGDRASIL_SESSION =
            "com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService";

    private static final String SIGNATURE_MSG = "Failed to verify signature";
    private static final String DECODE_MSG = "Could not decode textures payload";
    private static final String INVALID_SIGNATURE_MSG = "invalid signature for textures property";

    public TextureSignatureFilter() {
        super(Result.DENY, Result.NEUTRAL);
    }

    @Override
    public Result filter(LogEvent event) {
        if (!SkyblockEnhancementsConfig.hideTextureErrors) return Result.NEUTRAL;

        return shouldDeny(event.getLoggerName(), event.getLevel(), event.getMessage())
                ? Result.DENY
                : Result.NEUTRAL;
    }

    @Override
    public Result filter(
            Logger logger, Level level, Marker marker, Message msg, Throwable t) {
        if (!SkyblockEnhancementsConfig.hideTextureErrors) return Result.NEUTRAL;

        return shouldDeny(logger.getName(), level, msg) ? Result.DENY : Result.NEUTRAL;
    }

    @Override
    public Result filter(
            Logger logger, Level level, Marker marker, String msg, Object... params) {
        if (!SkyblockEnhancementsConfig.hideTextureErrors) return Result.NEUTRAL;

        if (level != Level.ERROR && level != Level.WARN) return Result.NEUTRAL;

        String loggerName = logger.getName();
        if (isTextureLogger(loggerName) && containsTextureMessage(msg)) return Result.DENY;

        return Result.NEUTRAL;
    }

    private boolean shouldDeny(String loggerName, Level level, Message msg) {
        if (level != Level.ERROR && level != Level.WARN) return Result.NEUTRAL == Result.DENY;
        if (!isTextureLogger(loggerName)) return false;

        return containsTextureMessage(msg.getFormattedMessage());
    }

    private static boolean isTextureLogger(String loggerName) {
        return loggerName.equals(YGGDRASIL_KEY_INFO)
                || loggerName.equals(YGGDRASIL_SESSION)
                || loggerName.contains("SkinManager")
                || loggerName.contains("class_1071");
    }

    private static boolean containsTextureMessage(String msg) {
        if (msg == null) return false;
        return msg.contains(SIGNATURE_MSG)
                || msg.contains(DECODE_MSG)
                || msg.contains(INVALID_SIGNATURE_MSG);
    }
}