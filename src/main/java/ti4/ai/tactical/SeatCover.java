package ti4.ai.tactical;

import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.DiceOdds;
import ti4.ai.eval.ImaginedTurn;
import ti4.ai.scoring.Footprint;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;
import ti4.service.info.ListPlayerInfoService;

@UtilityClass
class SeatCover {

    static final String OCCUPY_THE_SEAT = "ose";

    static boolean chases(Game game, Player seat, Tile tile, ObjectiveValue objectives) {
        return chasesSeat(game, seat, tile, objectives) || SupplyLines.chases(game, seat, tile, objectives);
    }

    static boolean contests(Game game, Player seat, Tile tile, ObjectiveValue objectives) {
        return !isSeat(game, seat, tile) && SupplyLines.chases(game, seat, tile, objectives);
    }

    static boolean completesThroughCannon(
            Game game,
            Player seat,
            Tile tile,
            ObjectiveValue objectives,
            Footprint after,
            List<UnitMove> arriving,
            double cannonFire) {
        if (isSeat(game, seat, tile)) return seatSurvives(game, objectives, after, cannonFire);
        return SupplyLines.survival(game, seat, tile, arriving) > 0;
    }

    static boolean completesAtRisk(Game game, Player seat, Tile tile, List<UnitMove> arriving) {
        return isSeat(game, seat, tile) || SupplyLines.survival(game, seat, tile, arriving) > 0;
    }

    static double seatShortfall(
            Game game, Player seat, Tile tile, ObjectiveValue objectives, Footprint after, List<UnitMove> arriving) {
        if (!isSeat(game, seat, tile)) return 0;
        int needed = ListPlayerInfoService.getObjectiveThreshold(OCCUPY_THE_SEAT, game);
        int reached = objectives.progress(OCCUPY_THE_SEAT, after);
        if (reached < needed) return 0;
        int spareHits = reached - needed + freshSustains(seat, tile, arriving);
        double held = 1 - DiceOdds.atLeast(CannonExposure.dieChances(game, seat, tile), spareHits + 1);
        return ObjectiveValue.VICTORY_POINT_VALUE * (1 - held);
    }

    private static int freshSustains(Player seat, Tile tile, List<UnitMove> arriving) {
        UnitHolder space = BoardView.space(tile);
        int sustains = 0;
        for (UnitType type : BoardView.MOVING_SHIPS) {
            if (!sustains(seat, type)) continue;
            sustains += BoardView.undamaged(space, seat, type);
            for (UnitMove move : arriving) {
                if (move.type() == type) sustains += move.count();
            }
        }
        return sustains;
    }

    private static boolean sustains(Player seat, UnitType type) {
        return BoardView.model(seat, type).filter(UnitModel::getSustainDamage).isPresent();
    }

    static double shortfall(Game game, Player seat, Tile tile, ObjectiveValue objectives, List<UnitMove> arriving) {
        if (isSeat(game, seat, tile) || !SupplyLines.chases(game, seat, tile, objectives)) return 0;
        double chance = SupplyLines.survival(game, seat, tile, arriving) * SupplyLines.hold(game, seat, tile);
        return ObjectiveValue.VICTORY_POINT_VALUE * (1 - chance);
    }

    private static boolean isSeat(Game game, Player seat, Tile tile) {
        return tile == game.getMecatolTile()
                && ImaginedTurn.unscoredSecrets(seat).containsKey(OCCUPY_THE_SEAT);
    }

    private static boolean chasesSeat(Game game, Player seat, Tile tile, ObjectiveValue objectives) {
        return isSeat(game, seat, tile)
                && objectives.before().planets().stream().anyMatch(game.mecatols()::contains);
    }

    private static boolean seatSurvives(Game game, ObjectiveValue objectives, Footprint after, double cannonFire) {
        int needed = ListPlayerInfoService.getObjectiveThreshold(OCCUPY_THE_SEAT, game);
        int survivors = objectives.progress(OCCUPY_THE_SEAT, after) - (int) Math.round(cannonFire);
        return objectives.progress(OCCUPY_THE_SEAT, objectives.before()) < needed && survivors >= needed;
    }
}
