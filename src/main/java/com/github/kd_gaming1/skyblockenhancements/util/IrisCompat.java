package com.github.kd_gaming1.skyblockenhancements.util;

import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;

public final class IrisCompat {

    private static final boolean IRIS_LOADED =
            FabricLoader.getInstance().isModLoaded("iris");

    private static Object irisApiInstance;
    private static Method isShaderPackInUse;

    private static boolean cachedShadersActive = false;

    static {
        if (IRIS_LOADED) {
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                irisApiInstance = api.getMethod("getInstance").invoke(null);
                // Resolve from the interface, not the concrete class
                isShaderPackInUse = api.getMethod("isShaderPackInUse");
            } catch (Exception e) {
                irisApiInstance = null;
                isShaderPackInUse = null;
            }
        }
    }

    public static void tick() {
        if (!IRIS_LOADED || isShaderPackInUse == null) return;
        try {
            cachedShadersActive = (boolean) isShaderPackInUse.invoke(irisApiInstance);
        } catch (Exception ignored) {
            cachedShadersActive = false;
        }
    }

    /** Returns true if Iris is loaded AND a shader pack is currently active. */
    public static boolean isShadersActive() {
        return cachedShadersActive;
    }

    private IrisCompat() {}
}