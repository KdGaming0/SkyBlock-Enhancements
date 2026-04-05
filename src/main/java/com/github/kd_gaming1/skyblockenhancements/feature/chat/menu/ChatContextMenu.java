package com.github.kd_gaming1.skyblockenhancements.feature.chat.menu;

import com.daqem.uilib.gui.widget.ButtonWidget;
import com.github.kd_gaming1.skyblockenhancements.access.SBEChatAccess;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.Nullable;

/**
 * A lightweight overlay that appears on right-click over a chat message, offering copy and delete
 * actions. Buttons use UI Lib's {@link ButtonWidget} for consistent styling.
 *
 * <p>The menu is not a separate screen — it is drawn and event-handled by the {@code ChatScreen}
 * mixin that owns it.
 */
public final class ChatContextMenu {

    private static final int BUTTON_WIDTH = 90;
    private static final int BUTTON_HEIGHT = 14;
    private static final int PADDING = 2;
    private static final int BACKGROUND_COLOR = 0xCC000000;
    private static final int BORDER_COLOR = 0xFF333333;

    /** Duration of the "Copied!" toast in milliseconds. */
    private static final long TOAST_DURATION_MS = 1_200;

    private static final int TOAST_PADDING_X = 4;
    private static final int TOAST_PADDING_Y = 2;
    private static final String TOAST_LABEL = "Copied!";

    private @Nullable GuiMessage targetMessage;
    private final List<MenuEntry> entries = new ArrayList<>();
    private int menuX;
    private int menuY;
    private int menuWidth;
    private int menuHeight;

    /** Screen position where the toast should appear. */
    private int toastX;

    private int toastY;

    /** {@link System#currentTimeMillis()} at which the last copy happened, or {@code -1}. */
    private long toastStartMs = -1;

    public boolean isOpen() {
        return targetMessage != null;
    }

    /** Opens the menu at the given screen position for the specified message. */
    public void open(
            GuiMessage message, int screenX, int screenY, int screenWidth, int screenHeight) {
        close();
        targetMessage = message;

        addEntry("Copy Text", this::copyRaw, "Copy the full message as plain text");
        addEntry("Copy Message", this::copyMessageBody, "Copy only the message body, without the sender prefix");
        addEntry("Copy &Codes", this::copyFormatted, "Copy with &-prefixed formatting codes");
        addEntry("Delete", this::deleteMessage, "Remove this message from chat history");

        // Layout: vertical stack of buttons.
        menuWidth = BUTTON_WIDTH + PADDING * 2;
        menuHeight = entries.size() * (BUTTON_HEIGHT + PADDING) + PADDING;

        menuX = Math.min(screenX, screenWidth - menuWidth);
        menuY = Math.min(screenY, screenHeight - menuHeight);
        if (menuY < 0) menuY = 0;

        int y = menuY + PADDING;
        for (MenuEntry entry : entries) {
            entry.button.setX(menuX + PADDING);
            entry.button.setY(y);
            y += BUTTON_HEIGHT + PADDING;
        }
    }

    /** Closes the menu and clears all state. */
    public void close() {
        targetMessage = null;
        entries.clear();
    }

    /** Renders the menu background and buttons, then the copy toast if active. */
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (isOpen()) {
            graphics.fill(
                    menuX - 1,
                    menuY - 1,
                    menuX + menuWidth + 1,
                    menuY + menuHeight + 1,
                    BORDER_COLOR);
            graphics.fill(menuX, menuY, menuX + menuWidth, menuY + menuHeight, BACKGROUND_COLOR);

            for (MenuEntry entry : entries) {
                entry.button.render(graphics, mouseX, mouseY, partialTick);
            }
        }

        renderToast(graphics);

        if (isOpen()) {
            for (MenuEntry entry : entries) {
                if (entry.tooltip != null && entry.button.isMouseOver(mouseX, mouseY)) {
                    graphics.setTooltipForNextFrame(
                            Minecraft.getInstance().font,
                            Component.literal(entry.tooltip),
                            mouseX,
                            mouseY);
                    break;
                }
            }
        }
    }

    /**
     * Shows the "Copied!" toast near the given screen position. Call this when a copy action
     * happens outside the context menu (e.g. direct right-click copy mode).
     */
    public void notifyCopied(int screenX, int screenY) {
        toastX = screenX;
        toastY = screenY;
        toastStartMs = System.currentTimeMillis();
    }

    /**
     * Handles a mouse click. Returns {@code true} if the click was consumed (hit a button or was
     * inside the menu bounds). Clicks outside the menu dismiss it.
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isOpen()) return false;

        boolean inside =
                mouseX >= menuX
                        && mouseX <= menuX + menuWidth
                        && mouseY >= menuY
                        && mouseY <= menuY + menuHeight;

        if (inside && button == 0) {
            for (MenuEntry entry : entries) {
                if (entry.button.isMouseOver(mouseX, mouseY)) {
                    entry.action.run();
                    close();
                    return true;
                }
            }
            return true;
        }

        close();
        return false;
    }

    // Actions
    private void copyRaw() {
        if (targetMessage == null) return;
        setClipboard(ChatMessageResolver.toRawText(targetMessage.content()));
        notifyCopied(menuX, menuY - BUTTON_HEIGHT);
    }

    private void copyMessageBody() {
        if (targetMessage == null) return;
        setClipboard(ChatMessageResolver.toMessageBody(targetMessage.content()));
        notifyCopied(menuX, menuY - BUTTON_HEIGHT);
    }

    private void copyFormatted() {
        if (targetMessage == null) return;
        setClipboard(ChatMessageResolver.toFormattedText(targetMessage.content()));
        notifyCopied(menuX, menuY - BUTTON_HEIGHT);
    }

    private void deleteMessage() {
        if (targetMessage == null) return;
        SBEChatAccess access = (SBEChatAccess) Minecraft.getInstance().gui.getChat();
        if (access.sbe$getAllMessages().remove(targetMessage)) {
            access.sbe$refreshMessages();
        }
    }

    // Helpers

    /**
     * Creates a button/action pair. The button is used only for rendering and hover detection.
     */
    private void addEntry(String label, Runnable action, @Nullable String tooltip) {
        ButtonWidget btn =
                new ButtonWidget(
                        0, 0, BUTTON_WIDTH, BUTTON_HEIGHT, Component.literal(label), b -> {});
        entries.add(new MenuEntry(btn, action, tooltip));
    }

    private void renderToast(GuiGraphics graphics) {
        if (toastStartMs < 0) return;

        long elapsed = System.currentTimeMillis() - toastStartMs;
        if (elapsed >= TOAST_DURATION_MS) {
            toastStartMs = -1;
            return;
        }

        // Fade out over the last third of the duration.
        float fadeStart = TOAST_DURATION_MS * 0.67f;
        float alpha =
                elapsed < fadeStart
                        ? 1f
                        : 1f - (elapsed - fadeStart) / (TOAST_DURATION_MS - fadeStart);
        int a = Math.round(alpha * 0xFF);

        var font = Minecraft.getInstance().font;
        int textWidth = font.width(TOAST_LABEL);
        int boxW = textWidth + TOAST_PADDING_X * 2;
        int boxH = font.lineHeight + TOAST_PADDING_Y * 2;

        int bx = toastX;
        int by = toastY - boxH - 2;

        graphics.fill(bx - 1, by - 1, bx + boxW + 1, by + boxH + 1, ARGB.color(a, 0x33, 0x33, 0x33));
        graphics.fill(bx, by, bx + boxW, by + boxH, ARGB.color(a, 0x00, 0x00, 0x00));
        graphics.drawString(font, TOAST_LABEL, bx + TOAST_PADDING_X, by + TOAST_PADDING_Y, ARGB.color(a, 0x55, 0xFF, 0x55), false);
    }

    private static void setClipboard(String text) {
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
    }

    private record MenuEntry(ButtonWidget button, Runnable action, @Nullable String tooltip) {}
}