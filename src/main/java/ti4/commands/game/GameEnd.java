package ti4.commands.game;

import static ti4.helpers.StringHelper.ordinal;

import java.io.File;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;

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
import ti4.AsyncTI4DiscordBot;
import ti4.commands.statistics.GameStatisticFilterer;
import ti4.commands.statistics.GameStats;
import ti4.generator.MapGenerator;
import ti4.helpers.ButtonHelper;
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
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.REMATCH, "True to start another game using the same channels (Default: False)"));
    }

    public void execute(SlashCommandInteractionEvent event) {
        GameManager gameManager = GameManager.getInstance();
        Game game = gameManager.getUserActiveGame(event.getUser().getId());

        if (game == null) {
            MessageHelper.replyToMessage(event, "Must set active Game");
            return;
        }
        String gameName = game.getName();
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
        boolean rematch = event.getOption(Constants.REMATCH, false, OptionMapping::getAsBoolean);
        secondHalfOfGameEnd(event, game, publish, archiveChannels, rematch);
    }

    public static void secondHalfOfGameEnd(GenericInteractionCreateEvent event, Game game, boolean publish, boolean archiveChannels, boolean rematch) {
        String gameName = game.getName();
        List<Role> gameRoles = event.getGuild().getRolesByName(gameName, true);
        boolean deleteRole = true;
        if (gameRoles.size() > 1) {
            MessageHelper.replyToMessage(event,
                "There are multiple roles that match this game name (" + gameName + "): " + gameRoles);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "Please call a @Bothelper to fix this before using `/game end`");
            return;
        } else if (gameRoles.isEmpty()) {
            MessageHelper.replyToMessage(event, "No roles match the game name (" + gameName + ") - no role will be deleted.");
            deleteRole = false;
        }

        // ADD USER PERMISSIONS DIRECTLY TO CHANNEL
        Helper.addMapPlayerPermissionsToGameChannels(event.getGuild(), game);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            "This game's channels' permissions have been updated.");

        // DELETE THE ROLE
        if (deleteRole && archiveChannels) {
            Role gameRole = gameRoles.get(0);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "Role deleted: " + gameRole.getName() + " - use `/game ping` to ping all players");
            gameRole.delete().queue();

            if (game.isFowMode()) {
                List<Role> gmRoles = event.getGuild().getRolesByName(game.getName() + " GM", true);
                if (!gmRoles.isEmpty()) {
                    gmRoles.get(0).delete().queue();
                }
            }
        }

        gameEndStuff(game, event, publish);
        // MOVE CHANNELS TO IN-LIMBO
        List<Category> limbos = event.getGuild().getCategoriesByName("The in-limbo PBD Archive", true);
        Category inLimboCategory = limbos.isEmpty() ? null : limbos.get(0);
        TextChannel tableTalkChannel = game.getTableTalkChannel();
        TextChannel actionsChannel = game.getMainGameChannel();
        if (inLimboCategory != null && archiveChannels) {
            if (inLimboCategory.getChannels().size() >= 45) { // HANDLE FULL IN-LIMBO
                cleanUpInLimboCategory(event.getGuild(), 3);
            }

            String moveMessage = "Channel has been moved to Category **" + inLimboCategory.getName()
                + "** and will be automatically cleaned up shortly.";
            if (tableTalkChannel != null) { // MOVE TABLETALK CHANNEL
                tableTalkChannel.getManager().setParent(inLimboCategory).queue();
                MessageHelper.sendMessageToChannel(tableTalkChannel, moveMessage);
            }
            if (actionsChannel != null) { // MOVE ACTIONS CHANNEL
                actionsChannel.getManager().setParent(inLimboCategory).queue();
                MessageHelper.sendMessageToChannel(actionsChannel, moveMessage);
            }
        }

        //DELETE FOW CHANNELS
        if (game.isFowMode() && archiveChannels) {
            Category fogCategory = event.getGuild().getCategoriesByName(game.getName(), true).get(0);
            if (fogCategory != null) {
                List<TextChannel> channels = new ArrayList<>();
                channels.addAll(fogCategory.getTextChannels());
                //Delay deletion so end of game messages have time to go through
                for (TextChannel channel : channels) {
                    channel.delete().queueAfter(2, TimeUnit.SECONDS);
                }
                fogCategory.delete().queueAfter(2, TimeUnit.SECONDS);
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
                if (threadChannel.getName().contains("Cards Info")) {
                    continue;
                } else {
                    threadChannel.getManager().setArchived(true).queue();
                }
            }
        }

        // GET BOTHELPER LOUNGE
        List<TextChannel> bothelperLoungeChannels = AsyncTI4DiscordBot.guildPrimary.getTextChannelsByName("staff-lounge", true);
        TextChannel bothelperLoungeChannel = bothelperLoungeChannels.size() > 0 ? bothelperLoungeChannels.get(0) : null;
        if (bothelperLoungeChannel != null) {
            // POST GAME END TO BOTHELPER LOUNGE GAME STARTS & ENDS THREAD
            List<ThreadChannel> threadChannels = bothelperLoungeChannel.getThreadChannels();
            String threadName = "game-starts-and-ends";
            for (ThreadChannel threadChannel_ : threadChannels) {
                if (threadChannel_.getName().equals(threadName)) {
                    MessageHelper.sendMessageToChannel(threadChannel_, "Game: **" + gameName + "** on server **" + game.getGuild().getName() + "** has concluded.");
                }
            }
        }

        // send game json file to s3
        GameSaveLoadManager.saveMapJson(game);
        File jsonGameFile = Storage.getMapsJSONStorage(game.getName() + ".json");
        boolean isWon = game.getWinner().isPresent() && game.isHasEnded();
        if (isWon) {
            WebHelper.putFile(game.getName(), jsonGameFile);
        }

        if (rematch) {
            ButtonHelper.secondHalfOfRematch(event, game);
        }
    }

    public static void gameEndStuff(Game game, GenericInteractionCreateEvent event, boolean publish) {
        String gameName = game.getName();
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "**Game: `" + gameName + "` has ended!**");
        game.setHasEnded(true);
        game.setEndedDate(new Date().getTime());
        GameSaveLoadManager.saveMap(game, event);
        String gameEndText = getGameEndText(game, event);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), gameEndText);
        game.setAutoPing(false);
        game.setAutoPingSpacer(0);
        if (!game.isFowMode()) {
            ButtonHelper.offerEveryoneTitlePossibilities(game);
        }

        TextChannel summaryChannel = getGameSummaryChannel(game);
        if (!game.isFowMode()) {
            // SEND THE MAP IMAGE
            MapGenerator.saveImage(game, DisplayType.all, event).thenAccept(fileUpload -> {
                MessageHelper.replyToMessage(event, fileUpload);
                // CREATE POST
                if (publish) {
                    if (summaryChannel == null) {
                        BotLogger.log(event, "`#the-pbd-chronicles` channel not found - `/game end` cannot post summary");
                        return;
                    }

                    // INFORM PLAYERS
                    summaryChannel.sendMessage(gameEndText).queue(m -> { // POST INITIAL MESSAGE
                        m.editMessageAttachments(fileUpload).queue(); // ADD MAP FILE TO MESSAGE
                        m.createThreadChannel(gameName).queueAfter(2, TimeUnit.SECONDS,
                          t -> {
                            sendFeedbackMessage(t, game);
                            sendRoundSummariesToThread(t, game);
                          });
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                            "Game summary has been posted in the " + summaryChannel.getAsMention() + " channel: " + m.getJumpUrl());
                    });
                }

                // TIGL Extras
                if (game.isCompetitiveTIGLGame() && game.getWinner().isPresent()) {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                        getTIGLFormattedGameEndText(game, event));
                    String blt = Constants.bltPing();
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), blt + " bot has been told to ping you when TIGL games end");
                }
            });
        } else if (publish) { //FOW SUMMARY
            if (summaryChannel == null) {
                BotLogger.log(event, "`#fow-war-stories` channel not found - `/game end` cannot post summary");
                return;
            }
            MessageHelper.sendMessageToChannel(summaryChannel, gameEndText);
            summaryChannel.createThreadChannel(gameName, true).queue( 
                t -> { 
                    MessageHelper.sendMessageToChannel(t, gameEndText);
                    sendFeedbackMessage(t, game);
                    sendRoundSummariesToThread(t, game);
            });
        }
    }

    private static void sendRoundSummariesToThread(ThreadChannel t, Game game) {
        String endOfGameSummary = "";

        for (int x = 1; x < game.getRound() + 1; x++) {
            String summary = "";
            for (Player player : game.getRealPlayers()) {
                String summaryKey = "endofround" + x + player.getFaction();
                if (!game.getStoredValue(summaryKey).isEmpty()) {
                    summary += player.getFactionEmoji() + ": " + game.getStoredValue(summaryKey) + "\n";
                }
            }
            if (!summary.isEmpty()) {
                summary = "**__Round " + x + " Secret Summary__**\n" + summary;
                endOfGameSummary = endOfGameSummary + summary;
            }
        }
        if (!endOfGameSummary.isEmpty()) {
            MessageHelper.sendMessageToChannel(t, endOfGameSummary);
        }
    }

    private static void sendFeedbackMessage(ThreadChannel t, Game game) {
        StringBuilder message = new StringBuilder();
        for (String playerID : game.getRealPlayerIDs()) { // GET ALL PLAYER PINGS
            Member member = game.getGuild().getMemberById(playerID);
            if (member != null)
                message.append(member.getAsMention()).append(" ");
        }
        message.append(
            "\nPlease provide a summary of the game below. You can also leave anonymous feedback on the bot [here](https://forms.gle/EvoWpRS4xEXqtNRa9)");
        
        MessageHelper.sendMessageToChannel(t, message.toString());
    }

    private static TextChannel getGameSummaryChannel(Game game) {
        List<TextChannel> textChannels = null;
        if (game.isFowMode() && AsyncTI4DiscordBot.guildFogOfWar != null) {
            Helper.checkThreadLimitAndArchive(AsyncTI4DiscordBot.guildFogOfWar);
            textChannels = AsyncTI4DiscordBot.guildFogOfWar.getTextChannelsByName("fow-war-stories", true);
        } else {
            Helper.checkThreadLimitAndArchive(AsyncTI4DiscordBot.guildPrimary);
            textChannels = AsyncTI4DiscordBot.guildPrimary.getTextChannelsByName("the-pbd-chronicles", true);
        }
        return textChannels.isEmpty() ? null : textChannels.get(0);
    }

    private static void appendUserName(StringBuilder sb, Player player, GenericInteractionCreateEvent event) {
        Optional<User> user = Optional.ofNullable(event.getJDA().getUserById(player.getUserID()));
        if (user.isPresent()) {
            sb.append(user.get().getAsMention());
        } else {
            sb.append(player.getUserName());
        }
    }

    public static String getGameEndText(Game game, GenericInteractionCreateEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("**Game: __").append(game.getName()).append("__**");
        if (!game.getCustomName().isEmpty()) {
            sb.append(" - ").append(game.getCustomName());
        }
        sb.append("\n");
        sb.append("**Duration:** ");
        sb.append(game.getCreationDate()).append(" - ").append(Helper.getDateRepresentation(game.getLastModifiedDate()));
        sb.append("\n");
        sb.append("\n");
        sb.append("**Players:**").append("\n");
        int index = 1;
        Optional<Player> winner = game.getWinner();
        for (Player player : game.getRealAndEliminatedPlayers()) {
            sb.append("> `").append(index).append(".` ");
            sb.append(player.getFactionEmoji());
            sb.append(Emojis.getColorEmojiWithName(player.getColor())).append(" ");
            appendUserName(sb, player, event);
            sb.append(" - *");
            if (player.isEliminated()) {
                sb.append("ELIMINATED*");
            } else {
                int playerVP = player.getTotalVictoryPoints();
                sb.append(playerVP).append("VP* ");
            }
            if (winner.isPresent() && winner.get() == player)
                sb.append(" - **WINNER**");
            sb.append("\n");
            index++;
        }

        sb.append("\n");
        if (game.isFowMode()) {
            sb.append("**GM:** ");
            for (Player gm : game.getPlayersWithGMRole()) {
                appendUserName(sb, gm, event);
                sb.append(" ");
            }
            sb.append("\n");
        }

        String gameModesText = game.getGameModesText();
        if (gameModesText.isEmpty())
            gameModesText = "None";
        sb.append("**Game Modes:** ").append(gameModesText).append(", ")
            .append(game.getVp()).append(" victory points")
            .append("\n");

        if (winner.isPresent() && !game.hasHomebrew()) {
            String winningPath = GameStats.getWinningPath(game, winner.get());
            sb.append("**Winning Path:** ").append(winningPath).append("\n");
            int playerCount = game.getRealAndEliminatedAndDummyPlayers().size();
            List<Game> games = GameStatisticFilterer.getNormalFinishedGames(playerCount, game.getVp());
            Map<String, Integer> winningPathCounts = GameStats.getAllWinningPathCounts(games);
            int gamesWithWinnerCount = winningPathCounts.values().stream().reduce(0, Integer::sum);
            if (gamesWithWinnerCount >= 100) {
                int winningPathCount = winningPathCounts.get(winningPath);
                double winningPathPercent = winningPathCount / (double) gamesWithWinnerCount;
                String winningPathCommonality = getWinningPathCommonality(winningPathCounts, winningPathCount);
                sb.append("Out of ").append(gamesWithWinnerCount).append(" similar games (").append(game.getVp()).append("VP, ")
                    .append(playerCount).append("P)")
                    .append(", this path has been seen ")
                    .append(winningPathCount - 1)
                    .append(" times before. It's the ").append(winningPathCommonality).append("most common path at ")
                    .append(formatPercent(winningPathPercent)).append(" of games.").append("\n");
                if (winningPathCount == 1) {
                    sb.append("🥳__**An async first! May your victory live on for all to see!**__🥳").append("\n");
                } else if (winningPathPercent <= .005) {
                    sb.append("🎉__**Few have traveled your path! We celebrate your boldness!**__🎉").append("\n");
                } else if (winningPathPercent <= .01) {
                    sb.append("🎉__**Who needs a conventional win? Not you!**__🎉").append("\n");
                }
            }
        }

        return sb.toString();
    }

    private static String getWinningPathCommonality(Map<String, Integer> winningPathCounts, int winningPathCount) {
        int commonality = 1;
        for (int i : winningPathCounts.values()) {
            if (i > winningPathCount) {
                commonality++;
            }
        }
        return commonality == 1 ? "" : ordinal(commonality) + " ";
    }

    private static String formatPercent(double d) {
        NumberFormat numberFormat = NumberFormat.getPercentInstance();
        numberFormat.setMinimumFractionDigits(1);
        return numberFormat.format(d);
    }

    public static String getTIGLFormattedGameEndText(Game game, GenericInteractionCreateEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(Emojis.TIGL).append("TIGL\n\n");
        sb.append("This was a TIGL game! 👑").append(game.getWinner().get().getPing())
            .append(", please [report the results](https://forms.gle/aACA16qcyG6j5NwV8):\n");
        sb.append("```\nMatch Start Date: ").append(Helper.getDateRepresentationTIGL(game.getEndedDate()))
            .append(" (TIGL wants Game End Date for Async)\n");
        sb.append("Match Start Time: 00:00\n\n");
        sb.append("Players:").append("\n");
        int index = 1;
        for (Player player : game.getRealPlayers()) {
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
        sb.append("Additional Notes: Async Game '").append(game.getName());
        if (!StringUtils.isBlank(game.getCustomName()))
            sb.append("   ").append(game.getCustomName());
        sb.append("'\n```");

        return sb.toString();
    }

    public static void cleanUpInLimboCategory(Guild guild, int channelCountToDelete) {
        Category inLimboCategory = guild.getCategoriesByName("The in-limbo PBD Archive", true).get(0);
        if (inLimboCategory == null) {
            BotLogger.log(
                "`GameEnd.cleanUpInLimboCategory`\nA clean up of in-limbo was attempted but could not find the **The in-limbo PBD Archive** category on server: "
                    + guild.getName());
            return;
        }
        inLimboCategory.getTextChannels().stream()
            .sorted(Comparator.comparing(MessageChannel::getLatestMessageId))
            .limit(channelCountToDelete)
            .forEach(channel -> channel.delete().queue());
    }
}
