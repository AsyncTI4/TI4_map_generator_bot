package ti4.listeners;

import javax.annotation.Nonnull;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.commons.lang3.exception.ExceptionUtils;
import ti4.AsyncTI4DiscordBot;
import ti4.commands2.Command;
import ti4.commands2.CommandManager;
import ti4.executors.ExecutorManager;
import ti4.helpers.DateTimeHelper;
import ti4.message.BotLogger;
import ti4.service.SusSlashCommandService;
import ti4.service.game.GameNameService;

public class SlashCommandListener extends ListenerAdapter {

    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        if (!AsyncTI4DiscordBot.isReadyToReceiveCommands() && !"developer setting".equals(event.getInteraction().getFullCommandName())) {
            event.getInteraction().reply("Please try again in a moment.\nThe bot is rebooting and is not ready to receive commands.").setEphemeral(true).queue();
            return;
        }

        event.getInteraction().deferReply().queue();

        process(event);
    }

    private static void process(SlashCommandInteractionEvent event) {
        long startTime = System.currentTimeMillis();
        long eventTime = DateTimeHelper.getLongDateTimeFromDiscordSnowflake(event.getInteraction());
        long eventDelay = startTime - eventTime;

        String gameName = GameNameService.getGameName(event);
        ExecutorManager.runAsync("Slash command task: " + event.getFullCommandName(), gameName, () -> process(event, eventDelay));
    }

    private static void process(SlashCommandInteractionEvent event, long eventDelay) {
        long startTime = System.currentTimeMillis();

        Member member = event.getMember();
        if (member != null) {
            String commandText = "```fix\n" + member.getEffectiveName() + " used " + event.getCommandString() + "\n```";
            event.getChannel().sendMessage(commandText).queue(m -> {
                BotLogger.logSlashCommand(event, m);
                SusSlashCommandService.checkIfShouldReportSusSlashCommand(event, m.getJumpUrl());
            }, BotLogger::catchRestError);
        }

        Command command = CommandManager.getCommand(event.getName());
        if (command.accept(event)) {
            try {
                command.preExecute(event);
                command.execute(event);
                command.postExecute(event);
                event.getHook().deleteOriginal().queue();
            } catch (Exception e) {
                String messageText = "Error trying to execute command: " + command.getName();
                String errorMessage = ExceptionUtils.getMessage(e);
                event.getHook().editOriginal(errorMessage).queue();
                BotLogger.log(event, messageText, e);
            }
        } else {
            event.getHook().deleteOriginal().queue();
        }

        long eventTime = DateTimeHelper.getLongDateTimeFromDiscordSnowflake(event.getInteraction());
        long endTime = System.currentTimeMillis();
        long processingRuntime = endTime - startTime;
        int milliThreshold = 2000;
        if (eventDelay > milliThreshold || processingRuntime > milliThreshold) {
            String responseTime = DateTimeHelper.getTimeRepresentationToMilliseconds(eventDelay);
            String executionTime = DateTimeHelper.getTimeRepresentationToMilliseconds(processingRuntime);
            String message = event.getChannel().getAsMention() + " " + event.getUser().getEffectiveName() + " used: `" + event.getCommandString() + "`\n> Warning: " +
                "This slash command took over " + milliThreshold + "ms to respond or execute\n> " +
                DateTimeHelper.getTimestampFromMillisecondsEpoch(eventTime) + " command was issued by user\n> " +
                DateTimeHelper.getTimestampFromMillisecondsEpoch(startTime) + " `" + responseTime + "` to respond\n> " +
                DateTimeHelper.getTimestampFromMillisecondsEpoch(endTime) + " `" + executionTime + "` to execute" + (processingRuntime > eventDelay ? "😲" : "");
            BotLogger.log(message);
        }
    }
}
