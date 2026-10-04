package ti4.spring.websocket;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.executors.ExecutionHistoryManager;
import ti4.executors.ExecutorUtility;
import ti4.executors.ShutdownResult;
import ti4.game.Game;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedGame;
import ti4.helpers.TimedRunnable;
import ti4.logging.BotLogger;
import ti4.spring.context.SpringContext;

@UtilityClass
public class GameWebStatePipeline {

    private static final int SHUTDOWN_TIMEOUT_SECONDS = 10;
    private static final int EXECUTION_TIME_WARNING_THRESHOLD_SECONDS = 5;
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name("ti4-game-web-state-", 0).factory());
    private static final Set<String> pendingGameNames = ConcurrentHashMap.newKeySet();

    public static void queue(@Nullable Game game) {
        if (game == null || game.isFowMode() || System.getenv("TESTING") != null) return;
        WebSocketNotifier notifier = getNotifierIfAvailable();
        if (notifier == null) return;

        // TODO: Skip the rebuild when nobody subscribes to /topic/game/{name}/state; invalidate the
        // GameWebDataService cache instead so REST recomputes on demand. Ask the simple broker's
        // subscription registry: SimpUserRegistry misses anonymous website viewers.
        String gameName = game.getName();
        if (!pendingGameNames.add(gameName)) return;

        var timedRunnable = new TimedRunnable(
                "GameWebStatePipeline task for `" + gameName + "`",
                EXECUTION_TIME_WARNING_THRESHOLD_SECONDS,
                () -> publishLatestSavedState(notifier, gameName));
        try {
            ExecutionHistoryManager.runWithExecutionHistory(EXECUTOR_SERVICE, timedRunnable);
        } catch (RejectedExecutionException e) {
            pendingGameNames.remove(gameName);
        }
    }

    public static ShutdownResult shutdown() {
        return ExecutorUtility.shutdownAndAwaitTermination(
                EXECUTOR_SERVICE, SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Nullable
    private static WebSocketNotifier getNotifierIfAvailable() {
        try {
            return SpringContext.getBean(WebSocketNotifier.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static void publishLatestSavedState(WebSocketNotifier notifier, String gameName) {
        pendingGameNames.remove(gameName);
        try {
            ManagedGame managedGame = GameManager.getManagedGame(gameName);
            if (managedGame == null) return;
            notifier.notifyGameStateChanged(managedGame.getGame());
        } catch (Exception e) {
            BotLogger.error("Failed to publish web state for game " + gameName + ".", e);
        }
    }
}
