package com.github.kd_gaming1.skyblockenhancements.feature.chat.tabs;

import java.util.function.Predicate;

/**
 * Hypixel chat channel tabs. Each tab defines a filter predicate that matches messages belonging to
 * that channel.
 */
public enum ChatTab {
    ALL("A", "/chat a", s -> true),
    PARTY(
            "P",
            "/chat p",
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
                            || s.equals("The party was disbanded because all invites expired and the party was empty")
                            || s.equals("You are now in the PARTY channel")
                            || s.equals("You must be in a party to join the party channel!")
                            || s.equals("You're already in this channel!")
                            || s.contains("-----------------------------")),
    GUILD(
            "G",
            "/chat g",
            s ->
                    s.startsWith("Guild > ")
                            || s.startsWith("G > ")
                            || s.equals("You are now in the GUILD channel")
                            || s.equals("You must be in a guild to join the guild channel!")
                            || s.equals("You're already in this channel!")
                            || s.contains("-----------------------------")),
    PM(
            "PM",
            "",
            s -> s.startsWith("To ") || s.startsWith("From ") || s.startsWith("Friend > ")),
    COOP(
            "Coop",
            "/chat skyblock-coop",
            s ->
                    s.startsWith("Co-op > ")
                            || s.equals("You are now in the SKYBLOCK CO-OP channel")
                            || s.equals("You're already in this channel!")
                            || s.contains("-----------------------------"));

    private final String label;
    private final String command;
    private final Predicate<String> filter;

    ChatTab(String label, String command, Predicate<String> filter) {
        this.label = label;
        this.command = command;
        this.filter = filter;
    }

    public String label() {
        return label;
    }

    public String command() {
        return command;
    }

    /** Returns {@code true} if the plain-text message belongs to this channel. */
    public boolean matches(String plainText) {
        return filter.test(plainText);
    }
}