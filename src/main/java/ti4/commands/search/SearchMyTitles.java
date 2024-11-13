package ti4.commands.search;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.message.MessageHelper;

public class SearchMyTitles extends SearchSubcommandData {

    public SearchMyTitles() {
        super(Constants.SEARCH_MY_TITLES, "List all the titles you've acquired");
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player to Show"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        User user = event.getOption(Constants.PLAYER, event.getUser(), OptionMapping::getAsUser);
        StringBuilder sb = getPlayerTitles(user.getId(), user.getName(), true);
        MessageHelper.sendMessageToThread(event.getChannel(), user.getName() + "'s Titles List", sb.toString());
    }

    public StringBuilder getPlayerTitles(String userId, String userName, boolean gamesIncluded) {
        HashMap<String, String> gameHistory = new HashMap<>();
        Map<String, Integer> titles = new HashMap<>();

        Predicate<Game> thisPlayerIsInGame = game -> game.hasPlayer(userId);
        List<Game> games = GameManager.getGameNameToGame().values().stream()
                .filter(thisPlayerIsInGame.and(Game::isHasEnded))
                .sorted(Comparator.comparing(Game::getGameNameForSorting))
                .toList();

        for (var managedGame : games) {
            var game = GameManager.getGame(managedGame.getName());
            String titlesForPlayer = game.getStoredValue("TitlesFor" + userId);
            if (titlesForPlayer.isEmpty()) {
                continue;
            }
            Arrays.stream(titlesForPlayer.split("_"))
                    .forEach(title -> {
                        titles.merge(title, 1, Integer::sum);
                        gameHistory.merge(title, game.getName(), (existing, newName) -> existing + ", " + newName);
                    });
        }

        int index = 1;
        StringBuilder sb = new StringBuilder("**__").append(userName).append("'s Titles__**\n");
        for (String title : titles.keySet()) {
            sb.append("`").append(Helper.leftpad("" + index, 2)).append(".`");
            if (gamesIncluded) {
                sb.append("**").append(title).append("** x").append(titles.get(title)).append(" (").append(gameHistory.get(title)).append(")");
            } else {
                sb.append("**").append(title).append("** x").append(titles.get(title));
            }
            sb.append("\n");
            index++;
        }
        if (titles.keySet().isEmpty()) {
            sb = new StringBuilder("No titles yet");
        }

        return sb;
    }

}
