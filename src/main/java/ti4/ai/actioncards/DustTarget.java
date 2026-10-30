package ti4.ai.actioncards;

import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.GameHorizon;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.strategy.CardValueScale;
import ti4.ai.tactical.CannonWipeRules;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitType;

@UtilityClass
class DustTarget {

    private static final int MAX_RAIDS = 2;
    private static final double NEIGHBOUR_LANE_COST = 0.4;
    private static final double OWN_SYSTEM_LANE_COST = 0.4;
    private static final double MECATOL_COST = 3.0;
    private static final double ACTION_COST = CardValueScale.TOKEN_FLOOR;

    static double value(Game game, Player seat, Tile tile) {
        if (!CannonWipeRules.held(seat) || !CannonWipeRules.NEUTRAL_SHIPS_COUNT || hasOwnDock(seat, tile)) {
            return Double.NEGATIVE_INFINITY;
        }
        double kill = CannonWipeRules.chanceToKillOneShip(game, seat, tile);
        int raids = Math.min(MAX_RAIDS, GameHorizon.roundsLeft(game));
        double scoring = ObjectiveValue.VICTORY_POINT_VALUE * (1 - Math.pow(1 - kill, raids));
        double costs = raids * CardValueScale.TOKEN_FLOOR
                + ActionCardPrior.AVERAGE_CARD
                + ACTION_COST
                + laneCost(game, seat, tile);
        double net = scoring - costs;
        return net > 0 ? net : Double.NEGATIVE_INFINITY;
    }

    private static boolean hasOwnDock(Player seat, Tile tile) {
        return tile.getUnitHolders().values().stream()
                .anyMatch(holder -> BoardView.count(holder, seat, UnitType.Spacedock) > 0);
    }

    private static double laneCost(Game game, Player seat, Tile tile) {
        double cost = BoardView.planets(tile).stream()
                        .anyMatch(planet -> seat.getPlanets().contains(planet.getName()))
                ? OWN_SYSTEM_LANE_COST
                : 0;
        cost += NEIGHBOUR_LANE_COST
                * FoWHelper.getAdjacentTilesAndNotThisTile(game, tile.getPosition(), seat, false).stream()
                        .map(game::getTileByPosition)
                        .filter(adjacent -> adjacent != null && hasOwnPresence(seat, adjacent))
                        .count();
        return cost + (game.mecatols().stream().anyMatch(tile.getUnitHolders()::containsKey) ? MECATOL_COST : 0);
    }

    private static boolean hasOwnPresence(Player seat, Tile tile) {
        return tile.containsPlayersUnits(seat)
                || BoardView.planets(tile).stream()
                        .anyMatch(planet -> seat.getPlanets().contains(planet.getName()));
    }
}
