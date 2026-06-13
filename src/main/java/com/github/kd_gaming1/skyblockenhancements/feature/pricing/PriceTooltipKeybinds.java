package com.github.kd_gaming1.skyblockenhancements.feature.pricing;

import com.github.kd_gaming1.skyblockenhancements.feature.KeybindCategories;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Registers and tracks the two price-modifier keybinds:
 * <ul>
 *   <li><b>Full Stack</b> (default Left Control) — multiplies displayed prices by the
 *       item's logical stack size (max stack for inventory items, order amount for Bazaar).</li>
 *   <li><b>Current Amount</b> (default Left Shift) — multiplies displayed prices by the
 *       hovered item's logical stack size (same as Full Stack for Bazaar UI).</li>
 * </ul>
 */
public final class PriceTooltipKeybinds {

    private static KeyMapping fullStackKey;
    private static KeyMapping currentAmountKey;

    private static final KeyMapping.Category CATEGORY = KeybindCategories.GENERAL;

    private PriceTooltipKeybinds() {}

    /** Registers the two modifier keybinds. Call once during mod init. */
    public static void init() {
        fullStackKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping(
                        "key.skyblock_enhancements.full_stack_price",
                        GLFW.GLFW_KEY_LEFT_CONTROL,
                        CATEGORY));

        currentAmountKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping(
                        "key.skyblock_enhancements.current_amount_price",
                        GLFW.GLFW_KEY_LEFT_SHIFT,
                        CATEGORY));
    }

    /** Returns {@code true} while the Full Stack modifier key is physically held. */
    public static boolean isFullStackHeld() {
        return isKeyPhysicallyDown(fullStackKey);
    }

    /** Returns {@code true} while the Current Amount modifier key is physically held. */
    public static boolean isCurrentAmountHeld() {
        return isKeyPhysicallyDown(currentAmountKey);
    }

    /**
     * Returns the translated display name of the Full Stack key, or empty if unbound.
     * Useful for hint lines in tooltips.
     */
    public static String getFullStackKeyName() {
        if (fullStackKey == null) return "";
        return fullStackKey.getTranslatedKeyMessage().getString();
    }

    /**
     * Returns the translated display name of the Current Amount key, or empty if unbound.
     * Useful for hint lines in tooltips.
     */
    public static String getCurrentAmountKeyName() {
        if (currentAmountKey == null) return "";
        return currentAmountKey.getTranslatedKeyMessage().getString();
    }

    // ── GLFW polling ───────────────────────────────────────────────────────────

    /**
     * Queries GLFW directly to determine whether a key is physically pressed.
     * This bypasses Minecraft's {@link KeyMapping#setAll()} / {@code isDown()}
     * logic which can be stale or reset while GUI screens are open.
     *
     * <p>Uses reflection to read {@code KeyMapping.key} (protected, no getter)
     * so rebound keys are detected correctly.
     */
    private static boolean isKeyPhysicallyDown(KeyMapping mapping) {
        if (mapping == null) return false;

        InputConstants.Key key = getBoundKey(mapping);
        if (key == null || key.getType() != InputConstants.Type.KEYSYM) return false;

        int keyCode = key.getValue();
        if (keyCode == InputConstants.UNKNOWN.getValue()) return false;

        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();

        return InputConstants.isKeyDown(window, keyCode);
    }

    /** Reads the current {@code KeyMapping.key} via Fabric API. */
    private static InputConstants.Key getBoundKey(KeyMapping mapping) {
        return KeyMappingHelper.getBoundKeyOf(mapping);
    }
}
