package com.github.kd_gaming1.skyblockenhancements.mixin.chat;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.feature.chat.tabs.ChatTab;
import com.github.kd_gaming1.skyblockenhancements.feature.chat.tabs.ChatTabState;
import com.github.kd_gaming1.skyblockenhancements.util.HypixelLocationState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds Hypixel channel tab buttons above the chat input field. */
@Mixin(ChatScreen.class)
public abstract class ChatTabScreenMixin extends Screen {

    @Shadow protected EditBox input;

    protected ChatTabScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void sbe$addTabButtons(CallbackInfo ci) {
        if (!SkyblockEnhancementsConfig.enableChatTabs) return;
        if (!HypixelLocationState.isOnHypixel()) return;

        Minecraft mc = Minecraft.getInstance();
        ChatComponent chat = mc.gui.getChat();
        int btnSize = 20;
        int spacing = 2;
        int startX = 5;

        int chatHeight = ChatComponent.getHeight(mc.options.chatHeightFocused().get());
        int y = this.height - chatHeight - 40 - btnSize - 5;

        for (ChatTab tab : ChatTab.values()) {
            int x = startX + tab.ordinal() * (btnSize + spacing);
            Button button =
                    Button.builder(
                                    Component.literal(tab.label()),
                                    btn -> {
                                        ChatTabState.setActiveTab(tab);
                                        chat.rescaleChat();
                                        mc.schedule(() -> setFocused(input));
                                    })
                            .bounds(x, y, btnSize, btnSize)
                            .build();

            addRenderableWidget(button);
        }
    }
}