package com.github.kd_gaming1.skyblockenhancements.mixin.chat;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.List;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Slides newly-arriving chat messages up into view instead of popping them in instantly.
 *
 * <p>Only triggers when the chat is scrolled to the bottom and the incoming message actually
 * added visible lines (filtered-out messages don't animate).
 *
 * <p>Inspired by Ezzenix's ChatAnimation (CC-BY-NC-SA 4.0); this is an original implementation.
 */
@Mixin(ChatComponent.class)
public abstract class ChatAnimationMixin {

    @Unique
    private static final float ANIMATION_LINE_FACTOR = 0.8f;

    @Shadow private int chatScrollbarPos;
    @Shadow @Final private List<GuiMessage.Line> trimmedMessages;
    @Shadow protected abstract int getLineHeight();

    @Unique private long sbe$lastMessageTime;
    @Unique private int sbe$displaySizeBefore;

    @Unique
    private float sbe$displacement() {
        if (!SkyblockEnhancementsConfig.enableChatAnimation || chatScrollbarPos != 0) return 0f;

        int duration = SkyblockEnhancementsConfig.chatAnimationDurationMs;
        float elapsed = System.currentTimeMillis() - sbe$lastMessageTime;
        float progress = Math.min(elapsed / (float) duration, 1f);
        float eased = 1f - (1f - progress) * (1f - progress); // ease-out quadratic
        return getLineHeight() * ANIMATION_LINE_FACTOR * (1f - eased);
    }

    @WrapOperation(
            method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;IIIZZ)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/ChatComponent;"
                            + "render(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IIZ)V"))
    private void sbe$animateRender(
            ChatComponent instance,
            ChatComponent.ChatGraphicsAccess access,
            int i, int j, boolean bl,
            Operation<Void> original,
            @Local(argsOnly = true) GuiGraphics graphics) {

        float dy = sbe$displacement();
        if (dy != 0f) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(0f, dy);
        }
        original.call(instance, access, i, j, bl);
        if (dy != 0f) graphics.pose().popMatrix();
    }

    @Inject(
            method = "addMessage(Lnet/minecraft/network/chat/Component;"
                    + "Lnet/minecraft/network/chat/MessageSignature;"
                    + "Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("HEAD"))
    private void sbe$snapshotDisplaySize(
            Component message, MessageSignature sig, GuiMessageTag tag, CallbackInfo ci) {
        sbe$displaySizeBefore = trimmedMessages.size();
    }

    @Inject(
            method = "addMessage(Lnet/minecraft/network/chat/Component;"
                    + "Lnet/minecraft/network/chat/MessageSignature;"
                    + "Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("TAIL"))
    private void sbe$recordMessageTime(
            Component message, MessageSignature sig, GuiMessageTag tag, CallbackInfo ci) {
        // Only start a new animation if the message actually produced visible lines.
        if (trimmedMessages.size() > sbe$displaySizeBefore) {
            sbe$lastMessageTime = System.currentTimeMillis();
        }
    }
}