package com.github.kd_gaming1.skyblockenhancements.command;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

import com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.RrvCompat;
import com.github.kd_gaming1.skyblockenhancements.feature.missingenchants.MissingEnchants;
import com.github.kd_gaming1.skyblockenhancements.repo.item.ItemStackBuilder;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItemRegistry;
import com.github.kd_gaming1.skyblockenhancements.util.JsonLookup;
import com.github.kd_gaming1.skyblockenhancements.util.NeuRepoCache;
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
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(literal("skyblockenhancements")
                        .executes(Commands::executeOpenConfig)
                        .then(literal("config")
                                .executes(Commands::executeOpenConfig))
                        .then(literal("refresh")
                                .then(literal("repoData")
                                        .executes(Commands::executeRefreshRepoData)))));
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

    private static int executeRefreshRepoData(CommandContext<FabricClientCommandSource> ctx) {
        ctx.getSource().sendFeedback(
                Component.literal("§e[Skyblock Enhancements] Refreshing repository data..."));

        NeuRepoCache cache = new NeuRepoCache();
        cache.refresh("constants/enchants.json");

        JsonLookup.clearCache();
        MissingEnchants.invalidateRepoDataCaches();

        if (RrvCompat.isActive()) {
            ctx.getSource().sendFeedback(Component.literal(PREFIX + "Refreshing NEU item repo..."));
            ItemStackBuilder.clearCache();
            SkyblockEnhancements.getInstance().getRepoDownloader().refresh().thenRun(() ->
                    ctx.getSource().sendFeedback(
                            Component.literal(PREFIX + "Item repo refreshed ("
                                    + NeuItemRegistry.getAll().size() + " items)")));
        }

        ctx.getSource().sendFeedback(
                Component.literal(PREFIX + "Repository data refreshed successfully!"));
        return 1;
    }
}