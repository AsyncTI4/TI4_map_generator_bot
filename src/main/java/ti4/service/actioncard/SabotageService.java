package ti4.service.actioncard;

import lombok.experimental.UtilityClass;
import ti4.helpers.Units;
import ti4.map.Game;
import ti4.map.Player;
import ti4.service.agenda.IsPlayerElectedService;
import ti4.service.unit.CheckUnitContainmentService;

@UtilityClass
public class SabotageService {

    public static boolean couldFeasiblySabotage(Player player, Game game) {
        if (player.isNpc()) {
            return false;
        }

        if (couldUseInstinctTraining(player) || couldUseWatcherMech(player, game)) {
            return true;
        }

        if (IsPlayerElectedService.isPlayerElected(game, player, "censure")
                || IsPlayerElectedService.isPlayerElected(game, player, "absol_censure")) {
            return false;
        }

        if (player.isPassed()
                && game.getActivePlayer() != null
                && (game.getActivePlayer().hasTech("tp")
                        || game.getActivePlayer().hasTech("tf-crafty"))) {
            return false;
        }

        return !allSabotagesAreDiscarded(game) && !allAcd2SabotagesAreDiscarded(game);
    }

    public static boolean couldUseInstinctTraining(Player player) {
        return player.hasTechReady("it") && (player.getStrategicCC() > 0 || player.hasRelicReady("emelpar"));
    }

    public static boolean couldUseWatcherMech(Player player, Game game) {
        return player.hasUnit("empyrean_mech")
                && !CheckUnitContainmentService.getTilesContainingPlayersUnits(game, player, Units.UnitType.Mech)
                        .isEmpty();
    }

    private static boolean allSabotagesAreDiscarded(Game game) {
        return game.getDiscardActionCards().containsKey("sabo1")
                && game.getDiscardActionCards().containsKey("sabo2")
                && game.getDiscardActionCards().containsKey("sabo3")
                && game.getDiscardActionCards().containsKey("sabo4");
    }

    private static boolean allAcd2SabotagesAreDiscarded(Game game) {
        return game.getDiscardActionCards().containsKey("sabotage1_acd2")
                && game.getDiscardActionCards().containsKey("sabotage2_acd2")
                && game.getDiscardActionCards().containsKey("sabotage3_acd2")
                && game.getDiscardActionCards().containsKey("sabotage4_acd2");
    }

    public static boolean canSabotage(Player player, Game game) {
        if (player.hasTechReady("it") && (player.getStrategicCC() > 0 || player.hasRelicReady("emelpar"))) {
            return true;
        }

        if (player.hasUnit("empyrean_mech")
                && !CheckUnitContainmentService.getTilesContainingPlayersUnits(game, player, Units.UnitType.Mech)
                        .isEmpty()) {
            return true;
        }

        if (player.hasUnit("tf-triune")
                && !CheckUnitContainmentService.getTilesContainingPlayersUnits(game, player, Units.UnitType.Fighter)
                        .isEmpty()) {
            return true;
        }

        boolean bigAcDeckGame =
                (game.getActionCardDeckSize() + game.getDiscardActionCards().size()) > 180;
        return (bigAcDeckGame || playerHasSabotage(player))
                && !IsPlayerElectedService.isPlayerElected(game, player, "censure")
                && !IsPlayerElectedService.isPlayerElected(game, player, "absol_censure");
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
