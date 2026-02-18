package com.github.kd_gaming1.skyblockenhancements.mixin.itemglow;

import com.github.kd_gaming1.skyblockenhancements.util.accessor.EntityRenderStateExtension;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.UUID;

/** Adds a UUID field to EntityRenderState so we can look up glow data per-entity. */
@Mixin(EntityRenderState.class)
public class EntityRenderStateMixin implements EntityRenderStateExtension {

    @Unique
    private UUID sbe_uuid = null;

    @Override
    public UUID sbe_getUuid() { return sbe_uuid; }

    @Override
    public void sbe_setUuid(UUID uuid) { this.sbe_uuid = uuid; }
}