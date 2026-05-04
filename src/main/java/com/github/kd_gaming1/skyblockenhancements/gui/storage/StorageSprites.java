package com.github.kd_gaming1.skyblockenhancements.gui.storage;

import static com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements.MOD_ID;

import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.resources.Identifier;

/**
 * Resource-pack-retexturable sprite sets for the storage dashboard.
 */
public final class StorageSprites {

    public static final WidgetSprites PANEL_BG = new WidgetSprites(
            id("storage/panel_background"),
            id("storage/panel_background"),
            id("storage/panel_background"));

    public static final WidgetSprites PAGE_BORDER_ACTIVE = new WidgetSprites(
            id("storage/page_border_active"),
            id("storage/page_border_active"),
            id("storage/page_border_active"));

    public static final WidgetSprites PAGE_BORDER_INACTIVE = new WidgetSprites(
            id("storage/page_border_inactive"),
            id("storage/page_border_inactive"),
            id("storage/page_border_inactive"));

    public static final Identifier SLOT_BG = id("storage/slot_background");

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    private StorageSprites() {}
}
