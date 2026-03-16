package com.github.kd_gaming1.skyblockenhancements.feature;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
//? if >=1.21.11 {
/*import net.minecraft.resources.Identifier;
 *///?} else {
import net.minecraft.resources.ResourceLocation;
//?}
import org.lwjgl.glfw.GLFW;

public class Fullbright {

    private static KeyMapping toggleKey;

    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            //? if >=1.21.11 {
            /*Identifier.fromNamespaceAndPath("skyblock_enhancements", "general")
             *///?} else {
            ResourceLocation.fromNamespaceAndPath("skyblock_enhancements", "general")
            //?}
    );

    public static void init() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.skyblock_enhancements.fullbright",
                GLFW.GLFW_KEY_G,
                CATEGORY
        ));
    }

    public static void onTick(Minecraft client) {
        if (toggleKey == null) return;
        while (toggleKey.consumeClick()) {
            SkyblockEnhancementsConfig.enableFullbright = !SkyblockEnhancementsConfig.enableFullbright;

            if (client.player != null) {
                boolean enabled = SkyblockEnhancementsConfig.enableFullbright;
                client.player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal(
                                "Fullbright " + (enabled ? "§aenabled" : "§cdisabled")
                        ),
                        false
                );
            }
        }
    }
}