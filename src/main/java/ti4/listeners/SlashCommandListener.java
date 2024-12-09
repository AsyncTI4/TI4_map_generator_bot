package ti4.listeners;

import javax.annotation.Nonnull;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.commons.lang3.exception.ExceptionUtils;
import ti4.AsyncTI4DiscordBot;
import ti4.commands2.Command;
import ti4.commands2.CommandManager;
import ti4.helpers.DateTimeHelper;
import ti4.message.BotLogger;
import ti4.service.SusSlashCommandService;

public class SlashCommandListener extends ListenerAdapter {

    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        if (!AsyncTI4DiscordBot.isReadyToReceiveCommands() && !"developer setting".equals(event.getInteraction().getFullCommandName())) {
            event.getInteraction().reply("Please try again in a moment.\nThe bot is rebooting and is not ready to receive commands.").setEphemeral(true).queue();
            return;
        }

        event.getInteraction().deferReply().queue();

        AsyncTI4DiscordBot.runAsync("Slash command task: " + event.getFullCommandName(), () -> process(event));
    }

    private static void process(SlashCommandInteractionEvent event) {
        long eventTime = DateTimeHelper.getLongDateTimeFromDiscordSnowflake(event.getInteraction());
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
            } catch (Exception e) {
                String messageText = "Error trying to execute command: " + command.getName();
                String errorMessage = ExceptionUtils.getMessage(e);
                event.getHook().editOriginal(errorMessage).queue();
                BotLogger.log(event, messageText, e);
            }
        }

        event.getHook().deleteOriginal().queue();

        long endTime = System.currentTimeMillis();
        final int milliThreshold = 2000;
        if (startTime - eventTime > milliThreshold || endTime - startTime > milliThreshold) {
            String responseTime = DateTimeHelper.getTimeRepresentationToMilliseconds(startTime - eventTime);
            String executionTime = DateTimeHelper.getTimeRepresentationToMilliseconds(endTime - startTime);
            String message = event.getChannel().getAsMention() + " " + event.getUser().getEffectiveName() + " used: `" + event.getCommandString() + "`\n> Warning: " +
                "This slash command took over " + milliThreshold + "ms to respond or execute\n> " +
                DateTimeHelper.getTimestampFromMillisecondsEpoch(eventTime) + " command was issued by user\n> " +
                DateTimeHelper.getTimestampFromMillisecondsEpoch(startTime) + " `" + responseTime + "` to respond\n> " +
                DateTimeHelper.getTimestampFromMillisecondsEpoch(endTime) + " `" + executionTime + "` to execute" + (endTime - startTime > startTime - eventTime ? "😲" : "");
            BotLogger.log(message);
        }
    }
}
