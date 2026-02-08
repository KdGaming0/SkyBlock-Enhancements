package com.github.kd_gaming1.skyblockenhancements.command;

import com.github.kd_gaming1.skyblockenhancements.feature.reminder.Reminder;
import com.github.kd_gaming1.skyblockenhancements.feature.reminder.ReminderManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import java.util.List;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * Client-side command interface for creating and managing reminders.
 * Supports creation with time units, trigger types, output methods, and optional repeating.
 */
public class ReminderCommand {

    private static final SuggestionProvider<FabricClientCommandSource> TIME_UNITS = (context, builder) -> {
        builder.suggest("seconds");
        builder.suggest("minutes");
        builder.suggest("minute");
        builder.suggest("hours");
        builder.suggest("days");
        builder.suggest("sec");
        builder.suggest("min");
        builder.suggest("hour");
        builder.suggest("day");
        return builder.buildFuture();
    };

    private static final SuggestionProvider<FabricClientCommandSource> TRIGGERS = (context, builder) -> {
           builder.suggest("while_playing");
           builder.suggest("real_time");
           return builder.buildFuture();
    };

    private static final SuggestionProvider<FabricClientCommandSource> OUTPUT = (context, builder) -> {
        builder.suggest("chat");
        builder.suggest("title_box");
        builder.suggest("chat_and_title");
        return builder.buildFuture();
    };

    private static SuggestionProvider<FabricClientCommandSource> suggestReminderIds(ReminderManager reminderManager) {
        return (context, builder) -> {
            for (Reminder reminder : reminderManager.getActiveReminders()) {
                builder.suggest(reminder.id, Component.literal(reminder.message));
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
                                                                .suggests(OUTPUT)
                                                                .then(argument("message", StringArgumentType.greedyString())
                                                                        .executes(context -> executeCreate(context, reminderManager, null))
                                                                )
                                                                .then(literal("repeat")
                                                                        .then(argument("times", StringArgumentType.word())
                                                                                .suggests((context, builder) -> {
                                                                                    builder.suggest("until_removed");
                                                                                    builder.suggest("2");
                                                                                    builder.suggest("3");
                                                                                    builder.suggest("5");
                                                                                    builder.suggest("10");
                                                                                    return builder.buildFuture();
                                                                                })
                                                                                .then(argument("message", StringArgumentType.greedyString())
                                                                                        .executes(context -> executeCreate(context, reminderManager, StringArgumentType.getString(context, "times")))
                                                                                )    )
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(literal("remove")
                                .then(argument("id", IntegerArgumentType.integer(1))
                                        .suggests(suggestReminderIds(reminderManager))
                                        .executes(context -> executeRemove(context, reminderManager))
                                )
                                .then(literal("all")
                                        .executes(context -> executeRemoveAll(context, reminderManager))
                                )
                        )
                        .then(literal("list")
                                .executes(context -> executeList(context, reminderManager))
                        )
                        .then(literal("help")
                                .executes(ReminderCommand::executeHelp)
                        )
                )
        );
    }

    private static int executeCreate(CommandContext<FabricClientCommandSource> context, ReminderManager reminderManager, String repeatTimes) {
        int amount = IntegerArgumentType.getInteger(context, "amount");
        String unit = StringArgumentType.getString(context, "unit");
        String trigger = StringArgumentType.getString(context, "trigger");
        String output = StringArgumentType.getString(context, "output");
        String message = StringArgumentType.getString(context, "message");

        long multiplier = getMultiplier(unit);
        if (multiplier == -1) {
            context.getSource().sendError(Component.literal("Invalid time unit: " + unit));
            return 0;
        }

        if (!output.matches("chat|title_box|chat_and_title")) {
            context.getSource().sendError(Component.literal("Invalid output type: " + output));
            return 0;
        }

        long durationMs = amount * multiplier;

        // Handle repeat logic
        Integer repeatCount = null;
        if (repeatTimes != null) {
            if (repeatTimes.equalsIgnoreCase("until_removed")) {
                repeatCount = -1;
            } else {
                try {
                    repeatCount = Integer.parseInt(repeatTimes);
                    if (repeatCount < 2) {
                        context.getSource().sendError(Component.literal("Repeat count must be at least 2"));
                        return 0;
                    }
                } catch (NumberFormatException e) {
                    context.getSource().sendError(Component.literal("Invalid repeat count: " + repeatTimes));
                    return 0;
                }
            }
        }

        Reminder reminder;
        if (trigger.equalsIgnoreCase("real_time")) {
            reminder = repeatCount != null
                    ? reminderManager.createRepeatingRealTimeReminder(durationMs, output, message, repeatCount)
                    : reminderManager.createRealTimeReminder(durationMs, output, message);
        } else if (trigger.equalsIgnoreCase("while_playing")) {
            reminder = repeatCount != null
                    ? reminderManager.createRepeatingWhilePlayingReminder(durationMs, output, message, repeatCount)
                    : reminderManager.createWhilePlayingReminder(durationMs, output, message);
        } else {
            context.getSource().sendError(Component.literal("Invalid trigger: " + trigger));
            return 0;
        }

        String feedbackMsg = repeatCount != null
                ? String.format("Created repeating %s reminder #%d (every %d %s, %s)",
                trigger, reminder.id, amount, unit,
                repeatCount == -1 ? "until removed" : repeatCount + " times")
                : String.format("Created %s reminder #%d in %d %s", trigger, reminder.id, amount, unit);

        context.getSource().sendFeedback(Component.literal(feedbackMsg));
        return 1;
    }

    private static int executeRemove(CommandContext<FabricClientCommandSource> context, ReminderManager reminderManager) {
        int id = IntegerArgumentType.getInteger(context, "id");

        if (reminderManager.removeReminder(id)) {
            context.getSource().sendFeedback(Component.literal("Removed reminder " + id));
            return 1;
        } else {
            context.getSource().sendError(Component.literal("Reminder " + id + " not found"));
            return 0;
        }
    }

    private static int executeRemoveAll(CommandContext<FabricClientCommandSource> context, ReminderManager reminderManager) {
        int count = reminderManager.removeAllReminders();
        context.getSource().sendFeedback(Component.literal("Removed " + count + " reminder(s)"));
        return 1;
    }

    private static int executeList(CommandContext<FabricClientCommandSource> context, ReminderManager reminderManager) {
        List<Reminder> reminders = reminderManager.getActiveReminders();

        if (reminders.isEmpty()) {
            context.getSource().sendFeedback(Component.literal("No active reminders"));
            return 0;
        }

        context.getSource().sendFeedback(Component.literal("Active reminders:"));
        for (Reminder reminder : reminders) {
            String timeInfo = formatRemainingTime(reminder);
            context.getSource().sendFeedback(Component.literal(
                    String.format("Reminder %d - Message: \"%s\" | Time left: %s | Type: %s",
                            reminder.id,
                            reminder.message,
                            timeInfo,
                            reminder.triggerType.toLowerCase().replace("_", " ")
                    )
            ));

        }

        return 1;
    }

    private static int executeHelp(CommandContext<FabricClientCommandSource> context) {
        context.getSource().sendFeedback(Component.literal("§6§l=== RemindMe Help ==="));
        context.getSource().sendFeedback(Component.literal("§e/remindme create <amount> <unit> <trigger> <output> <message>"));
        context.getSource().sendFeedback(Component.literal("  §7Creates a one-time reminder"));
        context.getSource().sendFeedback(Component.literal("  §7Units: seconds, minutes, hours, days (or sec, min, hour, day)"));
        context.getSource().sendFeedback(Component.literal("  §7Triggers: while_playing (in-game time (pause when you leave)) or real_time"));
        context.getSource().sendFeedback(Component.literal("  §7Output: chat, title_box, or chat_and_title"));
        context.getSource().sendFeedback(Component.literal(""));
        context.getSource().sendFeedback(Component.literal("§e/remindme create <amount> <unit> <trigger> <output> repeat <times> <message>"));
        context.getSource().sendFeedback(Component.literal("  §7Creates a repeating reminder"));
        context.getSource().sendFeedback(Component.literal("  §7Times: until_removed or a number (minimum 2)"));
        context.getSource().sendFeedback(Component.literal(""));
        context.getSource().sendFeedback(Component.literal("§e/remindme remove <id>"));
        context.getSource().sendFeedback(Component.literal("  §7Removes a specific reminder by ID"));
        context.getSource().sendFeedback(Component.literal(""));
        context.getSource().sendFeedback(Component.literal("§e/remindme remove all"));
        context.getSource().sendFeedback(Component.literal("  §7Removes all active reminders"));
        context.getSource().sendFeedback(Component.literal(""));
        context.getSource().sendFeedback(Component.literal("§e/remindme list"));
        context.getSource().sendFeedback(Component.literal("  §7Lists all active reminders"));
        return 1;
    }

    private static String formatRemainingTime(Reminder reminder) {
        long ms;
        if ("REAL_TIME".equals(reminder.triggerType)) {
            ms = Math.max(0, reminder.dueAtMs - System.currentTimeMillis());
        } else {
            ms = reminder.remainingMs;
        }

        if (ms < 0) ms = 0;

        long seconds = ms / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) return days + " day(s)";
        if (hours > 0) return hours + " hour(s)";
        if (minutes > 0) return minutes + " minute(s)";
        return seconds + " second(s)";
    }


    private static long getMultiplier(String unit) {
        return switch (unit.toLowerCase()) {
            case "s", "sec", "seconds" -> 1000L;
            case "m", "min", "minutes" -> 60 * 1000L;
            case "h", "hour", "hours" -> 60 * 60 * 1000L;
            case "d", "day", "days" -> 24 * 60 * 60 * 1000L;
            default -> -1;
        };
    }
}