package ti4.ai.tactical;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.FleetLimit;
import ti4.ai.scoring.ObjectiveValue;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;

@UtilityClass
public class SeatStrike {

    private static final String OCCUPY_THE_SEAT = SeatCover.OCCUPY_THE_SEAT;
    private static final double FREE_MECATOL_FEASIBILITY = 1.0;
    private static final double CROWDED_MECATOL_FEASIBILITY = 0.2;
    private static final double OUT_OF_SHIPS_FEASIBILITY = 0.1;

    public static boolean wantsDockOn(Game game, Player seat, Planet planet) {
        return holds(seat)
                && game.mecatols().contains(planet.getName())
                && seat.getPlanets().contains(planet.getName());
    }

    static boolean holds(Player seat) {
        return seat.getSecretsUnscored().containsKey(OCCUPY_THE_SEAT);
    }

    static boolean isSeat(Game game, Player seat, Tile tile) {
        return tile == game.getMecatolTile() && holds(seat);
    }

    static boolean controls(Game game, Player seat) {
        return seat.getPlanets().stream().anyMatch(game.mecatols()::contains);
    }

    static boolean takesMecatol(Game game, Player seat, StrikeEstimate estimate) {
        return controls(game, seat) || estimate.landings().keySet().stream().anyMatch(game.mecatols()::contains);
    }

    static boolean landsOnSeat(Game game, Player seat, Tile tile, StrikeEstimate estimate) {
        return isSeat(game, seat, tile) && estimate.landings().keySet().stream().anyMatch(game.mecatols()::contains);
    }

    static double gain(
            Game game,
            Player seat,
            ObjectiveValue objectives,
            Tile target,
            Map<UnitType, Integer> arriving,
            boolean controlsAfter) {
        if (!isSeat(game, seat, target)) return 0;
        int ships = controlsAfter ? shipsAfter(seat, target, arriving) : 0;
        return objectives.gainAt(OCCUPY_THE_SEAT, ships);
    }

    static double survivorAdjustment(
            Game game,
            Player seat,
            ObjectiveValue objectives,
            Tile target,
            double departingGain,
            StrikeEstimate estimate) {
        if (!isSeat(game, seat, target)) return 0;
        boolean controlsAfter = takesMecatol(game, seat, estimate);
        double survivingGain = gain(game, seat, objectives, target, estimate.survivors(), controlsAfter);
        double removal = controlsAfter ? overflowCost(seat, target, estimate.survivors()) : 0;
        return survivingGain - departingGain - removal;
    }

    static double immediateStake(
            Game game, Player seat, ObjectiveValue objectives, Tile target, StrikeEstimate estimate) {
        if (!landsOnSeat(game, seat, target, estimate)) return 0;
        return Math.max(0, gain(game, seat, objectives, target, estimate.survivors(), true));
    }

    static double prize(Game game, Player seat, Tile target) {
        if (!isSeat(game, seat, target) || controls(game, seat)) return 0;
        return ObjectiveValue.VICTORY_POINT_VALUE;
    }

    static double overflowCost(Player seat, Tile target, Map<UnitType, Integer> arriving) {
        Map<UnitType, Integer> present = shipsPresent(seat, target, arriving);
        int excess = nonFighterCount(present) - FleetLimit.ships(seat);
        if (excess <= 0) return 0;
        List<Double> costs = new ArrayList<>();
        present.forEach((type, count) -> {
            if (type == UnitType.Fighter) return;
            double cost = BoardView.model(seat, type).map(UnitModel::getCost).orElse(0f);
            for (int ship = 0; ship < count; ship++) costs.add(cost);
        });
        Collections.sort(costs);
        return costs.subList(0, excess).stream()
                .mapToDouble(Double::doubleValue)
                .sum();
    }

    private static int shipsAfter(Player seat, Tile target, Map<UnitType, Integer> arriving) {
        Map<UnitType, Integer> present = shipsPresent(seat, target, arriving);
        int kept = Math.min(nonFighterCount(present), FleetLimit.ships(seat));
        int room = present.entrySet().stream()
                .filter(entry -> entry.getKey() != UnitType.Fighter)
                .mapToInt(entry -> entry.getValue() * BoardView.capacity(seat, entry.getKey()))
                .sum();
        return kept + Math.min(present.getOrDefault(UnitType.Fighter, 0), room);
    }

    private static Map<UnitType, Integer> shipsPresent(Player seat, Tile target, Map<UnitType, Integer> arriving) {
        Map<UnitType, Integer> present = new EnumMap<>(BoardView.ships(BoardView.space(target), seat));
        arriving.forEach((type, count) -> {
            if (BoardView.MOVING_SHIPS.contains(type) || type == UnitType.Fighter)
                present.merge(type, count, Integer::sum);
        });
        return present;
    }

    private static int nonFighterCount(Map<UnitType, Integer> ships) {
        return ships.entrySet().stream()
                .filter(entry -> entry.getKey() != UnitType.Fighter)
                .mapToInt(Map.Entry::getValue)
                .sum();
    }

    public static double feasibility(Game game, Player seat) {
        Tile mecatol = game.getMecatolTile();
        if (mecatol == null || FleetLimit.ships(seat) <= 0) return OUT_OF_SHIPS_FEASIBILITY;
        if (controls(game, seat)) return FREE_MECATOL_FEASIBILITY;
        Player opponent = TacticalPlanner.singleOpponent(game, seat, mecatol);
        if (opponent == null) {
            boolean empty = !BoardView.hasEnemyUnits(game, seat, mecatol) && !heldByAnother(game, seat);
            return empty ? FREE_MECATOL_FEASIBILITY : CROWDED_MECATOL_FEASIBILITY;
        }
        double reach = TacticalPlanner.stageFeasibility(new TacticalPlanner.Context(game, seat), mecatol, opponent);
        return Math.max(CROWDED_MECATOL_FEASIBILITY, reach);
    }

    private static boolean heldByAnother(Game game, Player seat) {
        return game.getRealPlayers().stream()
                .filter(other -> other != seat)
                .anyMatch(other -> other.getPlanets().stream().anyMatch(game.mecatols()::contains));
    }
}
