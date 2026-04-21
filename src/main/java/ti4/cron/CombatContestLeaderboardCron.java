package ti4.cron;

import java.time.ZoneId;
import lombok.experimental.UtilityClass;
import ti4.logging.BotLogger;
import ti4.spring.context.SpringContext;
import ti4.spring.service.contest.CombatContestService;
import ti4.spring.service.deploy.ActiveLeaseService;

@UtilityClass
public class CombatContestLeaderboardCron {

    public static void register() {
        CronManager.schedulePeriodicallyAtTime(
                CombatContestLeaderboardCron.class,
                CombatContestLeaderboardCron::postLeaderboard,
                15,
                0,
                ZoneId.of("UTC"));
    }

    private static void postLeaderboard() {
        if (!ActiveLeaseService.shouldCurrentProcessRunScheduledWork()) return;
        BotLogger.logCron("Running CombatContestLeaderboardCron.");
        try {
            SpringContext.getBean(CombatContestService.class).postLeaderboard();
        } catch (Exception e) {
            BotLogger.error("**CombatContestLeaderboardCron failed.**", e);
        }
        BotLogger.logCron("Finished CombatContestLeaderboardCron.");
    }
}
