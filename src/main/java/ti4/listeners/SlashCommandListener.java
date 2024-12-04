package ti4.listeners;

import javax.annotation.Nonnull;
import java.util.List;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.channel.unions.IThreadContainerUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import ti4.AsyncTI4DiscordBot;
import ti4.commands2.Command;
import ti4.commands2.CommandManager;
import ti4.helpers.Constants;
import ti4.helpers.DateTimeHelper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.message.BotLogger;

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

        Member member = event.getMember();
        if (member != null) {
            String commandText = "```fix\n" + member.getEffectiveName() + " used " + event.getCommandString() + "\n```";
            event.getChannel().sendMessage(commandText).queue(m -> BotLogger.logSlashCommand(event, m), BotLogger::catchRestError);
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
                DateTimeHelper.getTimestampFromMillesecondsEpoch(eventTime) + " command was issued by user\n> " +
                DateTimeHelper.getTimestampFromMillesecondsEpoch(startTime) + " `" + responseTime + "` to respond\n> " +
                DateTimeHelper.getTimestampFromMillesecondsEpoch(endTime) + " `" + executionTime + "` to execute" + (endTime - startTime > startTime - eventTime ? "😲" : "");
            BotLogger.log(message);
        }
    }

    public static boolean setActiveGame(MessageChannel channel, String userID, String eventName, String subCommandName) {
        String channelName = channel.getName();
        Game userActiveGame = GameManager.getUserActiveGame(userID);
        List<String> mapList = GameManager.getGameNames();

        String gameID = StringUtils.substringBefore(channelName, "-");
        boolean gameExists = mapList.contains(gameID);

        boolean isThreadEnabledSubcommand = (Constants.COMBAT.equals(eventName) && Constants.COMBAT_ROLL.equals(subCommandName));
        if (!gameExists && channel instanceof ThreadChannel && isThreadEnabledSubcommand) {
            IThreadContainerUnion parentChannel = ((ThreadChannel) channel).getParentChannel();
            channelName = parentChannel.getName();
            gameID = StringUtils.substringBefore(channelName, "-");
            gameExists = mapList.contains(gameID);
        }

        boolean isUnprotectedCommand = eventName.contains(Constants.SHOW_GAME)
            || eventName.contains(Constants.BOTHELPER) || eventName.contains(Constants.ADMIN)
            || eventName.contains(Constants.DEVELOPER);
        boolean isUnprotectedCommandSubcommand = (Constants.GAME.equals(eventName)
            && Constants.CREATE_GAME.equals(subCommandName));
        if (!gameExists && !(isUnprotectedCommand) && !(isUnprotectedCommandSubcommand)) {
            return false;
        }
        if (gameExists && (GameManager.getUserActiveGame(userID) == null
            || !GameManager.getUserActiveGame(userID).getName().equals(gameID)
                && (GameManager.getGame(gameID) != null && (GameManager.getGame(gameID).isCommunityMode()
                    || GameManager.getGame(gameID).getPlayerIDs().contains(userID))))) {
            GameManager.setGameForUser(userID, gameID);
        } else if (GameManager.isUserWithActiveGame(userID)) {
            if (gameExists && !channelName.startsWith(userActiveGame.getName())) {
                // MessageHelper.sendMessageToChannel(channel,"Active game reset. Channel name
                // indicates to have map associated with it. Please select correct active game
                // or do action in neutral channel");
                GameManager.resetGameForUser(userID);
            }
        }
        return true;
    }
}
