package ti4.cron;

import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedGame;
import ti4.logging.BotLogger;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageManager.CleanupStats;
import ti4.spring.service.deploy.ActiveLeaseService;

@UtilityClass
public class GameMessageCleanupCron {

    private static final long TWO_WEEKS_MS = TimeUnit.DAYS.toMillis(14);

    public static void register() {
        CronManager.schedulePeriodicallyAtTime(
                GameMessageCleanupCron.class, GameMessageCleanupCron::cleanup, 4, 0, ZoneId.of("America/New_York"));
    }

    private static void cleanup() {
        if (!ActiveLeaseService.shouldCurrentProcessRunScheduledWork()) return;
        BotLogger.logCron("Running GameMessageCleanupCron.");
        try {
            cleanupGameMessages();
        } catch (Exception e) {
            BotLogger.error("**GameMessageCleanupCron failed.**", e);
        }
        BotLogger.logCron("Finished GameMessageCleanupCron.");
    }

    private static void cleanupGameMessages() {
        long twoWeeksAgo = System.currentTimeMillis() - TWO_WEEKS_MS;

        Map<String, Integer> realPlayerCountByActiveGame = GameManager.getManagedGames().stream()
                .filter(g -> !g.isHasEnded())
                .collect(Collectors.toMap(
                        ManagedGame::getName, g -> g.getRealPlayers().size()));

        CleanupStats stats = GameMessageManager.cleanupStaleEntries(realPlayerCountByActiveGame, twoWeeksAgo);

        BotLogger.info(String.format(
                "GameMessageCleanupCron: Removed `%d` game entries and `%d` individual messages.",
                stats.removedGameEntries(), stats.removedMessages()));
    }
}
