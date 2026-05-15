package ti4.cron;

import static java.util.function.Predicate.not;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.executors.ExecutionLockType;
import ti4.game.Game;
import ti4.game.persistence.ConsumeGameUtility;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedGame;
import ti4.logging.BotLogger;
import ti4.message.GameMessageManager;
import ti4.spring.service.deploy.ActiveLeaseService;

@UtilityClass
public class EndOldGamesCron {

    private static final Period AUTOMATIC_GAME_END_INACTIVITY_THRESHOLD = Period.ofMonths(2);

    public static void register() {
        CronManager.schedulePeriodicallyAtTime(
                EndOldGamesCron.class, EndOldGamesCron::endOldGames, 2, 0, ZoneId.of("America/New_York"));
    }

    private static void endOldGames() {
        if (!ActiveLeaseService.shouldCurrentProcessRunScheduledWork()) return;
        BotLogger.logCron("Running EndOldGamesCron.");
        try {
            List<String> gameNames = GameManager.getManagedGames().stream()
                    .filter(not(ManagedGame::isHasEnded))
                    .map(ManagedGame::getName)
                    .toList();
            ConsumeGameUtility.consumeGames(gameNames, EndOldGamesCron::endIfOld, ExecutionLockType.WRITE);
        } catch (Exception e) {
            BotLogger.error("**Error ending inactive games!**", e);
        }
        BotLogger.logCron("Finished EndOldGamesCron.");
    }

    private static void endIfOld(Game game) {
        LocalDate lastModifiedDate = Instant.ofEpochMilli(game.getLastModifiedDate())
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        LocalDate oldestLastModifiedDateBeforeEnding = LocalDate.now().minus(AUTOMATIC_GAME_END_INACTIVITY_THRESHOLD);

        if (lastModifiedDate.isBefore(oldestLastModifiedDateBeforeEnding)) {
            BotLogger.info("Game: " + game.getName() + " has not been modified since ~" + lastModifiedDate
                    + " - the game flag `hasEnded` has been set to true");
            // TODO: It'd be better if we could just call EndGameService
            game.setHasEnded(true);
            game.setEndedDate(System.currentTimeMillis());
            game.setAutoPing(false);
            game.setAutoPingSpacer(0);
            GameMessageManager.remove(List.of(game.getName()));
            GameManager.save(game, "Ended old game.");
        }
    }
}
