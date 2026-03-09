package com.github.kd_gaming1.skyblockenhancements.command;

import com.github.kd_gaming1.skyblockenhancements.SkyblockEnhancements;
import com.github.kd_gaming1.skyblockenhancements.feature.katreminder.KatReminderFeature;
import com.github.kd_gaming1.skyblockenhancements.feature.reminder.JsonFileUtil;
import com.github.kd_gaming1.skyblockenhancements.feature.reminder.OutputType;
import com.github.kd_gaming1.skyblockenhancements.feature.reminder.Reminder;
import com.github.kd_gaming1.skyblockenhancements.feature.reminder.ReminderManager;
import com.github.kd_gaming1.skyblockenhancements.feature.reminder.RemindersFileData;
import com.github.kd_gaming1.skyblockenhancements.feature.reminder.TriggerType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * Client-side command interface for creating and managing reminders.
 *
 * <p>Commands:</p>
 * <ul>
 *   <li>/remindme create &lt;amount&gt; &lt;unit&gt; &lt;trigger&gt; &lt;output&gt; [repeat &lt;times&gt;] &lt;message&gt;</li>
 *   <li>/remindme remove &lt;id&gt; | all</li>
 *   <li>/remindme rename &lt;id&gt; &lt;name&gt;</li>
 *   <li>/remindme pause &lt;id&gt;</li>
 *   <li>/remindme resume &lt;id&gt;</li>
 *   <li>/remindme snooze &lt;id&gt; &lt;amount&gt; &lt;unit&gt;</li>
 *   <li>/remindme list</li>
 *   <li>/remindme help</li>
 * </ul>
 */
public class ReminderCommand {

    private static final SuggestionProvider<FabricClientCommandSource> TIME_UNITS = (context, builder) -> {
        for (String unit : new String[]{"seconds", "minutes", "hours", "days", "sec", "min", "hour", "day"}) {
            builder.suggest(unit);
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<FabricClientCommandSource> TRIGGERS = (context, builder) -> {
        builder.suggest("while_playing");
        builder.suggest("real_time");
        return builder.buildFuture();
    };

    private static final SuggestionProvider<FabricClientCommandSource> OUTPUT_TYPES = (context, builder) -> {
        builder.suggest("chat");
        builder.suggest("title_box");
        builder.suggest("chat_and_title");
        builder.suggest("sound_only");
        return builder.buildFuture();
    };

    private static final SuggestionProvider<FabricClientCommandSource> REPEAT_TIMES = (context, builder) -> {
        for (String s : new String[]{"until_removed", "2", "3", "5", "10"}) {
            builder.suggest(s);
        }
        return builder.buildFuture();
    };

    private static SuggestionProvider<FabricClientCommandSource> suggestReminderIds(ReminderManager reminderManager) {
        return (context, builder) -> {
            for (Reminder reminder : reminderManager.getActiveReminders()) {
                builder.suggest(reminder.getId(), Component.literal(reminder.getDisplayName()));
            }
            return builder.buildFuture();
        };
    }

    private static SuggestionProvider<FabricClientCommandSource> suggestWhilePlayingIds(ReminderManager reminderManager) {
        return (context, builder) -> {
            for (Reminder reminder : reminderManager.getActiveReminders()) {
                if (reminder.getTriggerType() == TriggerType.WHILE_PLAYING) {
                    builder.suggest(reminder.getId(), Component.literal(reminder.getDisplayName()));
                }
            }
            return builder.buildFuture();
        };
    }

    public static void register(ReminderManager reminderManager) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher
                .register(literal("remindme")
                        .then(literal("create")
                                .then(argument("amount", IntegerArgumentType.integer(1))
                                        .then(argument("unit", StringArgumentType.word())
                                                .suggests(TIME_UNITS)
                                                .then(argument("trigger", StringArgumentType.word())
                                                        .suggests(TRIGGERS)
                                                        .then(argument("output", StringArgumentType.word())
                                                                .suggests(OUTPUT_TYPES)
                                                                .then(argument("message", StringArgumentType.greedyString())
                                                                        .executes(ctx -> executeCreate(ctx, reminderManager, null, null))
                                                                )
                                                                .then(literal("repeat")
                                                                        .then(argument("times", StringArgumentType.word())
                                                                                .suggests(REPEAT_TIMES)
                                                                                .then(argument("message", StringArgumentType.greedyString())
                                                                                        .executes(ctx -> executeCreate(ctx, reminderManager,
                                                                                                StringArgumentType.getString(ctx, "times"), null))
                                                                                )
                                                                        )
                                                                )
                                                                .then(literal("name")
                                                                        .then(argument("name", StringArgumentType.word())
                                                                                .then(argument("message", StringArgumentType.greedyString())
                                                                                        .executes(ctx -> executeCreate(ctx, reminderManager, null,
                                                                                                StringArgumentType.getString(ctx, "name")))
                                                                                )
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(literal("remove")
                                .then(argument("id", IntegerArgumentType.integer(1))
                                        .suggests(suggestReminderIds(reminderManager))
                                        .executes(ctx -> executeRemove(ctx, reminderManager))
                                )
                                .then(literal("all")
                                        .executes(ctx -> executeRemoveAll(ctx, reminderManager, false))
                                )
                                .then(literal("all_confirmed")
                                        .executes(ctx -> executeRemoveAll(ctx, reminderManager, true))
                                )
                        )
                        .then(literal("rename")
                                .then(argument("id", IntegerArgumentType.integer(1))
                                        .suggests(suggestReminderIds(reminderManager))
                                        .then(argument("name", StringArgumentType.greedyString())
                                                .executes(ctx -> executeRename(ctx, reminderManager))
                                        )
                                )
                        )
                        .then(literal("pause")
                                .then(argument("id", IntegerArgumentType.integer(1))
                                        .suggests(suggestWhilePlayingIds(reminderManager))
                                        .executes(ctx -> executePause(ctx, reminderManager))
                                )
                        )
                        .then(literal("resume")
                                .then(argument("id", IntegerArgumentType.integer(1))
                                        .suggests(suggestWhilePlayingIds(reminderManager))
                                        .executes(ctx -> executeResume(ctx, reminderManager))
                                )
                        )
                        .then(literal("snooze")
                                .then(argument("id", IntegerArgumentType.integer(1))
                                        .suggests(suggestReminderIds(reminderManager))
                                        .then(argument("amount", IntegerArgumentType.integer(1))
                                                .then(argument("unit", StringArgumentType.word())
                                                        .suggests(TIME_UNITS)
                                                        .executes(ctx -> executeSnooze(ctx, reminderManager))
                                                )
                                        )
                                )
                        )
                        .then(literal("list")
                                .executes(ctx -> executeList(ctx, reminderManager))
                        )
                        .then(literal("help")
                                .executes(ReminderCommand::executeHelp)
                        )
                )
        );
    }

    // ── Command handlers ─────────────────────────────────────────────────────

    private static int executeCreate(CommandContext<FabricClientCommandSource> context,
                                     ReminderManager reminderManager,
                                     String repeatTimesArg, String name) {
        int amount = IntegerArgumentType.getInteger(context, "amount");
        String unit = StringArgumentType.getString(context, "unit");
        String triggerArg = StringArgumentType.getString(context, "trigger");
        String outputArg = StringArgumentType.getString(context, "output");
        String message = StringArgumentType.getString(context, "message");

        long multiplier = getMultiplier(unit);
        if (multiplier == -1) {
            context.getSource().sendError(Component.literal("Invalid time unit: " + unit));
            return 0;
        }

        TriggerType triggerType;
        try {
            triggerType = TriggerType.valueOf(triggerArg.toUpperCase());
        } catch (IllegalArgumentException e) {
            context.getSource().sendError(Component.literal("Invalid trigger: " + triggerArg));
            return 0;
        }

        OutputType outputType;
        try {
            outputType = OutputType.valueOf(outputArg.toUpperCase());
        } catch (IllegalArgumentException e) {
            context.getSource().sendError(Component.literal("Invalid output type: " + outputArg));
            return 0;
        }

        int repeatCount = parseRepeatCount(context, repeatTimesArg);
        if (repeatTimesArg != null && repeatCount == 0) return 0;

        long durationMs = (long) amount * multiplier;
        Reminder reminder = reminderManager.createReminder(durationMs, outputType, name, message, triggerType, repeatCount);

        context.getSource().sendFeedback(buildCreateFeedback(reminder, amount, unit, repeatTimesArg, repeatCount));
        persistReminders(reminderManager);
        return 1;
    }

    private static int executeRemove(CommandContext<FabricClientCommandSource> context, ReminderManager reminderManager) {
        int id = IntegerArgumentType.getInteger(context, "id");

        if (reminderManager.removeReminder(id)) {
            persistReminders(reminderManager);
            context.getSource().sendFeedback(
                    Component.literal("Removed reminder ").withStyle(ChatFormatting.GRAY)
                            .append(Component.literal("#" + id).withStyle(ChatFormatting.DARK_GRAY))
            );
            return 1;
        }

        context.getSource().sendError(Component.literal("Reminder #" + id + " not found"));
        return 0;
    }

    private static int executeRemoveAll(CommandContext<FabricClientCommandSource> context,
                                        ReminderManager reminderManager, boolean confirmed) {
        if (!confirmed) {
            // Show a confirmation prompt before destroying everything.
            MutableComponent prompt = Component.literal("Remove all reminders? ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("[confirm]").withStyle(style -> style
                            .withColor(ChatFormatting.RED)
                            .withClickEvent(new ClickEvent.RunCommand("/remindme remove all_confirmed"))));
            context.getSource().sendFeedback(prompt);
            return 1;
        }

        int count = reminderManager.removeAllReminders() + KatReminderFeature.removeAllReminders();
        persistReminders(reminderManager);
        context.getSource().sendFeedback(Component.literal("Removed " + count + " reminder(s)").withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static int executeRename(CommandContext<FabricClientCommandSource> context, ReminderManager reminderManager) {
        int id = IntegerArgumentType.getInteger(context, "id");
        String newName = StringArgumentType.getString(context, "name");

        if (reminderManager.renameReminder(id, newName)) {
            persistReminders(reminderManager);
            context.getSource().sendFeedback(
                    Component.literal("Renamed #" + id + " to ").withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(newName).withStyle(ChatFormatting.YELLOW))
            );
            return 1;
        }

        context.getSource().sendError(Component.literal("Reminder #" + id + " not found"));
        return 0;
    }

    private static int executePause(CommandContext<FabricClientCommandSource> context, ReminderManager reminderManager) {
        int id = IntegerArgumentType.getInteger(context, "id");

        boolean paused = reminderManager.pauseReminder(id);
        if (paused) {
            persistReminders(reminderManager);
            context.getSource().sendFeedback(
                    Component.literal("Paused reminder ").withStyle(ChatFormatting.GRAY)
                            .append(Component.literal("#" + id).withStyle(ChatFormatting.DARK_GRAY))
            );
            return 1;
        }

        // Could be not found, or REAL_TIME (not pausable)
        Reminder found = reminderManager.getActiveReminders().stream().filter(r -> r.getId() == id).findFirst().orElse(null);
        if (found == null) {
            context.getSource().sendError(Component.literal("Reminder #" + id + " not found"));
        } else {
            context.getSource().sendError(Component.literal("Real-time reminders cannot be paused"));
        }
        return 0;
    }

    private static int executeResume(CommandContext<FabricClientCommandSource> context, ReminderManager reminderManager) {
        int id = IntegerArgumentType.getInteger(context, "id");

        if (reminderManager.resumeReminder(id)) {
            persistReminders(reminderManager);
            context.getSource().sendFeedback(
                    Component.literal("Resumed reminder ").withStyle(ChatFormatting.GRAY)
                            .append(Component.literal("#" + id).withStyle(ChatFormatting.DARK_GRAY))
            );
            return 1;
        }

        context.getSource().sendError(Component.literal("Reminder #" + id + " not found or not paused"));
        return 0;
    }

    private static int executeSnooze(CommandContext<FabricClientCommandSource> context, ReminderManager reminderManager) {
        int id = IntegerArgumentType.getInteger(context, "id");
        int amount = IntegerArgumentType.getInteger(context, "amount");
        String unit = StringArgumentType.getString(context, "unit");

        long multiplier = getMultiplier(unit);
        if (multiplier == -1) {
            context.getSource().sendError(Component.literal("Invalid time unit: " + unit));
            return 0;
        }

        long extraMs = (long) amount * multiplier;

        if (reminderManager.snoozeReminder(id, extraMs)) {
            persistReminders(reminderManager);
            context.getSource().sendFeedback(
                    Component.literal("Snoozed #" + id + " for ").withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(amount + " " + unit).withStyle(ChatFormatting.AQUA))
            );
            return 1;
        }

        context.getSource().sendError(Component.literal("Reminder #" + id + " not found"));
        return 0;
    }

    private static int executeList(CommandContext<FabricClientCommandSource> context, ReminderManager reminderManager) {
        List<Reminder> reminders = reminderManager.getActiveReminders();
        var katReminders = KatReminderFeature.getActiveReminders();

        if (reminders.isEmpty() && katReminders.isEmpty()) {
            context.getSource().sendFeedback(Component.literal("No active reminders").withStyle(ChatFormatting.GRAY));
            return 0;
        }

        MutableComponent header = Component.literal("Your Active Reminders: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("[remove all]").withStyle(style -> style
                        .withColor(ChatFormatting.RED)
                        .withClickEvent(new ClickEvent.RunCommand("/remindme remove all"))));
        context.getSource().sendFeedback(header);

        for (Reminder reminder : reminders) {
            context.getSource().sendFeedback(buildReminderListLine(reminder));
        }

        for (var katReminder : katReminders) {
            long remainingMs = Math.max(0L, katReminder.readyAtMs - System.currentTimeMillis());
            MutableComponent katName = Component.literal("Kat: ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(katReminder.pet).withStyle(mapRarityColor(katReminder.rarity)));
            context.getSource().sendFeedback(buildTimerLine("#K", katName, ReminderManager.formatMs(remainingMs), "real time", null, null, null));
        }

        return 1;
    }

    private static int executeHelp(CommandContext<FabricClientCommandSource> context) {
        context.getSource().sendFeedback(Component.literal("§6§l=== RemindMe Help ==="));
        context.getSource().sendFeedback(Component.literal("§e/remindme create <amount> <unit> <trigger> <output> <message>"));
        context.getSource().sendFeedback(Component.literal("  §7Creates a one-time reminder"));
        context.getSource().sendFeedback(Component.literal("  §7Units: seconds, minutes, hours, days"));
        context.getSource().sendFeedback(Component.literal("  §7Triggers: while_playing (pauses on logout) or real_time"));
        context.getSource().sendFeedback(Component.literal("  §7Output: chat, title_box, chat_and_title, sound_only"));
        context.getSource().sendFeedback(Component.literal(""));
        context.getSource().sendFeedback(Component.literal("§e/remindme create ... repeat <times> <message>"));
        context.getSource().sendFeedback(Component.literal("  §7Repeating reminder — times: until_removed or ≥2"));
        context.getSource().sendFeedback(Component.literal(""));
        context.getSource().sendFeedback(Component.literal("§e/remindme create ... name <label> <message>"));
        context.getSource().sendFeedback(Component.literal("  §7Creates a reminder with a custom display name"));
        context.getSource().sendFeedback(Component.literal(""));
        context.getSource().sendFeedback(Component.literal("§e/remindme rename <id> <name>  §7— Rename a reminder"));
        context.getSource().sendFeedback(Component.literal("§e/remindme pause <id>          §7— Pause a while_playing reminder"));
        context.getSource().sendFeedback(Component.literal("§e/remindme resume <id>         §7— Resume a paused reminder"));
        context.getSource().sendFeedback(Component.literal("§e/remindme snooze <id> <amount> <unit>  §7— Delay a reminder"));
        context.getSource().sendFeedback(Component.literal("§e/remindme remove <id>         §7— Remove a reminder"));
        context.getSource().sendFeedback(Component.literal("§e/remindme remove all          §7— Remove all reminders"));
        context.getSource().sendFeedback(Component.literal("§e/remindme list                §7— List all active reminders"));
        return 1;
    }

    // ── List line builder ────────────────────────────────────────────────────

    private static MutableComponent buildReminderListLine(Reminder reminder) {
        String index = "#" + reminder.getId();
        String timeLeft = ReminderManager.formatMs(reminder.getRemainingMs());
        String triggerLabel = reminder.getTriggerType() == TriggerType.REAL_TIME ? "real time" : "play time";

        // Repeat progress: "3/5" or "∞"
        String repeatLabel = null;
        if (reminder.getTotalRepeats() == ReminderManager.REPEAT_FOREVER) {
            repeatLabel = "∞";
        } else if (reminder.getTotalRepeats() > 1) {
            repeatLabel = (reminder.getRepeatCount() + 1) + "/" + reminder.getTotalRepeats();
        }

        String pauseCommand = reminder.isPaused()
                ? "/remindme resume " + reminder.getId()
                : (reminder.getTriggerType() == TriggerType.WHILE_PLAYING ? "/remindme pause " + reminder.getId() : null);

        MutableComponent name = Component.literal(reminder.getDisplayName()).withStyle(ChatFormatting.YELLOW);
        if (reminder.isPaused()) {
            name.append(Component.literal(" ⏸").withStyle(ChatFormatting.GRAY));
        }

        return buildTimerLine(index, name, timeLeft, triggerLabel,
                "/remindme remove " + reminder.getId(), pauseCommand, repeatLabel);
    }

    private static MutableComponent buildTimerLine(String index, Component name, String timeLeft,
                                                   String timerType, String removeCommand,
                                                   String pauseCommand, String repeatLabel) {
        MutableComponent line = Component.literal("")
                .append(Component.literal(index).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
                .append(name)
                .append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(timeLeft).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" left").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(timerType).withStyle(ChatFormatting.GOLD));

        if (repeatLabel != null) {
            line.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(repeatLabel).withStyle(ChatFormatting.LIGHT_PURPLE));
        }

        if (pauseCommand != null) {
            String buttonLabel = pauseCommand.contains("resume") ? "[resume]" : "[pause]";
            line.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(buttonLabel).withStyle(style -> style
                            .withColor(ChatFormatting.YELLOW)
                            .withClickEvent(new ClickEvent.RunCommand(pauseCommand))));
        }

        if (removeCommand != null) {
            line.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal("[remove]").withStyle(style -> style
                            .withColor(ChatFormatting.RED)
                            .withClickEvent(new ClickEvent.RunCommand(removeCommand))));
        }

        return line;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * @return 1 for single-fire (null arg), REPEAT_FOREVER for "until_removed",
     *         parsed int for numeric, or 0 on error (error already sent to source)
     */
    private static int parseRepeatCount(CommandContext<FabricClientCommandSource> context, String repeatTimesArg) {
        if (repeatTimesArg == null) return 1;
        if (repeatTimesArg.equalsIgnoreCase("until_removed")) return ReminderManager.REPEAT_FOREVER;

        try {
            int count = Integer.parseInt(repeatTimesArg);
            if (count < 2) {
                context.getSource().sendError(Component.literal("Repeat count must be at least 2"));
                return 0;
            }
            return count;
        } catch (NumberFormatException e) {
            context.getSource().sendError(Component.literal("Invalid repeat count: " + repeatTimesArg));
            return 0;
        }
    }

    private static MutableComponent buildCreateFeedback(Reminder reminder, int amount, String unit,
                                                        String repeatTimesArg, int repeatCount) {
        String triggerLabel = reminder.getTriggerType() == TriggerType.REAL_TIME ? "real time" : "play time";

        MutableComponent feedback = Component.literal("Created ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(triggerLabel).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" reminder ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("#" + reminder.getId()).withStyle(ChatFormatting.DARK_GRAY));

        if (reminder.getName() != null) {
            feedback.append(Component.literal(" \"" + reminder.getName() + "\"").withStyle(ChatFormatting.YELLOW));
        }

        feedback.append(Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(reminder.getMessage()).withStyle(ChatFormatting.WHITE));

        if (repeatTimesArg != null) {
            String repeatLabel = repeatCount == ReminderManager.REPEAT_FOREVER ? "until removed" : repeatCount + "×";
            feedback.append(Component.literal(" every ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(amount + " " + unit).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(repeatLabel).withStyle(ChatFormatting.LIGHT_PURPLE));
        } else {
            feedback.append(Component.literal(" in ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(amount + " " + unit).withStyle(ChatFormatting.AQUA));
        }

        return feedback;
    }

    private static ChatFormatting mapRarityColor(String rarity) {
        if (rarity == null) return ChatFormatting.WHITE;
        return switch (rarity.toUpperCase()) {
            case "UNCOMMON" -> ChatFormatting.GREEN;
            case "RARE" -> ChatFormatting.BLUE;
            case "EPIC" -> ChatFormatting.DARK_PURPLE;
            case "LEGENDARY" -> ChatFormatting.GOLD;
            case "MYTHIC" -> ChatFormatting.LIGHT_PURPLE;
            default -> ChatFormatting.WHITE;
        };
    }

    private static long getMultiplier(String unit) {
        return switch (unit.toLowerCase()) {
            case "s", "sec", "seconds" -> 1_000L;
            case "m", "min", "minutes" -> 60 * 1_000L;
            case "h", "hour", "hours" -> 60 * 60 * 1_000L;
            case "d", "day", "days" -> 24 * 60 * 60 * 1_000L;
            default -> -1;
        };
    }

    private static void persistReminders(ReminderManager reminderManager) {
        Path reminderPath = FabricLoader.getInstance()
                .getConfigDir()
                .resolve(SkyblockEnhancements.MOD_ID)
                .resolve("reminders.json");
        try {
            JsonFileUtil.writeAtomic(reminderPath, reminderManager.saveToStorage());
        } catch (IOException e) {
            SkyblockEnhancements.LOGGER.error("Failed to save reminders", e);
        }
    }
}