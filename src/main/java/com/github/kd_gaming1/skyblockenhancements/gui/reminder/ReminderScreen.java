package com.github.kd_gaming1.skyblockenhancements.gui.reminder;

import com.daqem.uilib.gui.AbstractScreen;
import com.daqem.uilib.gui.background.DarkenedBackground;
import com.github.kd_gaming1.skyblockenhancements.feature.reminder.ReminderManager;
import com.github.kd_gaming1.skyblockenhancements.gui.reminder.component.ReminderTabBarComponent;
import com.github.kd_gaming1.skyblockenhancements.gui.reminder.component.ReminderTabContentComponent;
import net.minecraft.network.chat.Component;

public class ReminderScreen extends AbstractScreen {

    private static final int PANEL_WIDTH = 380;
    private static final int PANEL_HEIGHT = 300;
    private static final int TAB_BAR_HEIGHT = 26;

    private final ReminderScreenState state;
    private final ReminderManager reminderManager;

    public ReminderScreen(ReminderManager reminderManager, Runnable persistAction) {
        super(Component.literal("Reminders"));
        this.reminderManager = reminderManager;
        this.state = new ReminderScreenState(reminderManager, persistAction);
        setBackground(new DarkenedBackground());
    }

    @Override
    protected void init() {
        int panelX = (width - PANEL_WIDTH) / 2;
        int panelY = (height - PANEL_HEIGHT) / 2;

        addComponent(new ReminderTabBarComponent(panelX, panelY, PANEL_WIDTH, TAB_BAR_HEIGHT, state));

        addComponent(
                new ReminderTabContentComponent(
                        panelX,
                        panelY + TAB_BAR_HEIGHT,
                        PANEL_WIDTH,
                        PANEL_HEIGHT - TAB_BAR_HEIGHT,
                        state,
                        reminderManager));

        super.init();
    }

    public ReminderScreenState getState() {
        return state;
    }
}