package ti4.service.stats;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import ti4.map.Game;
import ti4.map.GameManager;

// TODO
public class StatsComputationPipeline {
    // ListSlashCommandsUsed
    // SearchGames
    // GameStatisticsFilter*****
    // GameStats
    // StellarConverter
    // ListTitlesGiven

    // WARNING, THIS INVOLVES READING EVERY GAME. IT IS AN EXPENSIVE OPERATION.
    public static PagedGames getGamesPage(int page) {
        var gameNames = GameManager.getGameNames();
        var pagedGames = new PagedGames();
        for (int i = PagedGames.PAGE_SIZE * page; i < gameNames.size() && pagedGames.getGames().size() < PagedGames.PAGE_SIZE; i++) {
            pagedGames.games.add(GameManager.getGame(gameNames.get(i)));
        }
        pagedGames.hasNextPage = gameNames.size() / PagedGames.PAGE_SIZE > page;
        return pagedGames;
    }

    public static class PagedGames {

        public static final int PAGE_SIZE = 200;

        @Getter
        private final List<Game> games = new ArrayList<>();
        private boolean hasNextPage;

        public boolean hasNextPage() {
            return hasNextPage;
        }
    }
}
