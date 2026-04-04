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
 * Adds a smooth slide-up animation when new messages arrive.
 *
 * <p>Inspired by ChatAnimation by Ezzenix (CC-BY-NC-SA 4.0). This is an original implementation.
 */
@Mixin(ChatComponent.class)
public abstract class ChatAnimationMixin {

    @Shadow private int chatScrollbarPos;

    @Shadow @Final private List<GuiMessage.Line> trimmedMessages;

    @Shadow
    protected abstract int getLineHeight();

    @Unique private long sbe$lastMessageTime;
    @Unique private int sbe$displaySizeBefore;

    @Unique
    private float sbe$displacement() {
        if (!SkyblockEnhancementsConfig.enableChatAnimation || chatScrollbarPos != 0) return 0f;

        float elapsed = System.currentTimeMillis() - sbe$lastMessageTime;
        float progress = Math.min(elapsed / SkyblockEnhancementsConfig.chatAnimationDurationMs, 1f);
        float eased = 1f - (1f - progress) * (1f - progress); // ease-out quadratic
        return getLineHeight() * 0.8f * (1f - eased);
    }

    @WrapOperation(
            method =
                    "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;IIIZZ)V",
            at =
            @At(
                    value = "INVOKE",
                    target =
                            "Lnet/minecraft/client/gui/components/ChatComponent;"
                                    + "render(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IIZ)V"))
    private void sbe$animateRender(
            ChatComponent instance,
            ChatComponent.ChatGraphicsAccess access,
            int i,
            int j,
            boolean bl,
            Operation<Void> original,
            @Local(argsOnly = true) GuiGraphics graphics) {
        float dy = sbe$displacement();
        if (dy != 0) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(0, dy);
        }
        original.call(instance, access, i, j, bl);
        if (dy != 0) {
            graphics.pose().popMatrix();
        }
    }

    @Inject(
            method =
                    "addMessage(Lnet/minecraft/network/chat/Component;"
                            + "Lnet/minecraft/network/chat/MessageSignature;"
                            + "Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("HEAD"))
    private void sbe$snapshotDisplaySize(
            Component message, MessageSignature sig, GuiMessageTag tag, CallbackInfo ci) {
        sbe$displaySizeBefore = trimmedMessages.size();
    }

    @Inject(
            method =
                    "addMessage(Lnet/minecraft/network/chat/Component;"
                            + "Lnet/minecraft/network/chat/MessageSignature;"
                            + "Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("TAIL"))
    private void sbe$recordMessageTime(
            Component message, MessageSignature sig, GuiMessageTag tag, CallbackInfo ci) {
        // Only animate if the message actually added lines to the display queue.
        // Messages filtered out by the active chat tab produce no visible change.
        if (trimmedMessages.size() > sbe$displaySizeBefore) {
            sbe$lastMessageTime = System.currentTimeMillis();
        }
    }
}