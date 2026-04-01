package com.github.kd_gaming1.skyblockenhancements.mixin.chat;

import com.github.kd_gaming1.skyblockenhancements.access.SBEChatAccess;
import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.feature.chat.render.CenteredTextRenderer;
import com.github.kd_gaming1.skyblockenhancements.feature.chat.render.ChatGraphicsAccessProxy;
import com.github.kd_gaming1.skyblockenhancements.feature.chat.render.ChatRenderUtil;
import com.github.kd_gaming1.skyblockenhancements.feature.chat.render.ChatTextHelper;
import com.github.kd_gaming1.skyblockenhancements.feature.chat.render.CustomChatRenderer;
import com.github.kd_gaming1.skyblockenhancements.feature.chat.render.SeparatorRenderer;
import com.github.kd_gaming1.skyblockenhancements.util.HypixelLocationState;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public abstract class ChatRenderingMixin implements SBEChatAccess {

    @Shadow @Final private List<GuiMessage.Line> trimmedMessages;
    @Shadow @Final private List<GuiMessage> allMessages;

    @Shadow protected abstract int getWidth();
    @Shadow protected abstract double getScale();
    @Shadow protected abstract void refreshTrimmedMessages();

    @Unique
    private final Map<FormattedCharSequence, CustomChatRenderer> sbe$renderers =
            new Reference2ObjectOpenHashMap<>();

    @Unique private GuiGraphics sbe$graphics;
    @Unique private Font sbe$font;

    @Override public List<GuiMessage> sbe$getAllMessages() { return allMessages; }
    @Override public void sbe$refreshMessages() { refreshTrimmedMessages(); }
    @Override public int sbe$getScaledWidth() { return Mth.floor(getWidth() / getScale()); }

    @Override
    public @Nullable CustomChatRenderer sbe$getRenderer(FormattedCharSequence content) {
        return sbe$renderers.get(content);
    }

    @Override public @Nullable GuiGraphics sbe$getGraphics() { return sbe$graphics; }
    @Override public @Nullable Font sbe$getFont() { return sbe$font; }

    @ModifyVariable(
            method = "render(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IIZ)V",
            at = @At("HEAD"),
            argsOnly = true)
    private ChatComponent.ChatGraphicsAccess sbe$proxyGraphicsAccess(ChatComponent.ChatGraphicsAccess original) {
        return new ChatGraphicsAccessProxy(original, this);
    }

    @Inject(
            method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;IIIZZ)V",
            at = @At("HEAD"))
    private void sbe$captureRenderContext(
            GuiGraphics graphics, Font font, int tickCount,
            int mouseX, int mouseY, boolean focused, boolean changeCursor,
            CallbackInfo ci) {
        sbe$graphics = graphics;
        sbe$font = font;
        ChatRenderUtil.activeChatAccess = this;
    }

    @Inject(
            method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;IIIZZ)V",
            at = @At("TAIL"))
    private void sbe$clearRenderContext(CallbackInfo ci) {
        sbe$graphics = null;
        sbe$font = null;
        ChatRenderUtil.activeChatAccess = null;
    }

    @WrapOperation(
            method = "addMessageToDisplayQueue",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/GuiMessage;splitLines(Lnet/minecraft/client/gui/Font;I)Ljava/util/List;"))
    private List<FormattedCharSequence> sbe$processLines(
            GuiMessage instance, Font font, int width,
            Operation<List<FormattedCharSequence>> original,
            @Share("sbe_renderers") LocalRef<List<CustomChatRenderer>> renderersRef,
            @Local(argsOnly = true) GuiMessage message) {

        boolean onHypixel = HypixelLocationState.isOnHypixel();

        List<FormattedCharSequence> lines = original.call(instance, font, width);

        if (!onHypixel) {
            renderersRef.set(null);
            return lines;
        }

        List<CustomChatRenderer> renderers = new ArrayList<>(lines.size());
        boolean enableCenter = SkyblockEnhancementsConfig.centerHypixelText;
        boolean enableSeparators = SkyblockEnhancementsConfig.smoothSeparators;

        String fullString = message.content().getString();
        String trimmedString = fullString.trim();

        for (int idx = 0; idx < lines.size(); idx++) {
            CustomChatRenderer renderer = null;

            if (enableSeparators && ChatTextHelper.isFullSeparator(trimmedString)) {
                int color = ChatTextHelper.extractColor(message.content());
                renderer = new SeparatorRenderer(color, null);
            }
            else if (enableSeparators && ChatTextHelper.isCenteredSeparator(trimmedString)) {
                int color = ChatTextHelper.extractColor(message.content());
                String middle = ChatTextHelper.extractMiddleText(fullString);
                renderer = new SeparatorRenderer(color, middle);
            }
            else if (enableCenter && ChatTextHelper.isCenteredText(font, fullString, trimmedString, sbe$getScaledWidth())) {
                renderer = CenteredTextRenderer.INSTANCE;
            }

            renderers.add(renderer);
        }

        renderersRef.set(renderers);
        return lines;
    }

    @Inject(
            method = "addMessageToDisplayQueue",
            at = @At(value = "INVOKE",
                    target = "Ljava/util/List;addFirst(Ljava/lang/Object;)V",
                    shift = At.Shift.AFTER))
    private void sbe$storeRenderer(
            CallbackInfo ci,
            @Share("sbe_renderers") LocalRef<List<CustomChatRenderer>> renderersRef,
            @Local(ordinal = 1) int i,
            @Local(argsOnly = true) GuiMessage message) {

        FormattedCharSequence key = trimmedMessages.getFirst().content();

        List<CustomChatRenderer> renderers = renderersRef.get();
        if (renderers != null && i < renderers.size()) {
            CustomChatRenderer renderer = renderers.get(i);
            if (renderer != null) {
                sbe$renderers.put(key, renderer);
            }
        }
    }

    @WrapOperation(
            method = "addMessageToDisplayQueue",
            at = @At(value = "INVOKE",
                    target = "Ljava/util/List;removeLast()Ljava/lang/Object;"))
    private <E> E sbe$cleanupEvicted(List<E> instance, Operation<E> original) {
        FormattedCharSequence key = ((GuiMessage.Line) instance.getLast()).content();
        sbe$renderers.remove(key);
        return original.call(instance);
    }

    @Inject(method = "clearMessages", at = @At("HEAD"))
    private void sbe$clearAll(boolean clearHistory, CallbackInfo ci) {
        sbe$renderers.clear();
    }

    @Inject(method = "refreshTrimmedMessages", at = @At("HEAD"))
    private void sbe$clearOnRefresh(CallbackInfo ci) {
        sbe$renderers.clear();
    }
}