package com.github.kd_gaming1.skyblockenhancements.mixin.chat;

import com.github.kd_gaming1.skyblockenhancements.access.SBEChatAccess;
import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.feature.chat.render.CenteredTextRenderer;
import com.github.kd_gaming1.skyblockenhancements.feature.chat.render.ChatGraphicsAccessProxy;
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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public abstract class ChatRenderingMixin implements SBEChatAccess {

    @Shadow @Final private List<GuiMessage.Line> trimmedMessages;
    @Shadow @Final private List<GuiMessage> allMessages;
    @Shadow private int chatScrollbarPos;

    @Shadow
    protected abstract int getWidth();

    @Shadow
    protected abstract double getScale();

    @Shadow
    protected abstract void refreshTrimmedMessages();

    @Unique
    private final Map<FormattedCharSequence, CustomChatRenderer> sbe$renderers =
            new Reference2ObjectOpenHashMap<>();

    /**
     * Maps each display line back to the {@link GuiMessage} that produced it.
     * Uses an {@link IdentityHashMap} because {@link GuiMessage.Line} is a record and we want
     * to track the exact instance that was added to {@code trimmedMessages}, not any structurally
     * equal line from a different message.
     */
    @Unique
    private final Map<GuiMessage.Line, GuiMessage> sbe$lineToMessage = new IdentityHashMap<>();

    /** The message currently highlighted by the context menu, or {@code null}. */
    @Unique
    private @Nullable GuiMessage sbe$selectedMessage;

    /**
     * Tracks the {@link GuiMessage} currently being processed by {@code addMessageToDisplayQueue}
     * so that the {@code addFirst} injection can associate newly created lines with their parent.
     */
    @Unique
    private @Nullable GuiMessage sbe$currentParent;

    // --- SBEChatAccess implementation ---

    @Override
    public List<GuiMessage> sbe$getAllMessages() {
        return allMessages;
    }

    @Override
    public List<GuiMessage.Line> sbe$getTrimmedMessages() {
        return trimmedMessages;
    }

    @Override
    public int sbe$getChatScrollbarPos() {
        return chatScrollbarPos;
    }

    @Override
    public void sbe$refreshMessages() {
        refreshTrimmedMessages();
    }

    @Override
    public int sbe$getScaledWidth() {
        return Mth.floor(getWidth() / getScale());
    }

    @Override
    public @Nullable CustomChatRenderer sbe$getRenderer(FormattedCharSequence content) {
        return sbe$renderers.get(content);
    }

    @Override
    public @Nullable GuiMessage sbe$getParentMessage(GuiMessage.Line line) {
        return sbe$lineToMessage.get(line);
    }

    @Override
    public void sbe$setSelectedMessage(@Nullable GuiMessage message) {
        sbe$selectedMessage = message;
    }

    @Override
    public @Nullable GuiMessage sbe$getSelectedMessage() {
        return sbe$selectedMessage;
    }

    // --- Render proxy injection ---

    @WrapOperation(
            method =
                    "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;IIIZZ)V",
            at =
            @At(
                    value = "INVOKE",
                    target =
                            "Lnet/minecraft/client/gui/components/ChatComponent;"
                                    + "render(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IIZ)V"))
    private void sbe$proxyGraphicsAccess(
            ChatComponent instance,
            ChatComponent.ChatGraphicsAccess access,
            int i,
            int j,
            boolean bl,
            Operation<Void> original,
            @Local(argsOnly = true) GuiGraphics graphics,
            @Local(argsOnly = true) Font font) {
        ChatGraphicsAccessProxy proxy = new ChatGraphicsAccessProxy(access, this, graphics, font);
        original.call(instance, proxy, i, j, bl);
        proxy.finishOutline();
    }

    @WrapOperation(
            method = "captureClickableText",
            at =
            @At(
                    value = "INVOKE",
                    target =
                            "Lnet/minecraft/client/gui/components/ChatComponent;"
                                    + "render(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IIZ)V"))
    private void sbe$proxyClickableTextAccess(
            ChatComponent instance,
            ChatComponent.ChatGraphicsAccess access,
            int screenHeight,
            int ticks,
            boolean isChatting,
            Operation<Void> original) {
        original.call(
                instance,
                new ChatGraphicsAccessProxy(access, this, null, Minecraft.getInstance().font),
                screenHeight,
                ticks,
                isChatting);
    }

    // --- Line processing ---

    /**
     * Captures the parent {@link GuiMessage} at the start of {@code addMessageToDisplayQueue}
     * so that all lines created during this invocation can be associated with it.
     */
    @Inject(method = "addMessageToDisplayQueue", at = @At("HEAD"))
    private void sbe$captureParent(GuiMessage message, CallbackInfo ci) {
        sbe$currentParent = message;
    }

    @Inject(method = "addMessageToDisplayQueue", at = @At("TAIL"))
    private void sbe$clearParent(GuiMessage message, CallbackInfo ci) {
        sbe$currentParent = null;
    }

    @WrapOperation(
            method = "addMessageToDisplayQueue",
            at =
            @At(
                    value = "INVOKE",
                    target =
                            "Lnet/minecraft/client/GuiMessage;splitLines(Lnet/minecraft/client/gui/Font;I)Ljava/util/List;"))
    private List<FormattedCharSequence> sbe$processLines(
            GuiMessage instance,
            Font font,
            int width,
            Operation<List<FormattedCharSequence>> original,
            @Share("sbe_renderers") LocalRef<List<CustomChatRenderer>> renderersRef,
            @Local(argsOnly = true) GuiMessage message) {

        if (!HypixelLocationState.isOnHypixel()) {
            renderersRef.set(null);
            return original.call(instance, font, width);
        }

        // Bypass initial word wrapping to get \n-separated raw lines.
        List<FormattedCharSequence> rawLines = original.call(instance, font, Integer.MAX_VALUE);
        List<FormattedCharSequence> finalLines = new ArrayList<>();
        List<CustomChatRenderer> renderers = new ArrayList<>();

        boolean enableCenter = SkyblockEnhancementsConfig.centerHypixelText;
        boolean enableSeparators = SkyblockEnhancementsConfig.smoothSeparators;

        for (FormattedCharSequence rawSeq : rawLines) {
            String rawStr = ChatTextHelper.getString(rawSeq);
            String trimmedStr = rawStr.trim();

            if (enableSeparators && ChatTextHelper.isFullSeparator(trimmedStr)) {
                finalLines.add(ChatTextHelper.trim(rawSeq));
                renderers.add(
                        new SeparatorRenderer(ChatTextHelper.extractColor(rawSeq), null));

            } else if (enableSeparators && ChatTextHelper.isCenteredSeparator(trimmedStr)) {
                finalLines.add(ChatTextHelper.trim(rawSeq));
                renderers.add(
                        new SeparatorRenderer(
                                ChatTextHelper.extractColor(rawSeq),
                                ChatTextHelper.extractMiddleText(trimmedStr)));

            } else if (enableCenter && ChatTextHelper.isCenteredText(font, rawStr, trimmedStr)) {
                Component trimmedComp = ChatTextHelper.toComponent(ChatTextHelper.trim(rawSeq));
                for (FormattedCharSequence w : Minecraft.getInstance().font.split(trimmedComp, width)) {
                    finalLines.add(w);
                    renderers.add(CenteredTextRenderer.INSTANCE);
                }

            } else {
                Component normalComp = ChatTextHelper.toComponent(rawSeq);
                for (FormattedCharSequence w : Minecraft.getInstance().font.split(normalComp, width)) {
                    finalLines.add(w);
                    renderers.add(null);
                }
            }
        }

        renderersRef.set(renderers);
        return finalLines;
    }

    @Inject(
            method = "addMessageToDisplayQueue",
            at =
            @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;addFirst(Ljava/lang/Object;)V",
                    shift = At.Shift.AFTER))
    private void sbe$storeRenderer(
            CallbackInfo ci,
            @Share("sbe_renderers") LocalRef<List<CustomChatRenderer>> renderersRef,
            @Local(ordinal = 1) int i,
            @Local(argsOnly = true) GuiMessage message) {

        // Map the newly added line back to its parent message.
        GuiMessage.Line addedLine = trimmedMessages.getFirst();
        if (sbe$currentParent != null) {
            sbe$lineToMessage.put(addedLine, sbe$currentParent);
        }

        List<CustomChatRenderer> renderers = renderersRef.get();
        if (renderers == null || i >= renderers.size()) return;

        CustomChatRenderer renderer = renderers.get(i);
        if (renderer != null) {
            sbe$renderers.put(addedLine.content(), renderer);
        }
    }

    @WrapOperation(
            method = "addMessageToDisplayQueue",
            at =
            @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;removeLast()Ljava/lang/Object;"))
    private <E> E sbe$cleanupEvicted(List<E> instance, Operation<E> original) {
        GuiMessage.Line evicted = (GuiMessage.Line) instance.getLast();
        sbe$renderers.remove(evicted.content());
        sbe$lineToMessage.remove(evicted);
        return original.call(instance);
    }

    @Inject(method = "clearMessages", at = @At("HEAD"))
    private void sbe$clearAll(boolean clearHistory, CallbackInfo ci) {
        sbe$renderers.clear();
        sbe$lineToMessage.clear();
        sbe$selectedMessage = null;
    }

    @Inject(method = "refreshTrimmedMessages", at = @At("HEAD"))
    private void sbe$clearOnRefresh(CallbackInfo ci) {
        sbe$renderers.clear();
        sbe$lineToMessage.clear();
        // Intentionally preserve sbe$selectedMessage across refresh — the context menu may
        // still be open and the message still exists in allMessages.
    }
}