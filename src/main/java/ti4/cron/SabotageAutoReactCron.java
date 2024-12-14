package ti4.cron;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import lombok.experimental.UtilityClass;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.manage.GameManager;
import ti4.map.manage.ManagedGame;
import ti4.message.BotLogger;
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
            BotLogger.log("SabotageAutoReactCron failed for game: " + game.getName(), e);
        }
    }

    private static void automaticallyReactToSabotageWindows(Game game) {
        List<String> messageIds = new ArrayList<>(game.getMessageIDsForSabo());
        if (messageIds.isEmpty()) {
            return;
        }

        for (Player player : game.getRealPlayers()) {
            if (!playerShouldRandomlyReact(player) || canSabotage(player, game)) {
                continue;
            }

            for (String messageId : messageIds) {
                if (!ReactionService.checkForASpecificPlayerReact(messageId, player, game)) {
                    String message = game.isFowMode() ? "No Sabotage" : null;
                    ReactionService.addReaction(player, false, message, null, messageId, game);//TODO: updates game...
                }
            }
        }
    }

    private static boolean canSabotage(Player player, Game game) {
        if (player.hasTechReady("it") && (player.getStrategicCC() > 0 || player.hasRelicReady("emelpar"))) {
            return true;
        }

        if (player.hasUnit("empyrean_mech") && !ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, Units.UnitType.Mech).isEmpty()) {
            return true;
        }

        boolean bigAcDeckGame = (game.getActionCardDeckSize() + game.getDiscardActionCards().size()) > 180;
        return (bigAcDeckGame || playerHasSabotage(player))
            && !ButtonHelper.isPlayerElected(game, player, "censure")
            && !ButtonHelper.isPlayerElected(game, player, "absol_censure");
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

    private static boolean playerHasSabotage(Player player) {
        return player.getActionCards().containsKey("sabo1")
            || player.getActionCards().containsKey("sabo2")
            || player.getActionCards().containsKey("sabo3")
            || player.getActionCards().containsKey("sabo4")
            || player.getActionCards().containsKey("sabotage_ds")
            || player.getActionCards().containsKey("sabotage1_acd2")
            || player.getActionCards().containsKey("sabotage2_acd2")
            || player.getActionCards().containsKey("sabotage3_acd2")
            || player.getActionCards().containsKey("sabotage4_acd2");
    }
}
