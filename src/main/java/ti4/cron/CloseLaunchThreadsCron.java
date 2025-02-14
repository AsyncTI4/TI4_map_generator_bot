package ti4.cron;

import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import ti4.helpers.GameLaunchThreadHelper;
import ti4.map.manage.GameManager;
import ti4.map.manage.ManagedGame;

@UtilityClass
public class CloseLaunchThreadsCron {

    public static void register() {
        CronManager.schedulePeriodically(
                CloseLaunchThreadsCron.class, CloseLaunchThreadsCron::closeLaunchThreads, 1, 60, TimeUnit.MINUTES);
    }

    private static void closeLaunchThreads() {
        // BotLogger.log("`CloneLaunchThreadsCron` is closing some game launch threads");
        for (ManagedGame game : GameManager.getManagedGames()) {
            if (game.getLaunchPostThread() == null) continue;
            GameLaunchThreadHelper.checkIfCanCloseGameLaunchThread(game.getGame(), false);
        }
    }
}
