package ti4.commands.game;

import java.io.File;
import java.text.NumberFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.commons.lang3.StringUtils;
import ti4.AsyncTI4DiscordBot;
import ti4.commands.statistics.GameStatisticFilterer;
import ti4.commands.statistics.GameStats;
import ti4.generator.MapGenerator;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.helpers.Storage;
import ti4.helpers.WebHelper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

public class GameEnd extends GameSubcommandData {

    public GameEnd() {
        super(Constants.GAME_END, "Declare the game has ended");
        addOptions(new OptionData(OptionType.STRING, Constants.CONFIRM, "Confirm ending the game with 'YES'").setRequired(true));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.PUBLISH, "True to publish results to #pbd-chronicles. (Default: True)"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.ARCHIVE_CHANNELS, "True to archive the channels and delete the game role (Default: True)"));
    }

    public void execute(SlashCommandInteractionEvent event) {
        GameManager gameManager = GameManager.getInstance();
        Game activeGame = gameManager.getUserActiveGame(event.getUser().getId());

        if (activeGame == null) {
            MessageHelper.replyToMessage(event, "Must set active Game");
            return;
        }
        String gameName = activeGame.getName();
        if (!gameName.equals(StringUtils.substringBefore(event.getChannel().getName(), "-"))) {
            MessageHelper.replyToMessage(event, "`/game end` must be executed in game channel only!");
            return;
        }
        OptionMapping option = event.getOption(Constants.CONFIRM);
        if (option == null || !"YES".equals(option.getAsString())) {
            MessageHelper.replyToMessage(event, "Must confirm with 'YES'");
            return;
        }
        boolean publish = event.getOption(Constants.PUBLISH, true, OptionMapping::getAsBoolean);
        boolean archiveChannels = event.getOption(Constants.ARCHIVE_CHANNELS, true, OptionMapping::getAsBoolean);
        secondHalfOfGameEnd(event, activeGame, publish, archiveChannels);
    }

    public static void secondHalfOfGameEnd(GenericInteractionCreateEvent event, Game activeGame, boolean publish, boolean archiveChannels) {
        String gameName = activeGame.getName();
        List<Role> gameRoles = event.getGuild().getRolesByName(gameName, true);
        boolean deleteRole = true;
        if (gameRoles.size() > 1) {
            MessageHelper.replyToMessage(event, "There are multiple roles that match this game name (" + gameName + "): " + gameRoles);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Please call a @Bothelper to fix this before using `/game end`");
            return;
        } else if (gameRoles.isEmpty()) {
            MessageHelper.replyToMessage(event, "No roles match the game name (" + gameName + ") - no role will be deleted.");
            deleteRole = false;
        }

        //ADD USER PERMISSIONS DIRECTLY TO CHANNEL
        Helper.addMapPlayerPermissionsToGameChannels(event.getGuild(), activeGame);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "This game's channels' permissions have been updated.");

        // DELETE THE ROLE
        if (deleteRole && archiveChannels) {
            Role gameRole = gameRoles.get(0);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Role deleted: " + gameRole.getName() + " - use `/game ping` to ping all players");
            gameRole.delete().queue();
        }

        // POST GAME INFO
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "**Game: `" + gameName + "` has ended!**");
        activeGame.setHasEnded(true);
        activeGame.setEndedDate(new Date().getTime());
        GameSaveLoadManager.saveMap(activeGame, event);
        String gameEndText = getGameEndText(activeGame, event);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), gameEndText);
        activeGame.setAutoPing(false);
        activeGame.setAutoPingSpacer(0);

        // SEND THE MAP IMAGE
        MapGenerator.saveImage(activeGame, DisplayType.all, event)
            .thenAccept(fileUpload -> {
              MessageHelper.replyToMessage(event, fileUpload);
              StringBuilder message = new StringBuilder();
              for (String playerID : activeGame.getRealPlayerIDs()) { //GET ALL PLAYER PINGS
                Member member = event.getGuild().getMemberById(playerID);
                if (member != null) message.append(member.getAsMention());
              }
              message.append("\nPlease provide a summary of the game below:");

              Helper.checkThreadLimitAndArchive(AsyncTI4DiscordBot.guildPrimary);
              // CREATE POST IN #THE-PBD-CHRONICLES
              if (publish && !AsyncTI4DiscordBot.guildPrimary.getTextChannelsByName("the-pbd-chronicles", true).isEmpty()) {
                TextChannel pbdChroniclesChannel = AsyncTI4DiscordBot.guildPrimary.getTextChannelsByName("the-pbd-chronicles", true).get(0);
                String channelMention = pbdChroniclesChannel == null ? "#the-pbd-chronicles" : pbdChroniclesChannel.getAsMention();
                if (pbdChroniclesChannel == null) {
                  BotLogger.log(event, "`#the-pbd-chronicles` channel not found - `/game end` cannot post summary");
                  return;
                }
                if (!activeGame.isFoWMode()) {
                  // INFORM PLAYERS
                  pbdChroniclesChannel.sendMessage(gameEndText).queue(m -> { //POST INITIAL MESSAGE
                    m.editMessageAttachments(fileUpload).queue(); //ADD MAP FILE TO MESSAGE
                    m.createThreadChannel(gameName).queueAfter(2, TimeUnit.SECONDS, t -> t.sendMessage(message.toString()).queue(null,
                        (error) -> BotLogger.log("Failure to create Game End thread for **" + activeGame.getName() + "** in PBD Chronicles:\n> " + error.getMessage()))); //CREATE THREAD AND POST FOLLOW UP
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Game summary has been posted in the " + channelMention + " channel: " + m.getJumpUrl());
                  });
                }
              }

              // TIGL Extras
              if (activeGame.isCompetitiveTIGLGame() && activeGame.getWinner().isPresent()) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), getTIGLFormattedGameEndText(activeGame, event));
              }

              // GET BOTHELPER LOUNGE
              TextChannel bothelperLoungeChannel = AsyncTI4DiscordBot.guildPrimary.getTextChannelsByName("staff-lounge", true).get(0);
              if (bothelperLoungeChannel == null) {
                BotLogger.log(event, "`#staff-lounge` channel not found - `/game end` cannot continue");
                return;
              }

              // MOVE CHANNELS TO IN-LIMBO
              Category inLimboCategory = event.getGuild().getCategoriesByName("The in-limbo PBD Archive", true).get(0);
              TextChannel tableTalkChannel = activeGame.getTableTalkChannel();
              TextChannel actionsChannel = activeGame.getMainGameChannel();
              if (inLimboCategory != null && archiveChannels) {
                if (inLimboCategory.getChannels().size() >= 47) { //HANDLE FULL IN-LIMBO
                  cleanUpInLimboCategory(event.getGuild(), 3);
                }

                String moveMessage = "Channel has been moved to Category **" + inLimboCategory.getName() + "** and will be automatically cleaned up shortly.";
                if (tableTalkChannel != null) { //MOVE TABLETALK CHANNEL
                  tableTalkChannel.getManager().setParent(inLimboCategory).queue();
                  MessageHelper.sendMessageToChannel(tableTalkChannel, moveMessage);
                }
                if (actionsChannel != null) { //MOVE ACTIONS CHANNEL
                  actionsChannel.getManager().setParent(inLimboCategory).queue();
                  MessageHelper.sendMessageToChannel(actionsChannel, moveMessage);
                }
              }

              // CLOSE THREADS IN CHANNELS
              if (tableTalkChannel != null) {
                for (ThreadChannel threadChannel : tableTalkChannel.getThreadChannels()) {
                  threadChannel.getManager().setArchived(true).queue();
                }
              }
              if (actionsChannel != null) {
                for (ThreadChannel threadChannel : actionsChannel.getThreadChannels()) {
                  threadChannel.getManager().setArchived(true).queue();
                }
              }

              // POST GAME END TO BOTHELPER LOUNGE GAME STARTS & ENDS THREAD
              List<ThreadChannel> threadChannels = bothelperLoungeChannel.getThreadChannels();
              String threadName = "game-starts-and-ends";
              for (ThreadChannel threadChannel_ : threadChannels) {
                if (threadChannel_.getName().equals(threadName)) {
                  MessageHelper.sendMessageToChannel(threadChannel_,
                      "Game: **" + gameName + "** on server **" + activeGame.getGuild().getName() + "** has concluded.");
                }
              }

              // send game json file to s3
              File jsonGameFile = Storage.getMapsJSONStorage(activeGame.getName() + ".json");
              boolean isWon = activeGame.getWinner().isPresent() && activeGame.isHasEnded();
              if (isWon) {
                WebHelper.putFile(activeGame.getName(), jsonGameFile);
              }
            });
    }

    public static String getGameEndText(Game game, GenericInteractionCreateEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("__**").append(game.getName()).append("**__ - ").append(game.getCustomName()).append("\n");
        sb.append(game.getCreationDate()).append(" - ").append(Helper.getDateRepresentation(game.getLastModifiedDate()));
        sb.append("\n");
        sb.append("\n");
        sb.append("**Players:**").append("\n");
        int index = 1;
        Optional<Player> winner = game.getWinner();
        for (Player player : game.getRealPlayers()) {
            Optional<User> user = Optional.ofNullable(event.getJDA().getUserById(player.getUserID()));
            int playerVP = player.getTotalVictoryPoints();
            sb.append("> `").append(index).append(".` ");
            sb.append(player.getFactionEmoji());
            sb.append(Emojis.getColorEmojiWithName(player.getColor())).append(" ");
            if (user.isPresent()) {
                sb.append(user.get().getAsMention());
            } else {
                sb.append(player.getUserName());
            }
            sb.append(" - *").append(playerVP).append("VP* ");
            if (winner.isPresent() && winner.get() == player) sb.append(" - **WINNER**");
            sb.append("\n");
            index++;
        }

        sb.append("\n");
        String gameModesText = game.getGameModesText();
        if (gameModesText.isEmpty()) gameModesText = "None";
        sb.append("**Game Modes:** ").append(gameModesText).append(", ")
            .append(game.getVp()).append(" victory points")
            .append("\n");

        if (winner.isPresent() && !game.hasHomebrew()) {
            List<Game> games = GameStatisticFilterer.getNormalFinishedGames(game.getRealPlayers().size(), game.getVp());
            Map<String, Integer> winningPathCounts = GameStats.getAllWinningPathCounts(games);
            int gamesWithWinnerCount = winningPathCounts.values().stream().reduce(0, Integer::sum);
            String winningPath = GameStats.getWinningPath(game, winner.get());
            sb.append("**Winning Path:** ").append(winningPath).append("\n");
            int winningPathCount = winningPathCounts.get(winningPath) - 1;
            double winningPathPercent = gamesWithWinnerCount == 0 ? 0 : winningPathCount / (double) gamesWithWinnerCount;
            sb.append("Out of ").append(gamesWithWinnerCount).append(" similar games, this path has been seen ").append(winningPathCount)
                .append(" times before! That's ").append(formatPercent(winningPathPercent)).append("% of games!").append("\n");
            if (winningPathCount == 0) {
              sb.append("🥳__**An async first! May your victory live on for all to see!**__🥳").append("\n");
            } else if (winningPathPercent <= .01) {
              sb.append("🤯__**Few have traveled your path! We celebrate your boldness!**__🤯").append("\n");
            } else if (winningPathPercent <= .02) {
              sb.append("🎉__**Who needs a conventional win? Not you! Good job!**__🎉").append("\n");
            }
        }

        return sb.toString();
    }

    private static String formatPercent(double d) {
        NumberFormat numberFormat = NumberFormat.getPercentInstance();
        numberFormat.setMinimumFractionDigits(1);
        return numberFormat.format(d);
    }

    public static String getTIGLFormattedGameEndText(Game activeGame, GenericInteractionCreateEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(Emojis.TIGL).append("TIGL\n\n");
        sb.append("This was a TIGL game! 👑").append(activeGame.getWinner().get().getPing()).append(", please [report the results](https://forms.gle/aACA16qcyG6j5NwV8):\n");
        sb.append("```\nMatch Start Date: ").append(Helper.getDateRepresentation(activeGame.getEndedDate())).append(" (TIGL wants Game End Date for Async)\n");
        sb.append("Match Start Time: 00:00\n\n");
        sb.append("Players:").append("\n");
        int index = 1;
        for (Player player : activeGame.getRealPlayers()) {
            int playerVP = player.getTotalVictoryPoints();
            Optional<User> user = Optional.ofNullable(event.getJDA().getUserById(player.getUserID()));
            sb.append("  ").append(index).append(". ");
            sb.append(player.getFaction()).append(" - ");
            if (user.isPresent()) {
                sb.append(user.get().getName());
            } else {
                sb.append(player.getUserName());
            }
            sb.append(" - ").append(playerVP).append(" VP\n");
            index++;
        }

        sb.append("\n");
        sb.append("Platform: Async\n");
        sb.append("Additional Notes: Async Game '").append(activeGame.getName());
        if (!StringUtils.isBlank(activeGame.getCustomName())) sb.append("   ").append(activeGame.getCustomName());
        sb.append("'\n```");

        return sb.toString();
    }

    public static void cleanUpInLimboCategory(Guild guild, int channelCountToDelete) {
        Category inLimboCategory = guild.getCategoriesByName("The in-limbo PBD Archive", true).get(0);
        if (inLimboCategory == null) {
            BotLogger.log("`GameEnd.cleanUpInLimboCategory`\nA clean up of in-limbo was attempted but could not find the **The in-limbo PBD Archive** category on server: " + guild.getName());
            return;
        }
        inLimboCategory.getTextChannels().stream()
            .sorted(Comparator.comparing(MessageChannel::getLatestMessageId))
            .limit(channelCountToDelete)
            .forEach(channel -> channel.delete().queue());
    }
}
