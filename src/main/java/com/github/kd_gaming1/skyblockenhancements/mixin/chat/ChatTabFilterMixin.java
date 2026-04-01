package com.github.kd_gaming1.skyblockenhancements.mixin.chat;

import com.github.kd_gaming1.skyblockenhancements.feature.chat.tabs.ChatTabState;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;

/** Filters messages added to the visible display based on the active chat tab. */
@Mixin(ChatComponent.class)
public class ChatTabFilterMixin {

    @WrapMethod(method = "addMessageToDisplayQueue")
    private void sbe$filterByTab(GuiMessage message, Operation<Void> original) {
        if (ChatTabState.shouldShow(message.content())) {
            original.call(message);
        }
    }

    @WrapMethod(method = "addMessageToQueue")
    private void sbe$filterRawByTab(GuiMessage message, Operation<Void> original) {
        if (ChatTabState.shouldShow(message.content())) {
            original.call(message);
        }
    }
}