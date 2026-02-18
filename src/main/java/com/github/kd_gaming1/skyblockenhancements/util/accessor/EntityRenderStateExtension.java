package com.github.kd_gaming1.skyblockenhancements.util.accessor;

import java.util.UUID;

/** Duck interface for accessing the entity UUID stored on render states by our mixin. */
public interface EntityRenderStateExtension {
    UUID sbe_getUuid();
    void sbe_setUuid(UUID uuid);
}