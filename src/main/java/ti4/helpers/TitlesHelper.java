package ti4.helpers;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.manage.GameManager;
import ti4.map.manage.ManagedGame;
import ti4.service.game.ManagedGameService;

@UtilityClass
public class TitlesHelper {

    public StringBuilder getPlayerTitles(String userId, String userName, boolean gamesIncluded) {
        HashMap<String, String> gameHistory = new HashMap<>();
        Map<String, Integer> titles = new HashMap<>();

        Predicate<ManagedGame> thisPlayerIsInGame = game -> game.getPlayer(userId) != null;
        List<ManagedGame> games = GameManager.getManagedGames().stream()
                .filter(thisPlayerIsInGame.and(ManagedGame::isHasEnded))
                .sorted(Comparator.comparing(ManagedGameService::getGameNameForSorting))
                .toList();

        for (var managedGame : games) {
            var game = managedGame.getGame();
            String titlesForPlayer = game.getStoredValue("TitlesFor" + userId);
            if (titlesForPlayer.isEmpty()) {
                continue;
            }
            Arrays.stream(titlesForPlayer.split("_")).forEach(title -> {
                if (!title.isEmpty() && !title.equalsIgnoreCase("**")) {
                    titles.merge(title, 1, Integer::sum);
                    gameHistory.merge(title, game.getName(), (existing, newName) -> existing + ", " + newName);
                }
            });
        }

        int index = 1;
        StringBuilder sb = new StringBuilder("**__").append(userName).append("'s Titles__**\n");
        for (String title : titles.keySet()) {
            sb.append("`").append(Helper.leftpad("" + index, 2)).append(".`");
            if (gamesIncluded) {
                sb.append("**")
                        .append(title)
                        .append("** x")
                        .append(titles.get(title))
                        .append(" (")
                        .append(gameHistory.get(title))
                        .append(")");
            } else {
                sb.append("**").append(title).append("** x").append(titles.get(title));
            }
            sb.append("\n");
            index++;
        }
        if (titles.isEmpty()) {
            sb = new StringBuilder("No titles yet");
        }

        return sb;
    }

    @ButtonHandler("offerToGiveTitles")
    public static void offerToGiveTitles(ButtonInteractionEvent event, Game game) {
        PlayerTitleHelper.offerEveryoneTitlePossibilities(game);
        ButtonHelper.deleteMessage(event);
    }
}
