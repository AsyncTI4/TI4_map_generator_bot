package ti4.commands.search;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameProperties;
import ti4.message.MessageHelper;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

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

    public StringBuilder getPlayerTitles(String userID, String userName, boolean gamesIncluded) {
        HashMap<String, String> gameHist = new HashMap<>();
        Map<String, Integer> titles = new HashMap<>();
        int currentPage = 0;
        GameManager.PagedGames pagedGames;
        do {
            pagedGames = GameManager.getGamesPage(currentPage++);
            Predicate<Game> ignoreSpectateFilter = game -> game.getRealPlayerIDs().contains(userID);
            List<Game> games = pagedGames.getGames().stream()
                    .filter(ignoreSpectateFilter.and(GameProperties::isHasEnded))
                    .sorted(Comparator.comparing(Game::getGameNameForSorting))
                    .toList();

            for (Game playerGame : games) {
                String singularGameTiles = playerGame.getStoredValue("TitlesFor" + userID);
                if (!singularGameTiles.isEmpty()) {
                    for (String title : singularGameTiles.split("_")) {
                        if (titles.containsKey(title)) {
                            int amount = titles.get(title) + 1;
                            titles.put(title, amount);
                            gameHist.put(title, gameHist.get(title) + ", " + playerGame.getName());
                        } else {
                            titles.put(title, 1);
                            gameHist.put(title, playerGame.getName());
                        }

                    }
                }
            }
        } while (pagedGames.hasNextPage());

        int index = 1;
        StringBuilder sb = new StringBuilder("**__").append(userName).append("'s Titles__**\n");
        for (String title : titles.keySet()) {
            sb.append("`").append(Helper.leftpad("" + index, 2)).append(".`");
            if (gamesIncluded) {
                sb.append("**").append(title).append("** x").append(titles.get(title)).append(" (").append(gameHist.get(title)).append(")");
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
