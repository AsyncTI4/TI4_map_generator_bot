package ti4.cron;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.ManagedGame;
import ti4.message.logging.BotLogger;
import ti4.website.AsyncTi4WebsiteHelper;

@UtilityClass
public class AbbreviatedDashboardCron {

    private static final long CHANGE_WINDOW_MILLIS = ChronoUnit.DAYS.getDuration().multipliedBy(7).toMillis();

    public static void register() {
        CronManager.schedulePeriodically(
                AbbreviatedDashboardCron.class,
                AbbreviatedDashboardCron::postAbbreviatedDashboards,
                5,
                60,
                TimeUnit.MINUTES);
    }

    private static void postAbbreviatedDashboards() {
        BotLogger.logCron("Running AbbreviatedDashboardCron.");
        try {
            long cutoff = Instant.now().toEpochMilli() - CHANGE_WINDOW_MILLIS;
            List<ManagedGame> recentGames = GameManager.getManagedGames().stream()
                    .filter(game -> game.getLastModifiedDate() >= cutoff)
                    .toList();

            if (recentGames.isEmpty()) {
                BotLogger.logCron("No games updated in the last 7 days. Skipping abbreviated dashboard upload.");
                return;
            }

            BotLogger.logCron(
                    String.format("Posting abbreviated dashboard payloads for %d game(s).", recentGames.size()));
            AsyncTi4WebsiteHelper.postAbbreviatedDashboardPayloads(recentGames);
        } catch (Exception e) {
            BotLogger.error("**AbbreviatedDashboardCron failed.**", e);
        } finally {
            BotLogger.logCron("Finished AbbreviatedDashboardCron.");
        }
    }
}
