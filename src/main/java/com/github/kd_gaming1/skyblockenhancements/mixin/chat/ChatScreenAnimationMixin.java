package com.github.kd_gaming1.skyblockenhancements.mixin.chat;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Slides the chat input bar up when the chat screen opens. Cubic ease-out over a duration
 * capped at {@link #MAX_DURATION_MS}.
 *
 * <p>Inspired by Ezzenix's ChatAnimation (CC-BY-NC-SA 4.0); this is an original implementation.
 */
@Mixin(ChatScreen.class)
public class ChatScreenAnimationMixin {

    @Unique
    private static final float MAX_DURATION_MS = 500f;
    @Unique
    private static final float DURATION_MULTIPLIER = 2.5f;
    @Unique
    private static final float BASE_DISPLACEMENT_PX = 8f;

    @Unique private boolean sbe$initialized;
    @Unique private long sbe$openTime;

    @Unique
    private float sbe$barDisplacement() {
        if (!SkyblockEnhancementsConfig.enableChatAnimation) return 0f;

        Minecraft mc = Minecraft.getInstance();
        if (!sbe$initialized && mc.player != null && !mc.player.isSleeping()) {
            sbe$initialized = true;
            sbe$openTime = System.currentTimeMillis();
        }

        float duration = Math.min(
                SkyblockEnhancementsConfig.chatAnimationDurationMs * DURATION_MULTIPLIER,
                MAX_DURATION_MS);
        float elapsed = Math.min(System.currentTimeMillis() - sbe$openTime, duration);
        float t = 1f - elapsed / duration;
        float eased = 1f - t * t * t; // cubic ease-out
        float scale = (float) mc.getWindow().getGuiScale();
        return (1f - eased) * BASE_DISPLACEMENT_PX * (scale / 2f);
    }

    @WrapOperation(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"))
    private void sbe$animateBackground(
            GuiGraphics graphics, int x1, int y1, int x2, int y2, int color,
            Operation<Void> original) {
        sbe$withDisplacement(graphics, () -> original.call(graphics, x1, y1, x2, y2, color));
    }

    @WrapOperation(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/Screen;"
                            + "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"))
    private void sbe$animateWidgets(
            ChatScreen instance, GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
            Operation<Void> original) {
        sbe$withDisplacement(graphics,
                () -> original.call(instance, graphics, mouseX, mouseY, partialTick));
    }

    @Unique
    private void sbe$withDisplacement(GuiGraphics graphics, Runnable body) {
        float dy = sbe$barDisplacement();
        if (dy != 0f) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(0f, dy);
        }
        body.run();
        if (dy != 0f) graphics.pose().popMatrix();
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void sbe$resetOnClose(CallbackInfo ci) {
        sbe$initialized = false;
    }
}