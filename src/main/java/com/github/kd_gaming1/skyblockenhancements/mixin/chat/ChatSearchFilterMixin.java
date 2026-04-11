package com.github.kd_gaming1.skyblockenhancements.mixin.chat;

import com.github.kd_gaming1.skyblockenhancements.feature.chat.search.ChatSearchState;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Filters messages from the visible display queue when a search query is active.
 *
 * <p>Uses priority 1000 to ensure this runs after the tab filter (priority 900).
 * The deterministic chain is: tab filter → search filter → display. A message must pass both
 * filters to appear in the chat.
 */
@Mixin(value = ChatComponent.class)
public class ChatSearchFilterMixin {

    @WrapMethod(method = "addMessageToDisplayQueue")
    private void sbe$filterBySearch(GuiMessage message, Operation<Void> original) {
        if (ChatSearchState.matches(message)) {
            original.call(message);
        }
    }
}