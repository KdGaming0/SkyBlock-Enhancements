package com.github.kd_gaming1.skyblockenhancements.mixin.chat;

import com.github.kd_gaming1.skyblockenhancements.access.SBEChatAccess;
import com.github.kd_gaming1.skyblockenhancements.feature.chat.CompactMessageHandler;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Compacts duplicate chat messages by appending an occurrence counter (×N).
 *
 * <p>Applied at maximum priority so other mixins see the compacted message.
 */
@Mixin(value = ChatComponent.class, priority = Integer.MAX_VALUE)
public abstract class CompactChatMixin {

    @Unique private CompactMessageHandler sbe$compactHandler;

    @Unique
    private CompactMessageHandler sbe$handler() {
        if (sbe$compactHandler == null) {
            sbe$compactHandler = new CompactMessageHandler((SBEChatAccess) this);
        }
        return sbe$compactHandler;
    }

    @ModifyVariable(
            method =
                    "addMessage(Lnet/minecraft/network/chat/Component;"
                            + "Lnet/minecraft/network/chat/MessageSignature;"
                            + "Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("HEAD"),
            argsOnly = true)
    private Component sbe$compact(Component message) {
        return sbe$handler().process(message);
    }

    @Inject(method = "clearMessages", at = @At("HEAD"))
    private void sbe$clearCompact(boolean clearHistory, CallbackInfo ci) {
        sbe$handler().clear();
    }
}