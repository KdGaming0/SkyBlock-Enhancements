package com.github.kd_gaming1.skyblockenhancements.gui.reminder.component;

import com.daqem.uilib.gui.component.AbstractComponent;
import com.daqem.uilib.gui.component.color.ColorComponent;
import com.daqem.uilib.gui.widget.ScrollContainerWidget;
import com.github.kd_gaming1.skyblockenhancements.feature.reminder.ReminderManager;
import com.github.kd_gaming1.skyblockenhancements.gui.reminder.ReminderColors;
import com.github.kd_gaming1.skyblockenhancements.gui.reminder.ReminderScreenState;
import com.github.kd_gaming1.skyblockenhancements.gui.reminder.ReminderScreenState.Tab;
import net.minecraft.client.gui.GuiGraphics;

public class ReminderTabContentComponent extends AbstractComponent {

    private final ReminderScreenState state;

    private Tab cachedTab;
    private ReminderScreenState.RepeatMode cachedRepeatMode;
    private boolean cachedIsEditing;

    private ScrollContainerWidget createScroll;

    public ReminderTabContentComponent(int x, int y, int width, int height,
                                       ReminderScreenState state, ReminderManager reminderManager) {
        super(x, y, width, height);
        this.state           = state;
        this.cachedTab       = state.getActiveTab();
        this.cachedRepeatMode = state.getRepeatMode();
        this.cachedIsEditing  = state.isEditing();
        build();
    }

    private void build() {
        this.clear();
        createScroll = null;

        this.addComponent(new ColorComponent(0, 0, getWidth(), getHeight(), ReminderColors.PANEL_BG));
        this.addComponent(new ColorComponent(0, 0, 1, getHeight(), ReminderColors.BORDER_GOLD));
        this.addComponent(new ColorComponent(getWidth() - 1, 0, 1, getHeight(), ReminderColors.BORDER_GOLD));
        this.addComponent(new ColorComponent(0, getHeight() - 1, getWidth(), 1, ReminderColors.BORDER_GOLD));

        if (state.getActiveTab() == Tab.LIST) {
            this.addComponent(new ReminderListComponent(0, 0, getWidth(), getHeight(), state));
        } else {
            int innerW = getWidth() - 2;
            int innerH = getHeight() - 1;
            CreateReminderComponent form = new CreateReminderComponent(0, 0, innerW, 0, state);
            createScroll = new ScrollContainerWidget(innerW, innerH, 0);
            createScroll.uilib$updateParentPosition(getTotalX() + 1, getTotalY());
            createScroll.addComponent(form);
            this.addWidget(createScroll);
        }
    }

    @Override
    public void updateParentPosition(int parentX, int parentY, int parentWidth, int parentHeight) {
        super.updateParentPosition(parentX, parentY, parentWidth, parentHeight);
        if (createScroll != null) {
            createScroll.uilib$updateParentPosition(getTotalX() + 1, getTotalY());
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY,
                       float partialTick, int parentWidth, int parentHeight) {
        Tab  currentTab  = state.getActiveTab();
        ReminderScreenState.RepeatMode currentMode = state.getRepeatMode();
        boolean isEditing = state.isEditing();

        boolean tabChanged  = cachedTab != currentTab;
        boolean modeChanged = currentTab == Tab.CREATE && cachedRepeatMode != currentMode;
        boolean editChanged = currentTab == Tab.CREATE && cachedIsEditing != isEditing;

        if (tabChanged || modeChanged || editChanged) {
            cachedTab        = currentTab;
            cachedRepeatMode = currentMode;
            cachedIsEditing  = isEditing;
            build();
            this.updateParentPosition(getParentX(), getParentY(), parentWidth, parentHeight);
        }
    }
}