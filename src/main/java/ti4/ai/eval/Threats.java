package ti4.ai.eval;

import java.util.HashSet;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;

@UtilityClass
public class Threats {

    public static double incomingFleetCost(Game game, Player mover, Tile target) {
        UnitHolder space = BoardView.space(target);
        int supply = FleetLimit.ships(mover)
                - FighterWings.fleetSlots(
                        game, mover, BoardView.ships(space, mover), BoardView.groundForces(space, mover), false);
        return MoveBudget.shipsWithin(
                        game, mover, target, Journey.thisAction(), origin -> true, type -> cost(mover, type), supply)
                .values()
                .stream()
                .flatMap(ships -> ships.entrySet().stream())
                .filter(ship -> ship.getKey() != UnitType.Fighter || FighterWings.flies(game, mover))
                .mapToDouble(ship -> cost(mover, ship.getKey()) * ship.getValue())
                .sum();
    }

    private static double cost(Player player, UnitType type) {
        return BoardView.model(player, type).map(UnitModel::getCost).orElse(0f);
    }

    public static Set<String> reachableByEnemies(Game game, Player seat) {
        Set<String> reachable = new HashSet<>();
        for (Player other : game.getRealPlayers()) {
            if (other == seat) continue;
            for (Tile origin : game.getTileMap().values()) {
                if (!hasSelfMovingUnits(game, other, BoardView.space(origin))) continue;
                int budget = MoveBudget.searchBudget(game, other, origin);
                if (budget <= 0) continue;
                reachable.addAll(MovementGraph.riftingDistances(game, other, origin.getPosition(), budget)
                        .keySet());
            }
        }
        return reachable;
    }

    private static boolean hasSelfMovingUnits(Game game, Player player, UnitHolder space) {
        if (BoardView.nonFighterShips(space, player) > 0) return true;
        return FighterWings.flies(game, player) && BoardView.count(space, player, UnitType.Fighter) > 0;
    }

    public static boolean anyEnemyCanReach(Game game, Player seat, Tile target) {
        return game.getRealPlayers().stream()
                .filter(other -> other != seat)
                .anyMatch(other -> incomingFleetCost(game, other, target) > 0);
    }
}
