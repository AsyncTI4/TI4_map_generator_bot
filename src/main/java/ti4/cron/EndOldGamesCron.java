package ti4.cron;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;

import lombok.experimental.UtilityClass;
import ti4.map.Game;
import ti4.map.manage.GameManager;
import ti4.map.manage.ManagedGame;
import ti4.message.BotLogger;

import static java.util.function.Predicate.not;

@UtilityClass
public class EndOldGamesCron {

    private static final Period AUTOMATIC_GAME_END_INACTIVITY_THRESHOLD = Period.ofMonths(2);

    public static void register() {
        CronManager.schedulePeriodicallyAtTime(EndOldGamesCron.class, EndOldGamesCron::endOldGames, 2, 0, ZoneId.of("America/New_York"));
    }

    private static void endOldGames() {
        try {
            GameManager.getManagedGames().stream()
                .filter(not(ManagedGame::isHasEnded))
                .map(ManagedGame::getGame)
                .forEach(EndOldGamesCron::endIfOld);
        } catch (Exception e) {
            BotLogger.error("**Error ending inactive games!**", e);
        }
    }

    private void endIfOld(Game game) {
        LocalDate lastModifiedDate = Instant.ofEpochMilli(game.getLastModifiedDate())
            .atZone(ZoneId.systemDefault())
            .toLocalDate();

        LocalDate oldestLastModifiedDateBeforeEnding = LocalDate.now().minus(AUTOMATIC_GAME_END_INACTIVITY_THRESHOLD);

        if (lastModifiedDate.isBefore(oldestLastModifiedDateBeforeEnding)) {
            BotLogger.info("Game: " + game.getName() + " has not been modified since ~" + lastModifiedDate +
                " - the game flag `hasEnded` has been set to true");
            game.setHasEnded(true);
            GameManager.save(game, "Game automatically ended");
        }
    }
}
