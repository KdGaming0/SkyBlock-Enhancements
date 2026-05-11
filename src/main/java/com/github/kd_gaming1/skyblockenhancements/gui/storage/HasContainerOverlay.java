package com.github.kd_gaming1.skyblockenhancements.gui.storage;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * Interface that allows attaching a {@link ContainerOverlay} to any
 * {@link AbstractContainerScreen} without requiring a dedicated accessor class.
 */
@SuppressWarnings("unused")
public interface HasContainerOverlay {
    ContainerOverlay skyBlock_Enhancements$getSbeOverlay();

    void skyBlock_Enhancements$setSbeOverlay(ContainerOverlay overlay);
}
