package ti4.game.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import ti4.executors.ExecutionLockManager;
import ti4.executors.ExecutionLockType;
import ti4.game.Game;

public final class GamesPage {

    private static final int PAGE_SIZE = 100;

    private final List<String> gameLocks = new ArrayList<>();
    private final List<Game> games = new ArrayList<>();
    private boolean hasNextPage;

    private GamesPage() {}

    // WARNING, THIS INVOLVES READING EVERY GAME. IT IS AN EXPENSIVE OPERATION.
    // IT ALSO INVOLVES HOLDING A LOCK OVER THE ENTIRE PAGE FOR THE DURATION OF HANDLING THAT PAGE
    public static void consumeAllGames(Consumer<Game> consumer, ExecutionLockType lockType) {
        consumeAllGames(_ -> true, consumer, lockType);
    }

    // WARNING, THIS INVOLVES READING EVERY GAME. IT IS AN EXPENSIVE OPERATION.
    // IT ALSO INVOLVES HOLDING A LOCK OVER THE ENTIRE PAGE FOR THE DURATION OF HANDLING THAT PAGE
    public static void consumeAllGames(Predicate<Game> filter, Consumer<Game> consumer, ExecutionLockType lockType) {
        int currentPage = 0;
        GamesPage pagedGames = null;
        do {
            try {
                pagedGames = getPage(currentPage, lockType);
                currentPage++;
                pagedGames.games.stream().filter(filter).forEach(consumer);
            } finally {
                if (pagedGames != null) {
                    pagedGames.gameLocks.forEach(gameName -> ExecutionLockManager.unlock(gameName, lockType));
                }
            }
        } while (pagedGames.hasNextPage);
    }

    // WARNING, THIS INVOLVES READING MANY GAMES. IT IS AN EXPENSIVE OPERATION.
    private static GamesPage getPage(int page, ExecutionLockType lockType) {
        var gameNames = GameManager.getGameNames();
        var pagedGames = new GamesPage();
        for (int i = PAGE_SIZE * page; i < gameNames.size() && pagedGames.games.size() < PAGE_SIZE; i++) {
            String gameName = gameNames.get(i);
            ExecutionLockManager.lock(gameName, lockType);
            pagedGames.gameLocks.add(gameName);
            var game = GameManager.getManagedGame(gameName).getGame();
            pagedGames.games.add(game);
        }
        pagedGames.hasNextPage = gameNames.size() / PAGE_SIZE > page;
        return pagedGames;
    }
}
