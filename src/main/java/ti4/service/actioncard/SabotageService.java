package ti4.service.actioncard;

import lombok.experimental.UtilityClass;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units;
import ti4.map.Game;
import ti4.map.Player;

@UtilityClass
public class SabotageService {

    public static boolean couldFeasiblySabotage(Player player, Game game) {
        if (player.isNpc()) {
            return false;
        }
        if (player.hasTechReady("it") && (player.getStrategicCC() > 0 || player.hasRelicReady("emelpar"))) {
            return true;
        }

        if (player.hasUnit("empyrean_mech")
                && !ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, Units.UnitType.Mech)
                        .isEmpty()) {
            return true;
        }

        if (ButtonHelper.isPlayerElected(game, player, "censure")
                || ButtonHelper.isPlayerElected(game, player, "absol_censure")) {
            return false;
        }

        return !allSabotagesAreDiscarded(game) && !allAcd2SabotagesAreDiscarded(game);
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
                && !ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, Units.UnitType.Mech)
                        .isEmpty()) {
            return true;
        }

        boolean bigAcDeckGame =
                (game.getActionCardDeckSize() + game.getDiscardActionCards().size()) > 180;
        return (bigAcDeckGame || playerHasSabotage(player))
                && !ButtonHelper.isPlayerElected(game, player, "censure")
                && !ButtonHelper.isPlayerElected(game, player, "absol_censure");
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
