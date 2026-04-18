package com.github.kd_gaming1.skyblockenhancements.mixin.chat;

import com.daqem.uilib.gui.widget.CustomButtonWidget;
import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.feature.chat.ChatFeatureState;
import com.github.kd_gaming1.skyblockenhancements.feature.chat.search.ChatSearchTheme;
import com.github.kd_gaming1.skyblockenhancements.feature.chat.tabs.ChatTab;
import com.github.kd_gaming1.skyblockenhancements.feature.chat.tabs.ChatTabController;
import com.github.kd_gaming1.skyblockenhancements.feature.chat.tabs.ChatTabSprites;
import com.github.kd_gaming1.skyblockenhancements.util.HypixelLocationState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds Hypixel channel tab buttons above the chat input field. */
@Mixin(ChatScreen.class)
public abstract class ChatTabScreenMixin extends Screen {

    @Unique
    private static final int TAB_HEIGHT = 15;
    @Unique
    private static final int TAB_SPACING = 1;
    @Unique
    private static final int TAB_LABEL_PADDING = 10;
    @Unique
    private static final int TAB_BOTTOM_MARGIN = 14;

    @Shadow protected EditBox input;

    protected ChatTabScreenMixin(Component title) { super(title); }

    @Inject(method = "init", at = @At("TAIL"))
    private void sbe$addTabButtons(CallbackInfo ci) {
        if (!SkyblockEnhancementsConfig.enableChatTabs || !HypixelLocationState.isOnHypixel()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        ChatComponent chat = mc.gui.getChat();
        ChatTabController tabs = ChatFeatureState.get().tabs();

        int tabY = height - TAB_BOTTOM_MARGIN - TAB_HEIGHT - ChatSearchTheme.extraOffset();
        int x = 2;

        for (ChatTab tab : ChatTab.values()) {
            int width = Math.max(TAB_HEIGHT, mc.font.width(tab.label()) + TAB_LABEL_PADDING);
            addRenderableWidget(sbe$buildTabButton(tab, tabs, chat, mc, x, tabY, width));
            x += width + TAB_SPACING;
        }
    }

    @Inject(method = "keyPressed", at = @At("RETURN"))
    private void sbe$restoreInputFocusAfterHistoryNav(
            KeyEvent keyEvent, CallbackInfoReturnable<Boolean> cir) {
        int key = keyEvent.key();
        if ((key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) && getFocused() != input) {
            setFocused(input);
        }
    }

    // ---------------------------------------------------------------------
    // Button factory
    // ---------------------------------------------------------------------

    @Unique
    private CustomButtonWidget sbe$buildTabButton(
            ChatTab tab, ChatTabController tabs, ChatComponent chat, Minecraft mc,
            int x, int y, int width) {

        boolean active = tabs.getActiveTab() == tab;
        WidgetSprites sprites = active ? ChatTabSprites.ACTIVE : ChatTabSprites.INACTIVE;

        CustomButtonWidget button = new CustomButtonWidget(
                x, y, width, TAB_HEIGHT,
                Component.literal(tab.label()),
                sprites,
                ignored -> sbe$onTabClicked(tab, tabs, chat, mc));

        button.setTabOrderGroup(Integer.MAX_VALUE);
        return button;
    }

    @Unique
    private void sbe$onTabClicked(
            ChatTab tab, ChatTabController tabs, ChatComponent chat, Minecraft mc) {
        boolean wasAlreadyActive = tabs.getActiveTab() == tab;
        tabs.setActiveTab(tab);
        chat.rescaleChat();
        rebuildWidgets();

        if (!ChatFeatureState.get().search().isActive()
                || SkyblockEnhancementsConfig.alwaysShowChatSearch) {
            mc.schedule(() -> setFocused(input));
        }

        if (wasAlreadyActive) return;

        String cmd = tab.command();
        if (!cmd.isEmpty() && mc.player != null) {
            if (cmd.startsWith("/")) {
                mc.player.connection.sendCommand(cmd.substring(1));
            } else {
                mc.player.connection.sendChat(cmd);
            }
        }
    }
}