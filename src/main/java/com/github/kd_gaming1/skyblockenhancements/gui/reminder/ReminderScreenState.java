package com.github.kd_gaming1.skyblockenhancements.gui.reminder;

import com.github.kd_gaming1.skyblockenhancements.feature.reminder.OutputType;
import com.github.kd_gaming1.skyblockenhancements.feature.reminder.Reminder;
import com.github.kd_gaming1.skyblockenhancements.feature.reminder.ReminderManager;
import com.github.kd_gaming1.skyblockenhancements.feature.reminder.TriggerType;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ReminderScreenState {

    private int lastReminderStateHash = 0;

    public enum Tab {
        LIST,
        CREATE
    }

    public enum SortOrder {
        BY_ID("ID"),
        BY_TIME_LEFT("Time Left");

        private final String label;

        SortOrder(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public enum RepeatMode {
        ONCE("Once"),
        REPEAT_N("Repeat N times"),
        REPEAT_FOREVER("Repeat forever");

        private final String label;

        RepeatMode(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private final ReminderManager reminderManager;
    private final Runnable persistAction;

    private Tab activeTab = Tab.LIST;
    private SortOrder sortOrder = SortOrder.BY_ID;

    private String nameText = "";
    private String amountText = "";
    private TimeUnit selectedUnit = TimeUnit.MINUTES;
    private String amount2Text = "";
    private TimeUnit selectedUnit2 = TimeUnit.MINUTES;
    private TriggerType selectedTrigger = TriggerType.WHILE_PLAYING;
    private OutputType selectedVisualOutput = OutputType.CHAT;
    private boolean soundEnabled = false;
    private RepeatMode repeatMode = RepeatMode.ONCE;
    private String repeatCountText = "";
    private String messageText = "";

    private List<Reminder> reminders;

    private String feedbackMessage = null;
    private long feedbackExpireMs = 0;

    private Integer editingReminderId = null;

    private boolean listDirty = false;

    public ReminderScreenState(ReminderManager reminderManager, Runnable persistAction) {
        this.reminderManager = reminderManager;
        this.persistAction = persistAction;
        this.reminders = reminderManager.getActiveReminders();
    }

    public void resetReminderTime(int id) {
        if (reminderManager.resetReminderTime(id)) {
            persist();
            refreshReminders();
            markListDirty();
        }
    }

    public Tab getActiveTab() {
        return activeTab;
    }

    public void setActiveTab(Tab tab) {
        this.activeTab = tab;
        if (tab == Tab.LIST) {
            refreshReminders();
        }
    }

    public SortOrder getSortOrder() {
        return sortOrder;
    }

    public void cycleSortOrder() {
        SortOrder[] values = SortOrder.values();
        sortOrder = values[(sortOrder.ordinal() + 1) % values.length];
        markListDirty();
    }

    public String getNameText() {
        return nameText;
    }

    public void setNameText(String text) {
        this.nameText = text;
    }

    public String getAmountText() {
        return amountText;
    }

    public void setAmountText(String text) {
        this.amountText = text;
    }

    public TimeUnit getSelectedUnit() {
        return selectedUnit;
    }

    public void setSelectedUnit(TimeUnit u) {
        this.selectedUnit = u;
    }

    public String getAmount2Text() {
        return amount2Text;
    }

    public void setAmount2Text(String text) {
        this.amount2Text = text;
    }

    public TimeUnit getSelectedUnit2() {
        return selectedUnit2;
    }

    public void setSelectedUnit2(TimeUnit u) {
        this.selectedUnit2 = u;
    }

    public TriggerType getSelectedTrigger() {
        return selectedTrigger;
    }

    public void setSelectedTrigger(TriggerType t) {
        this.selectedTrigger = t;
    }

    public OutputType getSelectedVisualOutput() {
        return selectedVisualOutput;
    }

    public void setSelectedVisualOutput(OutputType out) {
        this.selectedVisualOutput = out;
    }

    public boolean isSoundEnabled() {
        return soundEnabled;
    }

    public void setSoundEnabled(boolean on) {
        this.soundEnabled = on;
    }

    public RepeatMode getRepeatMode() {
        return repeatMode;
    }

    public void setRepeatMode(RepeatMode m) {
        this.repeatMode = m;
    }

    public String getRepeatCountText() {
        return repeatCountText;
    }

    public void setRepeatCountText(String t) {
        this.repeatCountText = t;
    }

    public String getMessageText() {
        return messageText;
    }

    public void setMessageText(String text) {
        this.messageText = text;
    }

    public List<Reminder> getReminders() {
        return reminders;
    }

    public void refreshReminders() {
        var raw = new java.util.ArrayList<>(reminderManager.getActiveReminders());
        Comparator<Reminder> pausedLast = Comparator.comparingInt(r -> r.isPaused() ? 1 : 0);
        Comparator<Reminder> primary =
                sortOrder == SortOrder.BY_TIME_LEFT
                        ? Comparator.comparingLong(Reminder::getRemainingMs)
                        : Comparator.comparingInt(Reminder::getId);
        raw.sort(pausedLast.thenComparing(primary));
        reminders = Collections.unmodifiableList(raw);
    }

    public void markListDirty() {
        listDirty = true;
    }

    public boolean isListDirty() {
        return listDirty;
    }

    public void clearListDirty() {
        listDirty = false;
    }

    public String tryCreateReminder() {
        return trySaveReminder();
    }

    public String trySaveReminder() {
        FormData form = parseForm();
        if (form.error != null) {
            return form.error;
        }

        OutputType outType = deriveOutputType(selectedVisualOutput, soundEnabled);

        if (isEditing()) {
            reminderManager.updateReminder(
                    editingReminderId,
                    form.durationMs,
                    outType,
                    form.name,
                    form.message,
                    selectedTrigger,
                    form.totalRepeats);
            editingReminderId = null;
            showFeedback("Reminder updated!");
        } else {
            reminderManager.createReminder(
                    form.durationMs, outType, form.name, form.message, selectedTrigger, form.totalRepeats);
            showFeedback("Reminder created!");
        }

        persist();
        resetForm();
        refreshReminders();
        markListDirty();
        return null;
    }

    public void removeReminder(int id) {
        if (reminderManager.removeReminder(id)) {
            persist();
            refreshReminders();
            markListDirty();
        }
    }

    public void toggleReminder(Reminder reminder) {
        if (reminderManager.toggleReminder(reminder.getId())) {
            persist();
            markListDirty();
        }
    }

    public boolean isEditing() {
        return editingReminderId != null;
    }

    public void startEditing(Reminder r) {
        editingReminderId = r.getId();
        nameText = r.getName() != null ? r.getName() : "";
        messageText = r.getMessage();
        selectedTrigger = r.getTriggerType();

        // Safe Enum checking
        OutputType rOut = r.getOutputType();
        soundEnabled = rOut.hasSound;

        if (rOut.hasChat && rOut.hasTitle) {
            selectedVisualOutput = OutputType.CHAT_AND_TITLE;
        } else if (rOut.hasTitle) {
            selectedVisualOutput = OutputType.TITLE_BOX;
        } else {
            selectedVisualOutput = OutputType.CHAT;
        }

        if (r.getTotalRepeats() == 1) {
            repeatMode = RepeatMode.ONCE;
            repeatCountText = "";
        } else if (r.getTotalRepeats() == ReminderManager.REPEAT_FOREVER) {
            repeatMode = RepeatMode.REPEAT_FOREVER;
            repeatCountText = "";
        } else {
            repeatMode = RepeatMode.REPEAT_N;
            repeatCountText = String.valueOf(r.getTotalRepeats());
        }

        extractDurationFields(r.getOriginalDuration());
        setActiveTab(Tab.CREATE);
    }

    public void cancelEditing() {
        editingReminderId = null;
        resetForm();
        setActiveTab(Tab.LIST);
    }

    private void extractDurationFields(long totalMs) {
        TimeUnit[] units = {TimeUnit.DAYS, TimeUnit.HOURS, TimeUnit.MINUTES, TimeUnit.SECONDS};
        long remaining = totalMs;

        TimeUnit unit1 = TimeUnit.SECONDS;
        long val1 = 0;
        for (TimeUnit u : units) {
            if (remaining >= u.getMultiplierMs()) {
                unit1 = u;
                val1 = remaining / u.getMultiplierMs();
                remaining -= val1 * u.getMultiplierMs();
                break;
            }
        }

        TimeUnit unit2 = TimeUnit.SECONDS;
        long val2 = 0;
        if (remaining > 0) {
            for (TimeUnit u : units) {
                if (remaining >= u.getMultiplierMs()) {
                    unit2 = u;
                    val2 = remaining / u.getMultiplierMs();
                    break;
                }
            }
        }

        amountText = String.valueOf(val1);
        selectedUnit = unit1;
        if (val2 > 0) {
            amount2Text = String.valueOf(val2);
            selectedUnit2 = unit2;
        } else {
            amount2Text = "";
        }
    }

    private void resetForm() {
        amountText = "";
        amount2Text = "";
        messageText = "";
        nameText = "";
        repeatCountText = "";
        repeatMode = RepeatMode.ONCE;
    }

    public void pollReminderStateChanges() {
        int hash = 1;
        for (Reminder r : reminderManager.getActiveReminders()) {
            hash = 31 * hash + r.getId();
            hash = 31 * hash + (r.isPaused() ? 1 : 0);
            hash = 31 * hash + r.getRepeatCount();
            hash = 31 * hash + r.getTotalRepeats();
            // REMOVED time hash - UI updates itself directly now!
        }
        if (hash != lastReminderStateHash) {
            lastReminderStateHash = hash;
            markListDirty();
        }
    }

    public void showFeedback(String message) {
        feedbackMessage = message;
        feedbackExpireMs = System.currentTimeMillis() + 3000;
    }

    public String getActiveFeedback() {
        if (feedbackMessage == null) {
            return null;
        }
        if (System.currentTimeMillis() > feedbackExpireMs) {
            feedbackMessage = null;
            return null;
        }
        return feedbackMessage;
    }

    public int deriveRepeatCount() {
        return switch (repeatMode) {
            case ONCE -> 1;
            case REPEAT_FOREVER -> ReminderManager.REPEAT_FOREVER;
            case REPEAT_N -> {
                try {
                    int n = Integer.parseInt(repeatCountText.trim());
                    yield n >= 2 ? n : -1;
                } catch (NumberFormatException e) {
                    yield -1;
                }
            }
        };
    }

    private static OutputType deriveOutputType(OutputType visual, boolean sound) {
        if (!sound) {
            return visual;
        }
        return switch (visual) {
            case CHAT -> OutputType.CHAT_AND_SOUND;
            case TITLE_BOX -> OutputType.TITLE_AND_SOUND;
            case CHAT_AND_TITLE -> OutputType.ALL;
            default -> visual;
        };
    }

    private FormData parseForm() {
        int amount = 0;
        if (!amountText.trim().isEmpty()) {
            try {
                amount = Integer.parseInt(amountText.trim());
                if (amount < 0) return FormData.error("Amount cannot be negative.");
            } catch (NumberFormatException e) {
                return FormData.error("Amount must be a valid number.");
            }
        }

        int amount2 = 0;
        String a2 = amount2Text.trim();
        if (!a2.isEmpty()) {
            try {
                amount2 = Integer.parseInt(a2);
                if (amount2 < 0) return FormData.error("Secondary amount cannot be negative.");
            } catch (NumberFormatException e) {
                return FormData.error("Secondary amount must be a valid number.");
            }
        }

        if (amount == 0 && amount2 == 0) {
            return FormData.error("Total duration must be greater than 0.");
        }

        long durationMs = (long) amount * selectedUnit.getMultiplierMs();
        durationMs += (long) amount2 * selectedUnit2.getMultiplierMs();

        String msg = messageText.trim();
        if (msg.isEmpty()) {
            return FormData.error("Message cannot be empty.");
        }

        String name = nameText.trim().isEmpty() ? null : nameText.trim();

        int totalRepeats = deriveRepeatCount();
        if (repeatMode == RepeatMode.REPEAT_N && totalRepeats == -1) {
            return FormData.error("Repeat count must be a whole number ≥ 2.");
        }

        return FormData.ok(durationMs, name, msg, totalRepeats);
    }

    private void persist() {
        if (persistAction != null) {
            persistAction.run();
        }
    }

    private record FormData(long durationMs, String name, String message, int totalRepeats, String error) {

        private static FormData ok(long durationMs, String name, String message, int totalRepeats) {
            return new FormData(durationMs, name, message, totalRepeats, null);
        }

        private static FormData error(String error) {
            return new FormData(0, null, null, 0, error);
        }
    }

    public enum TimeUnit {
        SECONDS("Sec", 1_000L),
        MINUTES("Min", 60_000L),
        HOURS("Hour", 3_600_000L),
        DAYS("Day", 86_400_000L);

        private final String label;
        private final long multiplierMs;

        TimeUnit(String label, long multiplierMs) {
            this.label = label;
            this.multiplierMs = multiplierMs;
        }

        public String getLabel() {
            return label;
        }

        public long getMultiplierMs() {
            return multiplierMs;
        }
    }
}