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
        int startX = 4; // Matching the typical chat input box starting X

        // The input box height is typically 12, bottom padded by 2.
        // Putting it right over the input bar.
        int y = this.height - 14 - btnSize;

        for (ChatTab tab : ChatTab.values()) {
            // Adjust width automatically for labels longer than 1-2 chars (like "Coop")
            int width = Math.max(btnSize, mc.font.width(tab.label()) + 8);

            Button button =
                    Button.builder(
                                    Component.literal(tab.label()),
                                    btn -> {
                                        ChatTabState.setActiveTab(tab);
                                        chat.rescaleChat();
                                        mc.schedule(() -> setFocused(input));

                                        // Execute mode change command
                                        if (tab.command() != null && !tab.command().isEmpty() && mc.player != null) {
                                            if (tab.command().startsWith("/")) {
                                                mc.player.connection.sendCommand(tab.command().substring(1));
                                            } else {
                                                mc.player.connection.sendChat(tab.command());
                                            }
                                        }
                                    })
                            .bounds(startX, y, width, btnSize)
                            .build();

            addRenderableWidget(button);
            startX += width + spacing;
        }
    }
}