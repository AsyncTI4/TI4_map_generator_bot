package ti4.cron;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import ti4.executors.ExecutionLockManager;
import ti4.executors.ExecutionLockType;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedGame;
import ti4.helpers.discord.DiscordHelper;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageType;
import ti4.service.actioncard.SabotageService;
import ti4.service.button.ReactionService;
import ti4.spring.service.deploy.ActiveLeaseService;

@UtilityClass
public class SabotageAutoReactCron {

    private static final int SCHEDULED_PERIOD_MINUTES = 10;
    private static final int RUNS_PER_HOUR = 60 / SCHEDULED_PERIOD_MINUTES;

    public static void register() {
        CronManager.schedulePeriodically(
                SabotageAutoReactCron.class,
                SabotageAutoReactCron::autoReact,
                5,
                SCHEDULED_PERIOD_MINUTES,
                TimeUnit.MINUTES);
    }

    private static void autoReact() {
        if (!ActiveLeaseService.shouldCurrentProcessRunScheduledWork()) return;
        BotLogger.logCron("Running SabotageAutoReactCron.");

        Map<String, List<GameMessageManager.GameMessage>> acMessagesByGame =
                GameMessageManager.getAllByGame(GameMessageType.ACTION_CARD);

        var gamesToRemove = new HashSet<String>();
        for (Map.Entry<String, List<GameMessageManager.GameMessage>> entry : acMessagesByGame.entrySet()) {
            String gameName = entry.getKey();

            ExecutionLockManager.wrapWithLockAndRelease(gameName, ExecutionLockType.WRITE, () -> {
                        ManagedGame managedGame = GameManager.getManagedGame(gameName);
                        if (managedGame == null || managedGame.isHasEnded()) {
                            gamesToRemove.add(gameName);
                            return;
                        }

                        Game game = managedGame.getGame();
                        List<GameMessageManager.GameMessage> acMessages = entry.getValue();
                        try {
                            automaticallyReactToSabotageWindows(game, acMessages);
                        } catch (Exception e) {
                            BotLogger.error(
                                    new LogOrigin(game), "SabotageAutoReactCron failed for game: " + game.getName(), e);
                        }
                    })
                    .run();
        }

        GameMessageManager.remove(gamesToRemove);

        BotLogger.logCron("Finished SabotageAutoReactCron.");
    }

    private static void automaticallyReactToSabotageWindows(
            Game game, List<GameMessageManager.GameMessage> acMessages) {
        for (Player player : game.getRealPlayers()) {
            if (!playerShouldRandomlyReact(player, game)) {
                continue;
            }

            for (var acMessage : acMessages) {
                if (ReactionService.checkForSpecificPlayerReact(acMessage.messageId(), player, game)) {
                    continue;
                }

                String message = game.isFowMode() ? "No Sabotage" : null;
                try {
                    ReactionService.addReaction(player, false, message, null, acMessage.messageId(), game);
                } catch (Exception e) {
                    if (DiscordHelper.isUnknownMessageError(e)) {
                        GameMessageManager.remove(game.getName(), acMessage.messageId());
                        continue;
                    }
                    throw e;
                }
            }
        }
    }

    private static boolean playerShouldRandomlyReact(Player player, Game game) {
        if (player.isAFK() || player.getAutoSaboPassMedian() == 0) {
            return false;
        }

        boolean canSabotage = SabotageService.canSabotage(player, game);
        if (canSabotage) {
            return false;
        }

        int rollMax = player.getAutoSaboPassMedian() * RUNS_PER_HOUR;
        int rollResult = ThreadLocalRandom.current().nextInt(1, rollMax + 1);
        return rollResult == rollMax;
    }
}
