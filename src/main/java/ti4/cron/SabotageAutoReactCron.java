package ti4.cron;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import lombok.experimental.UtilityClass;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.manage.GameManager;
import ti4.map.manage.ManagedGame;
import ti4.message.BotLogger;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageType;
import ti4.service.actioncard.SabotageService;
import ti4.service.button.ReactionService;

import static java.util.function.Predicate.not;

@UtilityClass
public class SabotageAutoReactCron {

    private static final int SCHEDULED_PERIOD_MINUTES = 10;
    private static final int RUNS_PER_HOUR = 60 / SCHEDULED_PERIOD_MINUTES;

    public static void register() {
        CronManager.schedulePeriodically(SabotageAutoReactCron.class, SabotageAutoReactCron::autoReact, 5, SCHEDULED_PERIOD_MINUTES, TimeUnit.MINUTES);
    }

    private static void autoReact() {
        GameManager.getManagedGames().stream()
            .filter(not(ManagedGame::isHasEnded))
            .map(ManagedGame::getGame)
            .forEach(SabotageAutoReactCron::autoReact);
    }

    private static void autoReact(Game game) {
        try {
            automaticallyReactToSabotageWindows(game);
        } catch (Exception e) {
            BotLogger.error(new BotLogger.LogMessageOrigin(game), "SabotageAutoReactCron failed for game: " + game.getName(), e);
        }
    }

    private static void automaticallyReactToSabotageWindows(Game game) {
        List<GameMessageManager.GameMessage> acMessages = GameMessageManager.getAll(game.getName(), GameMessageType.ACTION_CARD);
        if (acMessages.isEmpty()) {
            return;
        }

        for (Player player : game.getRealPlayers()) {
            if (!playerShouldRandomlyReact(player) || SabotageService.canSabotage(player, game)) {
                continue;
            }

            for (var acMessage : acMessages) {
                if (!ReactionService.checkForSpecificPlayerReact(acMessage.messageId(), player, game)) {
                    String message = game.isFowMode() ? "No Sabotage" : null;
                    ReactionService.addReaction(player, false, message, null, acMessage.messageId(), game);
                }
            }
        }
    }

    private static boolean playerShouldRandomlyReact(Player player) {
        if (player.isAFK() && player.getAc() != 0) {
            return false;
        }
        return shouldRandomlyReact(player);
    }

    private static boolean shouldRandomlyReact(Player player) {
        if (player.getAutoSaboPassMedian() == 0) {
            return false;
        }
        int rollMax = player.getAutoSaboPassMedian() * RUNS_PER_HOUR;
        int rollResult = ThreadLocalRandom.current().nextInt(1, rollMax + 1);
        return rollResult == rollMax;
    }
}
