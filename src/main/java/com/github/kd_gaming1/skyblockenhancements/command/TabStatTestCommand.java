package com.github.kd_gaming1.skyblockenhancements.command;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;

import com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements;
import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.util.tab.SkyblockStats;
import com.github.kd_gaming1.skyblockenhancements.util.tab.TabListMonitor;
import com.github.kd_gaming1.skyblockenhancements.util.tab.TabStatParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;

/**
 * In-game debug commands for verifying the tab stat parser against live
 * Hypixel data. Every subcommand prints to both chat and the log so you
 * can compare what the parser thinks with what you see on your screen.
 *
 * <p>All commands are gated behind {@code devMode}.
 *
 * <table>
 *   <tr><td>{@code /sbe tabstats}</td><td>All parsed stats (key = value)</td></tr>
 *   <tr><td>{@code /sbe tabstats raw}</td><td>Raw tab lines, untouched</td></tr>
 *   <tr><td>{@code /sbe tabstats normalize}</td><td>Each line after colour/icon/whitespace cleanup</td></tr>
 *   <tr><td>{@code /sbe tabstats mining}</td><td>Mining-speed specific report</td></tr>
 *   <tr><td>{@code /sbe tabstats find <text>}</td><td>Show lines containing {@code text}</td></tr>
 * </table>
 */
public final class TabStatTestCommand {

    private static final String P = "§a[TabStats] ";
    private static final String E = "§c[TabStats] ";
    private static final String G = "§7[TabStats] ";

    private TabStatTestCommand() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            var root = literal("skyblockenhancements")
                            .then(literal("tabstats")
                                    .executes(TabStatTestCommand::cmdStats)
                                    .then(literal("raw")
                                            .executes(TabStatTestCommand::cmdRaw))
                                    .then(literal("normalize")
                                            .executes(TabStatTestCommand::cmdNormalize))
                                    .then(literal("mining")
                                            .executes(TabStatTestCommand::cmdMining))
                                    .then(literal("find")
                                            .then(argument("text", StringArgumentType.greedyString())
                                                    .executes(TabStatTestCommand::cmdFind))));

            var rootNode = dispatcher.register(root);
            dispatcher.register(literal("sbe").redirect(rootNode));
        });
    }

    // ── /sbe tabstats ─────────────────────────────────────────────────────────

    private static int cmdStats(CommandContext<FabricClientCommandSource> ctx) {
        if (!checkDevMode(ctx)) return 0;
        if (!checkReady(ctx)) return 0;

        Map<String, String> all = SkyblockStats.getAllStats();
        header(ctx, "Parsed §a" + all.size() + " §fstats");

        for (Map.Entry<String, String> e : all.entrySet()) {
            line(ctx, "  §7" + e.getKey() + " §f= §7" + e.getValue());
        }

        // Mining speed highlighted separately since it's the primary use-case.
        SkyblockStats.getMiningSpeed().ifPresentOrElse(
                ms -> info(ctx, "Mining Speed = §6" + ms),
                () -> warn(ctx, "Mining Speed §cnot found in tab list")
        );
        return 1;
    }

    // ── /sbe tabstats raw ─────────────────────────────────────────────────────

    private static int cmdRaw(CommandContext<FabricClientCommandSource> ctx) {
        if (!checkDevMode(ctx)) return 0;

        List<String> raw = TabListMonitor.getRawLines();
        if (raw.isEmpty()) {
            warn(ctx, "No tab lines captured yet.");
            return 0;
        }

        header(ctx, "Raw tab lines (" + raw.size() + "):");
        for (int i = 0; i < raw.size(); i++) {
            line(ctx, String.format("  §7[%02d] §r%s", i, raw.get(i)));
        }
        return 1;
    }

    // ── /sbe tabstats normalize ───────────────────────────────────────────────

    private static int cmdNormalize(CommandContext<FabricClientCommandSource> ctx) {
        if (!checkDevMode(ctx)) return 0;

        List<String> raw = TabListMonitor.getRawLines();
        if (raw.isEmpty()) {
            warn(ctx, "No tab lines captured yet.");
            return 0;
        }

        header(ctx, "Normalization pipeline (raw → clean):");
        for (int i = 0; i < raw.size(); i++) {
            String r = raw.get(i);
            String n = TabStatParser.normalize(r);
            if (n.isEmpty()) continue; // skip blank/decorative lines

            line(ctx, String.format("  §7[%02d] §r%s", i, r));
            line(ctx, "       §8→ §7" + n);

            // If the cleaned line looks like a stat, show what was extracted.
            var pair = TabStatParser.tryExtractPair(n);
            if (pair.isPresent()) {
                line(ctx, "       §8→ §ageneric: §7" + pair.get().getKey() + " §f= §7" + pair.get().getValue());
            }
        }
        return 1;
    }

    // ── /sbe tabstats mining ──────────────────────────────────────────────────

    private static int cmdMining(CommandContext<FabricClientCommandSource> ctx) {
        if (!checkDevMode(ctx)) return 0;
        if (!checkReady(ctx)) return 0;

        header(ctx, "Mining Speed Report");

        boolean has = SkyblockStats.hasMiningSpeed();
        info(ctx, "Has mining_speed stat … " + (has ? "§ayes" : "§cno"));

        SkyblockStats.getMiningSpeed().ifPresentOrElse(
                ms -> {
                    info(ctx, "Raw value   … §7" + SkyblockStats.getString("mining_speed").orElse("?"));
                    info(ctx, "As integer  … §6" + ms);
                },
                () -> warn(ctx, "Mining Speed not found — check tab list contains the stat")
        );

        // Show every line that mentions "mining" so the user can compare.
        List<String> raw = TabListMonitor.getRawLines();
        info(ctx, "Lines containing 'mining':");
        boolean found = false;
        for (int i = 0; i < raw.size(); i++) {
            if (raw.get(i).toLowerCase().contains("mining")) {
                line(ctx, String.format("  §7[%02d] §r%s", i, raw.get(i)));
                String n = TabStatParser.normalize(raw.get(i));
                if (!n.isEmpty()) line(ctx, "       §8→ §7" + n);
                found = true;
            }
        }
        if (!found) warn(ctx, "No line contains 'mining' — stat may use a different label");

        return 1;
    }

    // ── /sbe tabstats find <text> ─────────────────────────────────────────────

    private static int cmdFind(CommandContext<FabricClientCommandSource> ctx) {
        if (!checkDevMode(ctx)) return 0;

        String query = StringArgumentType.getString(ctx, "text").toLowerCase();
        List<String> raw = TabListMonitor.getRawLines();
        if (raw.isEmpty()) {
            warn(ctx, "No tab lines captured yet.");
            return 0;
        }

        header(ctx, "Lines containing '\u00a7f" + query + "\u00a7f':");
        int hits = 0;
        for (int i = 0; i < raw.size(); i++) {
            if (raw.get(i).toLowerCase().contains(query)) {
                line(ctx, String.format("  §7[%02d] §r%s", i, raw.get(i)));
                hits++;
            }
        }
        if (hits == 0) warn(ctx, "No matches.");
        return 1;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static boolean checkDevMode(CommandContext<FabricClientCommandSource> ctx) {
        if (SkyblockEnhancementsConfig.devMode) return true;
        ctx.getSource().sendError(Component.literal(E + "Dev mode only. Enable in config."));
        return false;
    }

    private static boolean checkReady(CommandContext<FabricClientCommandSource> ctx) {
        if (SkyblockStats.isReady()) return true;
        ctx.getSource().sendError(Component.literal(
                E + "No stats parsed yet. Join SkyBlock, then retry."));
        return false;
    }

    private static void header(CommandContext<FabricClientCommandSource> ctx, String msg) {
        ctx.getSource().sendFeedback(Component.literal(P + "§f" + msg));
        SkyblockEnhancements.LOGGER.info("[TabStats] {}", msg.replaceAll("§.", ""));
    }

    private static void info(CommandContext<FabricClientCommandSource> ctx, String msg) {
        ctx.getSource().sendFeedback(Component.literal(G + msg));
        SkyblockEnhancements.LOGGER.info("[TabStats] {}", msg.replaceAll("§.", ""));
    }

    private static void warn(CommandContext<FabricClientCommandSource> ctx, String msg) {
        ctx.getSource().sendFeedback(Component.literal(E + msg));
        SkyblockEnhancements.LOGGER.warn("[TabStats] {}", msg.replaceAll("§.", ""));
    }

    private static void line(CommandContext<FabricClientCommandSource> ctx, String msg) {
        ctx.getSource().sendFeedback(Component.literal(msg));
        // Lines are chat-only; avoid spamming the log with raw colour codes.
    }
}