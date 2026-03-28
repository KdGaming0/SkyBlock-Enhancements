package com.github.kd_gaming1.skyblockenhancements.mixin;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Suppresses conflicting POSE packets sent by the server for the local player,
 * preventing the camera from bouncing when quickly sneaking/unsneaking on high ping.
 * Other players are intentionally unaffected.
 */
@Mixin(ClientPacketListener.class)
public class ClientPlayNetworkHandlerMixin {

    @Unique
    private int sbe_currentEntityId = -1;

    @Inject(method = "handleSetEntityData", at = @At("HEAD"))
    private void sbe_captureEntityId(ClientboundSetEntityDataPacket packet, CallbackInfo ci) {
        sbe_currentEntityId = packet.id();
    }

    @ModifyArg(
            method = "handleSetEntityData",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/syncher/SynchedEntityData;assignValues(Ljava/util/List;)V"
            ),
            index = 0
    )
    private List<SynchedEntityData.DataValue<?>> sbe_filterPose(List<SynchedEntityData.DataValue<?>> list) {
        if (!SkyblockEnhancementsConfig.noDoubleSneak) return list;

        Minecraft mc = Minecraft.getInstance();
        // Only filter the local player
        if (mc.player == null || mc.player.getId() != sbe_currentEntityId) return list;

        boolean sneaking = mc.player.isShiftKeyDown();
        return list.stream()
                .filter(e -> {
                    if (e.serializer() != EntityDataSerializers.POSE) return true;
                    Pose pose = (Pose) e.value();
                    // Drop any server pose that contradicts our current input state.
                    // Sneaking: ignore stale STANDING (prevents bounce up)
                    // Not sneaking: ignore stale CROUCHING (prevents bounce down)
                    return sneaking ? pose != Pose.STANDING : pose != Pose.CROUCHING;
                })
                .toList();
    }
}