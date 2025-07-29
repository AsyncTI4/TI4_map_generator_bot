package ti4.executors;

import java.util.function.Consumer;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import ti4.map.Game;
import ti4.map.persistence.GameManager;

@UtilityClass
public class GameLockManager {

    public static void runWithTryLockSaveAndRelease(String gameName, ExecutionLockManager.LockType lockType, String taskName, Consumer<Game> consumer, MessageChannel messageChannel) {
        Runnable getRunAndSave = wrapWithGetRunAndSave(gameName, lockType, taskName, consumer);
        ExecutionLockManager
            .wrapWithTryLockAndRelease(gameName, lockType, getRunAndSave, messageChannel)
            .run();
    }

    private static Runnable wrapWithGetRunAndSave(String gameName, ExecutionLockManager.LockType lockType, String taskName, Consumer<Game> consumer) {
        return () -> {
            Game game = GameManager.getManagedGame(gameName).getGame();
            consumer.accept(game);
            if (lockType == ExecutionLockManager.LockType.WRITE) {
                GameManager.save(game, taskName);
            }
        };
    }

    public static void runWithTryLockSaveAndRelease(String gameName, ExecutionLockManager.LockType lockType, String taskName, Consumer<Game> consumer) {
        runWithTryLockSaveAndRelease(gameName, lockType, taskName, consumer, null);
    }

    public static void runWithLockSaveAndRelease(String gameName, ExecutionLockManager.LockType lockType, String taskName, Consumer<Game> consumer) {
        Runnable getRunAndSave = wrapWithGetRunAndSave(gameName, lockType, taskName, consumer);
        ExecutionLockManager
            .wrapWithLockAndRelease(gameName, lockType, getRunAndSave)
            .run();
    }
}
