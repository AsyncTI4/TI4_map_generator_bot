package ti4.ai.eval;

import java.util.Map;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
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
                int move = BoardView.moveValueWithGravityDrive(mover, ship.getKey());
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

    public static boolean anyEnemyCanReach(Game game, Player seat, Tile target) {
        return game.getRealPlayers().stream()
                .filter(other -> other != seat)
                .anyMatch(other -> incomingFleetCost(game, other, target) > 0);
    }
}
