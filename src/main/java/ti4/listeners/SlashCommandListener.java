package ti4.listeners;

import java.util.Arrays;
import java.util.List;
import javax.annotation.Nonnull;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.commons.lang3.function.Consumers;
import ti4.commands.ParentCommand;
import ti4.commands.SlashCommandManager;
import ti4.executors.ExecutorServiceManager;
import ti4.helpers.Constants;
import ti4.helpers.DateTimeHelper;
import ti4.message.logging.BotLogger;
import ti4.message.logging.LogOrigin;
import ti4.service.SusSlashCommandService;
import ti4.service.game.GameNameService;

public class SlashCommandListener extends ListenerAdapter implements ListenerInterface {

    private static final List<String> SLASHCOMMANDS_WITH_MODALS = Arrays.asList(
            Constants.ADD_TILE_LIST,
            Constants.ADD_TILE_LIST_RANDOM,
            Constants.EDIT_TRACK_RECORD,
            Constants.IMPORT_MAP_JSON);

    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        if (!receiveCommands(event)) return;

        if (!isModalCommand(event)) {
            event.getInteraction().deferReply().queue(Consumers.nop(), BotLogger::catchRestError);
        }

        queue(event);
    }

    public void queue(SlashCommandInteractionEvent event) {
        String gameName = GameNameService.getGameName(event);
        ExecutorServiceManager.runAsync(
                eventToString(event, gameName), gameName, event.getMessageChannel(), () -> process(event));
    }

    public String eventToString(GenericCommandInteractionEvent event, String gameName) {
        return "SlashCommandListener task for `" + event.getUser().getEffectiveName() + "`"
                + (gameName == null ? "" : " in `" + gameName + "`")
                + ": `"
                + event.getCommandString() + "`";
    }

    private static void process(SlashCommandInteractionEvent event) {
        long startTime = System.currentTimeMillis();

        ParentCommand command = SlashCommandManager.getCommand(event.getName());
        try {
            if (command.accept(event)) {
                command.preExecute(event);
                logSlashCommand(event);
                command.execute(event);
                command.postExecute(event);
                if (!isModalCommand(event)) {
                    event.getHook().deleteOriginal().queue(Consumers.nop(), BotLogger::catchRestError);
                }
            }
        } catch (Exception e) {
            command.onException(event, e);
        }

        long eventTime = DateTimeHelper.getLongDateTimeFromDiscordSnowflake(event.getInteraction());
        long eventDelay = startTime - eventTime;

        long endTime = System.currentTimeMillis();
        long processingRuntime = endTime - startTime;

        if (eventDelay > ListenerInterface.DELAY_THRESHOLD_MILLISECONDS
                || processingRuntime > ListenerInterface.DELAY_THRESHOLD_MILLISECONDS) {
            String responseTime = DateTimeHelper.getTimeRepresentationToMilliseconds(eventDelay);
            String executionTime = DateTimeHelper.getTimeRepresentationToMilliseconds(processingRuntime);
            String message = event.getChannel().getAsMention() + " "
                    + event.getUser().getEffectiveName() + " used: `" + event.getCommandString() + "`\n> Warning: "
                    + "This slash command took over "
                    + ListenerInterface.DELAY_THRESHOLD_MILLISECONDS + "ms to respond or execute\n> "
                    + DateTimeHelper.getTimestampFromMillisecondsEpoch(eventTime)
                    + " command was issued by user\n> " + DateTimeHelper.getTimestampFromMillisecondsEpoch(startTime)
                    + " `" + responseTime + "` to respond\n> "
                    + DateTimeHelper.getTimestampFromMillisecondsEpoch(endTime)
                    + " `" + executionTime + "` to execute" + (processingRuntime > eventDelay ? "😲" : "");
            BotLogger.warning(new LogOrigin(event), message);
        }
    }

    private static boolean isModalCommand(SlashCommandInteractionEvent event) {
        return SLASHCOMMANDS_WITH_MODALS.contains(event.getInteraction().getSubcommandName());
    }

    private static void logSlashCommand(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        if (member == null) return;

        var command = SlashCommandManager.getCommand(event.getInteraction().getName());
        String susPrefix = command.isSuspicious(event) ? "sus" : "notSus";
        String commandText =
                "```" + susPrefix + "\n" + member.getEffectiveName() + " used " + event.getCommandString() + "\n```";
        event.getChannel()
                .sendMessage(commandText)
                .queue(
                        m -> {
                            BotLogger.logSlashCommand(event, m);
                            SusSlashCommandService.checkIfShouldReportSusSlashCommand(event, m.getJumpUrl());
                        },
                        BotLogger::catchRestError);
    }
}
