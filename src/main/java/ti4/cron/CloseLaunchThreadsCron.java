package ti4.cron;

import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import ti4.helpers.GameLaunchThreadHelper;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.ManagedGame;
import ti4.message.logging.BotLogger;

@UtilityClass
public class CloseLaunchThreadsCron {

    public static void register() {
        CronManager.schedulePeriodically(
                CloseLaunchThreadsCron.class, CloseLaunchThreadsCron::closeLaunchThreads, 1, 60, TimeUnit.MINUTES);
    }

    private static void closeLaunchThreads() {
        BotLogger.info("Running CloneLaunchThreadsCron.");
        for (ManagedGame game : GameManager.getManagedGames()) {
            if (game.getLaunchPostThread() == null) continue;
            GameLaunchThreadHelper.checkIfCanCloseGameLaunchThread(game.getGame(), false);
        }
        BotLogger.info("Finished CloneLaunchThreadsCron.");
    }
}
