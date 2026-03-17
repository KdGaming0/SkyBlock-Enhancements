package com.github.kd_gaming1.skyblockenhancements.gui.reminder.component;

import com.daqem.uilib.gui.component.AbstractComponent;
import com.daqem.uilib.gui.component.color.ColorComponent;
import com.daqem.uilib.gui.component.text.TextComponent;
import com.daqem.uilib.gui.widget.ButtonWidget;
import com.daqem.uilib.gui.widget.EditBoxWidget;
import com.github.kd_gaming1.skyblockenhancements.feature.reminder.OutputType;
import com.github.kd_gaming1.skyblockenhancements.feature.reminder.TriggerType;
import com.github.kd_gaming1.skyblockenhancements.gui.reminder.ReminderColors;
import com.github.kd_gaming1.skyblockenhancements.gui.reminder.ReminderScreenState;
import com.github.kd_gaming1.skyblockenhancements.gui.reminder.ReminderScreenState.Tab;
import com.github.kd_gaming1.skyblockenhancements.gui.reminder.ReminderScreenState.TimeUnit;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class CreateReminderComponent extends AbstractComponent {

    private static final int PADDING       = 10;
    private static final int FIELD_H       = 16;
    private static final int LABEL_H       = 9;
    private static final int SECTION_INNER = 2;
    private static final int SECTION_GAP   = 7;
    private static final int ACCENT_BAR_W  = 2;
    private static final int ACCENT_PAD    = 4;

    private final ReminderScreenState state;

    private ButtonWidget[] unitButtons;
    private ButtonWidget[] unit2Buttons;
    private ButtonWidget[] triggerButtons;
    private ButtonWidget[] visualButtons;
    private TextComponent  errorLabel;

    public CreateReminderComponent(int x, int y, int width, int height, ReminderScreenState state) {
        super(x, y, width, height);
        this.state = state;
        build();
    }

    private void build() {
        this.clear();
        var font = Minecraft.getInstance().font;
        int cw = getWidth() - PADDING * 2;
        int y  = PADDING;

        // ── Name (optional) ────────────────────────────────────────────────────
        y = sectionLabel(y, "Name  (optional)");
        EditBoxWidget nameField = new EditBoxWidget(font, PADDING, y, cw, FIELD_H,
                Component.literal("Leave blank to use message text as name"));
        nameField.setValue(state.getNameText());
        nameField.setResponder(state::setNameText);
        nameField.setMaxLength(64);
        addWidget(nameField);
        y += FIELD_H + SECTION_GAP;

        // ── Duration ───────────────────────────────────────────────────────────
        y = sectionLabel(y, "Duration");

        TimeUnit[] units = TimeUnit.values();
        int unitBW = (cw - 46 - 4 - (units.length - 1) * 2) / units.length;

        EditBoxWidget amountField = numericBox(font, PADDING, y, 46, true, state.getAmountText(), state::setAmountText);
        addWidget(amountField);

        unitButtons = new ButtonWidget[units.length];
        for (int i = 0; i < units.length; i++) {
            final TimeUnit u = units[i];
            int bx = PADDING + 46 + 4 + i * (unitBW + 2);
            unitButtons[i] = new ButtonWidget(bx, y, unitBW, FIELD_H,
                    Component.literal(u.getLabel()),
                    b -> { state.setSelectedUnit(u); refreshUnitButtons(); });
            addWidget(unitButtons[i]);
        }
        refreshUnitButtons();
        y += FIELD_H + 2;

        addComponent(new TextComponent(PADDING, y + 4, Component.literal("+"), ReminderColors.TEXT_HINT));
        int sx   = PADDING + 14;
        int a2BW = (cw - 14 - 40 - 4 - (units.length - 1) * 2) / units.length;

        EditBoxWidget amount2Field = numericBox(font, sx, y, 40, false, state.getAmount2Text(), state::setAmount2Text);
        addWidget(amount2Field);

        unit2Buttons = new ButtonWidget[units.length];
        for (int i = 0; i < units.length; i++) {
            final TimeUnit u = units[i];
            int bx = sx + 40 + 4 + i * (a2BW + 2);
            unit2Buttons[i] = new ButtonWidget(bx, y, a2BW, FIELD_H,
                    Component.literal(u.getLabel()),
                    b -> { state.setSelectedUnit2(u); refreshUnit2Buttons(); });
            addWidget(unit2Buttons[i]);
        }
        refreshUnit2Buttons();
        y += FIELD_H + SECTION_GAP;

        // ── Trigger ────────────────────────────────────────────────────────────
        y = sectionLabel(y, "Trigger");
        addComponent(new TextComponent(PADDING, y,
                Component.literal("Pauses on logout vs. counts in real time."),
                ReminderColors.TEXT_SECONDARY));
        y += LABEL_H + SECTION_INNER;

        TriggerType[] triggers = TriggerType.values();
        String[] tLabels = {"While Playing", "Real Time"};
        String[] tTips   = {
                "Timer pauses when you disconnect from a server.",
                "Counts down in real-world time, even when offline."
        };
        int tBW = (cw - 2) / triggers.length;
        triggerButtons = new ButtonWidget[triggers.length];
        for (int i = 0; i < triggers.length; i++) {
            final TriggerType t = triggers[i];
            int bx = PADDING + i * (tBW + 2);
            triggerButtons[i] = new ButtonWidget(bx, y, tBW, FIELD_H,
                    Component.literal(tLabels[i]),
                    b -> { state.setSelectedTrigger(t); refreshTriggerButtons(); });
            triggerButtons[i].setTooltip(Tooltip.create(Component.literal(tTips[i])));
            addWidget(triggerButtons[i]);
        }
        refreshTriggerButtons();
        y += FIELD_H + SECTION_GAP;

        // ── Output ─────────────────────────────────────────────────────────────
        y = sectionLabel(y, "Output");
        addComponent(new TextComponent(PADDING, y,
                Component.literal("Visual notification — Sound is independent."),
                ReminderColors.TEXT_SECONDARY));
        y += LABEL_H + SECTION_INNER;

        int soundBW  = 78;
        int vTotalW  = cw - soundBW - 4;
        OutputType[] visuals = {OutputType.CHAT, OutputType.TITLE_BOX, OutputType.CHAT_AND_TITLE};
        String[]     vLabels = {"Chat", "Title", "Both"};
        int vBW = (vTotalW - (visuals.length - 1) * 2) / visuals.length;

        visualButtons = new ButtonWidget[visuals.length];
        for (int i = 0; i < visuals.length; i++) {
            final OutputType v = visuals[i];
            int bx = PADDING + i * (vBW + 2);
            visualButtons[i] = new ButtonWidget(bx, y, vBW, FIELD_H,
                    Component.literal(vLabels[i]),
                    b -> { state.setSelectedVisualOutput(v); refreshVisualButtons(); });
            addWidget(visualButtons[i]);
        }
        refreshVisualButtons();

        ButtonWidget soundButton = new ButtonWidget(PADDING + vTotalW + 4, y, soundBW, FIELD_H,
                getSoundLabel(state.isSoundEnabled()),
                btn -> {
                    boolean on = !state.isSoundEnabled();
                    state.setSoundEnabled(on);
                    btn.setMessage(getSoundLabel(on));
                });
        addWidget(soundButton);
        y += FIELD_H + SECTION_GAP;

        // ── Repeat ─────────────────────────────────────────────────────────────
        y = sectionLabel(y, "Repeat");
        addComponent(new TextComponent(PADDING, y,
                Component.literal("How many times to fire before turning off."),
                ReminderColors.TEXT_SECONDARY));
        y += LABEL_H + SECTION_INNER;

        ReminderScreenState.RepeatMode[] modes = ReminderScreenState.RepeatMode.values();
        int modeBW = (cw - (modes.length - 1) * 2) / modes.length;
        ButtonWidget[] repeatModeButtons = new ButtonWidget[modes.length];
        for (int i = 0; i < modes.length; i++) {
            final ReminderScreenState.RepeatMode m = modes[i];
            int bx = PADDING + i * (modeBW + 2);
            repeatModeButtons[i] = new ButtonWidget(bx, y, modeBW, FIELD_H,
                    Component.literal(m.getLabel()),
                    b -> state.setRepeatMode(m));
            addWidget(repeatModeButtons[i]);
        }
        for (int i = 0; i < modes.length; i++) {
            repeatModeButtons[i].active = modes[i] != state.getRepeatMode();
        }
        y += FIELD_H + SECTION_INNER;

        if (state.getRepeatMode() == ReminderScreenState.RepeatMode.REPEAT_N) {
            addComponent(new TextComponent(PADDING, y,
                    Component.literal("How many times to fire:"),
                    ReminderColors.TEXT_SECONDARY));
            y += LABEL_H + SECTION_INNER;

            EditBoxWidget repeatCountField = numericBox(font, PADDING, y, 60, true,
                    state.getRepeatCountText(), state::setRepeatCountText);
            addWidget(repeatCountField);
            y += FIELD_H + SECTION_INNER;
        }
        y += SECTION_GAP - SECTION_INNER;

        // ── Message ────────────────────────────────────────────────────────────
        y = sectionLabel(y, "Message");
        EditBoxWidget messageField = getEditBoxWidget(font, y, cw);
        addWidget(messageField);
        y += FIELD_H + 4;

        // ── Error + Create/Save Buttons ────────────────────────────────────────

        errorLabel = new TextComponent(PADDING, y, Component.literal(""), ReminderColors.ERROR);
        addComponent(errorLabel);
        y += LABEL_H + 4;

        if (state.isEditing()) {
            int halfW = (cw - 4) / 2;
            addWidget(new ButtonWidget(PADDING, y, halfW, 20,
                    Component.literal("Save Changes").withStyle(ChatFormatting.GREEN),
                    btn -> handleSaveAction()));

            addWidget(new ButtonWidget(PADDING + halfW + 4, y, halfW, 20,
                    Component.literal("Cancel").withStyle(ChatFormatting.GRAY),
                    btn -> state.cancelEditing()));
        } else {
            addWidget(new ButtonWidget(PADDING, y, cw, 20,
                    Component.literal("Create Reminder").withStyle(ChatFormatting.GREEN),
                    btn -> handleSaveAction()));
        }
        y += 20;

        setHeight(y + PADDING);
    }

    private @NotNull EditBoxWidget getEditBoxWidget(Font font, int y, int cw) {
        EditBoxWidget messageField = new EditBoxWidget(font, PADDING, y, cw, FIELD_H,
                Component.literal("e.g. Check the auction house")) {
            @Override
            public List<Component> validateInput(String input) {
                if (input.trim().isEmpty())
                    return new ArrayList<>(List.of(Component.literal("Message cannot be empty.")));
                return new ArrayList<>();
            }
        };
        messageField.setValue(state.getMessageText());
        messageField.setResponder(state::setMessageText);
        messageField.setMaxLength(128);
        return messageField;
    }

    private void handleSaveAction() {
        String err = state.trySaveReminder();
        errorLabel.setText(err != null ? Component.literal(err) : Component.literal(""));
        if (err == null) state.setActiveTab(Tab.LIST);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int sectionLabel(int y, String text) {
        addComponent(new ColorComponent(CreateReminderComponent.PADDING, y + 1, ACCENT_BAR_W, LABEL_H - 2, ReminderColors.ACCENT_GOLD));
        addComponent(new TextComponent(CreateReminderComponent.PADDING + ACCENT_BAR_W + ACCENT_PAD, y,
                Component.literal(text), ReminderColors.TEXT_PRIMARY));
        return y + LABEL_H + SECTION_INNER;
    }

    private EditBoxWidget numericBox(net.minecraft.client.gui.Font font,
                                     int x, int y, int w, boolean requirePositive,
                                     String initial, Consumer<String> responder) {
        EditBoxWidget f = new EditBoxWidget(font, x, y, w, FIELD_H, Component.literal("")) {
            @Override
            public List<Component> validateInput(String input) {
                if (input.isEmpty()) return new ArrayList<>();
                try {
                    int v = Integer.parseInt(input);
                    int min = requirePositive ? 1 : 0;
                    if (v < min) return new ArrayList<>(List.of(Component.literal("Must be ≥ " + min + ".")));
                } catch (NumberFormatException e) {
                    return new ArrayList<>(List.of(Component.literal("Must be a whole number.")));
                }
                return new ArrayList<>();
            }
        };
        f.setValue(initial);
        f.setResponder(responder);
        f.setMaxLength(6);
        return f;
    }

    private void refreshUnitButtons() {
        TimeUnit sel = state.getSelectedUnit();
        TimeUnit[] all = TimeUnit.values();
        for (int i = 0; i < unitButtons.length; i++) unitButtons[i].active = all[i] != sel;
    }

    private void refreshUnit2Buttons() {
        TimeUnit sel = state.getSelectedUnit2();
        TimeUnit[] all = TimeUnit.values();
        for (int i = 0; i < unit2Buttons.length; i++) unit2Buttons[i].active = all[i] != sel;
    }

    private void refreshTriggerButtons() {
        TriggerType sel = state.getSelectedTrigger();
        TriggerType[] all = TriggerType.values();
        for (int i = 0; i < triggerButtons.length; i++) triggerButtons[i].active = all[i] != sel;
    }

    private void refreshVisualButtons() {
        OutputType sel = state.getSelectedVisualOutput();
        OutputType[] all = {OutputType.CHAT, OutputType.TITLE_BOX, OutputType.CHAT_AND_TITLE};
        for (int i = 0; i < visualButtons.length; i++) visualButtons[i].active = all[i] != sel;
    }

    private static Component getSoundLabel(boolean on) {
        return on
                ? Component.literal("🔔 Sound: On").withStyle(ChatFormatting.YELLOW)
                : Component.literal("Sound: Off").withStyle(ChatFormatting.GRAY);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt, int pw, int ph) {
    }
}