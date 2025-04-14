package ti4.listeners;

import java.util.Arrays;
import java.util.List;

import javax.annotation.Nonnull;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.commons.lang3.exception.ExceptionUtils;
import ti4.AsyncTI4DiscordBot;
import ti4.commands.Command;
import ti4.commands.CommandManager;
import ti4.executors.ExecutorManager;
import ti4.helpers.Constants;
import ti4.helpers.DateTimeHelper;
import ti4.message.BotLogger;
import ti4.service.SusSlashCommandService;
import ti4.service.game.GameNameService;

public class SlashCommandListener extends ListenerAdapter {

    private static final long DELAY_THRESHOLD_MILLISECONDS = 1500;

    private static final List<String> SLASHCOMMANDS_WITH_MODALS = Arrays.asList(Constants.ADD_TILE_LIST, Constants.ADD_TILE_LIST_RANDOM);

    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        if (!AsyncTI4DiscordBot.isReadyToReceiveCommands() && !"developer setting".equals(event.getInteraction().getFullCommandName())) {
            event.getInteraction().reply("Please try again in a moment.\nThe bot is rebooting and is not ready to receive commands.").setEphemeral(true).queue();
            return;
        }

        if (!isModalCommand(event)) {
            event.getInteraction().deferReply().queue();
        }

        queue(event);
    }

    private static void queue(SlashCommandInteractionEvent event) {
        String gameName = GameNameService.getGameName(event);
        ExecutorManager.runAsync("SlashCommandListener task: " + event.getFullCommandName(), gameName, event.getMessageChannel(), () -> process(event));
    }

    private static void process(SlashCommandInteractionEvent event) {
        long startTime = System.currentTimeMillis();

        Command command = CommandManager.getCommand(event.getName());
        if (command.accept(event)) {
            try {
                logSlashCommand(event);
                command.preExecute(event);
                command.execute(event);
                command.postExecute(event);
                if (!isModalCommand(event)) {
                    event.getHook().deleteOriginal().queue();
                }
            } catch (Exception e) {
                String messageText = "Error trying to execute command: " + command.getName();
                String errorMessage = ExceptionUtils.getMessage(e);
                event.getHook().editOriginal(errorMessage).queue();
                BotLogger.error(new BotLogger.LogMessageOrigin(event), messageText, e);
            }
        }

        long eventTime = DateTimeHelper.getLongDateTimeFromDiscordSnowflake(event.getInteraction());
        long eventDelay = startTime - eventTime;

        long endTime = System.currentTimeMillis();
        long processingRuntime = endTime - startTime;

        if (eventDelay > DELAY_THRESHOLD_MILLISECONDS || processingRuntime > DELAY_THRESHOLD_MILLISECONDS) {
            String responseTime = DateTimeHelper.getTimeRepresentationToMilliseconds(eventDelay);
            String executionTime = DateTimeHelper.getTimeRepresentationToMilliseconds(processingRuntime);
            String message = event.getChannel().getAsMention() + " " + event.getUser().getEffectiveName() + " used: `" + event.getCommandString() + "`\n> Warning: " +
                "This slash command took over " + DELAY_THRESHOLD_MILLISECONDS + "ms to respond or execute\n> " +
                DateTimeHelper.getTimestampFromMillisecondsEpoch(eventTime) + " command was issued by user\n> " +
                DateTimeHelper.getTimestampFromMillisecondsEpoch(startTime) + " `" + responseTime + "` to respond\n> " +
                DateTimeHelper.getTimestampFromMillisecondsEpoch(endTime) + " `" + executionTime + "` to execute" + (processingRuntime > eventDelay ? "😲" : "");
            BotLogger.warning(new BotLogger.LogMessageOrigin(event), message);
        }
    }

    private static boolean isModalCommand(SlashCommandInteractionEvent event) {
        return SLASHCOMMANDS_WITH_MODALS.contains(event.getInteraction().getSubcommandName());
    }

    private static void logSlashCommand(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        if (member != null) {
            String commandText = "```fix\n" + member.getEffectiveName() + " used " + event.getCommandString() + "\n```";
            event.getChannel().sendMessage(commandText).queue(m -> {
                BotLogger.logSlashCommand(event, m);
                SusSlashCommandService.checkIfShouldReportSusSlashCommand(event, m.getJumpUrl());
            }, BotLogger::catchRestError);
        }
    }
}
