package com.github.kd_gaming1.skyblockenhancements.command;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

import com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements;
import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.util.HypixelLocationState;
import com.github.kd_gaming1.skyblockenhancements.util.NetworkStats;
import com.github.kd_gaming1.skyblockenhancements.util.StatValueType;
import com.github.kd_gaming1.skyblockenhancements.util.tab.SkyblockStats;
import com.github.kd_gaming1.skyblockenhancements.util.tab.TabListMonitor;
import com.github.kd_gaming1.skyblockenhancements.util.tab.TabStatParser;
import com.github.kd_gaming1.skyblockenhancements.util.tool.*;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;

/**
 * Unified debug command system for Skyblock Enhancements.
 *
 * Commands:
 *
 * /sbe tabstats
 * /sbe tabstats raw
 * /sbe tabstats normalize
 * /sbe tabstats mining
 * /sbe tabstats find <text>
 *
 * /sbe network
 *
 * /sbe tool
 * /sbe tool type
 * /sbe tool stats
 * /sbe tool id
 * /sbe tool lore
 */
public final class DebugCommand {

    private static final String TAB_P = "§a[TabStats] ";
    private static final String TAB_E = "§c[TabStats] ";
    private static final String TAB_G = "§7[TabStats] ";

    private static final String TOOL_P = "§a[Tool] ";
    private static final String TOOL_E = "§c[Tool] ";
    private static final String TOOL_G = "§7[Tool] ";

    private DebugCommand() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {

            var root = literal("skyblockenhancements")

                    // ── TAB STATS ──────────────────────────────────────────
                    .then(literal("tabstats")
                            .executes(DebugCommand::cmdTabStats)

                            .then(literal("raw")
                                    .executes(DebugCommand::cmdRaw))

                            .then(literal("normalize")
                                    .executes(DebugCommand::cmdNormalize))

                            .then(literal("mining")
                                    .executes(DebugCommand::cmdMining))

                            .then(literal("find")
                                    .then(argument("text", StringArgumentType.greedyString())
                                            .executes(DebugCommand::cmdFind))))

                    // ── NETWORK ───────────────────────────────────────────
                    .then(literal("network")
                            .executes(DebugCommand::cmdNetwork))

                    // ── TOOL ──────────────────────────────────────────────
                    .then(literal("tool")
                            .executes(DebugCommand::cmdTool)

                            .then(literal("dump")
                                    .executes(DebugCommand::cmdToolDump))

                            .then(literal("type")
                                    .executes(DebugCommand::cmdToolType))

                            .then(literal("stats")
                                    .executes(DebugCommand::cmdToolStats))

                            .then(literal("id")
                                    .executes(DebugCommand::cmdToolId))

                            .then(literal("lore")
                                    .executes(DebugCommand::cmdToolLore)))

                    .then(literal("debug")
                            .then(literal("location")
                                    .executes(DebugCommand::cmdLocationDebug)));

            var rootNode = dispatcher.register(root);

            dispatcher.register(
                    literal("sbe")
                            .redirect(rootNode)
            );
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TAB STATS
    // ─────────────────────────────────────────────────────────────────────────

    private static int cmdTabStats(CommandContext<FabricClientCommandSource> ctx) {
        if (!checkDevMode(ctx, TAB_E)) return 0;
        if (!checkReady(ctx)) return 0;

        Map<String, String> all = SkyblockStats.getAllStats();

        header(ctx, TAB_P, "Parsed §a" + all.size() + " §fstats");

        for (Map.Entry<String, String> e : all.entrySet()) {
            line(ctx, "  §7" + e.getKey() + " §f= §7" + e.getValue());
        }

        SkyblockStats.getMiningSpeed().ifPresentOrElse(
                ms -> info(ctx, TAB_G, "Mining Speed = §6" + ms),
                () -> warn(ctx, TAB_E, "Mining Speed §cnot found in tab list")
        );

        return 1;
    }

    private static int cmdRaw(CommandContext<FabricClientCommandSource> ctx) {
        if (!checkDevMode(ctx, TAB_E)) return 0;

        List<String> raw = TabListMonitor.getRawLines();

        if (raw.isEmpty()) {
            warn(ctx, TAB_E, "No tab lines captured yet.");
            return 0;
        }

        header(ctx, TAB_P, "Raw tab lines (" + raw.size() + "):");

        for (int i = 0; i < raw.size(); i++) {
            line(ctx, String.format("  §7[%02d] §r%s", i, raw.get(i)));
        }

        return 1;
    }

    private static int cmdNormalize(CommandContext<FabricClientCommandSource> ctx) {
        if (!checkDevMode(ctx, TAB_E)) return 0;

        List<String> raw = TabListMonitor.getRawLines();

        if (raw.isEmpty()) {
            warn(ctx, TAB_E, "No tab lines captured yet.");
            return 0;
        }

        header(ctx, TAB_P, "Normalization pipeline (raw → clean):");

        for (int i = 0; i < raw.size(); i++) {
            String r = raw.get(i);
            String n = TabStatParser.normalize(r);

            if (n.isEmpty()) continue;

            line(ctx, String.format("  §7[%02d] §r%s", i, r));
            line(ctx, "       §8→ §7" + n);

            var pair = TabStatParser.tryExtractPair(n);

            if (pair.isPresent()) {
                line(ctx,
                        "       §8→ §ageneric: §7"
                                + pair.get().getKey()
                                + " §f= §7"
                                + pair.get().getValue());
            }
        }

        return 1;
    }

    private static int cmdMining(CommandContext<FabricClientCommandSource> ctx) {
        if (!checkDevMode(ctx, TAB_E)) return 0;
        if (!checkReady(ctx)) return 0;

        header(ctx, TAB_P, "Mining Speed Report");

        boolean has = SkyblockStats.hasMiningSpeed();

        info(ctx, TAB_G, "Has mining_speed stat … " + (has ? "§ayes" : "§cno"));

        SkyblockStats.getMiningSpeed().ifPresentOrElse(
                ms -> {
                    info(ctx, TAB_G,
                            "Raw value   … §7"
                                    + SkyblockStats.getString("mining_speed").orElse("?"));

                    info(ctx, TAB_G, "As integer  … §6" + ms);
                },
                () -> warn(ctx, TAB_E,
                        "Mining Speed not found — check tab list contains the stat")
        );

        List<String> raw = TabListMonitor.getRawLines();

        info(ctx, TAB_G, "Lines containing 'mining':");

        boolean found = false;

        for (int i = 0; i < raw.size(); i++) {
            if (raw.get(i).toLowerCase().contains("mining")) {

                line(ctx, String.format("  §7[%02d] §r%s", i, raw.get(i)));

                String n = TabStatParser.normalize(raw.get(i));

                if (!n.isEmpty()) {
                    line(ctx, "       §8→ §7" + n);
                }

                found = true;
            }
        }

        if (!found) {
            warn(ctx, TAB_E,
                    "No line contains 'mining' — stat may use a different label");
        }

        return 1;
    }

    private static int cmdFind(CommandContext<FabricClientCommandSource> ctx) {
        if (!checkDevMode(ctx, TAB_E)) return 0;

        String query = StringArgumentType.getString(ctx, "text").toLowerCase();

        List<String> raw = TabListMonitor.getRawLines();

        if (raw.isEmpty()) {
            warn(ctx, TAB_E, "No tab lines captured yet.");
            return 0;
        }

        header(ctx, TAB_P, "Lines containing '\u00a7f" + query + "\u00a7f':");

        int hits = 0;

        for (int i = 0; i < raw.size(); i++) {
            if (raw.get(i).toLowerCase().contains(query)) {
                line(ctx, String.format("  §7[%02d] §r%s", i, raw.get(i)));
                hits++;
            }
        }

        if (hits == 0) {
            warn(ctx, TAB_E, "No matches.");
        }

        return 1;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // NETWORK
    // ─────────────────────────────────────────────────────────────────────────

    private static int cmdNetwork(CommandContext<FabricClientCommandSource> ctx) {
        if (!checkDevMode(ctx, TAB_E)) return 0;

        int ping = NetworkStats.getPingMs();
        double tps = NetworkStats.getTps();

        int tpsSamples = NetworkStats.getTpsSampleCount();
        boolean tpsReady = NetworkStats.hasEnoughData();

        header(ctx, TAB_P, "Network Stats");

        info(ctx, TAB_G,
                "Ping  … "
                        + (ping >= 0
                        ? "§6" + ping + " §7ms"
                        : "§cno connection"));

        info(ctx, TAB_G,
                "TPS   … "
                        + (tpsReady
                        ? tpsColor(tps) + String.format("%.2f", tps)
                        : "§7gathering… §8(" + tpsSamples + "/20)"));

        return 1;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TOOL
    // ─────────────────────────────────────────────────────────────────────────

    private static int cmdTool(CommandContext<FabricClientCommandSource> ctx) {
        if (!checkDevMode(ctx, TOOL_E)) return 0;

        ToolInfo info = ToolInspector.inspectHeld();

        if (info.isUnknown()) {
            info(ctx, TOOL_G, "Held item: §7" + info.getDisplayName());
            warn(ctx, TOOL_E,
                    "Not recognised as a tool (type = UNKNOWN)");
            return 1;
        }

        header(ctx, TOOL_P,
                "Tool Inspection §7— §f" + info.getDisplayName());

        info(ctx, TOOL_G, "Type        … §6" + info.getToolType());

        info(ctx, TOOL_G,
                "SkyBlock ID … §7"
                        + (info.getSkyblockId().isEmpty()
                        ? "<none>"
                        : info.getSkyblockId()));

        info(ctx, TOOL_G,
                "Is mining   … "
                        + (info.isMiningTool() ? "§ayes" : "§7no"));

        info(ctx, TOOL_G,
                "Is farming  … "
                        + (info.isFarmingTool() ? "§ayes" : "§7no"));

        info(ctx, TOOL_G,
                "Stats found … §a" + info.getStatCount());

        if (info.hasStat(ToolStat.MINING_SPEED)) {
            info.getInt(ToolStat.MINING_SPEED)
                    .ifPresent(v ->
                            info(ctx, TOOL_G,
                                    "Mining Speed    … §6" + v));
        }

        if (info.hasStat(ToolStat.BREAKING_POWER)) {
            info.getInt(ToolStat.BREAKING_POWER)
                    .ifPresent(v ->
                            info(ctx, TOOL_G,
                                    "Breaking Power  … §6" + v));
        }

        if (info.hasStat(ToolStat.MINING_FORTUNE)) {
            info.getInt(ToolStat.MINING_FORTUNE)
                    .ifPresent(v ->
                            info(ctx, TOOL_G,
                                    "Mining Fortune  … §6" + v));
        }

        if (info.hasStat(ToolStat.DAMAGE)) {
            info.getInt(ToolStat.DAMAGE)
                    .ifPresent(v ->
                            info(ctx, TOOL_G,
                                    "Damage          … §c" + v));
        }

        return 1;
    }

    private static int cmdToolDump(CommandContext<FabricClientCommandSource> ctx) {
        if (!checkDevMode(ctx, TOOL_E)) return 0;

        var mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        var held = mc.player.getMainHandItem();

        if (held.isEmpty()) {
            warn(ctx, TOOL_E, "Not holding anything.");
            return 0;
        }

        ToolInfo info = ToolInspector.inspect(held);

        header(ctx, TOOL_P, "=== TOOL DUMP ===");
        info(ctx, TOOL_G, "Item: §f" + info.getDisplayName());
        info(ctx, TOOL_G, "Type: §6" + info.getToolType());
        info(ctx, TOOL_G, "SkyBlock ID: §e" + (info.getSkyblockId().isEmpty() ? "N/A" : info.getSkyblockId()));
        info(ctx, TOOL_G, "Stats Bitmask: §b" + Long.toBinaryString(info.getPresentStatsMask()));

        info(ctx, TOOL_G, "--- Extracted Stats ---");
        for (ToolStat stat : ToolStat.VALUES) {
            if (info.hasStat(stat)) {
                if (stat.valueType() == StatValueType.FLOAT) {
                    info(ctx, TOOL_G, "  §7" + stat.key() + " = §a" + info.getDouble(stat, 0.0));
                } else {
                    info(ctx, TOOL_G, "  §7" + stat.key() + " = §a" + info.getInt(stat, 0));
                }
            }
        }

        // ── Raw Component & NBT Data Dump ─────────────────────────────────────
        net.minecraft.world.item.component.CustomData customData = held.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (customData != null) {
            info(ctx, TOOL_G, "--- Custom Data (NBT Root) ---");
            CompoundTag root = customData.copyTag();
            line(ctx, "  §b" + root.toString());

            // Unpack ExtraAttributes line-by-line for easier terminal/chat scanning
            root.getCompound("ExtraAttributes").ifPresent(extra -> {
                info(ctx, TOOL_G, "  --- ExtraAttributes Elements ---");
                for (String key : extra.keySet()) {
                    var tagValue = extra.get(key);
                    if (tagValue != null) {
                        info(ctx, TOOL_G, "    §7" + key + " = §e" + tagValue.toString());
                    }
                }
            });
        } else {
            info(ctx, TOOL_G, "--- Custom Data (NBT Root) ---");
            info(ctx, TOOL_G, "  §cNo custom_data component present on this item.");
        }

        info(ctx, TOOL_G, "--- Raw Lore ---");
        List<String> lore = ToolStatExtractor.getLoreLines(held);
        for (int i = 0; i < lore.size(); i++) {
            line(ctx, String.format(" §8[%02d] §r%s", i, lore.get(i)));
        }

        header(ctx, TOOL_P, "=================");
        return 1;
    }

    private static int cmdToolType(CommandContext<FabricClientCommandSource> ctx) {
        if (!checkDevMode(ctx, TOOL_E)) return 0;

        ToolType type = ToolDetector.detectHeldTool();

        info(ctx, TOOL_G, "Detected tool type: §6" + type);

        ToolDetector.getHeldSkyblockId().ifPresentOrElse(
                id -> info(ctx, TOOL_G, "SkyBlock ID: §7" + id),
                () -> info(ctx, TOOL_G, "SkyBlock ID: §7<none>")
        );

        return 1;
    }

    private static int cmdToolStats(CommandContext<FabricClientCommandSource> ctx) {
        if (!checkDevMode(ctx, TOOL_E)) return 0;

        ToolInfo info = ToolInspector.inspectHeld();

        Map<String, String> all = info.getAllStats();

        if (all.isEmpty()) {
            warn(ctx, TOOL_E,
                    "No stats extracted from held item.");
            return 1;
        }

        header(ctx, TOOL_P,
                "Extracted stats §7(" + all.size() + ")");

        for (Map.Entry<String, String> e : all.entrySet()) {
            line(ctx,
                    "  §7"
                            + e.getKey()
                            + " §f= §7"
                            + e.getValue());
        }

        return 1;
    }

    private static int cmdToolId(CommandContext<FabricClientCommandSource> ctx) {
        if (!checkDevMode(ctx, TOOL_E)) return 0;

        ToolDetector.getHeldSkyblockId().ifPresentOrElse(
                id -> info(ctx, TOOL_G,
                        "SkyBlock ID: §6" + id),
                () -> warn(ctx, TOOL_E,
                        "Held item has no SkyBlock ID (not a SkyBlock item?)")
        );

        return 1;
    }

    private static int cmdToolLore(CommandContext<FabricClientCommandSource> ctx) {
        if (!checkDevMode(ctx, TOOL_E)) return 0;

        var mc = Minecraft.getInstance();

        if (mc.player == null) {
            warn(ctx, TOOL_E, "Not in-game.");
            return 0;
        }

        var held = mc.player.getMainHandItem();

        if (held.isEmpty()) {
            warn(ctx, TOOL_E, "Not holding anything.");
            return 0;
        }

        var lines = ToolStatExtractor.getLoreLines(held);

        if (lines.isEmpty()) {
            warn(ctx, TOOL_E, "No lore on held item.");
            return 0;
        }

        header(ctx, TOOL_P,
                "Item lore §7(" + lines.size() + " lines)");

        for (int i = 0; i < lines.size(); i++) {
            line(ctx,
                    String.format("  §7[%02d] §r%s", i, lines.get(i)));
        }

        return 1;
    }

    private static int cmdLocationDebug(CommandContext<FabricClientCommandSource> ctx) {
        boolean onHypixel = HypixelLocationState.isOnHypixel();
        boolean onSkyblock = HypixelLocationState.isOnSkyblock();
        boolean onMiningIsland = HypixelLocationState.isOnMiningIsland();

        String msg = String.format("§eLocation Status: §fHypixel: %s, Skyblock: %s, Mining Island: %s",
                onHypixel ? "§aTrue" : "§cFalse",
                onSkyblock ? "§aTrue" : "§cFalse",
                onMiningIsland ? "§aTrue" : "§cFalse");

        ctx.getSource().sendFeedback(Component.literal(msg));
        return 1;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private static String tpsColor(double tps) {
        if (tps >= 19.0) return "§a";
        if (tps >= 15.0) return "§e";
        if (tps >= 10.0) return "§6";
        return "§c";
    }

    private static boolean checkDevMode(
            CommandContext<FabricClientCommandSource> ctx,
            String prefix
    ) {
        if (SkyblockEnhancementsConfig.devMode) {
            return true;
        }

        ctx.getSource().sendError(
                Component.literal(prefix + "Dev mode only. Enable in config.")
        );

        return false;
    }

    private static boolean checkReady(
            CommandContext<FabricClientCommandSource> ctx
    ) {
        if (SkyblockStats.isReady()) {
            return true;
        }

        ctx.getSource().sendError(
                Component.literal(
                        TAB_E + "No stats parsed yet. Join SkyBlock, then retry."
                )
        );

        return false;
    }

    private static void header(
            CommandContext<FabricClientCommandSource> ctx,
            String prefix,
            String msg
    ) {
        ctx.getSource().sendFeedback(
                Component.literal(prefix + "§f" + msg)
        );

        SkyblockEnhancements.LOGGER.info(
                "{}",
                msg.replaceAll("§.", "")
        );
    }

    private static void info(
            CommandContext<FabricClientCommandSource> ctx,
            String prefix,
            String msg
    ) {
        ctx.getSource().sendFeedback(
                Component.literal(prefix + msg)
        );

        SkyblockEnhancements.LOGGER.info(
                "{}",
                msg.replaceAll("§.", "")
        );
    }

    private static void warn(
            CommandContext<FabricClientCommandSource> ctx,
            String prefix,
            String msg
    ) {
        ctx.getSource().sendFeedback(
                Component.literal(prefix + msg)
        );

        SkyblockEnhancements.LOGGER.warn(
                "{}",
                msg.replaceAll("§.", "")
        );
    }

    private static void line(
            CommandContext<FabricClientCommandSource> ctx,
            String msg
    ) {
        ctx.getSource().sendFeedback(Component.literal(msg));
    }
}