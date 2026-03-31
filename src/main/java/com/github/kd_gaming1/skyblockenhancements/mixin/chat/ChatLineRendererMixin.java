package com.github.kd_gaming1.skyblockenhancements.mixin.chat;

import com.github.kd_gaming1.skyblockenhancements.access.SBEChatAccess;
import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.feature.chat.heads.ChatHeadResolver;
import com.github.kd_gaming1.skyblockenhancements.feature.chat.render.ChatRenderUtil;
import com.github.kd_gaming1.skyblockenhancements.feature.chat.render.CustomChatRenderer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Wraps the {@code handleMessage} call on the {@code ChatGraphicsAccess} interface
 * from inside the private {@code render(ChatGraphicsAccess, int, int, boolean)} method
 * of {@code ChatComponent}.
 *
 * <p>This avoids targeting any anonymous or package-private inner class — both of
 * which Mixin cannot reliably reach from outside the package.
 */
@Mixin(ChatComponent.class)
public class ChatLineRendererMixin {

    @WrapOperation(
            method = "render(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IIZ)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;" +
                            "handleMessage(IFLnet/minecraft/util/FormattedCharSequence;)Z"))
    private boolean sbe$renderCustomLine(
            ChatComponent.ChatGraphicsAccess access,
            int textY,
            float alpha,
            FormattedCharSequence text,
            Operation<Boolean> original) {

        SBEChatAccess chatAccess = ChatRenderUtil.activeChatAccess;
        if (chatAccess == null) {
            return original.call(access, textY, alpha, text);
        }

        Font font = chatAccess.sbe$getFont();
        GuiGraphics graphics = chatAccess.sbe$getGraphics();
        if (font == null || graphics == null) {
            return original.call(access, textY, alpha, text);
        }

        boolean hasHead = SkyblockEnhancementsConfig.enableChatHeads
                && chatAccess.sbe$getHeadPlayer(text) != null;

        if (hasHead) {
            sbe$drawHead(graphics, text, textY, alpha, chatAccess);
        }

        CustomChatRenderer renderer = chatAccess.sbe$getRenderer(text);
        if (renderer != null) {
            int lineX = hasHead ? ChatHeadResolver.HEAD_OFFSET : 0;
            int lineWidth = chatAccess.sbe$getScaledWidth() - lineX;
            renderer.render(graphics, font, text, lineX, textY, lineWidth, alpha);
            return false;
        }

        if (hasHead) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(ChatHeadResolver.HEAD_OFFSET, 0);
            boolean result = original.call(access, textY, alpha, text);
            graphics.pose().popMatrix();
            return result;
        }

        return original.call(access, textY, alpha, text);
    }

    @Unique
    private void sbe$drawHead(
            GuiGraphics graphics,
            FormattedCharSequence content,
            int textY,
            float alpha,
            SBEChatAccess chatAccess) {

        String playerName = chatAccess.sbe$getHeadPlayerName(content);
        if (playerName == null) return;

        PlayerSkin skin = ChatHeadResolver.resolveSkin(playerName);
        if (skin == null) return;

        int alphaInt = ARGB.as8BitChannel(alpha);
        if (alphaInt < 10) return;

        PlayerFaceRenderer.draw(graphics, skin, 0, textY, ChatHeadResolver.HEAD_SIZE);
    }
}