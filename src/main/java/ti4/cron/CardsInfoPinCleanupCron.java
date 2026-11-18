package ti4.cron;

import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.executors.ExecutionLockType;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.ConsumeGameUtility;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedGame;
import ti4.logging.BotLogger;
import ti4.message.CardsInfoPinCleanupService;
import ti4.spring.service.deploy.ActiveLeaseService;

@UtilityClass
public class CardsInfoPinCleanupCron {

    public static void register() {
        CronManager.registerManualOnly(CardsInfoPinCleanupCron.class, CardsInfoPinCleanupCron::cleanup);
    }

    private static void cleanup() {
        if (!ActiveLeaseService.shouldCurrentProcessRunScheduledWork()) return;
        BotLogger.logCron("Running CardsInfoPinCleanupCron.");
        try {
            List<String> gameNames = GameManager.getManagedGames().stream()
                    .filter(ManagedGame::isActive)
                    .map(ManagedGame::getName)
                    .toList();

            ConsumeGameUtility.consumeGames(gameNames, CardsInfoPinCleanupCron::cleanupGame, ExecutionLockType.READ);
        } catch (Exception e) {
            BotLogger.error("**CardsInfoPinCleanupCron failed.**", e);
        }
        BotLogger.logCron("Finished CardsInfoPinCleanupCron.");
    }

    private static void cleanupGame(Game game) {
        for (Player player : game.getRealPlayers()) {
            CardsInfoPinCleanupService.queuePinnedBotMessageCleanup(player.getCardsInfoThread());
        }
    }
}
