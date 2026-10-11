package ti4.cron;

import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedGame;
import ti4.helpers.GameLaunchThreadHelper;

@UtilityClass
public class CloseLaunchThreadsCron {

    public static void register() {
        CronManager.schedulePeriodically(
                CloseLaunchThreadsCron.class,
                CronManager.leasedAndLogged(CloseLaunchThreadsCron.class, CloseLaunchThreadsCron::closeLaunchThreads),
                1,
                60,
                TimeUnit.MINUTES);
    }

    private static void closeLaunchThreads() {
        for (ManagedGame game : GameManager.getManagedGames()) {
            if (game.getLaunchPostThread() == null) continue;
            GameLaunchThreadHelper.checkIfCanCloseGameLaunchThread(game.getGame(), false);
        }
    }
}
