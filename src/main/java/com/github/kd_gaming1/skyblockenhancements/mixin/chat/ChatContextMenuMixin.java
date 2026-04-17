package com.github.kd_gaming1.skyblockenhancements.mixin.chat;

import com.github.kd_gaming1.skyblockenhancements.access.SBEChatAccess;
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
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Wires a right-click chat context menu into {@link ChatScreen}. The actual menu lives in
 * {@link ChatContextMenu}; this mixin only forwards events.
 */
@Mixin(ChatScreen.class)
public abstract class ChatContextMenuMixin extends Screen {

    @Unique
    private static final int MOUSE_BUTTON_LEFT = 0;
    @Unique
    private static final int MOUSE_BUTTON_RIGHT = 1;

    @Unique private final ChatContextMenu sbe$contextMenu = new ChatContextMenu();

    protected ChatContextMenuMixin(Component title) { super(title); }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void sbe$onMouseClicked(
            MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {

        if (!SkyblockEnhancementsConfig.enableChatContextMenu) return;

        int button = event.button();
        double x = event.x();
        double y = event.y();

        // Left-click inside an open menu: route to the menu's button dispatcher.
        if (button == MOUSE_BUTTON_LEFT && sbe$contextMenu.isOpen()) {
            if (sbe$contextMenu.mouseClicked(x, y, button)) {
                cir.setReturnValue(true);
            }
            return;
        }

        if (button != MOUSE_BUTTON_RIGHT) return;

        GuiMessage message = ChatMessageResolver.resolve(y);
        if (message == null) {
            sbe$contextMenu.close();
            return;
        }

        if (SkyblockEnhancementsConfig.rightClickChatCopies) {
            sbe$directCopy(message, (int) x, (int) y);
        } else {
            sbe$contextMenu.open(message, (int) x, (int) y, this.width, this.height);
        }
        cir.setReturnValue(true);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void sbe$onKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && sbe$contextMenu.isOpen()) {
            sbe$contextMenu.close();
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void sbe$renderContextMenu(
            GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        sbe$contextMenu.render(graphics, mouseX, mouseY, partialTick);
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void sbe$closeMenuOnRemoved(CallbackInfo ci) {
        sbe$contextMenu.close();
    }

    /** Direct copy path: flash the outline briefly, copy raw text, show the toast. */
    @Unique
    private void sbe$directCopy(GuiMessage message, int x, int y) {
        Minecraft mc = Minecraft.getInstance();
        SBEChatAccess access = (SBEChatAccess) mc.gui.getChat();

        access.sbe$getLineTracker().setSelectedMessage(message);
        mc.keyboardHandler.setClipboard(ChatMessageResolver.toRawText(message.content()));
        sbe$contextMenu.notifyCopied(x, y);

        // One-frame flash then clear — the selection is purely visual here.
        mc.schedule(() -> access.sbe$getLineTracker().setSelectedMessage(null));
    }
}