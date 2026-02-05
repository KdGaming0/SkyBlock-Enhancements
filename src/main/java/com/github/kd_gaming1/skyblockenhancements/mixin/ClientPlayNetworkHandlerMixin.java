package com.github.kd_gaming1.skyblockenhancements.mixin;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.data.DataTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.List;
import java.util.stream.Collectors;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {

    @ModifyArg(
            method = "onEntityTrackerUpdate",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/data/DataTracker;writeUpdatedEntries(Ljava/util/List;)V"
            ),
            index = 0
    )
    private List<DataTracker.SerializedEntry<?>> filterPose(List<DataTracker.SerializedEntry<?>> list) {
        if (!SkyblockEnhancementsConfig.noDoubleSneak) return list;

        return list.stream()
                .filter(e -> e.handler() != TrackedDataHandlerRegistry.ENTITY_POSE)
                .collect(Collectors.toList());
    }
}