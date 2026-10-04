package ti4.helpers;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.service.game.ManagedGameService;
import ti4.spring.service.title.PlayerTitleService;

@UtilityClass
public class TitlesHelper {

    public StringBuilder getPlayerTitles(String userId, String userName, boolean gamesIncluded) {
        HashMap<String, String> gameHistory = new HashMap<>();
        Map<String, Integer> titles = new HashMap<>();

        PlayerTitleService playerTitleService = PlayerTitleService.getBean();
        playerTitleService.getEndedGameTitles(userId).stream()
                .filter(earned -> !earned.title().isBlank() && !"**".equals(earned.title()))
                .sorted(Comparator.comparing(earned -> ManagedGameService.getGameNameForSorting(earned.source())))
                .forEach(earned -> addTitle(titles, gameHistory, earned.title(), earned.source()));
        playerTitleService
                .getStandaloneTitles(userId)
                .forEach(earned -> addTitle(titles, gameHistory, earned.title(), earned.source()));

        int index = 1;
        StringBuilder sb = new StringBuilder("**__").append(userName).append("'s Titles__**\n");

        Map<String, Integer> titles2 = titles.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
        for (String title : titles2.keySet()) {
            sb.append('`').append(Helper.leftpad("" + index, 2)).append(".`");
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
            sb.append('\n');
            index++;
        }
        if (titles.isEmpty()) {
            sb = new StringBuilder("No titles yet");
        }

        return sb;
    }

    private static void addTitle(
            Map<String, Integer> titles, HashMap<String, String> gameHistory, String title, String source) {
        titles.merge(title, 1, Integer::sum);
        gameHistory.merge(title, source, (existing, newName) -> existing + ", " + newName);
    }

    @ButtonHandler("offerToGiveTitles")
    public static void offerToGiveTitles(ButtonInteractionEvent event, Game game) {
        PlayerTitleHelper.offerEveryoneTitlePossibilities(game);
        ButtonHelper.deleteMessage(event);
    }
}
