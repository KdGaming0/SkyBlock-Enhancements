package com.github.kd_gaming1.skyblockenhancements.mixin.chat;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Replaces the hardcoded chat history limit of 100 with a configurable value.
 *
 * <p>Targets both {@code addMessageToDisplayQueue} (trimmed lines limit) and the private {@code
 * addMessageToQueue} (raw message history limit).
 */
@Mixin(ChatComponent.class)
public class ChatHistoryMixin {

    @ModifyExpressionValue(
            method = {"addMessageToDisplayQueue", "addMessageToQueue"},
            at = @At(value = "CONSTANT", args = "intValue=100"))
    private int sbe$expandHistory(int original) {
        return SkyblockEnhancementsConfig.extendedChatHistory
                ? SkyblockEnhancementsConfig.chatHistorySize
                : original;
    }
}