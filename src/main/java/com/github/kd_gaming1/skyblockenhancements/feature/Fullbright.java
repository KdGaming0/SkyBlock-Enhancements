package com.github.kd_gaming1.skyblockenhancements.feature;

import com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements;
import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import eu.midnightdust.lib.config.MidnightConfig;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class Fullbright {

    private static KeyMapping toggleKey;
    private static final KeyMapping.Category CATEGORY = KeybindCategories.GENERAL;

    public static void init() {
        toggleKey =
                KeyBindingHelper.registerKeyBinding(
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
                client.player.displayClientMessage(
                        Component.literal("Fullbright " + (on ? "§aenabled" : "§cdisabled")), true);
            }
        }
    }
}