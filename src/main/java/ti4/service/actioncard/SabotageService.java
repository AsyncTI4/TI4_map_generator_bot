package ti4.service.actioncard;

import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.helpers.Helper;
import ti4.helpers.Units;
import ti4.map.Game;
import ti4.map.Player;
import ti4.service.agenda.IsPlayerElectedService;
import ti4.service.unit.CheckUnitContainmentService;

@UtilityClass
public class SabotageService {

    private static final Set<String> SABOTAGE_CARD_ALIASES =
            Set.of(
                    "sabo1",
                    "sabo2",
                    "sabo3",
                    "sabo4",
                    "sabotage_ds",
                    "sabotage1_acd2",
                    "sabotage2_acd2",
                    "sabotage3_acd2",
                    "sabotage4_acd2",
                    "tf-shatter1",
                    "tf-shatter2");

    public static boolean couldFeasiblySabotage(Player player, Game game) {
        if (player.isNpc()) {
            return false;
        }

        if (couldUseInstinctTraining(player) || couldUseWatcherMech(player, game)) {
            return true;
        }

        if (game.isTwilightsFallMode()
                && (Helper.checkForAllShattersDiscarded(game) || player.getAc() == 0)) {
            return false;
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
        return player.getActionCards().keySet().stream().anyMatch(SABOTAGE_CARD_ALIASES::contains);
    }
}
