package ti4.helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.manage.GameManager;
import ti4.map.manage.ManagedGame;
import ti4.message.MessageHelper;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.ColorEmojis;
import ti4.service.game.ManagedGameService;

@UtilityClass
public class SearchGameHelper {

    public static int searchGames(User user, GenericInteractionCreateEvent event, boolean onlyMyTurn, boolean includeEndedGames, boolean showAverageTurnTime, boolean showSecondaries, boolean showGameModes, boolean ignoreSpectate, boolean ignoreAborted, boolean wantNum) {
        String userID = user.getId();

        Predicate<ManagedGame> ignoreSpectateFilter = ignoreSpectate ? game -> game.getRealPlayers().stream().anyMatch(player -> player.getId().equals(user.getId())) : game -> game.getPlayers().stream().anyMatch(player -> player.getId().equals(user.getId()));
        Predicate<ManagedGame> onlyMyTurnFilter = onlyMyTurn ? game -> Objects.equals(game.getActivePlayerId(), user.getId()) : game -> true;
        Predicate<ManagedGame> endedGamesFilter = includeEndedGames ? game -> true : game -> !game.isHasEnded() && !game.isFowMode();
        Predicate<ManagedGame> onlyEndedFoWGames = game -> !game.isFowMode() || game.isHasEnded();
        Predicate<ManagedGame> ignoreAbortedFilter = ignoreAborted ? game -> !game.isHasEnded() || game.isHasWinner() : game -> true;
        Predicate<ManagedGame> allFilterPredicates = ignoreSpectateFilter.and(onlyMyTurnFilter).and(endedGamesFilter).and(onlyEndedFoWGames)
            .and(ignoreAbortedFilter);

        var filteredManagedGames = GameManager.getManagedGames().stream()
            .filter(allFilterPredicates)
            .sorted(Comparator.comparing(ManagedGameService::getGameNameForSorting))
            .toList();

        int index = 1;
        if (wantNum) {
            return filteredManagedGames.size();
        }
        int days = 0;

        StringBuilder sb = new StringBuilder("**__").append(user.getName()).append("'s Games__**\n");
        for (var managedGame : filteredManagedGames) {
            sb.append("`").append(Helper.leftpad("" + index, 2)).append(".`");
            var game = managedGame.getGame();
            sb.append(getPlayerMapListRepresentation(game, userID, showAverageTurnTime, showSecondaries, showGameModes));
            sb.append("\n");
            if (game.isHasEnded()) {
                days += Helper.getDateDifference(game.getCreationDate(), game.getEndedDateString());
            }
            index++;
        }
        if (event instanceof SlashCommandInteractionEvent slash) {
            MessageHelper.sendMessageToThread(slash.getChannel(), user.getName() + "'s Game List", sb.toString());
        }
        if (event instanceof ButtonInteractionEvent butt) {
            MessageHelper.sendMessageToThread(butt.getChannel(), user.getName() + "'s Game List", sb.toString());
        }
        return filteredManagedGames.size();

    }

    public static ArrayList<Integer> getGameDaysLength(User user, GenericInteractionCreateEvent event, boolean onlyMyTurn, boolean includeEndedGames, boolean showAverageTurnTime, boolean showSecondaries, boolean showGameModes, boolean ignoreSpectate, boolean ignoreAborted, boolean wantNum) {
        String userID = user.getId();

        Predicate<ManagedGame> ignoreSpectateFilter = ignoreSpectate ? game -> game.getRealPlayers().stream().anyMatch(player -> player.getId().equals(user.getId())) : game -> game.getPlayers().stream().anyMatch(player -> player.getId().equals(user.getId()));
        Predicate<ManagedGame> onlyMyTurnFilter = onlyMyTurn ? game -> Objects.equals(game.getActivePlayerId(), user.getId()) : game -> true;
        Predicate<ManagedGame> endedGamesFilter = includeEndedGames ? game -> true : game -> !game.isHasEnded() && !game.isFowMode();
        Predicate<ManagedGame> onlyEndedFoWGames = game -> !game.isFowMode() || game.isHasEnded();
        Predicate<ManagedGame> ignoreAbortedFilter = ignoreAborted ? game -> !game.isHasEnded() || game.isHasWinner() : game -> true;
        Predicate<ManagedGame> allFilterPredicates = ignoreSpectateFilter.and(onlyMyTurnFilter).and(endedGamesFilter).and(onlyEndedFoWGames)
            .and(ignoreAbortedFilter);

        var filteredManagedGames = GameManager.getManagedGames().stream()
            .filter(allFilterPredicates)
            .sorted(Comparator.comparing(ManagedGameService::getGameNameForSorting))
            .toList();

        int index = 1;

        ArrayList<Integer> days = new ArrayList<>();

        StringBuilder sb = new StringBuilder("**__").append(user.getName()).append("'s Games__**\n");
        for (var managedGame : filteredManagedGames) {
            sb.append("`").append(Helper.leftpad("" + index, 2)).append(".`");
            var game = managedGame.getGame();
            sb.append(getPlayerMapListRepresentation(game, userID, showAverageTurnTime, showSecondaries, showGameModes));
            sb.append("\n");
            if (game.isHasEnded()) {
                if (Helper.getDateDifference(game.getCreationDate(), game.getEndedDateString()) > 0) {
                    days.add(Helper.getDateDifference(game.getCreationDate(), game.getEndedDateString()));
                }
            }
            index++;
        }
        Collections.sort(days);

        return days;

    }

    public static double getWinPercentage(User user, GenericInteractionCreateEvent event, boolean onlyMyTurn, boolean includeEndedGames, boolean showAverageTurnTime, boolean showSecondaries, boolean showGameModes, boolean ignoreSpectate, boolean ignoreAborted, boolean wantNum) {
        String userID = user.getId();

        Predicate<ManagedGame> ignoreSpectateFilter = ignoreSpectate ? game -> game.getRealPlayers().stream().anyMatch(player -> player.getId().equals(user.getId())) : game -> game.getPlayers().stream().anyMatch(player -> player.getId().equals(user.getId()));
        Predicate<ManagedGame> onlyMyTurnFilter = onlyMyTurn ? game -> Objects.equals(game.getActivePlayerId(), user.getId()) : game -> true;
        Predicate<ManagedGame> endedGamesFilter = includeEndedGames ? game -> true : game -> !game.isHasEnded() && !game.isFowMode();
        Predicate<ManagedGame> onlyEndedFoWGames = game -> !game.isFowMode() || game.isHasEnded();
        Predicate<ManagedGame> ignoreAbortedFilter = ignoreAborted ? game -> !game.isHasEnded() || game.isHasWinner() : game -> true;
        Predicate<ManagedGame> allFilterPredicates = ignoreSpectateFilter.and(onlyMyTurnFilter).and(endedGamesFilter).and(onlyEndedFoWGames)
            .and(ignoreAbortedFilter);

        var filteredManagedGames = GameManager.getManagedGames().stream()
            .filter(allFilterPredicates)
            .sorted(Comparator.comparing(ManagedGameService::getGameNameForSorting))
            .toList();

        int index = 0;

        double wins = 0;

        StringBuilder sb = new StringBuilder("**__").append(user.getName()).append("'s Games__**\n");
        for (var managedGame : filteredManagedGames) {
            var game = managedGame.getGame();
            if (game.isHasEnded() && game.hasWinner()) {
                if (game.getWinners().contains(game.getPlayer(userID))) {
                    wins++;
                }
            }
            index++;
        }

        return wins / index;

    }

    public static String getTotalCompletedNOngoingGames(List<User> users, GenericInteractionCreateEvent event) {
        StringBuilder sb = new StringBuilder();
        AtomicInteger index = new AtomicInteger(1);
        sb.append("## __**Games**__\n");
        for (User user : users) {
            int ongoingAmount = SearchGameHelper.searchGames(user, event, false, false, false, true, false, true, true, true);
            int completedAndOngoingAmount = SearchGameHelper.searchGames(user, event, false, true, false, true, false, true, true, true);
            int completedGames = completedAndOngoingAmount - ongoingAmount;
            sb.append("`").append(Helper.leftpad(String.valueOf(index.get()), 3)).append(". ");
            sb.append(completedGames);
            sb.append("` Completed. `").append(ongoingAmount).append("` Ongoing -- ");
            sb.append(user.getEffectiveName());
            sb.append("\n");
            if (completedGames > 0) {
                sb.append("> The completed games took the following amount of time to complete (in days):");
                List<Integer> days = SearchGameHelper.getGameDaysLength(user, event, false, true, false, true, false, true, true, true);
                for (int day : days) {
                    sb.append(" ").append(day);
                }
                sb.append("\n");
                double getWinPercentage = SearchGameHelper.getWinPercentage(user, event, false, true, false, true, false, true, true, true);
                sb.append("> Player win percentage accross all games was: " + String.format("%.2f", getWinPercentage) + "\n");
            }
            index.getAndIncrement();
        }
        return sb.toString();
    }

    public static String getPlayerMapListRepresentation(Game game, String userID, boolean showAverageTurnTime, boolean showSecondaries, boolean showGameModes) {
        Player player = game.getPlayer(userID);
        if (player == null) return "";
        String gameChannelLink = game.getActionsChannel() == null ? "" : game.getActionsChannel().getAsMention();
        StringBuilder sb = new StringBuilder();
        if (Mapper.isValidFaction(player.getFaction())) sb.append(player.getFactionEmoji());
        if (player.getColor() != null && !"null".equals(player.getColor())) sb.append(ColorEmojis.getColorEmoji(player.getColor()));
        sb.append("**").append(game.getName()).append("**");
        sb.append(gameChannelLink);
        if (showAverageTurnTime) sb.append("  [Average Turn Time: `").append(playerAverageMapTurnLength(player)).append("`]");
        if (game.hasWinner()) {
            for (Player winner : game.getWinners()) {
                if (winner.getUserID().equals(player.getUserID())) sb.append(" **👑WINNER👑**");
            }
        }
        if (game.getActivePlayerID() != null && game.getActivePlayerID().equals(userID) && !game.isHasEnded()) sb.append(" **[__IT IS YOUR TURN__]**");
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
        if (game.isHasEnded()) sb.append(" [GAME IS OVER]");
        return sb.toString();
    }

    private static String playerAverageMapTurnLength(Player player) {
        long totalMillis = player.getTotalTurnTime();
        int numTurns = player.getNumberOfTurns();
        if (numTurns == 0 || totalMillis == 0) {
            return String.format("%02dh:%02dm:%02ds", 0, 0, 0);
        }

        long total = totalMillis / numTurns;

        total /= 1000; //total seconds (truncates)
        long seconds = total % 60;

        total /= 60; //total minutes (truncates)
        long minutes = total % 60;
        long hours = total / 60; //total hours (truncates)
        return String.format("%02dh:%02dm:%02ds", hours, minutes, seconds);
    }
}
