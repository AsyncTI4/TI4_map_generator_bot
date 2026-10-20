package ti4.ai.tactical;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.ai.scoring.Footprint;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;
import ti4.service.info.ListPlayerInfoService;

@UtilityClass
class SeatCover {

    static final String OCCUPY_THE_SEAT = "ose";

    static boolean chases(Game game, Player seat, Tile tile, ObjectiveValue objectives) {
        return tile == game.getMecatolTile()
                && seat.getSecretsUnscored().containsKey(OCCUPY_THE_SEAT)
                && objectives.before().planets().stream().anyMatch(game.mecatols()::contains);
    }

    static boolean completesThroughCannon(Game game, ObjectiveValue objectives, Footprint after, double cannonFire) {
        int needed = ListPlayerInfoService.getObjectiveThreshold(OCCUPY_THE_SEAT, game);
        int survivors = objectives.progress(OCCUPY_THE_SEAT, after) - (int) Math.round(cannonFire);
        return objectives.progress(OCCUPY_THE_SEAT, objectives.before()) < needed && survivors >= needed;
    }

    static double exposure(Player seat, Tile tile, List<UnitMove> arriving, double cannonFire) {
        Map<UnitType, Integer> present = new EnumMap<>(BoardView.ships(BoardView.space(tile), seat));
        for (UnitMove move : arriving) {
            if (BoardView.MOVING_SHIPS.contains(move.type()) || move.type() == UnitType.Fighter) {
                present.merge(move.type(), move.count(), Integer::sum);
            }
        }
        List<Double> costs = new ArrayList<>();
        present.forEach((type, count) -> {
            double cost = BoardView.model(seat, type).map(UnitModel::getCost).orElse(0f);
            for (int ship = 0; ship < count; ship++) costs.add(cost);
        });
        Collections.sort(costs);
        double remaining = cannonFire;
        double lost = 0;
        for (double cost : costs) {
            if (remaining <= 0) break;
            lost += Math.min(1, remaining) * cost;
            remaining--;
        }
        return lost;
    }
}
