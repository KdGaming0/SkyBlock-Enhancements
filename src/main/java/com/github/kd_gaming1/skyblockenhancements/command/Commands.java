package com.github.kd_gaming1.skyblockenhancements.command;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

import com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements;
import com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig;
import com.github.kd_gaming1.skyblockenhancements.util.ItemDebugHelper;
import com.github.kd_gaming1.skyblockenhancements.util.tab.SkyblockStats;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import eu.midnightdust.lib.config.MidnightConfig;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class Commands {

    private static final String PREFIX = "§a[Skyblock Enhancements] ";
    private static final String PREFIX_ERROR = "§c[Skyblock Enhancements] ";

    private Commands() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            var root = literal("skyblockenhancements")
                    .executes(Commands::executeOpenConfig)
                    .then(literal("config")
                            .executes(Commands::executeOpenConfig))
                    .then(literal("debugitem")
                            .executes(Commands::executeDebugItem))
                    .then(literal("ignore_tab_stat")
                            .then(argument("stat", StringArgumentType.word())
                                    .executes(Commands::executeIgnoreTabStat)));

            var rootNode = dispatcher.register(root);
            dispatcher.register(literal("sbe").redirect(rootNode));
        });
    }

    /**
     * Opens the MidnightConfig GUI. Scheduled to the next tick so the chat screen
     * closes before the config screen opens — otherwise Minecraft drops the screen immediately.
     */
    private static int executeOpenConfig(CommandContext<FabricClientCommandSource> ctx) {
        Minecraft client = Minecraft.getInstance();

        if (client.player == null) {
            ctx.getSource().sendError(
                    Component.literal(PREFIX_ERROR + "You must be in-game to open the config menu."));
            return 0;
        }

        client.schedule(() -> {
            try {
                client.setScreen(MidnightConfig.getScreen(client.screen, SkyblockEnhancements.MOD_ID));
            } catch (Exception e) {
                SkyblockEnhancements.LOGGER.error("Failed to open config menu", e);
            }
        });

        ctx.getSource().sendFeedback(Component.literal(PREFIX + "Opening configuration menu..."));
        return 1;
    }

    private static int executeDebugItem(CommandContext<FabricClientCommandSource> ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;

        // Use the stack the player is currently holding in main hand
        var stack = mc.player.getMainHandItem();
        if (stack.isEmpty()) {
            // Fall back to offhand
            stack = mc.player.getOffhandItem();
        }

        ItemDebugHelper.dumpToChat(stack);
        return 1;
    }

    private static int executeIgnoreTabStat(CommandContext<FabricClientCommandSource> ctx) {
        String stat = StringArgumentType.getString(ctx, "stat");
        SkyblockStats.ignoreDemand(stat);
        ctx.getSource().sendFeedback(Component.literal(PREFIX + "Ignored missing-stat warnings for " + stat + " this session."));
        return 1;
    }
}
