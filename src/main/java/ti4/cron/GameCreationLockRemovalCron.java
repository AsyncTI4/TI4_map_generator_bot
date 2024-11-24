package ti4.cron;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import lombok.experimental.UtilityClass;
import ti4.map.PersistenceManager;
import ti4.message.BotLogger;
import ti4.model.metadata.GameCreationLocks;

@UtilityClass
public class GameCreationLockRemovalCron {

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor();

    public static void start() {
        SCHEDULER.scheduleAtFixedRate(GameCreationLockRemovalCron::removeGameCreationLocks, 1, 10, TimeUnit.MINUTES);
    }

    private static void removeGameCreationLocks() {
        try {
            GameCreationLocks gameCreationLocks = PersistenceManager.readObjectFromJsonFile(GameCreationLocks.JSON_DATA_FILE_NAME, GameCreationLocks.class);
            if (gameCreationLocks == null) {
                gameCreationLocks = new GameCreationLocks();
            }
            var tenMinutesAgo = Instant.now().minus(10, ChronoUnit.MINUTES);
            boolean locksRemoved = gameCreationLocks.getUsernameToLastGameCreation().entrySet()
                .removeIf(entry -> entry.getValue().isBefore(tenMinutesAgo));
            if (locksRemoved) {
                PersistenceManager.writeObjectToJsonFile(GameCreationLocks.JSON_DATA_FILE_NAME, gameCreationLocks);
            }
        } catch (Exception e) {
            BotLogger.log("Failed to remove game creation locks.", e);
        }
    }

    public static void shutdown() {
        SCHEDULER.shutdown();
        try {
            if (!SCHEDULER.awaitTermination(10, TimeUnit.SECONDS)) {
                SCHEDULER.shutdownNow();
            }
        } catch (InterruptedException e) {
            SCHEDULER.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
