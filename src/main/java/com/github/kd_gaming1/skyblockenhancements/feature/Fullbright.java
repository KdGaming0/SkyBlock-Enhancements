package com.github.kd_gaming1.skyblockenhancements.feature;

import com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements;
import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import eu.midnightdust.lib.config.MidnightConfig;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class Fullbright {

    /**
     * Gamma value mapped to full strength by the <em>gamma fallback</em> path
     * ({@code OptionInstanceMixin}), used under Iris/shader packs or when the user opts into
     * {@code fullbrightUseGamma}. There the slider drives the vanilla gamma option up to this
     * value, which the lightmap shader turns into a brightened {@code notGamma} curve.
     *
     * <p>The default <em>shader path</em> does not use this constant — it feeds a separate
     * {@code FullbrightIntensity} uniform (0.0–1.0) that the shipped {@code lightmap.fsh} blends
     * toward white, keeping fullbright independent of the vanilla brightness slider.</p>
     */
    public static final double GAMMA_SCALE = 15.0;

    private static KeyMapping toggleKey;
    private static final KeyMapping.Category CATEGORY = KeybindCategories.GENERAL;

    public static void init() {
        toggleKey =
                KeyMappingHelper.registerKeyMapping(
                        new KeyMapping(
                                "key.skyblock_enhancements.fullbright",
                                GLFW.GLFW_KEY_UNKNOWN,
                                CATEGORY));
    }

    public static void onTick(Minecraft client) {
        if (toggleKey == null) return;
        while (toggleKey.consumeClick()) {
            SkyblockEnhancementsConfig.enableFullbright = !SkyblockEnhancementsConfig.enableFullbright;
            MidnightConfig.write(SkyblockEnhancements.MOD_ID);

            if (client.player != null) {
                boolean on = SkyblockEnhancementsConfig.enableFullbright;
                client.gui.setOverlayMessage(
                        Component.literal("Fullbright " + (on ? "§aenabled" : "§cdisabled")), false);
            }
        }
    }
}