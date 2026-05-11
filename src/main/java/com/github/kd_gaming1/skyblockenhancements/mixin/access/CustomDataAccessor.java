package com.github.kd_gaming1.skyblockenhancements.mixin.access;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the private {@code tag} field of {@link CustomData} so callers can read
 * NBT values without paying for an expensive {@link CompoundTag#copy()}.
 *
 * <p>The returned tag must never be mutated — it is the live backing store shared
 * by every reference to this {@code CustomData} instance.
 */
@Mixin(CustomData.class)
public interface CustomDataAccessor {

    @Accessor("tag")
    CompoundTag getRawTag();
}
