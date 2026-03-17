package com.github.kd_gaming1.skyblockenhancements.gui.reminder.component;

import com.daqem.uilib.gui.component.AbstractComponent;
import com.daqem.uilib.gui.component.color.ColorComponent;
import com.daqem.uilib.gui.component.text.TextAlign;
import com.daqem.uilib.gui.component.text.TextComponent;
import com.daqem.uilib.gui.component.text.TruncatedTextComponent;
import com.daqem.uilib.gui.widget.ButtonWidget;
import com.github.kd_gaming1.skyblockenhancements.feature.reminder.Reminder;
import com.github.kd_gaming1.skyblockenhancements.feature.reminder.ReminderManager;
import com.github.kd_gaming1.skyblockenhancements.feature.reminder.TriggerType;
import com.github.kd_gaming1.skyblockenhancements.gui.reminder.ReminderColors;
import com.github.kd_gaming1.skyblockenhancements.gui.reminder.ReminderScreenState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class ReminderRowComponent extends AbstractComponent {

    private static final int PADDING = 6;
    private static final int BUTTON_SIZE = 14;
    private static final int TOGGLE_W = 46;
    private static final int BADGE_W = 56;
    private static final int BADGE_H = 11;

    private static final int TOP_Y = PADDING;
    private static final int BOTTOM_Y = 42 - PADDING - 9;

    private final Reminder reminder;
    private final ReminderScreenState state;

    // Cached component to avoid tree lookups in render()
    private TextComponent timerTextComponent;

    public ReminderRowComponent(
            int x, int y, int width, int height, Reminder reminder, ReminderScreenState state) {
        super(x, y, width, height);
        this.reminder = reminder;
        this.state = state;
        build();
    }

    private void build() {
        this.clear();

        boolean paused = reminder.isPaused();

        int bgColor = paused ? ReminderColors.ROW_BG_PAUSED : ReminderColors.ROW_BG;
        int accentColor = paused ? ReminderColors.TEXT_PAUSED : ReminderColors.ACCENT_BLUE;

        this.addComponent(new ColorComponent(0, 0, getWidth(), getHeight(), bgColor));
        this.addComponent(new ColorComponent(0, getHeight() - 1, getWidth(), 1, ReminderColors.BORDER_IDLE));
        this.addComponent(new ColorComponent(0, 0, 3, getHeight(), accentColor));

        int toggleX = 3 + PADDING;
        ButtonWidget toggleBtn =
                new ButtonWidget(
                        toggleX,
                        (getHeight() - 20) / 2,
                        TOGGLE_W,
                        20,
                        getToggleLabel(reminder),
                        btn -> state.toggleReminder(reminder));
        this.addWidget(toggleBtn);

        int contentX = toggleX + TOGGLE_W + PADDING;

        int removeX = getWidth() - PADDING - BUTTON_SIZE;
        this.addWidget(
                new ButtonWidget(
                        removeX,
                        TOP_Y,
                        BUTTON_SIZE,
                        BUTTON_SIZE,
                        Component.literal("✗").withStyle(ChatFormatting.RED),
                        btn -> state.removeReminder(reminder.getId())));

        int editX = removeX - BUTTON_SIZE - 4;
        this.addWidget(
                new ButtonWidget(
                        editX,
                        TOP_Y,
                        BUTTON_SIZE,
                        BUTTON_SIZE,
                        Component.literal("✎").withStyle(ChatFormatting.YELLOW),
                        btn -> state.startEditing(reminder)));

        int resetX = editX - BUTTON_SIZE - 4;
        this.addWidget(
                new ButtonWidget(
                        resetX,
                        TOP_Y,
                        BUTTON_SIZE,
                        BUTTON_SIZE,
                        Component.literal("↺").withStyle(ChatFormatting.AQUA),
                        btn -> state.resetReminderTime(reminder.getId())));

        int textRightEdge = editX - PADDING;

        int nameColor = paused ? ReminderColors.TEXT_SECONDARY : ReminderColors.TEXT_PRIMARY;
        int nameWidth = textRightEdge - contentX;

        this.addComponent(
                new TruncatedTextComponent(contentX, TOP_Y, nameWidth, Component.literal(reminder.getDisplayName()), nameColor));

        int timeColor = paused ? ReminderColors.TEXT_HINT : ReminderColors.TEXT_TIME;

        // Cache the reference here!
        this.timerTextComponent = new TextComponent(contentX, BOTTOM_Y, Component.literal(buildTimerLine(reminder)), timeColor);
        this.addComponent(this.timerTextComponent);

        boolean isPlayTime = reminder.getTriggerType() == TriggerType.WHILE_PLAYING;
        int badgeBg = isPlayTime ? ReminderColors.BADGE_PLAY_BG : ReminderColors.BADGE_REAL_BG;
        int badgeFg = isPlayTime ? ReminderColors.BADGE_PLAY_TEXT : ReminderColors.BADGE_REAL_TEXT;
        String badge = isPlayTime ? "PLAY TIME" : "REAL TIME";

        if (paused) {
            badgeBg = 0xFF222222;
            badgeFg = 0xFF666666;
        }

        int badgeX = getWidth() - PADDING - BADGE_W;
        int badgeY = BOTTOM_Y - 1;
        this.addComponent(new ColorComponent(badgeX, badgeY, BADGE_W, BADGE_H, badgeBg));
        TextComponent badgeLabel =
                new TextComponent(badgeX + BADGE_W / 2, badgeY + 2, Component.literal(badge), badgeFg);
        badgeLabel.setTextAlign(TextAlign.CENTER);
        this.addComponent(badgeLabel);
    }

    @Override
    public void render(
            GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int parentWidth, int parentHeight) {
        if (reminder.isPaused()) {
            return;
        }
        // Direct, fast update instead of tree traversal
        if (this.timerTextComponent != null) {
            this.timerTextComponent.setText(Component.literal(buildTimerLine(reminder)));
        }
    }

    private static Component getToggleLabel(Reminder reminder) {
        if (!reminder.isPaused()) {
            return Component.literal("Pause").withStyle(ChatFormatting.YELLOW);
        }

        boolean atMaxDuration = reminder.getRemainingMs() >= reminder.getOriginalDuration();
        if (atMaxDuration) {
            return Component.literal("Turn On").withStyle(ChatFormatting.GREEN);
        }

        return Component.literal("Resume").withStyle(ChatFormatting.GREEN);
    }

    private static String buildTimerLine(Reminder reminder) {
        String prefix = "⏱ ";

        long pausedOrLiveMs = reminder.getRemainingMs();
        if (pausedOrLiveMs < 0) {
            pausedOrLiveMs = reminder.getOriginalDuration();
        }

        String timeStr =
                reminder.isPaused()
                        ? ReminderManager.formatMs(pausedOrLiveMs)
                        : ReminderManager.formatMs(pausedOrLiveMs) + " left";

        String repeat = repeatSuffix(reminder);
        return repeat.isEmpty() ? prefix + timeStr : prefix + timeStr + "  " + repeat;
    }

    private static String repeatSuffix(Reminder reminder) {
        int total = reminder.getTotalRepeats();
        if (total == ReminderManager.REPEAT_FOREVER) return "↺ ∞";
        if (total <= 1) return "";
        int remaining = total - reminder.getRepeatCount();
        return "↺ " + remaining + "/" + total;
    }
}