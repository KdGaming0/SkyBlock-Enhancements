package com.github.kd_gaming1.skyblockenhancements.mixin.chat;

import com.github.kd_gaming1.skyblockenhancements.access.SBEChatAccess;
import com.github.kd_gaming1.skyblockenhancements.feature.chat.tabs.ChatTabState;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Filters messages added to the visible display queue based on the active chat tab.
 *
 * <p>Raw history ({@code addMessageToQueue}) is intentionally not filtered so that switching tabs
 * can re-derive the correct view from the complete history via {@code refreshTrimmedMessages}.
 *
 * <p>Uses explicit priority 900 to ensure this runs before the search filter (priority 1000),
 * establishing a deterministic filter chain: tab filter → search filter → display.
 *
 * <p><b>Bug fix:</b> The old {@code history.indexOf(message)} relied on record equality, but
 * during real-time message arrival, {@code addMessageToDisplayQueue} is called <em>before</em>
 * {@code addMessageToQueue}, so the message doesn't exist in the history list yet — causing
 * {@code indexOf} to return {@code -1}. This fix scans by identity (reference equality) and
 * correctly handles the "not yet in history" case.
 */
@Mixin(value = ChatComponent.class, priority = 900)
public class ChatTabFilterMixin {

    @WrapMethod(method = "addMessageToDisplayQueue")
    private void sbe$filterByTab(GuiMessage message, Operation<Void> original) {
        SBEChatAccess access = (SBEChatAccess) this;
        List<GuiMessage> history = access.sbe$getAllMessages();

        int index = identityIndexOf(history, message);

        if (ChatTabState.shouldShow(message.content(), history, index)) {
            original.call(message);
        }
    }

    /**
     * Finds the index of {@code target} in {@code list} using reference equality ({@code ==})
     * rather than {@link Object#equals}. Returns {@code -1} if not found.
     */
    @Unique
    private static int identityIndexOf(List<GuiMessage> list, GuiMessage target) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == target) {
                return i;
            }
        }
        return -1;
    }
}