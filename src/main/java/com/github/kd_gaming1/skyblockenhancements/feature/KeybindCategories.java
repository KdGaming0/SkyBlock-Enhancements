package com.github.kd_gaming1.skyblockenhancements.feature;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class KeybindCategories {
    public static final KeyMapping.Category GENERAL =
            KeyMapping.Category.register(
                    Identifier.fromNamespaceAndPath("skyblock_enhancements", "general")
            );

    private KeybindCategories() {}
}