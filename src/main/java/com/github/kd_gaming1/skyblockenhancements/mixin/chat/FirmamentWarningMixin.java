package com.github.kd_gaming1.skyblockenhancements.mixin.chat;

import com.github.kd_gaming1.skyblockenhancements.compat.rrv.RrvCompat;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses the Firmament "RoughlyEnoughItems" download warning when RRV is present.
 *
 * This mixin cancels the chat addMessage call for messages that look like the Firmament
 * REI prompt. We only do this when RRV is installed so other users still see the warning.
 */
@Mixin(value = ChatComponent.class, priority = 2000)
public abstract class FirmamentWarningMixin {

    @Inject(
            method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void sbe$maybeSuppressFirmament(Component message, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {
        // Only suppress when RRV is installed — otherwise leave Firmament's warning alone.
        if (!RrvCompat.isRrvPresent()) return;

        if (message == null) return;

        String text = message.getString();

        // Match the unique prefix used by Firmament's warning. Keep this check simple and
        // forgiving (contains) to handle minor formatting/localization differences.
        if (text.contains("Firmament needs RoughlyEnoughItems")) {
            ci.cancel();
        }
    }
}

