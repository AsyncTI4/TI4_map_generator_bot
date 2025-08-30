package ti4.cron;

import java.time.ZoneId;
import lombok.experimental.UtilityClass;
import ti4.json.PersistenceManager;
import ti4.message.logging.BotLogger;
import ti4.service.statistics.MatchmakingRatingService;
import ti4.service.statistics.MatchmakingRatingService.MatchmakingRatingData;

@UtilityClass
public class MatchmakingRatingCron {

    public static void register() {
        CronManager.schedulePeriodicallyAtTime(
                MatchmakingRatingCron.class,
                MatchmakingRatingCron::calculateAndPersist,
                6,
                0,
                ZoneId.of("America/New_York"));
    }

    private static void calculateAndPersist() {
        BotLogger.logCron("Running MatchmakingRatingCron.");
        try {
            MatchmakingRatingData data = MatchmakingRatingService.calculateRatingsData();
            PersistenceManager.writeObjectToJsonFile(MatchmakingRatingService.MATCHMAKING_RATING_FILE, data);
        } catch (Exception e) {
            BotLogger.error("**MatchmakingRatingCron failed.**", e);
        }
        BotLogger.logCron("Finished MatchmakingRatingCron.");
    }
}
