package com.github.kd_gaming1.skyblockenhancements.mixin.chat;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.feature.chat.ChatFeatureState;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Hides messages that don't match the active search query. Priority 1000 so it runs after
 * {@link ChatTabFilterMixin} (priority 900) — tab first, search second, display.
 */
@Mixin(value = ChatComponent.class)
public class ChatSearchFilterMixin {

    @WrapMethod(method = "addMessageToDisplayQueue")
    private void sbe$filterBySearch(GuiMessage message, Operation<Void> original) {
        if (!SkyblockEnhancementsConfig.enableChatSearch
                || ChatFeatureState.get().search().matches(message)) {
            original.call(message);
        }
    }
}