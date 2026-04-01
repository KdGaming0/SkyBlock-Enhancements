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
 * Adds a subtle slide-up animation to the chat input bar when the chat screen opens.
 *
 * <p>Inspired by ChatAnimation by Ezzenix (CC-BY-NC-SA 4.0). This is an original implementation.
 */
@Mixin(ChatScreen.class)
public class ChatScreenAnimationMixin {

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

        float duration = Math.min(SkyblockEnhancementsConfig.chatAnimationDurationMs * 2.5f, 500f);
        float elapsed = Math.min(System.currentTimeMillis() - sbe$openTime, duration);
        float t = 1f - elapsed / duration;
        float eased = 1f - t * t * t; // cubic ease-out
        float scale = (float) mc.getWindow().getGuiScale();
        return (1f - eased) * 8f * (scale / 2f);
    }

    @WrapOperation(
            method = "render",
            at =
            @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"))
    private void sbe$animateBackground(
            GuiGraphics graphics,
            int x1,
            int y1,
            int x2,
            int y2,
            int color,
            Operation<Void> original) {
        float dy = sbe$barDisplacement();
        if (dy != 0) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(0, dy);
        }
        original.call(graphics, x1, y1, x2, y2, color);
        if (dy != 0) {
            graphics.pose().popMatrix();
        }
    }

    @WrapOperation(
            method = "render",
            at =
            @At(
                    value = "INVOKE",
                    target =
                            "Lnet/minecraft/client/gui/screens/Screen;"
                                    + "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"))
    private void sbe$animateWidgets(
            ChatScreen instance,
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick,
            Operation<Void> original) {
        float dy = sbe$barDisplacement();
        if (dy != 0) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(0, dy);
        }
        original.call(instance, graphics, mouseX, mouseY, partialTick);
        if (dy != 0) {
            graphics.pose().popMatrix();
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void sbe$resetOnClose(CallbackInfo ci) {
        sbe$initialized = false;
    }
}