package ti4.listeners;

import java.util.List;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.channel.unions.IThreadContainerUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import ti4.AsyncTI4DiscordBot;
import ti4.commands2.Command;
import ti4.commands2.CommandManager;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

public class SlashCommandListener extends ListenerAdapter {
    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        if (!AsyncTI4DiscordBot.isReadyToReceiveCommands() && !"developer setting".equals(event.getInteraction().getFullCommandName())) {
            event.getInteraction().reply("Please try again in a moment.\nThe bot is rebooting and is not ready to receive commands.").setEphemeral(true).queue();
            return;
        }

        long eventTime = Math.min(event.getInteraction().getTimeCreated().toEpochSecond() * 1000, System.currentTimeMillis());

        long startTime = System.currentTimeMillis();

        String userID = event.getUser().getId();
        // CHECK IF CHANNEL IS MATCHED TO A GAME
        if (!event.getInteraction().getName().equals(Constants.HELP)
            && !event.getInteraction().getName().equals(Constants.STATISTICS)
            && !event.getInteraction().getName().equals(Constants.USER)
            && !event.getInteraction().getName().equals(Constants.SEARCH)
            && !event.getInteraction().getName().equals(Constants.TIGL)
            && (event.getInteraction().getSubcommandName() == null
                || !event.getInteraction().getSubcommandName().equalsIgnoreCase(Constants.CREATE_GAME_BUTTON))
            && event.getOption(Constants.GAME_NAME) == null) {

            boolean isChannelOK = setActiveGame(event.getChannel(), userID, event.getName(), event.getSubcommandName());
            if (!isChannelOK) {
                event
                    .reply(
                        "Command canceled. Execute command in correctly named channel that starts with the game name.\n> For example, for game `pbd123`, the channel name should start with `pbd123`")
                    .setEphemeral(true).queue();
                return;
            } else {
                Game userActiveGame = GameManager.getUserActiveGame(userID);
                if (userActiveGame != null) {
                    userActiveGame.incrementSpecificSlashCommandCount(event.getFullCommandName());
                }
            }
        }

        long deferTime = System.currentTimeMillis();
        event.getInteraction().deferReply().queue();

        Member member = event.getMember();
        if (member != null) {
            String commandText = "```fix\n" + member.getEffectiveName() + " used " + event.getCommandString() + "\n```";
            event.getChannel().sendMessage(commandText).queue(m -> {
                BotLogger.logSlashCommand(event, m);
                Game userActiveGame = GameManager.getUserActiveGame(userID);
                boolean harmless = event.getInteraction().getName().equals(Constants.HELP)
                    || event.getInteraction().getName().equals(Constants.STATISTICS)
                    || event.getInteraction().getName().equals(Constants.BOTHELPER)
                    || event.getInteraction().getName().equals(Constants.DEVELOPER)
                    || (event.getInteraction().getSubcommandName() != null && event.getInteraction()
                        .getSubcommandName().equalsIgnoreCase(Constants.CREATE_GAME_BUTTON))
                    || event.getInteraction().getName().equals(Constants.SEARCH)
                    || !(!event.getInteraction().getName().equals(Constants.USER) && !event.getInteraction().getName().equals(Constants.SHOW_GAME))
                    || event.getOption(Constants.GAME_NAME) != null;
                if (userActiveGame != null && !userActiveGame.isFowMode() && !harmless
                    && userActiveGame.getName().contains("pbd") && !userActiveGame.getName().contains("pbd1000") && !userActiveGame.getName().contains("pbd100two")) {
                    if (event.getMessageChannel() instanceof ThreadChannel thread) {
                        if (!thread.isPublic()) {
                            reportSusSlashCommand(event, m);
                        }
                    } else if (event.getMessageChannel() != userActiveGame.getActionsChannel()
                        && event.getMessageChannel() != userActiveGame.getTableTalkChannel()
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
        int milliThreshhold = 3000;
        if (startTime - eventTime > milliThreshhold || endTime - startTime > milliThreshhold) {
            String message = "This slash command took a while:\n> " +
                Helper.getTimeRepresentationToMilliseconds(startTime - eventTime) + " for the bot to respond\n> " +
                Helper.getTimeRepresentationToMilliseconds(endTime - startTime) + " for the bot to execute";
            BotLogger.log(event, message);
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
