package com.github.kd_gaming1.skyblockenhancements.mixin;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.List;
import java.util.stream.Collectors;

@Mixin(ClientPacketListener.class)
public class ClientPlayNetworkHandlerMixin {

    @ModifyArg(
            method = "handleSetEntityData",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/syncher/SynchedEntityData;assignValues(Ljava/util/List;)V"
            ),
            index = 0
    )
    private List<SynchedEntityData.DataValue<?>> filterPose(List<SynchedEntityData.DataValue<?>> list) {
        if (!SkyblockEnhancementsConfig.noDoubleSneak) return list;

        return list.stream()
                .filter(e -> e.serializer() != EntityDataSerializers.POSE)
                .collect(Collectors.toList());
    }
}