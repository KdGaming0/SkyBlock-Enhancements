package com.github.kd_gaming1.skyblockenhancements.feature.chat.tabs;

import java.util.function.Predicate;

/**
 * Named, self-contained predicates describing which messages belong to each Hypixel channel.
 *
 * <p>Generic status messages ("You're already in this channel!" etc.) are deliberately NOT
 * matched by any channel predicate: they are identical across channels and would otherwise
 * leak into the wrong tab. They remain visible in the ALL tab.
 */
public final class ChatTabFilters {

    private ChatTabFilters() {}

    public static final Predicate<String> ALL = s -> true;

    public static final Predicate<String> PARTY = ChatTabFilters::isPartyMessage;
    public static final Predicate<String> GUILD = ChatTabFilters::isGuildMessage;
    public static final Predicate<String> PRIVATE_MESSAGE = ChatTabFilters::isPrivateMessage;
    public static final Predicate<String> COOP = ChatTabFilters::isCoopMessage;

    // ── Party ────────────────────────────────────────────────────────────

    private static boolean isPartyMessage(String s) {
        return hasChannelPrefix(s, "Party > ", "P > ")
                || isPartyLifecycle(s)
                || isPartyMembership(s)
                || isPartyChannelStatus(s);
    }

    private static boolean isPartyLifecycle(String s) {
        return s.contains("has invited you to join their party!")
                || s.contains("to the party! They have 60 seconds to accept.")
                || s.contains("has disbanded the party!")
                || s.startsWith("The party was transferred to ")
                || s.equals("The party was disbanded because all invites expired "
                + "and the party was empty");
    }

    private static boolean isPartyMembership(String s) {
        return s.endsWith("joined the party.")
                || s.endsWith("has left the party.")
                || s.endsWith("has been removed from the party.");
    }

    private static boolean isPartyChannelStatus(String s) {
        return s.equals("You are now in the PARTY channel")
                || s.equals("You must be in a party to join the party channel!");
    }

    // ── Guild ────────────────────────────────────────────────────────────

    private static boolean isGuildMessage(String s) {
        return hasChannelPrefix(s, "Guild > ", "G > ")
                || s.equals("You are now in the GUILD channel");
    }

    // ── PM ───────────────────────────────────────────────────────────────

    private static boolean isPrivateMessage(String s) {
        return s.startsWith("To ") || s.startsWith("From ") || s.startsWith("Friend > ");
    }

    // ── Co-op ────────────────────────────────────────────────────────────

    private static boolean isCoopMessage(String s) {
        return s.startsWith("Co-op > ")
                || s.equals("You are now in the SKYBLOCK CO-OP channel");
    }

    // ── Utilities ────────────────────────────────────────────────────────

    private static boolean hasChannelPrefix(String s, String... prefixes) {
        for (String prefix : prefixes) {
            if (s.startsWith(prefix)) return true;
        }
        return false;
    }
}