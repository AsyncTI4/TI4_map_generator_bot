package ti4.map;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;

public class GamesPage {

    public static final int PAGE_SIZE = 200;

    private GamesPage() {}

    @Getter
    private final List<Game> games = new ArrayList<>();
    private boolean hasNextPage;

    public boolean hasNextPage() {
        return hasNextPage;
    }

    // WARNING, THIS INVOLVES READING EVERY GAME. IT IS AN EXPENSIVE OPERATION.
    public static GamesPage getPage(int page) {
        var gameNames = GameManager.getGameNames();
        var pagedGames = new GamesPage();
        for (int i = PAGE_SIZE * page; i < gameNames.size() && pagedGames.getGames().size() < PAGE_SIZE; i++) {
            pagedGames.games.add(GameManager.getGame(gameNames.get(i)));
        }
        pagedGames.hasNextPage = gameNames.size() / PAGE_SIZE > page;
        return pagedGames;
    }
}
