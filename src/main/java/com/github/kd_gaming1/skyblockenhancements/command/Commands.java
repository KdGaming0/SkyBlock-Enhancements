package com.github.kd_gaming1.skyblockenhancements.command;

import com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements;
import com.github.kd_gaming1.skyblockenhancements.util.JsonLookup;
import com.github.kd_gaming1.skyblockenhancements.util.NeuRepoCache;
import com.mojang.brigadier.context.CommandContext;
import eu.midnightdust.lib.config.MidnightConfig;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class Commands {
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(literal("skyblockenhancements")
                .executes(Commands::executeOpenConfig)
                .then(literal("config")
                        .executes(Commands::executeOpenConfig))
                .then(literal("refresh")
                        .then(literal("repoData")
                            .executes(Commands::executeRefreshRepoData)))
        ));
    }

    /**
     * Opens the configuration menu.
     * Uses client.send() to delay opening until after the chat closes.
     */
    private static int executeOpenConfig(CommandContext<FabricClientCommandSource> ctx) {
        Minecraft client = Minecraft.getInstance();

        if (client.player == null) {
            sendError(ctx);
            return 0;
        }

        // Schedule GUI opening on the next tick (after chat closes)
        client.schedule(() -> {
            try {
                client.setScreen(MidnightConfig.getScreen(client.screen, SkyblockEnhancements.MOD_ID));
            } catch (Exception e) {
                SkyblockEnhancements.LOGGER.error("Failed to open config menu", e);
            }
        });

        sendSuccess(ctx);
        return 1;
    }

    private static int executeRefreshRepoData(CommandContext<FabricClientCommandSource> ctx) {
        ctx.getSource().sendFeedback(Component.literal("§e[Skyblock Enhancements] Refreshing repository data..."));

        NeuRepoCache cache = new NeuRepoCache();

        // Refresh the enchants data file (add new line for new files)
        cache.refresh("constants/enchants.json");

        JsonLookup.clearCache();

        ctx.getSource().sendFeedback(Component.literal("§a[Skyblock Enhancements] Repository data refreshed successfully!"));
        return 1;
    }


    /**
     * Sends a success message to the player.
     */
    private static void sendSuccess(CommandContext<FabricClientCommandSource> ctx) {
        ctx.getSource().sendFeedback(Component.literal("§a[Skyblock Enhancements] " + "Opening configuration menu..."));
    }

    /**
     * Sends an error message to the player.
     */
    private static void sendError(CommandContext<FabricClientCommandSource> ctx) {
        ctx.getSource().sendError(Component.literal("§c[Skyblock Enhancements]] " + "You must be in-game to open the config menu."));
    }


}
