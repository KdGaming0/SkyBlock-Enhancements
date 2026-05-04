package com.github.kd_gaming1.skyblockenhancements.command;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

import com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements;
import com.github.kd_gaming1.skyblockenhancements.feature.storage.StorageFeature;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.RrvCompat;
import com.github.kd_gaming1.skyblockenhancements.compat.rrv.injection.DataReadinessTracker;
import com.github.kd_gaming1.skyblockenhancements.feature.missingenchants.MissingEnchants;
import com.github.kd_gaming1.skyblockenhancements.repo.DownloadSession;
import com.github.kd_gaming1.skyblockenhancements.repo.item.ItemStackBuilder;
import com.github.kd_gaming1.skyblockenhancements.repo.neu.NeuItemRegistry;
import com.github.kd_gaming1.skyblockenhancements.repo.io.AtomicFileWriter;
import com.github.kd_gaming1.skyblockenhancements.repo.network.JsonHttpClient;
import com.github.kd_gaming1.skyblockenhancements.util.JsonLookup;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.net.http.HttpClient;
import java.nio.file.Path;
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
                    .then(literal("refresh")
                            .then(literal("repoData")
                                    .executes(Commands::executeRefreshRepoData)))
                    .then(literal("storage")
                            .then(literal("clear-cache")
                                    .executes(Commands::executeClearStorageCache)));

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

    private static final String ENCHANTS_URL =
            "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/constants/enchants.json";

    private static void refreshEnchantsData(CommandContext<FabricClientCommandSource> ctx) {
        try {
            JsonHttpClient client = new JsonHttpClient(HttpClient.newHttpClient(), new GsonBuilder().create());
            String text = client.getString(ENCHANTS_URL);
            if (text != null) {
                Path target = net.fabricmc.loader.api.FabricLoader.getInstance()
                        .getConfigDir()
                        .resolve(SkyblockEnhancements.MOD_ID)
                        .resolve("data")
                        .resolve("constants")
                        .resolve("enchants.json");
                AtomicFileWriter.writeString(target, text);
            } else {
                ctx.getSource().sendError(Component.literal(PREFIX_ERROR + "Failed to download enchants data."));
            }
        } catch (Exception e) {
            SkyblockEnhancements.LOGGER.error("Failed to refresh enchants data", e);
            ctx.getSource().sendError(Component.literal(PREFIX_ERROR + "Failed to refresh enchants data."));
        }
    }

    private static int executeClearStorageCache(CommandContext<FabricClientCommandSource> ctx) {
        StorageFeature.clearCache();
        ctx.getSource().sendFeedback(Component.literal(PREFIX + "Storage cache cleared."));
        return 1;
    }

    private static int executeRefreshRepoData(CommandContext<FabricClientCommandSource> ctx) {
        ctx.getSource().sendFeedback(
                Component.literal("§e[Skyblock Enhancements] Refreshing repository data..."));

        refreshEnchantsData(ctx);

        JsonLookup.clearCache();
        MissingEnchants.invalidateRepoDataCaches();

        if (RrvCompat.isActive()) {
            ctx.getSource().sendFeedback(Component.literal(PREFIX + "Refreshing NEU item repo..."));
            ItemStackBuilder.clearCache();
            DownloadSession session = SkyblockEnhancements.getInstance().getRepoDownloader().refresh();
            DataReadinessTracker.waitAndInject(session).thenRun(() -> {
                ctx.getSource().sendFeedback(
                        Component.literal(PREFIX + "Item repo refreshed ("
                                + NeuItemRegistry.getAll().size() + " items)")
                );
            });
        }

        ctx.getSource().sendFeedback(
                Component.literal(PREFIX + "Repository data refreshed successfully!"));
        return 1;
    }
}