package ti4.cron;

import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import ti4.executors.ExecutionLockType;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.ConsumeGameUtility;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedGame;
import ti4.message.CardsInfoPinCleanupService;

@UtilityClass
public class CardsInfoPinCleanupCron {

    private static final long MANUAL_ONLY_DELAY_DAYS = Long.MAX_VALUE;

    public static void register() {
        CronManager.scheduleOnce(
                CardsInfoPinCleanupCron.class,
                CronManager.leasedAndLogged(CardsInfoPinCleanupCron.class, CardsInfoPinCleanupCron::cleanup),
                MANUAL_ONLY_DELAY_DAYS,
                TimeUnit.DAYS);
    }

    private static void cleanup() {
        List<String> gameNames = GameManager.getManagedGames().stream()
                .filter(ManagedGame::isActive)
                .map(ManagedGame::getName)
                .toList();
        ConsumeGameUtility.consumeGames(gameNames, CardsInfoPinCleanupCron::cleanupGame, ExecutionLockType.READ);
    }

    private static void cleanupGame(Game game) {
        for (Player player : game.getRealPlayers()) {
            CardsInfoPinCleanupService.queuePinnedBotMessageCleanup(player.getCardsInfoThread());
        }
    }
}
