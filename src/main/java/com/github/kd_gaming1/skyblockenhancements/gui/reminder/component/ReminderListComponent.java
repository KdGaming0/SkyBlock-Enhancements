package com.github.kd_gaming1.skyblockenhancements.gui.reminder.component;

import com.daqem.uilib.gui.component.AbstractComponent;
import com.daqem.uilib.gui.component.color.ColorComponent;
import com.daqem.uilib.gui.component.text.TextAlign;
import com.daqem.uilib.gui.component.text.TextComponent;
import com.daqem.uilib.gui.widget.ButtonWidget;
import com.daqem.uilib.gui.widget.ScrollContainerWidget;
import com.github.kd_gaming1.skyblockenhancements.feature.reminder.Reminder;
import com.github.kd_gaming1.skyblockenhancements.gui.reminder.ReminderColors;
import com.github.kd_gaming1.skyblockenhancements.gui.reminder.ReminderScreenState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.util.List;

/**
 * Scrollable list of active reminders, or a styled empty-state card.
 *
 * <p>The row width is reduced by {@code SCROLLBAR_RESERVE} pixels so the ScrollContainerWidget's
 * scrollbar never renders on top of the action buttons.
 */
public class ReminderListComponent extends AbstractComponent {

    private static final int PADDING = 8;
    private static final int HEADER_H = 18;
    private static final int ROW_HEIGHT = 42;
    private static final int ROW_SPACING = 4;
    private static final int SCROLLBAR_RESERVE = 14;

    private record ReminderSnapshot(int id, boolean paused, int repeatCount, int totalRepeats) {}

    private final ReminderScreenState state;
    private List<ReminderSnapshot> cachedSnapshots = List.of();
    private ScrollContainerWidget scrollContainer;

    public ReminderListComponent(int x, int y, int width, int height, ReminderScreenState state) {
        super(x, y, width, height);
        this.state = state;
        build();
        this.cachedSnapshots = snapshot(state.getReminders());
    }

    private void build() {
        this.clear();

        List<Reminder> reminders = state.getReminders();

        if (reminders.isEmpty()) {
            buildEmptyState();
            return;
        }

        int scrollW = getWidth() - PADDING * 2;
        int scrollY = PADDING + HEADER_H;
        int scrollH = getHeight() - scrollY - PADDING;
        int rowW = scrollW - SCROLLBAR_RESERVE;

        String sortLabel = "Sort: " + state.getSortOrder().getLabel() + " ▾";
        int btnW = 90;
        ButtonWidget sortBtn =
                new ButtonWidget(
                        getWidth() - PADDING - btnW,
                        PADDING,
                        btnW,
                        HEADER_H - 2,
                        Component.literal(sortLabel).withStyle(ChatFormatting.GRAY),
                        btn -> {
                            state.cycleSortOrder();
                            // listDirty will trigger refresh + potential rebuild in render()
                        });
        this.addWidget(sortBtn);

        scrollContainer = new ScrollContainerWidget(scrollW, scrollH, ROW_SPACING);
        scrollContainer.uilib$updateParentPosition(getTotalX() + PADDING, getTotalY() + scrollY);

        for (Reminder reminder : reminders) {
            scrollContainer.addComponent(new ReminderRowComponent(0, 0, rowW, ROW_HEIGHT, reminder, state));
        }

        this.addWidget(scrollContainer);
    }

    private void buildEmptyState() {
        scrollContainer = null;
        int cx = getWidth() / 2;
        int cy = getHeight() / 2;
        int cardW = getWidth() - PADDING * 6;
        int cardH = 110;
        int cardX = (getWidth() - cardW) / 2;
        int cardY = cy - cardH / 2;

        this.addComponent(new ColorComponent(cardX, cardY, cardW, cardH, ReminderColors.ROW_BG));
        this.addComponent(new ColorComponent(cardX, cardY, cardW, 2, ReminderColors.BORDER_GOLD));

        TextComponent icon =
                new TextComponent(cx, cardY + 10, Component.literal("⏰"), ReminderColors.ACCENT_GOLD);
        icon.setTextAlign(TextAlign.CENTER);
        this.addComponent(icon);

        TextComponent header =
                new TextComponent(cx, cardY + 26, Component.literal("No reminders yet"), ReminderColors.TEXT_PRIMARY);
        header.setTextAlign(TextAlign.CENTER);
        this.addComponent(header);

        TextComponent body =
                new TextComponent(
                        cx,
                        cardY + 44,
                        Component.literal("Use + Create to set one up, or /remindme create in chat."),
                        ReminderColors.TEXT_SECONDARY);
        body.setTextAlign(TextAlign.CENTER);
        this.addComponent(body);

        TextComponent hint =
                new TextComponent(
                        cx,
                        cardY + cardH - 14,
                        Component.literal("Tip: /remindme help  for all options"),
                        ReminderColors.TEXT_HINT);
        hint.setTextAlign(TextAlign.CENTER);
        this.addComponent(hint);
    }

    @Override
    public void updateParentPosition(int parentX, int parentY, int parentWidth, int parentHeight) {
        super.updateParentPosition(parentX, parentY, parentWidth, parentHeight);
        if (scrollContainer != null) {
            scrollContainer.uilib$updateParentPosition(getTotalX() + PADDING, getTotalY() + PADDING + HEADER_H);
        }
    }

    @Override
    public void render(
            GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int parentWidth, int parentHeight) {
        if (state.isListDirty()) {
            state.clearListDirty();
            state.refreshReminders();
        }

        List<Reminder> current = state.getReminders();
        List<ReminderSnapshot> now = snapshot(current);

        if (!now.equals(cachedSnapshots)) {
            cachedSnapshots = now;
            build();
            this.updateParentPosition(getParentX(), getParentY(), parentWidth, parentHeight);
        }
    }

    private static List<ReminderSnapshot> snapshot(List<Reminder> reminders) {
        return reminders.stream()
                .map(r -> new ReminderSnapshot(r.getId(), r.isPaused(), r.getRepeatCount(), r.getTotalRepeats()))
                .toList();
    }
}