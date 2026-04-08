package com.github.kd_gaming1.skyblockenhancements.mixin.chat;

import com.daqem.uilib.gui.widget.CustomButtonWidget;
import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.feature.chat.search.ChatSearchLayout;
import com.github.kd_gaming1.skyblockenhancements.feature.chat.search.ChatSearchState;
import com.github.kd_gaming1.skyblockenhancements.feature.chat.tabs.ChatTab;
import com.github.kd_gaming1.skyblockenhancements.feature.chat.tabs.ChatTabSprites;
import com.github.kd_gaming1.skyblockenhancements.feature.chat.tabs.ChatTabState;
import com.github.kd_gaming1.skyblockenhancements.util.HypixelLocationState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds Hypixel channel tab buttons above the chat input field.
 */
@Mixin(ChatScreen.class)
public abstract class ChatTabScreenMixin extends Screen {

    @Shadow protected EditBox input;

    protected ChatTabScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void sbe$addTabButtons(CallbackInfo ci) {
        if (!SkyblockEnhancementsConfig.enableChatTabs || !HypixelLocationState.isOnHypixel()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        ChatComponent chat = mc.gui.getChat();
        int tabHeight = 15;
        int spacing = 1;
        int x = 2;
        int tabY = this.height - 14 - tabHeight - ChatSearchLayout.extraOffset();

        for (ChatTab tab : ChatTab.values()) {
            int w = Math.max(tabHeight, mc.font.width(tab.label()) + 10);
            boolean active = ChatTabState.getActiveTab() == tab;
            WidgetSprites sprites = active ? ChatTabSprites.ACTIVE : ChatTabSprites.INACTIVE;

            CustomButtonWidget button =
                    new CustomButtonWidget(
                            x,
                            tabY,
                            w,
                            tabHeight,
                            Component.literal(tab.label()),
                            sprites,
                            btn -> {
                                boolean wasAlreadyActive = ChatTabState.getActiveTab() == tab;
                                ChatTabState.setActiveTab(tab);
                                chat.rescaleChat();
                                rebuildWidgets();

                                if (!ChatSearchState.isActive()
                                        || SkyblockEnhancementsConfig.alwaysShowChatSearch) {
                                    mc.schedule(() -> setFocused(input));
                                }

                                if (wasAlreadyActive) return;

                                String cmd = tab.command();
                                if (cmd != null && !cmd.isEmpty() && mc.player != null) {
                                    if (cmd.startsWith("/")) {
                                        mc.player.connection.sendCommand(cmd.substring(1));
                                    } else {
                                        mc.player.connection.sendChat(cmd);
                                    }
                                }
                            });

            button.setTabOrderGroup(Integer.MAX_VALUE);

            addRenderableWidget(button);
            x += w + spacing;
        }
    }

    @Inject(method = "keyPressed", at = @At("RETURN"))
    private void sbe$restoreInputFocusAfterHistoryNav(KeyEvent keyEvent, CallbackInfoReturnable<Boolean> cir) {
        int key = keyEvent.key();
        if ((key == 264 || key == 265) && getFocused() != input) {
            setFocused(input);
        }
    }
}