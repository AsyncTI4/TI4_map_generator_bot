package ti4.cron;

import static java.util.function.Predicate.not;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.executors.ExecutionLockManager;
import ti4.map.Game;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.ManagedGame;
import ti4.message.GameMessageManager;
import ti4.message.logging.BotLogger;

@UtilityClass
public class EndOldGamesCron {

    private static final Period AUTOMATIC_GAME_END_INACTIVITY_THRESHOLD = Period.ofMonths(2);

    public static void register() {
        CronManager.schedulePeriodicallyAtTime(
                EndOldGamesCron.class, EndOldGamesCron::endOldGames, 2, 0, ZoneId.of("America/New_York"));
    }

    private static void endOldGames() {
        BotLogger.logCron("Running EndOldGamesCron.");
        try {
            GameManager.getManagedGames().stream()
                    .filter(not(ManagedGame::isHasEnded))
                    .map(ManagedGame::getName)
                    .forEach(gameName -> ExecutionLockManager.wrapWithLockAndRelease(
                                    gameName, ExecutionLockManager.LockType.WRITE, () -> endIfOld(gameName))
                            .run());
        } catch (Exception e) {
            BotLogger.error("**Error ending inactive games!**", e);
        }
        BotLogger.logCron("Finished EndOldGamesCron.");
    }

    private void endIfOld(String gameName) {
        Game game = GameManager.getManagedGame(gameName).getGame();
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
