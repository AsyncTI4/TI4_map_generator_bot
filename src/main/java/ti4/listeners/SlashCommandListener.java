package ti4.listeners;

import javax.annotation.Nonnull;
import java.util.List;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
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
import ti4.helpers.ThreadGetter;
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
        event.getInteraction().deferReply().queue();
        AsyncTI4DiscordBot.runAsync("Slash command task", () -> process(event));
    }

    private static void process(SlashCommandInteractionEvent event) {
        long eventTime = DateTimeHelper.getLongDateTimeFromDiscordSnowflake(event.getInteraction());

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

        Member member = event.getMember();
        if (member != null) {
            String commandText = "```fix\n" + member.getEffectiveName() + " used " + event.getCommandString() + "\n```";
            event.getChannel().sendMessage(commandText).queue(m -> {
                BotLogger.logSlashCommand(event, m);
                checkIfShouldReportSusSlashCommand(event, userID, m);
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
                DateTimeHelper.getTimestampFromMillesecondsEpoch(eventTime) + " command was issued by user\n> " +
                DateTimeHelper.getTimestampFromMillesecondsEpoch(startTime) + " `" + responseTime + "` to respond\n> " +
                DateTimeHelper.getTimestampFromMillesecondsEpoch(endTime) + " `" + executionTime + "` to execute" + (endTime - startTime > startTime - eventTime ? "😲" : "");
            BotLogger.log(message);
        }
    }

    private static void checkIfShouldReportSusSlashCommand(SlashCommandInteractionEvent event, String userID, Message m) {
        Game game = GameManager.getUserActiveGame(userID);
        if (game == null) return;
        if (game.isFowMode()) return;

        final List<String> harmlessCommands = List.of(Constants.HELP, Constants.STATISTICS, Constants.BOTHELPER, Constants.DEVELOPER, Constants.SEARCH, Constants.USER, Constants.SHOW_GAME);
        if (harmlessCommands.contains(event.getInteraction().getName())) return;

        final List<String> harmlessSubcommands = List.of(Constants.CREATE_GAME_BUTTON);
        if (event.getInteraction().getSubcommandName() != null && harmlessSubcommands.contains(event.getInteraction().getSubcommandName())) return;

        final List<String> harmlessCommandOptions = List.of(Constants.GAME_NAME);
        if (harmlessCommandOptions.stream().anyMatch(cmd -> event.getOption(cmd) != null)) return;

        final List<String> excludedGames = List.of("pbd1000", "pbd100two");
        if (excludedGames.contains(game.getName())) return;

        boolean isPrivateThread = (event.getMessageChannel() instanceof ThreadChannel thread && !thread.isPublic());
        boolean isGameChannel = event.getMessageChannel() != game.getActionsChannel()
            && event.getMessageChannel() != game.getTableTalkChannel()
            && !event.getMessageChannel().getName().contains("bot-map-updates");

        if (isPrivateThread || isGameChannel) {
            reportSusSlashCommand(event, m);
        }
    }

    private static void reportSusSlashCommand(SlashCommandInteractionEvent event, Message commandResponseMessage) {
        TextChannel bothelperLoungeChannel = AsyncTI4DiscordBot.guildPrimary.getTextChannelsByName("staff-lounge", true).stream().findFirst().orElse(null);
        if (bothelperLoungeChannel == null) return;
        ThreadChannel threadChannel = ThreadGetter.getThreadInChannel(bothelperLoungeChannel, "sus-slash-commands", true, true);
        String sb = event.getUser().getEffectiveName() + " " + "`" + event.getCommandString() + "` " + commandResponseMessage.getJumpUrl();
        MessageHelper.sendMessageToChannel(threadChannel, sb);

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
