package ti4.game.persistence;

import java.util.function.Consumer;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;
import ti4.executors.ExecutionLockManager;
import ti4.executors.ExecutionLockType;
import ti4.game.Game;

@UtilityClass
public class ConsumeGameUtility {

    // WARNING, THIS INVOLVES READING EVERY GAME. IT IS AN EXPENSIVE OPERATION.
    public static void consumeAllGames(Consumer<Game> consumer, ExecutionLockType lockType) {
        consumeAllGames(null, consumer, lockType);
    }

    // WARNING, THIS INVOLVES READING EVERY GAME. IT IS AN EXPENSIVE OPERATION.
    public static void consumeAllGames(Predicate<Game> filter, Consumer<Game> consumer, ExecutionLockType lockType) {
        consumeGames(GameManager.getGameNames(), filter, consumer, lockType);
    }

    // WARNING, THIS INVOLVES READING EVERY GAME. IT IS AN EXPENSIVE OPERATION.
    public static void consumeGames(Iterable<String> gameNames, Consumer<Game> consumer, ExecutionLockType lockType) {
        consumeGames(gameNames, null, consumer, lockType);
    }

    // WARNING, THIS INVOLVES READING EVERY GAME. IT IS AN EXPENSIVE OPERATION.
    public static void consumeGames(
            Iterable<String> gameNames, Predicate<Game> filter, Consumer<Game> consumer, ExecutionLockType lockType) {
        for (String gameName : gameNames) {
            var managed = GameManager.getManagedGame(gameName);
            if (managed == null) continue;
            ExecutionLockManager.wrapWithLockAndRelease(gameName, lockType, () -> {
                Game game = managed.getGame();

                if (filter == null || filter.test(game)) {
                    consumer.accept(game);
                }
            });
        }
    }
}
