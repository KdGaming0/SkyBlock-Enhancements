package com.github.kd_gaming1.skyblockenhancements.feature.chat.menu;

import com.daqem.uilib.gui.widget.ButtonWidget;
import com.github.kd_gaming1.skyblockenhancements.access.SBEChatAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.Nullable;

/**
 * Right-click overlay offering copy and delete actions on a chat message. The menu is
 * data-driven: construction time receives a list of {@link MenuAction}s and both the buttons
 * and click dispatch are built from that list.
 *
 * <p>While open, the targeted message is highlighted via the selection state on the active
 * {@link com.github.kd_gaming1.skyblockenhancements.feature.chat.ChatLineTracker}. The outline
 * itself is drawn by {@link
 * com.github.kd_gaming1.skyblockenhancements.feature.chat.render.ChatGraphicsAccessProxy}.
 */
public final class ChatContextMenu {

    private static final int BUTTON_WIDTH = 90;
    private static final int BUTTON_HEIGHT = 14;
    private static final int PADDING = 2;
    private static final int BACKGROUND_COLOR = 0xCC000000;
    private static final int BORDER_COLOR = 0xFF333333;

    private static final long TOAST_DURATION_MS = 1_200L;
    private static final float TOAST_FADE_START_FRACTION = 0.67f;
    private static final int TOAST_PADDING_X = 4;
    private static final int TOAST_PADDING_Y = 2;
    private static final String TOAST_COPIED = "Copied!";
    private static final String TOAST_DELETED = "Deleted!";

    /** A single entry in the menu: label, optional tooltip, action, and the toast shown on success. */
    public record MenuAction(String label, @Nullable String tooltip, Consumer<GuiMessage> handler, String toastLabel) {}

    public static final List<MenuAction> DEFAULT_ACTIONS = List.of(
            new MenuAction("Copy Text",
                    "Copy the full message as plain text",
                    msg -> copyToClipboard(ChatMessageResolver.toRawText(msg.content())),
                    TOAST_COPIED),
            new MenuAction("Copy Message",
                    "Copy only the message body, without the sender prefix",
                    msg -> copyToClipboard(ChatMessageResolver.toMessageBody(msg.content())),
                    TOAST_COPIED),
            new MenuAction("Copy &Codes",
                    "Copy with &-prefixed formatting codes",
                    msg -> copyToClipboard(ChatMessageResolver.toFormattedText(msg.content())),
                    TOAST_COPIED),
            new MenuAction("Delete",
                    "Remove this message from chat history",
                    ChatContextMenu::deleteMessage,
                    TOAST_DELETED));

    private final List<MenuAction> actions;
    private final List<ButtonWidget> buttons = new ArrayList<>();

    private @Nullable GuiMessage target;
    private int menuX, menuY, menuWidth, menuHeight;

    private int toastX, toastY;
    private long toastStartMs = -1L;
    private String toastLabel = TOAST_COPIED;

    public ChatContextMenu() {
        this(DEFAULT_ACTIONS);
    }

    public ChatContextMenu(List<MenuAction> actions) {
        this.actions = List.copyOf(actions);
    }

    public boolean isOpen() {
        return target != null;
    }

    public void open(GuiMessage message, int screenX, int screenY, int screenWidth, int screenHeight) {
        close();
        target = message;
        setHighlightedMessage(message);
        buildButtons();
        layoutMenu(screenX, screenY, screenWidth, screenHeight);
    }

    public void close() {
        target = null;
        buttons.clear();
        setHighlightedMessage(null);
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (isOpen()) {
            drawFrame(graphics);
            for (ButtonWidget button : buttons) {
                button.render(graphics, mouseX, mouseY, partialTick);
            }
            drawHoveredTooltip(graphics, mouseX, mouseY);
        }
        drawToast(graphics);
    }

    /** Shows the "Copied!" toast near the given screen position. */
    public void notifyCopied(int screenX, int screenY) {
        showToast(TOAST_COPIED, screenX, screenY);
    }

    /** Shows an arbitrary toast label near the given screen position. */
    public void showToast(String label, int screenX, int screenY) {
        this.toastLabel = label;
        this.toastX = screenX;
        this.toastY = screenY;
        this.toastStartMs = System.currentTimeMillis();
    }

    /** @return {@code true} if the click was consumed (hit a button or was inside the menu). */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isOpen()) return false;

        boolean inside = mouseX >= menuX && mouseX <= menuX + menuWidth
                && mouseY >= menuY && mouseY <= menuY + menuHeight;

        if (inside && button == 0) {
            for (int i = 0; i < buttons.size(); i++) {
                if (buttons.get(i).isMouseOver(mouseX, mouseY)) {
                    fireAction(i);
                    close();
                    return true;
                }
            }
            return true;
        }

        close();
        return false;
    }

    // ---------------------------------------------------------------------
    // Layout
    // ---------------------------------------------------------------------

    private void buildButtons() {
        buttons.clear();
        for (MenuAction action : actions) {
            buttons.add(new ButtonWidget(
                    0, 0, BUTTON_WIDTH, BUTTON_HEIGHT,
                    Component.literal(action.label()),
                    ignored -> {}));
        }
    }

    private void layoutMenu(int screenX, int screenY, int screenWidth, int screenHeight) {
        menuWidth = BUTTON_WIDTH + PADDING * 2;
        menuHeight = actions.size() * (BUTTON_HEIGHT + PADDING) + PADDING;

        menuX = Math.min(screenX, screenWidth - menuWidth);
        menuY = Math.clamp(screenY, 0, screenHeight - menuHeight);

        int y = menuY + PADDING;
        for (ButtonWidget button : buttons) {
            button.setX(menuX + PADDING);
            button.setY(y);
            y += BUTTON_HEIGHT + PADDING;
        }
    }

    private void fireAction(int index) {
        if (target == null) return;
        MenuAction action = actions.get(index);
        action.handler().accept(target);
        showToast(action.toastLabel(), menuX, menuY - BUTTON_HEIGHT);
    }

    // ---------------------------------------------------------------------
    // Drawing
    // ---------------------------------------------------------------------

    private void drawFrame(GuiGraphics graphics) {
        graphics.fill(menuX - 1, menuY - 1, menuX + menuWidth + 1, menuY + menuHeight + 1, BORDER_COLOR);
        graphics.fill(menuX, menuY, menuX + menuWidth, menuY + menuHeight, BACKGROUND_COLOR);
    }

    private void drawHoveredTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int i = 0; i < buttons.size(); i++) {
            String tooltip = actions.get(i).tooltip();
            if (tooltip != null && buttons.get(i).isMouseOver(mouseX, mouseY)) {
                graphics.setTooltipForNextFrame(
                        Minecraft.getInstance().font,
                        Component.literal(tooltip),
                        mouseX, mouseY);
                return;
            }
        }
    }

    private void drawToast(GuiGraphics graphics) {
        if (toastStartMs < 0) return;

        long elapsed = System.currentTimeMillis() - toastStartMs;
        if (elapsed >= TOAST_DURATION_MS) {
            toastStartMs = -1L;
            return;
        }

        float fadeStart = TOAST_DURATION_MS * TOAST_FADE_START_FRACTION;
        float alpha = elapsed < fadeStart
                ? 1f
                : 1f - (elapsed - fadeStart) / (TOAST_DURATION_MS - fadeStart);
        int a = Math.round(alpha * 0xFF);

        Font font = Minecraft.getInstance().font;
        int textWidth = font.width(toastLabel);
        int boxW = textWidth + TOAST_PADDING_X * 2;
        int boxH = font.lineHeight + TOAST_PADDING_Y * 2;

        int bx = toastX;
        int by = toastY - boxH - 2;

        graphics.fill(bx - 1, by - 1, bx + boxW + 1, by + boxH + 1, ARGB.color(a, 0x33, 0x33, 0x33));
        graphics.fill(bx, by, bx + boxW, by + boxH, ARGB.color(a, 0x00, 0x00, 0x00));
        graphics.drawString(font, toastLabel,
                bx + TOAST_PADDING_X, by + TOAST_PADDING_Y,
                ARGB.color(a, 0x55, 0xFF, 0x55), false);
    }

    // ---------------------------------------------------------------------
    // Default action helpers
    // ---------------------------------------------------------------------

    private static void copyToClipboard(String text) {
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
    }

    private static void deleteMessage(GuiMessage message) {
        SBEChatAccess access = (SBEChatAccess) Minecraft.getInstance().gui.getChat();
        if (access.sbe$getAllMessages().remove(message)) {
            access.sbe$refreshMessages();
        }
    }

    private static void setHighlightedMessage(@Nullable GuiMessage message) {
        Minecraft mc = Minecraft.getInstance();
        SBEChatAccess access = (SBEChatAccess) mc.gui.getChat();
        access.sbe$getLineTracker().setSelectedMessage(message);
    }
}