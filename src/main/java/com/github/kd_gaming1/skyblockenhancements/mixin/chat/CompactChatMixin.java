package com.github.kd_gaming1.skyblockenhancements.mixin.chat;

import com.github.kd_gaming1.skyblockenhancements.access.SBEChatAccess;
import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.feature.chat.compact.CompactMessageHandler;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Compacts consecutive duplicate chat messages via {@link CompactMessageHandler}. Priority is
 * {@code Integer.MAX_VALUE} so every other chat mixin observes the already-compacted message.
 */
@Mixin(value = ChatComponent.class, priority = Integer.MAX_VALUE)
public abstract class CompactChatMixin {

    @Unique private CompactMessageHandler sbe$compactHandler;
    @Unique private boolean sbe$compactedThisMessage;

    @Unique
    private CompactMessageHandler sbe$handler() {
        if (sbe$compactHandler == null) {
            sbe$compactHandler = new CompactMessageHandler((SBEChatAccess) this);
        }
        return sbe$compactHandler;
    }

    @ModifyVariable(
            method = "addMessage(Lnet/minecraft/network/chat/Component;"
                    + "Lnet/minecraft/network/chat/MessageSignature;"
                    + "Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("HEAD"),
            argsOnly = true)
    private Component sbe$compact(Component message) {
        if (!SkyblockEnhancementsConfig.compactDuplicateMessages) return message;
        Component processed = sbe$handler().process(message);
        sbe$compactedThisMessage = processed != message;
        return processed;
    }

    /**
     * After vanilla finishes adding the compacted message, rebuild the display queue so
     * stale lines from the prior occurrence are discarded. Removing the old message from
     * {@code allMessages} happens before the new one is enqueued, so a refresh mid-flight
     * cannot see the replacement — only a post-addMessage refresh can produce a consistent
     * display state.
     */
    @Inject(
            method = "addMessage(Lnet/minecraft/network/chat/Component;"
                    + "Lnet/minecraft/network/chat/MessageSignature;"
                    + "Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("TAIL"))
    private void sbe$refreshAfterCompact(
            Component message, MessageSignature sig, GuiMessageTag tag, CallbackInfo ci) {
        if (sbe$compactedThisMessage) {
            sbe$compactedThisMessage = false;
            ((SBEChatAccess) this).sbe$refreshMessages();
        }
    }

    @Inject(method = "clearMessages", at = @At("HEAD"))
    private void sbe$clearCompact(boolean clearHistory, CallbackInfo ci) {
        sbe$handler().clear();
    }
}