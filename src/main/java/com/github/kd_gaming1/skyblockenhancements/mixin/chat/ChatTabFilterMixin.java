package com.github.kd_gaming1.skyblockenhancements.mixin.chat;

import com.github.kd_gaming1.skyblockenhancements.access.SBEChatAccess;
import com.github.kd_gaming1.skyblockenhancements.feature.chat.tabs.ChatTabState;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Filters messages added to the visible display queue based on the active chat tab.
 *
 * <p>Raw history ({@code addMessageToQueue}) is intentionally not filtered so that switching tabs
 * can re-derive the correct view from the complete history via {@code refreshTrimmedMessages}.
 */
@Mixin(ChatComponent.class)
public class ChatTabFilterMixin {

    @WrapMethod(method = "addMessageToDisplayQueue")
    private void sbe$filterByTab(GuiMessage message, Operation<Void> original) {
        SBEChatAccess access = (SBEChatAccess) this;
        List<GuiMessage> history = access.sbe$getAllMessages();
        int index = history.indexOf(message);
        if (ChatTabState.shouldShow(message.content(), history, index)) {
            original.call(message);
        }
    }
}