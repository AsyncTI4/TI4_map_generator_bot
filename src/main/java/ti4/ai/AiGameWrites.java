package ti4.ai;

import java.util.function.Consumer;
import lombok.experimental.UtilityClass;
import ti4.ai.trace.Thinking;
import ti4.executors.ExecutionLockManager;
import ti4.executors.ExecutionLockType;
import ti4.game.Game;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedGame;

@UtilityClass
public class AiGameWrites {

    public static void persist(Game game, String reason, Consumer<Game> change) {
        try (Thinking.Span timing = Thinking.measure("ai.persist")) {
            persistTimed(game, reason, change);
        }
    }

    private static void persistTimed(Game game, String reason, Consumer<Game> change) {
        change.accept(game);
        String name = game.getName();
        if (!GameManager.isValid(name)) return;
        ExecutionLockManager.lock(name, ExecutionLockType.WRITE);
        try {
            ManagedGame managed = GameManager.getManagedGame(name);
            Game latest = managed == null ? game : managed.getGame();
            if (latest != game) change.accept(latest);
            GameManager.save(latest, reason);
        } finally {
            ExecutionLockManager.unlock(name, ExecutionLockType.WRITE);
        }
    }
}
