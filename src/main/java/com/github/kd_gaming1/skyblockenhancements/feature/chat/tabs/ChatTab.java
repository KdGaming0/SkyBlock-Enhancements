package com.github.kd_gaming1.skyblockenhancements.feature.chat.tabs;

import java.util.function.Predicate;

/**
 * Hypixel chat channel tabs. Each tab defines a filter predicate that matches messages belonging to
 * that channel.
 *
 * <p>Inspired by hypixel-chat-tabs by yellowbirb. This is an original implementation.
 */
public enum ChatTab {
    ALL("A", s -> true),
    PARTY(
            "P",
            s ->
                    s.startsWith("Party > ")
                            || s.startsWith("P > ")
                            || s.contains("has invited you to join their party!")
                            || s.contains("to the party! They have 60 seconds to accept.")
                            || s.contains("has disbanded the party!")
                            || s.endsWith("joined the party.")
                            || s.endsWith("has left the party.")
                            || s.endsWith("has been removed from the party.")
                            || s.startsWith("The party was transferred to ")
                            || s.equals(
                            "The party was disbanded because all invites expired and the party was empty")),
    GUILD("G", s -> s.startsWith("Guild > ") || s.startsWith("G > ")),
    PM(
            "PM",
            s ->
                    s.startsWith("To ") || s.startsWith("From ") || s.startsWith("Friend > ")),
    COOP("CC", s -> s.startsWith("Co-op > "));

    private final String label;
    private final Predicate<String> filter;

    ChatTab(String label, Predicate<String> filter) {
        this.label = label;
        this.filter = filter;
    }

    public String label() {
        return label;
    }

    /** Returns {@code true} if the plain-text message belongs to this channel. */
    public boolean matches(String plainText) {
        return filter.test(plainText);
    }
}