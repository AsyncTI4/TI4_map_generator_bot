package ti4.listeners;

import javax.annotation.Nonnull;
import java.util.List;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.commons.lang3.exception.ExceptionUtils;
import ti4.AsyncTI4DiscordBot;
import ti4.commands2.Command;
import ti4.commands2.CommandHelper;
import ti4.commands2.CommandManager;
import ti4.helpers.Constants;
import ti4.helpers.DateTimeHelper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.map.ManagedGame;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

public class SlashCommandListener extends ListenerAdapter {

    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        if (!AsyncTI4DiscordBot.isReadyToReceiveCommands() && !"developer setting".equals(event.getInteraction().getFullCommandName())) {
            event.getInteraction().reply("Please try again in a moment.\nThe bot is rebooting and is not ready to receive commands.").setEphemeral(true).queue();
            return;
        }
        event.getInteraction().deferReply().queue();
        AsyncTI4DiscordBot.runAsync("Slash command task", () -> process(event));
    }
    
    private static void process(SlashCommandInteractionEvent event) {
        long eventTime = DateTimeHelper.getLongDateTimeFromDiscordSnowflake(event.getInteraction());

        long startTime = System.currentTimeMillis();

        // CHECK IF CHANNEL IS MATCHED TO A GAME
        if (!event.getInteraction().getName().equals(Constants.HELP)
            && !event.getInteraction().getName().equals(Constants.STATISTICS)
            && !event.getInteraction().getName().equals(Constants.USER)
            && !event.getInteraction().getName().equals(Constants.SEARCH)
            && !event.getInteraction().getName().equals(Constants.TIGL)
            && (event.getInteraction().getSubcommandName() == null
            || !event.getInteraction().getSubcommandName().equalsIgnoreCase(Constants.CREATE_GAME_BUTTON))
            && event.getOption(Constants.GAME_NAME) == null) {

            String gameName = CommandHelper.getGameNameFromChannel(event);
            if (!GameManager.isValidGame(gameName)) {
                event.reply(
                    "Command canceled. Execute command in correctly named channel that starts with the game name.\n> For example, for game `pbd123`, the channel name should start with `pbd123`")
                        .setEphemeral(true).queue();
                return;
            }
            Game game = GameManager.getGame(gameName);
            game.incrementSpecificSlashCommandCount(event.getFullCommandName());
            GameSaveLoadManager.saveGame(game, "Increment slash command count.");
        }

        Member member = event.getMember();
        if (member != null) {
            String commandText = "```fix\n" + member.getEffectiveName() + " used " + event.getCommandString() + "\n```";
            event.getChannel().sendMessage(commandText).queue(m -> {
                BotLogger.logSlashCommand(event, m);
                String gameName = CommandHelper.getGameNameFromChannel(event);
                ManagedGame game = GameManager.getManagedGame(gameName);
                boolean harmless = event.getInteraction().getName().equals(Constants.HELP)
                    || event.getInteraction().getName().equals(Constants.STATISTICS)
                    || event.getInteraction().getName().equals(Constants.BOTHELPER)
                    || event.getInteraction().getName().equals(Constants.DEVELOPER)
                    || (event.getInteraction().getSubcommandName() != null && event.getInteraction()
                    .getSubcommandName().equalsIgnoreCase(Constants.CREATE_GAME_BUTTON))
                    || event.getInteraction().getName().equals(Constants.SEARCH)
                    || !(!event.getInteraction().getName().equals(Constants.USER) && !event.getInteraction().getName().equals(Constants.SHOW_GAME))
                    || event.getOption(Constants.GAME_NAME) != null;
                if (game != null && !game.isFowMode() && !harmless && game.getName().contains("pbd") && !game.getName().contains("pbd1000")
                        && !game.getName().contains("pbd100two")) {
                    if (event.getMessageChannel() instanceof ThreadChannel thread) {
                        if (!thread.isPublic()) {
                            reportSusSlashCommand(event, m);
                        }
                    } else if (event.getMessageChannel() != game.getActionsChannel()
                            && event.getMessageChannel() != game.getTableTalkChannel()
                            && !event.getMessageChannel().getName().contains("bot-map-updates")) {
                        reportSusSlashCommand(event, m);
                    }
                }
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
        final int milliThreshold = 3000;
        if (startTime - eventTime > milliThreshold || endTime - startTime > milliThreshold) {
            String responseTime = DateTimeHelper.getTimeRepresentationToMilliseconds(startTime - eventTime);
            String executionTime = DateTimeHelper.getTimeRepresentationToMilliseconds(endTime - startTime);
            String message = event.getChannel().getAsMention() + " " + event.getUser().getEffectiveName() + " used: `" + event.getCommandString() + "`\n> Warning: " +
                "This slash command took over " + milliThreshold + "ms to respond or execute\n> " +
                DateTimeHelper.getTimestampFromMillesecondsEpoch(eventTime) + " command was issued by user\n> " +
                DateTimeHelper.getTimestampFromMillesecondsEpoch(startTime) + " `" + responseTime + "` to respond\n> " +
                DateTimeHelper.getTimestampFromMillesecondsEpoch(endTime) + " `" + executionTime + "` to execute" + (endTime - startTime > startTime - eventTime ? "😲" : "");
            BotLogger.log(message);
        }
    }

    private static void reportSusSlashCommand(SlashCommandInteractionEvent event, Message commandResponseMessage) {
        TextChannel bothelperLoungeChannel = AsyncTI4DiscordBot.guildPrimary
            .getTextChannelsByName("staff-lounge", true).stream().findFirst().orElse(null);
        if (bothelperLoungeChannel == null)
            return;
        List<ThreadChannel> threadChannels = bothelperLoungeChannel.getThreadChannels();
        if (threadChannels.isEmpty())
            return;
        String threadName = "sus-slash-commands";
        // SEARCH FOR EXISTING OPEN THREAD
        for (ThreadChannel threadChannel_ : threadChannels) {
            if (threadChannel_.getName().equals(threadName)) {
                String sb = event.getUser().getEffectiveName() + " " +
                    "`" + event.getCommandString() + "` " +
                    commandResponseMessage.getJumpUrl();
                MessageHelper.sendMessageToChannel(threadChannel_, sb);
                break;
            }
        }
    }
}
