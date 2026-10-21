package ti4.ai.tactical;

import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.ai.scoring.Footprint;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
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

    static double shortfall(Game game, Player seat, Tile tile, ObjectiveValue objectives, List<UnitMove> arriving) {
        if (isSeat(game, seat, tile) || !SupplyLines.chases(game, seat, tile, objectives)) return 0;
        double chance = SupplyLines.survival(game, seat, tile, arriving) * SupplyLines.hold(game, seat, tile);
        return ObjectiveValue.VICTORY_POINT_VALUE * (1 - chance);
    }

    private static boolean isSeat(Game game, Player seat, Tile tile) {
        return tile == game.getMecatolTile() && seat.getSecretsUnscored().containsKey(OCCUPY_THE_SEAT);
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
