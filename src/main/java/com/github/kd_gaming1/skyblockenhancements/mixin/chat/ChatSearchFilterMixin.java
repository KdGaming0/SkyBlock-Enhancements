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
 * <p>This sits alongside {@link ChatTabFilterMixin} — both wrap {@code addMessageToDisplayQueue}.
 * Mixin applies them in declaration order within the same priority, so both filters compose
 * naturally: a message must pass the tab filter <em>and</em> the search filter to appear.
 */
@Mixin(ChatComponent.class)
public class ChatSearchFilterMixin {

    @WrapMethod(method = "addMessageToDisplayQueue")
    private void sbe$filterBySearch(GuiMessage message, Operation<Void> original) {
        if (ChatSearchState.matches(message)) {
            original.call(message);
        }
    }
}