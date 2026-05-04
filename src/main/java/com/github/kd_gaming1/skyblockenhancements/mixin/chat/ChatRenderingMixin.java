package com.github.kd_gaming1.skyblockenhancements.mixin.chat;

import com.github.kd_gaming1.skyblockenhancements.access.SBEChatAccess;
import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.feature.chat.ChatLineTracker;
import com.github.kd_gaming1.skyblockenhancements.feature.chat.render.ChatGraphicsAccessProxy;
import com.github.kd_gaming1.skyblockenhancements.feature.chat.render.ChatLineProcessor;
import com.github.kd_gaming1.skyblockenhancements.feature.chat.render.CustomChatRenderer;
import com.github.kd_gaming1.skyblockenhancements.util.HypixelLocationState;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import java.util.List;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adapter between vanilla {@link ChatComponent} and the chat-rendering feature classes.
 *
 * <p>The mixin holds one piece of persistent state — the per-instance {@link ChatLineTracker}
 * — and delegates every non-trivial decision to the feature classes. When all affected config
 * options are disabled and the player is off Hypixel, the proxy wrap still runs but every
 * lookup short-circuits to {@code null}, preserving vanilla behaviour exactly.
 */
@Mixin(ChatComponent.class)
public abstract class ChatRenderingMixin implements SBEChatAccess {

    @Shadow @Final private List<GuiMessage.Line> trimmedMessages;
    @Shadow @Final private List<GuiMessage> allMessages;
    @Shadow private int chatScrollbarPos;
    @Shadow private boolean newMessageSinceScroll;

    @Shadow protected abstract int getWidth();
    @Shadow protected abstract double getScale();
    @Shadow protected abstract void refreshTrimmedMessages();

    @Unique private final ChatLineTracker sbe$lineTracker = new ChatLineTracker();

    // Proxy reuse cache (3.11) — avoids per-frame allocation when delegate/font/line-spacing are stable.
    @Unique private ChatGraphicsAccessProxy sbe$renderProxy;
    @Unique private ChatComponent.ChatGraphicsAccess sbe$lastDelegate;
    @Unique private Font sbe$lastProxyFont;
    @Unique private double sbe$lastLineSpacing = -1.0;

    // ---------- SBEChatAccess ----------

    @Override public List<GuiMessage> sbe$getAllMessages() { return allMessages; }
    @Override public List<GuiMessage.Line> sbe$getTrimmedMessages() { return trimmedMessages; }
    @Override public int sbe$getChatScrollbarPos() { return chatScrollbarPos; }
    @Override public int sbe$getScaledWidth() { return Mth.floor(getWidth() / getScale()); }
    @Override public ChatLineTracker sbe$getLineTracker() { return sbe$lineTracker; }

    /**
     * Rebuilds the display queue while preserving the player's scroll offset.
     * This prevents mod features that refresh the display (compact chat, message
     * deletion, tab/search filters) from force-snapping the chat back to the bottom.
     */
    @Unique
    private void sbe$clampScroll() {
        int linesPerPage = ((ChatComponent) (Object) this).getLinesPerPage();
        int maxScroll = Math.max(0, this.trimmedMessages.size() - linesPerPage);
        this.chatScrollbarPos = Math.min(this.chatScrollbarPos, maxScroll);
        if (this.chatScrollbarPos <= 0) {
            this.chatScrollbarPos = 0;
            this.newMessageSinceScroll = false;
        }
    }

    @Override
    public void sbe$refreshMessages() {
        int savedScroll = this.chatScrollbarPos;
        boolean savedNewMessage = this.newMessageSinceScroll;

        // Pin to bottom while the queue is temporarily empty/small so that
        // addMessageToDisplayQueue's internal scrollChat(1) calls become no-ops.
        this.chatScrollbarPos = 0;
        this.newMessageSinceScroll = false;

        refreshTrimmedMessages();

        this.chatScrollbarPos = savedScroll;
        this.newMessageSinceScroll = savedNewMessage;
        sbe$clampScroll();
    }

    // ---------- Render proxy ----------

    @WrapOperation(
            method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;IIIZZ)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/ChatComponent;"
                            + "render(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IIZ)V"))
    private void sbe$proxyGraphicsAccess(
            ChatComponent instance,
            ChatComponent.ChatGraphicsAccess access,
            int i, int j, boolean bl,
            Operation<Void> original,
            @Local(argsOnly = true) GuiGraphics graphics,
            @Local(argsOnly = true) Font font) {

        double lineSpacing = Minecraft.getInstance().options.chatLineSpacing().get();
        ChatGraphicsAccessProxy proxy = sbe$renderProxy;
        if (proxy == null || sbe$lastDelegate != access || sbe$lastProxyFont != font
                || sbe$lastLineSpacing != lineSpacing) {
            proxy = new ChatGraphicsAccessProxy(access, this, graphics, font);
            sbe$renderProxy = proxy;
            sbe$lastDelegate = access;
            sbe$lastProxyFont = font;
            sbe$lastLineSpacing = lineSpacing;
        } else {
            proxy.prepareForFrame(access, graphics, font);
        }
        original.call(instance, proxy, i, j, bl);
        proxy.finishOutline();
    }

    @WrapOperation(
            method = "captureClickableText",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/ChatComponent;"
                            + "render(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IIZ)V"))
    private void sbe$proxyClickableTextAccess(
            ChatComponent instance,
            ChatComponent.ChatGraphicsAccess access,
            int screenHeight, int ticks, boolean isChatting,
            Operation<Void> original) {

        ChatGraphicsAccessProxy proxy = new ChatGraphicsAccessProxy(
                access, this, null, Minecraft.getInstance().font);
        original.call(instance, proxy, screenHeight, ticks, isChatting);
    }

    // ---------- Line processing ----------

    @Inject(method = "addMessageToDisplayQueue", at = @At("HEAD"))
    private void sbe$beginLineBatch(GuiMessage message, CallbackInfo ci) {
        sbe$lineTracker.beginAddingLinesFor(message);
    }

    @Inject(method = "addMessageToDisplayQueue", at = @At("TAIL"))
    private void sbe$endLineBatch(GuiMessage message, CallbackInfo ci) {
        sbe$lineTracker.finishAddingLines();
    }

    @WrapOperation(
            method = "addMessageToDisplayQueue",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/GuiMessage;splitLines(Lnet/minecraft/client/gui/Font;I)Ljava/util/List;"))
    private List<FormattedCharSequence> sbe$processLines(
            GuiMessage instance, Font font, int width,
            Operation<List<FormattedCharSequence>> original,
            @Share("sbe_renderers") LocalRef<List<CustomChatRenderer>> renderersRef) {

        boolean centerEnabled = SkyblockEnhancementsConfig.centerHypixelText;
        boolean separatorsEnabled = SkyblockEnhancementsConfig.smoothSeparators;

        // True vanilla path when no rendering feature is active or we're off Hypixel.
        if (!HypixelLocationState.isOnHypixel() || (!centerEnabled && !separatorsEnabled)) {
            renderersRef.set(null);
            return original.call(instance, font, width);
        }

        // Step 1: bypass initial word-wrapping to inspect raw \n-separated lines.
        List<FormattedCharSequence> rawLines = original.call(instance, font, Integer.MAX_VALUE);

        // Step 2: classify and re-wrap, producing per-line renderers.
        ChatLineProcessor.Result result = ChatLineProcessor.process(
                rawLines, font, width, centerEnabled, separatorsEnabled);

        renderersRef.set(result.renderers());
        return result.lines();
    }

    /**
     * Fires immediately after each {@code trimmedMessages.addFirst(...)}. The {@code i} local
     * (ordinal=1) is the line index within the current message — this is the only local-capture
     * index used by this file, and it's stable across vanilla refactors because it names the
     * lambda variable used by the enclosing for-loop.
     */
    @Inject(
            method = "addMessageToDisplayQueue",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;addFirst(Ljava/lang/Object;)V",
                    shift = At.Shift.AFTER))
    private void sbe$registerAddedLine(
            CallbackInfo ci,
            @Share("sbe_renderers") LocalRef<List<CustomChatRenderer>> renderersRef,
            @Local(ordinal = 1) int lineIndex) {

        GuiMessage.Line added = trimmedMessages.getFirst();

        List<CustomChatRenderer> renderers = renderersRef.get();
        CustomChatRenderer renderer = (renderers != null && lineIndex < renderers.size())
                ? renderers.get(lineIndex)
                : null;

        sbe$lineTracker.recordLine(added, renderer);
    }

    @WrapOperation(
            method = "addMessageToDisplayQueue",
            at = @At(value = "INVOKE", target = "Ljava/util/List;removeLast()Ljava/lang/Object;"))
    private <E> E sbe$evictLine(List<E> instance, Operation<E> original) {
        GuiMessage.Line evicted = (GuiMessage.Line) instance.getLast();
        sbe$lineTracker.evictLine(evicted);
        return original.call(instance);
    }

    @Inject(method = "clearMessages", at = @At("HEAD"))
    private void sbe$clearAll(boolean clearHistory, CallbackInfo ci) {
        sbe$lineTracker.clearAll();
    }

    @Inject(method = "clearMessages", at = @At("TAIL"))
    private void sbe$clampScrollAfterClear(boolean clearHistory, CallbackInfo ci) {
        this.chatScrollbarPos = 0;
        this.newMessageSinceScroll = false;
    }

    @Inject(method = "refreshTrimmedMessages", at = @At("HEAD"))
    private void sbe$clearOnRefresh(CallbackInfo ci) {
        // Preserve selection across refresh: the selected GuiMessage still exists in
        // allMessages, so re-deriving its lines is enough to restore the outline.
        sbe$lineTracker.clearLineMappings();
    }

    @Inject(method = "refreshTrimmedMessages", at = @At("TAIL"))
    private void sbe$clampScrollAfterRefresh(CallbackInfo ci) {
        sbe$clampScroll();
    }

    @Inject(
            method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;IIIZZ)V",
            at = @At("HEAD"))
    private void sbe$clampScrollBeforeRender(CallbackInfo ci) {
        sbe$clampScroll();
    }
}