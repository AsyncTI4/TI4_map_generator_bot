package ti4.service.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.AsyncTI4DiscordBot;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.helpers.Helper;
import ti4.helpers.PlayerTitleHelper;
import ti4.helpers.RepositoryDispatchEvent;
import ti4.helpers.TIGLHelper;
import ti4.helpers.ThreadArchiveHelper;
import ti4.helpers.ThreadGetter;
import ti4.helpers.async.RoundSummaryHelper;
import ti4.image.MapRenderPipeline;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.GameMessageManager;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.service.emoji.ColorEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.statistics.game.WinningPathCacheService;
import ti4.service.statistics.game.WinningPathComparisonService;
import ti4.service.statistics.game.WinningPathHelper;
import ti4.service.tigl.TiglGameReport;
import ti4.service.tigl.TiglPlayerResult;
import ti4.website.UltimateStatisticsWebsiteHelper;

@UtilityClass
public class EndGameService {

    public static void secondHalfOfGameEnd(
            GenericInteractionCreateEvent event, Game game, boolean publish, boolean archiveChannels, boolean rematch) {
        String gameName = game.getName();
        List<Role> gameRoles = event.getGuild().getRolesByName(gameName, true);
        boolean deleteRole = true;
        if (gameRoles.size() > 1) {
            MessageHelper.replyToMessage(
                    event, "There are multiple roles that match this game name (" + gameName + "): " + gameRoles);
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Please call a @Bothelper to fix this before using `/game end`");
            return;
        } else if (gameRoles.isEmpty()) {
            MessageHelper.replyToMessage(
                    event, "No roles match the game name (" + gameName + ") - no role will be deleted.");
            deleteRole = false;
        }

        // Do not publish games that never really took off
        if (game.getRealPlayers().isEmpty() || game.getRound() == 1) publish = false;

        // ADD USER PERMISSIONS DIRECTLY TO CHANNEL
        Helper.addMapPlayerPermissionsToGameChannels(event.getGuild(), gameName);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(), "This game's channels' permissions have been updated.");

        // DELETE THE ROLE
        if (deleteRole && archiveChannels && !rematch) {
            Role gameRole = gameRoles.getFirst();
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "Role deleted: " + gameRole.getName() + " - use `/game ping` to ping all players");
            gameRole.delete().queue();

            if (game.isFowMode()) {
                List<Role> gmRoles = event.getGuild().getRolesByName(game.getName() + " GM", true);
                if (!gmRoles.isEmpty()) {
                    gmRoles.getFirst().delete().queue();
                }
            }
        }

        // MOVE CHANNELS TO IN-LIMBO
        List<Category> limbos = event.getGuild().getCategoriesByName("The in-limbo PBD Archive", true);
        Category inLimboCategory = limbos.isEmpty() ? null : limbos.getFirst();
        TextChannel tableTalkChannel = game.getTableTalkChannel();
        TextChannel actionsChannel = game.getMainGameChannel();
        Category og = actionsChannel.getParentCategory();
        if (!game.isFowMode() && inLimboCategory != null && archiveChannels && !rematch) {
            if (inLimboCategory.getChannels().size() >= 45) { // HANDLE FULL IN-LIMBO
                cleanUpInLimboCategory(event.getGuild(), 3);
            }

            String moveMessage = "Channel has been moved to Category **" + inLimboCategory.getName()
                    + "** and will be automatically cleaned up shortly.";
            if (tableTalkChannel != null) { // MOVE TABLETALK CHANNEL
                tableTalkChannel.getManager().setParent(inLimboCategory).queueAfter(15, TimeUnit.SECONDS);
                MessageHelper.sendMessageToChannel(tableTalkChannel, moveMessage);
            }
            if (actionsChannel != null) { // MOVE ACTIONS CHANNEL
                actionsChannel.getManager().setParent(inLimboCategory).queueAfter(15, TimeUnit.SECONDS);
                MessageHelper.sendMessageToChannel(actionsChannel, moveMessage);
            }
            if (og != null && og.getTextChannels().size() < 3) {
                og.delete().queueAfter(20, TimeUnit.SECONDS);
            }
        }

        // DELETE FOW CHANNELS
        if (game.isFowMode() && archiveChannels && !rematch) {
            List<Category> fogCategories = event.getGuild().getCategoriesByName(game.getName(), true);
            if (!fogCategories.isEmpty()) {
                Category fogCategory = fogCategories.getFirst();
                List<TextChannel> channels = new ArrayList<>(fogCategory.getTextChannels());
                // Delete channels with delay to avoid race condition and rate limits
                for (int i = 0; i < channels.size(); i++) {
                    TextChannel channel = channels.get(i);
                    channel.delete()
                            .queueAfter(
                                    2 + i,
                                    TimeUnit.SECONDS,
                                    success -> {},
                                    error -> BotLogger.warning("Failed to delete channel: " + channel.getName() + " - "
                                            + error.getMessage()));
                }
                // Delete category after all channels are scheduled for deletion
                fogCategory
                        .delete()
                        .queueAfter(
                                2 + channels.size(),
                                TimeUnit.SECONDS,
                                success -> {},
                                error -> BotLogger.warning("Failed to delete category: " + fogCategory.getName() + " - "
                                        + error.getMessage()));
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
                if (!threadChannel.getName().contains("Cards Info")) {
                    threadChannel.getManager().setArchived(true).queue();
                }
            }
        }
        gameEndStuff(game, event, publish);

        // GET BOTHELPER LOUNGE
        List<TextChannel> bothelperLoungeChannels =
                AsyncTI4DiscordBot.guildPrimary.getTextChannelsByName("staff-lounge", true);
        TextChannel bothelperLoungeChannel =
                !bothelperLoungeChannels.isEmpty() ? bothelperLoungeChannels.getFirst() : null;
        if (bothelperLoungeChannel != null) {
            // POST GAME END TO BOTHELPER LOUNGE GAME STARTS & ENDS THREAD
            String threadName = "game-starts-and-ends";
            ThreadGetter.getThreadInChannel(
                    bothelperLoungeChannel,
                    threadName,
                    threadChannel -> MessageHelper.sendMessageToChannel(
                            threadChannel,
                            "Game: **" + gameName + "** on server **"
                                    + game.getGuild().getName() + "** has concluded."));
        }

        // Archive Game Channels
        if (tableTalkChannel != null) {
            new RepositoryDispatchEvent("archive_game_channel", Map.of("channel", tableTalkChannel.getId()))
                    .sendEvent();
        }
        if (actionsChannel != null) {
            new RepositoryDispatchEvent("archive_game_channel", Map.of("channel", actionsChannel.getId())).sendEvent();
        }

        if (rematch) {
            RematchService.secondHalfOfRematch(event, game);
        }
    }

    public static void gameEndStuff(Game game, GenericInteractionCreateEvent event, boolean publish) {
        String gameName = game.getName();

        game.setHasEnded(true);
        game.setEndedDate(System.currentTimeMillis());
        game.setAutoPing(false);
        game.setAutoPingSpacer(0);

        GameMessageManager.remove(List.of(game.getName()));

        if (!game.getRealPlayers().isEmpty() && !game.isFowMode() && !game.islandMode()) {
            PlayerTitleHelper.offerEveryoneTitlePossibilities(game);
        }

        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "**Game: `" + gameName + "` has ended!**");

        writeChronicle(game, event, publish);
        WinningPathCacheService.addGame(game);
    }

    private static void writeChronicle(Game game, GenericInteractionCreateEvent event, boolean publish) {
        String gameEndText = getGameEndText(game, event);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), gameEndText);
        TextChannel summaryChannel = getGameSummaryChannel(game);
        if (!game.isFowMode()) {
            MapRenderPipeline.queue(game, event, DisplayType.all, fileUpload -> {
                MessageHelper.replyToMessage(event, fileUpload);
                // CREATE POST
                if (publish) {
                    if (summaryChannel == null) {
                        BotLogger.warning(
                                new BotLogger.LogMessageOrigin(event),
                                "`#the-pbd-chronicles` channel not found - `/game end` cannot post summary");
                        return;
                    }

                    // INFORM PLAYERS
                    MessageHelper.splitAndSentWithAction(
                            gameEndText,
                            summaryChannel,
                            m -> { // POST INITIAL MESSAGE
                                m.editMessageAttachments(fileUpload).queue(); // ADD MAP FILE TO MESSAGE
                                m.createThreadChannel(game.getName()).queueAfter(2, TimeUnit.SECONDS, t -> {
                                    sendFeedbackMessage(t, game);
                                    sendRoundSummariesToThread(t, game);
                                });
                                MessageHelper.sendMessageToChannel(
                                        event.getMessageChannel(),
                                        "Game summary has been posted in the " + summaryChannel.getAsMention()
                                                + " channel: " + m.getJumpUrl());
                            });
                }

                // TIGL Extras
                if (game.isCompetitiveTIGLGame() && game.getWinner().isPresent()) {
                    MessageHelper.sendMessageToChannel(
                            event.getMessageChannel(), getTIGLFormattedGameEndText(game, event));
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), MiscEmojis.BLT + Constants.bltPing());
                    TIGLHelper.checkIfTIGLRankUpOnGameEnd(game);
                    if (!game.isReplacementMade()) {
                        UltimateStatisticsWebsiteHelper.sendTiglGameReport(
                                buildTiglReport(game), event.getMessageChannel());
                    } else {
                        MessageHelper.sendMessageToChannel(
                                event.getMessageChannel(),
                                "This game had a replacement. Please report the results manually: https://www.ti4ultimate.com/community/tigl/report-game");
                    }
                }
            });
        } else if (publish) { // FOW SUMMARY
            if (summaryChannel == null) {
                BotLogger.warning(
                        new BotLogger.LogMessageOrigin(event),
                        "`#fow-war-stories` channel not found - `/game end` cannot post summary");
                return;
            }
            MessageHelper.sendMessageToChannel(summaryChannel, gameEndText);
            summaryChannel.createThreadChannel(game.getName(), true).queue(t -> {
                MessageHelper.sendMessageToChannel(t, gameEndText);
                sendFeedbackMessage(t, game);
                sendRoundSummariesToThread(t, game);
            });
        }
    }

    private static void sendRoundSummariesToThread(ThreadChannel t, Game game) {
        StringBuilder endOfGameSummary = new StringBuilder();

        for (int x = 1; x < game.getRound() + 1; x++) {
            StringBuilder summary = new StringBuilder();
            for (Player player : game.getPlayers().values()) {
                String summaryKey = RoundSummaryHelper.resolveRoundSummaryKey(player, String.valueOf(x));
                if (!game.getStoredValue(summaryKey).isEmpty()) {
                    summary.append(RoundSummaryHelper.resolvePlayerEmoji(player))
                            .append(": ")
                            .append(game.getStoredValue(summaryKey))
                            .append("\n");
                }
            }
            if (!summary.isEmpty()) {
                summary.insert(0, "**__Round " + x + " Secret Objective Summary__**\n");
                endOfGameSummary.append(summary);
            }
        }
        if (!endOfGameSummary.isEmpty()) {
            MessageHelper.sendMessageToChannel(t, endOfGameSummary.toString());
        }
    }

    private static void sendFeedbackMessage(ThreadChannel t, Game game) {
        StringBuilder message = new StringBuilder();
        for (String playerID : game.getRealPlayerIDs()) { // GET ALL PLAYER PINGS
            Member member = game.getGuild().getMemberById(playerID);
            if (member != null) message.append(member.getAsMention()).append(" ");
        }
        message.append(
                "\nPlease provide a summary of the game below. You can also leave anonymous feedback on the bot [here](https://forms.gle/EvoWpRS4xEXqtNRa9)");

        MessageHelper.sendMessageToChannel(t, message.toString());
    }

    private static TextChannel getGameSummaryChannel(Game game) {
        List<TextChannel> textChannels;
        if (game.isFowMode() && AsyncTI4DiscordBot.guildFogOfWar != null) {
            ThreadArchiveHelper.checkThreadLimitAndArchive(AsyncTI4DiscordBot.guildFogOfWar);
            textChannels = AsyncTI4DiscordBot.guildFogOfWar.getTextChannelsByName("fow-war-stories", true);
        } else {
            ThreadArchiveHelper.checkThreadLimitAndArchive(AsyncTI4DiscordBot.guildPrimary);
            textChannels = AsyncTI4DiscordBot.guildPrimary.getTextChannelsByName("the-pbd-chronicles", true);
        }
        return textChannels.isEmpty() ? null : textChannels.getFirst();
    }

    private static void appendUserName(StringBuilder sb, Player player, GenericInteractionCreateEvent event) {
        Optional<User> user = Optional.ofNullable(event.getJDA().getUserById(player.getUserID()));
        if (user.isPresent()) {
            sb.append(user.get().getAsMention());
        } else {
            sb.append(player.getUserName());
        }
    }

    private static String getGameEndText(Game game, GenericInteractionCreateEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("**Game: __").append(game.getName()).append("__**");
        if (!game.getCustomName().isEmpty()) {
            sb.append(" - ").append(game.getCustomName());
        }
        sb.append("\n");
        sb.append("**Duration:** ");
        sb.append(game.getCreationDate())
                .append(" - ")
                .append(Helper.getDateRepresentation(game.getLastModifiedDate()));
        sb.append("\n");
        sb.append("\n");
        sb.append("**Players:**").append("\n");
        int index = 1;
        for (Player player : game.getRealAndEliminatedPlayers()) {
            sb.append("> `").append(index).append(".` ");
            sb.append(player.getFactionEmoji());
            sb.append(ColorEmojis.getColorEmojiWithName(player.getColor())).append(" ");
            appendUserName(sb, player, event);
            sb.append(" - *");
            if (player.isEliminated()) {
                sb.append("ELIMINATED*");
            } else {
                int playerVP = player.getTotalVictoryPoints();
                sb.append(playerVP).append("VP* ");
            }
            if (game.hasWinner()) {
                for (Player winner2 : game.getWinners()) {
                    if (winner2 == player) sb.append(" **👑WINNER👑**");
                }
            }
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
        if (gameModesText.isEmpty()) gameModesText = "None";
        int vpCount = game.getVp();
        sb.append("**Game Modes:** ")
                .append(gameModesText)
                .append(", ")
                .append(vpCount)
                .append(" victory points")
                .append("\n");

        var winner = game.getWinner();
        if (winner.isPresent() && game.isNormalGame()) {
            String winningPath = WinningPathHelper.buildWinningPath(game, winner.get());
            sb.append("**Winning Path:** ").append(winningPath).append("\n");
            String comparison = WinningPathComparisonService.compareWinningPathToAllOthers(
                    winningPath, game.getRealAndEliminatedPlayers().size(), vpCount);
            sb.append(comparison);
        }

        return sb.toString();
    }

    private static String getTIGLFormattedGameEndText(Game game, GenericInteractionCreateEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(MiscEmojis.TIGL).append("TIGL\n\n");
        sb.append("This was a TIGL game! 👑")
                .append(game.getWinner().get().getPing())
                .append(", please [report the results](https://forms.gle/aACA16qcyG6j5NwV8):\n");
        sb.append("```\nMatch Start Date: ")
                .append(Helper.getDateRepresentationTIGL(game.getEndedDate()))
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
        if (!StringUtils.isBlank(game.getCustomName())) sb.append("   ").append(game.getCustomName());
        sb.append("'\n```");

        return sb.toString();
    }

    private static TiglGameReport buildTiglReport(Game game) {
        var report = new TiglGameReport();
        report.setGameId(game.getID());
        report.setScore(game.getVp());

        var tiglPlayerResults = game.getRealPlayers().stream()
                .map(player -> {
                    var tiglPlayerResult = new TiglPlayerResult();
                    tiglPlayerResult.setScore(player.getTotalVictoryPoints());
                    if (player.getFactionModel() != null) {
                        tiglPlayerResult.setFaction(player.getFactionModel().getFactionName());
                    } else {
                        tiglPlayerResult.setFaction(player.getFaction());
                    }
                    tiglPlayerResult.setDiscordId(player.getUserID());
                    tiglPlayerResult.setDiscordTag(player.getUserName());
                    return tiglPlayerResult;
                })
                .toList();

        report.setPlayerResults(tiglPlayerResults);
        report.setSource("Async");
        report.setTimestamp(System.currentTimeMillis() / 1000);
        return report;
    }

    private static void cleanUpInLimboCategory(Guild guild, int channelCountToDelete) {
        Category inLimboCategory =
                guild.getCategoriesByName("The in-limbo PBD Archive", true).getFirst();
        if (inLimboCategory == null) {
            BotLogger.warning(
                    guild,
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
