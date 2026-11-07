package ti4.game.persistence;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.experimental.UtilityClass;
import ti4.executors.ExecutionHistoryManager;
import ti4.executors.ExecutorUtility;
import ti4.executors.ShutdownResult;
import ti4.game.Game;
import ti4.helpers.TimedRunnable;
import ti4.logging.BotLogger;
import ti4.spring.service.persistence.GameEntityMapper;
import ti4.spring.service.persistence.GameEntityPersistenceService;
import ti4.spring.service.persistence.GameEntitySnapshot;

@UtilityClass
public class GameDatabaseSyncPipeline {

    private static final int SHUTDOWN_TIMEOUT_SECONDS = 30;
    private static final int SYNC_EXECUTION_TIME_WARNING_THRESHOLD_SECONDS = 5;
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name("ti4-game-database-sync-", 0).factory());

    static void queueSync(Game game, ManagedGameState managedGameState) {
        if (DatabasePersistenceGate.isDisabled()) return;
        try {
            GameEntitySnapshot snapshot = GameEntityMapper.toSnapshot(game, managedGameState);
            queue(
                    game.getName(),
                    service -> service.replaceIfMatchesGameFile(
                            snapshot, () -> GameRevisionKeyFile.current(game.getName())));
        } catch (Exception e) {
            BotLogger.error("Failed to prepare database sync for game " + game.getName() + ".", e);
        }
    }

    static void queueSyncFromGameFile(String gameName) {
        if (DatabasePersistenceGate.isDisabled()) return;
        queue(gameName, service -> {
            Game game = GameLoadService.load(gameName);
            if (game == null) return;
            service.restoreIfMatchesGameFile(
                    GameEntityMapper.toSnapshot(game), () -> GameRevisionKeyFile.current(gameName));
        });
    }

    static void queueDelete(String gameName) {
        if (DatabasePersistenceGate.isDisabled()) return;
        queue(
                gameName,
                service -> service.deleteIfGameFileMissing(gameName, () -> GameRevisionKeyFile.current(gameName)));
    }

    public static ShutdownResult shutdown() {
        return ExecutorUtility.shutdownAndAwaitTermination(
                EXECUTOR_SERVICE, SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    static boolean queueReconciliationTask(GameDatabaseReconciliationChain.ReconciliationTask task) {
        return queueTask(task.name(), task.warningThresholdSeconds(), task);
    }

    private static boolean queueTask(String taskName, int executionTimeWarningThresholdSeconds, Runnable runnable) {
        var timedRunnable = new TimedRunnable(taskName, executionTimeWarningThresholdSeconds, runnable);
        try {
            ExecutionHistoryManager.runWithExecutionHistory(EXECUTOR_SERVICE, timedRunnable);
            return true;
        } catch (RejectedExecutionException e) {
            BotLogger.error("`" + taskName + "` was rejected because the bot is shutting down.");
            return false;
        }
    }

    private static void queue(String gameName, Consumer<GameEntityPersistenceService> databaseWrite) {
        Runnable runnable = () -> {
            if (DatabasePersistenceGate.isDisabled()) return;
            try {
                databaseWrite.accept(GameEntityPersistenceService.getBean());
            } catch (Exception e) {
                BotLogger.error("Failed to sync game " + gameName + " to the database.", e);
            }
        };
        queueTask(
                "GameDatabaseSyncPipeline task for `" + gameName + "`",
                SYNC_EXECUTION_TIME_WARNING_THRESHOLD_SECONDS,
                runnable);
    }
}
