package com.github.kd_gaming1.skyblockenhancements.feature.katreminder;

import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses server game messages in the "[NPC] Name: Dialog" format and forwards
 * dialogs for explicitly tracked NPCs.
 */
public class NpcDialogWatcher {
    // Captures: 1) NPC name, 2) spoken dialog line.
    private static final Pattern NPC_MESSAGE_PATTERN = Pattern.compile("^\\[NPC\\]\\s+([^:]+):\\s*(.+)$");
    // Strip legacy Minecraft formatting codes like section-sign + format char.
    private static final Pattern LEGACY_FORMAT_CODE_PATTERN = Pattern.compile("(?i)\\u00A7[0-9A-FK-OR]");

    private final Set<String> trackedNpcs = new HashSet<>();
    private final BiConsumer<String, String> dialogHandler;

    public NpcDialogWatcher(BiConsumer<String, String> dialogHandler, String... npcNames) {
        this.dialogHandler = dialogHandler;
        this.trackedNpcs.addAll(Arrays.asList(npcNames));
    }

    public void addTrackedNpc(String npcName) {
        trackedNpcs.add(npcName);
    }

    public void onGameMessage(Component message) {
        // Convert formatted chat component to plain text for regex matching.
        String raw = LEGACY_FORMAT_CODE_PATTERN.matcher(message.getString()).replaceAll("");
        Matcher matcher = NPC_MESSAGE_PATTERN.matcher(raw);
        if (!matcher.matches()) return;

        String npcName = matcher.group(1).trim();
        // Ignore NPCs that are not explicitly registered.
        if (!trackedNpcs.contains(npcName)) return;

        String npcDialog = matcher.group(2).trim();
        dialogHandler.accept(npcName, npcDialog);
    }
}
