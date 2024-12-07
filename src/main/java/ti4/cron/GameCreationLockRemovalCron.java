package ti4.cron;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

import lombok.experimental.UtilityClass;
import ti4.map.PersistenceManager;
import ti4.message.BotLogger;
import ti4.model.metadata.GameCreationLocks;

@UtilityClass
public class GameCreationLockRemovalCron {

    public static void register() {
        CronManager.register(GameCreationLockRemovalCron.class, GameCreationLockRemovalCron::removeGameCreationLocks, 1, 10, TimeUnit.MINUTES);
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
            BotLogger.log("**Failed to remove game creation locks.**", e);
        }
    }
}
