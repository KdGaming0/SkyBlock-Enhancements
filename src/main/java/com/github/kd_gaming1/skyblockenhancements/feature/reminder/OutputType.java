package com.github.kd_gaming1.skyblockenhancements.feature.reminder;

/**
 * How the player is notified when a reminder fires.
 */
public enum OutputType {
    CHAT(true, false, false),
    TITLE_BOX(false, true, false),
    CHAT_AND_TITLE(true, true, false),
    SOUND_ONLY(false, false, true),
    CHAT_AND_SOUND(true, false, true),
    TITLE_AND_SOUND(false, true, true),
    ALL(true, true, true);

    public final boolean hasChat;
    public final boolean hasTitle;
    public final boolean hasSound;

    OutputType(boolean hasChat, boolean hasTitle, boolean hasSound) {
        this.hasChat = hasChat;
        this.hasTitle = hasTitle;
        this.hasSound = hasSound;
    }
}