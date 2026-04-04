package com.github.kd_gaming1.skyblockenhancements.feature.filter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;

/**
 * Installs custom Log4j filters. Call {@link #register()} once during mod initialization.
 */
public final class LogFilterRegistry {

    private LogFilterRegistry() {}

    /** Attaches all SBE log filters to the root logger. Safe to call multiple times. */
    public static void register() {
        Logger rootLogger = (Logger) LogManager.getRootLogger();
        rootLogger.addFilter(new TextureSignatureFilter());
    }
}