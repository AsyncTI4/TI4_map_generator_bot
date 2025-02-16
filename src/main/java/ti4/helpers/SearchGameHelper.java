package ti4.helpers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
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
        StringBuilder sb = new StringBuilder("**__").append(user.getName()).append("'s Games__**\n");
        for (var managedGame : filteredManagedGames) {
            sb.append("`").append(Helper.leftpad("" + index, 2)).append(".`");
            var game = managedGame.getGame();
            sb.append(getPlayerMapListRepresentation(game, userID, showAverageTurnTime, showSecondaries, showGameModes));
            sb.append("\n");
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
        int numTurns = player.getNumberTurns();
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
