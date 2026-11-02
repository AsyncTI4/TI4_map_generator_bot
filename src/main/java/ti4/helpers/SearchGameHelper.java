package ti4.helpers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IDeferrableCallback;
import org.apache.commons.lang3.function.Consumers;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedGame;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.ColorEmojis;
import ti4.service.game.ManagedGameService;
import ti4.service.game.TeamSeatService;
import ti4.service.game.TeamSeatService.FogSeat;

@UtilityClass
public class SearchGameHelper {

    public static int searchGames(
            User user,
            GenericInteractionCreateEvent event,
            boolean onlyMyTurn,
            boolean includeEndedGames,
            boolean showAverageTurnTime,
            boolean showSecondaries,
            boolean showGameModes,
            boolean ignoreSpectate,
            boolean ignoreAborted,
            boolean wantNum) {
        return searchGames(
                user,
                event,
                onlyMyTurn,
                includeEndedGames,
                showAverageTurnTime,
                showSecondaries,
                showGameModes,
                ignoreSpectate,
                ignoreAborted,
                wantNum,
                null);
    }

    public static int searchGames(
            User user,
            GenericInteractionCreateEvent event,
            boolean onlyMyTurn,
            boolean includeEndedGames,
            boolean showAverageTurnTime,
            boolean showSecondaries,
            boolean showGameModes,
            boolean ignoreSpectate,
            boolean ignoreAborted,
            boolean wantNum,
            User user2) {
        return searchGames(
                user,
                event,
                onlyMyTurn,
                includeEndedGames,
                showAverageTurnTime,
                showSecondaries,
                showGameModes,
                ignoreSpectate,
                ignoreAborted,
                wantNum,
                user2,
                false);
    }

    public static int searchGames(
            User user,
            GenericInteractionCreateEvent event,
            boolean onlyMyTurn,
            boolean includeEndedGames,
            boolean showAverageTurnTime,
            boolean showSecondaries,
            boolean showGameModes,
            boolean ignoreSpectate,
            boolean ignoreAborted,
            boolean wantNum,
            User user2,
            boolean ephemeral) {
        String userID = user.getId();

        Predicate<ManagedGame> allFilterPredicates =
                gameFilter(userID, onlyMyTurn, includeEndedGames, ignoreSpectate, ignoreAborted, false);
        if (user2 != null) {
            allFilterPredicates = allFilterPredicates.and(participationFilter(user2.getId(), ignoreSpectate));
        }
        var filteredManagedGames = filterAndSort(allFilterPredicates);

        int index = 1;
        if (wantNum) {
            return filteredManagedGames.size();
        }
        StringBuilder sb = new StringBuilder("**__").append(user.getName()).append("'s Games__**\n");
        for (var managedGame : filteredManagedGames) {
            sb.append('`').append(Helper.leftpad("" + index, 2)).append(".`");
            var game = managedGame.getGame();
            sb.append(
                    getPlayerMapListRepresentation(game, userID, showAverageTurnTime, showSecondaries, showGameModes));
            sb.append('\n');
            index++;
        }
        String fogGameList = user2 == null
                ? fogGameList(user, event, onlyMyTurn, showAverageTurnTime, showSecondaries, showGameModes)
                : "";
        if (ephemeral) {
            sendEphemeral(event, sb + fogGameList);
            return filteredManagedGames.size();
        }
        if (event instanceof SlashCommandInteractionEvent slash) {
            MessageHelper.sendMessageToThread(slash.getChannel(), user.getName() + "'s Game List", sb.toString());
        }
        if (event instanceof ButtonInteractionEvent butt) {
            MessageHelper.sendMessageToThread(butt.getChannel(), user.getName() + "'s Game List", sb.toString());
        }
        if (!fogGameList.isEmpty()) {
            sendEphemeral(event, fogGameList);
        } else if (event instanceof SlashCommandInteractionEvent slash) {
            slash.getHook().deleteOriginal().queue(Consumers.nop(), BotLogger::catchRestError);
        }
        return filteredManagedGames.size();
    }

    private static String fogGameList(
            User user,
            GenericInteractionCreateEvent event,
            boolean onlyMyTurn,
            boolean showAverageTurnTime,
            boolean showSecondaries,
            boolean showGameModes) {
        List<FogSeat> seats = TeamSeatService.findSharedActiveFogSeats(
                        user.getId(), event.getUser().getId())
                .stream()
                .filter(seat -> !onlyMyTurn
                        || seat.seat().getUserID().equals(seat.game().getActivePlayerID()))
                .toList();
        if (seats.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n**__Fog games (only visible to you)__**\n");
        int index = 1;
        for (FogSeat seat : seats) {
            TextChannel privateChannel = seat.seat().getPrivateChannel();
            String channelLink = privateChannel == null ? "" : privateChannel.getAsMention();
            sb.append('`').append(Helper.leftpad("" + index, 2)).append(".`");
            sb.append(getPlayerMapListRepresentation(
                    seat.game(), seat.seat(), channelLink, showAverageTurnTime, showSecondaries, showGameModes));
            sb.append('\n');
            index++;
        }
        return sb.toString();
    }

    private static void sendEphemeral(GenericInteractionCreateEvent event, String text) {
        if (!(event instanceof IDeferrableCallback callback)) {
            return;
        }
        for (String chunk : MessageHelper.splitLargeText(text, 2000)) {
            callback.getHook().sendMessage(chunk).setEphemeral(true).queue(Consumers.nop(), BotLogger::catchRestError);
        }
    }

    public static void msgGames(
            User user,
            boolean onlyMyTurn,
            boolean includeEndedGames,
            boolean ignoreSpectate,
            boolean ignoreAborted,
            boolean pingGame,
            String msg) {

        var filteredManagedGames = filterAndSort(
                gameFilter(user.getId(), onlyMyTurn, includeEndedGames, ignoreSpectate, ignoreAborted, false));

        for (var managedGame : filteredManagedGames) {
            if (managedGame.getTableTalkChannel() != null) {
                String name = user.getName();
                if (managedGame.getTableTalkChannel().getGuild().getMemberById(user.getId()) != null) {
                    Member mem = managedGame.getTableTalkChannel().getGuild().getMemberById(user.getId());
                    name = mem.getNickname();
                    if (name == null) {
                        name = mem.getEffectiveName();
                    }
                }
                String msg2 = "The player " + name + " sends the following msg:\n" + msg;
                if (pingGame) {
                    msg2 = managedGame.getGame().getPing() + " " + msg2;
                }
                MessageHelper.sendMessageToChannel(managedGame.getTableTalkChannel(), msg2);
            }
        }
    }

    public static List<ManagedGame> findGamesIncludingActiveFog(
            String userId, boolean includeEndedGames, boolean ignoreAborted) {
        return filterAndSort(gameFilter(userId, false, includeEndedGames, false, ignoreAborted, true));
    }

    private static List<ManagedGame> filterAndSort(Predicate<ManagedGame> filter) {
        return GameManager.getManagedGames().stream()
                .filter(filter)
                .sorted(Comparator.comparing(ManagedGameService::getGameNameForSorting))
                .toList();
    }

    static Predicate<ManagedGame> gameFilter(
            String userId,
            boolean onlyMyTurn,
            boolean includeEndedGames,
            boolean ignoreSpectate,
            boolean ignoreAborted,
            boolean includeActiveFog) {
        Predicate<ManagedGame> onlyMyTurnFilter = onlyMyTurn
                ? game -> Objects.equals(game.getActivePlayerId(), game.getSeatUserId(userId))
                : game -> true;
        Predicate<ManagedGame> endedGamesFilter = includeEndedGames ? game -> true : game -> !game.isHasEnded();
        Predicate<ManagedGame> activeFogFilter =
                includeActiveFog ? game -> true : game -> !game.isFowMode() || game.isHasEnded();
        Predicate<ManagedGame> ignoreAbortedFilter =
                ignoreAborted ? game -> !game.isHasEnded() || game.isHasWinner() : game -> true;
        return participationFilter(userId, ignoreSpectate)
                .and(onlyMyTurnFilter)
                .and(endedGamesFilter)
                .and(activeFogFilter)
                .and(ignoreAbortedFilter);
    }

    private static Predicate<ManagedGame> participationFilter(String userId, boolean ignoreSpectate) {
        return ignoreSpectate
                ? game -> game.getRealPlayers().stream()
                        .anyMatch(player -> player.getId().equals(game.getSeatUserId(userId)))
                : game -> game.getPlayers().stream()
                        .anyMatch(player -> player.getId().equals(game.getSeatUserId(userId)));
    }

    private static String getPlayerMapListRepresentation(
            Game game, String userID, boolean showAverageTurnTime, boolean showSecondaries, boolean showGameModes) {
        Player player = TeamSeatService.seatOf(game, userID);
        if (player == null) return "";
        String gameChannelLink =
                game.getActionsChannel() == null ? "" : game.getActionsChannel().getAsMention();
        return getPlayerMapListRepresentation(
                game, player, gameChannelLink, showAverageTurnTime, showSecondaries, showGameModes);
    }

    private static String getPlayerMapListRepresentation(
            Game game,
            Player player,
            String gameChannelLink,
            boolean showAverageTurnTime,
            boolean showSecondaries,
            boolean showGameModes) {
        StringBuilder sb = new StringBuilder();
        if (Mapper.isValidFaction(player.getFaction())) sb.append(player.getFactionEmoji());
        if (player.getColor() != null && !"null".equals(player.getColor()))
            sb.append(ColorEmojis.getColorEmoji(player.getColor()));
        sb.append(" `").append(game.getName()).append("` ");
        sb.append(gameChannelLink);
        if (showAverageTurnTime)
            sb.append("  [Average Turn Time: `")
                    .append(playerAverageMapTurnLength(player))
                    .append("`]");
        if (game.hasWinner()) {
            for (Player winner : game.getWinners()) {
                if (winner.getUserID().equals(player.getUserID())) sb.append(" **👑WINNER👑**");
            }
        }
        if (game.getActivePlayerID() != null
                && game.getActivePlayerID().equals(player.getUserID())
                && !game.isHasEnded()) sb.append(" - __It is your turn__");
        if (showSecondaries && !game.isHasEnded()) {
            List<String> secondaries = new ArrayList<>();
            for (int sc : game.getPlayedSCs()) {
                if (!player.hasFollowedSC(sc) && !player.getSCs().contains(sc)) {
                    secondaries.add(CardEmojis.getSCBackFromInteger(sc).toString());
                }
            }
            if (!secondaries.isEmpty()) {
                sb.append("\n> Please follow: ").append(String.join(" ", secondaries));
            }
        }
        if (showGameModes) sb.append(" | Game Modes: ").append(game.getGameModesText());
        if (game.isHasEnded()) {
            long creationDateTime = game.getCreationDateTime();
            long endedDateTime = game.getEndedDate();
            int daysPlayed =
                    (int) Duration.ofMillis(endedDateTime - creationDateTime).toDays();
            String dayLabel = (daysPlayed == 1) ? "DAY" : "DAYS";
            String endedStatus = game.hasWinner() ? "COMPLETED" : "ABANDONED";
            sb.append(" [")
                    .append(daysPlayed)
                    .append(' ')
                    .append(dayLabel)
                    .append(", ")
                    .append(endedStatus)
                    .append("]");
        }
        return sb.toString();
    }

    private static String playerAverageMapTurnLength(Player player) {
        long totalMillis = player.getTotalTurnTime();
        int numTurns = player.getNumberOfTurns();
        if (numTurns == 0 || totalMillis == 0) {
            return String.format("%02dh:%02dm:%02ds", 0, 0, 0);
        }

        long total = totalMillis / numTurns;

        total /= 1000; // total seconds (truncates)
        long seconds = total % 60;

        total /= 60; // total minutes (truncates)
        long minutes = total % 60;
        long hours = total / 60; // total hours (truncates)
        return String.format("%02dh:%02dm:%02ds", hours, minutes, seconds);
    }
}
