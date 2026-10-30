package ti4.ai.eval;

import java.util.HashSet;
import java.util.Map;
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
        double total = 0;
        for (Tile origin : game.getTileMap().values()) {
            if (origin == target || origin.hasPlayerCC(mover)) continue;
            for (Map.Entry<UnitType, Integer> ship :
                    BoardView.ships(BoardView.space(origin), mover).entrySet()) {
                if (ship.getKey() == UnitType.Fighter) continue;
                int move = BoardView.moveValueWithBoosts(mover, ship.getKey());
                if (!MovementGraph.reach(game, mover, origin.getPosition(), move)
                        .containsKey(target.getPosition())) continue;
                total += BoardView.model(mover, ship.getKey())
                                .map(UnitModel::getCost)
                                .orElse(0f)
                        * ship.getValue();
            }
        }
        return total;
    }

    public static Set<String> reachableByEnemies(Game game, Player seat) {
        Set<String> reachable = new HashSet<>();
        for (Player other : game.getRealPlayers()) {
            if (other == seat) continue;
            for (Tile origin : game.getTileMap().values()) {
                UnitHolder space = BoardView.space(origin);
                if (BoardView.nonFighterShips(space, other) <= 0) continue;
                int move = BoardView.ships(space, other).keySet().stream()
                        .filter(type -> type != UnitType.Fighter)
                        .mapToInt(type -> BoardView.moveValueWithBoosts(other, type))
                        .max()
                        .orElse(0);
                reachable.addAll(MovementGraph.reach(game, other, origin.getPosition(), move)
                        .keySet());
            }
        }
        return reachable;
    }

    public static boolean anyEnemyCanReach(Game game, Player seat, Tile target) {
        return game.getRealPlayers().stream()
                .filter(other -> other != seat)
                .anyMatch(other -> incomingFleetCost(game, other, target) > 0);
    }
}
