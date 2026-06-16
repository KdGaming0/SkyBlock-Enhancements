package com.github.kd_gaming1.skyblockenhancements.mixin;

import com.github.kd_gaming1.skyblockenhancements.util.ProfileIdTracker;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides the {@code /profileid} response and its suggestion-spam follow-ups from chat.
 *
 * <p>The unformatted text is handed to {@link ProfileIdTracker#handleIncomingChat(String)}
 * for parsing; if the tracker identifies the message as part of its probe, the packet is
 * cancelled before it reaches the chat GUI.
 *
 * <p>Only system chat is intercepted; the {@code /profileid} response is sent as a system
 * message on Hypixel.
 */
@Mixin(ClientPacketListener.class)
public class ProfileIdResponseMixin {

    @Inject(method = "handleSystemChat", at = @At("HEAD"), cancellable = true)
    private void skyblockenhancements$handleSystemChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        if (ProfileIdTracker.handleIncomingChat(packet.content().getString())) {
            ci.cancel();
        }
    }
}
