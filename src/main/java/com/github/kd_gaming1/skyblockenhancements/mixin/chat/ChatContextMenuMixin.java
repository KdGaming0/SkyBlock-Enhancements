package com.github.kd_gaming1.skyblockenhancements.mixin.chat;

import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.feature.chat.menu.ChatContextMenu;
import com.github.kd_gaming1.skyblockenhancements.feature.chat.menu.ChatMessageResolver;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds a right-click context menu to the chat screen. When the player right-clicks a chat message,
 * either a context menu with copy/delete options is shown, or the raw text is copied directly to
 * the clipboard (controlled by the {@code rightClickChatCopies} config option).
 */
@Mixin(ChatScreen.class)
public abstract class ChatContextMenuMixin extends Screen {

    @Unique private final ChatContextMenu sbe$contextMenu = new ChatContextMenu();

    protected ChatContextMenuMixin(Component title) {
        super(title);
    }

    // Mouse handling

    /**
     * Intercepts mouse clicks. Right-click (button 1) resolves the hovered message and either
     * copies directly or opens the context menu. Left-click (button 0) is forwarded to the open
     * menu first so its buttons can be pressed.
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void sbe$onMouseClicked(
            MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {

        if (!SkyblockEnhancementsConfig.enableChatContextMenu) return;

        int button = event.button();
        double x = event.x();
        double y = event.y();

        // Forward left-clicks to the open menu so its buttons work.
        if (button == 0 && sbe$contextMenu.isOpen()) {
            boolean consumed = sbe$contextMenu.mouseClicked(x, y, button);
            if (consumed) {
                cir.setReturnValue(true);
            }
            return;
        }

        // Right-click: resolve the message under the cursor.
        if (button == 1) {
            GuiMessage message = ChatMessageResolver.resolve(x, y);
            if (message == null) {
                sbe$contextMenu.close();
                return;
            }

            if (SkyblockEnhancementsConfig.rightClickChatCopies) {
                String text = ChatMessageResolver.toRawText(message.content());
                Minecraft.getInstance().keyboardHandler.setClipboard(text);
                sbe$contextMenu.notifyCopied((int) x, (int) y);
            } else {
                sbe$contextMenu.open(message, (int) x, (int) y, this.width, this.height);
            }
            cir.setReturnValue(true);
        }
    }

    // Keyboard handling

    /** Pressing Escape while the context menu is open dismisses the menu without closing chat. */
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void sbe$onKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        // GLFW_KEY_ESCAPE = 256
        if (event.key() == 256 && sbe$contextMenu.isOpen()) {
            sbe$contextMenu.close();
            cir.setReturnValue(true);
        }
    }

    // Rendering

    /** Draws the context menu overlay and copy toast after the rest of the chat screen. */
    @Inject(method = "render", at = @At("TAIL"))
    private void sbe$renderContextMenu(
            GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        sbe$contextMenu.render(graphics, mouseX, mouseY, partialTick);
    }

    // Lifecycle

    /** Dismiss the context menu when the chat screen is removed. */
    @Inject(method = "removed", at = @At("HEAD"))
    private void sbe$closeMenuOnRemoved(CallbackInfo ci) {
        sbe$contextMenu.close();
    }
}