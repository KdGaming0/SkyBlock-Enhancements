package com.github.kd_gaming1.skyblockenhancements.mixin.chat;

import com.github.kd_gaming1.skyblockenhancements.access.SBEChatAccess;
import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.feature.chat.ChatFeatureState;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Hides messages that don't match the active chat tab.
 *
 * <p>Priority 900 runs this before {@link ChatSearchFilterMixin} (priority 1000), giving the
 * deterministic filter chain: tab → search → display. A message must pass both filters.
 *
 * <p>When chat tabs are disabled in config, the filter is completely bypassed — no lookup,
 * no allocations, no alteration of vanilla behaviour.
 */
@Mixin(value = ChatComponent.class, priority = 900)
public class ChatTabFilterMixin {

    @WrapMethod(method = "addMessageToDisplayQueue")
    private void sbe$filterByTab(GuiMessage message, Operation<Void> original) {
        if (!SkyblockEnhancementsConfig.enableChatTabs) {
            original.call(message);
            return;
        }

        SBEChatAccess access = (SBEChatAccess) this;
        List<GuiMessage> history = access.sbe$getAllMessages();
        int index = identityIndexOf(history, message);

        if (ChatFeatureState.get().tabs().shouldShow(message.content(), history, index)) {
            original.call(message);
        }
    }

    /**
     * Reference-equality search. Needed because {@code addMessageToDisplayQueue} runs before
     * {@code addMessageToQueue}, so the message frequently isn't in {@code history} yet —
     * {@code List.indexOf} relies on {@link Object#equals} and could match an unrelated record
     * with identical contents.
     */
    @Unique
    private static int identityIndexOf(List<GuiMessage> list, GuiMessage target) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == target) return i;
        }
        return -1;
    }
}