package com.github.kd_gaming1.skyblockenhancements.gui.reminder.component;

import com.daqem.uilib.gui.component.AbstractComponent;
import com.daqem.uilib.gui.component.color.ColorComponent;
import com.daqem.uilib.gui.component.text.TextAlign;
import com.daqem.uilib.gui.component.text.TextComponent;
import com.daqem.uilib.gui.widget.ButtonWidget;
import com.github.kd_gaming1.skyblockenhancements.gui.reminder.ReminderColors;
import com.github.kd_gaming1.skyblockenhancements.gui.reminder.ReminderScreenState;
import com.github.kd_gaming1.skyblockenhancements.gui.reminder.ReminderScreenState.Tab;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Horizontal tab bar for the reminder screen.
 * <p>
 * ┌────────────────────────────────────────┐ ← gold top border
 * │  ⏰ Reminders  │  + Create             │
 * └────────────────┘                        ← gold underline on active tab
 *                   ──────────────────────── ← bottom divider (gold side borders drawn AFTER divider
 *                                              so corner pixels are always gold, not dark)
 */
public class ReminderTabBarComponent extends AbstractComponent {

    private final ReminderScreenState state;
    private Tab cachedTab;

    public ReminderTabBarComponent(int x, int y, int width, int height, ReminderScreenState state) {
        super(x, y, width, height);
        this.state     = state;
        this.cachedTab = state.getActiveTab();
        build();
    }

    private void build() {
        this.clear();

        int tabW = getWidth() / 2;
        int h    = getHeight();

        // 1. Background
        this.addComponent(new ColorComponent(0, 0, getWidth(), h, ReminderColors.TAB_BAR_BG));

        // 2. Top gold border
        this.addComponent(new ColorComponent(0, 0, getWidth(), 1, ReminderColors.BORDER_GOLD));

        // 3. Bottom divider — drawn BEFORE gold side borders so the corners stay gold
        this.addComponent(new ColorComponent(0, h - 1, getWidth(), 1, ReminderColors.DIVIDER));

        // 4. Gold side borders — rendered on top of the divider's corner pixels
        this.addComponent(new ColorComponent(0, 0, 1, h, ReminderColors.BORDER_GOLD));
        this.addComponent(new ColorComponent(getWidth() - 1, 0, 1, h, ReminderColors.BORDER_GOLD));

        // 5. Active tab background tint
        int activeX = state.getActiveTab() == Tab.LIST ? 0 : tabW;
        this.addComponent(new ColorComponent(activeX + 1, 1, tabW - 2, h - 2, ReminderColors.TAB_ACTIVE_BG));

        // 6. Active tab gold underline
        this.addComponent(new ColorComponent(activeX + 4, h - 3, tabW - 8, 2, ReminderColors.BORDER_GOLD));

        // 7. Tab divider
        this.addComponent(new ColorComponent(tabW, 4, 1, h - 8, ReminderColors.DIVIDER));

        // 8. Labels — drawn over everything so they're always visible
        boolean listActive = state.getActiveTab() == Tab.LIST;
        int     active     = ReminderColors.TEXT_PRIMARY;
        int     muted      = ReminderColors.TEXT_SECONDARY;

        TextComponent listLabel = new TextComponent(
                tabW / 2, h / 2 - 4,
                Component.literal("⏰ Reminders"),
                listActive ? active : muted);
        listLabel.setTextAlign(TextAlign.CENTER);
        this.addComponent(listLabel);

        // Dynamic Edit / Create Label
        TextComponent createLabel = new TextComponent(
                tabW + tabW / 2, h / 2 - 4,
                Component.literal(state.isEditing() ? "✎ Edit" : "+ Create"),
                listActive ? muted : active);
        createLabel.setTextAlign(TextAlign.CENTER);
        this.addComponent(createLabel);

        // 9. Transparent click-area buttons (alpha=0 hides the vanilla button frame)
        ButtonWidget listBtn = new ButtonWidget(
                0, 0, tabW, h, Component.empty(),
                btn -> state.setActiveTab(Tab.LIST));
        listBtn.setAlpha(0f);

        ButtonWidget createBtn = new ButtonWidget(
                tabW, 0, tabW, h, Component.empty(),
                btn -> state.setActiveTab(Tab.CREATE));
        createBtn.setAlpha(0f);

        this.addWidget(listBtn);
        this.addWidget(createBtn);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY,
                       float partialTick, int parentWidth, int parentHeight) {
        if (cachedTab != state.getActiveTab()) {
            cachedTab = state.getActiveTab();
            build();
            this.updateParentPosition(getParentX(), getParentY(), parentWidth, parentHeight);
        }
    }
}