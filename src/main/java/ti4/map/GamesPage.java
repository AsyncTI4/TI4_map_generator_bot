package ti4.map;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import lombok.Getter;

public class GamesPage {

    public static final int PAGE_SIZE = 500;

    private GamesPage() {}

    @Getter
    private final List<Game> games = new ArrayList<>();
    private boolean hasNextPage;

    public boolean hasNextPage() {
        return hasNextPage;
    }

    // WARNING, THIS INVOLVES READING EVERY GAME. IT IS AN EXPENSIVE OPERATION.
    public static void consumeAllGames(Consumer<Game> consumer) {
        consumeAllGames(game -> true, consumer);
    }

    // WARNING, THIS INVOLVES READING EVERY GAME. IT IS AN EXPENSIVE OPERATION.
    public static void consumeAllGames(Predicate<Game> filter, Consumer<Game> consumer) {
        int currentPage = 0;
        GamesPage pagedGames;
        do {
            pagedGames = GamesPage.getPage(currentPage++);
            pagedGames.getGames().stream()
                .filter(filter)
                .forEach(consumer);
        } while (pagedGames.hasNextPage());
    }

    // WARNING, THIS INVOLVES READING MANY GAMES. IT IS AN EXPENSIVE OPERATION.
    private static GamesPage getPage(int page) {
        var gameNames = GameManager.getGameNames();
        var pagedGames = new GamesPage();
        for (int i = PAGE_SIZE * page; i < gameNames.size() && pagedGames.getGames().size() < PAGE_SIZE; i++) {
            pagedGames.games.add(GameManager.getGame(gameNames.get(i)));
        }
        pagedGames.hasNextPage = gameNames.size() / PAGE_SIZE > page;
        return pagedGames;
    }
}
